package com.wxpush.admin.support;

import com.wxpush.admin.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器 —— 挂在 {@code /api/**} 上，充当管理后台的统一门禁。
 *
 * <p>设计上只做一件事：<b>检查会话里有没有登录者</b>。
 * 没有就抛 {@link UnauthorizedException}，由 {@code GlobalExceptionHandler}
 * 转成统一的 401 JSON 响应。这样拦截器不需要自己拼 JSON，
 * 响应格式与业务接口完全一致，只有一处定义。</p>
 *
 * <p>⚠️ <b>绝不能拦 {@code /wx}</b>（微信回调）：微信服务器不会带任何 Cookie，
 * 拦住它会让公众号功能全线失效。所以拦截路径必须限定在 {@code /api/**}，
 * 具体注册见 {@link com.wxpush.config.WebConfig}。</p>
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 免登录白名单。
     *
     * <p>放在这个类里而不是 {@code WebConfig} 里，是为了让「哪些接口不需要登录」
     * 和「拦截逻辑」待在同一处 —— 白名单只有一个真相源，加路径时不会漏改另一处。</p>
     *
     * <p>只放行两个：</p>
     * <ul>
     *   <li>{@code /api/auth/login} —— 登录本身当然不能要求先登录；</li>
     *   <li>{@code /api/auth/logout} —— 未登录时登出应幂等成功，不该报 401。</li>
     * </ul>
     *
     * <p>注意 {@code /api/auth/me} <b>不在</b>白名单里：它正是用来探测登录态的。</p>
     */
    public static final String[] WHITELIST = {
            "/api/auth/login",
            "/api/auth/logout"
    };

    /** 路径匹配器，Spring 自带工具，无需额外依赖 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ⚠️ 跨域预检请求必须放行。
        // 浏览器发 CORS 预检（OPTIONS）时按规范不带 Cookie，如果把 OPTIONS 也拦下，
        // 前后端分域部署时所有请求都会先死在预检上（表现为「接口全 401」）。
        // 开发期走 Vite 代理、生产期走 Nginx 同域，都不会触发预检，
        // 但这段是给「将来真分域」留的保险。
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 白名单放行。
        // 刻意在拦截器内部判断，而不是靠 WebConfig 的 excludePathPatterns ——
        // 这样「哪些接口免登录」与「判断逻辑」待在同一处，且单元测试能直接覆盖到这段分支。
        // 取路径时减掉 contextPath，将来若给应用配了 context-path 也不会失配。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String pattern : WHITELIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }

        // getSession(false)：没有会话就返回 null，不要凭空创建 ——
        // 未登录的请求不该在服务端留下一个空 Session（那等于给攻击者开了个内存增长的开关）。
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthService.SESSION_USER_KEY) != null) {
            return true;
        }

        throw new UnauthorizedException("未登录或登录已过期");
    }
}
