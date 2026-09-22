package com.wxpush.admin.controller;

import com.wxpush.admin.dto.ApiResponse;
import com.wxpush.admin.dto.MenuPublishResult;
import com.wxpush.domain.menu.MenuParser;
import com.wxpush.domain.menu.MenuTree;
import com.wxpush.service.MenuService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 管理后台 —— 自定义菜单接口（增 / 删 / 查）。
 *
 * <p>三个端点都在 {@code /api/**} 下，所以会被 {@code LoginInterceptor} 自动保护，
 * 不需要在这里再写鉴权逻辑。</p>
 *
 * <p>⚠️ 与 {@code /wx} 的区别：那个方向是「微信调我们」（无鉴权，靠签名校验保护），
 * 这里是「运营者调我们」（有鉴权，靠会话保护）。</p>
 */
@RestController
@RequestMapping("/api/menu")
public class AdminMenuController {

    private final MenuService menuService;

    public AdminMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 查询当前在微信服务器上生效的菜单。
     *
     * <p>返回微信的原始结构（{@code {"menu":{...}}}）。运营者在公众平台手工改过菜单时，
     * 这里看到的就是<b>线上真实状态</b>，而不是我们本地认为的状态。</p>
     */
    @GetMapping
    public ApiResponse<JsonNode> current() {
        return ApiResponse.ok(menuService.currentMenu());
    }

    /**
     * 发布菜单（覆盖线上现有菜单）。
     *
     * @param body    微信格式的菜单 JSON：{@code {"button":[...]}}
     * @param dryRun  干跑模式：只校验并回显「将要发送的报文」，<b>不调用微信</b>。
     *                本地没有 IP 白名单时也能验证菜单结构是否正确
     */
    @PostMapping
    public ApiResponse<MenuPublishResult> publish(@RequestBody JsonNode body,
                                                 @RequestParam(defaultValue = "false") boolean dryRun) {
        // 解析即校验：非法结构在到达微信之前就被拒掉，错误信息由我们自己给
        MenuTree menu = MenuParser.parse(body);
        String payload = menuService.renderPayload(menu);

        if (dryRun) {
            return ApiResponse.ok(MenuPublishResult.dryRun(payload));
        }
        menuService.publish(menu);
        return ApiResponse.ok(MenuPublishResult.published(payload));
    }

    /** 清空线上菜单（公众号恢复成默认状态） */
    @DeleteMapping
    public ApiResponse<Void> clear() {
        menuService.clear();
        return ApiResponse.ok(null);
    }
}
