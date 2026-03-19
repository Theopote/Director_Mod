package com.chronoblocks.ui.imgui;

import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * ChronoBlocks ImGui 样式隔离作用域（仿 Treefactory 实现）。
 * <p>
 * 确保 ChronoBlocks 的 ImGui 界面与其他模组相互隔离：
 * - 其他模组的样式不会影响 ChronoBlocks 界面
 * - ChronoBlocks 的样式不会影响其他模组的界面（如 Treefactory）
 * <p>
 * 通过 pushStyleVar / pushStyleColor 在渲染时临时应用样式，渲染结束后 pop，不永久修改共享上下文。
 * <p>
 * 用法：在 ImGui.newFrame() 之后、所有 ChronoBlocks UI 渲染之前创建此 scope，
 * 在 ImGui.render() 之前关闭（使用 try-with-resources）。
 */
public final class ChronoblocksStyleScope implements AutoCloseable {

    private static final int STYLE_VAR_COUNT = 21;
    private static final int STYLE_COLOR_COUNT = 39;

    private ChronoblocksStyleScope() {
        pushChronoblocksStyle();
    }

    /**
     * 创建样式隔离作用域。在 ChronoBlocks 的 ImGui 渲染开始时调用。
     */
    public static ChronoblocksStyleScope enter() {
        return new ChronoblocksStyleScope();
    }

    /**
     * 推送 ChronoBlocks 的样式（StyleVar + StyleColor），覆盖可能被其他模组修改的值。
     */
    private void pushChronoblocksStyle() {
        // StyleVars（与 Treefactory 类似的基准变量）
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, UITheme.Spacing.PANEL_PADDING, UITheme.Spacing.PANEL_PADDING);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, UITheme.Rounding.NORMAL);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 32f, 32f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowTitleAlign, 0.0f, 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, UITheme.Rounding.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, UITheme.Rounding.NORMAL);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, UITheme.Rounding.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing, UITheme.Spacing.SMALL, UITheme.Spacing.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, UITheme.Spacing.LARGE);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 14.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, UITheme.Rounding.NORMAL);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabMinSize, 10.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, UITheme.Rounding.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.TabRounding, UITheme.Rounding.SMALL);

        // StyleColors（ChronoBlocks 深色主题）
        float[] c;
        c = toFloat(UITheme.DarkTheme.TEXT);
        ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TEXT_DISABLED);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.HIGHLIGHT);
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.WINDOW_BG);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.CHILD_BG);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.POPUP_BG);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.BORDER);
        ImGui.pushStyleColor(ImGuiCol.Border, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.BORDER_SHADOW);
        ImGui.pushStyleColor(ImGuiCol.BorderShadow, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.FRAME_BG);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.FRAME_BG_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.FRAME_BG_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TITLE_BG);
        ImGui.pushStyleColor(ImGuiCol.TitleBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TITLE_BG_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TITLE_BG_COLLAPSED);
        ImGui.pushStyleColor(ImGuiCol.TitleBgCollapsed, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TITLE_BG);
        ImGui.pushStyleColor(ImGuiCol.MenuBarBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SCROLLBAR_BG);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SCROLLBAR_GRAB);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SCROLLBAR_GRAB_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SCROLLBAR_GRAB_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.CHECK_MARK);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SLIDER_GRAB);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SLIDER_GRAB_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.BUTTON);
        ImGui.pushStyleColor(ImGuiCol.Button, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.BUTTON_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.BUTTON_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.HEADER);
        ImGui.pushStyleColor(ImGuiCol.Header, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.HEADER_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.HEADER_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SEPARATOR);
        ImGui.pushStyleColor(ImGuiCol.Separator, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SEPARATOR_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.SEPARATOR_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.RESIZE_GRIP);
        ImGui.pushStyleColor(ImGuiCol.ResizeGrip, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.RESIZE_GRIP_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.ResizeGripHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.RESIZE_GRIP_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.ResizeGripActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TAB);
        ImGui.pushStyleColor(ImGuiCol.Tab, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TAB_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.TabHovered, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TAB_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.TabActive, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TAB_UNFOCUSED);
        ImGui.pushStyleColor(ImGuiCol.TabUnfocused, c[0], c[1], c[2], c[3]);
        c = toFloat(UITheme.DarkTheme.TAB_UNFOCUSED);
        ImGui.pushStyleColor(ImGuiCol.TabUnfocusedActive, c[0], c[1], c[2], c[3]);
    }

    private static float[] toFloat(int rgba) {
        float r = ((rgba >> 24) & 0xFF) / 255.0f;
        float g = ((rgba >> 16) & 0xFF) / 255.0f;
        float b = ((rgba >> 8) & 0xFF) / 255.0f;
        float a = (rgba & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }

    @Override
    public void close() {
        ImGui.popStyleColor(STYLE_COLOR_COUNT);
        ImGui.popStyleVar(STYLE_VAR_COUNT);
    }
}
