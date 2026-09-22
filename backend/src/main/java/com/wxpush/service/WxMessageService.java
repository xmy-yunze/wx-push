package com.wxpush.service;

import com.wxpush.dispatcher.MessageHandlerFactory;
import com.wxpush.domain.DedupKey;
import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.handler.MessageHandler;
import com.wxpush.repository.entity.WxMessageLog;
import com.wxpush.repository.mapper.WxMessageLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 消息处理门面 —— 业务层唯一对外的入口。
 *
 * <p>职责：编排「记录 → 取处理器 → 执行」这条主线。
 * <b>不解析 XML、不拼报文</b>，那些属于接入层；<b>不做具体消息类型的业务</b>，那属于 handler。</p>
 */
@Slf4j
@Service
public class WxMessageService {

    private final MessageHandlerFactory handlerFactory;
    private final WxMessageLogMapper messageLogMapper;

    public WxMessageService(MessageHandlerFactory handlerFactory,
                            WxMessageLogMapper messageLogMapper) {
        this.handlerFactory = handlerFactory;
        this.messageLogMapper = messageLogMapper;
    }

    /**
     * 处理一条入站消息。
     *
     * @param inbound 入站消息
     * @return 回复对象；{@code null} 表示不回复
     */
    public ReplyMessage process(InboundMessage inbound) {
        if (inbound == null) {
            return null;
        }

        // ① 先记录流水（幂等），失败不影响回复
        persistQuietly(inbound);

        // ② 按消息类型取处理器，没有实现时不回复
        MessageHandler handler = handlerFactory.getHandler(inbound.getMsgType());
        if (handler == null) {
            log.debug("暂不支持的消息类型 [{}]，跳过处理", inbound.getMsgType().code());
            return null;
        }

        // ③ 交给处理器执行（模板方法内部已做异常兜底）
        return handler.handle(inbound);
    }

    /**
     * 记录消息流水。
     *
     * <p>两处刻意的设计：</p>
     * <ul>
     *   <li><b>幂等</b>：靠 {@code dedup_key} 唯一索引 + {@code INSERT IGNORE}。
     *       微信在超时或失败后会重推同一条消息，重复消息走 {@code rows = 0} 分支被自然忽略。
     *       <br>⚠️ 这里刻意<b>不用 {@code msg_id}</b> 做唯一键：事件消息没有 MsgId，
     *       落库为 null，而 MySQL 唯一索引不比较 null —— 唯一约束对事件消息等于不存在，
     *       重推就会写出重复行。去重键的算法见 {@link DedupKey}。</li>
     *   <li><b>降级</b>：落库失败（比如数据库没启动）只记警告，<b>不影响给用户回复</b>。
     *       写库是辅助能力，不能让主链路跟着挂。</li>
     * </ul>
     *
     * <p>注意：重复消息<b>只是不重复入库，仍然会照常处理并回复</b> ——
     * 因为微信重推往往意味着上一次回复没送达，用户还在等。</p>
     */
    private void persistQuietly(InboundMessage inbound) {
        try {
            WxMessageLog entity = WxMessageLog.builder()
                    // 去重键可能为 null（报文缺 CreateTime 等无法安全判定的情况），
                    // 此时唯一索引不生效 —— 宁可不去重，也不误挡真实消息
                    .dedupKey(DedupKey.of(inbound))
                    .msgId(inbound.getMsgId())
                    .fromUser(inbound.getFromUserName())
                    .toUser(inbound.getToUserName())
                    .msgType(inbound.getMsgType() == null ? null : inbound.getMsgType().code())
                    .event(inbound.getEvent())
                    .content(inbound.getContent())
                    .build();

            int rows = messageLogMapper.insertIgnore(entity);
            if (rows == 0) {
                log.debug("消息已存在，跳过写入（判定为微信重试推送）");
            }
        } catch (Exception e) {
            log.warn("消息落库失败，已跳过（不影响本次回复）：{}", e.getMessage());
        }
    }
}
