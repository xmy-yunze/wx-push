package com.wxpush.admin.dto;

/**
 * 菜单发布结果。
 *
 * <p>为什么干跑和真发布共用同一个返回结构？因为前端展示逻辑完全一样 ——
 * 都是「把这段 JSON 显示给运营者看」。区别只在 {@code published} 这个标志，
 * 前端据此决定提示语是「已发布到微信」还是「预览（未发送）」。</p>
 *
 * @param published   是否真的调用了微信接口；干跑模式为 false
 * @param payloadJson 实际发送（或将要发送）给微信的菜单 JSON 报文
 */
public record MenuPublishResult(boolean published, String payloadJson) {

    /** 干跑：只校验，不发送 */
    public static MenuPublishResult dryRun(String payloadJson) {
        return new MenuPublishResult(false, payloadJson);
    }

    /** 真发布成功 */
    public static MenuPublishResult published(String payloadJson) {
        return new MenuPublishResult(true, payloadJson);
    }
}
