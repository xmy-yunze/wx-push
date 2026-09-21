package com.wxpush.repository.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台账号实体，对应表 {@code admin_user}。
 *
 * <p>字段名用驼峰，靠 MyBatis 的 {@code map-underscore-to-camel-case} 与
 * 下划线列名自动对应（XML 里也显式写了 resultMap，两种方式都保留，便于对照学习）。</p>
 *
 * <p><b>⚠️ 这个对象含有密码哈希，绝不要直接序列化后返回给前端。</b>
 * 对外一律转成 {@code LoginUserVO} 或 {@code SessionUser}。</p>
 */
@Data
@Builder
public class AdminUser {

    /** 主键，由数据库自增回填 */
    private Long id;

    /** 登录账号，数据库上有唯一索引 {@code uk_username} 兜底防重 */
    private String username;

    /**
     * 密码哈希，存储格式为自描述的 {@code pbkdf2$迭代次数$盐$哈希}。
     *
     * <p>库里<b>没有明文密码</b>，也不存可逆加密 —— 忘了密码只能重置，取不回来。</p>
     */
    private String passwordHash;

    /** 显示名，用于顶栏展示；可以为空，为空时前端回退显示 username */
    private String displayName;

    /** 状态：1 = 启用，0 = 停用。停用而非删除，是为了保留审计线索 */
    private Integer status;

    /** 最后一次登录成功的时间；从未登录为 null */
    private LocalDateTime lastLoginAt;

    /** 创建时间，由数据库 DEFAULT CURRENT_TIMESTAMP 生成 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    private LocalDateTime updatedAt;
}
