package com.director.ui.properties;

import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

/** Three independently editable axes. Ctrl-click a field to type a value. */
public final class Vec3Field {
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] FORMATS = {"X %.2f", "Y %.2f", "Z %.2f"};
    // Draw-list colors use ImGui's packed ABGR order.
    private static final int[] AXIS_COLORS = {0xFF6666FF, 0xFF69CC70, 0xFFFFA864};

    private Vec3Field() {}

    public static Vec3d drag(String id, Vec3d value, float speed) {
        float spacing = ImGui.getStyle().getItemInnerSpacing().x;
        float width = Math.max(1f, (ImGui.getContentRegionAvailX() - 2 * spacing) / 3f);
        double[] components = {value.x, value.y, value.z};
        float[] axis = new float[1];
        boolean changed = false;

        ImGui.beginGroup();
        ImGui.pushID(id);
        try {
            for (int i = 0; i < AXES.length; i++) {
                if (i > 0) {
                    ImGui.sameLine(0, spacing);
                }
                axis[0] = (float) components[i];
                ImGui.setNextItemWidth(width);
                if (ImGui.dragFloat("##" + AXES[i], axis, speed, 0f, 0f, FORMATS[i])) {
                    components[i] = axis[0];
                    changed = true;
                }
                var min = ImGui.getItemRectMin();
                var max = ImGui.getItemRectMax();
                ImGui.getWindowDrawList().addLine(min.x + 2f, min.y + 3f, min.x + 2f, max.y - 3f, AXIS_COLORS[i]);
            }
        } finally {
            ImGui.popID();
            ImGui.endGroup();
        }
        return changed ? new Vec3d(components[0], components[1], components[2]) : value;
    }
}
