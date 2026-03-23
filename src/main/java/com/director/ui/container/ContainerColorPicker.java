package com.director.ui.container;

import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;

/**
 * 容器颜色选择器组件
 * 提供8种预设颜色和自定义颜色选择功能
 */
public class ContainerColorPicker {
    
    // 8种预设颜色（ARGB格式，0xAARRGGBB）
    private static final int[] PRESET_COLORS = {
        0xFFFFFFFF, // 白色
        0xFFFF0000, // 红色
        0xFF00FF00, // 绿色
        0xFF0000FF, // 蓝色
        0xFFFFFF00, // 黄色
        0xFFFF00FF, // 品红
        0xFF00FFFF, // 青色
        0xFFFF8000, // 橙色
    };
    
    private static final String[] PRESET_COLOR_NAMES = {
        "白色", "红色", "绿色", "蓝色", "黄色", "品红", "青色", "橙色"
    };
    
    /**
     * 渲染颜色选择器（标题颜色和图标颜色）
     * @param titleColorBuffer 标题颜色缓冲区（4个float: RGBA）
     * @param iconColorBuffer 图标颜色缓冲区（4个float: RGBA）
     * @return 是否修改了颜色
     */
    public static boolean render(float[] titleColorBuffer, float[] iconColorBuffer) {
        boolean changed = false;
        
        ImGui.text("标题颜色:");
        ImGui.sameLine();
        if (renderColorPicker("标题颜色", titleColorBuffer)) {
            changed = true;
        }
        
        ImGui.spacing();
        ImGui.text("图标颜色:");
        ImGui.sameLine();
        if (renderColorPicker("图标颜色", iconColorBuffer)) {
            changed = true;
        }
        
        return changed;
    }
    
    /**
     * 渲染单个颜色选择器（包含预设和自定义）
     */
    public static boolean renderColorPicker(String label, float[] colorBuffer) {
        boolean changed = false;
        
        // 显示预设颜色按钮（8个）
        ImGui.beginGroup();
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (i > 0 && i % 4 == 0) {
                ImGui.newLine();
            }
            
            // 将预设颜色转换为 RGBA float 数组
            int presetColor = PRESET_COLORS[i];
            float[] presetRGBA = new float[]{
                ((presetColor >> 16) & 0xFF) / 255.0f,  // R
                ((presetColor >> 8) & 0xFF) / 255.0f,   // G
                (presetColor & 0xFF) / 255.0f,          // B
                ((presetColor >> 24) & 0xFF) / 255.0f   // A
            };
            
            // 创建颜色按钮
            ImGui.pushID(label + "_preset_" + i);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 
                presetRGBA[0], presetRGBA[1], presetRGBA[2], 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered,
                presetRGBA[0] * 0.8f, presetRGBA[1] * 0.8f, presetRGBA[2] * 0.8f, 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,
                presetRGBA[0] * 0.6f, presetRGBA[1] * 0.6f, presetRGBA[2] * 0.6f, 1.0f);
            
            if (ImGui.button("", 20, 20)) {
                // 点击预设颜色，更新颜色缓冲区
                System.arraycopy(presetRGBA, 0, colorBuffer, 0, 4);
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
        
        // 自定义颜色选择器
        if (ImGui.colorEdit4("自定义##" + label, colorBuffer, ImGuiColorEditFlags.NoAlpha)) {
            changed = true;
        }
        
        return changed;
    }
    
    /**
     * 将 ARGB int 颜色转换为 RGBA float 数组
     */
    public static float[] colorIntToFloat(int color) {
        return new float[]{
            ((color >> 16) & 0xFF) / 255.0f,  // R
            ((color >> 8) & 0xFF) / 255.0f,   // G
            (color & 0xFF) / 255.0f,          // B
            ((color >> 24) & 0xFF) / 255.0f   // A
        };
    }
    
    /**
     * 将 RGBA float 数组转换为 ARGB int 颜色
     */
    public static int colorFloatToInt(float[] color) {
        return ((int)(color[3] * 255) << 24) |  // A
               ((int)(color[0] * 255) << 16) |  // R
               ((int)(color[1] * 255) << 8) |   // G
               (int)(color[2] * 255);            // B
    }
}
