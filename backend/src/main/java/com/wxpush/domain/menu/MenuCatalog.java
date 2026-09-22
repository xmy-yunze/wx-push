package com.wxpush.domain.menu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 菜单目录 —— <b>菜单结构与点击文案的唯一维护点</b>。
 *
 * <h3>为什么这两件事必须放在一起</h3>
 * <p>click 类型菜单的 key 是我们自己编的，微信只负责原样带回来。所以
 * 「菜单上写着『怎么用』」和「点了以后回复什么」是<b>强绑定</b>的一对信息。
 * 如果菜单定义在 A 处、文案写在 B 处，改菜单时极容易忘记同步文案 ——
 * 表现为用户点了菜单没反应，而代码看起来毫无问题。</p>
 *
 * <p>放在同一个类里，还有两个附带好处：</p>
 * <ul>
 *   <li>{@code KEY_*} 常量被菜单构造与文案表<b>共用</b>，拼错编译期就报错，不会变成运行期静默失败</li>
 *   <li>改菜单/改文案只需动这一个文件</li>
 * </ul>
 *
 * <p>⚠️ 想改菜单内容或点击回复的文案，改这里即可，不用碰任何其他代码。</p>
 */
public final class MenuCatalog {

    /** 关于我 */
    public static final String KEY_ABOUT = "MENU_ABOUT";
    /** 怎么用 */
    public static final String KEY_HOWTO = "MENU_HOWTO";
    /** 联系我 */
    public static final String KEY_CONTACT = "MENU_CONTACT";
    /** 当前进度 */
    public static final String KEY_PROGRESS = "MENU_PROGRESS";

    /** click key → 回复文案 */
    private static final Map<String, String> CLICK_REPLIES = buildReplies();

    private MenuCatalog() {
    }

    /**
     * 默认菜单。
     *
     * <p>三个一级菜单刻意覆盖了两种形态：<b>两个直接可点的叶子 + 一个带二级的容器</b>，
     * 这样组合模式的两条路径都能被真实验证到（而不只是单测里覆盖）。</p>
     */
    public static MenuTree defaultMenu() {
        return MenuBuilder.create()
                .click("关于我", KEY_ABOUT)
                .sub("更多", sub -> sub
                        .click("怎么用", KEY_HOWTO)
                        .click("联系我", KEY_CONTACT))
                .click("当前进度", KEY_PROGRESS)
                .build();
    }

    /**
     * 取某个 click key 对应的回复文案。
     *
     * @return 有对应文案时返回它；未知 key 返回空 —— 调用方据此决定「不回复」。
     *         菜单可能被运营者在公众平台手工改过，所以未知 key 是正常情况，不该报错。
     */
    public static Optional<String> replyForClick(String key) {
        return Optional.ofNullable(CLICK_REPLIES.get(key));
    }

    /** 全部 click 映射（供测试与文档使用） */
    public static Map<String, String> clickReplies() {
        return CLICK_REPLIES;
    }

    private static Map<String, String> buildReplies() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(KEY_ABOUT, "这是一条个人订阅号，用来练 Spring Boot + 原生 MyBatis 的消息系统。"
                + "发条文字消息试试，我会原样回给你。");
        map.put(KEY_HOWTO, "直接发文字消息就行，我会原样回给你；点菜单里的项目也能收到回复。");
        map.put(KEY_CONTACT, "有想说的直接发消息，都会记在管理后台的留言记录里。");
        map.put(KEY_PROGRESS, "微信消息收发与管理后台（数据看板、消息记录）已完成，正在做自定义菜单。");
        return Collections.unmodifiableMap(map);
    }
}
