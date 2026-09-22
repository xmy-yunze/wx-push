package com.wxpush.domain.menu;

import java.util.List;
import java.util.Map;

/**
 * 菜单按钮 —— <b>组合模式中的「组件」</b>。
 *
 * <h3>为什么这里适合组合模式</h3>
 * <p>微信菜单天然是树：一级按钮可以直接是功能项，也可以展开出二级按钮。
 * 而两者对上层来说应该是同一种东西 —— 调用方不需要知道「我手上这个是叶子还是容器」，
 * 只要能拿到它对应的 JSON 就行。</p>
 *
 * <p>组合模式带来的实际好处是：<b>{@link #toMap()} 写得极其自然</b>。
 * 容器在拼自己的结构时，对每个孩子统一调 {@code toMap()}，
 * 孩子是叶子还是更深一层容器，容器自己不用关心 ——
 * 递归会在运行期自然展开。如果不用组合模式，就得写
 * {@code if (child instanceof SubMenu) {...} else {...}} 这种类型判断，
 * 而且每加一种按钮类型就要改一遍。</p>
 *
 * <p>⚠️ 微信的约束：菜单<b>只有两级</b>。二级按钮里不能再嵌子菜单。
 * 这条约束由 {@link SubMenu} 在构造时强制，不靠使用者自觉。</p>
 */
public abstract class MenuButton {

    /**
     * 一级菜单名称的显示宽度上限。
     *
     * <p>微信官方口径是「一级最多 4 个汉字<b>或</b> 8 个字母」—— 说明它按<b>显示宽度</b>算，
     * 不是按字符个数。所以中文算 2 个单位、ASCII 算 1 个单位。</p>
     */
    protected static final int MAX_WIDTH_LEVEL_1 = 8;

    /** 二级菜单名称宽度上限：8 个汉字或 16 个字母 */
    protected static final int MAX_WIDTH_LEVEL_2 = 16;

    /** 按钮显示名称 */
    protected final String name;

    protected MenuButton(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * 是否为容器（能展开二级菜单）。
     *
     * <p>默认 {@code false}：绝大多数按钮是叶子。只有 {@link SubMenu} 覆写它。
     * 需要判断类型时优先用这个方法，而不是 {@code instanceof} ——
     * 将来新增容器类型时，调用方不用改。</p>
     */
    public boolean isContainer() {
        return false;
    }

    /**
     * 子按钮列表。
     *
     * <p>叶子返回空列表，容器返回真实列表。调用方可以直接遍历，
     * 不用先判断「有没有孩子」—— 空列表的遍历天然是安全的。</p>
     */
    public List<MenuButton> children() {
        return List.of();
    }

    /**
     * 转成微信菜单接口要求的 Map 结构（字段顺序保留，便于人工核对）。
     *
     * <p>这是组合模式的<b>统一接口</b>：叶子和容器都提供它，容器在实现里递归调用孩子的同名方法。</p>
     */
    public abstract Map<String, Object> toMap();

    /**
     * 名称的显示宽度：ASCII 算 1，其余（中文、全角符号）算 2。
     *
     * <p>⚠️ 用 {@code charAt} 遍历，代理对字符（如 emoji）会被算成 4 —— 略偏保守，
     * 但菜单名里基本不会出现 emoji，不为此增加复杂度。</p>
     */
    protected int nameWidth() {
        int width = 0;
        for (int i = 0; i < name.length(); i++) {
            width += (name.charAt(i) < 0x80) ? 1 : 2;
        }
        return width;
    }

    /**
     * 校验自身合法性。
     *
     * @param level 层级：1 = 一级，2 = 二级
     * @throws IllegalArgumentException 不合法时抛出，消息是可直接展示给运营者的中文
     */
    void validate(int level) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        int max = (level == 1) ? MAX_WIDTH_LEVEL_1 : MAX_WIDTH_LEVEL_2;
        if (nameWidth() > max) {
            throw new IllegalArgumentException(
                    "菜单名称「" + name + "」过长：这是 " + level + " 级菜单，最多 " + max
                            + " 个字符宽（1 个汉字算 2），当前为 " + nameWidth());
        }
    }

    /** 供子类统一拼装「名称」字段，省掉每处重复的 put */
    protected void putName(Map<String, Object> target) {
        target.put("name", name);
    }
}
