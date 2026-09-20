package com.wxpush.domain;

/**
 * 出站回复消息的统一抽象。
 *
 * <p>业务层产出的是「回复对象」，不是 XML 报文。
 * 报文格式的组装由接入层（{@code WxReplyXmlBuilder}）负责，业务层不感知。</p>
 */
public interface ReplyMessage {

    /** 接收方：即回复时的用户 openid */
    String toUserName();

    /** 发送方：即公众号原始 ID */
    String fromUserName();

    /** 回复的创建时间（Unix 秒） */
    long createTime();

    /** 本条回复对应的消息类型，供报文组装层分发 */
    MsgType replyType();
}
