package com.wxpush.admin.support;

/**
 * 请求的资源不存在（对应 HTTP 404）。
 *
 * <p>抛出后由 {@link GlobalExceptionHandler} 统一转成规范响应，
 * Service 层不需要自己拼「错误响应」。</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
