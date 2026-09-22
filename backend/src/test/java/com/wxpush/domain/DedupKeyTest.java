package com.wxpush.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DedupKey} 的单元测试。
 *
 * <p>这张测试表要守住的核心命题只有两条：</p>
 * <ol>
 *   <li><b>同一条消息重推 → 键必须相同</b>（否则幂等失效，重复入库）</li>
 *   <li><b>两次真实的不同消息 → 键必须不同</b>（否则真实消息被静默丢弃）</li>
 * </ol>
 *
 * <p>第 2 条比第 1 条更容易被写坏：一旦键里漏掉区分字段（比如忘了 CreateTime），
 * 第 1 条照样过，第 2 条会静默失守 —— 而"消息丢了"比"消息重复"更难被发现。</p>
 */
@DisplayName("消息去重键 DedupKey")
class DedupKeyTest {

    /** 造一条文本消息（有 MsgId） */
    private InboundMessage textMessage(String msgId) {
        return InboundMessage.builder()
                .fromUserName("oUser001")
                .toUserName("gh_abc")
                .msgType(MsgType.TEXT)
                .content("你好")
                .createTime(1700000000L)
                .msgId(msgId)
                .build();
    }

    /** 造一条 CLICK 事件（没有 MsgId） */
    private InboundMessage clickEvent(String eventKey, long createTime) {
        return InboundMessage.builder()
                .fromUserName("oUser001")
                .toUserName("gh_abc")
                .msgType(MsgType.EVENT)
                .event("CLICK")
                .eventKey(eventKey)
                .createTime(createTime)
                .build();
    }

    // ---------------------------------------------------------------- 普通消息

    @Test
    @DisplayName("文本消息：键为 msg:<MsgId>")
    void textMessageKey() {
        assertEquals("msg:1234567890123456", DedupKey.of(textMessage("1234567890123456")));
    }

    @Test
    @DisplayName("文本消息：同一条重推，键相同（幂等的前提）")
    void textMessageRetryGivesSameKey() {
        String first = DedupKey.of(textMessage("1234567890123456"));
        String second = DedupKey.of(textMessage("1234567890123456"));
        assertEquals(first, second);
    }

    @Test
    @DisplayName("文本消息：不同 MsgId，键不同")
    void differentMsgIdGivesDifferentKey() {
        assertNotEquals(DedupKey.of(textMessage("111")), DedupKey.of(textMessage("222")));
    }

    @Test
    @DisplayName("文本消息：键不依赖 CreateTime（同一条重推时它可能被微信改写）")
    void textMessageKeyIgnoresCreateTime() {
        InboundMessage a = textMessage("999");
        InboundMessage b = textMessage("999");
        b.setCreateTime(1700009999L);
        assertEquals(DedupKey.of(a), DedupKey.of(b));
    }

    // ---------------------------------------------------------------- 事件消息

    @Test
    @DisplayName("CLICK 事件：键为 evt:<用户>:<事件>:<EventKey>:<CreateTime>")
    void clickEventKey() {
        assertEquals("evt:oUser001:CLICK:MENU_ABOUT:1700000000",
                DedupKey.of(clickEvent("MENU_ABOUT", 1700000000L)));
    }

    @Test
    @DisplayName("CLICK 事件：同一条重推，键相同 —— 这正是原来失效的场景")
    void clickRetryGivesSameKey() {
        String first = DedupKey.of(clickEvent("MENU_ABOUT", 1700000000L));
        String second = DedupKey.of(clickEvent("MENU_ABOUT", 1700000000L));
        assertEquals(first, second);
    }

    @Test
    @DisplayName("CLICK 事件：用户真的又点了一次（CreateTime 不同）→ 键不同，不能被误挡")
    void clickAgainLaterGivesDifferentKey() {
        String first = DedupKey.of(clickEvent("MENU_ABOUT", 1700000000L));
        String second = DedupKey.of(clickEvent("MENU_ABOUT", 1700000100L));
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("CLICK 事件：点了不同菜单 → 键不同")
    void differentEventKeyGivesDifferentKey() {
        String about = DedupKey.of(clickEvent("MENU_ABOUT", 1700000000L));
        String howto = DedupKey.of(clickEvent("MENU_HOWTO", 1700000000L));
        assertNotEquals(about, howto);
    }

    @Test
    @DisplayName("CLICK 事件：不同用户点同一个菜单 → 键不同")
    void differentUserGivesDifferentKey() {
        InboundMessage a = clickEvent("MENU_ABOUT", 1700000000L);
        InboundMessage b = clickEvent("MENU_ABOUT", 1700000000L);
        b.setFromUserName("oUser002");
        assertNotEquals(DedupKey.of(a), DedupKey.of(b));
    }

    @Test
    @DisplayName("subscribe 事件：没有 EventKey，用空串占位，键仍稳定且不串位")
    void subscribeEventWithoutEventKey() {
        InboundMessage subscribe = InboundMessage.builder()
                .fromUserName("oUser001")
                .msgType(MsgType.EVENT)
                .event("subscribe")
                .createTime(1700000000L)
                .build();
        assertEquals("evt:oUser001:subscribe::1700000000", DedupKey.of(subscribe));
    }

    // ---------------------------------------------------------------- 边界

    @Test
    @DisplayName("事件缺 CreateTime → 返回 null（放弃去重，宁可重复也不误挡）")
    void eventWithoutCreateTimeGivesNull() {
        InboundMessage noCreateTime = InboundMessage.builder()
                .fromUserName("oUser001")
                .msgType(MsgType.EVENT)
                .event("CLICK")
                .eventKey("MENU_ABOUT")
                .build();   // 刻意不设 createTime
        assertNull(DedupKey.of(noCreateTime));
    }

    @Test
    @DisplayName("事件与非事件不会撞键：evt: 与 msg: 前缀不同")
    void prefixesDoNotCollide() {
        InboundMessage event = clickEvent("MENU_ABOUT", 1700000000L);
        InboundMessage text = textMessage("MENU_ABOUT");
        assertNotEquals(DedupKey.of(event), DedupKey.of(text));
    }

    @Test
    @DisplayName("超长键 → 返回 null，绝不截断（截断会把两条不同消息拼成同一个键）")
    void overlongKeyGivesNullInsteadOfTruncation() {
        // EventKey 撑到超过 255 字符
        String hugeKey = "K".repeat(DedupKey.MAX_LENGTH);
        assertNull(DedupKey.of(clickEvent(hugeKey, 1700000000L)));
    }

    @Test
    @DisplayName("刚好等于上限长度 → 保留（边界不多挡一位）")
    void exactlyMaxLengthIsAccepted() {
        // "evt:" + 用户(8) + ':' + "CLICK"(5) + ':' + key + ':' + 时间戳(10) = 255
        String user = "oUser001";      // 8
        String time = "1700000000";    // 10
        int fixed = "evt:".length() + user.length() + 1 + "CLICK".length() + 1 + 1 + time.length();
        int keyLen = DedupKey.MAX_LENGTH - fixed;
        String key = "K".repeat(keyLen);

        String result = DedupKey.of(clickEvent(key, 1700000000L));
        assertNotNull(result);
        assertEquals(DedupKey.MAX_LENGTH, result.length());
    }

    @Test
    @DisplayName("null 入参 → 返回 null，不抛异常")
    void nullMessageGivesNull() {
        assertNull(DedupKey.of(null));
    }

    @Test
    @DisplayName("首尾空白被去掉：手写报文里混入空格不会造出两个键")
    void surroundingWhitespaceIsTrimmed() {
        InboundMessage padded = textMessage("  123456  ");
        assertEquals(DedupKey.of(textMessage("123456")), DedupKey.of(padded));
    }
}
