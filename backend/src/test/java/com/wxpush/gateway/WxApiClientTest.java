package com.wxpush.gateway;

import com.wxpush.config.WxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 微信 API 适配器测试 —— 用假的 HTTP 服务器替掉 {@code api.weixin.qq.com}。
 *
 * <p>这里守的是最有价值的一段逻辑：<b>把微信的 errcode 翻译成运营者能照着修的提示</b>。
 * 例如 {@code 40164} 只回一句 "invalid ip" 是没用的，必须告诉人家
 * 「去公众平台把公网出口 IP 加进白名单」。这段翻译如果坏了，
 * 排查时间会从 1 分钟变成 1 小时。</p>
 */
@DisplayName("微信 API 适配器")
class WxApiClientTest {

    private static final String BASE = "https://api.weixin.qq.com/cgi-bin";

    private MockRestServiceServer server;
    private WxApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WxApiClient(builder, new ObjectMapper(),
                new WxProperties("stub-token", "wx-app-id", "wx-app-secret"));
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("拉取 access_token 成功 → 返回 token 与有效期")
    void fetchAccessTokenSuccess() {
        server.expect(requestTo(containsString("/token")))
                .andRespond(withSuccess("{\"access_token\":\"AT-123\",\"expires_in\":7200}",
                        MediaType.TEXT_PLAIN));

        WxApiClient.TokenResult result = client.fetchAccessToken();

        assertEquals("AT-123", result.accessToken());
        assertEquals(7200L, result.expiresInSeconds());
    }

    @Test
    @DisplayName("查询菜单成功 → 原样返回微信 JSON（含 menu 节点，且没有 errcode）")
    void getMenuSuccess() {
        String body = "{\"menu\":{\"button\":[{\"type\":\"click\",\"name\":\"关于我\"}]}}";
        server.expect(requestTo(containsString("/menu/get")))
                // ⚠️ 必须带 charset：text/plain 不带 charset 时按 ISO-8859-1 解码，中文会乱码。
                // 症状是「返回内容不对」，但根因在测试替身的编码，不在被测代码 —— 这个坑值得记住。
                .andRespond(withSuccess(body, MediaType.valueOf("text/plain;charset=UTF-8")));

        assertEquals(body, client.getMenu("AT-123"));
    }

    @Test
    @DisplayName("创建菜单：errcode 为 0 视为成功，不抛异常")
    void createMenuAcceptsErrcodeZero() {
        server.expect(requestTo(containsString("/menu/create")))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.TEXT_PLAIN));

        client.createMenu("AT-123", "{\"button\":[]}");
        // 走到这里没抛异常即通过
    }

    // ==================== 错误码翻译 ====================

    @Test
    @DisplayName("40164 → 提示里出现「白名单」和原始错误码")
    void translatesIpWhitelistError() {
        server.expect(requestTo(containsString("/menu/create")))
                .andRespond(withSuccess("{\"errcode\":40164,\"errmsg\":\"invalid ip 1.2.3.4\"}",
                        MediaType.TEXT_PLAIN));

        WxApiException e = assertThrows(WxApiException.class,
                () -> client.createMenu("AT-123", "{}"));

        assertEquals(40164, e.getErrCode());
        assertTrue(e.getMessage().contains("白名单"), "实际：" + e.getMessage());
        assertTrue(e.getMessage().contains("40164"), "提示里要留原始错误码便于排查");
        assertTrue(e.getMessage().contains("invalid ip"), "原始 errmsg 也要保留");
    }

    @Test
    @DisplayName("45058 → 提示说明「未认证订阅号不能跳外链」")
    void translatesInvalidUrlDomain() {
        server.expect(requestTo(containsString("/menu/create")))
                .andRespond(withSuccess("{\"errcode\":45058,\"errmsg\":\"invalid url domain\"}",
                        MediaType.TEXT_PLAIN));

        WxApiException e = assertThrows(WxApiException.class,
                () -> client.createMenu("AT-123", "{}"));

        assertEquals(45058, e.getErrCode());
        assertTrue(e.getMessage().contains("未认证订阅号"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("40001 / 42001 被标记为「token 失效」，供上层决定重试")
    void marksTokenInvalidErrors() {
        server.expect(requestTo(containsString("/menu/delete")))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}",
                        MediaType.TEXT_PLAIN));

        WxApiException e = assertThrows(WxApiException.class, () -> client.deleteMenu("AT-123"));

        assertTrue(e.isTokenInvalid(), "40001 应被识别为 token 失效");
    }

    @Test
    @DisplayName("未知错误码 → 走通用提示，仍保留 errcode 与 errmsg")
    void unknownErrorCodeFallsBack() {
        server.expect(requestTo(containsString("/menu/get")))
                .andRespond(withSuccess("{\"errcode\":99999,\"errmsg\":\"something odd\"}",
                        MediaType.TEXT_PLAIN));

        WxApiException e = assertThrows(WxApiException.class, () -> client.getMenu("AT-123"));

        assertEquals(99999, e.getErrCode());
        assertTrue(e.getMessage().contains("99999"));
        assertTrue(e.getMessage().contains("something odd"));
    }

    @Test
    @DisplayName("非 JSON 响应 → 提示「可能是网关或防火墙页面」，而不是抛解析异常")
    void handlesNonJsonResponse() {
        server.expect(requestTo(containsString("/menu/get")))
                .andRespond(withSuccess("<html>502 Bad Gateway</html>", MediaType.TEXT_HTML));

        WxApiException e = assertThrows(WxApiException.class, () -> client.getMenu("AT-123"));

        assertTrue(e.getMessage().contains("不是 JSON"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("空响应 → 明确报错，不静默通过")
    void handlesEmptyResponse() {
        server.expect(requestTo(containsString("/menu/get")))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThrows(WxApiException.class, () -> client.getMenu("AT-123"));
    }

    // ==================== 配置缺失 ====================

    @Test
    @DisplayName("未配置 AppID / AppSecret → 提前失败并指明要设哪个环境变量")
    void failsFastWhenCredentialsMissing() {
        WxApiClient noCredentialClient = new WxApiClient(RestClient.builder(), new ObjectMapper(),
                new WxProperties("stub-token", "", ""));

        WxApiException e = assertThrows(WxApiException.class, noCredentialClient::fetchAccessToken);

        assertTrue(e.getMessage().contains("WX_APP_ID"), "实际：" + e.getMessage());
        assertTrue(e.getMessage().contains("WX_APP_SECRET"));
    }

    @Test
    @DisplayName("凭证缺失时不该发出任何 HTTP 请求")
    void doesNotCallWechatWhenCredentialsMissing() {
        WxApiClient noCredentialClient = new WxApiClient(RestClient.builder(), new ObjectMapper(),
                new WxProperties("stub-token", "", ""));

        assertThrows(WxApiException.class, noCredentialClient::fetchAccessToken);

        // server 上没有任何 expectation，若真发了请求会失败 —— 能走到这里说明确实没发
        server.verify();
    }
}
