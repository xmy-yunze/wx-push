package com.wxpush.domain.menu;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 JSON → {@link MenuTree} 的解析器。
 *
 * <h3>为什么要有这一层</h3>
 * <p>前端提交的是<b>微信格式的菜单 JSON</b>（{@code {"button":[...]}}），
 * 但它是不可信输入 —— 用 curl 就能直接调这个接口。所以必须先解析成模型、
 * 经过组合模式那一整套校验（一级 ≤3、二级 ≤5、名称长度、禁止三级嵌套），
 * 才允许发往微信。</p>
 *
 * <p>这样做比「把前端 JSON 直接转发给微信」好在：错误信息由我们自己给，
 * 能说清「哪里不对、怎么改」；而微信只会回一个 {@code 40054 菜单结构不合法}，
 * 运营者拿到这个不知道该动哪。</p>
 *
 * <p>⚠️ 解析失败一律抛 {@link IllegalArgumentException}，由全局异常处理器转成 400 ——
 * 这类错误是<b>调用方传错了</b>，不是服务端故障。</p>
 */
public final class MenuParser {

    private MenuParser() {
    }

    /**
     * 解析并校验菜单。
     *
     * @param root 形如 {@code {"button":[{"type":"click","name":"关于我","key":"..."}]}} 的 JSON
     * @throws IllegalArgumentException 结构或字段不合法
     */
    public static MenuTree parse(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new IllegalArgumentException("菜单内容为空，请至少提供一个一级菜单");
        }

        JsonNode buttonArray = root.get("button");
        if (buttonArray == null) {
            throw new IllegalArgumentException("菜单 JSON 缺少 button 字段（微信要求的根结构是 {\"button\":[...]}）");
        }
        if (!buttonArray.isArray()) {
            throw new IllegalArgumentException("button 必须是数组");
        }

        List<MenuButton> buttons = new ArrayList<>();
        for (JsonNode node : buttonArray) {
            buttons.add(parseButton(node, 1));
        }

        // MenuTree 的构造即校验：一级 ≤3、二级 ≤5、名称宽度、禁止三级嵌套都在这里被强制执行
        return new MenuTree(buttons);
    }

    /** 递归解析单个按钮：带 sub_button 的当容器，否则按 type 当叶子 */
    private static MenuButton parseButton(JsonNode node, int level) {
        String name = text(node, "name");
        if (name == null) {
            throw new IllegalArgumentException("发现一个没有名称（name）的菜单按钮");
        }

        if (node.has("sub_button")) {
            JsonNode subArray = node.get("sub_button");
            if (!subArray.isArray()) {
                throw new IllegalArgumentException("菜单「" + name + "」的 sub_button 必须是数组");
            }
            List<MenuButton> children = new ArrayList<>();
            for (JsonNode child : subArray) {
                // 二级按钮统一按 level=2 解析；它若还带 sub_button，会被 MenuTree 的校验拒掉
                children.add(parseButton(child, 2));
            }
            return new SubMenu(name, children);
        }

        String type = text(node, "type");
        if ("click".equals(type)) {
            return new ClickButton(name, text(node, "key"));
        }
        if ("view".equals(type)) {
            return new ViewButton(name, text(node, "url"));
        }
        throw new IllegalArgumentException(
                "菜单「" + name + "」的类型不被支持：" + (type == null ? "未指定 type" : type)
                        + "。当前仅支持 click 与 view");
    }

    /** 取字符串字段；空串按缺失处理，便于统一走「必填校验」 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
