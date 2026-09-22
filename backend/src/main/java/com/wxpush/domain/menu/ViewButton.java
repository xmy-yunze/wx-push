package com.wxpush.domain.menu;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * view 类型按钮 —— 用户点击后<b>跳转到网页</b>。
 *
 * <p>⚠️ 本项目当前<b>用不上它</b>，保留它是为了两件事：</p>
 * <ol>
 *   <li><b>证明组合模式的扩展性</b>：新增一种按钮类型只需加一个 Leaf 子类，
 *       建造者、容器、序列化逻辑一行都不用改 —— 这正是组合模式 + 多态的价值。</li>
 *   <li><b>为将来留门</b>：一旦公众号完成认证、或有了已发表内容，
 *       改配置就能切到跳转型，不需要重写这一块。</li>
 * </ol>
 *
 * <p>⚠️ 微信对未认证订阅号的限制：{@code url} 填外部网址会返回
 * {@code 45058 invalid url domain}，<b>与是否备案、是否配域名都无关</b>。
 * 只能填公众号内的链接（页面模板 / 合集 / 历史消息页面 / 已发表内容）。</p>
 *
 * <p>所以 {@link #validate(int)} 里按<b>域名</b>判断：只有 {@code mp.weixin.qq.com}
 * 下的地址算「公众号内页面」，其余一律提前拦下并说明原因 ——
 * 与其等微信返回一个错误码让运营者猜，不如在本地就说清楚。</p>
 */
public final class ViewButton extends MenuButton {

    private final String url;

    public ViewButton(String name, String url) {
        super(name);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "view");
        putName(map);
        map.put("url", url);
        return map;
    }

    @Override
    void validate(int level) {
        super.validate(level);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("view 菜单「" + name + "」必须提供 url");
        }
        // ⚠️ 判断标准是**域名**，不是「有没有 http 前缀」——
        // 公众号内链接（页面模板 / 合集 / 历史消息）本身也是 https，都在 mp.weixin.qq.com 下。
        // 其它域名一律会被微信拒掉（45058 invalid url domain），
        // 且与是否备案、是否配域名完全无关，所以这里提前拦下并说清原因。
        boolean isOfficialAccountPage = url.startsWith("http://mp.weixin.qq.com")
                || url.startsWith("https://mp.weixin.qq.com");
        if (!isOfficialAccountPage) {
            throw new IllegalArgumentException(
                    "view 菜单「" + name + "」的不是公众号内页面，未认证订阅号不允许跳外链（微信会返回 45058）。"
                            + "只能填 mp.weixin.qq.com 下的页面：页面模板 / 合集 / 历史消息");
        }
    }
}
