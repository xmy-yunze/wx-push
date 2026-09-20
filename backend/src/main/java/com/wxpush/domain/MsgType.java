package com.wxpush.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微信消息类型。
 *
 * <p>对应微信报文里的 {@code MsgType} 字段。P0 只用到 TEXT / EVENT，
 * 其余类型先登记在这里，后续迭代按需实现对应处理器。</p>
 */
public enum MsgType {

    /** 文本消息 */
    TEXT("text"),
    /** 图片消息 */
    IMAGE("image"),
    /** 语音消息 */
    VOICE("voice"),
    /** 视频消息 */
    VIDEO("video"),
    /** 小视频消息 */
    SHORT_VIDEO("shortvideo"),
    /** 地理位置消息 */
    LOCATION("location"),
    /** 链接消息 */
    LINK("link"),
    /** 事件推送（关注 / 取关 / 菜单点击等），此类型没有 MsgId */
    EVENT("event"),
    /** 未知类型兜底，避免微信新增类型时直接抛异常 */
    UNKNOWN("unknown");

    private final String code;

    MsgType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** code → 枚举 的查表缓存，避免每次 values() 遍历 */
    private static final Map<String, MsgType> CODE_INDEX =
            Arrays.stream(values()).collect(Collectors.toMap(MsgType::code, Function.identity()));

    /**
     * 按微信报文中的字符串解析类型，无法识别时返回 {@link #UNKNOWN}。
     *
     * @param code 微信报文里的 MsgType 原文（可能为 null）
     */
    public static MsgType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return CODE_INDEX.getOrDefault(code.trim().toLowerCase(), UNKNOWN);
    }
}
