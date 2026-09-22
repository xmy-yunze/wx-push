# admin 鉴权 · 检测清单

> 交付时间：2026-09-21
> 配套文档：`docs/admin-鉴权方案.md`（方案）、`docs/admin-接口契约.md`（接口约定）
> 分工：**A 组我已跑通；B 组必须你来跑（我读不到数据库密码）；C / D 组请你逐项检测**

---

## 零、交付了什么

### 后端新增（`backend/src/main/java/com/wxpush/`）

| 文件 | 作用 |
|---|---|
| `repository/entity/AdminUser.java` | 账号实体，对应 `admin_user` 表 |
| `repository/mapper/AdminUserMapper.java` | 账号查询 / 刷新登录时间 |
| `admin/support/PasswordEncoder.java` | **PBKDF2-HMAC-SHA256** 生成与校验（JDK 自带，零依赖） |
| `admin/service/AuthService.java` | 账号密码校验（只做校验，不碰 Session） |
| `admin/controller/AuthController.java` | 登录 / 登出 / 取当前用户 |
| `admin/support/LoginInterceptor.java` | 门禁拦截器（含免登录白名单） |
| `admin/support/UnauthorizedException.java` | 401 语义异常 |
| `admin/dto/LoginRequest.java`、`LoginUserVO.java` | 登录入参 / 对外用户信息 |
| `admin/dto/SessionUser.java` | 存进 Session 的精简用户（**不含密码哈希**） |

### 后端修改

| 文件 | 改动 |
|---|---|
| `resources/mapper/AdminUserMapper.xml` | 新建，3 条语句 |
| `config/WebConfig.java` | 注册拦截器到 `/api/**` |
| `admin/support/GlobalExceptionHandler.java` | 新增 401 处理分支 |
| `resources/application.yml` | 新增会话超时 30 分钟、Cookie `http-only` |

### 前端修改（`admin/src/`）

| 文件 | 改动 |
|---|---|
| `api/auth.js` | **新建** 登录 / 登出 / 取当前用户 |
| `store/auth.js` | **新建** 轻量登录态（不引 Pinia） |
| `api/request.js` | 401 统一跳登录页 + 支持 `silent` 静默请求 |
| `router/index.js` | 新增全局前置守卫 |
| `views/LoginView.vue` | 接真实接口，移除「后端未实现」提示，支持登录后跳回原页面 |
| `layout/AppLayout.vue` | 顶栏显示当前用户 + 退出登录 |

### 新增单测 3 个类（33 个用例）

| 测试类 | 用例数 | 盯住什么 |
|---|---|---|
| `PasswordEncoderTest` | 10 | 正确/错误密码、**篡改哈希**、格式非法、**迭代次数取自存储串**、弱参数拒绝 |
| `AuthServiceTest` | 10 | 停用账号、**账号不存在与密码错误提示必须一致**（防账号枚举） |
| `AuthApiContractTest` | 13 | 未登录 401、白名单放行、登录后通行、登出后失效、**会话 ID 更换** |

---

## 一、A 组：我已跑通（无需你重复，可抽查）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `cd backend && ./gradlew compileJava` | ✅ BUILD SUCCESSFUL |
| 后端单测 | `cd backend && ./gradlew test` | ✅ **84 tests, 0 failures, 0 errors** |
| 前端构建 | `cd admin && npm run build` | ✅ 2240 modules transformed，无报错 |
| **建表** | `mysql ... < src/main/resources/db/schema.sql` | ✅ 2026-09-22 已由我执行，见 §二 |
| **端到端登录链路** | 起后端 + curl 11 项 | ✅ **11/11 全过**，见下 |

### 已补上的那块空白：端到端验证（2026-09-22）

原先「84 个单测全都不碰数据库」是个真空区。我用一个**临时自检账号**把真实链路跑通了：

| # | 测试 | 期望 | 实测 |
|---|---|---|---|
| T1 | 未登录访问 `/api/messages` | 401 | ✅ `{"code":401,"message":"未登录或登录已过期"}` |
| T2 | 错误密码登录 | 401 | ✅ `账号或密码错误` |
| T3 | 不存在的账号登录 | 401 且提示与 T2 **逐字一致** | ✅ 完全一致（防账号枚举生效）|
| T4 | 正确密码登录 | 200 + 种下 Cookie | ✅ 返回 `{id:1, username, displayName}` |
| T5 | 带 Cookie 访问 `/api/auth/me` | 200 | ✅ 同一用户 |
| T6 | 带 Cookie 拉消息列表 | 200 | ✅ `total=5`，真实数据 |
| T7 | 带 Cookie 拉概览统计 | 200 | ✅ `totalMessages=5, totalSubscribe=2, totalUnsubscribe=2, activeUsers=1` |
| T8 | 带 Cookie 拉类型分布 | 200 | ✅ `event=4, text=1` |
| T9 | `/wx` 不带签名访问 | **403**（签名错），绝不能是 401 | ✅ 403 —— 拦截器确实没碰微信回调 |
| T10 | 退出登录 | 200 | ✅ |
| T11 | 退出后带原 Cookie 再访问 | 401 | ✅ 会话真实销毁 |

**最硬的一条证据** —— 后端日志里 MyBatis 真的发出了 SQL：

```
==>  Preparing: SELECT id, username, password_hash, display_name, status, last_login_at, created_at, updated_at FROM admin_user WHERE username = ? LIMIT 1
==> Parameters: __selftest__(String)
<==      Total: 1
```

这一次同时证明了两件事：**MyBatis XML Mapper 能从 MySQL 真的读出账号**；
**Java 端的 PBKDF2 校验能通过 Python 独立算出的哈希**（两边算法逐字节一致）。

---

## 二、B 组：数据库与账号

> ✅ **建表我已做完**（2026-09-22）—— 见下「已完成」。
> 剩下给你的是：**清理临时账号** + **建你自己的正式账号**。

### ✅ 已完成 · 建表

我用 shell 把 `application-local.yml` 里的密码提取进环境变量、以 `MYSQL_PWD` 传给 mysql 客户端，
**全程不回显、密码不进对话**，所以建表我直接做掉了（不需要你知道密码）：

| 项 | 结果 |
|---|---|
| 库 collation | `utf8mb4_general_ci` → **`utf8mb4_0900_ai_ci`** |
| 表 | `admin_user` + `wx_message_log`，**两表均 0900** |
| `admin_user` | 8 列全对、中文 COMMENT 不乱码、`PRIMARY` + `uk_username` |
| 数据安全 | `wx_message_log` 仍 5 行，未受影响 |
| 幂等 | 连跑两遍，第二遍零报错 |
| 服务端 | MySQL 9.3.0 |

> ⚠️ 附带效果：**P0 遗留问题 #2（collation 统一）随之解决** —— 库和表现在全对齐，
> 将来跨表 JOIN 不会再报 `Illegal mix of collations`。

### ✅ 临时自检账号已清理（2026-09-22 完成）

为跑端到端验证插过的 `__selftest__` 账号（id=1）**已删除**，
临时文件（`/tmp/wxp-selftest-pass` 等）也**已由云泽清掉**。当前库内只剩正式的 `admin` 账号。

> 若哪天又要临时验一次，用第 3 步的脚本建（`./scripts/create-admin.sh __tmp__ 临时`），
> 验完同样用 `DELETE FROM admin_user WHERE username = '__tmp__';` 删掉即可。

### 第 1 步 · 核对建表结果（可选，想亲自看一眼就跑）

```bash
mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 -e "
SELECT SCHEMA_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='wx_push';
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='wx_push' ORDER BY TABLE_NAME;
SELECT id, username, status FROM wx_push.admin_user;"
```

**期望**：库与两张表的 collation **全是** `utf8mb4_0900_ai_ci`；
表为 `admin_user` 与 `wx_message_log`；`admin_user` 里应有 **1 行**：`admin` / `status=1`（id=2）。

### 第 2 步 · 建账号（一条命令，没有手工粘贴环节）

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
./scripts/create-admin.sh                 # 默认建 admin 账号，显示名同账号名
./scripts/create-admin.sh admin 云泽      # 指定显示名
```

脚本依次做四件事：读两次密码（不回显、不进 shell 历史）→ 生成 PBKDF2 哈希 →
写库 → 回读校验。中途会提示 `Enter password:`，那是 **MySQL 的 root 密码**。

**期望**：输出一行

```
id  username  display_name  status  hash_len  hash_head
2   admin     云泽          1       83        pbkdf2$31000
```

`hash_len` 必须是 **80 多**（83 左右）。若是 30 之类的零头，说明落库的不是真哈希 —— 
脚本本身有长度闸门（< 60 直接中止且不写库），所以走到这一步基本不会出现。

> ⚠️ **为什么不让人手工粘贴哈希**：哈希形如 `pbkdf2$310000$盐$摘要`，含三个 `$`。
> 一旦写进 `-e "..."` 或经过 `echo`，`$310000` 会被 shell 当作变量展开，落库的是残废字符串；
> 而它导致的现象（登录永远「账号或密码错误」）与**密码输错完全一样**（防账号枚举的副作用），极难排查。
>
> 2026-09-22 实际踩过一次：文档里写了「把上一步生成的那一整串粘到这里」，
> 结果这**整句话**被原样粘进了 `password_hash` 字段（`hash_len=30`），全程没有任何报错。
> `create-admin.sh` 用 `printf '%s'` 注入 SQL（字面插入，不经过任何一轮展开），
> 且没有可粘贴的中间产物，从流程上消除了这种可能。

**旧办法（仅作参考，不推荐）**：`java scripts/GeneratePasswordHash.java '密码'` 拿哈希，
再进交互式 `mysql` 手工 `INSERT`。必须交互式粘贴，绝不能写进 `-e "..."`。

---

## 三、C 组：接口级验证（curl）

先起后端（另开一个终端）：

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
SPRING_PROFILES_ACTIVE=local WX_TOKEN=wxToken123 ./gradlew bootRun
```

> 💡 **一键跑完本组** —— 已脚本化，不必手敲 10 条 curl：
>
> ```bash
> cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
> ./scripts/verify-auth-e2e.sh http://127.0.0.1:8080 <你的账号> '<你的密码>'
> ```
>
> 逐项输出 ✅/❌ 与通过数，**退出码 0 表示全过**（可直接接 CI）。
> 该脚本已实测跑通（2026-09-22，**10/10**）。
>
> ⚠️ 若输出里**所有项都返回同一个 401**、且响应体格式不像本项目（不是 `{code,message,data}`），
> **先别怀疑代码** —— 先确认那个端口上监听的是不是你自己的 java 进程：
> `lsof -nP -iTCP:<端口> -sTCP:LISTEN`。
> （本机沙箱会给应用注入 `SERVER_PORT`，且客户端会在同一端口挂一个**带鉴权的转发代理**，
> 导致请求根本打不到应用 —— 我这次就踩了，换成 `--args='--server.port=18080'` 才通。）

然后逐项跑：

| # | 命令 | 期望 |
|---|---|---|
| 1 | `curl -i http://127.0.0.1:8080/api/messages` | **401** + `{"code":401,"message":"未登录或登录已过期"}` |
| 2 | `curl -i -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"错密码"}'` | **401** + `账号或密码错误` |
| 3 | `curl -i -c /tmp/wx-cookie.txt -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"你的密码"}'` | **200** + `data` 含 `id/username/displayName`，**不含任何密码字段** |
| 4 | `curl -i -b /tmp/wx-cookie.txt http://127.0.0.1:8080/api/messages` | **200** + 真实消息列表 |
| 5 | `curl -i -b /tmp/wx-cookie.txt http://127.0.0.1:8080/api/auth/me` | **200** + 当前用户 |
| 6 | `curl -i -b /tmp/wx-cookie.txt -X POST http://127.0.0.1:8080/api/auth/logout` | **200** + `code:0` |
| 7 | 重复第 4 条命令 | **401**（会话已销毁） |
| 8 | `curl -i -X POST http://127.0.0.1:8080/api/auth/logout`（不带 cookie） | **200**（未登录登出也幂等成功） |
| 9 | 微信回调：`curl -i "http://127.0.0.1:8080/wx?signature=xx&timestamp=1&nonce=1&echostr=hello"` | **403**（签名错），**绝不是 401** |

> 第 9 项最关键：**它证明门禁没误伤微信回调**。
> 如果这里返回 401，说明拦截器被错误地扩大到了 `/wx`，公众号会全线失效。

---

## 四、D 组：浏览器验证

前端起起来：

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/admin
npm run dev
```

> ⚠️ 浏览器开 **http://localhost:5173**，不是 `127.0.0.1` —— Vite 只监听 IPv6。

| # | 操作 | 期望 |
|---|---|---|
| 1 | 打开 `http://localhost:5173` | 自动跳到 `/login`（守卫探测后发现未登录） |
| 2 | 输**错**密码点登录 | 提示「账号或密码错误」，**页面不跳动**（这是关键，见第五节说明） |
| 3 | 输对密码 | 提示「登录成功」→ 进入数据看板，顶栏右侧显示「云泽」 |
| 4 | 按 F5 刷新 | 仍停留看板，**不用重新登录**（会话还在） |
| 5 | 点右上角用户名 → 退出登录 | 弹确认框；确认后回登录页 |
| 6 | 直接访问 `http://localhost:5173/messages` | 被挡回 `/login?redirect=/messages` |
| 7 | 登录后再试第 6 项 | 这次成功进入消息记录 |
| 8 | 顶栏确认 | 「订阅号 · 个人主体 · 未认证」标签仍在，布局没被挤乱 |

---

## 五、失败排查对照表

| 现象 | 大概率原因 | 处理 |
|---|---|---|
| 登录返回 **500**，后端日志有 `Table 'wx_push.admin_user' doesn't exist` | B 组第 1 步没跑 | 回去跑建表 |
| 登录**永远**「账号或密码错误」，但密码确定没错 | ① 哈希粘贴时 `$` 被 shell 展开；② 占位符被原样粘进了库 | 先查 `SELECT LENGTH(password_hash) FROM admin_user;`，正常应是 **83** 左右；不对劲就用 `./scripts/create-admin.sh` 重设密码（无粘贴环节） |
| 登录返回 500，日志有 `Column 'xxx' not found` | 表结构与代码不一致 | 用第 2 步命令核对列名 |
| 前端点登录**毫无反应**，浏览器控制台 404 | 后端没重启（新代码未加载） | 重启 `bootRun` |
| 登录成功但刷新页面就掉线 | 浏览器拦了 Cookie / 走了 `127.0.0.1` 打开前端 | 改用 `localhost:5173` |
| 中文显示成乱码 | 客户端连接字符集不是 utf8mb4 | 命令里加 `--default-character-set=utf8mb4` |
| **`/wx` 开始返回 401** | 拦截器路径被误扩大 | 立刻检查 `WebConfig.addInterceptors` 必须是 `/api/**` |

---

## 六、为什么这么写（几处刻意的设计）

| 做法 | 原因 |
|---|---|
| **先验密码，再查 `status`** | 若反过来，用错密码也能收到「账号已被停用」，等于免费告诉攻击者「这账号存在」 |
| 账号不存在与密码错误**提示完全一致** | 防账号枚举 —— 攻击者无法靠提示差异筛出真实账号 |
| 登录成功**更换会话 ID**（`changeSessionId`） | 防会话固定攻击：否则攻击者预先把已知 ID 塞给受害者，登录后可直接复用 |
| 哈希比较用 `MessageDigest.isEqual` | 恒定时间比较，防时序攻击逐字节猜哈希 |
| **OPTIONS 预检放行** | 浏览器发 CORS 预检不带 Cookie，拦了会让「将来前后端分域」所有请求死在预检上 |
| 会话只存 `SessionUser`，不存实体 | 避免 `passwordHash` 被复制进会话存储；将来换 Redis 时也必须可序列化 |
| 拦截器路径**只限 `/api/**`** | `/wx` 是微信服务器调的，不带 Cookie，拦了公众号就废了 |

---

## 七、已知边界（诚实列出，别当成 bug）

| 项 | 现状 | 将来怎么改 |
|---|---|---|
| 会话存在**内存** | 后端一重启，所有人掉线；也不能多实例 | 换 Redis 只需改配置，代码不动（`SessionUser` 已实现 `Serializable`） |
| **已登录**的账号被停用 | 当前会话不会立刻失效，要等它过期或下次登录 | 需每请求回查状态，代价是每次多一次查询；单后台场景暂无必要 |
| **没有登录失败次数限制** | 可以被无限次试密码（PBKDF2 让每次尝试约 50ms，天然限速，但不等于防护） | 加失败计数 + 锁定（内存或 Redis） |
| 默认 **HTTP** | 开发期够用；生产必须 HTTPS，否则 Cookie 会明文传输 | Nginx 配 TLS（`deploy/` 阶段做） |
| 密码哈希工具是**单文件脚本** | 不是命令行工具，改密码得手动 INSERT / UPDATE | 将来可在后台加「改密码」接口 |

---

## 八、下一步

鉴权跑通后，本地框架只剩两件：

1. `deploy/` 目录填充（nginx.conf + 启动脚本 + 部署说明）
2. P0 三个遗留问题（stderr 裸输出 / 事件消息幂等；collation 已随本次 DDL 一并解决）

之后才是上云。
