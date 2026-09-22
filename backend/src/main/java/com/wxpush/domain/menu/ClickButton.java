package com.wxpush.domain.menu;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * click 类型按钮 —— 用户点击后<b>微信推事件给我们的服务器</b>（不跳转任何页面）。
 *
 * <p>这是未认证订阅号唯一实用的按钮类型：</p>
 * <ul>
 *   <li>不需要外链（未认证号填外链会报 {@code 45058}）</li>
 *   <li>不需要已发表内容（公众号里目前一条都没有）</li>
 *   <li>点击后微信推 {@code CLICK} 事件到 {@code /wx}，我们被动回复文本 ——
 *       相当于「用菜单做出一组常见问题快捷入口」</li>
 * </ul>
 *
 * <p>{@code key} 是我们自己定义的标识（≤128 字节），微信只是把它原样带回来。
 * 它相当于「菜单项的身份证」，服务端靠它决定回复什么内容 ——
 * 所以 key 与回复文案的映射必须集中维护，不能散落（见 {@link MenuCatalog}）。</p>
 */
public final class ClickButton extends MenuButton {

    /** 微信规定的 click 事件 key 长度上限（字节） */
    private static final int MAX_KEY_BYTES = 128;

    private final String key;

    public ClickButton(String name, String key) {
        super(name);
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "click");
        putName(map);
        map.put("key", key);
        return map;
    }

    @Override
    void validate(int level) {
        super.validate(level);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("click 菜单「" + name + "」必须提供 key");
        }
        int bytes = key.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "click 菜单「" + name + "」的 key 过长：最多 " + MAX_KEY_BYTES
                            + " 字节，当前 " + bytes);
        }
    }
}
