package com.wxpush.repository.dto;

import lombok.Data;

/**
 * 消息类型分布 —— 统计查询的专用结果载体。
 *
 * <p>{@code name} / {@code value} 是 ECharts 饼图 {@code series.data} 的标准字段名，
 * SQL 里直接起成这两个别名，前端拿到就能用，不用再改字段名。</p>
 */
@Data
public class TypeCount {

    /** 类型名：text / event / image ... */
    private String name;

    /** 该类型的消息条数 */
    private Long value;
}
