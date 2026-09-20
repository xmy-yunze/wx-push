package com.wxpush.admin.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 消息列表的查询条件，由 Spring 从 URL 查询参数自动绑定（setter 注入）。
 *
 * <p>注意这里是<b>原始入参</b>：{@code page} 可能是 0 或负数、{@code size} 可能写成 99999，
 * 真正的边界校正由 Service 统一负责，Controller 不做业务判断。</p>
 */
@Data
public class AdminMessageQuery {

    /** 页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 20;

    /** 消息类型筛选：text / event ...；不传 = 全部 */
    private String msgType;

    /** 事件类型筛选：subscribe / unsubscribe ...；不传 = 全部 */
    private String event;

    /** 起始日期（含），格式 yyyy-MM-dd；不传 = 不限 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期（含），格式 yyyy-MM-dd；不传 = 不限 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 关键词，模糊匹配发送者 openid 或消息内容；不传 = 不限 */
    private String keyword;
}
