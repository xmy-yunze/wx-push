package com.wxpush.domain.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 二级菜单容器 —— 组合模式中的「容器（Composite）」。
 *
 * <p>它和 {@link ClickButton} 这类叶子的关系是：<b>都实现 {@link MenuButton}，
 * 都能被 {@code toMap()} 递归展开</b>。上层把一组按钮丢给菜单树，
 * 树对每个孩子统一调 {@code toMap()} —— 孩子下面还有没有一层，树不需要知道。</p>
 *
 * <p>⚠️ 微信菜单<b>只有两级</b>：一级按钮下面挂 sub_button，而 sub_button 里
 * 不能再有 sub_button。这条限制由本类的 {@link #validate(int)} 强制
 * （收到 level ≥ 2 时直接拒绝），而不是靠使用者记得。</p>
 */
public final class SubMenu extends MenuButton {

    /** 二级按钮个数上限（微信规定） */
    private static final int MAX_CHILDREN = 5;

    private final List<MenuButton> children;

    public SubMenu(String name, List<MenuButton> children) {
        super(name);
        // 防御性拷贝：避免外部在构造后改动列表，导致「校验通过但实际内容变了」
        this.children = List.copyOf(children);
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public List<MenuButton> children() {
        return children;
    }

    /**
     * ⚠️ 注意这里<b>没有</b> {@code type} 字段 —— 微信规定容器按钮不带 type，
     * 它靠「有没有 sub_button」来区分。这和叶子的结构差异正是组合模式要屏蔽的东西：
     * 差异只在本类里，调用方看不到。
     */
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putName(map);
        // 组合模式最关键的一行：对每个孩子统一调 toMap()，不判断它是叶子还是更深一层
        map.put("sub_button", children.stream().map(MenuButton::toMap).toList());
        return map;
    }

    @Override
    void validate(int level) {
        super.validate(level);

        if (level >= 2) {
            throw new IllegalArgumentException(
                    "菜单「" + name + "」位于二级位置，微信只支持两级菜单，二级按钮不能再包含子菜单");
        }
        if (children.isEmpty()) {
            throw new IllegalArgumentException("二级菜单「" + name + "」下面至少要有一个按钮");
        }
        if (children.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException(
                    "二级菜单「" + name + "」下有 " + children.size() + " 个按钮，微信最多允许 " + MAX_CHILDREN + " 个");
        }
        // 递归校验孩子（它们必然是 level 2）
        for (MenuButton child : children) {
            if (child == null) {
                throw new IllegalArgumentException("二级菜单「" + name + "」中存在空按钮");
            }
            child.validate(2);
        }
    }
}
