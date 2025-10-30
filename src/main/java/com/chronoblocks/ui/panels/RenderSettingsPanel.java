package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎨 渲染设置面板
 * 
 * 提供选择渲染和可视调试功能：
 * - 高亮颜色设置
 * - 边框线配置
 * - 预览半径设置
 * - Debug Overlay
 * - 渲染性能设置
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class RenderSettingsPanel extends AbstractPanel {
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "RenderSettings";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderSettingsPanel.class);
    
    /** 高亮设置 */
    private float[] highlightColor = {1.0f, 0.0f, 0.0f, 0.5f}; // 红色，50%透明度
    private float highlightIntensity = 1.0f;
    private boolean enableHighlight = true;
    
    /** 边框线设置 */
    private float[] borderColor = {0.0f, 1.0f, 0.0f, 1.0f}; // 绿色，不透明
    private float borderWidth = 2.0f;
    private boolean enableBorder = true;
    private boolean enableBorderGlow = false;
    
    /** 预览设置 */
    private float previewRadius = 5.0f;
    private boolean enablePreview = true;
    private float[] previewColor = {0.0f, 0.0f, 1.0f, 0.3f}; // 蓝色，30%透明度
    
    /** Debug设置 */
    private boolean enableDebugOverlay = false;
    private boolean showBlockInfo = false;
    private boolean showSelectionBounds = false;
    private boolean showPerformanceStats = false;
    
    /** 渲染性能设置 */
    private int maxRenderDistance = 32;
    private boolean enableFrustumCulling = true;
    private boolean enableOcclusionCulling = false;
    private int renderQuality = 2; // 0=低, 1=中, 2=高
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.COLOR + " 渲染设置###RenderSettingsPanel";
    }
    
    @Override
    public int getPriority() {
        return 30;
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.COLOR + " 渲染设置");
        
        // 高亮设置
        renderHighlightSettings();
        ImGui.separator();
        
        // 边框线设置
        renderBorderSettings();
        ImGui.separator();
        
        // 预览设置
        renderPreviewSettings();
        ImGui.separator();
        
        // Debug设置
        renderDebugSettings();
        ImGui.separator();
        
        // 性能设置
        renderPerformanceSettings();
        ImGui.separator();
        
        // 操作按钮
        renderActionButtons();
    }
    
    /**
     * 渲染高亮设置
     */
    private void renderHighlightSettings() {
        UITheme.label("高亮设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("启用高亮", enableHighlight)) {
            enableHighlight = !enableHighlight;
            LOGGER.debug("[RenderSettingsPanel] 高亮: {}", enableHighlight);
        }
        
        if (enableHighlight) {
            ImGui.indent();
            
            // 高亮颜色
            UITheme.label("高亮颜色:", UITheme.DarkTheme.TEXT);
            if (ImGui.colorEdit4("##highlightColor", highlightColor)) {
                LOGGER.debug("[RenderSettingsPanel] 高亮颜色: R={}, G={}, B={}, A={}", 
                    highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }
            
            // 高亮强度
            UITheme.label("高亮强度:", UITheme.DarkTheme.TEXT);
            float[] intensityArray = {highlightIntensity};
            if (ImGui.sliderFloat("##highlightIntensity", intensityArray, 0.1f, 2.0f, "%.1f")) {
                highlightIntensity = intensityArray[0];
                LOGGER.debug("[RenderSettingsPanel] 高亮强度: {}", highlightIntensity);
            }
            
            ImGui.unindent();
        }
    }
    
    /**
     * 渲染边框线设置
     */
    private void renderBorderSettings() {
        UITheme.label("边框线设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("启用边框线", enableBorder)) {
            enableBorder = !enableBorder;
            LOGGER.debug("[RenderSettingsPanel] 边框线: {}", enableBorder);
        }
        
        if (enableBorder) {
            ImGui.indent();
            
            // 边框颜色
            UITheme.label("边框颜色:", UITheme.DarkTheme.TEXT);
            if (ImGui.colorEdit4("##borderColor", borderColor)) {
                LOGGER.debug("[RenderSettingsPanel] 边框颜色: R={}, G={}, B={}, A={}", 
                    borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
            }
            
            // 边框宽度
            UITheme.label("边框宽度:", UITheme.DarkTheme.TEXT);
            float[] widthArray = {borderWidth};
            if (ImGui.sliderFloat("##borderWidth", widthArray, 1.0f, 5.0f, "%.1f")) {
                borderWidth = widthArray[0];
                LOGGER.debug("[RenderSettingsPanel] 边框宽度: {}", borderWidth);
            }
            
            // 边框发光
            if (ImGui.checkbox("边框发光", enableBorderGlow)) {
                enableBorderGlow = !enableBorderGlow;
                LOGGER.debug("[RenderSettingsPanel] 边框发光: {}", enableBorderGlow);
            }
            
            ImGui.unindent();
        }
    }
    
    /**
     * 渲染预览设置
     */
    private void renderPreviewSettings() {
        UITheme.label("预览设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("启用预览", enablePreview)) {
            enablePreview = !enablePreview;
            LOGGER.debug("[RenderSettingsPanel] 预览: {}", enablePreview);
        }
        
        if (enablePreview) {
            ImGui.indent();
            
            // 预览半径
            UITheme.label("预览半径:", UITheme.DarkTheme.TEXT);
            float[] radiusArray = {previewRadius};
            if (ImGui.sliderFloat("##previewRadius", radiusArray, 1.0f, 20.0f, "%.1f")) {
                previewRadius = radiusArray[0];
                LOGGER.debug("[RenderSettingsPanel] 预览半径: {}", previewRadius);
            }
            
            // 预览颜色
            UITheme.label("预览颜色:", UITheme.DarkTheme.TEXT);
            if (ImGui.colorEdit4("##previewColor", previewColor)) {
                LOGGER.debug("[RenderSettingsPanel] 预览颜色: R={}, G={}, B={}, A={}", 
                    previewColor[0], previewColor[1], previewColor[2], previewColor[3]);
            }
            
            ImGui.unindent();
        }
    }
    
    /**
     * 渲染Debug设置
     */
    private void renderDebugSettings() {
        UITheme.label("Debug设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.checkbox("启用Debug覆盖层", enableDebugOverlay)) {
            enableDebugOverlay = !enableDebugOverlay;
            LOGGER.debug("[RenderSettingsPanel] Debug覆盖层: {}", enableDebugOverlay);
        }
        
        if (enableDebugOverlay) {
            ImGui.indent();
            
            if (ImGui.checkbox("显示方块信息", showBlockInfo)) {
                showBlockInfo = !showBlockInfo;
                LOGGER.debug("[RenderSettingsPanel] 方块信息: {}", showBlockInfo);
            }
            
            if (ImGui.checkbox("显示选择边界", showSelectionBounds)) {
                showSelectionBounds = !showSelectionBounds;
                LOGGER.debug("[RenderSettingsPanel] 选择边界: {}", showSelectionBounds);
            }
            
            if (ImGui.checkbox("显示性能统计", showPerformanceStats)) {
                showPerformanceStats = !showPerformanceStats;
                LOGGER.debug("[RenderSettingsPanel] 性能统计: {}", showPerformanceStats);
            }
            
            ImGui.unindent();
        }
    }
    
    /**
     * 渲染性能设置
     */
    private void renderPerformanceSettings() {
        UITheme.label("性能设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 渲染距离
        UITheme.label("最大渲染距离:", UITheme.DarkTheme.TEXT);
        int[] distanceArray = {maxRenderDistance};
        if (ImGui.sliderInt("##maxRenderDistance", distanceArray, 8, 128)) {
            maxRenderDistance = distanceArray[0];
            LOGGER.debug("[RenderSettingsPanel] 渲染距离: {}", maxRenderDistance);
        }
        
        // 视锥剔除
        if (ImGui.checkbox("视锥剔除", enableFrustumCulling)) {
            enableFrustumCulling = !enableFrustumCulling;
            LOGGER.debug("[RenderSettingsPanel] 视锥剔除: {}", enableFrustumCulling);
        }
        
        // 遮挡剔除
        if (ImGui.checkbox("遮挡剔除", enableOcclusionCulling)) {
            enableOcclusionCulling = !enableOcclusionCulling;
            LOGGER.debug("[RenderSettingsPanel] 遮挡剔除: {}", enableOcclusionCulling);
        }
        
        // 渲染质量
        UITheme.label("渲染质量:", UITheme.DarkTheme.TEXT);
        String[] qualityOptions = {"低", "中", "高"};
        imgui.type.ImInt qualityInt = new imgui.type.ImInt(renderQuality);
        if (ImGui.combo("##renderQuality", qualityInt, qualityOptions)) {
            renderQuality = qualityInt.get();
            LOGGER.debug("[RenderSettingsPanel] 渲染质量: {}", qualityOptions[renderQuality]);
        }
    }
    
    /**
     * 渲染操作按钮
     */
    private void renderActionButtons() {
        UITheme.label("操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("重置为默认值##resetRenderSettings")) {
            resetToDefaults();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("应用设置##applyRenderSettings")) {
            applySettings();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("保存预设##saveRenderPreset")) {
            savePreset();
        }
    }
    
    /**
     * 重置为默认值
     */
    private void resetToDefaults() {
        highlightColor = new float[]{1.0f, 0.0f, 0.0f, 0.5f};
        highlightIntensity = 1.0f;
        enableHighlight = true;
        borderColor = new float[]{0.0f, 1.0f, 0.0f, 1.0f};
        borderWidth = 2.0f;
        enableBorder = true;
        enableBorderGlow = false;
        previewRadius = 5.0f;
        enablePreview = true;
        previewColor = new float[]{0.0f, 0.0f, 1.0f, 0.3f};
        enableDebugOverlay = false;
        showBlockInfo = false;
        showSelectionBounds = false;
        showPerformanceStats = false;
        maxRenderDistance = 32;
        enableFrustumCulling = true;
        enableOcclusionCulling = false;
        renderQuality = 2;
        LOGGER.info("[RenderSettingsPanel] 重置为默认值");
    }
    
    /**
     * 应用设置
     */
    private void applySettings() {
        try {
            // 这里应该将设置应用到实际的渲染器
            LOGGER.info("[RenderSettingsPanel] 应用渲染设置");
        } catch (Exception e) {
            LOGGER.error("[RenderSettingsPanel] 应用设置失败", e);
        }
    }
    
    /**
     * 保存预设
     */
    private void savePreset() {
        try {
            // 这里应该保存当前设置为预设
            LOGGER.info("[RenderSettingsPanel] 保存渲染预设");
        } catch (Exception e) {
            LOGGER.error("[RenderSettingsPanel] 保存预设失败", e);
        }
    }
    
    // ==================== Getter/Setter ====================
    
    public float[] getHighlightColor() { return highlightColor; }
    public void setHighlightColor(float[] color) { this.highlightColor = color; }
    
    public float getHighlightIntensity() { return highlightIntensity; }
    public void setHighlightIntensity(float intensity) { this.highlightIntensity = intensity; }
    
    public boolean isHighlightEnabled() { return enableHighlight; }
    public void setHighlightEnabled(boolean enabled) { this.enableHighlight = enabled; }
    
    public float[] getBorderColor() { return borderColor; }
    public void setBorderColor(float[] color) { this.borderColor = color; }
    
    public float getBorderWidth() { return borderWidth; }
    public void setBorderWidth(float width) { this.borderWidth = width; }
    
    public boolean isBorderEnabled() { return enableBorder; }
    public void setBorderEnabled(boolean enabled) { this.enableBorder = enabled; }
}
