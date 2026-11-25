package com.director.ui.feedback;

import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Objects;

/**
 * 面板内的一行反馈：操作失败的原因、控件被禁用的原因。
 *
 * <p>统一图标、颜色与换行，避免各个适配器各自保存 {@code String error} 再用 {@code textWrapped} 画出来。
 * 实例保存“属于哪个对象的最近一条消息”，对象切换后自动失效；{@link #draw} 与 {@link #drawReason}
 * 是不带状态的一次性绘制，用来说明控件为什么不可用。
 *
 * <p>状态部分（{@link #show}、{@link #messageFor}、{@link #clear}）不依赖 ImGui，可直接单元测试。
 */
public final class InlineNotice {
    public enum Level { INFO, WARNING, ERROR }

    private Object owner;
    private Level level = Level.INFO;
    private String message = "";

    /** 记录一条属于 {@code owner} 的消息；空消息等同于清除。 */
    public void show(Object owner, Level level, String message) {
        this.level = Objects.requireNonNull(level, "level");
        this.owner = owner;
        this.message = message == null ? "" : message;
    }

    public void clear() {
        owner = null;
        message = "";
    }

    /** 返回应显示给 {@code currentOwner} 的消息；若消息属于别的对象则先清除并返回空串。 */
    public String messageFor(Object currentOwner) {
        if (owner != currentOwner) {
            clear();
            return "";
        }
        return message;
    }

    public Level level() {
        return level;
    }

    /** 画出属于 {@code currentOwner} 的消息；没有消息时什么也不画。 */
    public void render(Object currentOwner) {
        String text = messageFor(currentOwner);
        if (!text.isEmpty()) {
            draw(level, text);
        }
    }

    /** 一次性绘制一条带图标的彩色消息，自动换行。 */
    public static void draw(Level level, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String icon;
        int color;
        switch (level) {
            case ERROR -> { icon = Icons.ERROR; color = UITheme.Semantic.DANGER; }
            case WARNING -> { icon = Icons.WARNING; color = UITheme.Semantic.WARNING; }
            default -> { icon = Icons.INFO; color = UITheme.Semantic.INFO; }
        }
        drawWrapped(icon + " " + text, color);
    }

    /** 说明“为什么这些控件现在不可用”：锁图标加次级文字，不使用警示色。 */
    public static void drawReason(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        drawWrapped(Icons.LOCK + " " + text, UITheme.DarkTheme.TEXT_DISABLED);
    }

    private static void drawWrapped(String text, int color) {
        float[] c = UITheme.toFloatArrayPublic(color);
        ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], c[3]);
        ImGui.pushTextWrapPos(0f);
        ImGui.text(text);
        ImGui.popTextWrapPos();
        ImGui.popStyleColor();
    }
}
