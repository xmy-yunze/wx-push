package com.wxpush.gateway;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 入站 XML 报文解析测试。
 *
 * <p>报文样例取自微信官方文档的明文模式格式。</p>
 */
class WxXmlParserTest {

    private final WxXmlParser parser = new WxXmlParser();

    @Test
    @DisplayName("解析文本消息：各字段正确映射，且大小写风格被翻译成 Java 命名")
    void parseTextMessage() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[gh_abc123]]></ToUserName>
                  <FromUserName><![CDATA[oUserOpenid]]></FromUserName>
                  <CreateTime>1348831860</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[你好]]></Content>
                  <MsgId>1234567890123456</MsgId>
                </xml>
                """;

        InboundMessage message = parser.parse(xml);

        assertEquals("gh_abc123", message.getToUserName());
        assertEquals("oUserOpenid", message.getFromUserName());
        assertEquals(1348831860L, message.getCreateTime());
        assertEquals(MsgType.TEXT, message.getMsgType());
        assertEquals("你好", message.getContent());
        assertEquals("1234567890123456", message.getMsgId());
        assertNull(message.getEvent());
    }

    @Test
    @DisplayName("解析关注事件：没有 MsgId，Event 字段有值")
    void parseSubscribeEvent() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[gh_abc123]]></ToUserName>
                  <FromUserName><![CDATA[oUserOpenid]]></FromUserName>
                  <CreateTime>1348831860</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[subscribe]]></Event>
                </xml>
                """;

        InboundMessage message = parser.parse(xml);

        assertEquals(MsgType.EVENT, message.getMsgType());
        assertEquals("subscribe", message.getEvent());
        assertNull(message.getMsgId(), "事件消息不应有 MsgId");
    }

    @Test
    @DisplayName("文本正文保留首尾空格（不吞用户输入）")
    void parseKeepsContentWhitespace() {
        String xml = """
                <xml>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[  hello  ]]></Content>
                </xml>
                """;

        assertEquals("  hello  ", parser.parse(xml).getContent());
    }

    @Test
    @DisplayName("未知 MsgType 归为 UNKNOWN，而不是抛异常")
    void parseUnknownTypeFallsBack() {
        String xml = """
                <xml>
                  <MsgType><![CDATA[some_new_type]]></MsgType>
                </xml>
                """;

        assertEquals(MsgType.UNKNOWN, parser.parse(xml).getMsgType());
    }

    @Test
    @DisplayName("空报文直接拒绝")
    void parseRejectsBlankXml() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    @DisplayName("安全：拒绝带外部实体的报文（防 XXE 读取服务器本地文件）")
    void parseRejectsXxePayload() {
        String xxe = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <xml>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content>&xxe;</Content>
                </xml>
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(xxe),
                "禁用 DOCTYPE 后，带 DTD 的报文应被拒绝");
    }
}
