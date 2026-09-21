package com.wxpush.repository.mapper;

import com.wxpush.repository.entity.AdminUser;
import org.apache.ibatis.annotations.Param;

/**
 * 管理后台账号 Mapper（原生 MyBatis，SQL 写在 XML 里）。
 *
 * <p>XML 位置：{@code src/main/resources/mapper/AdminUserMapper.xml}。
 * 该接口位于 {@code com.wxpush.repository.mapper} 包下，
 * 已由启动类的 {@code @MapperScan} 统一扫描，不需要额外加 {@code @Mapper} 注解。</p>
 *
 * <p>参数一律用 {@code @Param} 显式命名，让 XML 里的 {@code #{username}} 有确定的对应关系。</p>
 */
public interface AdminUserMapper {

    /**
     * 按登录账号查询账号。
     *
     * <p>⚠️ 注意 MySQL 的 collation 是 {@code utf8mb4_0900_ai_ci}，其中 {@code ci} = case-insensitive，
     * 所以 {@code 'Admin'} 和 {@code 'admin'} 会命中同一行。对账号登录是好事（少一次「大小写错了」的困惑），
     * 但要知道这个行为是数据库给的，不是代码写的。</p>
     *
     * @param username 登录账号
     * @return 匹配的账号；不存在返回 {@code null}
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 按主键查询账号。
     *
     * <p>登录后若需要读取账号的最新状态（比如被停用、改名），用它按 Session 里的 id 回查。</p>
     *
     * @param id 主键
     * @return 匹配的账号；不存在返回 {@code null}
     */
    AdminUser selectById(@Param("id") Long id);

    /**
     * 刷新最后一次登录时间。
     *
     * <p>刻意<b>不</b>用 {@code updated_at} 代替：{@code updated_at} 会在任何字段变更时自动更新，
     * 无法单独表达「这个人什么时候登录过」。</p>
     *
     * @param id 主键
     * @return 受影响行数；{@code 0} 表示账号不存在
     */
    int updateLastLoginAt(@Param("id") Long id);
}
