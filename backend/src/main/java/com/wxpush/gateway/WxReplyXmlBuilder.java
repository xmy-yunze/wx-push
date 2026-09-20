package com.wxpush.gateway;

import com.wxpush.domain.ReplyMessage;
import com.wxpush.domain.reply.TextReply;
import org.springframework.stereotype.Component;

/**
 * 出站回复报文组装器（{@link ReplyMessage} → XML）。
 *
 * <p>微信只认 XML，而且必须是<b>字符串</b>直接写回响应体 ——
 * 如果返回 Java 对象，Spring 会序列化成 JSON，微信无法识别。</p>
 */
@Component
public class WxReplyXmlBuilder {

    /**
     * 被动回复的文本长度保护阈值。
     *
     * <p>微信对被动回复的文本内容有长度上限（以官方文档为准，量级在 2KB 字节以内）。
     * 这里取一个保守值按「字符数」截断，避免因为一条超长回复导致整条消息下发失败。</p>
     */
    private static final int MAX_CONTENT_CHARS = 600;

    private static final String ELLIPSIS = "...";

    /**
     * 组装回复报文。
     *
     * @param reply 业务层产出的回复对象
     * @return XML 字符串；返回<b>空字符串表示不回复</b>（微信不会下发任何内容）
     */
    public String build(ReplyMessage reply) {
        if (reply == null) {
            return "";
        }
        return switch (reply.replyType()) {
            case TEXT -> reply instanceof TextReply text ? buildText(text) : "";
            // 其余回复类型（图文 / 图片 / 语音 …）尚未实现，先返回空串即「不回复」。
            // 将来扩展时在这里补 case 即可，业务层不受影响。
            default -> "";
        };
    }

    /** 组装文本回复报文；注意收发双方已在 TextReply.of 中完成互换 */
    private String buildText(TextReply reply) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<xml>");
        xml.append(createTimeTag(reply.createTime()));
        appendCdataTag(xml, "ToUserName", reply.toUserName());
        appendCdataTag(xml, "FromUserName", reply.fromUserName());
        appendCdataTag(xml, "MsgType", "text");
        appendCdataTag(xml, "Content", truncate(reply.content()));
        xml.append("</xml>");
        return xml.toString();
    }

    private static String createTimeTag(long epochSecond) {
        return "<CreateTime>" + epochSecond + "</CreateTime>";
    }

    /**
     * 追加一个 CDATA 包裹的标签。
     *
     * <p>用 CDATA 而不是转义字符，是为了让文本里的 {@code & < >} 等特殊字符
     * 原样传递，不需要逐个转义。</p>
     */
    private static void appendCdataTag(StringBuilder xml, String tagName, String value) {
        xml.append('<').append(tagName).append("><![CDATA[")
                .append(escapeCdata(value))
                .append("]]></").append(tagName).append('>');
    }

    /**
     * 处理 CDATA 内容的边界情况。
     *
     * <p>CDATA 段内不能出现 {@code ]]>} 序列，否则会提前结束 CDATA。
     * 标准解法是拆成两段：{@code ]]>} → {@code ]]]]><![CDATA[>}。</p>
     */
    private static String escapeCdata(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("]]>", "]]]]><![CDATA[>");
    }

    /** 超长内容截断，避免整条回复下发失败 */
    private static String truncate(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTENT_CHARS) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_CHARS - ELLIPSIS.length()) + ELLIPSIS;
    }
}
