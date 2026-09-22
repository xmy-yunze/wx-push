package com.wxpush.domain.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单 JSON 解析测试。
 *
 * <p>这一层守的是「不可信输入」的边界：接口是开放的，用 curl 就能塞任何 JSON 进来。
 * 解析器必须在<b>发往微信之前</b>把不合法的结构拦掉，否则错误会以微信的
 * {@code 40054} 形式回来，运营者根本看不懂该改哪里。</p>
 */
@DisplayName("菜单 JSON 解析")
class MenuParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static MenuTree parse(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        return MenuParser.parse(node);
    }

    @Test
    @DisplayName("合法菜单 → 解析出正确的树结构")
    void parsesValidMenu() throws Exception {
        MenuTree menu = parse("""
                {"button":[
                  {"type":"click","name":"关于我","key":"K1"},
                  {"name":"更多","sub_button":[
                    {"type":"click","name":"怎么用","key":"K2"},
                    {"type":"view","name":"历史消息","url":"https://mp.weixin.qq.com/x"}
                  ]}
                ]}""");

        assertEquals(2, menu.buttons().size());
        assertTrue(menu.buttons().get(1).isContainer());
        assertEquals(2, menu.buttons().get(1).children().size());
    }

    @Test
    @DisplayName("缺少 button 字段 → 拒绝，并说明微信要求的根结构")
    void rejectsMissingButton() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"menu\":{}}"));

        assertTrue(e.getMessage().contains("button"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("button 不是数组 → 拒绝")
    void rejectsNonArrayButton() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"button\":{}}"));
    }

    @Test
    @DisplayName("空的 button 数组 → 拒绝（清空菜单要用删除接口，不是发空菜单）")
    void rejectsEmptyButtonArray() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"button\":[]}"));
    }

    @Test
    @DisplayName("按钮缺少 name → 拒绝")
    void rejectsMissingName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"button\":[{\"type\":\"click\",\"key\":\"K1\"}]}"));

        assertTrue(e.getMessage().contains("名称"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("name 是空白串 → 按缺失处理并拒绝")
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"button\":[{\"type\":\"click\",\"name\":\"   \",\"key\":\"K1\"}]}"));
    }

    @Test
    @DisplayName("sub_button 不是数组 → 拒绝")
    void rejectsNonArraySubButton() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"button\":[{\"name\":\"更多\",\"sub_button\":\"oops\"}]}"));
    }

    @Test
    @DisplayName("click 按钮缺 key → 拒绝")
    void rejectsClickWithoutKey() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"button\":[{\"type\":\"click\",\"name\":\"关于我\"}]}"));
    }

    @Test
    @DisplayName("view 按钮填外链 → 拒绝（未认证订阅号限制）")
    void rejectsExternalUrl() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"button\":[{\"type\":\"view\",\"name\":\"官网\",\"url\":\"https://example.com\"}]}"));

        assertTrue(e.getMessage().contains("45058"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("null 节点 → 拒绝")
    void rejectsNullNode() {
        assertThrows(IllegalArgumentException.class, () -> MenuParser.parse(null));
    }
}
