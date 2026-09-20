package com.wxpush.dispatcher;

import com.wxpush.domain.MsgType;
import com.wxpush.handler.MessageHandler;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消息处理器工厂 —— <b>工厂 + 策略</b>的「工厂」这一半。
 *
 * <p>构造时把 Spring 容器里所有的 {@link MessageHandler} 收进来，按
 * {@link MessageHandler#supportedType()} 建立索引，运行期按类型 O(1) 取用。</p>
 *
 * <p>关键收益：调用方只依赖「一个接口 + 一次查表」，
 * 完全不需要知道具体有哪些实现类，也不需要写任何类型判断。</p>
 */
@Component
public class MessageHandlerFactory {

    /** 类型 → 处理器；用 EnumMap 保证遍历顺序与枚举声明一致 */
    private final Map<MsgType, MessageHandler> handlerMap;

    /**
     * 由 Spring 自动注入全部处理器实现。
     *
     * <p>用 {@code List<MessageHandler>} 而不是逐个注入，是为了做到「新增处理器无需改这里」。</p>
     */
    public MessageHandlerFactory(List<MessageHandler> handlers) {
        Map<MsgType, MessageHandler> map = new EnumMap<>(MsgType.class);
        for (MessageHandler handler : handlers) {
            MessageHandler previous = map.put(handler.supportedType(), handler);
            if (previous != null) {
                // 重复声明同一个消息类型属于配置错误，直接启动失败，好过运行期行为不确定
                throw new IllegalStateException(String.format(
                        "消息类型 [%s] 存在重复处理器：%s 与 %s",
                        handler.supportedType().code(),
                        previous.getClass().getName(),
                        handler.getClass().getName()));
            }
        }
        this.handlerMap = Collections.unmodifiableMap(map);
    }

    /**
     * 取指定类型的处理器。
     *
     * @param msgType 消息类型
     * @return 对应处理器；没有实现时返回 {@code null}（调用方按「不回复」处理）
     */
    public MessageHandler getHandler(MsgType msgType) {
        return msgType == null ? null : handlerMap.get(msgType);
    }

    /** 当前已注册的处理器类型集合，用于启动自检与排查 */
    public Set<MsgType> supportedTypes() {
        return handlerMap.keySet();
    }
}
