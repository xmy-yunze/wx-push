package com.wxpush.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 消息流水的对外视图对象（VO）。
 *
 * <p>为什么不直接把实体 {@code WxMessageLog} 返回给前端？
 * 实体属于持久层，它的字段跟着表结构走；一旦数据库加字段或改列名，
 * 前端契约就会被连带打破。VO 是一道隔离层，让两边各自演进。</p>
 */
public record MessageVO(
        /** 主键 */
        Long id,
        /** 微信消息 ID；事件消息为 null */
        String msgId,
        /** 发送者 openid */
        String fromUser,
        /** 公众号原始 ID */
        String toUser,
        /** 消息类型：text / event ... */
        String msgType,
        /** 事件类型：subscribe / unsubscribe ...；非事件消息为 null */
        String event,
        /** 消息内容 */
        String content,
        /** 入库时间，统一序列化为 yyyy-MM-dd HH:mm:ss（默认 ISO 会带一个 T，前端展示要多一步转换） */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}
