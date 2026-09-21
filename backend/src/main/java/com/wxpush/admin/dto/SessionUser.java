package com.wxpush.admin.dto;

import java.io.Serializable;

/**
 * 存进 HttpSession 的「当前登录者」信息。
 *
 * <p><b>为什么不直接把 {@code AdminUser} 实体塞进 Session？</b> 三个原因：</p>
 * <ol>
 *   <li><b>防止密码哈希四处扩散</b> —— 实体里带着 {@code passwordHash}，
 *       存进 Session 等于把它复制到会话存储里，能少一处就少一处；</li>
 *   <li><b>会话只存必要字段</b> —— 会话存储（尤其是将来的 Redis）不该被当成缓存表用；</li>
 *   <li><b>可序列化</b> —— 现在 Session 在内存里无所谓，将来换成 Redis 时对象必须能序列化。
 *       实现 {@link Serializable} 是为那一步提前铺路，避免到时候才发现存不进去。</li>
 * </ol>
 *
 * @param id          账号主键
 * @param username    登录账号
 * @param displayName 显示名；可能为 null，前端需回退显示 username
 */
public record SessionUser(Long id, String username, String displayName) implements Serializable {
}
