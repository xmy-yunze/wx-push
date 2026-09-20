package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;

/**
 * 消息处理器策略接口。
 *
 * <p>每一种消息类型对应一个实现类。新增类型 = 新增一个类 + 加上 {@code @Component}，
 * <b>既有代码一行都不用改</b> —— 这是工厂 + 策略组合要解决的核心问题：
 * 干掉 {@code if ("text".equals(type)) ... else if ...} 这类长链判断。</p>
 */
public interface MessageHandler {

    /** 本处理器负责的消息类型，工厂按此建立索引 */
    MsgType supportedType();

    /**
     * 处理一条消息。
     *
     * @param message 入站消息（业务层只认这个模型，不感知 XML）
     * @return 回复对象；返回 {@code null} 表示不回复
     */
    ReplyMessage handle(InboundMessage message);
}
