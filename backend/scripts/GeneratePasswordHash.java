import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生成 admin 账号的密码哈希（PBKDF2-HMAC-SHA256）。
 *
 * <p>用法（JDK 11+ 支持直接运行单文件源码，不需要编译、不需要配 classpath）：</p>
 * <pre>
 *   cd backend
 *   java scripts/GeneratePasswordHash.java 你想设的密码
 * </pre>
 *
 * <p>输出形如 {@code pbkdf2$310000$xxxx$yyyy}，把它填进 {@code admin_user} 表的
 * {@code password_hash} 列即可。密码自始至终只经过你自己的终端。</p>
 *
 * <p><b>为什么不存 MD5 / SHA1？</b> 那类算法算得太快 —— 现代显卡每秒能试上百亿次，
 * 加不加盐都挡不住撞库。PBKDF2 通过「加盐 + 高迭代次数」把单次校验成本抬高到毫秒级，
 * 让暴力破解在经济上不划算。</p>
 *
 * <p><b>为什么把参数写进存储串？</b> 这样将来调高迭代次数时，
 * 老密码仍能用它当年那套参数验证通过，不需要强制所有人改密码。</p>
 */
public class GeneratePasswordHash {

    /** JDK 自带的密钥派生算法，无需引入任何第三方依赖 */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 迭代次数。越高越安全、也越慢；30 万次在现代机器上约需几十毫秒，是合理平衡点 */
    private static final int ITERATIONS = 310_000;

    /** 盐长度（字节）。每个账号一个随机盐，让相同密码产生不同哈希，防彩虹表 */
    private static final int SALT_BYTES = 16;

    /** 输出哈希长度（位）*/
    private static final int KEY_BITS = 256;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("用法：java scripts/GeneratePasswordHash.java <密码>");
            System.err.println("示例：java scripts/GeneratePasswordHash.java myPassword123");
            System.exit(1);
        }

        char[] password = args[0].toCharArray();

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_BITS);

        Base64.Encoder encoder = Base64.getEncoder();
        System.out.println("pbkdf2$" + ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(hash));
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("生成密码哈希失败", e);
        }
    }
}
