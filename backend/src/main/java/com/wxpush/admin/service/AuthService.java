package com.wxpush.admin.service;

import com.wxpush.admin.support.PasswordEncoder;
import com.wxpush.admin.support.UnauthorizedException;
import com.wxpush.repository.entity.AdminUser;
import com.wxpush.repository.mapper.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理后台鉴权服务 —— 只负责<b>校验账号密码</b>，不碰 HttpSession。
 *
 * <p>这条边界是刻意划的：Session 属于 Web 层（Servlet API），
 * 一旦 Service 依赖 {@code HttpSession}，单元测试就必须构造 Web 容器，
 * 而且将来换鉴权载体（Session → Token）时业务代码也要跟着改。
 * 现在 Service 的依赖只有「一个 Mapper + 一个编码器」，测试里手写假 Mapper 即可跑通。</p>
 */
@Slf4j
@Service
public class AuthService {

    /**
     * 当前登录者在 Session 中的键名。
     *
     * <p>加前缀而非直接叫 {@code "user"}：Session 里可能还有别的属性，
     * 名字太泛早晚撞车。</p>
     */
    public static final String SESSION_USER_KEY = "WX_PUSH_ADMIN_USER";

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AdminUserMapper adminUserMapper, PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 校验账号密码。
     *
     * <p><b>⚠️ 判断顺序是有讲究的：先校密码，再查启用状态。</b>
     * 如果反过来先查 {@code status}，那么攻击者用一个错误密码也能收到
     * 「账号已被停用」的提示 —— 等于免费得到一个「这个账号存在」的确认信号。
     * 先验密码，则「停用」提示只在密码正确时出现，不泄露额外信息。</p>
     *
     * <p>另外，账号不存在与密码错误返回<b>完全相同</b>的提示，
     * 避免「账号枚举」：攻击者无法通过提示差异判断某个账号是否存在。</p>
     *
     * @param username    登录账号
     * @param rawPassword 明文密码
     * @return 校验通过的账号实体
     * @throws UnauthorizedException 账号密码不正确、或账号已被停用
     */
    public AdminUser authenticate(String username, String rawPassword) {
        // 空值先挡掉。注意：这里也回「账号或密码错误」，
        // 不要回「请填写密码」之类的差异化提示，保持一致。
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new UnauthorizedException("账号或密码错误");
        }

        AdminUser user = adminUserMapper.selectByUsername(username.trim());

        // 用短路求值把「账号不存在」和「密码不对」合并成同一个分支：
        // user 为 null 时根本不会去算哈希，但对外表现完全一致。
        boolean passwordOk = user != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!passwordOk) {
            // 日志里也不记「账号不存在」这种细节差异，只记被拒绝的事实。
            // 记 username 是必要的 —— 排查「有人一直在试密码」时需要它。
            log.warn("登录失败：账号或密码不正确（username={}）", username);
            throw new UnauthorizedException("账号或密码错误");
        }

        if (!isEnabled(user)) {
            log.warn("登录失败：账号已被停用（id={}, username={}）", user.getId(), user.getUsername());
            throw new UnauthorizedException("账号已被停用，请联系管理员");
        }

        recordLogin(user.getId());
        log.info("登录成功：id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /**
     * 账号是否处于启用状态。
     *
     * <p>显式判空：数据库列虽然 NOT NULL DEFAULT 1，但从别的路径读进来可能是 null，
     * 那时 {@code status == 1} 拆箱会直接 NPE。</p>
     */
    private boolean isEnabled(AdminUser user) {
        return user.getStatus() != null && user.getStatus() == 1;
    }

    /**
     * 记录本次登录时间。
     *
     * <p><b>写失败只记日志、不往上抛</b>：这是有意的取舍 ——
     * {@code last_login_at} 只是审计信息，不该因为它写不进去就把用户挡在门外
     * （密码已经校验通过了）。真出问题，日志里能看到。</p>
     */
    private void recordLogin(Long userId) {
        try {
            adminUserMapper.updateLastLoginAt(userId);
        } catch (Exception e) {
            log.warn("更新最后登录时间失败（不影响本次登录）：userId={}", userId, e);
        }
    }
}
