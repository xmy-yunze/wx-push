package com.wxpush.domain.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树 —— 组合模式的「根」，对应微信接口最外层的 {@code {"button":[...]}}。
 *
 * <p>它单独存在（而不是直接用 {@link SubMenu} 当根）是因为微信对根的报文格式
 * 有额外一层包装：{@code button} 数组。</p>
 *
 * <p>⚠️ <b>构造即校验</b>：{@link #MenuTree(List)} 里直接跑完整校验，
 * 所以一个「非法菜单树」对象<b>根本不可能被创建出来</b>。
 * 这比「先构造、再调用方记得校验」可靠得多 —— 后者总有漏掉的时候。
 * 配合 {@link MenuBuilder}，外部只能通过 {@code build()} 得到菜单树，
 * 而 {@code build()} 只会返回校验通过的对象。</p>
 */
public final class MenuTree {

    /** 一级菜单个数上限（微信规定） */
    public static final int MAX_TOP_LEVEL = 3;

    private final List<MenuButton> buttons;

    /**
     * 包级构造：外部只应通过 {@link MenuBuilder#build()} 创建。
     *
     * @throws IllegalArgumentException 结构不合法
     */
    MenuTree(List<MenuButton> buttons) {
        this.buttons = List.copyOf(buttons);
        validate();
    }

    public List<MenuButton> buttons() {
        return buttons;
    }

    /** 转成微信菜单接口要求的根结构：{@code {"button":[...]}} */
    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("button", buttons.stream().map(MenuButton::toMap).toList());
        return root;
    }

    private void validate() {
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("菜单不能为空：至少要有 1 个一级菜单");
        }
        if (buttons.size() > MAX_TOP_LEVEL) {
            throw new IllegalArgumentException(
                    "一级菜单有 " + buttons.size() + " 个，微信最多允许 " + MAX_TOP_LEVEL + " 个");
        }
        for (MenuButton button : buttons) {
            if (button == null) {
                throw new IllegalArgumentException("菜单中存在空按钮");
            }
            // level=1 —— 校验会顺着组合结构往下递归
            button.validate(1);
        }
    }
}
