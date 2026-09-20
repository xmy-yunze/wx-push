package com.wxpush.repository.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信消息流水实体，对应表 {@code wx_message_log}。
 *
 * <p>字段名用驼峰，靠 MyBatis 的 {@code map-underscore-to-camel-case} 与
 * 下划线列名自动对应（XML 里也显式写了 resultMap，两种方式都保留，便于对照学习）。</p>
 */
@Data
@Builder
public class WxMessageLog {

    /** 主键，由数据库自增回填 */
    private Long id;

    /** 微信消息 ID；<b>事件消息没有该字段，为 null</b> */
    private String msgId;

    /** 发送者 openid */
    private String fromUser;

    /** 公众号原始 ID */
    private String toUser;

    /** 消息类型：text / image / event ... */
    private String msgType;

    /** 事件类型：subscribe / unsubscribe / CLICK ...；非事件消息为 null */
    private String event;

    /** 消息内容（文本消息为正文，其它类型可能为空） */
    private String content;

    /** 入库时间，由数据库 NOW() 生成 */
    private LocalDateTime createdAt;
}
