package com.wxpush.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 入站消息统一模型。
 *
 * <p>作用：把微信那套「首字母大写的 XML 字段」翻译成业务层认识的 Java 对象。
 * 业务层（handler）<b>永远只认这个模型</b>，不感知报文格式。
 * 这样将来微信调整字段，只需改 XML 解析器一处 —— 这就是适配器模式的价值。</p>
 */
@Data
@Builder
public class InboundMessage {

    /** 公众号原始 ID（微信报文里的 ToUserName），回复时要作为 FromUserName */
    private String toUserName;

    /** 发送者 openid（微信报文里的 FromUserName），回复时要作为 ToUserName */
    private String fromUserName;

    /** 消息创建时间，Unix 秒 */
    private Long createTime;

    /** 消息类型 */
    private MsgType msgType;

    /** 文本消息的正文；非文本消息为 null */
    private String content;

    /** 消息 ID，微信用于去重；<b>事件消息没有此字段，为 null</b>  */
    private String msgId;

    /** 事件类型，仅 MsgType = EVENT 时有值（subscribe / unsubscribe / CLICK ...） */
    private String event;

    /** 事件 key，如菜单点击事件的 EventKey */
    private String eventKey;
}
