# admin 接口契约 · wx-push

> **本文件是前后端一致性的唯一依据。**
> 前端 `admin/` 严格按本契约调用，后端 `backend/` 严格按本契约实现。任一方要改字段，先改本文件。
>
> 最后更新：2026-09-20 ｜ 状态：消息与统计两组已实现并验证，鉴权组待实现

---

## 一、通用约定

| 项 | 约定 |
|---|---|
| Base URL（开发期） | `http://localhost:8080`，前端通过 Vite proxy 用 `/api` 前缀转发 |
| Base URL（生产） | `https://你的域名`，前端静态资源与后端同域，由 Nginx 分流 |
| 数据格式 | 请求与响应均为 `application/json`，`UTF-8` |
| 时间格式 | 统一 `yyyy-MM-dd HH:mm:ss`（如 `2026-09-20 21:55:09`） |
| 日期参数格式 | 统一 `yyyy-MM-dd`（如 `2026-09-20`） |
| 鉴权方式 | **Session + Cookie**（`JSESSIONID`），前端 axios 需开 `withCredentials` |

### 1.1 统一响应结构

**所有** `/api/**` 接口都返回这个外壳，无一例外：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `0` = 成功；非 0 = 失败 |
| `message` | string | 成功固定为 `"ok"`；失败时是可直接展示给运营者的中文提示 |
| `data` | object / array / null | 业务数据；失败时恒为 `null` |

**前端处理策略**：先判断 `code === 0`，再取 `data`；否则用 `message` 弹 `ElMessage.error`。

### 1.2 错误码表

| code | HTTP 状态码 | 含义 | 前端应对 |
|---|---|---|---|
| `0` | 200 | 成功 | 正常取 `data` |
| `400` | 400 | 参数不合法（如日期格式错、size 超限） | 提示 `message`，让用户改条件 |
| `401` | 401 | 未登录 / 登录已过期 | 跳转登录页（待实现） |
| `404` | 404 | 资源不存在（如消息 id 不存在） | 提示 `message` |
| `500` | 500 | 服务端内部错误 | 提示 `message`，让用户看后端日志 |

> ⚠️ **HTTP 状态码与业务 `code` 同时返回，两者含义一致。** 前端拦截器只需读 `code` 即可，不必额外判断 HTTP 状态。

### 1.3 分页参数约定

列表类接口统一接受：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `page` | int | `1` | 页码，从 **1** 开始；小于 1 时后端按 1 处理 |
| `size` | int | `20` | 每页条数；大于 **100** 时后端按 100 处理 |

分页响应统一为：

```json
{
  "total": 5,
  "page": 1,
  "size": 20,
  "list": []
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | 满足条件的**总记录数**（不是当前页条数） |
| `page` | int | 后端实际使用的页码（已校正） |
| `size` | int | 后端实际使用的每页条数（已校正） |
| `list` | array | 当前页数据 |

> 前端分页组件直接用 `total` 算总页数，不需要再发一次计数请求。

---

## 二、消息接口（已实现 ✅）

### 2.1 `GET /api/messages` —— 消息分页列表

**用途**：消息记录页的表格数据源。

**请求参数**（全部可选，均为 URL 查询参数）：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | int | 否 | 页码，默认 `1` |
| `size` | int | 否 | 每页条数，默认 `20`，上限 `100` |
| `msgType` | string | 否 | 消息类型精确匹配：`text` / `event` / `image` … |
| `event` | string | 否 | 事件类型精确匹配：`subscribe` / `unsubscribe` / `CLICK` … |
| `startDate` | string | 否 | 起始日期（**含**当天），格式 `yyyy-MM-dd` |
| `endDate` | string | 否 | 结束日期（**含**当天），格式 `yyyy-MM-dd` |
| `keyword` | string | 否 | 关键词，模糊匹配 `fromUser` 或 `content` |

**请求示例**：

```
GET /api/messages?page=1&size=20&msgType=event&event=subscribe
GET /api/messages?startDate=2026-09-01&endDate=2026-09-20&keyword=oUser
```

**响应 `data`**：标准分页结构，`list` 元素为**消息对象**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 5,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 1,
        "msgId": "1234567890123456",
        "fromUser": "oUserOpenid",
        "toUser": "gh_abc123",
        "msgType": "text",
        "event": null,
        "content": "你好",
        "createdAt": "2026-09-20 21:55:09"
      }
    ]
  }
}
```

**消息对象字段**：

| 字段 | 类型 | 可为 null | 说明 |
|---|---|---|---|
| `id` | number | 否 | 主键，**前端表格的 row-key 用它** |
| `msgId` | string | **是** | 微信消息 ID；**事件消息没有此字段，为 null** |
| `fromUser` | string | 否 | 发送者 openid |
| `toUser` | string | 否 | 公众号原始 ID |
| `msgType` | string | 否 | `text` / `event` / `image` … |
| `event` | string | **是** | 事件类型；非事件消息为 null |
| `content` | string | **是** | 消息内容；事件消息通常为 null |
| `createdAt` | string | 否 | 入库时间，`yyyy-MM-dd HH:mm:ss` |

**排序**：`id` 倒序（最新在前）。

> ⚠️ **前端注意**：`msgId` / `event` / `content` 三个字段**可能为 null**，表格渲染时要给占位符（如 `—`），否则会显示成空白单元格。

---

### 2.2 `GET /api/messages/{id}` —— 消息详情

**用途**：点击表格行查看完整报文。

**路径参数**：

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | number | 消息主键 |

**响应 `data`**：**单个消息对象**（字段同 2.1，**不是**分页结构）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "msgId": "1234567890123456",
    "fromUser": "oUserOpenid",
    "toUser": "gh_abc123",
    "msgType": "text",
    "event": null,
    "content": "你好",
    "createdAt": "2026-09-20 21:55:09"
  }
}
```

**错误响应**（id 不存在）：

```json
{
  "code": 404,
  "message": "消息不存在：id=999",
  "data": null
}
```

---

## 三、统计接口（已实现 ✅）

### 3.1 `GET /api/stats/overview` —— 概览指标

**用途**：数据看板顶部的 6 个数字卡片。

**请求参数**：无。

**响应 `data`**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "totalMessages": 5,
    "todayMessages": 5,
    "totalSubscribe": 2,
    "totalUnsubscribe": 2,
    "netGrowth": 0,
    "activeUsers": 1
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `totalMessages` | number | 消息总条数 |
| `todayMessages` | number | 今日消息条数（按服务器本地日期 00:00 起算） |
| `totalSubscribe` | number | 累计关注次数 |
| `totalUnsubscribe` | number | 累计取关次数 |
| `netGrowth` | number | 净增关注 = 关注 − 取关，**可能为负** |
| `activeUsers` | number | 去重活跃用户数（按 openid 去重） |

> ⚠️ **口径说明**：`totalSubscribe` 是「关注**次数**」不是「当前粉丝数」——因为这张表是消息流水，同一个人多次关注会被计多次。真正的粉丝数需要单独的用户表（尚未建）。

---

### 3.2 `GET /api/stats/trend` —— 按天趋势

**用途**：数据看板的折线图。

**请求参数**：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `days` | int | `7` | 统计最近多少天（**含今天**），上限 `90` |

**响应 `data`**：数组，**按日期升序连续排列**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "date": "2026-09-14", "total": 0, "subscribe": 0, "unsubscribe": 0 },
    { "date": "2026-09-15", "total": 0, "subscribe": 0, "unsubscribe": 0 },
    { "date": "2026-09-20", "total": 5, "subscribe": 2, "unsubscribe": 2 }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `date` | string | 日期，`yyyy-MM-dd` |
| `total` | number | 当天消息总数 |
| `subscribe` | number | 当天关注次数 |
| `unsubscribe` | number | 当天取关次数 |

> ✅ **后端已补零**：没有任何数据的日期也会返回，`total` / `subscribe` / `unsubscribe` 均为 `0`。
> 前端**不需要**自己补日期，直接把 `date` 数组喂给 `xAxis.data`、`total` 数组喂给 `series.data` 即可。

---

### 3.3 `GET /api/stats/type-distribution` —— 消息类型分布

**用途**：数据看板的饼图。

**请求参数**：无。

**响应 `data`**：数组，按条数倒序

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "name": "event", "value": 4 },
    { "name": "text", "value": 1 }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 类型名：`text` / `event` / `image` … |
| `value` | number | 该类型的消息条数 |

> ✅ **字段名就是 ECharts 饼图 `series.data` 的标准字段名**，前端拿到可直接赋值：
> `series[0].data = res.data`

---

## 四、鉴权接口（⬜ 待实现）

> 方案已定：**Session + Cookie**。以下为契约草案，实现后本节状态改为「已实现」。

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/auth/login` | 运营者登录 |
| `POST` | `/api/auth/logout` | 登出，销毁 Session |
| `GET` | `/api/auth/me` | 取当前登录用户信息（前端刷新页面后用它恢复登录态） |

依赖的表 `admin_user` **尚未创建**，DDL 草案见 `docs/admin-鉴权方案.md`，需确认后执行。

---

## 五、前后端一致性检查清单

前端联调时逐项核对：

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 响应外壳 | 所有接口都有 `code` / `message` / `data` 三字段 |
| 2 | 成功判定 | `code === 0`，不是 `code === 200` |
| 3 | 分页字段名 | `total` / `page` / `size` / `list`，不是 `records` / `rows` |
| 4 | 列表 vs 详情 | 列表返回分页对象，详情返回**裸对象**，两者结构不同 |
| 5 | null 字段 | `msgId` / `event` / `content` 可能为 null，需占位符 |
| 6 | 时间格式 | `2026-09-20 21:55:09`，中间是空格不是 `T` |
| 7 | 趋势补零 | `trend` 返回的日期连续，前端不要再补 |
| 8 | 饼图字段 | `name` / `value` 可直接喂 ECharts |
| 9 | 跨域凭据 | axios 开 `withCredentials: true`（Session 方案必须） |
| 10 | 错误提示 | 失败时用 `message` 弹提示，不要用 HTTP 状态码文案 |

---

_契约变更流程：改本文件 → 同步改后端 → 同步改前端 → 跑一遍第五节检查清单。_
