package com.wxpush.gateway;

import com.wxpush.config.WxProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 微信签名校验器。
 *
 * <p>算法（微信官方定义）：</p>
 * <ol>
 *   <li>把 {@code token}、{@code timestamp}、{@code nonce} 三个字符串<b>按字典序排序</b></li>
 *   <li>直接拼接成一个字符串（无分隔符）</li>
 *   <li>做 SHA-1，取<b>小写十六进制</b></li>
 *   <li>与微信传来的 {@code signature} 比对</li>
 * </ol>
 *
 * <p>注意：这里的 {@code token} 是你自己在公众平台填的，<b>不会随请求传输</b>，
 * 所以这个校验同时起到了「证明该请求确实来自微信服务器」的作用。</p>
 */
@Component
public class WxSignatureVerifier {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final WxProperties properties;

    public WxSignatureVerifier(WxProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验签名是否有效。
     *
     * @param signature 微信传来的签名
     * @param timestamp 微信传来的时间戳
     * @param nonce     微信传来的随机数
     * @return 校验通过返回 true
     */
    public boolean verify(String signature, String timestamp, String nonce) {
        String token = properties.token();
        if (isBlank(signature) || isBlank(timestamp) || isBlank(nonce) || isBlank(token)) {
            // Token 未配置时一律拒绝，避免「空 token 恰好算出空签名」这类边界被绕过
            return false;
        }
        String expected = sign(token, timestamp, nonce);
        // 用常量时间比较，避免通过响应耗时差异逐字节猜出签名（时序攻击）
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 按微信规则计算签名：排序 → 拼接 → SHA-1 → 小写十六进制。
     *
     * <p>该方法同时供生产代码与单元测试使用，便于本地不连微信也能自测。</p>
     */
    public String sign(String token, String timestamp, String nonce) {
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        return sha1Hex(String.join("", parts));
    }

    /** SHA-1 摘要，输出小写十六进制字符串 */
    private static String sha1Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int b = digest[i] & 0xFF;
                out[i * 2] = HEX[b >>> 4];
                out[i * 2 + 1] = HEX[b & 0x0F];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然内置 SHA-1，走到这里说明运行环境异常
            throw new IllegalStateException("当前 JDK 不支持 SHA-1 算法", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
