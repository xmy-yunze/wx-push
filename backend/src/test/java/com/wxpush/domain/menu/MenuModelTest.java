package com.wxpush.domain.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单模型测试 —— 覆盖组合模式的递归结构与全部结构校验。
 *
 * <p>这一层不碰网络、不碰数据库，纯逻辑，跑得极快。它证明的是：
 * <b>非法菜单不可能被构造出来</b>（构造即校验），所以发往微信的报文天然合法。</p>
 */
@DisplayName("菜单模型（组合模式 + 建造者）")
class MenuModelTest {

    // ==================== 组合模式的递归结构 ====================

    @Test
    @DisplayName("默认菜单：3 个一级，其中一个带 2 个二级")
    void defaultMenuStructure() {
        List<MenuButton> buttons = MenuCatalog.defaultMenu().buttons();

        assertEquals(3, buttons.size());
        assertFalse(buttons.get(0).isContainer(), "「关于我」应是叶子");
        assertTrue(buttons.get(1).isContainer(), "「更多」应是容器");
        assertFalse(buttons.get(2).isContainer(), "「当前进度」应是叶子");
        assertEquals(2, buttons.get(1).children().size());
    }

    @Test
    @DisplayName("叶子序列化为 type/name/key，且不含 sub_button")
    void leafToMap() {
        MenuTree menu = MenuBuilder.create().click("关于我", "K1").build();

        Map<String, Object> button = firstButton(menu);
        assertEquals("click", button.get("type"));
        assertEquals("关于我", button.get("name"));
        assertEquals("K1", button.get("key"));
        assertFalse(button.containsKey("sub_button"), "叶子不该有 sub_button");
    }

    @Test
    @DisplayName("容器序列化为 name/sub_button，且不含 type（微信的规定）")
    void containerToMap() {
        MenuTree menu = MenuBuilder.create()
                .sub("更多", sub -> sub.click("怎么用", "K1").click("联系我", "K2"))
                .build();

        Map<String, Object> container = firstButton(menu);
        assertEquals("更多", container.get("name"));
        assertNull(container.get("type"), "容器不能带 type 字段");
        assertTrue(container.get("sub_button") instanceof List, "sub_button 应是数组");
    }

    @Test
    @DisplayName("容器递归展开子按钮 —— 组合模式的统一接口")
    void containerRecursesIntoChildren() {
        MenuTree menu = MenuBuilder.create()
                .sub("更多", sub -> sub.click("怎么用", "K1"))
                .build();

        // 关键：容器在拼 sub_button 时对每个孩子统一调 toMap()，
        // 所以这里能直接看到叶子被正确展开，调用方无需判断「孩子是什么类型」
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) firstButton(menu).get("sub_button");

        assertEquals(1, children.size());
        assertEquals("click", children.get(0).get("type"));
        assertEquals("怎么用", children.get(0).get("name"));
        assertEquals("K1", children.get(0).get("key"));
    }

    @Test
    @DisplayName("根结构是 {\"button\":[...]}（微信要求的包装）")
    void rootStructure() {
        Map<String, Object> root = MenuCatalog.defaultMenu().toMap();

        assertEquals(1, root.size());
        assertTrue(root.containsKey("button"));
    }

    // ==================== 结构校验：一级 / 二级数量 ====================

    @Test
    @DisplayName("一级菜单超过 3 个 → 拒绝")
    void tooManyTopLevel() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create()
                        .click("A", "K1").click("B", "K2").click("C", "K3").click("D", "K4")
                        .build());

        assertTrue(e.getMessage().contains("最多允许 3 个"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("二级菜单超过 5 个 → 拒绝")
    void tooManyChildren() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create()
                        .sub("更多", sub -> sub
                                .click("1", "K1").click("2", "K2").click("3", "K3")
                                .click("4", "K4").click("5", "K5").click("6", "K6"))
                        .build());

        assertTrue(e.getMessage().contains("最多允许 5 个"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("三级嵌套 → 拒绝（微信只有两级）")
    void nestedTooDeep() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create()
                        .sub("一级", sub -> sub.sub("二级", deeper -> deeper.click("三级", "K")))
                        .build());

        assertTrue(e.getMessage().contains("只支持两级"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("空菜单 → 拒绝")
    void emptyMenu() {
        assertThrows(IllegalArgumentException.class, () -> MenuBuilder.create().build());
    }

    @Test
    @DisplayName("二级菜单下面一个按钮都没有 → 拒绝")
    void emptySubMenu() {
        assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create().sub("更多", sub -> { }).build());
    }

    // ==================== 名称宽度校验 ====================

    @Test
    @DisplayName("一级名称：4 个汉字（宽度 8）合法")
    void topLevelNameAtLimit() {
        MenuTree menu = MenuBuilder.create().click("当前进度", "K").build();

        assertEquals("当前进度", firstButton(menu).get("name"));
    }

    @Test
    @DisplayName("一级名称：5 个汉字（宽度 10）被拒")
    void topLevelNameTooWide() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create().click("五个汉字啦", "K").build());

        assertTrue(e.getMessage().contains("过长"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("二级名称上限更宽：8 个汉字合法")
    void childNameAllowsWider() {
        MenuTree menu = MenuBuilder.create()
                .sub("更多", sub -> sub.click("八个汉字都没问题", "K"))
                .build();

        assertEquals("更多", firstButton(menu).get("name"));
    }

    @Test
    @DisplayName("ASCII 按 1 算宽度：8 个字母与 4 个汉字等价")
    void asciiWidthCountsOne() {
        MenuTree menu = MenuBuilder.create().click("abcdefgh", "K").build();

        assertEquals("abcdefgh", firstButton(menu).get("name"));
    }

    // ==================== 字段校验 ====================

    @Test
    @DisplayName("click 缺 key → 拒绝")
    void clickWithoutKey() {
        assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create().click("关于我", null).build());
    }

    @Test
    @DisplayName("click 的 key 超过 128 字节 → 拒绝")
    void clickKeyTooLong() {
        assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create().click("关于我", "K".repeat(129)).build());
    }

    @Test
    @DisplayName("view 填外部网址 → 拒绝，并明确提示 45058（未认证订阅号不能跳外链）")
    void viewWithExternalUrlRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                MenuBuilder.create().view("官网", "https://example.com").build());

        assertTrue(e.getMessage().contains("45058"), "实际：" + e.getMessage());
        assertTrue(e.getMessage().contains("未认证"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("view 填公众号内链接 → 放行")
    void viewWithInternalUrlAccepted() {
        MenuTree menu = MenuBuilder.create()
                .view("历史消息", "https://mp.weixin.qq.com/mp/profile_ext?action=home")
                .build();

        assertEquals("view", firstButton(menu).get("type"));
    }

    // ==================== 辅助 ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstButton(MenuTree menu) {
        List<Map<String, Object>> buttons = (List<Map<String, Object>>) menu.toMap().get("button");
        return buttons.get(0);
    }
}
