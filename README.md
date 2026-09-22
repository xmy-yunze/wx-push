# wx-push

微信公众号（个人主体订阅号）的消息接收、落库与可视化管理后台。

后端 **Spring Boot 4.0.7 + 原生 MyBatis**，前端 **Vue 3 + Element Plus + ECharts**。
这不只是"做一个功能"，主要目的是把**设计模式**落到一个有真实业务约束的项目里。

> 建议先读第一节 —— 微信侧的权限边界决定了这个项目"能做什么、不能做什么"，
> 不了解这个前提会看不懂代码里为什么缺了很多"本该有"的功能。

**当前进度**（截至 2026-09-22，本地框架功能面已闭环）：

| 模块 | 状态 |
|---|---|
| 消息接入（验签 / 明文收发 / XML 解析） | ✅ |
| 被动回复（文本回声 / 关注事件 / CLICK 菜单事件） | ✅ |
| 消息落库 + 幂等（`dedup_key`） | ✅ 代码完成 · ⬜ DDL 迁移待执行 |
| 管理后台（登录鉴权 / 数据看板 / 消息记录） | ✅ |
| 自定义菜单（增 / 删 / 查 + 干跑预览） | ✅ |
| `deploy/` 部署配置 · 微信真机联调 | ⬜ 待办（需真实环境） |
| `h5/` 关注者端 | ⬜ 定位待定（见 1.1） |

> 后端单测 **155 个全绿**（17 个测试类）；后端已实测启动、前端已实测构建。

---

## 一、项目背景与能力边界

### 1.1 账号类型带来的权限封顶

本项目对接的是**个人主体、未认证订阅号**。这个前提决定了功能上限，
且无法通过任何开发手段突破 —— 个人主体不能做微信认证：

| 能力 | 可用性 |
|---|---|
| 服务器配置（接收消息推送） | 可用 |
| 被动回复消息 | 可用 |
| 自定义菜单 | 可用（但**不能跳转外链**，会报 `45058`） |
| 客服消息 / 模板消息 | 不可用 |
| 群发 / 素材管理 / 用户管理 | 不可用 |
| 网页授权（获取 openid） | 不可用 |
| 微信支付 | 不可用 |

由此得到一个直接结论：**`h5/` 目录目前是空占位**。
未认证订阅号既不能用菜单跳转外部网页（报 `45058`），也拿不到网页授权的 openid ——
页面进不去、进去了也认不出是谁。这块要等账号主体升级后才有意义。

### 1.2 两个容易踩的协议细节

- **报文格式**：微信 → 后端（被动回调）是 **XML**，不是 JSON；
  后端 → 微信（主动调用）才是 JSON。很多教程直接贴 JSON 示例，会让人踩坑。
- **加密模式**：当前采用**明文模式**，不启用 AES，因此不需要处理 `EncodingAESKey`。
  若将来切换到安全模式，`WxXmlParser` 这一层需要改造。

---

## 二、技术栈

### 后端

| 项 | 选型 |
|---|---|
| 框架 | Spring Boot 4.0.7 |
| 语言 | Java 17（Gradle toolchain） |
| 构建 | Gradle（`build.gradle` + `gradlew`） |
| 持久层 | **原生 MyBatis + XML Mapper**（刻意不用 MyBatis-Plus） |
| 数据库 | MySQL 8 |
| 依赖管理 | 阿里云镜像优先，mavenCentral 兜底 |

> 为什么不用 MyBatis-Plus？因为本项目的目标是**练手而非提效**——
> 手写 XML 才能看清 SQL 与对象映射的全过程。MyBatis-Plus 会用它的 CRUD 封装
> 把这层细节藏起来，与学习目标冲突。

### 前端（admin 管理后台）

| 项 | 选型 |
|---|---|
| 框架 | Vue 3（组合式 API） |
| 构建 | Vite 6 |
| UI | Element Plus 2.9 |
| 图表 | ECharts 5.6 |
| 路由 | Vue Router 4 |
| 请求 | axios（统一封装 + 响应拆壳） |

---

## 三、目录结构

```
wx-push/
├── backend/                    Spring Boot 后端
│   ├── scripts/                运维脚本（冒烟测试、密码哈希生成）
│   ├── src/main/java/com/wxpush/
│   │   ├── gateway/            微信接入层：验签、XML 解析、回复构建；WxApiClient / AccessTokenManager（主动调用）
│   │   ├── dispatcher/         消息分发：按 MsgType 路由到对应处理器
│   │   ├── handler/            各类型消息处理器（文本 / 事件 / CLICK）
│   │   ├── domain/            领域对象：消息模型、回复模型、菜单树（menu/）
│   │   ├── service/            业务服务（含 MenuService 菜单门面）
│   │   ├── repository/         持久层：Entity + Mapper 接口
│   │   ├── admin/              管理后台接口：Controller / Service / DTO
│   │   └── config/             Spring 配置（CORS 等）
│   ├── src/main/resources/
│   │   ├── mapper/             MyBatis XML 映射文件
│   │   ├── db/schema.sql       表结构唯一真相源（幂等，可重复执行）
│   │   ├── db/alter-*.sql      一次性迁移脚本（升级已有库用，见 4.2）
│   │   └── application.yml     主配置（密钥全部走环境变量）
│   └── src/test/               单元测试
├── admin/                      管理后台前端
│   ├── src/views/              数据看板 / 消息记录 / 菜单管理 / 登录页
│   ├── src/api/                接口封装与 axios 拦截器
│   └── src/router/             路由表（含登录守卫）
├── h5/                         移动端（占位，见 1.1 说明）
├── deploy/                     部署与联调
│   ├── scripts/run-local.sh    本地联调启动后端（127.0.0.1:18080）
│   ├── scripts/tunnel.sh       内网穿透隧道（cpolar / cloudflared）
│   └── （Docker Compose 那套待机器确定后再写）
└── docs/                       方案与契约文档
```

---

## 四、快速开始

### 4.1 前置要求

- JDK 17
- Node.js 18+
- MySQL 8（库名 `wx_push`）

### 4.2 建库建表

`schema.sql` 是幂等的 —— 空库和已有库各执行一遍都不会报错、不丢数据。

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 \
  < backend/src/main/resources/db/schema.sql
```

> 必须带 `--default-character-set=utf8mb4`，否则中文 `COMMENT` 会存成乱码。
> 本机开发环境的 MySQL 端口是 `3326`（非默认），按你的实际环境调整，
> 同时要同步修改 `application.yml` 里的 JDBC URL。

> 📌 **升级已有的库**：`schema.sql` 用的是 `CREATE TABLE IF NOT EXISTS`，对**已存在**的表不会做任何改动。
> 因此表结构变更（例如 2026-09-22 新增的 `dedup_key` 去重键）要执行对应的一次性迁移脚本：
>
> ```bash
> mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 \
>   < backend/src/main/resources/db/alter-002-add-dedup-key.sql
> ```
>
> ⚠️ 迁移脚本**只能执行一次** —— MySQL 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`，
> 重复执行会报 `Duplicate column name`（那是预期行为，不是脚本坏了）。

### 4.3 启动后端

密钥一律**不写进代码、不进 Git**，通过环境变量或本地 profile 文件注入：

```bash
cd backend

# 方式一：环境变量
DB_PASSWORD=你的数据库密码 \
WX_TOKEN=你的微信Token \
WX_APP_ID=你的AppID \
WX_APP_SECRET=你的AppSecret \
./gradlew bootRun
```

```bash
# 方式二：本地 profile 文件（推荐，避免密码进 shell 历史）
# 在 backend/ 根目录创建 application-local.yml（已被 .gitignore 排除）
# 内容形如：
#   spring.datasource.password: 你的密码
# 然后：
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

服务默认监听 `8080`。

### 4.4 启动前端

```bash
cd admin
npm install
npm run dev     # 开发服务 → http://localhost:5173
```

Vite 已配置 proxy，把 `/api` 转发到 `http://127.0.0.1:8080`，因此前端不需要处理跨域。

> 注意用 `localhost` 而非 `127.0.0.1` 访问前端 —— Vite 默认只监听 IPv6 的 `[::1]`。

---

## 五、接口一览

### 微信回调（微信服务器调用，不带 Cookie，不可加鉴权）

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/wx` | 服务器地址验证（校验签名后原样返回 `echostr`） |
| `POST` | `/wx` | 接收消息推送，返回被动回复 XML |

### 管理后台（`/api/**`）

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/messages` | 消息分页列表（支持类型/事件/日期区间/关键词筛选） |
| `GET` | `/api/messages/{id}` | 消息详情 |
| `GET` | `/api/stats/overview` | 概览统计 |
| `GET` | `/api/stats/trend?days=7` | 按天趋势 |
| `GET` | `/api/stats/type-distribution` | 消息类型分布 |

接口的字段与返回结构以 `docs/admin-接口契约.md` 为**唯一依据** ——
前后端都以它为准，避免各写各的导致对不上。

---

## 六、设计模式落点

这是本项目的核心诉求之一，各模式都有明确的业务理由，不是为用而用：

| 模式 | 落在哪里 | 解决什么问题 |
|---|---|---|
| 适配器 | `gateway/WxXmlParser` | 把微信的 XML 报文转成内部领域对象，隔离外部协议变化 |
| 工厂 + 策略 | `dispatcher/MessageHandlerFactory` | 按 `MsgType` 动态选择处理器，替代长 `if-else` |
| 模板方法 | `handler/AbstractMessageHandler` | 固定"解析 → 处理 → 回复"骨架，子类只填差异部分 |
| 建造者 | `domain/ReplyMessage` 及 `reply/` | 回复对象字段多且组合不一，建造者让构造过程可读 |
| 责任链 | 规划中 | 日志、去重、限流等横切逻辑串成链 |

---

## 七、当前进度

> 整体概览见文首「当前进度」表。这里只列**尚未完成**的部分。

| 项 | 状态 |
|---|---|
| `deploy/` 部署配置 | ⏸️ **暂缓** —— 目标共享服务器实测**资源不足**（可用内存仅 457 Mi、无 swap），按原方案会触发 OOM 拖垮别人的服务 → `docs/deploy-部署方案.md` |
| 微信真机联调 | 🚧 改走**本机 + 内网穿透**（免服务器、免备案，立刻可做）→ `docs/联调-内网穿透方案.md` |
| 事件消息幂等 | ✅ **已修复**（改用 `dedup_key` 唯一键）· 171 单测全绿 · ⬜ 待执行一次性迁移 `db/alter-002-add-dedup-key.sql` → `docs/幂等修复-检测清单.md` |
| `h5/` 关注者端 | ⬜ 受账号权限限制，暂缓（见 1.1） |

> 管理后台**已完成鉴权**（Session + Cookie、`LoginInterceptor` 挂 `/api/**`，白名单仅 login/logout），
> 详见 `docs/admin-鉴权方案.md` 与 `docs/admin-鉴权-检测清单.md`。

---

## 八、文档索引

`docs/` 下是与代码同步维护的方案与契约文档：

| 文档 | 内容 |
|---|---|
| `微信公众号系统-技术方案.md` | 整体架构与选型 |
| `admin-接口契约.md` | 前后端接口契约（一致性唯一依据） |
| `admin-鉴权方案.md` | 鉴权方案选型与 DDL |
| `自定义菜单-方案.md` | 菜单设计：组合/建造者/适配器/单例双检锁/门面 |
| `P0-实施计划.md` | P0 阶段实施步骤 |
| `P0-检测清单.md` | P0 验证清单（命令 + 期望结果） |
| `admin-检测清单.md` | 管理后台验证清单 |
| `admin-鉴权-检测清单.md` | 鉴权端到端验证清单 |
| `自定义菜单-检测清单.md` | 菜单验证清单（含真机联调步骤） |
| `deploy-部署方案.md` | 部署方案（§零 部署红线 · 实测基线 · 资源判定） |
| `联调-内网穿透方案.md` | **本机 + 内网穿透联调方案**（不依赖服务器、免备案） |
| `联调-检测清单.md` | 联调验证清单（A 组已跑 / B 组本机 / C 组微信端） |
| `幂等修复-检测清单.md` | `dedup_key` 去重修复的迁移步骤与复验（D1–D9） |
| `P0-遗留问题-改法问答.md` | 遗留问题的成因与改法 |

---

## 九、安全约定

- 数据库密码、`AppSecret`、`Token`、`EncodingAESKey` **绝不写进代码、绝不提交进 Git**
- 密钥通过环境变量或 `application-local.yml`（已在 `.gitignore` 中排除）注入
- 日志中不打印 `access_token`、`AppSecret`、用户 openid 明文
- `access_token` 全局唯一、有效期约 2 小时，需收口到单例 + 缓存，禁止各处各刷
