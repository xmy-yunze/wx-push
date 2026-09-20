package com.wxpush.repository.mapper;

import com.wxpush.repository.dto.DailyCount;
import com.wxpush.repository.dto.TypeCount;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息统计查询 Mapper。
 *
 * <p>为什么和 {@link WxMessageLogMapper} 分开？
 * 那边是「按行取数据」，这边是「聚合出指标」，SQL 形态完全不同；
 * 混在一个接口里，类会越来越长，职责也说不清。</p>
 */
public interface WxMessageStatsMapper {

    /** 消息总条数 */
    long countAll();

    /**
     * 统计某个时间点之后的消息条数（「今日消息」= 传今天 00:00）。
     *
     * @param since 起始时间（含）
     */
    long countSince(@Param("since") LocalDateTime since);

    /**
     * 按事件类型计数，例如 {@code subscribe} / {@code unsubscribe}。
     *
     * @param event 事件名
     */
    long countByEvent(@Param("event") String event);

    /** 去重后的活跃用户数（按 from_user 去重） */
    long countDistinctUsers();

    /**
     * 按天聚合消息量，用于趋势折线图。
     *
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     */
    List<DailyCount> selectDailyCount(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /** 按消息类型聚合条数，用于类型分布饼图 */
    List<TypeCount> selectTypeDistribution();
}
