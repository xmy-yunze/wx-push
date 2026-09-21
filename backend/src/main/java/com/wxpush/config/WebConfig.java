package com.wxpush.config;

import com.wxpush.admin.support.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置 —— 管两件事：<b>跨域</b>与<b>登录拦截</b>。
 *
 * <h2>一、跨域</h2>
 * <p>开发期前端跑在 Vite 的 5173 端口，后端在 8080，属于不同源，
 * 浏览器默认会拦掉请求（这就是那个经典的 CORS 报错）。</p>
 *
 * <p>⚠️ 两个容易写错的点：</p>
 * <ul>
 *   <li>只给 {@code /api/**} 开跨域 —— 微信回调 {@code /wx} <b>绝不能</b>开，
 *       它只应该被微信服务器访问；</li>
 *   <li>{@code allowCredentials(true)} 与 {@code allowedOrigins("*")} <b>不能同时用</b>，
 *       浏览器会直接拒绝。必须改用 {@code allowedOriginPatterns} 做模式匹配。</li>
 * </ul>
 *
 * <p>生产环境前端由 Nginx 与后端同域托管，同源请求不涉及 CORS，
 * 到时候可以把这段收紧成只允许正式域名。</p>
 *
 * <h2>二、登录拦截</h2>
 * <p>给 {@code /api/**} 挂上门禁 {@link LoginInterceptor}，
 * 让管理后台接口不再匿名可读（消息流水里有用户 openid 和聊天内容）。</p>
 *
 * <p>⚠️ <b>路径只限 {@code /api/**}，绝不能扩大到全局</b> ——
 * 微信回调 {@code /wx} 是微信服务器调用的，它不带任何 Cookie，
 * 一旦被拦下就会让公众号功能全线失效（这是本项目最容易踩的坑之一）。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册登录拦截器。
     *
     * <p>这里只声明「拦哪些路径」，<b>免登录白名单由拦截器内部判断</b>
     * （见 {@link LoginInterceptor#WHITELIST}）。刻意不用 {@code excludePathPatterns}，
     * 是为了让白名单与判断逻辑同处一地，并且能被单元测试直接覆盖。</p>
     *
     * <p>拦截器用 {@code new} 而不是注入：它是<b>无状态</b>的（没有成员变量、没有依赖），
     * 交给 Spring 管理生命周期没有收益。将来若要注入依赖，再改成 Bean 也不迟。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/api/**");
    }
}
