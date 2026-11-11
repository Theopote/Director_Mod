package com.director.ui.properties;

import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

/** Three independently editable axes. Ctrl-click a field to type a value. */
public final class Vec3Field {
    private static final String[] AXES = {"X", "Y", "Z"};
    // Draw-list colors use ImGui's packed ABGR order.
    private static final int[] AXIS_COLORS = {0xFF6666FF, 0xFF69CC70, 0xFFFFA864};

    private Vec3Field() {}

    public static Vec3d drag(String id, Vec3d value, float speed) {
        double[] components = {value.x, value.y, value.z};
        boolean changed = dragComponents(id, components, speed, "%.2f");
        return changed ? new Vec3d(components[0], components[1], components[2]) : value;
    }

    /** Edits a numeric draft without committing it to an animation asset. */
    public static boolean drag(String id, float[] value, float speed, String format) {
        if (value == null || value.length != 3) {
            throw new IllegalArgumentException("A vector requires exactly three components");
        }
        double[] components = {value[0], value[1], value[2]};
        boolean changed = dragComponents(id, components, speed, format);
        if (changed) {
            for (int i = 0; i < value.length; i++) {
                value[i] = (float) components[i];
            }
        }
        return changed;
    }

    private static boolean dragComponents(String id, double[] components, float speed, String format) {
        float spacing = ImGui.getStyle().getItemInnerSpacing().x;
        float width = Math.max(1f, (ImGui.getContentRegionAvailX() - 2 * spacing) / 3f);
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
                if (ImGui.dragFloat("##" + AXES[i], axis, speed, 0f, 0f, AXES[i] + " " + format)) {
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
        return changed;
    }
}
