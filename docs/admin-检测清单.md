# admin 前后端 · 检测清单

> 交付时间：2026-09-20
> 配套文档：`docs/admin-接口契约.md`（接口约定）、`docs/admin-鉴权方案.md`（下一步）
> 分工：**Aria 已跑通下面的 A 组；B 组和 C 组请你逐项检测**

---

## 零、交付了什么

### 后端新增（`backend/src/main/java/com/wxpush/admin/`）

| 文件 | 作用 |
|---|---|
| `dto/ApiResponse.java` | 统一响应外壳 `{code, message, data}` |
| `dto/PageResult.java` | 统一分页结构 `{total, page, size, list}` |
| `dto/MessageVO.java` | 消息视图对象（对外契约，不直接暴露实体） |
| `dto/OverviewVO.java` | 概览指标 |
| `dto/AdminMessageQuery.java` | 列表查询条件（绑定 URL 参数） |
| `controller/AdminMessageController.java` | 消息列表 / 详情 |
| `controller/AdminStatsController.java` | 概览 / 趋势 / 类型分布 |
| `service/AdminMessageService.java` | 入参校正 + 分页 + 实体转 VO |
| `service/AdminStatsService.java` | 统计聚合 + 趋势补零 |
| `support/GlobalExceptionHandler.java` | 全局异常 → 规范响应 |
| `support/ResourceNotFoundException.java` | 404 语义异常 |
| `repository/mapper/WxMessageStatsMapper.java` | 统计 SQL 接口 |
| `repository/dto/DailyCount.java`、`TypeCount.java` | 统计结果载体 |
| `config/WebConfig.java` | CORS（只给 `/api/**`） |

改动：`WxMessageLogMapper`（+3 方法）、`WxMessageLogMapper.xml`（+3 语句）、`mapper/WxMessageStatsMapper.xml`（新建）。

### 前端新建（`admin/`）

```
admin/
├── package.json / vite.config.js / index.html / .gitignore
└── src/
    ├── main.js / App.vue / assets/main.css
    ├── router/index.js                 路由表（登录页独立，其余共享布局）
    ├── api/request.js                  axios 封装（拆壳 + 401 处理）
    ├── api/message.js                  消息接口
    ├── api/stats.js                    统计接口
    ├── layout/AppLayout.vue            侧边栏 + 顶栏布局
    └── views/
        ├── DashboardView.vue           数据看板（6 卡片 + 折线 + 饼图）
        ├── MessageListView.vue         消息记录（筛选 + 表格 + 分页 + 详情抽屉）
        └── LoginView.vue               登录页（等后端接口）
```

---

## 一、怎么跑起来

**两个终端，各跑一条：**

```bash
# 终端 1 —— 后端（需先有 backend/application-local.yml 配好数据库密码）
cd ~/Desktop/微信公众号/wx-push/backend
SPRING_PROFILES_ACTIVE=local WX_TOKEN=wxToken123 ./gradlew bootRun

# 终端 2 —— 前端
cd ~/Desktop/微信公众号/wx-push/admin
npm run dev
```

然后浏览器打开 **http://localhost:5173**

> ⚠️ 用 `localhost` 而不是 `127.0.0.1` —— Vite 默认只监听 IPv6 的 `[::1]`，
> 走 `127.0.0.1` 会连不上。（这个坑我已踩过，见第四节。）

---

## 二、A 组 · 自动化检测（Aria 已全跑通 ✅）

| # | 项目 | 命令 | 实测结果 |
|---|---|---|---|
| A1 | 后端单元测试 | `cd backend && ./gradlew test` | ✅ **51 用例全过**（新增 25 个：服务层 15 + 接口契约 10） |
| A2 | admin 接口实测 | `cd backend && ./scripts/verify-admin-api.sh` | ✅ **21 项全过**（含分页 / 筛选 / 日期边界 / 404 / 统计口径） |
| A3 | 微信回调冒烟 | `cd backend && ./scripts/smoke-test.sh wxToken123 8080` | ✅ 11 项全过（未受本次改动影响） |
| A4 | 前端构建 | `cd admin && npm run build` | ✅ 2238 模块，3.73s，无错误 |
| A5 | 端到端联调 | 见下 | ✅ 前端经 proxy 打后端 5 个接口全部返回正确数据 |

### A5 的实测输出（最能说明「前后端一致」）

前端 dev server 启动后，用 `http://localhost:5173/api/...` 请求 —— 走的是**前端配置的那条链路**：

```
GET /api/messages?size=3      → code=0  total=5  返回 3 条（id 6/5/3，id 倒序）
GET /api/stats/overview       → totalMessages=5 todayMessages=5 subscribe=2 unsubscribe=2 netGrowth=0 activeUsers=1
GET /api/stats/trend?days=7   → 7 个点：09-14…09-19 全 0，09-20 为 5（补零生效）
GET /api/stats/type-dist          → [('event',4), ('text',1)]
GET /api/messages/999         → HTTP 404 + code=404 + "消息不存在：id=999"
```

这些数字与数据库里那 5 行真实数据**完全吻合**。

---

## 三、B 组 · 请你自己跑的检测

| # | 检测项 | 命令 / 操作 | 期望 |
|---|---|---|---|
| B1 | 一键跑全部后端验证 | `cd backend && ./scripts/verify-admin-api.sh` | 末尾显示「通过 21 项，失败 0 项」 |
| B2 | 跑测试 | `cd backend && ./gradlew test` | BUILD SUCCESSFUL，51 用例 |
| B3 | 打开页面看数据 | 两个终端启动后访问 `http://localhost:5173` | 看板 6 个卡片有数字，折线图有 09-20 的点，饼图 event=4/text=1 |
| B4 | 消息列表翻页 / 筛选 | 页面上筛选「事件类型 = 关注」 | 表格只剩 2 条 subscribe |
| B5 | 点「详情」 | 点表格任意行的详情 | 右侧抽屉展示完整字段，`msgId` 显示「— （事件消息没有此字段）」 |
| B6 | 前端构建 | `cd admin && npm run build` | 产物输出到 `admin/dist/` |

---

## 四、C 组 · 代码走读（值得花时间的地方）

这几个问题**只有走读代码才能确认设计对不对**，自动化测不出来：

| # | 看哪个文件 | 回答这个问题 |
|---|---|---|
| C1 | `GlobalExceptionHandler.java` | 为什么类上必须写 `basePackages = "com.wxpush.admin"`？去掉会有什么后果？（提示：想想 `/wx` 收到 JSON 会怎样） |
| C2 | `WxMessageLogMapper.xml` 的 `Condition_Where` | 为什么把 WHERE 抽成 `<sql>` 片段给列表和计数共用？写两份会出什么问题？ |
| C3 | `AdminMessageService.page()` | 为什么 `endDate` 要 `plusDays(1)`？直接 `<= endDate` 会漏掉什么？ |
| C4 | `AdminStatsService.trend()` | 为什么要在 Java 里补零？不补的话 ECharts 会画成什么样？ |
| C5 | `WxMessageStatsMapper.xml` 的 `selectDailyCount` | `\`date\`` 和 `\`value\`` 为什么必须加反引号？ |
| C6 | `WxMessageLogMapper.xml` 的 `selectPage` | 为什么 `ORDER BY id DESC` 而不是 `created_at DESC`？ |
| C7 | `admin/src/api/request.js` | 响应拦截器里那行 `return body.data` 省掉了什么？为什么要在这一层做？ |
| C8 | `admin/vite.config.js` | proxy 的 target 为什么写 `127.0.0.1` 而不是 `localhost`？ |
| C9 | `WebConfig.java` | 为什么 `allowCredentials(true)` 不能配 `allowedOrigins("*")`？ |
| C10 | `DashboardView.vue` | 为什么图表实例用 `shallowRef` 而不是 `ref`？为什么 `onBeforeUnmount` 要 `dispose()`？ |

---

## 五、已验证 / 未验证（如实清单）

### ✅ 已验证

| 项 | 证据 |
|---|---|
| 后端编译 | `./gradlew build` 通过 |
| 后端单元测试 | 51 用例全过（7 个测试类） |
| 5 个接口的真实数据 | 21 项断言全过，数字与库内 5 行吻合 |
| 分页边界 | `page=0&size=9999` 被校正为 `page=1&size=100` |
| 日期区间边界 | 9-20 当天含全部 5 条；起点设 9-21 则为 0 |
| 404 契约 | HTTP 404 + `code=404` + 中文 message |
| 趋势补零 | 返回 7 个点，无数据的日期为 0 |
| 前端构建 | 2238 模块编译通过，无错误 |
| 前端 → 后端链路 | 经 Vite proxy 打通 5 个接口 |
| CORS 配置 | `/api/**` 返回正确的 CORS 头；`/wx` **无** CORS 头 |
| 密码未进命令行 | 改用 `application-local.yml`（已 gitignore） |

### ⚠️ 未验证（不隐瞒）

| 项 | 原因 |
|---|---|
| **浏览器里的实际渲染效果** | 我没在真实浏览器中点过页面。构建通过 + 数据链路通，但 UI 布局、图表样式、交互细节需要你打开看 |
| **前端筛选 / 翻页的交互** | 逻辑代码已写，但没有在浏览器里实际点过 |
| **ECharts 图表的视觉效果** | 数据格式已确认能对上，但实际画出来什么样没见过 |
| **登录功能** | 后端 `/api/auth/login` **未实现**（需先建 `admin_user` 表，DDL 待确认） |
| **admin 鉴权** | 同上 —— 目前所有 `/api/**` 接口**匿名可访问** |
| **生产构建产物部署** | `admin/dist` 已生成，但 Nginx 托管配置未做 |

> ⚠️ **安全提醒**：当前 `/api/**` 没有任何鉴权，本机开发无妨，**部署到公网前必须做完鉴权**。

---

## 六、已知问题

| # | 问题 | 影响 | 状态 |
|---|---|---|---|
| 1 | `admin/dist/assets/DashboardView-*.js` 约 **1 MB** | 首屏加载偏慢（主要是 ECharts 全量引入） | 可优化：改用按需引入，或把 echarts 单独拆 chunk。当前不影响功能 |
| 2 | Vite 默认只监听 IPv6 `[::1]`，`127.0.0.1` 打不通 | 若用 `127.0.0.1:5173` 访问会失败 | 已写进文档；用 `localhost` 即可 |
| 3 | `/api/**` 无鉴权 | 数据裸奔 | 待做（`docs/admin-鉴权方案.md`） |
| 4 | P0 遗留三项未修 | stderr 裸输出 / collation / 事件幂等 | 见 `docs/P0-遗留问题-改法问答.md`，待你批准 |

---

## 七、待你决策

| # | 事项 | 说明 |
|---|---|---|
| 1 | **P0 遗留三项**执行顺序 | 建议 1 → 2 → 3（见 `P0-遗留问题-改法问答.md`） |
| 2 | **建 `admin_user` 表** | DDL 见 `docs/admin-鉴权方案.md` 第三节，**要动数据库，等你确认** |
| 3 | **Session 存哪** | 内存（开发够用）/ Redis（为多实例铺路） |
| 4 | **`h5/` 定位** | 纯展示 / 后置 / 砍掉（见 CODEBUDDY.md 2.1） |
| 5 | **域名备案** | 整条链周期最长的前置项，建议尽早启动 |
