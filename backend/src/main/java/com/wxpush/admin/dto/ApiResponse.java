package com.wxpush.admin.dto;

/**
 * admin 接口的统一响应包装。
 *
 * <p>所有 {@code /api/**} 接口一律返回该结构，前端只需先判断 {@code code}，
 * 再决定是取 {@code data} 还是弹 {@code message}。</p>
 *
 * <p>为什么不直接把业务对象当返回值？因为失败时需要一个地方放「为什么失败」，
 * 而且 HTTP 状态码不足以表达业务语义（比如「消息不存在」和「参数格式错」都可能是 4xx）。</p>
 *
 * @param code    0 = 成功；非 0 = 失败，见 {@code docs/admin-接口契约.md} 的错误码表
 * @param message 提示信息，失败时一定是可直接展示给运营者的中文
 * @param data    业务数据；失败时为 {@code null}
 */
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功 */
    public static final int CODE_OK = 0;
    /** 参数不合法 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录 / 登录已过期 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 调用上游（微信）接口失败 */
    public static final int CODE_BAD_GATEWAY = 502;
    /** 服务端异常 */
    public static final int CODE_SERVER_ERROR = 500;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_OK, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
