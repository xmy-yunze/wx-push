# admin 鉴权方案 · wx-push

> 状态：**方案已定，代码未写，DDL 未执行** —— 等你确认后再动手
> 目标：给 `admin/` 管理后台加上登录鉴权，让 `/api/**` 不再裸奔

---

## 一、为什么必须做

`admin/` 目前所有接口都是**匿名可读**的。只要知道地址，任何人都能拉到全部消息流水 ——
包括用户 openid 和聊天内容。这在开发期无所谓，一旦部署到公网就是数据泄露。

所以这件事的优先级不在「功能」，在「安全底线」。

---

## 二、方案选型：Session + Cookie

| 维度 | Session + Cookie（选定） | JWT |
|---|---|---|
| 服务端状态 | 有（内存 / Redis） | 无 |
| 登出 | 直接销毁 Session，**立即失效** | 需要额外维护黑名单，否则 token 到期前一直有效 |
| 多实例部署 | 需要把 Session 存 Redis（项目本来就要上 Redis） | 天然支持 |
| 代码量 | 少：一个登录接口 + 一个拦截器 | 多：签发、校验、过期、刷新四件事 |
| 前端配合 | `withCredentials: true`（**已配好**） | 需要自己管 token 存取 |

对本项目（单后台、运营者自用、同域部署），Session 是成本最低且登出语义最干净的选择。

---

## 三、数据库变更（⚠️ 待你确认后才执行）

**新建一张表**，不改动任何现有表：

```sql
USE wx_push;

CREATE TABLE IF NOT EXISTS admin_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)     NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(200)    NOT NULL COMMENT '密码哈希（PBKDF2 格式，见第五节）',
    display_name  VARCHAR(64)     DEFAULT NULL COMMENT '显示名',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1=启用，0=禁用',
    last_login_at DATETIME        DEFAULT NULL COMMENT '最后一次登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '管理后台账号';
```

**几个刻意的设计点**：

| 点 | 原因 |
|---|---|
| 显式写 `COLLATE` | 上一轮发现的坑：不写 collation 会回落到字符集固有默认，导致和 `wx_message_log` 的库级设置不一致，将来 JOIN 会报 `Illegal mix of collations` |
| `password_hash` 留 200 字符 | 不存明文、不存可逆加密。长度留足余量，将来换算法不用改表 |
| `status` 而不是直接删号 | 运营账号出问题时应能停用而非物理删除，保留审计线索 |
| `uk_username` 唯一索引 | 从数据库层面保证账号不重复，而不是靠应用层先查后插（那个有并发竞态） |

---

## 四、密码怎么存

**绝不存明文，也不存 MD5/SHA1** —— 后者对撞库攻击几乎不设防。

选定 **PBKDF2-HMAC-SHA256**，理由：

- **JDK 自带**（`javax.crypto.SecretKeyFactory`），零额外依赖 —— 不必为了一个哈希函数引入整个 Spring Security；
- 加盐 + 高迭代次数，天然抗彩虹表和暴力破解；
- 相比 BCrypt 需要额外引库，PBKDF2 在「够安全 + 零依赖」之间是最优解。

存储格式（自描述，方便将来调参或换算法）：

```
pbkdf2$310000$<salt的Base64>$<hash的Base64>
```

各段含义：算法标识 `$` 迭代次数 `$` 盐 `$` 哈希值。

> 校验时从字符串里读出迭代次数和盐，所以**将来调高迭代次数不会让老密码失效** ——
> 这是把参数写进存储格式的价值。

### 生成哈希的工具

`backend/scripts/GeneratePasswordHash.java` —— 用 JDK 17 的单文件源码运行能力，一条命令生成：

```bash
cd backend
java scripts/GeneratePasswordHash.java 你想设的密码
```

输出形如 `pbkdf2$310000$xxxx$yyyy`，再手工插进 `admin_user` 表即可。

**这样设计的原因**：你不需要把密码告诉我，也不需要我生成 ——
密码从头到尾只经过你自己的终端。

---

## 五、接口与拦截器设计

### 5.1 三个接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | 入参 `{ username, password }`；成功返回 `{ id, username, displayName }`，同时写入 Session |
| `POST` | `/api/auth/logout` | 销毁 Session，返回 `code: 0` |
| `GET` | `/api/auth/me` | 返回当前登录用户；未登录返回 `code: 401` |

### 5.2 拦截器

一个 `LoginInterceptor` 挂在 `/api/**`，白名单放行：

- `/api/auth/login` —— 登录本身当然不能要求先登录
- `/api/auth/logout` —— 未登录时登出应幂等成功

拦截后：

- 已登录 → 放行
- 未登录 → 返回 `401` + `{ code: 401, message: "未登录或登录已过期" }`

> ⚠️ **绝不能拦截 `/wx`** —— 那是微信服务器调用的，微信不带任何 Cookie，
> 拦住它会直接导致公众号功能全线失效。

### 5.3 前端要动的地方

| 文件 | 改动 |
|---|---|
| `src/api/request.js` | 401 分支里补上 `router.push('/login')`（目前是注释掉的 TODO） |
| `src/router/index.js` | 加全局前置守卫：未登录访问非 `/login` 页面时跳登录页 |
| `src/views/LoginView.vue` | **不用改** —— 已按契约实现，接口一上线即可跑通 |

---

## 六、待决策清单

| # | 事项 | 选项 |
|---|---|---|
| 1 | **建 `admin_user` 表** | ① 执行上面的 DDL ② 先不建 |
| 2 | **账号怎么初始化** | ① 你用自己的密码生成哈希后手工 INSERT ② 我提供一个初始化 SQL（需你给出哈希） |
| 3 | **Session 存哪** | ① 先用内存（开发期够用） ② 直接上 Redis（为多实例部署提前铺路） |
| 4 | **会话有效期** | 默认 30 分钟空闲超时，可调 |

这四项定了我就开始写代码。按老规矩：**先出方案 → 你确认 → 我写 → 你检测**。

---

_本文档与 `docs/admin-接口契约.md` 第四节配套阅读。_
