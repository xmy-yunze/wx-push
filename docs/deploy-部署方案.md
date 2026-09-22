# deploy · 部署方案

> 制定时间：2026-09-22
> 状态：⚠️ **暂缓** —— 预检实测发现共享服务器**资源不足**（可用内存仅 **457 Mi**、**无 swap**），
>       按原方案部署几乎必然触发 OOM，**可能拖垮别人的服务**（红线 R2）。
>       **本轮改走内网穿透先联调** → `docs/联调-内网穿透方案.md`
> 配套文档：`docs/deploy-检测清单.md`（检测清单）
> 部署方式：**Docker Compose**（2026-09-22 云泽确认）
> 服务器：**阿里云 ECS · 出口 IP `121.41.167.211` · Ubuntu · 共享机器**（实测数据见 §1.2）

---

# 🔴 零、红线（最高优先级，任何情况下不得违反）

> **一句话：在任何"会占端口 / 吃资源 / 动别人东西"的动作之前，必须先检查；检查不通过就中止。**

## 0.1 四条铁律

| # | 铁律 | 违反后果 |
|---|---|---|
| **R1** | **先查端口，再部署。** 启动任何监听端口的容器之前，必须先确认该端口**没有被占用**；被占用就改端口，**绝不去抢、绝不去停别人的服务** | 端口冲突 → 我们的容器起不来；若强行处理，可能**打断别人的服务** |
| **R2** | **先查资源，再启用。** 启用前必须确认内存/磁盘够用，且**我们的容器都设了内存上限** | 共享机器上把内存吃满 → **OOM Killer 会随机杀进程，被杀的可能不是我们，而是别人的服务** → **服务器挂机** |
| **R3** | **绝不碰别人的东西。** 不停止/删除/重启任何不是 `wxpush-` 前缀的容器、镜像、卷、网络 | 别人的服务中断，属于事故 |
| **R4** | **绝不跑全局性 docker 命令。** 禁用清单见下 | 影响面不可控 |

## 0.2 🚫 绝对禁用的命令（在这台共享机器上）

```bash
# —— 以下命令一律不准执行 ——
docker system prune -a          # 会删掉别人没在运行的镜像/构建缓存
docker volume prune             # 会删掉别人没在用（但需要）的卷 —— 数据丢失
docker image prune -a           # 同上
docker rm $(docker ps -aq)      # 删掉所有容器，含别人的
docker rmi $(docker images -q)  # 删掉所有镜像
docker stop $(docker ps -q)     # 停掉所有容器，含别人的
systemctl restart docker        # ⚠️ 重启 docker 守护进程 = 重启所有容器 = 别人的服务全断
systemctl stop docker
rm -rf /var/lib/docker         # 自毁，永远不要想
```

> ⚠️ **`systemctl restart docker` 是最容易被误用的一个**：
> 它看起来只是"重启一下 docker"，实际会**把机器上所有容器全部重启**。
> 装/改 docker 配置（如镜像加速）后确实需要它 —— **做这件事之前必须先问清楚**，
> 并且选一个影响最小的时机（见 §0.5）。

## 0.3 每个危险动作前必须有的检查

| 动作 | 必须先做的检查 | 不通过怎么办 |
|---|---|---|
| 启动 Nginx 容器（占 80/443） | `ss -lntp \| grep -E ':(80\|443)\b'` | 换端口（改 `.env`），或与占位方协商 |
| 启动 MySQL 容器（占内存） | `free -h` 剩余内存 ≥ 1.5G | 加内存上限 / 调小 buffer pool / 暂缓 |
| 初始化数据库（写磁盘） | `df -h /` 剩余 ≥ 5G | 先清自己的旧构建产物，**不碰别人的** |
| 装 docker / 改 daemon.json | 先问：会不会影响别人？ | 与服务器所有者确认时间窗口 |

## 0.4 变更前必须先留"基线快照"（一条命令，成本极低）

```bash
# 出问题时的对照依据：改之前长什么样
{ date; ss -lntp; echo "---"; docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'; echo "---"; free -h; } \
  | tee /tmp/wxpush-baseline-before.txt
```

> 万一出问题，把 `wxpush-baseline-before.txt` 与出问题后的状态一对比，**立刻能判断是不是我们造成的**。

## 0.5 需要重启 docker 守护进程时（唯一被允许的流程）

1. **先问服务器所有者**（你哥）—— 这是别人的机器
2. **先记录 `docker ps`**，写下当前在跑的容器
3. 选**低峰时段**执行
4. 执行后**立刻核对**：`docker ps` 里原来那些容器**是否都回来了**
5. 若有容器没起来，**第一时间通知对方**，不要试图自己乱修

---

# 一、部署环境实情（决定方案的事实）

| 项 | 事实 | 影响 |
|---|---|---|
| 公网 / **出口** IP | **`121.41.167.211`**（`curl ifconfig.me` 实测，阿里云杭州节点） | ⚠️ 与截图里读到的 `.221` **不一致** → **微信 IP 白名单以出口 IP `.211` 为准**，见 §七 |
| 主机名 | `iZszudh5fw0oagZ` | 阿里云默认命名 |
| 访问入口 | **Next Terminal 堡垒机（`:8088`，另占 `:2022`）** | 它是**登录入口**，不是业务服务；**别动它** |
| 登录身份 | `root`，且 **`sudo` 免密可用** | shell 与 sudo 都有完整权限 |
| ⚠️ 机器性质 | **共享机器** —— 除堡垒机外还跑着 `v2fly` / `redis-dev` / `login-demo-mysql` / `postgresql` / `guacd` | **§零 红线全部由此而来** |
| ⚠️ 控制台权限 | **云泽拿不到**（服务器是他哥买的） | 不能改安全组、不能重启实例、不能买 RDS/快照、不能备案 |
| ⚠️ **配置规格** | **2 核 / 1.6 GiB 内存 / 无 swap / 系统盘 40G（已用 20G，剩 19G）** | **🔴 见 1.3 —— 资源不足，原方案跑不动** |
| ⚠️ **内存余量** | **available 仅 457 Mi**（used 1.1 Gi，`Swap: 0B`） | OOM 风险极高 |
| 已被占端口 | `:3306`（login-demo-mysql）、`:6379`（redis-dev）、`:2022` `:8088`（next-terminal）、`:5432`/`:4822`（容器内部） | 我们的服务**不能占这些** |
| ✅ 可用端口 | **`80` / `443` / `8080` 实测空闲**（`ss -lntp` 无监听） | Nginx 可按原计划用 80/443 |
| 微信侧硬需求 | 回调 URL **只认 80 / 443** | 80/443 空闲 → **这条不阻塞** |

### 1.1 只有服务器所有者能做的事（必须求助）

| # | 事项 | 为什么必须 |
|---|---|---|
| 1 | **安全组放行 80 / 443** | 安全组是阿里云控制台的网络层防火墙。没放行 → 我们的服务起了**也从公网访问不到** |
| 2 | **ICP 备案**（若要用域名） | 大陆节点未备案域名走 80/443 会被阿里云拦截 |
| 3 | 快照 / 备份策略 | 数据在共享机器上，快照是最后一道保险 |
| 4 | 装 docker 需要重启守护进程时 | 见 §0.5 |

### 1.2 📋 实测基线快照（2026-09-22 20:38 · 红线 R5）

```text
# ss -lntp | grep -E ':(80|443|8080|3306)\s'
LISTEN 0 4096    0.0.0.0:3306   0.0.0.0:*   users:(("docker-proxy",pid=2170,fd=7))
LISTEN 0 4096       [::]:3306      [::]:*   users:(("docker-proxy",pid=2182,fd=7))
# → 只匹配到 3306；80 / 443 / 8080 均无监听 = 空闲

# docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
NAMES               IMAGE                                                          PORTS
v2fly               v2fly/v2fly-core                                               （无映射）
redis-dev           redis:latest                                                   6379->6379
login-demo-mysql    mysql:8.0                                                      3306->3306, 33060
next-terminal       .../next-terminal:latest                                       2022, 8088
postgresql          .../dushixiang/postgres:16.4                                   5432（仅内部）
guacd               .../dushixiang/guacd:latest                                    4822（仅内部）

# free -h
               total        used        free      shared  buff/cache   available
Mem:           1.6Gi       1.1Gi       112Mi        41Mi        535Mi       457Mi
Swap:             0B          0B          0B                                  ← ⚠️ 无 swap

# df -h /    → /dev/vda3  40G  已用 20G  剩 19G  52%
# nproc     → 2
# sudo      → 免密可用
# 出口 IP   → 121.41.167.211
```

**三条读法（都很关键）**

1. **`available` 只有 457 Mi，且完全没有 swap** —— 本次预检**最重要**的数字，见 1.3。
2. **`docker ps` 的 PORTS 列为空 ≠ 没监听端口**。`v2fly` 没有端口映射，但它是代理程序，极可能用
   `network_mode: host` **直接占用宿主机端口** → **判断端口是否空闲一律以 `ss -lntp` 为准，不看 `docker ps`**。
3. **`3306` 已被别人占用**（`login-demo-mysql`，`mysql:8.0`）→ 我们**绝不能**再占 3306，也**不去动它**。
   容器的命名与云泽的练习项目同名，**归属待确认**（见对话）。

### 1.3 🔴 资源判定：**按原方案（MySQL + 后端 + Nginx 全上这台机）跑不动**

| 组件 | 常驻内存（保守估计） |
|---|---|
| MySQL 8 容器（官方最低要求本身就 ≥ 1G） | 350–450 MB |
| Spring Boot（即便 `mem_limit=400m` + `MaxRAMPercentage=60`） | ≈ 350 MB |
| Nginx | 10–20 MB |
| **需要合计** | **≈ 715–820 MB** |
| **现有可用** | **457 MB** |
| **缺口** | **≈ 260–360 MB** |

**结论**：缺口在三成以上，而且**没有 swap 兜底**。内存一旦吃满，Linux 的 **OOM Killer 会挑一个进程杀掉**
—— 被杀的很可能是 `v2fly` 或 `next-terminal`（**别人的服务**）。**这正是红线 R2 要禁止的事。**

> ⚠️ **因此本轮不写 `docker-compose.yml` 那一套配置**（写了就是往坑里跳）。
> 必须先定方向（见对话中的选项），确定「哪台机器、哪几个组件」之后再写。

---

# 二、架构：同域部署

```
                互联网 / 微信服务器（mp.weixin.qq.com）
                             │  80 (HTTP) / 443 (HTTPS)
                             ▼
             ┌───────────────────────────────────────┐
             │   Nginx 容器  wxpush-nginx             │  ← 唯一对外入口
             │                                        │
             │   /wx   → backend:8080   微信回调        │
             │   /api  → backend:8080   管理接口        │
             │   /     → /usr/share/nginx/html  SPA    │
             └────────────────┬──────────────────────┘
                              │ docker 内部网络 wxpush-net（不对外）
                              ▼
                  ┌────────────────────────┐
                  │  Spring Boot  wxpush-backend │
                  │  （8080 不发布到宿主机公网）    │
                  └───────────┬────────────┘
                              │
              ┌───────────────┴────────────────┐
              ▼                                ▼
   ┌────────────────────┐        微信 API（access_token / 菜单）
   │ MySQL  wxpush-mysql │        → 需把「出口公网 IP」加白名单
   │ （3306 不发布）      │
   └────────────────────┘
```

## 2.1 三个关键设计决策

| 决策 | 取值 | 为什么 |
|---|---|---|
| **同域**（admin 前端与后端同域） | Nginx 一个入口统一分发 | Cookie 走默认 `SameSite=Lax` 就够、**完全不触发跨域** → **后端一行配置都不用改**（与 `vite.config.js` 里"同源省掉 CORS"的注释在生产同样成立） |
| **后端 8080 不对外** | 容器内监听，不 `ports:` 发布 | 后端没有自己的鉴权外壳（`/wx` 靠签名、`/api` 靠 Session），直接暴露只增加攻击面 |
| **MySQL 3306 不对外** | 只在 `wxpush-net` 内可达 | 数据库绝不该有公网入口 |

> 📌 本项目**不需要 Redis**：`access_token` 走进程内单例 + 双检锁（见 `docs/自定义菜单-方案.md` §三）。
> 长期记忆里"MySQL + Redis"的描述已过时，此处修正。

---

# 三、交付物清单（`deploy/` 下要写的东西）

| 文件 | 作用 | 状态 |
|---|---|---|
| `docs/deploy-部署方案.md` | 本文件 | ✅ 本文件 |
| `docs/deploy-检测清单.md` | 部署后逐项验证（命令 + 期望 + 排错） | ⬜ |
| `deploy/scripts/preflight-check.sh` | **红线的可执行落地**：检查端口/资源/容器，不通过就**拒绝继续** | ⬜ |
| `deploy/docker-compose.yml` | 三服务编排（nginx / backend / mysql） | ⬜ |
| `deploy/backend/Dockerfile` | 多阶段构建：JDK17 构建 → JRE 运行 fat jar | ⬜ |
| `deploy/nginx/nginx.conf` | 主配置（gzip、日志格式） | ⬜ |
| `deploy/nginx/conf.d/wx-push.conf` | 站点配置：`/wx`、`/api` 反代 + SPA fallback | ⬜ |
| `deploy/env.example` | 环境变量模板（**真实 `.env` 不进 Git**） | ⬜ |
| `deploy/.gitignore` | 挡 `.env` / `*.key` / `certs/` | ⬜ |
| `deploy/scripts/build-local.sh` | 本地：`./gradlew bootJar` + `npm run build` | ⬜ |
| `deploy/scripts/deploy-remote.sh` | 本地 → 服务器：rsync 上传产物与配置 | ⬜ |
| `deploy/scripts/health-check.sh` | 部署后自检（首页 / `/api` / `/wx` / 端口） | ⬜ |

---

# 四、Docker 编排要点

## 4.1 端口策略：**全部走 `.env` 变量**（这是红线 R1 的技术保障）

```yaml
# docker-compose.yml 摘录（示意）
services:
  nginx:
    ports:
      - "${HTTP_PORT:-80}:80"      # 预检发现 80 被占 → 改 .env 里的 HTTP_PORT，不动代码
      - "${HTTPS_PORT:-443}:443"
```
- 端口**不写死在 compose 里**，一律引用 `.env` 的变量
- 预检脚本校验的正是这些变量指向的端口
- 发现被占用时，运维动作只是「改一行 `.env`」→ 不返工、不冲突

## 4.2 隔离四件套（红线 R3 的落地）

| 隔离项 | 做法 |
|---|---|
| **项目名** | compose 顶层 `name: wxpush` → 容器/网络自动带前缀 |
| **容器名** | 显式 `container_name: wxpush-nginx` / `wxpush-backend` / `wxpush-mysql` |
| **网络** | 独立 `wxpush-net`，不加入别人的网络 |
| **卷** | 显式 `name: wxpush-mysql-data`，与别人彻底分开 |

> 好处：出任何问题时，`docker ps` 里**一眼就能分清哪些是我们的**，
> `docker rm` 时也**不可能误伤别人**。

## 4.3 资源上限（红线 R2 的落地）

```yaml
# 每个服务都必须有，防止我们拖垮共享机器
    mem_limit: 700m
    memswap_limit: 700m      # 禁止用 swap（共享机器上 swap 被抢更伤）
```

| 服务 | 建议 `mem_limit` | 备注 |
|---|---|---|
| mysql | `700m` | 搭配 `--innodb-buffer-pool-size=256M --performance-schema=OFF` |
| backend | `700m` | 搭配 JVM `-XX:MaxRAMPercentage=70` |
| nginx | `64m` | 静态文件 + 反代，很轻 |

**JVM 参数**（通过 `JAVA_TOOL_OPTIONS` 注入，不改代码）：
```
-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8
```

## 4.4 时区与字符集（两个必踩的坑）

| 项 | 配置 | 不配的后果 |
|---|---|---|
| **时区** | mysql 与 backend 容器都设 `TZ=Asia/Shanghai` | 容器默认 UTC，`created_at` **差 8 小时** |
| **字符集** | mysql 启动参数 `--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci` | 与本地库不一致 → JOIN 报 `Illegal mix of collations` |

## 4.5 数据库初始化

项目 `schema.sql` **自带 `CREATE DATABASE wx_push` + `USE wx_push`**，因此可以直接：

```yaml
  mysql:
    volumes:
      - ./backend/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```
首次启动自动建库建表（`schema.sql` 本身是幂等的，重复执行也安全）。

> ⚠️ `/docker-entrypoint-initdb.d/` **只在数据目录为空时执行**。
> 若卷里已有数据，改 schema 要手工执行（见检测清单）。

## 4.6 依赖与自愈

```yaml
    healthcheck:                       # mysql 加健康检查
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1"]
      interval: 10s
      retries: 10
    restart: unless-stopped            # ⚠️ 用 unless-stopped，不用 always
```
- backend 用 `depends_on: { mysql: { condition: service_healthy } }`
- **`restart: unless-stopped`**：容器异常退出会自动拉起，但**我们手动停掉的不会被自动拉起**（可控）
- ⚠️ 不设 `restart: always`，避免"我们停不掉自己的容器"

---

# 五、⚠️ 必须改的一处配置（否则容器起来也连不上库）

现在 `backend/src/main/resources/application.yml` 的 JDBC 地址是**硬编码本机地址**：

```yaml
url: jdbc:mysql://localhost:3326/wx_push?...
```

容器化后 MySQL 是**另一个容器**，host 必须变成 compose 服务名 `mysql`、端口回到标准 `3306`。

## 5.1 建议改法（一行，与现有风格统一）

```yaml
datasource:
  url: ${DB_URL:jdbc:mysql://localhost:3326/wx_push?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
```

- **本地开发**：不设 `DB_URL` → 走默认值 `localhost:3326`，**行为完全不变**
- **生产**：`.env` 里设 `DB_URL=jdbc:mysql://mysql:3306/wx_push?...` 覆盖

> 与已有的 `DB_USERNAME` / `DB_PASSWORD` 写法保持一致，读起来是一套。
> **备选方案**（不改代码）：Spring Boot 松散绑定支持环境变量 `SPRING_DATASOURCE_URL` 直接覆盖，
> 但那样"配置从哪来"会分成两种风格，不如统一。

> ⚠️ **这处改动需云泽确认后再执行**（属"动已有配置文件"，按协作约定先确认）。

---

# 六、部署流程（每个危险点前都有检查）

## 步骤 0 · 预检（🔴 红线，必须先做，且不通过就中止）

```bash
cd /path/to/deploy
./scripts/preflight-check.sh          # 不通过 → 直接退出，不进入步骤 1
```

预检内容（全部只读）：

| 检查 | 命令 | 通过标准 |
|---|---|---|
| **P1 端口** | `ss -lntp \| grep -E ":($HTTP_PORT\|$HTTPS_PORT)\b"` | **输出为空** |
| P2 内存 | `free -h` | 可用 ≥ 1.5G |
| P3 磁盘 | `df -h /` | 可用 ≥ 5G |
| P4 容器 | `docker ps` | 能看到别人的容器（**用于记录，不是错误**） |
| P5 权限 | `sudo -n true` | 返回 0 |
| P6 基线 | 存 `/tmp/wxpush-baseline-before.txt` | 文件生成 |

## 步骤 1 · 本地构建

```bash
./scripts/build-local.sh
# 产出：backend/build/libs/wx-push-backend-0.0.1-SNAPSHOT.jar（约 25MB）
#       admin/dist/**（静态文件）
```

## 步骤 2 · 上传产物与配置

```bash
./scripts/deploy-remote.sh            # rsync → 服务器（不含 .env、不含密钥）
```

## 步骤 3 · 在服务器上创建 `.env`（唯一要手工填的一步）

```bash
cp env.example .env && chmod 600 .env && vi .env
# 必填 5 项：
#   DB_URL / DB_USERNAME / DB_PASSWORD / WX_TOKEN / WX_APP_ID / WX_APP_SECRET
```

> 🔴 `.env` 权限必须 `600`；**绝不进 Git**（`deploy/.gitignore` 已挡）。

## 步骤 4 · 启动（先只起 mysql，再起其余）

```bash
docker compose --env-file .env up -d mysql
docker compose --env-file .env ps            # 确认 mysql healthy
docker compose --env-file .env up -d         # 再起 nginx + backend
```

> **分两步起**：先确认 MySQL 真的健康，再起应用。
> 否则 backend 会因为连不上库反复重启，日志被刷满，反而难排查。

## 步骤 5 · 自检

```bash
./scripts/health-check.sh http://127.0.0.1
```

## 步骤 6 · 微信侧配置（三处，顺序不能乱）

见 §七。

## 步骤 7 · 出问题怎么回滚

```bash
docker compose --env-file .env down      # ⚠️ 绝不加 -v（-v 会删卷=删数据）
```
- **`down` 不加 `-v`**：容器删掉，**数据卷保留** → 修好配置再 `up` 即可
- 需要完全回退到改动前状态：把 `--env-file .env` 里的端口改回原值、`down` 掉我们的容器
- ⚠️ **永远不要为了"清干净"而用 `down -v`**

---

# 七、微信侧要改的三处（部署成功后）

| # | 位置 | 填什么 | 注意 |
|---|---|---|---|
| 1 | 公众平台 → 开发 → 基本配置 → **服务器地址(URL)** | `https://你的域名/wx` | 必须公网可达、**只认 80/443** |
| 2 | 同上 → **Token** | 与 `.env` 的 `WX_TOKEN` **逐字一致** | 不一致 → URL 验证失败 |
| 3 | 同上 → **IP 白名单** | 服务器的**公网出口 IP** | 不加 → 菜单接口报 `40164` |

> 第 3 项的 IP 用 `curl -s ifconfig.me` 查（**要在服务器上查**，不是在本机查）。
> ⚠️ 共享机器换网或更换出口时会变，变了要重新加。

---

# 八、备选路径：80/443 拿不到怎么办

微信**只认 80/443**。若这台机器上 80/443 已被占用、或协调不下来：

| 备选 | 做法 | 特点 |
|---|---|---|
| **A. 内网穿透先联调** | 在你**自己电脑**上跑后端，用 cpolar / cloudflared 拿一个临时域名指向本机 | ✅ 不需要备案、不需要服务器、立刻可联调<br>⚠️ 只适合开发期，不适合长期<br>➡️ **已采纳**，见 `docs/联调-内网穿透方案.md` |
| **B. 与占位方协商换端口** | 问服务器所有者 80/443 能否给我们 | 取决于协调 |
| **C. 自己买一台最便宜的 ECS** | 独享，端口自由、可自由重启、可做备案 | 有成本，但**彻底摆脱共享机器的所有约束** |
| **D. 借子域名** | 若他哥有已备案域名，借一个子域名 | 最省事，但需对方配合 |

> 🔴 **无论选哪条，都不允许"强行占用"别人的端口**（红线 R1）。

---

# 九、备份与恢复

| 项 | 做法 |
|---|---|
| 每日备份 | 宿主机 cron：`docker exec wxpush-mysql mysqldump ... > /path/backup/wx_push-$(date +%F).sql` |
| 保留策略 | 保留最近 7 天，**只删我们自己备份目录里的旧文件**（红线 R3） |
| 恢复 | `docker exec -i wxpush-mysql mysql -uroot -p wx_push < backup.sql` |
| 兜底 | 请服务器所有者开**阿里云自动快照** |

> ⚠️ 备份文件**不要**放在 `/var/lib/docker` 下面（那是 docker 的地盘，且会被卷占用）。

---

# 十、排错表

| 现象 | 大概率原因 | 怎么办 |
|---|---|---|
| 容器起不来，日志说端口被占 | 80/443 被别人占了 | **预检漏做了** → 改 `.env` 端口；**不要去停别人的** |
| 公网访问不了，但本机 `curl 127.0.0.1` 正常 | **安全组没放行** | 只有服务器所有者能改，去求助（§1.1） |
| 别人的服务突然挂了 | 我们吃满内存触发了 OOM Killer | **立即 `docker compose down` 我们的容器**，然后检查 `mem_limit` 是否生效 |
| 后端日志报 `Connection refused` 连数据库 | 用了 `localhost` 而不是服务名 `mysql` | 检查 `DB_URL`（§五） |
| `created_at` 差 8 小时 | 容器时区是 UTC | 检查 `TZ=Asia/Shanghai`（§4.4） |
| JOIN 报 `Illegal mix of collations` | 字符集没对齐 | 检查 mysql 启动参数（§4.4） |
| 微信 URL 验证失败 | Token 与 `WX_TOKEN` 不一致 | 逐字核对 |
| 菜单接口报 `40164` | IP 不在白名单 | 服务器上 `curl ifconfig.me` 查 IP 加白名单 |
| `docker compose down` 后数据没了 | 有人用了 `-v` | 数据卷被删。→ **记住：永远不加 `-v`** |

---

# 十一、明确不做

| 项 | 原因 |
|---|---|
| Redis | 本机单实例，`access_token` 进程内单例已正确 |
| Kubernetes / Swarm | 单机三容器，Docker Compose 足够 |
| 阿里云 RDS | 学习项目，自建容器够用且免费（但备份要自己做，见 §九） |
| CI/CD 自动部署 | 手动脚本 + 检测清单更透明，也便于学习 |
| 改 docker daemon 配置（镜像加速） | ⚠️ 需要 `systemctl restart docker` → **会影响别人**，走 §0.5 流程 |

---

_本文档是方案。按协作约定：**红线部分（§零）先记牢，配置写完交云泽逐项检测**。_
