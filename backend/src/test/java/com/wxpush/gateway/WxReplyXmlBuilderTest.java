package com.wxpush.gateway;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.reply.TextReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回复报文组装测试。
 */
class WxReplyXmlBuilderTest {

    private final WxReplyXmlBuilder builder = new WxReplyXmlBuilder();

    private static InboundMessage inbound(String officialId, String userOpenid) {
        return InboundMessage.builder()
                .toUserName(officialId)
                .fromUserName(userOpenid)
                .msgType(MsgType.TEXT)
                .build();
    }

    @Test
    @DisplayName("⚠️ 关键：回复时收发双方必须互换")
    void replySwapsSenderAndReceiver() {
        InboundMessage received = inbound("gh_official", "o_user");
        TextReply reply = TextReply.of(received, "你好");

        String xml = builder.build(reply);

        // 原发送者（用户）→ 回复的接收方
        assertTrue(xml.contains("<ToUserName><![CDATA[o_user]]></ToUserName>"),
                "回复的 ToUserName 应是用户 openid，实际报文：" + xml);
        // 原接收者（公众号）→ 回复的发送方
        assertTrue(xml.contains("<FromUserName><![CDATA[gh_official]]></FromUserName>"),
                "回复的 FromUserName 应是公众号原始 ID，实际报文：" + xml);
    }

    @Test
    @DisplayName("不回复时返回空字符串（微信据此不下发任何内容）")
    void nullReplyBuildsEmptyString() {
        assertEquals("", builder.build(null));
    }

    @Test
    @DisplayName("报文结构包含 MsgType=text 与 Content，且全部用 CDATA 包裹")
    void buildTextReplyStructure() {
        String xml = builder.build(TextReply.of(inbound("gh_official", "o_user"), "你说的是：你好"));

        assertTrue(xml.startsWith("<xml>") && xml.endsWith("</xml>"), "报文应以 <xml> 包裹：" + xml);
        assertTrue(xml.contains("<MsgType><![CDATA[text]]></MsgType>"));
        assertTrue(xml.contains("<Content><![CDATA[你说的是：你好]]></Content>"));
        assertTrue(xml.matches("(?s).*<CreateTime>\\d+</CreateTime>.*"), "应包含数字类型的 CreateTime");
    }

    @Test
    @DisplayName("内容含 ]]> 时正确拆分 CDATA，不破坏报文结构")
    void escapeCdataTerminator() {
        String xml = builder.build(TextReply.of(inbound("gh_official", "o_user"), "a]]>b"));

        assertTrue(xml.contains("]]]]><![CDATA[>"), "应把 ]]> 拆成 ]]]]><![CDATA[>");
        assertTrue(xml.endsWith("</xml>"));
    }

    @Test
    @DisplayName("超长内容被截断，避免整条回复下发失败")
    void truncateLongContent() {
        String longText = "啊".repeat(2000);
        String xml = builder.build(TextReply.of(inbound("gh_official", "o_user"), longText));

        int contentStart = xml.indexOf("<Content><![CDATA[");
        int contentEnd = xml.indexOf("]]></Content>");
        String content = xml.substring(contentStart + "<Content><![CDATA[".length(), contentEnd);

        assertTrue(content.length() < longText.length(), "内容应被截断");
        assertTrue(content.endsWith("..."), "截断后应有省略号提示");
    }

    @Test
    @DisplayName("回归：未实现的回复类型返回空串而不是抛异常")
    void unsupportedReplyTypeReturnsEmpty() {
        ReplyMessage unsupported = new ReplyMessage() {
            @Override
            public String toUserName() {
                return "o_user";
            }

            @Override
            public String fromUserName() {
                return "gh_official";
            }

            @Override
            public long createTime() {
                return 0L;
            }

            @Override
            public MsgType replyType() {
                return MsgType.IMAGE;
            }
        };

        assertEquals("", builder.build(unsupported));
    }
}
