package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.reply.TextReply;
import org.springframework.stereotype.Component;

/**
 * 事件消息处理器。
 *
 * <p>事件推送的 {@code MsgType} 统一是 {@code event}，真正的类型在 {@code Event} 字段里
 * （subscribe 关注 / unsubscribe 取关 / CLICK 菜单点击 …）。</p>
 *
 * <p>注意：事件消息<b>没有 MsgId</b>，落库时该字段为 null。</p>
 */
@Component
public class EventMessageHandler extends AbstractMessageHandler {

    /** 关注事件 */
    private static final String EVENT_SUBSCRIBE = "subscribe";
    /** 取消关注事件 */
    private static final String EVENT_UNSUBSCRIBE = "unsubscribe";

    /** 关注欢迎语（微信服务器有 5 秒限制，这里保持简短）；public 以便测试直接引用，避免硬编码两份 */
    public static final String WELCOME = "欢迎关注！发条消息试试，我会把你说的原样回给你。";

    @Override
    public MsgType supportedType() {
        return MsgType.EVENT;
    }

    @Override
    protected ReplyMessage doHandle(InboundMessage message) {
        String event = message.getEvent();
        if (EVENT_SUBSCRIBE.equals(event)) {
            return TextReply.of(message, WELCOME);
        }
        if (EVENT_UNSUBSCRIBE.equals(event)) {
            // 用户已经取关，回复没有意义（也送不到）
            return null;
        }
        // 其余事件（菜单点击等）P0 不处理，返回 null = 不回复。
        // 将来事件类型变多时，再按「工厂 + 策略」拆成独立处理器，避免这里堆成 if-else 长链。
        return null;
    }
}
