package com.wxpush.admin.support;

import com.wxpush.admin.dto.ApiResponse;
import com.wxpush.gateway.WxApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * admin 接口的全局异常处理。
 *
 * <p><b>⚠️ 关键点：{@code basePackages} 必须限定在 admin 包。</b>
 * 如果不加这个限制，{@code @RestControllerAdvice} 会拦截<b>整个应用</b>的异常 ——
 * 包括 {@code /wx} 微信回调。那样一来，微信接口一旦抛异常就会返回 JSON 格式的错误体，
 * 而微信只认 XML 或空串，会导致解析失败并触发重推。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wxpush.admin")
public class GlobalExceptionHandler {

    /** 资源不存在 → 404 */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ResourceNotFoundException e) {
        log.warn("admin 接口：资源不存在 —— {}", e.getMessage());
        return ApiResponse.fail(ApiResponse.CODE_NOT_FOUND, e.getMessage());
    }

    /**
     * 未登录 / 登录已过期 / 账号密码不正确 → 401。
     *
     * <p>两处会抛这个异常：{@link LoginInterceptor} 拦下未登录的请求，
     * 以及 {@code AuthService} 校验账号密码失败。
     * 走同一个处理器，保证两者输出<b>完全一致</b>的响应结构 ——
     * 前端只需要按一套规则处理 401。</p>
     *
     * <p>⚠️ 这里不区分「账号不存在」与「密码错误」，提示统一为业务层给的那句话，
     * 避免通过提示差异做账号枚举。</p>
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("admin 接口：未授权 —— {}", e.getMessage());
        return ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, e.getMessage());
    }

    /**
     * 参数不合法 → 400。
     *
     * <p>给运营者看的提示不能是堆栈，所以这里只返回简短的中文说明，
     * 细节写进日志留给开发排查。</p>
     *
     * <p>菜单结构校验失败也走这里：{@code MenuParser} 与 {@code MenuTree} 抛的
     * {@link IllegalArgumentException} 消息是特意写成人话的（例如「一级菜单有 4 个，
     * 微信最多允许 3 个」），直接透传就能让运营者知道该改什么。</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException e) {
        log.warn("admin 接口：参数不合法 —— {}", e.getMessage());
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "请求参数不合法：" + e.getMessage());
    }

    /**
     * 调用微信接口失败 → 502。
     *
     * <p>为什么是 502 而不是 500？因为故障在<b>上游</b>（微信侧，或到微信的网络），
     * 不是本服务内部错误。运营者看到 502 就知道排查方向完全不同 ——
     * 多半是 IP 白名单、密钥或菜单内容的问题，而不是我们代码坏了。</p>
     *
     * <p>⚠️ 这里把异常消息<b>原样</b>返回给前端。它是特意写成
     * 「能直接展示的中文提示 + 括号里的原始错误码」的，例如
     * 「调用来源 IP 不在公众号后台的白名单内……（微信返回 40164：invalid ip）」。
     * 这是排错的主要线索，藏起来反而让人无从下手。</p>
     */
    @ExceptionHandler(WxApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleWxApi(WxApiException e) {
        log.warn("admin 接口：调用微信失败 —— {}", e.getMessage());
        return ApiResponse.fail(ApiResponse.CODE_BAD_GATEWAY, e.getMessage());
    }

    /**
     * 兜底 —— 其它未预期异常 → 500。
     *
     * <p>对外只说「服务端内部错误」，具体堆栈只进日志。
     * 把异常细节返回给前端是常见的信息泄露渠道。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("admin 接口：未预期异常", e);
        return ApiResponse.fail(ApiResponse.CODE_SERVER_ERROR, "服务端内部错误，请查看后端日志");
    }
}
