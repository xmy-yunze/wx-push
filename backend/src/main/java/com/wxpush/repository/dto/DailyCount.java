package com.wxpush.repository.dto;

import lombok.Data;

/**
 * 按天聚合的消息量 —— 统计查询的专用结果载体。
 *
 * <p>字段名与 SQL 里的列别名一一对应，靠 MyBatis 自动映射填充。
 * 由于要交给 ECharts 折线图，{@code date} 在 SQL 里就用 {@code DATE_FORMAT} 转成了
 * 字符串，避免 {@code java.sql.Date} 序列化出来带时区后缀。</p>
 */
@Data
public class DailyCount {

    /** 日期，格式 yyyy-MM-dd */
    private String date;

    /** 当天消息总数 */
    private Long total;

    /** 当天关注次数 */
    private Long subscribe;

    /** 当天取关次数 */
    private Long unsubscribe;
}
