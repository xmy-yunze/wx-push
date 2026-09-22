package com.wxpush.gateway;

import com.wxpush.config.WxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 微信服务端 API 的<b>适配器</b>。
 *
 * <p>把「微信接口长什么样」全部关在这个类里，上层只看到干净的方法。
 * 这样做的价值很具体：微信接口的入参风格并不统一（有的 token 在 query、
 * 有的在 body；有的返回 {@code {menu:{...}}}、有的返回 {@code {errcode,errmsg}}），
 * 如果让业务代码直接拼 URL，这些差异就会散落到各处，改一次要全项目搜。</p>
 *
 * <p>⚠️ 方向别搞反：<b>这个类是「我们 → 微信」（主动调用，JSON）</b>；
 * 微信推送过来的消息是「微信 → 我们」（被动接收，XML），在
 * {@link WxCallbackController} 那一侧，两者不要混。</p>
 *
 * <p>⚠️ 安全：{@code /token} 接口的 URL 里含 {@code appSecret}。
 * 打日志时绝不要打印完整请求 URL，否则密钥会进日志文件。</p>
 */
@Slf4j
@Component
public class WxApiClient {

    /** 微信 API 基址 */
    private static final String BASE_URL = "https://api.weixin.qq.com/cgi-bin";

    /**
     * 常见错误码 → 人话提示。
     *
     * <p>只覆盖「运营者自己能修」的那批。其余错误码会走通用提示，
     * 附上原始 errcode/errmsg 供排查。宁可少写，也不要凭猜写给错方向。</p>
     */
    private static final Map<Integer, String> HINTS = Map.ofEntries(
            Map.entry(40013, "AppID 无效，请核对环境变量 WX_APP_ID 与公众平台的「开发者ID」是否一致"),
            Map.entry(40054, "菜单结构不合法：一级最多 3 个、二级最多 5 个，且名称不能超长"),
            Map.entry(40125, "AppSecret 无效，请核对环境变量 WX_APP_SECRET（注意公众平台可重置它）"),
            Map.entry(40164, "调用来源 IP 不在公众号后台的白名单内。请到「设置与开发 → 基本配置 → IP 白名单」"
                    + "把服务器当前公网出口 IP 加进去（个人热点上网时该 IP 会变，每次都要重新加）"),
            Map.entry(41001, "缺少 access_token 参数（内部实现问题，请检查调用链）"),
            Map.entry(42001, "access_token 已过期（通常会被自动重试，若反复出现请检查是否有多处各自刷新 token）"),
            Map.entry(45009, "接口调用频率超限，请稍后再试"),
            Map.entry(45058, "菜单里含有外部网址。未认证订阅号只能填公众号内链接（页面模板 / 合集 / 历史消息），"
                    + "跳转外链没有任何绕过办法")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WxProperties properties;

    public WxApiClient(RestClient.Builder builder, ObjectMapper objectMapper, WxProperties properties) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 拉取 access_token。
     *
     * <p>⚠️ 这个接口<b>不要直接调用</b>——一律通过 {@link AccessTokenManager}。
     * 因为 access_token 全局唯一，每拉一次都会让上一个立刻失效。</p>
     *
     * @return token 与其有效期（秒）
     */
    public TokenResult fetchAccessToken() {
        requireCredentials();
        JsonNode root = check(
                rawGet("/token", Map.of(
                        "grant_type", "client_credential",
                        "appid", properties.appId(),
                        "secret", properties.appSecret()
                )),
                "获取 access_token");

        String token = root.path("access_token").asText("");
        if (token.isBlank()) {
            throw new WxApiException(WxApiException.ERR_NETWORK, "响应中没有 access_token",
                    "微信返回的响应里没有 access_token，请检查配置");
        }
        return new TokenResult(token, root.path("expires_in").asLong(7200L));
    }

    /**
     * 创建（发布）菜单。
     *
     * @param accessToken 有效 token
     * @param menuJson    已经序列化好的菜单 JSON —— 由 {@code MenuTree} 负责生成
     */
    public void createMenu(String accessToken, String menuJson) {
        String body;
        try {
            body = restClient.post()
                    .uri(builder -> builder.path("/menu/create")
                            .queryParam("access_token", accessToken).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(menuJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            body = e.getResponseBodyAsString();
        } catch (RestClientException e) {
            throw networkFailure(e);
        }
        check(body, "创建菜单");
    }

    /**
     * 查询当前生效的菜单。
     *
     * <p>成功时微信返回 {@code {"menu":{...}}}，<b>没有 errcode 字段</b> ——
     * 所以这里把原始 JSON 字符串原样返回给前端展示，不做二次建模：
     * 菜单的真相源在微信服务器，我们只当搬运工，避免出现「两份定义对不上」。</p>
     */
    public String getMenu(String accessToken) {
        String body = rawGet("/menu/get", Map.of("access_token", accessToken));
        check(body, "查询菜单");
        return body;
    }

    /** 删除（清空）菜单 */
    public void deleteMenu(String accessToken) {
        check(rawGet("/menu/delete", Map.of("access_token", accessToken)), "删除菜单");
    }

    // ------------------------------------------------------------------
    // 内部：统一的请求与错误检查
    // ------------------------------------------------------------------

    /** 发 GET 请求并拿到原始响应体（非 200 也尽量取回响应体，因为微信有时用它带 errcode） */
    private String rawGet(String path, Map<String, String> params) {
        try {
            return restClient.get()
                    .uri(builder -> {
                        UriBuilder b = builder.path(path);
                        params.forEach(b::queryParam);
                        return b.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            return e.getResponseBodyAsString();
        } catch (RestClientException e) {
            throw networkFailure(e);
        }
    }

    /**
     * 解析响应体并检查微信错误码。
     *
     * <p>微信的约定：成功时可能<b>没有</b> {@code errcode} 字段（如 menu/get），
     * 也可能返回 {@code errcode=0}。所以判断条件是「字段存在且非 0」才算失败。</p>
     */
    private JsonNode check(String rawBody, String apiName) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new WxApiException(WxApiException.ERR_NETWORK, "空响应",
                    apiName + "失败：微信返回了空响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            // 不要原样打印整个响应体：可能很长，且万一含敏感信息
            throw new WxApiException(WxApiException.ERR_NETWORK, "非 JSON 响应",
                    apiName + "失败：微信返回的不是 JSON（可能是网关或防火墙页面，请检查网络出口）");
        }

        JsonNode errCodeNode = root.get("errcode");
        if (errCodeNode != null && errCodeNode.asInt() != 0) {
            int errCode = errCodeNode.asInt();
            String errMsg = root.path("errmsg").asText("");
            log.warn("微信接口【{}】失败：errcode={} errmsg={}", apiName, errCode, errMsg);
            throw new WxApiException(errCode, errMsg, HINTS.get(errCode));
        }
        return root;
    }

    /** 配置缺失时提前失败，给出明确指引，而不是等微信返回 40125 让人猜 */
    private void requireCredentials() {
        if (isBlank(properties.appId()) || isBlank(properties.appSecret())) {
            throw new WxApiException(WxApiException.ERR_NETWORK, "缺少凭证",
                    "尚未配置 AppID / AppSecret：请设置环境变量 WX_APP_ID 与 WX_APP_SECRET 后重启服务"
                            + "（取值见公众平台「设置与开发 → 基本配置」）");
        }
    }

    private static WxApiException networkFailure(RestClientException e) {
        return new WxApiException(WxApiException.ERR_NETWORK, e.getMessage(),
                "无法连接微信接口：请检查本机网络出口、DNS 与防火墙设置");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** access_token 及其有效期（秒） */
    public record TokenResult(String accessToken, long expiresInSeconds) {
    }
}
