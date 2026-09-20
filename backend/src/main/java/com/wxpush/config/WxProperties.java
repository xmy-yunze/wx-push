package com.wxpush.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信相关配置绑定。
 *
 * <p>三项均来自「微信公众平台 → 设置与开发 → 基本配置」，
 * 通过环境变量注入，<b>禁止硬编码进代码或提交进 Git</b>（项目红线）。</p>
 *
 * @param token     服务器配置里自己填的 Token，用于签名校验（不参与网络传输）
 * @param appId     开发者 ID
 * @param appSecret 开发者密码，泄露等同于公众号被接管
 */
@ConfigurationProperties(prefix = "wx")
public record WxProperties(String token, String appId, String appSecret) {
}
