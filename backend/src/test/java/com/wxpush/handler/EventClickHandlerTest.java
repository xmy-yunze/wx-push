package com.wxpush.handler;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.menu.MenuCatalog;
import com.wxpush.domain.reply.TextReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单点击事件测试。
 *
 * <p>这里验证「菜单能用」的另一半：菜单是 click 型的，用户点下去之后是<b>我们</b>
 * 负责回话。如果这一环坏了，现象是「菜单点得动但毫无反应」——
 * 而菜单创建接口本身完全正常，很容易查错方向。</p>
 */
@DisplayName("菜单点击事件（CLICK）")
class EventClickHandlerTest {

    private final EventMessageHandler handler = new EventMessageHandler();

    private static InboundMessage click(String eventKey) {
        return InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                // ⚠️ 微信发的就是全大写 CLICK，不是 click
                .event("CLICK")
                .eventKey(eventKey)
                .build();
    }

    @Test
    @DisplayName("点击已知菜单 → 回复 MenuCatalog 配置的文案")
    void knownKeyRepliesWithConfiguredText() {
        ReplyMessage reply = handler.handle(click(MenuCatalog.KEY_ABOUT));

        TextReply text = assertInstanceOf(TextReply.class, reply);
        assertEquals(MenuCatalog.replyForClick(MenuCatalog.KEY_ABOUT).orElseThrow(), text.content());
    }

    @Test
    @DisplayName("每个 click key 都能得到文案，且互不相同")
    void everyKeyHasItsOwnReply() {
        Set<String> contents = new HashSet<>();

        for (String key : MenuCatalog.clickReplies().keySet()) {
            ReplyMessage reply = handler.handle(click(key));

            TextReply text = assertInstanceOf(TextReply.class, reply, "key " + key + " 没有回复");
            assertTrue(contents.add(text.content()), "key " + key + " 的文案与别人重复了");
        }
    }

    @Test
    @DisplayName("未知 key → 不回复（菜单可能被运营者手工改过，属正常情况，不该报错）")
    void unknownKeyIsSilent() {
        assertNull(handler.handle(click("NOT_A_REAL_KEY")));
    }

    @Test
    @DisplayName("缺少 EventKey → 不回复，也不抛异常")
    void missingEventKeyIsSilent() {
        assertNull(handler.handle(click(null)));
    }

    // ==================== 原有行为不能被打坏 ====================

    @Test
    @DisplayName("关注事件仍回复欢迎语")
    void subscribeStillWelcomes() {
        InboundMessage subscribe = InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                .event("subscribe")
                .build();

        TextReply reply = assertInstanceOf(TextReply.class, handler.handle(subscribe));
        assertEquals(EventMessageHandler.WELCOME, reply.content());
    }

    @Test
    @DisplayName("取关事件仍不回复")
    void unsubscribeStillSilent() {
        InboundMessage unsubscribe = InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                .event("unsubscribe")
                .build();

        assertNull(handler.handle(unsubscribe));
    }

    @Test
    @DisplayName("其它未知事件（如扫码）不回复")
    void otherEventsAreSilent() {
        InboundMessage scan = InboundMessage.builder()
                .toUserName("gh_official")
                .fromUserName("o_user")
                .msgType(MsgType.EVENT)
                .event("SCAN")
                .build();

        assertNull(handler.handle(scan));
    }
}
