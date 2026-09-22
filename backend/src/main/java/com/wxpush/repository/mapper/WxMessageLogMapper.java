package com.wxpush.repository.mapper;

import com.wxpush.repository.entity.WxMessageLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信消息流水 Mapper（原生 MyBatis，SQL 写在 XML 里）。
 *
 * <p>XML 位置：{@code src/main/resources/mapper/WxMessageLogMapper.xml}，
 * 其中 {@code namespace} 必须是本接口的全限定名，方法名与 XML 中语句的 {@code id} 一一对应。</p>
 *
 * <p>参数一律用 {@code @Param} 显式命名 —— 虽然项目编译时开了 {@code -parameters}，
 * 不写也能识别，但显式命名让 XML 里的 {@code #{msgType}} 与 Java 参数名有确定的对应关系，
 * 改名时不容易悄悄错位。</p>
 */
public interface WxMessageLogMapper {

    /**
     * 幂等写入一条消息流水。
     *
     * <p>SQL 用 {@code INSERT IGNORE}：命中 {@code dedup_key} 唯一索引时静默跳过，不抛异常。
     * 去重键由 {@link com.wxpush.domain.DedupKey} 计算 —— 普通消息用 {@code msg:<MsgId>}，
     * 事件消息用 {@code evt:<FromUser>:<Event>:<EventKey>:<CreateTime>}。
     * <b>不能用 msg_id 做唯一键</b>：事件消息的 msg_id 为 null，而唯一索引不比较 null。</p>
     *
     * @param entity 消息流水（{@code dedupKey} 为 null 时退化为不去重）
     * @return 实际写入行数；{@code 0} 表示该消息已存在（微信重试推送）
     */
    int insertIgnore(WxMessageLog entity);

    /**
     * 按消息 ID 查询，主要用于本地验证落库是否成功。
     *
     * <p>⚠️ 两条限制：① 事件消息没有 MsgId，用本方法查不到；
     * ② {@code msg_id} 已降级为普通索引（不再唯一），理论上可能匹配多行，故取第一条。</p>
     *
     * @param msgId 微信消息 ID
     * @return 匹配的记录；不存在返回 {@code null}
     */
    WxMessageLog selectByMsgId(String msgId);

    /**
     * 按主键查询（admin 消息详情用）。
     *
     * @param id 主键
     * @return 匹配的记录；不存在返回 {@code null}
     */
    WxMessageLog selectById(@Param("id") Long id);

    /**
     * 条件分页查询，按 id 倒序（最新在前）。
     *
     * <p>分页用 {@code LIMIT ? OFFSET ?} 手写，没引 PageHelper ——
     * 练手项目优先把「分页到底是怎么实现的」搞清楚。</p>
     *
     * @param offset 偏移量 = (page - 1) * size，由 Service 算好传入
     * @param limit  每页条数
     */
    List<WxMessageLog> selectPage(@Param("msgType") String msgType,
                                  @Param("event") String event,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /**
     * 条件计数，WHERE 子句与 {@link #selectPage} 完全共用同一段 SQL 片段，
     * 保证「总数」和「列表」永远是同一套筛选条件下的结果。
     */
    long countByCondition(@Param("msgType") String msgType,
                          @Param("event") String event,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("keyword") String keyword);
}
