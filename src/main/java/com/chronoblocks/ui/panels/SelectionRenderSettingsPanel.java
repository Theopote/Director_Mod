package com.chronoblocks.ui.panels;

import com.chronoblocks.client.renderer.SelectionRenderEvents;
import com.chronoblocks.client.renderer.SelectionRenderer;
import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 选择渲染设置面板
 * 
 * 提供选择渲染效果的详细配置选项，包括：
 * - 颜色设置
 * - 透明度调节
 * - 轮廓线宽度
 * - 动画效果开关
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionRenderSettingsPanel extends AbstractPanel {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionRenderSettingsPanel.class);
    
    /** 选择渲染器 */
    private final SelectionRenderer selectionRenderer;
    
    /** 工具预览管理器 */
    private final ToolPreviewManager toolPreviewManager;
    
    /** 颜色编辑状态 */
    private float[] selectionColor = {0.0f, 1.0f, 0.0f, 0.3f}; // 绿色半透明
    private float[] outlineColor = {0.0f, 1.0f, 0.0f, 0.8f}; // 绿色轮廓
    private float[] previewColor = {1.0f, 1.0f, 0.0f, 0.3f}; // 黄色半透明
    private float[] previewOutlineColor = {1.0f, 1.0f, 0.0f, 0.8f}; // 黄色轮廓
    
    /** 其他设置 */
    private float outlineWidth = 2.0f;
    private boolean showSelectionCount = true;
    private boolean enableAnimations = true;
    private boolean enablePreview = true;
    private float brushPreviewRadius = 3.0f;
    
    public SelectionRenderSettingsPanel() {
        this.selectionRenderer = SelectionRenderEvents.getSelectionRenderer();
        this.toolPreviewManager = SelectionRenderEvents.getToolPreviewManager();
        
        // 从渲染器获取当前设置
        updateFromRenderer();
        
        LOGGER.info("[SelectionRenderSettingsPanel] 初始化选择渲染设置面板");
    }
    
    @Override
    public String getPanelName() {
        return "SelectionRenderSettings";
    }
    
    @Override
    public String getWindowName() {
        return "选择渲染设置###SelectionRenderSettingsPanel";
    }
    
    @Override
    public int getPriority() {
        return 15;
    }
    
    @Override
    protected int getWindowFlags() {
        return imgui.flag.ImGuiWindowFlags.NoCollapse;
    }
    
    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }
    
    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.SETTINGS + " 选择渲染设置");
        
        // 渲染开关
        renderRenderToggles();
        ImGui.separator();
        
        // 颜色设置
        renderColorSettings();
        ImGui.separator();
        
        // 预览设置
        renderPreviewSettings();
        ImGui.separator();
        
        // 其他设置
        renderOtherSettings();
        ImGui.separator();
        
        // 重置按钮
        renderResetButton();
    }
    
    /**
     * 渲染渲染开关
     */
    private void renderRenderToggles() {
        UITheme.label("渲染开关:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 选择渲染开关
        boolean selectionEnabled = selectionRenderer.isEnabled();
        if (ImGui.checkbox("启用选择渲染", selectionEnabled)) {
            selectionRenderer.setEnabled(!selectionEnabled);
        }
        
        // 预览渲染开关
        boolean previewEnabled = toolPreviewManager.isEnabled();
        if (ImGui.checkbox("启用预览渲染", previewEnabled)) {
            toolPreviewManager.setEnabled(!previewEnabled);
        }
        
        // 选择数量显示开关
        if (ImGui.checkbox("显示选择数量", showSelectionCount)) {
            showSelectionCount = !showSelectionCount;
            selectionRenderer.setShowSelectionCount(showSelectionCount);
        }
        
        // 动画效果开关
        if (ImGui.checkbox("启用动画效果", enableAnimations)) {
            enableAnimations = !enableAnimations;
            selectionRenderer.setEnableAnimations(enableAnimations);
        }
    }
    
    /**
     * 渲染颜色设置
     */
    private void renderColorSettings() {
        UITheme.label("颜色设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 选择高亮颜色
        UITheme.label("选择高亮颜色:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        if (ImGui.colorEdit4("##selectionColor", selectionColor)) {
            selectionRenderer.setSelectionColor(selectionColor);
        }
        
        // 选择轮廓颜色
        UITheme.label("选择轮廓颜色:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        if (ImGui.colorEdit4("##outlineColor", outlineColor)) {
            selectionRenderer.setOutlineColor(outlineColor);
        }
        
        // 预览高亮颜色
        UITheme.label("预览高亮颜色:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        if (ImGui.colorEdit4("##previewColor", previewColor)) {
            toolPreviewManager.setPreviewColor(previewColor);
        }
        
        // 预览轮廓颜色
        UITheme.label("预览轮廓颜色:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        if (ImGui.colorEdit4("##previewOutlineColor", previewOutlineColor)) {
            toolPreviewManager.setPreviewOutlineColor(previewOutlineColor);
        }
    }
    
    /**
     * 渲染预览设置
     */
    private void renderPreviewSettings() {
        UITheme.label("预览设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 笔刷预览半径
        UITheme.label("笔刷预览半径:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        float[] radiusArray = {brushPreviewRadius};
        if (ImGui.sliderFloat("##brushPreviewRadius", radiusArray, 1.0f, 10.0f, "%.1f")) {
            brushPreviewRadius = radiusArray[0];
            toolPreviewManager.setBrushPreviewRadius(brushPreviewRadius);
        }
        
        // 预览开关
        if (ImGui.checkbox("启用预览效果", enablePreview)) {
            enablePreview = !enablePreview;
            // 这里可以控制预览的启用/禁用
        }
    }
    
    /**
     * 渲染其他设置
     */
    private void renderOtherSettings() {
        UITheme.label("其他设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 轮廓线宽度
        UITheme.label("轮廓线宽度:", UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        float[] widthArray = {outlineWidth};
        if (ImGui.sliderFloat("##outlineWidth", widthArray, 1.0f, 5.0f, "%.1f")) {
            outlineWidth = widthArray[0];
            selectionRenderer.setOutlineWidth(outlineWidth);
        }
        
        // 显示当前预览状态
        if (toolPreviewManager.isDragging()) {
            UITheme.label("预览状态: 拖拽中", UITheme.DarkTheme.TEXT_SELECTED);
            UITheme.label("预览方块数量: " + toolPreviewManager.getPreviewBlockCount(), UITheme.DarkTheme.TEXT);
        } else {
            UITheme.label("预览状态: 空闲", UITheme.DarkTheme.TEXT);
        }
    }
    
    /**
     * 渲染重置按钮
     */
    private void renderResetButton() {
        UITheme.label("操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("重置为默认设置##reset")) {
            resetToDefaults();
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重置所有设置为默认值");
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("应用设置##apply")) {
            applySettings();
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("应用当前设置到渲染器");
        }
    }
    
    /**
     * 从渲染器更新设置
     */
    private void updateFromRenderer() {
        try {
            selectionColor = selectionRenderer.getSelectionColor();
            outlineColor = selectionRenderer.getOutlineColor();
            previewColor = toolPreviewManager.getPreviewColor();
            previewOutlineColor = toolPreviewManager.getPreviewOutlineColor();
            outlineWidth = selectionRenderer.getOutlineWidth();
            showSelectionCount = selectionRenderer.isShowSelectionCount();
            enableAnimations = selectionRenderer.isEnableAnimations();
            brushPreviewRadius = toolPreviewManager.getBrushPreviewRadius();
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderSettingsPanel] 从渲染器更新设置失败", e);
        }
    }
    
    /**
     * 重置为默认设置
     */
    private void resetToDefaults() {
        try {
            // 重置颜色
            selectionColor = new float[]{0.0f, 1.0f, 0.0f, 0.3f};
            outlineColor = new float[]{0.0f, 1.0f, 0.0f, 0.8f};
            previewColor = new float[]{1.0f, 1.0f, 0.0f, 0.3f};
            previewOutlineColor = new float[]{1.0f, 1.0f, 0.0f, 0.8f};
            
            // 重置其他设置
            outlineWidth = 2.0f;
            showSelectionCount = true;
            enableAnimations = true;
            enablePreview = true;
            brushPreviewRadius = 3.0f;
            
            // 应用设置
            applySettings();
            
            LOGGER.info("[SelectionRenderSettingsPanel] 设置已重置为默认值");
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderSettingsPanel] 重置设置失败", e);
        }
    }
    
    /**
     * 应用设置到渲染器
     */
    private void applySettings() {
        try {
            // 应用颜色设置
            selectionRenderer.setSelectionColor(selectionColor);
            selectionRenderer.setOutlineColor(outlineColor);
            toolPreviewManager.setPreviewColor(previewColor);
            toolPreviewManager.setPreviewOutlineColor(previewOutlineColor);
            
            // 应用其他设置
            selectionRenderer.setOutlineWidth(outlineWidth);
            selectionRenderer.setShowSelectionCount(showSelectionCount);
            selectionRenderer.setEnableAnimations(enableAnimations);
            toolPreviewManager.setBrushPreviewRadius(brushPreviewRadius);
            
            LOGGER.info("[SelectionRenderSettingsPanel] 设置已应用到渲染器");
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderSettingsPanel] 应用设置失败", e);
        }
    }
}
