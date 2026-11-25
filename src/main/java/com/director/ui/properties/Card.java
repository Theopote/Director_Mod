package com.director.ui.properties;

import com.director.ui.theme.UITheme;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.HashMap;
import java.util.Map;

/**
 * 属性面板的分组卡片：与卡片等宽的标题条 + 略亮的圆角底色块 + 弱描边。
 *
 * <p>用法与 {@link Section#header} 对应，但必须配对调用 {@link #end()}：
 * <pre>{@code
 * boolean open = Card.begin("id", "标题");
 * if (open) { ... 控件 ... }
 * Card.end();
 * }</pre>
 *
 * <p>展开时内容放在一个单列表格里：左右各留 {@link #PAD_X} 的边距，控件宽度（{@code -1}）、
 * 分割线、自动换行都以表格单元格为边界，不会超出卡片。卡片内的 {@code text}/{@code textDisabled}
 * 等不换行文本也会在单元格右边缘自动换行。
 *
 * <p>底色块在内容之前绘制，高度取自上一帧（展开/折叠的瞬间会有一帧滞后）。
 * 卡片与表格都不可嵌套；二级分组请继续使用 {@link Section#header}。
 */
public final class Card {
    private static final float PAD_X = UITheme.Spacing.NORMAL;
    private static final float PAD_Y = UITheme.Spacing.SMALL;
    private static final float GAP_AFTER = UITheme.Spacing.NORMAL;

    private static final Map<String, Float> LAST_HEIGHT = new HashMap<>();

    private static boolean active;
    private static boolean tableOpen;
    private static String currentId;
    private static float left;
    private static float top;
    private static float right;
    private static float headerHeight;
    private static boolean open;

    private Card() {}

    public static boolean begin(String id, String title) {
        return begin(id, title, true);
    }

    public static boolean begin(String id, String title, boolean defaultOpen) {
        if (active) {
            throw new IllegalStateException("Card 不可嵌套，请先调用 Card.end()");
        }
        active = true;
        tableOpen = false;
        currentId = id;
        left = ImGui.getCursorScreenPosX();
        top = ImGui.getCursorScreenPosY();
        right = left + ImGui.getContentRegionAvailX();
        headerHeight = ImGui.getFrameHeight();

        float lastHeight = LAST_HEIGHT.getOrDefault(id, headerHeight);
        boolean wasOpen = lastHeight > headerHeight + 0.5f;
        float rounding = UITheme.Rounding.SMALL;
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(left, top, right, top + lastHeight, UITheme.CARD_BG, rounding);
        drawList.addRectFilled(left, top, right, top + headerHeight, UITheme.CARD_HEADER_BG, rounding,
                wasOpen ? ImDrawFlags.RoundCornersTop : ImDrawFlags.RoundCornersAll);

        // 标题条底色由卡片自己画，这里只保留悬停/按下的微弱高亮
        ImGui.pushStyleColor(ImGuiCol.Header, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 1f, 1f, 1f, 0.07f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 1f, 1f, 1f, 0.12f);
        int flags = ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth
                | ImGuiTreeNodeFlags.FramePadding;
        if (defaultOpen) {
            flags |= ImGuiTreeNodeFlags.DefaultOpen;
        }
        open = ImGui.treeNodeEx(title + "###" + id, flags);
        ImGui.popStyleColor(3);

        if (!open) {
            return false;
        }
        UITheme.spacing(UITheme.Spacing.TINY);
        ImGui.indent(PAD_X);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0f, 0f);
        float width = Math.max(1f, right - left - PAD_X * 2f);
        tableOpen = ImGui.beginTable("##card_" + id, 1,
                ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoSavedSettings, width, 0f);
        if (tableOpen) {
            ImGui.tableSetupColumn("##content", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.pushTextWrapPos(0f);
        }
        return tableOpen;
    }

    public static void end() {
        if (!active) {
            return;
        }
        if (tableOpen) {
            ImGui.popTextWrapPos();
            ImGui.endTable();
        }
        if (open) {
            ImGui.popStyleVar();
            ImGui.unindent(PAD_X);
            UITheme.spacing(PAD_Y);
        }
        float bottom = ImGui.getCursorScreenPosY() - ImGui.getStyle().getItemSpacing().y;
        LAST_HEIGHT.put(currentId, bottom - top);
        ImGui.getWindowDrawList().addRect(left, top, right, bottom, UITheme.CARD_BORDER, UITheme.Rounding.SMALL);

        active = false;
        UITheme.spacing(GAP_AFTER - ImGui.getStyle().getItemSpacing().y);
    }
}
