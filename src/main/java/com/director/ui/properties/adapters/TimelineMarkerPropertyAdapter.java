package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineMarker;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TimelineMarkerPropertyAdapter - 标记属性适配器
 * 
 * <p>为时间线上的标记（TimelineMarker）提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>时间位置（标记在时间线上的位置）</li>
 *   <li>标记名称</li>
 *   <li>标记颜色（可编辑）</li>
 *   <li>标记描述/注释</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public class TimelineMarkerPropertyAdapter implements IPropertyAdapter<TimelineMarker> {
    
    private static final String TITLE = Icons.TAG + " 标记";
    
    // ImGui 临时对象
    private final ImFloat timeFloat = new ImFloat();
    private final ImString nameString = new ImString("", 256);
    private final ImString noteString = new ImString("", 512);
    private final float[] colorArr = new float[3];
    
    @Override
    public boolean renderProperties(TimelineMarker marker) {
        if (marker == null) {
            UITheme.label("（没有选中标记）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        AtomicBoolean modified = new AtomicBoolean(false);
        
        // 标题
        UITheme.title(TITLE);
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            // 时间位置
            timeFloat.set(marker.getTime());
            if (ImGui.dragFloat("时间位置", timeFloat.getData(), 0.02f, 0.0f, 10_000f, "%.2fs")) {
                marker.setTime(timeFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("标记在时间线上的位置（秒）");
                ImGui.endTooltip();
            }
            
            UITheme.spacingSmall();
            
            // 标记名称
            nameString.set(marker.getName());
            if (ImGui.inputText("标记名称", nameString)) {
                marker.setName(nameString.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("标记的显示名称，用于标识和查找");
                ImGui.endTooltip();
            }
            
            UITheme.spacingSmall();
            ImGui.unindent();
        }
        
        // ========== 外观设置 ==========
        if (ImGui.collapsingHeader(Icons.PALETTE + " 外观设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            // 预设颜色色卡
            ImGui.text("预设颜色：");
            UITheme.spacingSmall();
            
            // ⭐ 定义预设颜色（ARGB 格式，0xAARRGGBB）
            int[] presetColors = {
                0xFFFF0000, // 红色
                0xFFFF8000, // 橙色
                0xFFFFFF00, // 黄色
                0xFF00FF00, // 绿色
                0xFF00FFFF, // 青色
                0xFF0000FF, // 蓝色
                0xFFFF00FF, // 品红
                0xFFFFFFFF, // 白色
                0xFF808080, // 灰色
                0xFF000000, // 黑色（用深灰替代，纯黑可能看不清）
                0xFFFF6B6B, // 浅红
                0xFF4ECDC4, // 青绿
            };
            
            String[] presetColorNames = {
                "红色", "橙色", "黄色", "绿色", 
                "青色", "蓝色", "品红", "白色",
                "灰色", "深灰", "浅红", "青绿"
            };
            
            // 显示预设颜色按钮（每行4个）
            ImGui.beginGroup();
            for (int i = 0; i < presetColors.length; i++) {
                if (i > 0 && i % 4 == 0) {
                    ImGui.newLine();
                }
                
                // 将预设颜色转换为 RGB float 数组（用于 colorButton）
                int presetColor = presetColors[i];
                float[] presetRGB = new float[]{
                    ((presetColor >> 16) & 0xFF) / 255.0f,  // R
                    ((presetColor >> 8) & 0xFF) / 255.0f,   // G
                    (presetColor & 0xFF) / 255.0f           // B
                };
                
                ImGui.pushID("preset_" + i);
                // 使用 colorButton 显示预设颜色（使用 float[] 数组）
                if (ImGui.colorButton(presetColorNames[i], presetRGB, ImGuiColorEditFlags.NoAlpha, 24, 24)) {
                    // 点击预设颜色，直接设置为标记颜色（ARGB 格式）
                    marker.setColor(presetColor);
                    // 同步更新 colorArr，以便颜色选择器显示正确的值
                    colorArr[0] = presetRGB[0];
                    colorArr[1] = presetRGB[1];
                    colorArr[2] = presetRGB[2];
                    modified.set(true);
                }
                
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(presetColorNames[i]);
                }
                
                ImGui.popID();
                
                if (i < presetColors.length - 1 && (i + 1) % 4 != 0) {
                    ImGui.sameLine();
                }
            }
            ImGui.endGroup();
            
            UITheme.spacingSmall();
            UITheme.separator();
            UITheme.spacingSmall();
            
            // 自定义颜色选择器
            ImGui.text("自定义颜色：");
            UITheme.spacingSmall();
            
            // 标记颜色
            int color = marker.getColor();
            // 将 ARGB 转换为 RGB float 数组
            colorArr[0] = ((color >> 16) & 0xFF) / 255.0f;  // R
            colorArr[1] = ((color >> 8) & 0xFF) / 255.0f;   // G
            colorArr[2] = (color & 0xFF) / 255.0f;          // B
            
            int flags = ImGuiColorEditFlags.DisplayRGB 
                      | ImGuiColorEditFlags.InputRGB 
                      | ImGuiColorEditFlags.NoAlpha;
            
            if (ImGui.colorEdit3("标记颜色", colorArr, flags)) {
                // ⭐ 修复：将 RGB float 数组转换回 ARGB int（正确的通道顺序：R, G, B）
                int r = (int)(colorArr[0] * 255.0f);
                int g = (int)(colorArr[1] * 255.0f);
                int b = (int)(colorArr[2] * 255.0f);
                // ARGB 格式：A(31-24) R(23-16) G(15-8) B(7-0)
                int newColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                marker.setColor(newColor);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("标记在时间线上的显示颜色");
                ImGui.endTooltip();
            }
            
            UITheme.spacingSmall();
            ImGui.unindent();
        }
        
        // ========== 描述/注释 ==========
        if (ImGui.collapsingHeader(Icons.INFO + " 描述/注释")) {
            ImGui.indent();
            String currentNote = marker.getNote();
            if (currentNote == null) {
                currentNote = "";
            }
            noteString.set(currentNote);
            
            if (ImGui.inputTextMultiline("描述", noteString, 512, 100)) {
                marker.setNote(noteString.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("标记的详细描述或注释，用于记录分镜信息");
                ImGui.endTooltip();
            }
            
            UITheme.spacingSmall();
            ImGui.unindent();
        }
        
        return modified.get();
    }
    
    @Override
    public Class<TimelineMarker> getTargetType() {
        return TimelineMarker.class;
    }
}
