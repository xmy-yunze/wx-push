package com.wxpush.domain.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 菜单建造者 —— 把「一层套一层的菜单树」写得像一句话。
 *
 * <h3>不用建造者会怎样</h3>
 * <p>菜单是嵌套结构，直接 new 出来长这样：</p>
 * <pre>{@code
 * new MenuTree(List.of(
 *     new ClickButton("关于我", "MENU_ABOUT"),
 *     new SubMenu("更多", List.of(
 *         new ClickButton("怎么用", "MENU_HOWTO"),
 *         new ClickButton("联系我", "MENU_CONTACT"))),
 *     new ClickButton("更新日志", "MENU_CHANGELOG")));
 * }</pre>
 * <p>括号层级已经很难一眼看懂，而且<b>校验完全依赖调用方自觉</b>。</p>
 *
 * <h3>用建造者之后</h3>
 * <pre>{@code
 * MenuBuilder.create()
 *     .click("关于我", MenuCatalog.KEY_ABOUT)
 *     .sub("更多", sub -> sub
 *         .click("怎么用", MenuCatalog.KEY_HOWTO)
 *         .click("联系我", MenuCatalog.KEY_CONTACT))
 *     .click("更新日志", MenuCatalog.KEY_CHANGELOG)
 *     .build();
 * }</pre>
 *
 * <p>两点实际收益：</p>
 * <ol>
 *   <li><b>可读</b>：缩进层级直接映射菜单层级，一眼看出结构</li>
 *   <li><b>安全</b>：{@link #build()} 返回的一定是校验通过的 {@link MenuTree}，
 *       绕不过去。校验放在建造者里，就不会有「某处忘了校验」</li>
 * </ol>
 */
public final class MenuBuilder {

    private final List<MenuButton> buttons = new ArrayList<>();

    /** 只能通过 {@link #create()} 开始建造 */
    private MenuBuilder() {
    }

    public static MenuBuilder create() {
        return new MenuBuilder();
    }

    /** 加一个 click 类型按钮（点击后微信推 CLICK 事件到我们的服务器） */
    public MenuBuilder click(String name, String key) {
        buttons.add(new ClickButton(name, key));
        return this;
    }

    /** 加一个 view 类型按钮（跳转网页，未认证订阅号只能填公众号内链接） */
    public MenuBuilder view(String name, String url) {
        buttons.add(new ViewButton(name, url));
        return this;
    }

    /**
     * 加一个带二级子菜单的按钮。
     *
     * @param childrenSpec 子菜单的构造过程，例如 {@code sub -> sub.click("A", "K1")}
     */
    public MenuBuilder sub(String name, Consumer<MenuBuilder> childrenSpec) {
        // 用一个独立的小建造者收集孩子：这样调用方不必自己 new 列表，
        // 而且「二级里再嵌二级」会被 SubMenu/MenuTree 的校验直接拒掉
        MenuBuilder childBuilder = new MenuBuilder();
        childrenSpec.accept(childBuilder);
        buttons.add(new SubMenu(name, childBuilder.buttons));
        return this;
    }

    /**
     * 完成建造。
     *
     * @throws IllegalArgumentException 菜单结构不合法（超过 3 个一级、超过 5 个二级、名称过长等）
     */
    public MenuTree build() {
        return new MenuTree(buttons);
    }
}
