package com.wxpush.dispatcher;

import com.wxpush.domain.MsgType;
import com.wxpush.handler.EventMessageHandler;
import com.wxpush.handler.MessageHandler;
import com.wxpush.handler.TextMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 处理器工厂测试 —— 验证「工厂 + 策略」的两条关键行为。
 */
class MessageHandlerFactoryTest {

    @Test
    @DisplayName("按消息类型取出对应处理器")
    void indexHandlerByType() {
        MessageHandlerFactory factory = new MessageHandlerFactory(
                List.of(new TextMessageHandler(), new EventMessageHandler()));

        assertInstanceOf(TextMessageHandler.class, factory.getHandler(MsgType.TEXT));
        assertInstanceOf(EventMessageHandler.class, factory.getHandler(MsgType.EVENT));
        assertTrue(factory.supportedTypes().containsAll(List.of(MsgType.TEXT, MsgType.EVENT)));
    }

    @Test
    @DisplayName("没有实现的类型返回 null（调用方按「不回复」处理）")
    void unknownTypeReturnsNull() {
        MessageHandlerFactory factory = new MessageHandlerFactory(List.of(new TextMessageHandler()));

        assertNull(factory.getHandler(MsgType.IMAGE));
        assertNull(factory.getHandler(null));
    }

    @Test
    @DisplayName("同一消息类型注册两个处理器 → 启动即失败，不做模糊兜底")
    void duplicateTypeFailsFast() {
        List<MessageHandler> duplicated = List.of(new TextMessageHandler(), new TextMessageHandler());

        assertThrows(IllegalStateException.class, () -> new MessageHandlerFactory(duplicated));
    }
}
