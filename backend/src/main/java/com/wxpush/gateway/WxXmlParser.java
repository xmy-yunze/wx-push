package com.wxpush.gateway;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.MsgType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;

/**
 * 微信入站报文解析器（XML → {@link InboundMessage}）。
 *
 * <p>报文样例（明文模式）：</p>
 * <pre>{@code
 * <xml>
 *   <ToUserName><![CDATA[gh_xxx]]></ToUserName>
 *   <FromUserName><![CDATA[o_yyy]]></FromUserName>
 *   <CreateTime>1348831860</CreateTime>
 *   <MsgType><![CDATA[text]]></MsgType>
 *   <Content><![CDATA[你好]]></Content>
 *   <MsgId>1234567890123456</MsgId>
 * </xml>
 * }</pre>
 *
 * <p>用 JDK 自带的 DOM 解析，不引第三方库：报文结构很扁平，DOM 足够；
 * 而且省掉一个依赖，就能少一处版本兼容风险。</p>
 */
@Component
public class WxXmlParser {

    /**
     * DocumentBuilderFactory 创建开销较大且可复用；
     * 但 DocumentBuilder 本身<b>非线程安全</b>，所以每次解析新建一个。
     */
    private final DocumentBuilderFactory factory;

    public WxXmlParser() {
        this.factory = createSecureFactory();
    }

    /**
     * 解析微信推送的 XML 报文。
     *
     * @param xml 原始报文
     * @return 统一的入站消息模型
     * @throws IllegalArgumentException 报文为空或格式非法
     */
    public InboundMessage parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("微信报文为空");
        }
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();

            return InboundMessage.builder()
                    .toUserName(text(root, "ToUserName"))
                    .fromUserName(text(root, "FromUserName"))
                    .createTime(toEpochSecond(text(root, "CreateTime")))
                    .msgType(MsgType.fromCode(text(root, "MsgType")))
                    // 正文保留原样（不 trim），尊重用户输入的首尾空格
                    .content(rawText(root, "Content"))
                    .msgId(text(root, "MsgId"))
                    .event(text(root, "Event"))
                    .eventKey(text(root, "EventKey"))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("解析微信 XML 报文失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构造禁用了外部实体解析的工厂。
     *
     * <p>⚠️ 安全要点：报文是来自公网的<b>不可信输入</b>。
     * 若不关闭 DTD / 外部实体，攻击者可以构造 XXE 报文读取服务器本地文件
     * （例如 {@code file:///etc/passwd}）或发起内网探测。这是 XML 解析的经典漏洞。</p>
     */
    private static DocumentBuilderFactory createSecureFactory() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            // 当前 XML 实现不支持这些特性时，宁可启动失败也不要带着 XXE 风险运行
            throw new IllegalStateException("无法为 XML 解析器启用安全特性", e);
        }
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    /** 取标签文本并去掉首尾空白；标签不存在返回 null */
    private static String text(Element root, String tagName) {
        String value = rawText(root, tagName);
        return value == null ? null : value.trim();
    }

    /**
     * 取标签原始文本。
     *
     * <p>只保留原样、不做 trim —— 用于 {@code Content} 字段，
     * 因为用户发来的正文里首尾空格是有效内容，不该被悄悄吃掉。</p>
     */
    private static String rawText(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    /** CreateTime 是 Unix 秒，解析失败返回 null（不因单个字段异常而整条失败） */
    private static Long toEpochSecond(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
