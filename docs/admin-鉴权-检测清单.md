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

> ⚠️ 这里有一件**没验证**的事：全部单测都**不碰数据库**。
> 也就是说「MyBatis 能真的从 `admin_user` 读出账号」这件事，只有 B 组能证明。

---

## 二、B 组：必须你来跑（我读不到 `application-local.yml` 里的密码）

安全策略拦住了我读取含密码的文件 —— 这正是我们要的效果，密码不经我手。
所以下面三步只能你在自己终端执行。

### 第 1 步 · 建表

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
```

`schema.sql` 是**幂等**的，重复执行不报错、不丢数据。

**期望**：无输出（或只有 warning），无 ERROR。

### 第 2 步 · 核对结果

```bash
mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 -e "
SELECT SCHEMA_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='wx_push';
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='wx_push' ORDER BY TABLE_NAME;
SELECT COUNT(*) AS admin_user_rows FROM wx_push.admin_user;"
```

**期望**：

- 三行 collation **全部**是 `utf8mb4_0900_ai_ci`
- 两张表：`admin_user`、`wx_message_log`
- `admin_user_rows` = 0（还没建账号）

### 第 3 步 · 生成密码哈希

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
java scripts/GeneratePasswordHash.java '你想设的密码'
```

**期望**：输出形如 `pbkdf2$310000$xxxxxxxx$yyyyyyyy`（约 84 个字符）。

> 每次运行结果都不同 —— 盐是随机的，这是正常的（也是必须的）。

### 第 4 步 · 建账号

⚠️ **必须用交互式终端，不要写在 `-e "..."` 双引号里**：
哈希里的 `$310000` 会被 shell 当成变量展开，粘进去的哈希直接就废了。

```bash
mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 wx_push
```

进去之后：

```sql
INSERT INTO admin_user (username, password_hash, display_name)
VALUES ('admin', '把上一步生成的那一整串粘到这里', '云泽');

SELECT id, username, display_name, status, last_login_at FROM admin_user;
```

**期望**：1 行，`username=admin`，`status=1`，`last_login_at` 为 NULL。

---

## 三、C 组：接口级验证（curl）

先起后端（另开一个终端）：

```bash
cd /Users/xiongmengyao/Desktop/微信公众号/wx-push/backend
SPRING_PROFILES_ACTIVE=local WX_TOKEN=wxToken123 ./gradlew bootRun
```

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
| 登录**永远**「账号或密码错误」，但密码确定没错 | 哈希粘贴时 `$` 被 shell 展开（写进了 `-e "..."`） | 用交互式 `mysql` 重插一遍 |
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
