package com.director.ui.properties;

import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/** Places the next value widget beside a clipped, frame-aligned label. */
public final class PropertyRow {
    private static final float LABEL_WIDTH = 72f;

    private PropertyRow() {}

    public static void label(String label) {
        float width = Math.min(LABEL_WIDTH, ImGui.getContentRegionAvailX() * 0.35f);
        if (width < 1f) {
            return;
        }

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float height = ImGui.getFrameHeight();
        boolean truncated = ImGui.calcTextSize(label).x > width;
        ImGui.dummy(width, height);
        var drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(x, y, x + width, y + height, true);
        String display = truncated ? ellipsize(label, width) : label;
        drawList.addText(x, y + ImGui.getStyle().getFramePadding().y, ImGui.getColorU32(ImGuiCol.Text), display);
        drawList.popClipRect();

        if (truncated && ImGui.isItemHovered()) {
            ImGui.setTooltip(label);
        }
        ImGui.sameLine(0, UITheme.Spacing.SMALL);
    }

    public static void field(String label) {
        label(label);
        ImGui.setNextItemWidth(-1f);
    }

    private static String ellipsize(String label, float width) {
        int end = label.length();
        while (end > 0) {
            end = label.offsetByCodePoints(end, -1);
            String shortened = label.substring(0, end) + "...";
            if (ImGui.calcTextSize(shortened).x <= width) {
                return shortened;
            }
        }
        return "...";
    }
}
