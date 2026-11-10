package com.director.ui.properties;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

/** A flat disclosure header whose stable ID scopes ImGui state by object type and section. */
public final class Section {
    private Section() {}

    public static boolean header(String id, String title) {
        boolean open = ImGui.treeNodeEx(title + "###" + id,
                ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.NoTreePushOnOpen
                        | ImGuiTreeNodeFlags.SpanAvailWidth);
        var min = ImGui.getItemRectMin();
        var max = ImGui.getItemRectMax();
        float lineStart = min.x + ImGui.getTreeNodeToLabelSpacing()
                + ImGui.calcTextSize(title).x + ImGui.getStyle().getItemSpacing().x;
        float y = (min.y + max.y) * 0.5f;
        if (lineStart < max.x) {
            ImGui.getWindowDrawList().addLine(lineStart, y, max.x, y, ImGui.getColorU32(ImGuiCol.Separator));
        }
        return open;
    }
}
