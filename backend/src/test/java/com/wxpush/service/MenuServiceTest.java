package com.wxpush.service;

import com.wxpush.domain.menu.MenuCatalog;
import com.wxpush.domain.menu.MenuTree;
import com.wxpush.gateway.AccessTokenManager;
import com.wxpush.gateway.WxApiException;
import com.wxpush.support.StubWxApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单服务测试。
 *
 * <p>重点验证<b>token 失效的自动重试</b>：这是最容易写成偶发故障的地方 ——
 * 40001 只在「token 被别人挤掉」时出现，本地几乎测不到，所以必须靠测试盯住。</p>
 */
@DisplayName("菜单服务")
class MenuServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StubWxApiClient api;
    private AccessTokenManager tokenManager;
    private MenuService service;

    @BeforeEach
    void setUp() {
        api = new StubWxApiClient();
        api.setFetchDelayMillis(0);
        tokenManager = new AccessTokenManager(api);
        service = new MenuService(tokenManager, api, OBJECT_MAPPER);
    }

    // ==================== 发布 ====================

    @Test
    @DisplayName("发布菜单：调用微信一次，报文含 button 数组与中文名")
    void publishSendsPayload() {
        service.publish(MenuCatalog.defaultMenu());

        assertEquals(1, api.createMenuCalls());
        String payload = api.lastMenuJson();
        assertTrue(payload.contains("\"button\""), "报文应有 button 根字段：" + payload);
        assertTrue(payload.contains("关于我"), "中文菜单名应原样发送（不该被转义）：" + payload);
        assertTrue(payload.contains("MENU_ABOUT"), "click 的 key 应在报文里：" + payload);
        assertTrue(payload.contains("\"sub_button\""), "容器应展开成 sub_button：" + payload);
    }

    @Test
    @DisplayName("发布时 access_token 失效（40001）→ 丢弃缓存、重试一次并成功")
    void publishRetriesOnceOnTokenInvalid() {
        api.queueCreateMenuFailure(new WxApiException(40001, "invalid credential", null));

        service.publish(MenuCatalog.defaultMenu());

        assertEquals(2, api.createMenuCalls(), "应重试一次");
        assertEquals(2, api.fetchCount(), "重试前必须重新拉取 token（丢弃缓存）");
    }

    @Test
    @DisplayName("重试后仍报 40001 → 抛出，且只重试一次（不无限循环）")
    void publishDoesNotRetryForever() {
        api.queueCreateMenuFailure(new WxApiException(40001, "invalid credential", null));
        api.queueCreateMenuFailure(new WxApiException(40001, "invalid credential", null));

        WxApiException e = assertThrows(WxApiException.class,
                () -> service.publish(MenuCatalog.defaultMenu()));

        assertEquals(40001, e.getErrCode());
        assertEquals(2, api.createMenuCalls(), "最多尝试两次，不能无限重试");
    }

    @Test
    @DisplayName("42001（token 过期）同样触发重试")
    void publishRetriesOnTokenExpired() {
        api.queueCreateMenuFailure(new WxApiException(42001, "access_token expired", null));

        service.publish(MenuCatalog.defaultMenu());

        assertEquals(2, api.createMenuCalls());
    }

    @Test
    @DisplayName("非 token 类错误（45058 外链）→ 不重试，直接抛出")
    void publishDoesNotRetryOnOtherErrors() {
        api.queueCreateMenuFailure(new WxApiException(45058, "invalid url domain", null));

        WxApiException e = assertThrows(WxApiException.class,
                () -> service.publish(MenuCatalog.defaultMenu()));

        assertEquals(45058, e.getErrCode());
        assertEquals(1, api.createMenuCalls(), "配置类错误重试没有意义，不该重试");
        assertTrue(e.getMessage().contains("45058"), "提示里应带上原始错误码");
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("查询菜单：返回微信原始结构（menu 节点）")
    void currentMenuReturnsRawStructure() {
        api.setMenuResponse("{\"menu\":{\"button\":[{\"type\":\"click\",\"name\":\"关于我\"}]}}");

        JsonNode result = service.currentMenu();

        assertTrue(result.has("menu"), "应保留微信的原始包装：" + result);
        assertEquals("关于我", result.path("menu").path("button").get(0).path("name").asText());
    }

    @Test
    @DisplayName("查询时微信报 40164（IP 白名单）→ 不重试，原样抛出")
    void currentMenuSurfacesIpWhitelistError() {
        api.queueGetMenuFailure(new WxApiException(40164, "invalid ip 1.2.3.4", null));

        WxApiException e = assertThrows(WxApiException.class, () -> service.currentMenu());

        assertEquals(40164, e.getErrCode());
        assertEquals(1, api.getMenuCalls(), "IP 白名单问题重试也没用");
    }

    // ==================== 清空 ====================

    @Test
    @DisplayName("清空菜单：调用删除接口一次")
    void clearCallsDelete() {
        service.clear();

        assertEquals(1, api.deleteMenuCalls());
    }

    // ==================== 干跑 ====================

    @Test
    @DisplayName("干跑：只生成报文，不发任何请求")
    void renderPayloadDoesNotCallWechat() {
        String payload = service.renderPayload(MenuCatalog.defaultMenu());

        assertTrue(payload.contains("\"button\""));
        assertEquals(0, api.createMenuCalls(), "干跑绝不能调用微信");
        assertEquals(0, api.fetchCount(), "干跑连 token 都不该取");
    }

    @Test
    @DisplayName("干跑产出的报文可被解析回等价结构（序列化闭环）")
    void renderPayloadRoundTrips() throws Exception {
        MenuTree menu = MenuCatalog.defaultMenu();

        JsonNode parsed = OBJECT_MAPPER.readTree(service.renderPayload(menu));

        assertEquals(3, parsed.path("button").size());
        assertTrue(parsed.path("button").get(1).has("sub_button"));
    }
}
