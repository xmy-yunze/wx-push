package com.wxpush.admin.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码编码器 —— PBKDF2-HMAC-SHA256 的生成与校验。
 *
 * <p><b>为什么不用 MD5 / SHA1？</b> 那类算法算得太快，现代显卡每秒能试上百亿次，
 * 加不加盐都挡不住撞库。PBKDF2 通过「随机盐 + 高迭代次数」把单次校验成本抬到几十毫秒，
 * 让暴力破解在经济上不划算。</p>
 *
 * <p><b>为什么用 PBKDF2 而不是 BCrypt？</b> PBKDF2 是 JDK 自带的
 * （{@code javax.crypto.SecretKeyFactory}），零额外依赖 ——
 * 不必为了一个哈希函数引入整个 Spring Security。安全性上两者都是可接受的现代选择。</p>
 *
 * <p>存储格式（自描述，参数随哈希一起存）：</p>
 * <pre>
 *   pbkdf2 $ 310000 $ &lt;salt 的 Base64&gt; $ &lt;hash 的 Base64&gt;
 *   算法标识   迭代次数      盐                 哈希值
 * </pre>
 *
 * <p><b>把参数写进存储串的价值</b>：校验时从串里读迭代次数和盐，
 * 所以将来调高迭代次数，老密码仍能用它当年那套参数验证通过，不需要强制所有人改密码。</p>
 *
 * <p>⚠️ {@code backend/scripts/GeneratePasswordHash.java} 与本类用的是同一套算法和格式。
 * 两边必须保持一致，否则「脚本生成的哈希登不进去」。测试里用固定向量盯住了这个约束。</p>
 */
@Slf4j
@Component
public class PasswordEncoder {

    /** JDK 自带的密钥派生算法，无第三方依赖 */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 算法标识，存储串的第一段 */
    public static final String ID = "pbkdf2";

    /** 默认迭代次数。越高越安全也越慢；30 万次在现代机器上约几十毫秒，是合理平衡点 */
    public static final int DEFAULT_ITERATIONS = 310_000;

    /** 盐长度（字节）。每个账号一个随机盐，让相同密码产生不同哈希，防彩虹表 */
    public static final int SALT_BYTES = 16;

    /** 输出哈希长度（位） */
    public static final int KEY_BITS = 256;

    /** 迭代次数的下限。低于这个值说明存储串被改坏了，拒绝校验而不是照单执行 */
    private static final int MIN_ITERATIONS = 1_000;

    /**
     * 生成密码哈希（新增/重置账号时用）。
     *
     * @param rawPassword 明文密码
     * @return 形如 {@code pbkdf2$310000$xxxx$yyyy} 的存储串
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, DEFAULT_ITERATIONS, KEY_BITS);

        Base64.Encoder encoder = Base64.getEncoder();
        return ID + "$" + DEFAULT_ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(hash);
    }

    /**
     * 校验明文密码是否与存储串匹配。
     *
     * <p>任何「存储串格式不合法」的情况都返回 {@code false} 而不是抛异常：
     * 一条坏数据不该让接口 500，更不该让攻击者通过报错差异推断出库里的内容。</p>
     *
     * <p>比较用 {@link MessageDigest#isEqual} —— 它是恒定时间比较。
     * 若用 {@code Arrays.equals}，比较会在第一个不同字节处提前返回，
     * 理论上可被「时序攻击」逐字节猜出哈希。这是密码校验场景的固定要求。</p>
     *
     * @param rawPassword 用户提交的明文密码
     * @param storedHash  库里的存储串
     * @return 匹配返回 {@code true}
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        // 用 split 的第二个参数 -1 保留末尾空段：
        // 否则 "a$b$c$" 这类末尾为空的串会被截掉一段，长度校验就失效了。
        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 4 || !ID.equals(parts[0])) {
            log.warn("密码哈希格式不合法（应为 {}$迭代次数$盐$哈希），已拒绝校验", ID);
            return false;
        }

        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            log.warn("密码哈希解析失败（迭代次数或 Base64 段损坏），已拒绝校验");
            return false;
        }

        if (iterations < MIN_ITERATIONS || salt.length == 0 || expected.length == 0) {
            log.warn("密码哈希参数异常（迭代次数 {}，盐 {} 字节，哈希 {} 字节），已拒绝校验",
                    iterations, salt.length, expected.length);
            return false;
        }

        // 用存储串里的迭代次数和盐重新算一遍 —— 这正是「参数随哈希存」的用处。
        // 密钥长度取自存储的哈希长度，而不是写死常量，这样将来换 keyBits 也能平滑兼容。
        byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 调用 JDK 做密钥派生。
     *
     * <p>{@code PBEKeySpec} 会内部拷贝一份密码字符数组，用完后显式 {@code clearPassword()}
     * 把它抹掉，减少明文在内存里停留的时间（堆转储、内存泄漏时可能被捞到）。
     * 注意这只清掉 spec 里的副本，调用方自己的 char[] 仍需自行处理。</p>
     */
    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            // 走到这里只可能是 JDK 不支持该算法或参数非法，属于环境/配置问题，不该被静默吞掉
            throw new IllegalStateException("密码哈希计算失败（算法 " + ALGORITHM + "）", e);
        } finally {
            spec.clearPassword();
        }
    }
}
