package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.reply.TextReply;
import org.springframework.stereotype.Component;

/**
 * 文本消息处理器。
 *
 * <p>P0 阶段实现「回声」：用户发什么就回什么（加个前缀便于肉眼确认链路通了）。</p>
 */
@Component
public class TextMessageHandler extends AbstractMessageHandler {

    /** 回复前缀，用于直观确认消息确实经过了我们的服务 */
    private static final String ECHO_PREFIX = "你说的是：";

    @Override
    public MsgType supportedType() {
        return MsgType.TEXT;
    }

    @Override
    protected ReplyMessage doHandle(InboundMessage message) {
        String content = message.getContent() == null ? "" : message.getContent();
        return TextReply.of(message, ECHO_PREFIX + content);
    }
}
