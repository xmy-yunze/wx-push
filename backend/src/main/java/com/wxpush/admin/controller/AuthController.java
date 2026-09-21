package com.wxpush.admin.controller;

import com.wxpush.admin.dto.ApiResponse;
import com.wxpush.admin.dto.LoginRequest;
import com.wxpush.admin.dto.LoginUserVO;
import com.wxpush.admin.dto.SessionUser;
import com.wxpush.admin.service.AuthService;
import com.wxpush.admin.support.UnauthorizedException;
import com.wxpush.repository.entity.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 —— 鉴权接口（登录 / 登出 / 取当前用户）。
 *
 * <p>鉴权方案是 <b>Session + Cookie</b>：登录成功后把用户信息写进服务端 Session，
 * 浏览器只拿到一个 JSESSIONID。客户端不需要自己管 token，
 * 而且「登出」就是销毁 Session，语义干净、立即生效。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录。
     *
     * <p>入参 {@code {username, password}}，成功返回当前用户信息并写入会话。</p>
     *
     * <p><b>关于会话固定攻击（Session Fixation）</b>：如果登录前后用的是同一个
     * JSESSIONID，那么攻击者只要能事先把某个已知的 ID 塞给受害者（比如通过链接参数），
     * 受害者登录后这个 ID 依然有效，攻击者就能直接拿它冒充已登录用户。
     * 所以登录成功必须<b>换一个新的会话 ID</b>。</p>
     *
     * <p>⚠️ 顺序不能颠倒：{@code changeSessionId()} 要求当前请求已经关联了会话，
     * 否则抛 {@code IllegalStateException}。所以先 {@code getSession(true)} 确保会话存在，
     * 再换 ID。</p>
     */
    @PostMapping("/login")
    public ApiResponse<LoginUserVO> login(@RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest) {
        AdminUser user = authService.authenticate(request.username(), request.password());

        httpRequest.getSession(true);      // 确保已有关联会话
        httpRequest.changeSessionId();     // 换成新的 ID，防会话固定

        HttpSession session = httpRequest.getSession();
        session.setAttribute(AuthService.SESSION_USER_KEY,
                new SessionUser(user.getId(), user.getUsername(), user.getDisplayName()));

        return ApiResponse.ok(new LoginUserVO(user.getId(), user.getUsername(), user.getDisplayName()));
    }

    /**
     * 登出 —— 销毁会话。
     *
     * <p>本接口在拦截器白名单里（未登录也能访问），所以 {@code getSession(false)}
     * 可能拿到 {@code null}：这种情况下直接返回成功，
     * 让「登出」保持<b>幂等</b> —— 重复点登出不该报错。</p>
     *
     * <p>另外这里不是简单地清属性，而是 {@code invalidate()}：
     * 前者会让 Session 对象（连同 ID）继续存在，服务端仍占着内存；
     * 销毁则是彻底清掉。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.<Void>ok(null);
    }

    /**
     * 取当前登录用户。
     *
     * <p>本接口<b>不在</b>白名单里 —— 也就是说「能不能走到这里」本身就是登录态的判据：
     * 未登录时请求会被 {@code LoginInterceptor} 拦下并返回 401，
     * 因此前端可以用它作为路由守卫的探测接口。</p>
     *
     * <p>会话虽然在拦截器里已确认有效，但两次读取之间仍可能被并发登出销毁，
     * 所以这里保留一次判空，不能想当然地直接强转。</p>
     */
    @GetMapping("/me")
    public ApiResponse<LoginUserVO> me(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        SessionUser current = session == null
                ? null
                : (SessionUser) session.getAttribute(AuthService.SESSION_USER_KEY);

        if (current == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }

        return ApiResponse.ok(new LoginUserVO(current.id(), current.username(), current.displayName()));
    }
}
