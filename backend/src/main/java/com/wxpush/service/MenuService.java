package com.wxpush.service;

import com.wxpush.domain.menu.MenuTree;
import com.wxpush.gateway.AccessTokenManager;
import com.wxpush.gateway.WxApiClient;
import com.wxpush.gateway.WxApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

/**
 * 自定义菜单服务 —— 对外只暴露「查 / 发布 / 清空」三个动作。
 *
 * <h3>设计取舍：菜单为什么不落库</h3>
 * <p>菜单的<b>真相源在微信服务器</b>：{@code menu/get} 随时能把它取回来。
 * 如果我们在本地再存一份，就必然出现「本地记录和线上实际不一致」的问题 ——
 * 运营者在公众平台手工改过、或发布失败但本地已写库，都会导致两边对不上。
 * 所以这里不建表、不落库，本地只缓存 access_token。</p>
 *
 * <h3>为什么要自动重试一次</h3>
 * <p>access_token 是全局唯一的，别的调用方（如公众平台后台）刷新过它，
 * 我们缓存里的那个就作废了。此时微信会返回 40001 / 42001。
 * 处理办法很明确：<b>丢弃缓存 → 立刻重取 → 重试一次</b>。
 * 只重试一次，避免因为配置错误（比如 AppSecret 不对）陷入循环调用。</p>
 */
@Slf4j
@Service
public class MenuService {

    private final AccessTokenManager accessTokenManager;
    private final WxApiClient wxApiClient;
    private final ObjectMapper objectMapper;

    public MenuService(AccessTokenManager accessTokenManager,
                       WxApiClient wxApiClient,
                       ObjectMapper objectMapper) {
        this.accessTokenManager = accessTokenManager;
        this.wxApiClient = wxApiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前在微信服务器上生效的菜单。
     *
     * @return 微信返回的原始结构（{@code {"menu":{...}}}）。<b>不重新建模成对象</b>，
     *         是为了让管理端看到的就是线上真实内容（包括运营者在公众平台手工改的部分），
     *         避免「我们的模型」和「微信的实际状态」出现第二份定义
     */
    public JsonNode currentMenu() {
        String raw = withTokenRetry(wxApiClient::getMenu);
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException e) {
            // WxApiClient.check() 已保证过是 JSON，走到这里说明响应异常
            throw new WxApiException(WxApiException.ERR_NETWORK, "非 JSON 响应",
                    "查询菜单失败：微信返回的不是 JSON");
        }
    }

    /**
     * 生成将发送给微信的菜单报文，<b>不做任何网络调用</b>。
     *
     * <p>这是「干跑」能力的基础：本地没有 IP 白名单、或不想动线上菜单时，
     * 仍能把菜单结构序列化出来人工核对 —— 验证的是「我们发给微信的东西对不对」，
     * 而单测验证的是「代码逻辑对不对」，两者互补。</p>
     */
    public String renderPayload(MenuTree menu) {
        return toJson(menu);
    }

    /**
     * 发布菜单（覆盖线上现有菜单）。
     *
     * @param menu 已通过校验的菜单树 —— 非法结构在 {@code build()} / {@code parse()} 阶段就已被拒绝
     */
    public void publish(MenuTree menu) {
        String menuJson = toJson(menu);
        log.info("准备发布自定义菜单：{}", menuJson);
        withTokenRetry(token -> {
            wxApiClient.createMenu(token, menuJson);
            return null;
        });
        log.info("自定义菜单发布成功");
    }

    /** 清空线上菜单（恢复成公众号默认状态） */
    public void clear() {
        withTokenRetry(token -> {
            wxApiClient.deleteMenu(token);
            return null;
        });
        log.info("自定义菜单已清空");
    }

    /** 把菜单树序列化成微信要求的 JSON 报文 */
    private String toJson(MenuTree menu) {
        try {
            return objectMapper.writeValueAsString(menu.toMap());
        } catch (JacksonException e) {
            // 走到这里说明菜单树里有无法序列化的内容，属于代码问题，不是配置问题
            throw new IllegalStateException("菜单序列化失败", e);
        }
    }

    /**
     * 带一次 token 重试的执行模板。
     *
     * <p>把「取 token → 调用 → 遇 40001/42001 则换 token 重试一次」这段固定流程收在一处，
     * 三个动作都复用它 —— 避免每个方法各写一遍重试逻辑（写漏一个就变成偶发故障）。</p>
     *
     * @param call 用 token 执行的实际调用
     */
    private <T> T withTokenRetry(Function<String, T> call) {
        String token = accessTokenManager.accessToken();
        try {
            return call.apply(token);
        } catch (WxApiException e) {
            if (!e.isTokenInvalid()) {
                throw e;
            }
            log.warn("access_token 已失效（errcode={}），丢弃缓存后重试一次", e.getErrCode());
            accessTokenManager.invalidate();
            return call.apply(accessTokenManager.accessToken());
        }
    }
}
