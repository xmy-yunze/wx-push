package com.wxpush.gateway;

import com.wxpush.domain.InboundMessage;
import com.wxpush.domain.ReplyMessage;
import com.wxpush.service.WxMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 微信服务器回调入口 —— 整个系统<b>唯一</b>对微信暴露的路径。
 *
 * <p>GET 与 POST 走同一个 URL{@code /wx}，靠请求方法区分：</p>
 * <ul>
 *   <li><b>GET</b>：公众平台「提交」时的一次性 URL 验证，校验通过需<b>原样返回 echostr</b></li>
 *   <li><b>POST</b>：用户消息推送，处理完返回被动回复报文</li>
 * </ul>
 *
 * <p>这一层是「协议适配器」：负责验签、拿原始报文、翻译成内部模型，
 * 再把业务结果翻译回 XML。<b>不写业务逻辑、不碰数据库。</b></p>
 */
@Slf4j
@RestController
@RequestMapping("/wx")
public class WxCallbackController {

    private final WxSignatureVerifier signatureVerifier;
    private final WxXmlParser xmlParser;
    private final WxReplyXmlBuilder replyXmlBuilder;
    private final WxMessageService messageService;

    public WxCallbackController(WxSignatureVerifier signatureVerifier,
                                WxXmlParser xmlParser,
                                WxReplyXmlBuilder replyXmlBuilder,
                                WxMessageService messageService) {
        this.signatureVerifier = signatureVerifier;
        this.xmlParser = xmlParser;
        this.replyXmlBuilder = replyXmlBuilder;
        this.messageService = messageService;
    }

    /**
     * URL 验证（微信公众平台填写服务器地址后立即调用）。
     *
     * <p>⚠️ 三个坑：{@code echostr} 必须<b>原样</b>返回 —— 不能加引号、不能包成 JSON、
     * 不能 trim；否则公众平台会提示「配置失败」。</p>
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyUrl(
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "echostr", required = false) String echostr) {

        if (!signatureVerifier.verify(signature, timestamp, nonce)) {
            log.warn("微信 URL 验证失败：签名不匹配（请检查后台 Token 与配置是否一致）");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        log.info("微信 URL 验证通过");
        return ResponseEntity.ok(echostr == null ? "" : echostr);
    }

    /**
     * 接收用户消息推送。
     *
     * <p>⚠️ 用 {@code HttpServletRequest} 读原始 body，而不是 {@code @RequestBody} 映射对象 ——
     * 微信发的是 {@code text/xml}，Spring 默认的消息转换器只认 JSON，直接映射会 415。</p>
     *
     * <p>⚠️ 必须在 <b>5 秒内</b>返回，否则用户端会看到「该公众号暂时无法提供服务」，
     * 且微信会重试推送同一条消息。所以主流程里不能有慢操作。</p>
     */
    @PostMapping(produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> handleMessage(
            HttpServletRequest request,
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce) {

        if (!signatureVerifier.verify(signature, timestamp, nonce)) {
            log.warn("收到未经签名校验的推送，已拒绝");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        String rawXml;
        try {
            rawXml = readBody(request);
        } catch (IOException e) {
            log.error("读取微信推送报文失败", e);
            return ResponseEntity.ok("");
        }

        try {
            InboundMessage inbound = xmlParser.parse(rawXml);
            ReplyMessage reply = messageService.process(inbound);
            return ResponseEntity.ok(replyXmlBuilder.build(reply));
        } catch (IllegalArgumentException e) {
            // 报文畸形时返回空串（= 不回复）：重试也救不回来，没必要让微信反复推
            log.error("微信报文解析失败：{}", e.getMessage());
            return ResponseEntity.ok("");
        }
    }

    /** 读取原始请求体（微信统一使用 UTF-8） */
    private static String readBody(HttpServletRequest request) throws IOException {
        try (InputStream in = request.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
