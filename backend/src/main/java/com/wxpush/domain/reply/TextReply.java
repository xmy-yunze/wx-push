package com.wxpush.domain.reply;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;

/**
 * 文本回复。
 *
 * @param toUserName  接收方（用户 openid）
 * @param fromUserName 发送方（公众号原始 ID）
 * @param createTime  创建时间（Unix 秒）
 * @param content     回复正文
 */
public record TextReply(
        String toUserName,
        String fromUserName,
        long createTime,
        String content
) implements ReplyMessage {

    @Override
    public MsgType replyType() {
        return MsgType.TEXT;
    }

    /**
     * 基于入站消息构造一条文本回复。
     *
     * <p>⚠️ 这里是本项目最容易出错、也最隐蔽的地方：<b>收发双方必须互换</b>。
     * 入站报文的 {@code FromUserName} 是用户、{@code ToUserName} 是公众号；
     * 回复时方向反转，用户变成接收方、公众号变成发送方。
     * 把这一步固化在工厂方法里，调用方就不可能写反。</p>
     *
     * @param inbound 收到的用户消息
     * @param content 要回复的正文
     */
    public static TextReply of(InboundMessage inbound, String content) {
        return new TextReply(
                inbound.getFromUserName(),                       // 原发送者 → 新的接收者
                inbound.getToUserName(),                         // 原接收者 → 新的发送者
                System.currentTimeMillis() / 1000L,
                content
        );
    }
}
