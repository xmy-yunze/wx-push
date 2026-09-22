package com.wxpush.admin;

import com.wxpush.admin.controller.AdminMenuController;
import com.wxpush.admin.dto.SessionUser;
import com.wxpush.admin.service.AuthService;
import com.wxpush.admin.support.GlobalExceptionHandler;
import com.wxpush.admin.support.LoginInterceptor;
import com.wxpush.gateway.AccessTokenManager;
import com.wxpush.gateway.WxApiException;
import com.wxpush.service.MenuService;
import com.wxpush.support.StubWxApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜单接口的 HTTP 契约测试。
 *
 * <p>测四件事：</p>
 * <ol>
 *   <li>接口确实被<b>登录门禁</b>保护（未登录 401）；</li>
 *   <li>三个端点的入参 / 出参字段名稳定（前端已按契约实现）；</li>
 *   <li><b>非法菜单在到达微信之前就被拒掉</b>，且错误提示是人话；</li>
 *   <li>微信报错时，错误码与可读提示能透传到前端（运营者靠这个自救）。</li>
 * </ol>
 *
 * <p>standalone 模式：不启 Spring 容器、不连数据库、不连微信 —— 毫秒级跑完。</p>
 */
@DisplayName("菜单接口契约")
class AdminMenuApiContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 一个合法菜单：1 个叶子 + 1 个含 1 个二级的容器 */
    private static final String VALID_MENU = """
            {"button":[
              {"type":"click","name":"关于我","key":"MENU_ABOUT"},
              {"name":"更多","sub_button":[
                {"type":"click","name":"怎么用","key":"MENU_HOWTO"}
              ]}
            ]}""";

    private StubWxApiClient api;
    private MockMvc mockMvc;
    private MockHttpSession loggedInSession;

    @BeforeEach
    void setUp() {
        api = new StubWxApiClient();
        api.setFetchDelayMillis(0);
        MenuService menuService = new MenuService(
                new AccessTokenManager(api), api, OBJECT_MAPPER);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminMenuController(menuService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new LoginInterceptor())
                .build();

        loggedInSession = new MockHttpSession();
        loggedInSession.setAttribute(AuthService.SESSION_USER_KEY,
                new SessionUser(1L, "admin", "云泽"));
    }

    // ==================== 门禁 ====================

    @Test
    @DisplayName("未登录访问 /api/menu → 401")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/menu").contentType(MediaType.APPLICATION_JSON).content(VALID_MENU))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/menu"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("GET /api/menu → 返回微信原始结构（menu 节点）")
    void getCurrentMenu() throws Exception {
        api.setMenuResponse("{\"menu\":{\"button\":[{\"type\":\"click\",\"name\":\"关于我\"}]}}");

        mockMvc.perform(get("/api/menu").session(loggedInSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.menu.button[0].name").value("关于我"));
    }

    // ==================== 发布 ====================

    @Test
    @DisplayName("POST /api/menu?dryRun=true → 只回显报文，不调用微信")
    void dryRunDoesNotCallWechat() throws Exception {
        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .param("dryRun", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MENU))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.payloadJson", containsString("关于我")));

        org.junit.jupiter.api.Assertions.assertEquals(0, api.createMenuCalls(),
                "干跑绝不该调用微信");
    }

    @Test
    @DisplayName("POST /api/menu → 真发布，调用微信一次")
    void publishCallsWechat() throws Exception {
        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MENU))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.published").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(1, api.createMenuCalls());
    }

    @Test
    @DisplayName("一级菜单超过 3 个 → 400，提示说清上限（不让微信来报 40054）")
    void rejectsTooManyTopLevel() throws Exception {
        String tooMany = """
                {"button":[
                  {"type":"click","name":"A","key":"K1"},
                  {"type":"click","name":"B","key":"K2"},
                  {"type":"click","name":"C","key":"K3"},
                  {"type":"click","name":"D","key":"K4"}
                ]}""";

        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("最多允许 3 个")));

        org.junit.jupiter.api.Assertions.assertEquals(0, api.createMenuCalls(),
                "非法菜单绝不能发到微信");
    }

    @Test
    @DisplayName("三级嵌套 → 400（微信只有两级）")
    void rejectsNestedTooDeep() throws Exception {
        String deep = """
                {"button":[{"name":"一级","sub_button":[
                  {"name":"二级","sub_button":[{"type":"click","name":"三级","key":"K"}]}
                ]}]}""";

        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("只支持两级")));
    }

    @Test
    @DisplayName("未知菜单类型 → 400")
    void rejectsUnknownType() throws Exception {
        String unknown = """
                {"button":[{"type":"miniprogram","name":"小程序","key":"K"}]}""";

        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("类型不被支持")));
    }

    @Test
    @DisplayName("缺少 button 字段 → 400")
    void rejectsMissingButton() throws Exception {
        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menu\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("button")));
    }

    // ==================== 微信侧错误 ====================

    @Test
    @DisplayName("微信报 40164（IP 白名单）→ 502，并把可读提示透传给前端")
    void surfacesIpWhitelistHint() throws Exception {
        api.queueGetMenuFailure(new WxApiException(40164, "invalid ip 1.2.3.4",
                "调用来源 IP 不在公众号后台的白名单内"));

        mockMvc.perform(get("/api/menu").session(loggedInSession))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message", containsString("白名单")))
                .andExpect(jsonPath("$.message", containsString("40164")));
    }

    @Test
    @DisplayName("微信报 45058（外链）→ 502，提示里带原始错误码")
    void surfacesInvalidUrlDomain() throws Exception {
        api.queueCreateMenuFailure(new WxApiException(45058, "invalid url domain",
                "菜单里含有外部网址"));

        mockMvc.perform(post("/api/menu")
                        .session(loggedInSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MENU))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message", containsString("45058")));
    }

    // ==================== 清空 ====================

    @Test
    @DisplayName("DELETE /api/menu → 调用微信删除接口")
    void clearMenu() throws Exception {
        mockMvc.perform(delete("/api/menu").session(loggedInSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        org.junit.jupiter.api.Assertions.assertEquals(1, api.deleteMenuCalls());
    }
}
