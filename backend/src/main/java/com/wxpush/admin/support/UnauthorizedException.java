package com.wxpush.admin.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 未登录 / 登录已过期 / 账号密码不正确（对应 HTTP 401）。
 *
 * <p>两个使用场景：</p>
 * <ul>
 *   <li>{@link LoginInterceptor} 发现没有有效会话时抛出；</li>
 *   <li>{@code AuthService} 校验账号密码失败、或账号被停用时抛出。</li>
 * </ul>
 *
 * <p>抛出后由 {@link GlobalExceptionHandler} 统一转成
 * {@code 401 + {code:401, message:"..."}} 的规范响应 ——
 * 这样拦截器与业务代码输出的是<b>同一种</b>错误格式，前端只需按一套规则处理。</p>
 *
 * <p><b>为什么异常上还要加 {@code @ResponseStatus}？</b>
 * 这是一道<b>兜底保险</b>。{@code GlobalExceptionHandler} 限定了
 * {@code basePackages = "com.wxpush.admin"}，它只对 admin 包下的 Controller 生效；
 * 万一请求落在了没有匹配 handler 的路径上（异常处理器的 advice 不适用），
 * 类上的这个注解能让 Spring 内置的 {@code ResponseStatusExceptionResolver} 接住它，
 * 依然返回 401 而不是 500。</p>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
