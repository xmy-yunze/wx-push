package com.wxpush.admin;

import com.wxpush.admin.service.AuthService;
import com.wxpush.admin.support.PasswordEncoder;
import com.wxpush.admin.support.UnauthorizedException;
import com.wxpush.repository.entity.AdminUser;
import com.wxpush.repository.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 鉴权服务的单元测试 —— 重点在「什么情况下必须拒绝」，以及拒绝时<b>不能泄露什么信息</b>。
 *
 * <p>和 {@code AdminServiceTest} 一样手写假 Mapper、不用 Mockito：
 * 依赖只有一个接口，写个记录参数的假实现既直观又能反过来断言 Service 传了什么下去。</p>
 *
 * <p>本测试<b>完全不碰数据库</b>。</p>
 */
@DisplayName("鉴权服务")
class AuthServiceTest {

    private static final PasswordEncoder ENCODER = new PasswordEncoder();

    /** 30 万次迭代不便宜，整个测试类只算一次，之后复用 */
    private static String correctHash;

    private static final String RAW_PASSWORD = "correct-password-2026";

    @BeforeAll
    static void hashOnce() {
        correctHash = ENCODER.encode(RAW_PASSWORD);
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("账号密码正确 → 返回账号实体")
    void authenticate_成功() {
        StubMapper mapper = new StubMapper(enabledUser());
        AdminUser result = new AuthService(mapper, ENCODER).authenticate("admin", RAW_PASSWORD);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("admin", result.getUsername());
    }

    @Test
    @DisplayName("账号首尾空格被忽略")
    void authenticate_忽略账号首尾空格() {
        StubMapper mapper = new StubMapper(enabledUser());
        new AuthService(mapper, ENCODER).authenticate("  admin  ", RAW_PASSWORD);

        assertEquals("admin", mapper.capturedUsername,
                "传给 Mapper 的应是 trim 后的账号，避免「复制粘贴带了空格」就登不进去");
    }

    @Test
    @DisplayName("登录成功时刷新最后登录时间")
    void authenticate_刷新登录时间() {
        StubMapper mapper = new StubMapper(enabledUser());
        new AuthService(mapper, ENCODER).authenticate("admin", RAW_PASSWORD);

        assertTrue(mapper.lastLoginUpdated, "登录成功应记录 last_login_at");
    }

    @Test
    @DisplayName("刷新登录时间失败不影响登录成功")
    void authenticate_记录登录时间失败不阻断登录() {
        StubMapper mapper = new StubMapper(enabledUser());
        mapper.failOnLastLoginUpdate = true;

        assertDoesNotThrow(() -> new AuthService(mapper, ENCODER).authenticate("admin", RAW_PASSWORD),
                "last_login_at 只是审计信息，不该因为它写不进去就把用户挡在门外");
    }

    // ==================== 拒绝路径 ====================

    @Test
    @DisplayName("账号不存在 → UnauthorizedException")
    void authenticate_账号不存在() {
        StubMapper mapper = new StubMapper(null);
        AuthService service = new AuthService(mapper, ENCODER);

        assertThrows(UnauthorizedException.class, () -> service.authenticate("nobody", RAW_PASSWORD));
    }

    @Test
    @DisplayName("密码错误 → UnauthorizedException")
    void authenticate_密码错误() {
        StubMapper mapper = new StubMapper(enabledUser());
        AuthService service = new AuthService(mapper, ENCODER);

        assertThrows(UnauthorizedException.class, () -> service.authenticate("admin", "wrong-password"));
    }

    @Test
    @DisplayName("账号不存在与密码错误的提示必须完全一致（防账号枚举）")
    void authenticate_不泄露账号是否存在() {
        AuthService notExist = new AuthService(new StubMapper(null), ENCODER);
        AuthService wrongPwd = new AuthService(new StubMapper(enabledUser()), ENCODER);

        String msg1 = assertThrows(UnauthorizedException.class,
                () -> notExist.authenticate("nobody", RAW_PASSWORD)).getMessage();
        String msg2 = assertThrows(UnauthorizedException.class,
                () -> wrongPwd.authenticate("admin", "wrong-password")).getMessage();

        assertEquals(msg1, msg2,
                "两种失败的提示若不同，攻击者就能靠提示差异判断出哪些账号真实存在");
    }

    @Test
    @DisplayName("账号为空 / 密码为空 → 一律拒绝，提示与密码错误相同")
    void authenticate_空入参被拒() {
        StubMapper mapper = new StubMapper(enabledUser());
        AuthService service = new AuthService(mapper, ENCODER);

        String expected = assertThrows(UnauthorizedException.class,
                () -> service.authenticate("admin", "wrong")).getMessage();

        assertEquals(expected, assertThrows(UnauthorizedException.class,
                () -> service.authenticate(null, RAW_PASSWORD)).getMessage());
        assertEquals(expected, assertThrows(UnauthorizedException.class,
                () -> service.authenticate("   ", RAW_PASSWORD)).getMessage());
        assertEquals(expected, assertThrows(UnauthorizedException.class,
                () -> service.authenticate("admin", null)).getMessage());
        assertEquals(expected, assertThrows(UnauthorizedException.class,
                () -> service.authenticate("admin", "")).getMessage());
    }

    @Test
    @DisplayName("账号被停用（密码正确）→ 单独提示已停用")
    void authenticate_停用账号() {
        StubMapper mapper = new StubMapper(userWithStatus(0));
        AuthService service = new AuthService(mapper, ENCODER);

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> service.authenticate("admin", RAW_PASSWORD));

        assertTrue(e.getMessage().contains("停用"), "密码正确时应明确告知账号已停用，便于运营者排查");
        assertFalse(mapper.lastLoginUpdated, "被停用的账号不应刷新登录时间");
    }

    @Test
    @DisplayName("账号被停用但密码错误 → 仍只回「账号或密码错误」，不暴露账号存在")
    void authenticate_停用账号密码错误时不泄露状态() {
        AuthService notExist = new AuthService(new StubMapper(null), ENCODER);
        AuthService disabledWrongPwd = new AuthService(new StubMapper(userWithStatus(0)), ENCODER);

        String msgNotExist = assertThrows(UnauthorizedException.class,
                () -> notExist.authenticate("admin", "wrong")).getMessage();
        String msgDisabled = assertThrows(UnauthorizedException.class,
                () -> disabledWrongPwd.authenticate("admin", "wrong")).getMessage();

        assertEquals(msgNotExist, msgDisabled,
                "若这里返回「账号已停用」，等于告诉攻击者「该账号存在」，"
                        + "所以必须先验密码、再查状态");
    }

    // ==================== 测试替身 ====================

    private static AdminUser enabledUser() {
        return userWithStatus(1);
    }

    private static AdminUser userWithStatus(int status) {
        return AdminUser.builder()
                .id(1L)
                .username("admin")
                .passwordHash(correctHash)
                .displayName("云泽")
                .status(status)
                .build();
    }

    /** 假 Mapper：返回预设账号，同时记录收到的账号与是否刷新过登录时间 */
    private static class StubMapper implements AdminUserMapper {

        private final AdminUser userToReturn;
        String capturedUsername;
        boolean lastLoginUpdated = false;
        boolean failOnLastLoginUpdate = false;

        StubMapper(AdminUser userToReturn) {
            this.userToReturn = userToReturn;
        }

        @Override
        public AdminUser selectByUsername(String username) {
            capturedUsername = username;
            // 模拟真实 SQL 的语义：账号对不上就是 null
            return userToReturn != null && userToReturn.getUsername().equals(username) ? userToReturn : null;
        }

        @Override
        public AdminUser selectById(Long id) {
            return userToReturn;
        }

        @Override
        public int updateLastLoginAt(Long id) {
            if (failOnLastLoginUpdate) {
                throw new IllegalStateException("模拟数据库故障");
            }
            lastLoginUpdated = true;
            return 1;
        }
    }
}
