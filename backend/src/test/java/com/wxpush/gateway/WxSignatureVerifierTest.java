package com.wxpush.gateway;

import com.wxpush.config.WxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签名校验的单元测试。
 *
 * <p>这一层不依赖微信、不依赖 Spring、不依赖数据库，纯算法验证。</p>
 */
class WxSignatureVerifierTest {

    private static final String TOKEN = "wxToken123";
    private static final String TIMESTAMP = "1700000000";
    private static final String NONCE = "abc123";

    /**
     * 期望签名，<b>由 shell 独立算出</b>，不是用 Java 自己算自己：
     * <pre>{@code printf '%s' "1700000000abc123wxToken123" | shasum -a 1}</pre>
     * 三个值按字典序排序后为 {@code 1700000000 | abc123 | wxToken123}。
     */
    private static final String EXPECTED_SIGNATURE = "1cfaf372ed61021ae9e166f14d75ed7304da707b";

    private WxSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WxSignatureVerifier(new WxProperties(TOKEN, "wxAppId", "wxAppSecret"));
    }

    @Test
    @DisplayName("签名算法与微信官方规则一致（排序 → 拼接 → SHA-1 → 小写十六进制）")
    void signMatchesOfficialRule() {
        assertEquals(EXPECTED_SIGNATURE, verifier.sign(TOKEN, TIMESTAMP, NONCE));
    }

    @Test
    @DisplayName("签名正确时校验通过")
    void verifyAcceptsCorrectSignature() {
        assertTrue(verifier.verify(EXPECTED_SIGNATURE, TIMESTAMP, NONCE));
    }

    @Test
    @DisplayName("签名错误时校验不通过")
    void verifyRejectsWrongSignature() {
        assertFalse(verifier.verify("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", TIMESTAMP, NONCE));
    }

    @Test
    @DisplayName("参数缺失时校验不通过，不抛异常")
    void verifyRejectsMissingParams() {
        assertFalse(verifier.verify(null, TIMESTAMP, NONCE));
        assertFalse(verifier.verify(EXPECTED_SIGNATURE, null, NONCE));
        assertFalse(verifier.verify(EXPECTED_SIGNATURE, TIMESTAMP, null));
        assertFalse(verifier.verify("", TIMESTAMP, NONCE));
    }

    @Test
    @DisplayName("Token 未配置时一律拒绝（防止空 Token 绕过校验）")
    void verifyRejectsWhenTokenNotConfigured() {
        WxSignatureVerifier unconfigured = new WxSignatureVerifier(new WxProperties("", "appId", "secret"));
        assertFalse(unconfigured.verify(EXPECTED_SIGNATURE, TIMESTAMP, NONCE));
    }

    @Test
    @DisplayName("签名比对区分大小写（微信下发的是小写十六进制）")
    void verifyIsCaseSensitive() {
        assertFalse(verifier.verify(EXPECTED_SIGNATURE.toUpperCase(), TIMESTAMP, NONCE));
    }
}
