package com.director.i18n;

/**
 * 工具名称/描述/教学提示的 lang 键解析（{@code gui.director.tool.{id}.*}）。
 */
public final class ToolI18n {

    private static final String PREFIX = "gui.director.tool.";

    private ToolI18n() {
    }

    /** 将 toolId 规范化为 lang 键段（如 {@code __world_placeholder__} → {@code world_placeholder}）。 */
    public static String langId(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return "unknown";
        }
        String id = toolId.trim();
        if (id.startsWith("__") && id.endsWith("__") && id.length() > 4) {
            id = id.substring(2, id.length() - 2);
        }
        return id.replace('-', '_');
    }

    public static String nameKey(String toolId) {
        return PREFIX + langId(toolId) + ".name";
    }

    public static String descKey(String toolId) {
        return PREFIX + langId(toolId) + ".desc";
    }

    public static String tipKey(String toolId, int index) {
        return PREFIX + langId(toolId) + ".tip." + index;
    }

    public static String name(String toolId) {
        return DirectorI18n.translate(nameKey(toolId));
    }

    public static String description(String toolId) {
        return DirectorI18n.translate(descKey(toolId));
    }

    public static String tip(String toolId, int index) {
        return DirectorI18n.translate(tipKey(toolId, index));
    }
}
