package com.director.ui.container;

import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;

/**
 * 容器颜色选择器组件。
 */
public class ContainerColorPicker {

    private static final int[] PRESET_COLORS = {
        0xFFFFFFFF,
        0xFFFF0000,
        0xFF00FF00,
        0xFF0000FF,
        0xFFFFFF00,
        0xFFFF00FF,
        0xFF00FFFF,
        0xFFFF8000,
    };

    private static final String[] PRESET_COLOR_NAMES = {
        "白色", "红色", "绿色", "蓝色", "黄色", "品红", "青色", "橙色"
    };

    /**
     * 渲染单个颜色选择器，包含预设色和自定义选择。
     */
    public static boolean renderColorPicker(String label, float[] colorBuffer) {
        boolean changed = false;

        ImGui.beginGroup();
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (i > 0 && i % 4 == 0) {
                ImGui.newLine();
            }

            int presetColor = PRESET_COLORS[i];
            float[] presetRgba = new float[]{
                ((presetColor >> 16) & 0xFF) / 255.0f,
                ((presetColor >> 8) & 0xFF) / 255.0f,
                (presetColor & 0xFF) / 255.0f,
                ((presetColor >> 24) & 0xFF) / 255.0f
            };

            ImGui.pushID(label + "_preset_" + i);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,
                presetRgba[0], presetRgba[1], presetRgba[2], 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered,
                presetRgba[0] * 0.8f, presetRgba[1] * 0.8f, presetRgba[2] * 0.8f, 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,
                presetRgba[0] * 0.6f, presetRgba[1] * 0.6f, presetRgba[2] * 0.6f, 1.0f);

            if (ImGui.button("", 20, 20)) {
                System.arraycopy(presetRgba, 0, colorBuffer, 0, 4);
                changed = true;
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PRESET_COLOR_NAMES[i]);
            }

            ImGui.popStyleColor(3);
            ImGui.popID();

            ImGui.sameLine();
        }
        ImGui.endGroup();

        ImGui.spacing();

        if (ImGui.colorEdit4("自定义#" + label, colorBuffer, ImGuiColorEditFlags.NoAlpha)) {
            changed = true;
        }

        return changed;
    }

    public static float[] colorIntToFloat(int color) {
        return new float[]{
            ((color >> 16) & 0xFF) / 255.0f,
            ((color >> 8) & 0xFF) / 255.0f,
            (color & 0xFF) / 255.0f,
            ((color >> 24) & 0xFF) / 255.0f
        };
    }

    public static int colorFloatToInt(float[] color) {
        return ((int) (color[3] * 255) << 24)
            | ((int) (color[0] * 255) << 16)
            | ((int) (color[1] * 255) << 8)
            | (int) (color[2] * 255);
    }
}
