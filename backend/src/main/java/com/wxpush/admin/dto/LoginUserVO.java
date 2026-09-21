package com.wxpush.admin.dto;

/**
 * 登录用户信息，用于 {@code /api/auth/login} 与 {@code /api/auth/me} 的响应。
 *
 * <p>⚠️ <b>绝不含 {@code passwordHash}</b> —— 这是对外结构，
 * 任何密码相关字段都不允许出现在这里。</p>
 *
 * @param id          账号主键
 * @param username    登录账号
 * @param displayName 显示名；可能为 null，前端需回退显示 username
 */
public record LoginUserVO(Long id, String username, String displayName) {
}
