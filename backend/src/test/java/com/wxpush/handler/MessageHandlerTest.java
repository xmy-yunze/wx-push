package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.reply.TextReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 消息处理器测试。
 *
 * <p>这里同时验证了模板方法骨架的行为：异常被兜底成「不回复」，不会往上抛。</p>
 */
class MessageHandlerTest {

    private final TextMessageHandler textHandler = new TextMessageHandler();
    private final EventMessageHandler eventHandler = new EventMessageHandler();

    private static InboundMessage text(String content) {
        return InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.TEXT)
                .content(content)
                .build();
    }

    @Test
    @DisplayName("文本消息：回声并带前缀")
    void textEcho() {
        ReplyMessage reply = textHandler.handle(text("你好"));

        TextReply textReply = assertInstanceOf(TextReply.class, reply);
        assertEquals("你说的是：你好", textReply.content());
        assertEquals(MsgType.TEXT, textHandler.supportedType());
    }

    @Test
    @DisplayName("文本消息：Content 为空时也不抛异常")
    void textNullContent() {
        TextReply reply = assertInstanceOf(TextReply.class, textHandler.handle(text(null)));
        assertEquals("你说的是：", reply.content());
    }

    @Test
    @DisplayName("关注事件：回复欢迎语")
    void subscribeRepliesWelcome() {
        InboundMessage event = InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                .event("subscribe")
                .build();

        TextReply reply = assertInstanceOf(TextReply.class, eventHandler.handle(event));
        assertEquals(EventMessageHandler.WELCOME, reply.content());
    }

    @Test
    @DisplayName("取关事件：不回复")
    void unsubscribeRepliesNothing() {
        InboundMessage event = InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                .event("unsubscribe")
                .build();

        assertNull(eventHandler.handle(event));
    }

    @Test
    @DisplayName("未处理的事件类型：不回复，也不抛异常")
    void otherEventRepliesNothing() {
        InboundMessage event = InboundMessage.builder()
                .msgType(MsgType.EVENT)
                .event("CLICK")
                .build();

        assertNull(eventHandler.handle(event));
    }
}
