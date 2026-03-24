package com.director.ui.theme;

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
    
    /** 深色主题（默认）：底色更深、控件面更亮，对比清晰；无边框描边依赖底色差分轮廓。 */
    public static class DarkTheme {
        // 背景色（窗口最暗，子区域逐级提亮）
        public static final int WINDOW_BG = rgba(16, 16, 18, 220);           // 主窗口背景（更透明）
        public static final int CHILD_BG = rgba(38, 38, 42, 230);            // 子窗口 / 内容区（更透明）
        public static final int POPUP_BG = rgba(44, 44, 50, 225);            // 弹出窗口背景（更透明）
        
        // 文本色
        public static final int TEXT = rgba(236, 236, 240, 255);             // 主文本
        public static final int TEXT_DISABLED = rgba(142, 142, 152, 255);    // 禁用文本
        public static final int TEXT_SELECTED = rgba(255, 255, 255, 255);    // 选中文本
        public static final int TEXT_WARNING = rgba(255, 200, 0, 255);        // 警告文本（黄色）
        
        // 边框色（描边已关闭时仍供分隔线等使用，略弱化）
        public static final int BORDER = rgba(72, 74, 82, 180);
        public static final int BORDER_SHADOW = rgba(0, 0, 0, 48);
        
        // 按钮色（与输入框底色区分，略偏冷灰）
        public static final int BUTTON = rgba(55, 58, 66, 255);
        public static final int BUTTON_HOVERED = rgba(72, 76, 88, 255);
        public static final int BUTTON_ACTIVE = rgba(88, 94, 108, 255);
        
        // 标题栏
        public static final int TITLE_BG = rgba(30, 30, 34, 255);
        public static final int TITLE_BG_ACTIVE = rgba(40, 40, 46, 255);
        public static final int TITLE_BG_COLLAPSED = rgba(26, 26, 30, 255);

        /** 普通面板标题栏 */
        public static final int TITLE_BG_PANEL = rgba(72, 74, 82, 255);
        public static final int TITLE_BG_PANEL_ACTIVE = rgba(86, 88, 98, 255);
        public static final int TITLE_BG_PANEL_COLLAPSED = rgba(60, 62, 70, 255);
        
        // 输入框、滑块槽等 frame 背景（相对 CHILD_BG 明显更亮）
        public static final int FRAME_BG = rgba(48, 50, 58, 255);
        public static final int FRAME_BG_HOVERED = rgba(58, 60, 70, 255);
        public static final int FRAME_BG_ACTIVE = rgba(68, 70, 82, 255);
        
        // 滑块和进度条
        public static final int SLIDER_GRAB = rgba(100, 168, 255, 255);
        public static final int SLIDER_GRAB_ACTIVE = rgba(130, 196, 255, 255);
        
        // 复选框和单选按钮
        public static final int CHECK_MARK = rgba(100, 168, 255, 255);
        
        // 标签页
        public static final int TAB = rgba(42, 44, 52, 255);
        public static final int TAB_HOVERED = rgba(55, 58, 68, 255);
        public static final int TAB_ACTIVE = rgba(72, 130, 210, 255);
        public static final int TAB_UNFOCUSED = rgba(34, 36, 42, 255);
        
        // 滚动条
        public static final int SCROLLBAR_BG = rgba(20, 20, 22, 220);
        public static final int SCROLLBAR_GRAB = rgba(96, 98, 108, 255);
        public static final int SCROLLBAR_GRAB_HOVERED = rgba(118, 120, 132, 255);
        public static final int SCROLLBAR_GRAB_ACTIVE = rgba(138, 140, 154, 255);
        
        // 分割线
        public static final int SEPARATOR = rgba(62, 64, 72, 255);
        public static final int SEPARATOR_HOVERED = rgba(82, 84, 94, 255);
        public static final int SEPARATOR_ACTIVE = rgba(100, 102, 114, 255);
        
        // 表头
        public static final int HEADER = rgba(52, 54, 62, 255);
        public static final int HEADER_HOVERED = rgba(64, 66, 76, 255);
        public static final int HEADER_ACTIVE = rgba(78, 80, 92, 255);
        
        // 高亮色（选择、激活等）
        public static final int HIGHLIGHT = rgba(72, 130, 210, 130);
        public static final int HIGHLIGHT_HOVERED = rgba(100, 168, 255, 180);
        
        // 调整大小手柄
        public static final int RESIZE_GRIP = rgba(72, 130, 210, 70);
        public static final int RESIZE_GRIP_HOVERED = rgba(100, 168, 255, 160);
        public static final int RESIZE_GRIP_ACTIVE = rgba(130, 196, 255, 255);

        // 按钮特殊颜色
        public static final int BUTTON_DEBUG = rgba(51, 153, 204, 255);       // 调试按钮
        public static final int BUTTON_DEBUG_HOVERED = rgba(77, 179, 230, 255);
        public static final int BUTTON_DEBUG_ACTIVE = rgba(26, 128, 179, 255);
        public static final int BUTTON_DANGER = rgba(204, 51, 51, 255);       // 危险按钮（关闭）
        public static final int BUTTON_DANGER_HOVERED = rgba(255, 77, 77, 255);
        public static final int BUTTON_DANGER_ACTIVE = rgba(179, 26, 26, 255);

        // 渲染开关颜色
        public static final int RENDER_ENABLED = rgba(51, 204, 51, 255);      // 渲染启用（绿色）
        public static final int RENDER_ENABLED_HOVERED = rgba(77, 255, 77, 255);
    }
    
    // ==================== 间距规范 ====================
    
    /** 
     * 间距常量（像素）
     * 
     * <p>设计原则：
     * <ul>
     *   <li>元素之间的间距：6px（按钮之间、控件之间）</li>
     *   <li>按钮文字左右内边距：6px（等于元素间距，视觉统一）</li>
     *   <li>面板边距：8px（控件与面板边缘的距离）</li>
     * </ul>
     */
    public static class Spacing {
        public static final float TINY = 2.0f;          // 超小间距
        public static final float SMALL = 4.0f;         // 小间距（推荐用于元素之间）
        public static final float NORMAL = 8.0f;        // 标准间距
        public static final float MEDIUM = 12.0f;       // 中等间距
        public static final float LARGE = 16.0f;        // 大间距
        public static final float EXTRA_LARGE = 24.0f;  // 超大间距

        
        // 面板边距（控件与面板边缘的距离，比元素间距大）
        public static final float PANEL_PADDING = 8.0f;          // 面板内边距（12px）
        public static final float PANEL_SPACING = 8.0f;           // 面板之间的间距
        
        // 元素间距（推荐的统一间距）
        public static final float ELEMENT_SPACING = 4.0f;         // 元素之间的标准间距（水平和垂直）
        public static final float ELEMENT_SPACING_HORIZONTAL = 6.0f;  // 左右按钮之间
        public static final float ELEMENT_SPACING_VERTICAL = 6.0f;    // 上下元素之间
        
        // 按钮尺寸（18px字体 + 上下各3px padding = 24px总高度）
        public static final float BUTTON_HEIGHT = 24.0f;  // 保持24px高度
        public static final float BUTTON_PADDING_X = 6.0f;  // 按钮文字左右内边距，等于元素间距
        public static final float BUTTON_PADDING_Y = 3.0f;  // 3px边距，适配18px字体

        /** 纯图标或图标为主的紧凑按钮（工具栏图标模式、时间线播放键），减小内边距使字形在正方形容器内尽量占满 */
        public static final float ICON_BUTTON_PADDING_X = 2.0f;
        public static final float ICON_BUTTON_PADDING_Y = 2.0f;
        
        // 输入框宽度（常用尺寸）
        public static final float INPUT_WIDTH_SMALL = 70.0f;    // 小输入框（如时间输入）
        public static final float INPUT_WIDTH_MEDIUM = 100.0f; // 中等输入框（如时长输入）
        public static final float INPUT_WIDTH_LARGE = 200.0f;  // 大输入框（如名称输入）
        public static final float INPUT_WIDTH_AUTO = 0.0f;     // 自动宽度
        
        // 标题栏
        public static final float TITLE_HEIGHT = 28.0f;
        
        // 分组
        public static final float GROUP_PADDING = 8.0f;
        public static final float GROUP_SPACING = 12.0f;
        
        // ========== 时间线轨道布局 ==========
        /**
         * 时间线轨道高度（像素）
         * 
         * <p>每条轨道的高度，用于渲染时间线面板中的轨道。
         * 设置为22px，保持紧凑的视觉效果。
         */
        public static final float TIMELINE_TRACK_HEIGHT = 22.0f;
        
        /**
         * 时间线轨道间距（像素）
         * 
         * <p>相邻轨道之间的间距，用于分隔不同的轨道。
         * 设置为3px，保持紧凑而清晰的视觉效果。
         */
        public static final float TIMELINE_TRACK_GAP = 2.0f;
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
    
    /** 
     * 字体大小规范
     * 
     * <p>统一的字体大小配置，用于ImGuiFontManager和UI组件
     */
    public static class FontSize {
        // 基础字体大小
        public static final float DEFAULT = 16.0f;      // 默认字体大小（中英文）
        public static final float ICON = 16.0f;         // 图标字体大小
        
        // 分级字体大小
        public static final float TINY = 12.0f;
        public static final float SMALL = 14.0f;
        public static final float NORMAL = 18.0f;        // 与DEFAULT相同
        public static final float MEDIUM = 20.0f;
        public static final float LARGE = 22.0f;
        public static final float EXTRA_LARGE = 26.0f;
        public static final float TITLE = 20.0f;         // 标题字体稍大
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
        // 1. 面板边距：12px（控件与面板边缘的距离）
        style.setWindowPadding(Spacing.PANEL_PADDING, Spacing.PANEL_PADDING);
        
        // 2. 按钮内边距：18px字体 + 上下各3px = 24px按钮高度，左右各6px = 元素间距
        style.setFramePadding(Spacing.BUTTON_PADDING_X, Spacing.BUTTON_PADDING_Y);  // (6px, 3px)
        style.setButtonTextAlign(0.5f, 0.5f);

        // 3. 元素间距：统一使用6px（上下按钮之间、左右按钮之间都是6px）
        style.setItemSpacing(Spacing.ELEMENT_SPACING, Spacing.ELEMENT_SPACING);  // (6px, 6px)
        
        // 4. 元素内部间距：4px（如复选框的框与文字之间）
        style.setItemInnerSpacing(Spacing.SMALL, Spacing.SMALL);  // (4px, 4px)
        
        // 5. 缩进间距：16px
        style.setIndentSpacing(Spacing.LARGE);
        
        // 圆角
        style.setWindowRounding(Rounding.NORMAL);
        style.setChildRounding(Rounding.SMALL);
        style.setFrameRounding(Rounding.SMALL);
        style.setPopupRounding(Rounding.NORMAL);
        style.setScrollbarRounding(Rounding.NORMAL);
        style.setGrabRounding(Rounding.SMALL);
        style.setTabRounding(Rounding.SMALL);
        
        // 无边框描边；层次靠底色与圆角区分
        style.setWindowBorderSize(0.0f);
        style.setChildBorderSize(0.0f);
        style.setPopupBorderSize(0.0f);
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
     * 添加标准元素间距（推荐使用）
     * 
     * <p>用于元素之间（按钮与按钮、控件与控件）的标准间距：4px
     */
    public static void spacing() {
        spacing(Spacing.ELEMENT_SPACING);  // 4px，统一的元素间距
    }
    
    /**
     * 添加小间距（2px）
     * 
     * <p>用于非常紧凑的布局
     */
    public static void spacingSmall() {
        spacing(Spacing.TINY);  // 2px
    }
    
    /**
     * 添加中等间距（8px）
     * 
     * <p>用于分组之间、段落之间的间距
     */
    public static void spacingMedium() {
        spacing(Spacing.NORMAL);  // 8px
    }
    
    /**
     * 添加大间距（12px）
     * 
     * <p>用于大的分组或区域之间的间距，与面板边距相同
     */
    public static void spacingLarge() {
        spacing(Spacing.MEDIUM);  // 12px，与PANEL_PADDING一致
    }

    /**
     * 分隔符（加强版）
     * 
     * <p>分隔符上下各留4px间距，与元素间距保持一致
     */
    public static void separator() {
        //spacing(Spacing.ELEMENT_SPACING);  // 上方间距4px
        ImGui.separator();
        //spacing(Spacing.ELEMENT_SPACING);  // 下方间距4px
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