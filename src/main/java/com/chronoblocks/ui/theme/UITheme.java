package com.chronoblocks.ui.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI主题系统 - 统一的视觉风格和设计规范
 * 
 * <p>参考专业动画软件（Blender, After Effects）的设计规范
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class UITheme {
    private static final Logger LOGGER = LoggerFactory.getLogger(UITheme.class);
    
    // ==================== 颜色主题 ====================
    
    /** 深色主题（默认） */
    public static class DarkTheme {
        // 背景色
        public static final int WINDOW_BG = rgba(24, 24, 24, 245);           // 主窗口背景
        public static final int CHILD_BG = rgba(28, 28, 28, 255);            // 子窗口背景
        public static final int POPUP_BG = rgba(32, 32, 32, 240);            // 弹出窗口背景
        
        // 文本色
        public static final int TEXT = rgba(220, 220, 220, 255);             // 主文本
        public static final int TEXT_DISABLED = rgba(128, 128, 128, 255);    // 禁用文本
        public static final int TEXT_SELECTED = rgba(255, 255, 255, 255);    // 选中文本
        
        // 边框色
        public static final int BORDER = rgba(48, 48, 48, 255);              // 边框
        public static final int BORDER_SHADOW = rgba(0, 0, 0, 80);           // 边框阴影
        
        // 按钮色
        public static final int BUTTON = rgba(51, 51, 51, 255);              // 按钮默认
        public static final int BUTTON_HOVERED = rgba(68, 68, 68, 255);      // 按钮悬停
        public static final int BUTTON_ACTIVE = rgba(85, 85, 85, 255);       // 按钮按下
        
        // 标题栏
        public static final int TITLE_BG = rgba(32, 32, 32, 255);            // 标题栏
        public static final int TITLE_BG_ACTIVE = rgba(42, 42, 42, 255);     // 活动标题栏
        public static final int TITLE_BG_COLLAPSED = rgba(28, 28, 28, 255);  // 折叠标题栏
        
        // 选择器和滑块
        public static final int FRAME_BG = rgba(42, 42, 42, 255);            // 输入框背景
        public static final int FRAME_BG_HOVERED = rgba(51, 51, 51, 255);    // 输入框悬停
        public static final int FRAME_BG_ACTIVE = rgba(60, 60, 60, 255);     // 输入框激活
        
        // 滑块和进度条
        public static final int SLIDER_GRAB = rgba(61, 133, 224, 255);       // 滑块抓取
        public static final int SLIDER_GRAB_ACTIVE = rgba(51, 118, 204, 255);// 滑块抓取激活
        
        // 复选框和单选按钮
        public static final int CHECK_MARK = rgba(61, 133, 224, 255);        // 复选标记
        
        // 标签页
        public static final int TAB = rgba(42, 42, 42, 255);                 // 标签页默认
        public static final int TAB_HOVERED = rgba(51, 51, 51, 255);         // 标签页悬停
        public static final int TAB_ACTIVE = rgba(51, 118, 204, 255);        // 标签页激活
        public static final int TAB_UNFOCUSED = rgba(38, 38, 38, 255);       // 标签页未聚焦
        
        // 滚动条
        public static final int SCROLLBAR_BG = rgba(24, 24, 24, 200);        // 滚动条背景
        public static final int SCROLLBAR_GRAB = rgba(68, 68, 68, 255);      // 滚动条抓取
        public static final int SCROLLBAR_GRAB_HOVERED = rgba(85, 85, 85, 255);
        public static final int SCROLLBAR_GRAB_ACTIVE = rgba(102, 102, 102, 255);
        
        // 分割线
        public static final int SEPARATOR = rgba(68, 68, 68, 255);           // 分割线
        public static final int SEPARATOR_HOVERED = rgba(85, 85, 85, 255);   // 分割线悬停
        public static final int SEPARATOR_ACTIVE = rgba(102, 102, 102, 255); // 分割线激活
        
        // 表头
        public static final int HEADER = rgba(51, 51, 51, 255);              // 表头
        public static final int HEADER_HOVERED = rgba(68, 68, 68, 255);      // 表头悬停
        public static final int HEADER_ACTIVE = rgba(85, 85, 85, 255);       // 表头激活
        
        // 高亮色（选择、激活等）
        public static final int HIGHLIGHT = rgba(61, 133, 224, 102);         // 选择高亮
        public static final int HIGHLIGHT_HOVERED = rgba(61, 133, 224, 153); // 选择高亮悬停
        
        // 调整大小手柄
        public static final int RESIZE_GRIP = rgba(61, 133, 224, 51);        // 调整大小手柄
        public static final int RESIZE_GRIP_HOVERED = rgba(61, 133, 224, 153);
        public static final int RESIZE_GRIP_ACTIVE = rgba(61, 133, 224, 255);
    }
    
    // ==================== 间距规范 ====================
    
    /** 间距常量（像素） */
    public static class Spacing {
        public static final float TINY = 2.0f;          // 超小间距
        public static final float SMALL = 4.0f;         // 小间距
        public static final float NORMAL = 8.0f;        // 标准间距
        public static final float MEDIUM = 12.0f;       // 中等间距
        public static final float LARGE = 16.0f;        // 大间距
        public static final float EXTRA_LARGE = 24.0f;  // 超大间距
        
        // 内边距
        public static final float PADDING_TINY = 2.0f;
        public static final float PADDING_SMALL = 4.0f;
        public static final float PADDING_NORMAL = 8.0f;
        public static final float PADDING_LARGE = 12.0f;
        
        // 面板边距
        public static final float PANEL_PADDING = 12.0f;
        public static final float PANEL_SPACING = 8.0f;
        
        // 按钮尺寸
        public static final float BUTTON_HEIGHT = 24.0f;
        public static final float BUTTON_PADDING_X = 12.0f;
        public static final float BUTTON_PADDING_Y = 6.0f;
        
        // 输入框
        public static final float INPUT_HEIGHT = 24.0f;
        public static final float INPUT_PADDING = 8.0f;
        
        // 标题栏
        public static final float TITLE_HEIGHT = 28.0f;
        
        // 分组
        public static final float GROUP_PADDING = 8.0f;
        public static final float GROUP_SPACING = 12.0f;
    }
    
    // ==================== 圆角规范 ====================
    
    /** 圆角半径（像素） */
    public static class Rounding {
        public static final float NONE = 0.0f;
        public static final float SMALL = 2.0f;
        public static final float NORMAL = 4.0f;
        public static final float MEDIUM = 6.0f;
        public static final float LARGE = 8.0f;
    }
    
    // ==================== 字体规范 ====================
    
    /** 字体大小 */
    public static class FontSize {
        public static final float TINY = 11.0f;
        public static final float SMALL = 13.0f;
        public static final float NORMAL = 15.0f;
        public static final float MEDIUM = 17.0f;
        public static final float LARGE = 19.0f;
        public static final float EXTRA_LARGE = 24.0f;
        public static final float TITLE = 18.0f;
    }
    
    // ==================== 应用主题 ====================
    
    /**
     * 应用深色主题
     */
    public static void applyDarkTheme() {
        ImGuiStyle style = ImGui.getStyle();

        // 设置颜色（使用辅助方法）
        setStyleColor(style, ImGuiCol.Text, DarkTheme.TEXT);
        setStyleColor(style, ImGuiCol.TextDisabled, DarkTheme.TEXT_DISABLED);
        setStyleColor(style, ImGuiCol.TextSelectedBg, DarkTheme.HIGHLIGHT);
        
        setStyleColor(style, ImGuiCol.WindowBg, DarkTheme.WINDOW_BG);
        setStyleColor(style, ImGuiCol.ChildBg, DarkTheme.CHILD_BG);
        setStyleColor(style, ImGuiCol.PopupBg, DarkTheme.POPUP_BG);
        
        setStyleColor(style, ImGuiCol.Border, DarkTheme.BORDER);
        setStyleColor(style, ImGuiCol.BorderShadow, DarkTheme.BORDER_SHADOW);
        
        setStyleColor(style, ImGuiCol.FrameBg, DarkTheme.FRAME_BG);
        setStyleColor(style, ImGuiCol.FrameBgHovered, DarkTheme.FRAME_BG_HOVERED);
        setStyleColor(style, ImGuiCol.FrameBgActive, DarkTheme.FRAME_BG_ACTIVE);
        
        setStyleColor(style, ImGuiCol.TitleBg, DarkTheme.TITLE_BG);
        setStyleColor(style, ImGuiCol.TitleBgActive, DarkTheme.TITLE_BG_ACTIVE);
        setStyleColor(style, ImGuiCol.TitleBgCollapsed, DarkTheme.TITLE_BG_COLLAPSED);
        
        setStyleColor(style, ImGuiCol.MenuBarBg, DarkTheme.TITLE_BG);
        
        setStyleColor(style, ImGuiCol.ScrollbarBg, DarkTheme.SCROLLBAR_BG);
        setStyleColor(style, ImGuiCol.ScrollbarGrab, DarkTheme.SCROLLBAR_GRAB);
        setStyleColor(style, ImGuiCol.ScrollbarGrabHovered, DarkTheme.SCROLLBAR_GRAB_HOVERED);
        setStyleColor(style, ImGuiCol.ScrollbarGrabActive, DarkTheme.SCROLLBAR_GRAB_ACTIVE);
        
        setStyleColor(style, ImGuiCol.CheckMark, DarkTheme.CHECK_MARK);
        
        setStyleColor(style, ImGuiCol.SliderGrab, DarkTheme.SLIDER_GRAB);
        setStyleColor(style, ImGuiCol.SliderGrabActive, DarkTheme.SLIDER_GRAB_ACTIVE);
        
        setStyleColor(style, ImGuiCol.Button, DarkTheme.BUTTON);
        setStyleColor(style, ImGuiCol.ButtonHovered, DarkTheme.BUTTON_HOVERED);
        setStyleColor(style, ImGuiCol.ButtonActive, DarkTheme.BUTTON_ACTIVE);
        
        setStyleColor(style, ImGuiCol.Header, DarkTheme.HEADER);
        setStyleColor(style, ImGuiCol.HeaderHovered, DarkTheme.HEADER_HOVERED);
        setStyleColor(style, ImGuiCol.HeaderActive, DarkTheme.HEADER_ACTIVE);
        
        setStyleColor(style, ImGuiCol.Separator, DarkTheme.SEPARATOR);
        setStyleColor(style, ImGuiCol.SeparatorHovered, DarkTheme.SEPARATOR_HOVERED);
        setStyleColor(style, ImGuiCol.SeparatorActive, DarkTheme.SEPARATOR_ACTIVE);
        
        setStyleColor(style, ImGuiCol.ResizeGrip, DarkTheme.RESIZE_GRIP);
        setStyleColor(style, ImGuiCol.ResizeGripHovered, DarkTheme.RESIZE_GRIP_HOVERED);
        setStyleColor(style, ImGuiCol.ResizeGripActive, DarkTheme.RESIZE_GRIP_ACTIVE);
        
        setStyleColor(style, ImGuiCol.Tab, DarkTheme.TAB);
        setStyleColor(style, ImGuiCol.TabHovered, DarkTheme.TAB_HOVERED);
        setStyleColor(style, ImGuiCol.TabActive, DarkTheme.TAB_ACTIVE);
        setStyleColor(style, ImGuiCol.TabUnfocused, DarkTheme.TAB_UNFOCUSED);
        setStyleColor(style, ImGuiCol.TabUnfocusedActive, DarkTheme.TAB_UNFOCUSED);
        
        // 设置样式参数
        style.setWindowPadding(Spacing.PANEL_PADDING, Spacing.PANEL_PADDING);
        style.setFramePadding(Spacing.PADDING_NORMAL, Spacing.PADDING_SMALL);
        style.setItemSpacing(Spacing.NORMAL, Spacing.NORMAL);
        style.setItemInnerSpacing(Spacing.SMALL, Spacing.SMALL);
        style.setIndentSpacing(Spacing.LARGE);
        
        // 圆角
        style.setWindowRounding(Rounding.NORMAL);
        style.setChildRounding(Rounding.SMALL);
        style.setFrameRounding(Rounding.SMALL);
        style.setPopupRounding(Rounding.NORMAL);
        style.setScrollbarRounding(Rounding.NORMAL);
        style.setGrabRounding(Rounding.SMALL);
        style.setTabRounding(Rounding.SMALL);
        
        // 边框
        style.setWindowBorderSize(1.0f);
        style.setChildBorderSize(0.0f);
        style.setPopupBorderSize(1.0f);
        style.setFrameBorderSize(0.0f);
        style.setTabBorderSize(0.0f);
        
        // 滚动条
        style.setScrollbarSize(14.0f);
        
        // 抗锯齿
        style.setAntiAliasedLines(true);
        style.setAntiAliasedFill(true);
        
        LOGGER.info("[UITheme] 深色主题已应用");
    }
    
    /**
     * 转换RGBA整数为float数组（内部使用）
     */
    private static float[] toFloatArray(int rgba) {
        float r = ((rgba >> 24) & 0xFF) / 255.0f;
        float g = ((rgba >> 16) & 0xFF) / 255.0f;
        float b = ((rgba >> 8) & 0xFF) / 255.0f;
        float a = (rgba & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }
    
    /**
     * 转换RGBA整数为float数组（公共方法）
     * @param rgba RGBA颜色值
     * @return float数组 [r, g, b, a]
     */
    public static float[] toFloatArrayPublic(int rgba) {
        return toFloatArray(rgba);
    }
    
    /**
     * 设置样式颜色的辅助方法
     * @param style ImGui样式
     * @param idx 颜色索引
     * @param rgba RGBA颜色值
     */
    private static void setStyleColor(ImGuiStyle style, int idx, int rgba) {
        float[] color = toFloatArray(rgba);
        style.setColor(idx, color[0], color[1], color[2], color[3]);
    }
    
    /**
     * RGBA颜色打包
     */
    private static int rgba(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 添加垂直间距
     */
    public static void spacing(float height) {
        ImGui.dummy(0, height);
    }
    
    /**
     * 添加标准间距
     */
    public static void spacing() {
        spacing(Spacing.NORMAL);
    }
    
    /**
     * 添加小间距
     */
    public static void spacingSmall() {
        spacing(Spacing.SMALL);
    }
    
    /**
     * 添加大间距
     */
    public static void spacingLarge() {
        spacing(Spacing.LARGE);
    }
    
    /**
     * 开始水平布局
     */
    public static void beginHorizontal() {
        // ImGui 自动水平布局，使用 sameLine()
    }
    
    /**
     * 分隔符（加强版）
     */
    public static void separator() {
        spacing(Spacing.SMALL);
        ImGui.separator();
        spacing(Spacing.SMALL);
    }
    
    /**
     * 文本标签（带颜色）
     */
    public static void label(String text, int color) {
        float[] col = toFloatArray(color);
        ImGui.textColored(col[0], col[1], col[2], col[3], text);
    }
    
    /**
     * 标题文本
     */
    public static void title(String text) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, Spacing.MEDIUM);
        label(text, DarkTheme.TEXT_SELECTED);
        ImGui.popStyleVar();
        separator();
    }
    
    /**
     * 分组开始
     */
    public static void beginGroup(String title) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, Spacing.GROUP_PADDING, Spacing.GROUP_PADDING);
        ImGui.beginGroup();
        if (title != null && !title.isEmpty()) {
            label(title, DarkTheme.TEXT);
            spacingSmall();
        }
    }
    
    /**
     * 分组结束
     */
    public static void endGroup() {
        ImGui.endGroup();
        ImGui.popStyleVar();
        spacing(Spacing.GROUP_SPACING);
    }
}
