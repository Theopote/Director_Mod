package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 颜色属性适配器 - Director Properties System v4.5
 * 
 * <p>用于编辑颜色值 (RGBA)。
 * 内部使用 JOML {@link Vector4f} 存储颜色分量。
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>ColorEdit4 - RGBA 四通道编辑</li>
 *   <li>拾色器 - 可视化颜色选择</li>
 *   <li>Alpha 通道 - 支持透明度</li>
 *   <li>颜色预览 - 实时预览块</li>
 *   <li>快捷预设 - 常用颜色快速选择</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 自动注册（在 Bootstrap 中）
 * PropertyAdapterRegistry.registerAdapter(Vector4f.class, new ColorPropertyAdapter());
 * 
 * // 使用
 * Vector4f color = new Vector4f(1.0f, 0.5f, 0.0f, 1.0f); // 橙色
 * SelectionManager.get().setCurrentSelection(color);
 * 
 * // 属性面板自动显示拾色器
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class ColorPropertyAdapter implements IPropertyAdapter<Vector4f> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ColorPropertyAdapter.class);
    
    // 预设颜色
    private static final float[][] PRESETS = {
        {1.0f, 0.0f, 0.0f, 1.0f},  // 红色
        {0.0f, 1.0f, 0.0f, 1.0f},  // 绿色
        {0.0f, 0.0f, 1.0f, 1.0f},  // 蓝色
        {1.0f, 1.0f, 0.0f, 1.0f},  // 黄色
        {1.0f, 0.5f, 0.0f, 1.0f},  // 橙色
        {0.5f, 0.0f, 1.0f, 1.0f},  // 紫色
        {1.0f, 1.0f, 1.0f, 1.0f},  // 白色
        {0.0f, 0.0f, 0.0f, 1.0f},  // 黑色
    };
    
    private static final String[] PRESET_NAMES = {
        "红", "绿", "蓝", "黄", "橙", "紫", "白", "黑"
    };

    @Override
    public boolean renderProperties(Vector4f color) {
        if (color == null) {
            UITheme.spacingLarge();
            UITheme.label("颜色为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 标题
        UITheme.title("🎨 颜色属性");
        
        // 颜色编辑器
        UITheme.beginGroup("颜色编辑");
        
        float[] rgba = {color.x, color.y, color.z, color.w};
        
        // 带拾色器的颜色编辑器
        int flags = ImGuiColorEditFlags.AlphaBar 
                  | ImGuiColorEditFlags.AlphaPreview
                  | ImGuiColorEditFlags.DisplayRGB
                  | ImGuiColorEditFlags.InputRGB
                  | ImGuiColorEditFlags.PickerHueWheel;
        
        if (ImGui.colorEdit4("RGBA", rgba, flags)) {
            color.set(rgba[0], rgba[1], rgba[2], rgba[3]);
            modified = true;
            LOGGER.debug("[ColorPropertyAdapter] 颜色已更新: ({}, {}, {}, {})", 
                rgba[0], rgba[1], rgba[2], rgba[3]);
        }
        
        UITheme.spacingSmall();
        
        // 显示十六进制值
        int r = (int)(rgba[0] * 255);
        int g = (int)(rgba[1] * 255);
        int b = (int)(rgba[2] * 255);
        int a = (int)(rgba[3] * 255);
        String hexColor = String.format("#%02X%02X%02X%02X", r, g, b, a);
        
        UITheme.label("Hex: " + hexColor, UITheme.DarkTheme.TEXT_DISABLED);
        
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 颜色预设
        UITheme.beginGroup("快捷预设");
        
        for (int i = 0; i < PRESETS.length; i++) {
            float[] preset = PRESETS[i];
            
            // 显示预设颜色块
            ImGui.pushID(i);
            
            // colorButton 接受 float[] 数组
            if (ImGui.colorButton(PRESET_NAMES[i], preset, ImGuiColorEditFlags.NoAlpha, 30, 30)) {
                color.set(preset[0], preset[1], preset[2], preset[3]);
                modified = true;
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PRESET_NAMES[i]);
            }
            
            ImGui.popID();
            
            // 每 4 个换行
            if ((i + 1) % 4 != 0 && i < PRESETS.length - 1) {
                ImGui.sameLine();
            }
        }
        
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 快捷操作
        UITheme.beginGroup("快捷操作");
        
        if (ImGui.button("重置为白色", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            color.set(1, 1, 1, 1);
            modified = true;
        }
        ImGui.sameLine();
        
        if (ImGui.button("重置为黑色", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            color.set(0, 0, 0, 1);
            modified = true;
        }
        ImGui.sameLine();
        
        if (ImGui.button("完全透明", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            color.w = 0.0f;
            modified = true;
        }
        
        UITheme.endGroup();
        
        return modified;
    }

    @Override
    public Class<Vector4f> getTargetType() {
        return Vector4f.class;
    }
    
    @Override
    public String getAdapterName() {
        return "颜色属性";
    }
}

