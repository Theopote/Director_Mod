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

        clippedText(label, width);
        ImGui.sameLine(0, UITheme.Spacing.SMALL);
    }

    private static void clippedText(String text, float width) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float height = ImGui.getFrameHeight();
        float textWidth = ImGui.calcTextSize(text).x;
        boolean truncated = textWidth > width;
        ImGui.dummy(width, height);
        var drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(x, y, x + width, y + height, true);
        String display = truncated ? ellipsize(text, width) : text;
        drawList.addText(x, y + ImGui.getStyle().getFramePadding().y, ImGui.getColorU32(ImGuiCol.Text), display);
        drawList.popClipRect();

        if (truncated && ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }

    public static void field(String label) {
        label(label);
        ImGui.setNextItemWidth(-1f);
    }

    public static void value(String label, String value) {
        label(label);
        clippedText(value, Math.max(1f, ImGui.getContentRegionAvailX()));
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
