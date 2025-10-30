package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🛠️ 工具属性面板
 * 
 * 提供当前选择工具的属性编辑功能：
 * - 画笔半径设置
 * - 工具提示开关
 * - 工具参数配置
 * - 工具模式切换
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ToolPropertiesPanel extends AbstractPanel {
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "ToolProperties";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPropertiesPanel.class);
    
    /** 工具属性 */
    private float brushRadius = 3.0f;
    private boolean enableTooltips = true;
    private boolean enableSnapping = false;
    private float snapDistance = 1.0f;
    private boolean enableGridSnap = false;
    private float gridSize = 1.0f;
    
    /** 工具模式 */
    private String currentToolMode = "click";
    private boolean enableMultiSelect = false;
    private boolean enableAutoComplete = true;
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.TOOLS + " 工具属性###ToolPropertiesPanel";
    }
    
    @Override
    public int getPriority() {
        return 35;
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.TOOLS + " 工具属性");
        
        // 工具模式选择
        renderToolModeSelection();
        ImGui.separator();
        
        // 画笔设置
        renderBrushSettings();
        ImGui.separator();
        
        // 吸附设置
        renderSnappingSettings();
        ImGui.separator();
        
        // 工具选项
        renderToolOptions();
        ImGui.separator();
        
        // 操作按钮
        renderActionButtons();
    }
    
    /**
     * 渲染工具模式选择
     */
    private void renderToolModeSelection() {
        UITheme.label("工具模式:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.radioButton("点击选择##clickMode", currentToolMode.equals("click"))) {
            currentToolMode = "click";
            LOGGER.debug("[ToolPropertiesPanel] 切换到点击选择模式");
        }
        
        ImGui.sameLine();
        
        if (ImGui.radioButton("框选##boxMode", currentToolMode.equals("box"))) {
            currentToolMode = "box";
            LOGGER.debug("[ToolPropertiesPanel] 切换到框选模式");
        }
        
        ImGui.sameLine();
        
        if (ImGui.radioButton("魔棒##wandMode", currentToolMode.equals("wand"))) {
            currentToolMode = "wand";
            LOGGER.debug("[ToolPropertiesPanel] 切换到魔棒模式");
        }
    }
    
    /**
     * 渲染画笔设置
     */
    private void renderBrushSettings() {
        UITheme.label("画笔设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 画笔半径
        UITheme.label("画笔半径:", UITheme.DarkTheme.TEXT);
        float[] radiusArray = {brushRadius};
        if (ImGui.sliderFloat("##brushRadius", radiusArray, 1.0f, 10.0f, "%.1f")) {
            brushRadius = radiusArray[0];
            LOGGER.debug("[ToolPropertiesPanel] 画笔半径: {}", brushRadius);
        }
        
        // 工具提示
        if (ImGui.checkbox("启用工具提示", enableTooltips)) {
            enableTooltips = !enableTooltips;
            LOGGER.debug("[ToolPropertiesPanel] 工具提示: {}", enableTooltips);
        }
    }
    
    /**
     * 渲染吸附设置
     */
    private void renderSnappingSettings() {
        UITheme.label("吸附设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("启用吸附", enableSnapping)) {
            enableSnapping = !enableSnapping;
            LOGGER.debug("[ToolPropertiesPanel] 吸附: {}", enableSnapping);
        }
        
        if (enableSnapping) {
            ImGui.indent();
            
            // 吸附距离
            UITheme.label("吸附距离:", UITheme.DarkTheme.TEXT);
            float[] snapArray = {snapDistance};
            if (ImGui.sliderFloat("##snapDistance", snapArray, 0.1f, 5.0f, "%.1f")) {
                snapDistance = snapArray[0];
                LOGGER.debug("[ToolPropertiesPanel] 吸附距离: {}", snapDistance);
            }
            
            // 网格吸附
            if (ImGui.checkbox("网格吸附", enableGridSnap)) {
                enableGridSnap = !enableGridSnap;
                LOGGER.debug("[ToolPropertiesPanel] 网格吸附: {}", enableGridSnap);
            }
            
            if (enableGridSnap) {
                UITheme.label("网格大小:", UITheme.DarkTheme.TEXT);
                float[] gridArray = {gridSize};
                if (ImGui.sliderFloat("##gridSize", gridArray, 0.5f, 4.0f, "%.1f")) {
                    gridSize = gridArray[0];
                    LOGGER.debug("[ToolPropertiesPanel] 网格大小: {}", gridSize);
                }
            }
            
            ImGui.unindent();
        }
    }
    
    /**
     * 渲染工具选项
     */
    private void renderToolOptions() {
        UITheme.label("工具选项:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("多选模式", enableMultiSelect)) {
            enableMultiSelect = !enableMultiSelect;
            LOGGER.debug("[ToolPropertiesPanel] 多选模式: {}", enableMultiSelect);
        }
        
        if (ImGui.checkbox("自动完成", enableAutoComplete)) {
            enableAutoComplete = !enableAutoComplete;
            LOGGER.debug("[ToolPropertiesPanel] 自动完成: {}", enableAutoComplete);
        }
    }
    
    /**
     * 渲染操作按钮
     */
    private void renderActionButtons() {
        UITheme.label("操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("重置为默认值##resetToolProperties")) {
            resetToDefaults();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("应用设置##applyToolProperties")) {
            applySettings();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("保存预设##saveToolPreset")) {
            savePreset();
        }
    }
    
    /**
     * 重置为默认值
     */
    private void resetToDefaults() {
        brushRadius = 3.0f;
        enableTooltips = true;
        enableSnapping = false;
        snapDistance = 1.0f;
        enableGridSnap = false;
        gridSize = 1.0f;
        currentToolMode = "click";
        enableMultiSelect = false;
        enableAutoComplete = true;
        LOGGER.info("[ToolPropertiesPanel] 重置为默认值");
    }
    
    /**
     * 应用设置
     */
    private void applySettings() {
        try {
            // 这里应该将设置应用到实际的工具管理器
            LOGGER.info("[ToolPropertiesPanel] 应用工具设置");
        } catch (Exception e) {
            LOGGER.error("[ToolPropertiesPanel] 应用设置失败", e);
        }
    }
    
    /**
     * 保存预设
     */
    private void savePreset() {
        try {
            // 这里应该保存当前设置为预设
            LOGGER.info("[ToolPropertiesPanel] 保存工具预设");
        } catch (Exception e) {
            LOGGER.error("[ToolPropertiesPanel] 保存预设失败", e);
        }
    }
    
    // ==================== Getter/Setter ====================
    
    /**
     * 获取画笔半径
     */
    public float getBrushRadius() {
        return brushRadius;
    }
    
    /**
     * 设置画笔半径
     */
    public void setBrushRadius(float radius) {
        this.brushRadius = radius;
    }
    
    /**
     * 是否启用工具提示
     */
    public boolean isTooltipsEnabled() {
        return enableTooltips;
    }
    
    /**
     * 是否启用吸附
     */
    public boolean isSnappingEnabled() {
        return enableSnapping;
    }
    
    /**
     * 获取吸附距离
     */
    public float getSnapDistance() {
        return snapDistance;
    }
    
    /**
     * 获取当前工具模式
     */
    public String getCurrentToolMode() {
        return currentToolMode;
    }
    
    /**
     * 设置当前工具模式
     */
    public void setCurrentToolMode(String mode) {
        this.currentToolMode = mode;
    }
}
