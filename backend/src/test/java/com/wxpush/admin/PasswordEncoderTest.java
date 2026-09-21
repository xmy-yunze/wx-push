package com.wxpush.admin;

import com.wxpush.admin.support.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码编码器的单元测试。
 *
 * <p>这是本项目安全性的根 —— 一旦这里错了，等于给后台留了一把万能钥匙。
 * 所以覆盖得比业务代码更细：正常路径、边界、以及<b>每一条「必须拒绝」的分支</b>。</p>
 *
 * <p>这些测试<b>完全不碰数据库</b>，任何环境都能跑。</p>
 */
@DisplayName("密码编码器（PBKDF2）")
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordEncoder();

    /** 与 {@code GeneratePasswordHash.java} 同一套参数 */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_BITS = 256;

    /** 固定盐，让「历史哈希」在测试里可复现 */
    private static final byte[] FIXED_SALT = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    // ==================== 生成 ====================

    @Test
    @DisplayName("encode：输出四段式自描述串，盐与哈希长度符合约定")
    void encode_输出格式() {
        String stored = encoder.encode("anyPassword123");

        String[] parts = stored.split("\\$", -1);
        assertEquals(4, parts.length, "应为 pbkdf2$迭代次数$盐$哈希 四段");
        assertEquals(PasswordEncoder.ID, parts[0]);
        assertEquals(String.valueOf(PasswordEncoder.DEFAULT_ITERATIONS), parts[1]);
        assertEquals(PasswordEncoder.SALT_BYTES, Base64.getDecoder().decode(parts[2]).length);
        assertEquals(KEY_BITS / 8, Base64.getDecoder().decode(parts[3]).length);
    }

    @Test
    @DisplayName("encode：同一密码两次生成的结果必须不同（盐随机，防彩虹表）")
    void encode_盐随机() {
        String first = encoder.encode("samePassword");
        String second = encoder.encode("samePassword");

        assertNotEquals(first, second, "两次结果相同说明盐没有随机，彩虹表就能直接命中");
    }

    @Test
    @DisplayName("encode：空密码或 null 抛 IllegalArgumentException")
    void encode_拒绝空密码() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(null));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(""));
    }

    // ==================== 校验 ====================

    @Test
    @DisplayName("matches：正确密码通过")
    void matches_正确密码通过() {
        String stored = encoder.encode("correctPassword");
        assertTrue(encoder.matches("correctPassword", stored));
    }

    @Test
    @DisplayName("matches：错误密码被拒")
    void matches_错误密码被拒() {
        String stored = encoder.encode("correctPassword");

        assertFalse(encoder.matches("wrongPassword", stored));
        assertFalse(encoder.matches("CorrectPassword", stored), "大小写不同也算错误");
        assertFalse(encoder.matches("correctPassword ", stored), "尾部空格不能忽略");
    }

    @Test
    @DisplayName("matches：哈希被篡改一个字符即被拒")
    void matches_篡改哈希被拒() {
        String stored = encoder.encode("secret123");
        String[] parts = stored.split("\\$", -1);

        // 把哈希段的首字符换成另一个合法 Base64 字符 ——
        // 串仍然「格式合法」能解码，但内容已经不对了
        char[] hashChars = parts[3].toCharArray();
        hashChars[0] = hashChars[0] == 'A' ? 'B' : 'A';
        parts[3] = new String(hashChars);

        assertFalse(encoder.matches("secret123", String.join("$", parts)));
    }

    @Test
    @DisplayName("matches：迭代次数取自存储串，而不是写死的常量")
    void matches_迭代次数取自存储串() {
        // 用远低于默认值的迭代次数造一个「历史哈希」。
        // 如果校验端写死用 310000 去算，这里必然对不上 ——
        // 那么这个断言就是在保护「将来调参不会让老密码全部失效」这条设计承诺。
        String legacy = storedWith("legacyPassword", 1_000);

        assertTrue(encoder.matches("legacyPassword", legacy),
                "必须用存储串里的迭代次数校验，否则调高迭代次数就会让所有老密码失效");
        assertFalse(encoder.matches("wrongPassword", legacy));
    }

    @Test
    @DisplayName("matches：迭代次数低于安全下限时拒绝（防哈希被改成弱参数）")
    void matches_迭代次数过低被拒() {
        String weak = storedWith("weakPassword", 10);
        assertFalse(encoder.matches("weakPassword", weak),
                "有人把库里的迭代次数改成 10 后，这个哈希必须不再被接受");
    }

    @Test
    @DisplayName("matches：格式非法的串一律返回 false，且不抛异常")
    void matches_格式非法被拒() {
        String[] malformed = {
                "",
                "abc",
                "pbkdf2$310000$xxxx",                        // 只有三段
                "pbkdf2$310000$xxxx$yyyy$zzzz",              // 五段
                "md5$310000$xxxx$yyyy",                      // 算法标识不对
                "pbkdf2$abc$xxxx$yyyy",                      // 迭代次数不是数字
                "pbkdf2$310000$!!!!$!!!!",                   // 不是合法 Base64
                "pbkdf2$310000$$",                           // 盐与哈希为空
        };

        for (String broken : malformed) {
            assertFalse(encoder.matches("anyPassword", broken),
                    "坏数据应返回 false 而不是抛异常，否则一条脏数据就能把接口打成 500：" + broken);
        }
    }

    @Test
    @DisplayName("matches：明文或存储串为 null / 空时返回 false")
    void matches_空值返回false() {
        String stored = encoder.encode("somePassword");

        assertFalse(encoder.matches(null, stored));
        assertFalse(encoder.matches("", stored));
        assertFalse(encoder.matches("somePassword", null));
        assertFalse(encoder.matches("somePassword", ""));
    }

    // ==================== 测试辅助 ====================

    /**
     * 按指定参数造一个存储串，用来模拟「历史上用别的参数生成的哈希」。
     *
     * <p>这里刻意在测试内独立实现一遍 PBKDF2，而不是去调 {@code PasswordEncoder} 的私有方法 ——
     * 虽然底层用的是同一个 JDK API，但这样至少能验证「参数是从串里读出来的」这个行为分支，
     * 而不是拿被测对象自己验自己。</p>
     */
    private static String storedWith(String rawPassword, int iterations) {
        byte[] hash = pbkdf2(rawPassword, FIXED_SALT, iterations, KEY_BITS);
        return "pbkdf2$" + iterations + "$"
                + Base64.getEncoder().encodeToString(FIXED_SALT) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("测试内构造哈希失败", e);
        } finally {
            spec.clearPassword();
        }
    }
}
