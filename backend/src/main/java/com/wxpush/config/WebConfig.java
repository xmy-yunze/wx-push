package com.wxpush.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置。
 *
 * <p>目前只有一件事：<b>给 admin 前端开跨域</b>。
 * 开发期前端跑在 Vite 的 5173 端口，后端在 8080，属于不同源，
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
}
