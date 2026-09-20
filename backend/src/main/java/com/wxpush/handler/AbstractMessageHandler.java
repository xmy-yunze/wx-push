package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理器抽象基类 —— <b>模板方法模式</b>。
 *
 * <p>各种消息的处理流程有一个共同骨架：记日志 → 执行具体业务 → 收尾。
 * 其中只有「业务」这一步随类型变化。于是把骨架固定在这里（{@link #handle} 声明为 final，
 * 子类无法改变流程），把变化点抽成抽象方法 {@link #doHandle} 交给子类实现。</p>
 *
 * <p>这里骨架承担的三件事都有实际价值：</p>
 * <ol>
 *   <li><b>耗时统计</b> —— 5 秒超时是硬约束，需要能量出每个处理器耗时</li>
 *   <li><b>异常兜底</b> —— 业务异常不能让微信收到 500，否则用户端会看到错误提示</li>
 *   <li><b>统一日志</b> —— 排查问题时格式一致</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractMessageHandler implements MessageHandler {

    @Override
    public final ReplyMessage handle(InboundMessage message) {
        String type = supportedType().code();
        long startNanos = System.nanoTime();
        try {
            ReplyMessage reply = doHandle(message);
            log.debug("[{}] 处理完成，耗时 {} ms，{}",
                    type, elapsedMillis(startNanos), reply == null ? "不回复" : "已生成回复");
            return reply;
        } catch (Exception e) {
            // 兜底：任何异常都转成「不回复」，保证微信侧收到 200，用户端不会报错
            log.error("[{}] 处理器执行异常，已降级为不回复", type, e);
            return null;
        }
    }

    /**
     * 具体业务逻辑，由各处理器实现。
     *
     * @return 回复对象；{@code null} 表示不回复
     */
    protected abstract ReplyMessage doHandle(InboundMessage message);

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
