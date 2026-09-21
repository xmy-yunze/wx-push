# wx-push

微信公众号（个人主体订阅号）的消息接收、落库与可视化管理后台。

后端 **Spring Boot 4.0.7 + 原生 MyBatis**，前端 **Vue 3 + Element Plus + ECharts**。
这不只是"做一个功能"，主要目的是把**设计模式**落到一个有真实业务约束的项目里。

> 建议先读第一节 —— 微信侧的权限边界决定了这个项目"能做什么、不能做什么"，
> 不了解这个前提会看不懂代码里为什么缺了很多"本该有"的功能。

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
│   │   ├── gateway/            微信接入层：验签、XML 解析、回复构建
│   │   ├── dispatcher/         消息分发：按 MsgType 路由到对应处理器
│   │   ├── handler/            各类型消息处理器（文本 / 事件）
│   │   ├── domain/            领域对象：消息模型、回复模型
│   │   ├── service/            业务服务
│   │   ├── repository/         持久层：Entity + Mapper 接口
│   │   ├── admin/              管理后台接口：Controller / Service / DTO
│   │   └── config/             Spring 配置（CORS 等）
│   ├── src/main/resources/
│   │   ├── mapper/             MyBatis XML 映射文件
│   │   ├── db/schema.sql       表结构唯一真相源（幂等，可重复执行）
│   │   └── application.yml     主配置（密钥全部走环境变量）
│   └── src/test/               单元测试
├── admin/                      管理后台前端
│   ├── src/views/              数据看板 / 消息记录 / 登录页
│   ├── src/api/                接口封装与 axios 拦截器
│   └── src/router/             路由表
├── h5/                         移动端（占位，见 1.1 说明）
├── deploy/                     部署配置（占位，规划中）
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

### 已完成

- 微信回调全链路：签名校验、XML 解析、文本回声、事件回复
- **落库幂等**：`INSERT IGNORE` + 唯一索引，微信重试推送不会产生重复记录
- 管理后台接口：消息列表 / 详情 + 3 个统计接口
- 管理后台前端：数据看板（ECharts）、消息记录、登录页
- 自动化验证：单元测试通过，另有 P0 冒烟脚本与 admin 接口校验脚本

### 待完成

| 项 | 状态 |
|---|---|
| 管理后台**鉴权** | 方案与 DDL 已定稿（`admin_user` 表结构在 `schema.sql` 中），前端登录页已就绪，后端接口待实现 |
| P0 遗留优化 | 解析器静默输出、事件消息幂等等，改法已整理成文档 |
| `deploy/` 部署配置 | Nginx 反向代理、启动脚本、部署说明 |
| 微信真机联调 | 需要内网穿透（ngrok / frp）把本机暴露为公网地址 |
| `h5/` 移动端 | 受账号权限限制，暂缓（见 1.1） |

> **重要**：管理后台接口目前**尚未鉴权**。部署到公网前必须先完成鉴权，
> 否则任何知道地址的人都能读到全部消息流水（含用户 openid 与聊天内容）。

---

## 八、文档索引

`docs/` 下是与代码同步维护的方案与契约文档：

| 文档 | 内容 |
|---|---|
| `微信公众号系统-技术方案.md` | 整体架构与选型 |
| `admin-接口契约.md` | 前后端接口契约（一致性唯一依据） |
| `admin-鉴权方案.md` | 鉴权方案选型与 DDL |
| `P0-实施计划.md` | P0 阶段实施步骤 |
| `P0-检测清单.md` | P0 验证清单（命令 + 期望结果） |
| `admin-检测清单.md` | 管理后台验证清单 |
| `P0-遗留问题-改法问答.md` | 遗留问题的成因与改法 |

---

## 九、安全约定

- 数据库密码、`AppSecret`、`Token`、`EncodingAESKey` **绝不写进代码、绝不提交进 Git**
- 密钥通过环境变量或 `application-local.yml`（已在 `.gitignore` 中排除）注入
- 日志中不打印 `access_token`、`AppSecret`、用户 openid 明文
- `access_token` 全局唯一、有效期约 2 小时，需收口到单例 + 缓存，禁止各处各刷
