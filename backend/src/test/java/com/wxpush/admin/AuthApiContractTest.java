package com.wxpush.admin;

import com.wxpush.admin.controller.AdminMessageController;
import com.wxpush.admin.controller.AuthController;
import com.wxpush.admin.dto.SessionUser;
import com.wxpush.admin.service.AdminMessageService;
import com.wxpush.admin.service.AuthService;
import com.wxpush.admin.support.GlobalExceptionHandler;
import com.wxpush.admin.support.LoginInterceptor;
import com.wxpush.admin.support.PasswordEncoder;
import com.wxpush.repository.entity.AdminUser;
import com.wxpush.repository.entity.WxMessageLog;
import com.wxpush.repository.mapper.AdminUserMapper;
import com.wxpush.repository.mapper.WxMessageLogMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权接口的 HTTP 契约测试 —— 含<b>拦截器</b>，验证门禁真的装上了。
 *
 * <p>测三件事：</p>
 * <ol>
 *   <li>登录接口的入参与响应字段名（前端已按契约实现，改坏了前端就静默拿到 undefined）；</li>
 *   <li>未登录访问受保护接口必须是 {@code 401}，且响应结构与业务接口一致；</li>
 *   <li>白名单确实放行、登录后确实能通行、登出后确实被拦 —— 也就是「门禁是活的」。</li>
 * </ol>
 *
 * <p>standalone 模式的 MockMvc：不启 Spring 容器、不连数据库，毫秒级跑完。
 * 拦截器用 {@code addInterceptors} 挂上，作用于所有请求 ——
 * 因为免登录白名单已写在 {@link LoginInterceptor} 内部，这里的行为与生产一致。</p>
 */
@DisplayName("鉴权接口契约")
class AuthApiContractTest {

    private static final PasswordEncoder ENCODER = new PasswordEncoder();
    private static final String RAW_PASSWORD = "correct-password-2026";

    /** 30 万次迭代只算一次 */
    private static AdminUser admin;

    private MockMvc mockMvc;

    @BeforeAll
    static void buildAdmin() {
        admin = AdminUser.builder()
                .id(1L)
                .username("admin")
                .passwordHash(ENCODER.encode(RAW_PASSWORD))
                .displayName("云泽")
                .status(1)
                .build();
    }

    @BeforeEach
    void setUp() {
        AuthService authService = new AuthService(new StubAdminUserMapper(), ENCODER);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AuthController(authService),
                        new AdminMessageController(new AdminMessageService(new StubMessageMapper())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new LoginInterceptor())
                .build();
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("POST /api/auth/login → 成功返回 id / username / displayName")
    void login_成功() throws Exception {
        mockMvc.perform(login(RAW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.displayName").value("云泽"));
    }

    @Test
    @DisplayName("POST /api/auth/login → 响应里绝不能出现密码哈希")
    void login_不泄露密码哈希() throws Exception {
        mockMvc.perform(login(RAW_PASSWORD))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login → 密码错误返回 401 + code 401")
    void login_密码错误() throws Exception {
        mockMvc.perform(login("wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login → 空请求体返回 401，而不是 500")
    void login_空请求体() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录成功后会话 ID 必须更换（防会话固定攻击）")
    void login_更换会话ID() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mockMvc.perform(login(RAW_PASSWORD).session(before))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession after = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(after);
        assertNotEquals(idBefore, after.getId(),
                "登录前后会话 ID 若不变，攻击者可以预先把自己的 ID 塞给受害者再直接复用");
    }

    @Test
    @DisplayName("会话里存的是 SessionUser，不是带密码哈希的实体")
    void login_会话不存密码哈希() throws Exception {
        MvcResult result = mockMvc.perform(login(RAW_PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        Object stored = session.getAttribute(AuthService.SESSION_USER_KEY);
        assertNotNull(stored, "登录成功必须把当前用户写进会话");
        assertInstanceOf(SessionUser.class, stored,
                "会话里应存精简的 SessionUser，避免密码哈希被复制进会话存储");
    }

    // ==================== 门禁 ====================

    @Test
    @DisplayName("未登录访问 /api/messages → 401「未登录或登录已过期」")
    void 未登录_被拦截() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("未登录访问 /api/auth/me → 401（它就靠这个来探测登录态）")
    void 未登录_访问me被拦截() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录后可以访问受保护接口")
    void 已登录_放行() throws Exception {
        mockMvc.perform(get("/api/messages").session(loginAndGetSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("登录后 /api/auth/me 返回当前用户")
    void 已登录_返回当前用户() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(loginAndGetSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.displayName").value("云泽"));
    }

    @Test
    @DisplayName("登出后原会话失效，再访问受保护接口被拦")
    void 登出_会话失效() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/messages").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("未登录直接调登出 → 幂等成功（白名单放行，不报 401）")
    void 未登录_登出幂等() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("白名单只放行登录接口，其它 /api/auth/** 仍需登录")
    void 白名单_不外溢() throws Exception {
        // 若有人把白名单写成 /api/auth/**，下面这个断言就会失败
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 测试辅助 ====================

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}");
    }

    /** 走一次真实登录，拿到会话供后续请求复用 */
    private MockHttpSession loginAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(login(RAW_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ==================== 测试替身 ====================

    private static class StubAdminUserMapper implements AdminUserMapper {

        @Override
        public AdminUser selectByUsername(String username) {
            return admin.getUsername().equals(username) ? admin : null;
        }

        @Override
        public AdminUser selectById(Long id) {
            return admin;
        }

        @Override
        public int updateLastLoginAt(Long id) {
            return 1;
        }
    }

    /** 与数据库第 1 行真实数据一致的固定返回 */
    private static class StubMessageMapper implements WxMessageLogMapper {

        private static WxMessageLog sample() {
            return WxMessageLog.builder()
                    .id(1L)
                    .msgId("1234567890123456")
                    .fromUser("oUserOpenid")
                    .toUser("gh_abc123")
                    .msgType("text")
                    .event(null)
                    .content("你好")
                    .createdAt(LocalDateTime.of(2026, 9, 20, 21, 55, 9))
                    .build();
        }

        @Override
        public int insertIgnore(WxMessageLog entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WxMessageLog selectByMsgId(String msgId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WxMessageLog selectById(Long id) {
            return sample();
        }

        @Override
        public List<WxMessageLog> selectPage(String msgType, String event, LocalDateTime startTime,
                                             LocalDateTime endTime, String keyword, int offset, int limit) {
            return List.of(sample());
        }

        @Override
        public long countByCondition(String msgType, String event, LocalDateTime startTime,
                                     LocalDateTime endTime, String keyword) {
            return 1L;
        }
    }
}
