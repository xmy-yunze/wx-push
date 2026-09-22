package com.wxpush.domain;

/**
 * 消息去重键 —— 「落库幂等」的判定依据。
 *
 * <p>微信在超时或未及时收到响应时，会<b>重推同一条消息</b>。为了不让同一条消息写两次，
 * 我们把本类算出的键写进 {@code wx_message_log.dedup_key}，并在该列上建唯一索引：
 * 重复插入命中的行被 {@code INSERT IGNORE} 静默跳过。</p>
 *
 * <h3>为什么不能直接用 msg_id 做唯一键</h3>
 * <p>事件消息（subscribe / unsubscribe / CLICK …）的报文里<b>没有 MsgId</b>，
 * 落库即为 {@code NULL}；而 <b>MySQL 的唯一索引不比较 NULL</b> —— 多行 NULL 被视为互不相同。
 * 于是唯一约束对事件消息<b>完全失效</b>，微信重推就会写出重复行。
 * 这正是本类要解决的问题：用「一定非空」的键替代 msg_id。</p>
 *
 * <h3>键的构成</h3>
 * <ul>
 *   <li>普通消息：{@code msg:<MsgId>}</li>
 *   <li>事件消息：{@code evt:<FromUser>:<Event>:<EventKey>:<CreateTime>}</li>
 * </ul>
 *
 * <p>事件取这四项，是因为<b>微信重推时它们逐字不变</b>；而用户真实地再做一次同样动作时，
 * {@code CreateTime} 必然不同 —— 既能挡住重推，又不会误伤真实行为。</p>
 *
 * <p>⚠️ 与 {@code wx_message_log.dedup_key VARCHAR(255)} 的长度约定：
 * 超长时返回 {@code null}（放弃去重）而不是截断 —— 截断会把两条不同的消息
 * 拼成同一个键，导致<b>真实消息被静默丢弃</b>，比不去重严重得多。</p>
 */
public final class DedupKey {

    /** 普通消息前缀 */
    public static final String PREFIX_MESSAGE = "msg:";

    /** 事件消息前缀 */
    public static final String PREFIX_EVENT = "evt:";

    /** 与数据库列 {@code dedup_key VARCHAR(255)} 对齐 */
    public static final int MAX_LENGTH = 255;

    /** 字段缺失时的占位，保证键的分段结构稳定（不会因为少一个分隔符而错位） */
    private static final String EMPTY = "";

    private DedupKey() {
        // 工具类，不允许实例化
    }

    /**
     * 计算一条入站消息的去重键。
     *
     * @param message 入站消息
     * @return 去重键；{@code null} 表示<b>无法安全判定</b>，调用方应放弃去重、照常入库
     */
    public static String of(InboundMessage message) {
        if (message == null) {
            return null;
        }

        String msgId = safe(message.getMsgId());
        if (!msgId.isEmpty()) {
            return limit(PREFIX_MESSAGE + msgId);
        }

        // 走到这里说明是事件消息（没有 MsgId）。
        // CreateTime 是判定「同一次事件」的关键 —— 微信重推时它不变。
        // 若它缺失（报文异常），宁可放弃去重，也不要拿一个会误合并真实点击的键去挡。
        if (message.getCreateTime() == null) {
            return null;
        }

        return limit(PREFIX_EVENT
                + safe(message.getFromUserName()) + ':'
                + safe(message.getEvent()) + ':'
                + safe(message.getEventKey()) + ':'
                + message.getCreateTime());
    }

    /** 超长直接放弃去重，绝不截断（见类注释） */
    private static String limit(String key) {
        return key.length() > MAX_LENGTH ? null : key;
    }

    /** null 安全 + 去首尾空白（微信报文里这些字段不会含空格，去掉是为了防止手写报文时混入） */
    private static String safe(String value) {
        return value == null ? EMPTY : value.trim();
    }
}
