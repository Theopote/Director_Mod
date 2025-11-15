package com.chronoblocks.ui.panels;

import com.chronoblocks.client.renderer.SelectionRenderer;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SelectionRenderSettingsPanel v4.0（样式预设版）
 * <p>
 * 允许用户自定义选区的显示样式，包括：
 * - 渲染模式（线框 / 半透明 / 混合）
 * - 边框颜色与宽度（独立的边框预设）
 * - 填充颜色与透明度（独立的填充预设）
 * - 动画与高亮效果
 * - 模块化预设系统（边框预设 + 填充预设可自由组合）
 * - 性能设置（渲染距离、视锥剔除、遮挡剔除、渲染质量）
 * 
 * <p>
 * <strong>与其他面板的区别：</strong>
 * <ul>
 *   <li>此面板：专注于选区样式预设和性能优化，实时生效</li>
 *   <li>ToolPropertiesPanel：专注于工具参数配置和工具预览设置</li>
 * </ul>
 * 
 * <p>
 * <strong>重要特性：</strong>
 * - 所有设置实时生效，无需点击"应用"按钮
 * - 自动禁用SelectionOverlayRenderer，避免重复渲染
 * - 模块化预设系统：边框和填充预设可自由组合
 * 
 * @author ChronoBlocks
 * @version 4.1
 * @see ToolPropertiesPanel 工具属性面板（包含工具预览设置）
 */
public class SelectionRenderSettingsPanel extends AbstractPanel {
    
    // ==================== 样式数据结构 ====================
    
    /**
     * 边框样式预设
     */
    public record BorderStyle(String name, float[] color, float width) {}
    
    /**
     * 填充样式预设
     */
    public record FillStyle(String name, float[] color, float intensity) {}
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "SelectionRenderSettings";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionRenderSettingsPanel.class);
    
    // ==================== 默认值常量 ====================
    
    /** 默认渲染模式：0=线框, 1=填充, 2=混合 */
    private static final int DEFAULT_RENDER_MODE = 2;
    
    /** 默认高亮颜色 RGBA */
    private static final float[] DEFAULT_HIGHLIGHT_COLOR = {1.0f, 1.0f, 1.0f, 0.35f};
    
    /** 默认边框颜色 RGBA */
    private static final float[] DEFAULT_BORDER_COLOR = {0.1f, 0.8f, 1.0f, 0.95f};
    
    /** 默认边框宽度 */
    private static final float DEFAULT_BORDER_WIDTH = 2.0f;
    
    /** 默认高亮强度 */
    private static final float DEFAULT_HIGHLIGHT_INTENSITY = 1.0f;
    
    /** 默认最大渲染距离 */
    private static final int DEFAULT_MAX_RENDER_DISTANCE = 64;
    
    // ==================== 成员变量 ====================
    
    /**
     * 构造函数 - 默认不可见
     */
    public SelectionRenderSettingsPanel() {
        super();
        // 默认不可见，由Window菜单的Toggle Panels管理
        setVisible(false);
        
        // 注意：不在构造函数中立即应用设置，而是在首次渲染时应用
        // 这样可以避免在UI系统完全初始化前调用渲染器
    }
    
    /** 渲染模式：0=线框, 1=填充, 2=混合 */
    private int renderMode = DEFAULT_RENDER_MODE;
    
    // ==================== 模块化预设系统 ====================
    
    /** 边框样式预设列表 */
    private final BorderStyle[] borderPresets = {
        new BorderStyle("蓝色辉光", new float[]{0.1f, 0.7f, 1.0f, 1.0f}, 2.5f),
        new BorderStyle("危险红", new float[]{1.0f, 0.2f, 0.2f, 1.0f}, 2.0f),
        new BorderStyle("科技绿", new float[]{0.1f, 1.0f, 0.3f, 1.0f}, 1.5f),
        new BorderStyle("默认", new float[]{0.1f, 0.8f, 1.0f, 0.95f}, 2.0f)
    };
    
    /** 填充样式预设列表 */
    private final FillStyle[] fillPresets = {
        new FillStyle("柔光蓝", new float[]{0.3f, 0.7f, 1.0f, 0.25f}, 1.0f),
        new FillStyle("警告黄", new float[]{1.0f, 0.8f, 0.0f, 0.30f}, 1.2f),
        new FillStyle("幽灵绿", new float[]{0.1f, 1.0f, 0.3f, 0.2f}, 0.8f),
        new FillStyle("默认", DEFAULT_HIGHLIGHT_COLOR.clone(), DEFAULT_HIGHLIGHT_INTENSITY)
    };
    
    /** 当前选择的边框预设索引 */
    private int selectedBorderPreset = 3; // 默认选择"默认"预设
    
    /** 当前选择的填充预设索引 */
    private int selectedFillPreset = 3; // 默认选择"默认"预设
    
    /** 高亮设置 */
    private float[] highlightColor = DEFAULT_HIGHLIGHT_COLOR.clone();
    private float highlightIntensity = DEFAULT_HIGHLIGHT_INTENSITY;
    
    /** 边框线设置 */
    private float[] borderColor = DEFAULT_BORDER_COLOR.clone();
    private float borderWidth = DEFAULT_BORDER_WIDTH;
    
    /** 性能设置 */
    private int maxRenderDistance = DEFAULT_MAX_RENDER_DISTANCE;
    private boolean enableFrustumCulling = true;
    private boolean enableOcclusionCulling = false;
    
    /** 标记设置是否已应用 */
    private boolean settingsApplied = false;
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.COLOR + " 选区渲染设置###SelectionRenderSettingsPanel";
    }
    
    @Override
    public int getPriority() {
        return 30;
    }
    
    @Override
    protected int getWindowFlags() {
        return super.getWindowFlags();
    }
    
    @Override
    protected void doRender() {
        // 首次渲染时应用默认设置（如果尚未应用）
        if (!settingsApplied) {
            try {
                applySettings();
                settingsApplied = true;
                LOGGER.info("[SelectionRenderSettingsPanel] 首次渲染时已应用默认渲染设置");
            } catch (Exception e) {
                LOGGER.warn("[SelectionRenderSettingsPanel] 首次渲染时应用设置失败", e);
            }
        }
        
        UITheme.title(Icons.COLOR + " 选区渲染设置");
        
        // 渲染模式选择
        renderModeSelector();
        ImGui.separator();
        
        // 样式预设
        renderStylePresets();
        ImGui.separator();
        
        // 高亮设置
        renderHighlightSettings();
        ImGui.separator();
        
        // 边框设置
        renderBorderSettings();
        ImGui.separator();
        
        // 性能设置
        renderPerformanceSettings();
        ImGui.separator();
        
        // 操作按钮
        renderActionButtons();
    }
    
    /**
     * 渲染模式选择
     */
    private void renderModeSelector() {
        UITheme.label("渲染模式:", UITheme.DarkTheme.TEXT_SELECTED);
        
        String[] modes = {"线框", "半透明", "线框 + 半透明"};
        ImInt modeInt = new ImInt(renderMode);
        
        if (ImGui.combo("##renderMode", modeInt, modes)) {
            renderMode = modeInt.get();
            LOGGER.info("[SelectionRenderSettingsPanel] 渲染模式: {}", modes[renderMode]);
            applySettings(); // 实时应用
        }
    }
    
    /**
     * 样式预设 - 模块化设计
     * 边框预设和填充预设可以自由组合
     */
    private void renderStylePresets() {
        // --- 边框预设 ---
        UITheme.label("边框样式预设:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 从对象数组中提取名称
        String[] borderPresetNames = java.util.Arrays.stream(borderPresets)
                                                      .map(BorderStyle::name)
                                                      .toArray(String[]::new);
        
        ImInt borderPresetInt = new ImInt(selectedBorderPreset);
        if (ImGui.combo("##borderPreset", borderPresetInt, borderPresetNames)) {
            selectedBorderPreset = borderPresetInt.get();
            applyBorderPreset(borderPresets[selectedBorderPreset]);
        }
        
        ImGui.separator();
        
        // --- 填充预设 ---
        UITheme.label("填充样式预设:", UITheme.DarkTheme.TEXT_SELECTED);
        
        String[] fillPresetNames = java.util.Arrays.stream(fillPresets)
                                                    .map(FillStyle::name)
                                                    .toArray(String[]::new);
        
        ImInt fillPresetInt = new ImInt(selectedFillPreset);
        if (ImGui.combo("##fillPreset", fillPresetInt, fillPresetNames)) {
            selectedFillPreset = fillPresetInt.get();
            applyFillPreset(fillPresets[selectedFillPreset]);
        }
    }
    
    /**
     * 应用边框预设并立即生效
     */
    private void applyBorderPreset(BorderStyle style) {
        this.borderColor = style.color().clone();
        this.borderWidth = style.width();
        
        // 立即应用到渲染器
        applySettings();
        
        LOGGER.info("[SelectionRenderSettingsPanel] 应用边框预设: {}", style.name());
    }
    
    /**
     * 应用填充预设并立即生效
     */
    private void applyFillPreset(FillStyle style) {
        this.highlightColor = style.color().clone();
        this.highlightIntensity = style.intensity();
        
        // 立即应用到渲染器
        applySettings();
        
        LOGGER.info("[SelectionRenderSettingsPanel] 应用填充预设: {}", style.name());
    }
    
    /**
     * 高亮设置
     */
    private void renderHighlightSettings() {
        UITheme.label("填充高亮:", UITheme.DarkTheme.TEXT_SELECTED);

        ImGui.indent();

        if (ImGui.colorEdit4("填充颜色##highlightColor", highlightColor)) {
            LOGGER.debug("[SelectionRenderSettingsPanel] 高亮颜色更新");
            applySettings(); // 实时应用
        }

        float[] intensity = {highlightIntensity};
        if (ImGui.sliderFloat("亮度##highlightIntensity", intensity, 0.1f, 2.0f, "%.1f")) {
            highlightIntensity = intensity[0];
            applySettings(); // 实时应用
        }

        ImGui.unindent();
    }
    
    /**
     * 边框设置
     */
    private void renderBorderSettings() {
        UITheme.label("边框线设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        ImGui.indent();

        if (ImGui.colorEdit4("边框颜色##borderColor", borderColor)) {
            LOGGER.debug("[SelectionRenderSettingsPanel] 边框颜色更新");
            applySettings(); // 实时应用
        }

        float[] width = {borderWidth};
        if (ImGui.sliderFloat("线宽##borderWidth", width, 1.0f, 5.0f, "%.1f")) {
            borderWidth = width[0];
            applySettings(); // 实时应用
        }

        ImGui.unindent();
    }
    
    /**
     * 性能设置
     */
    private void renderPerformanceSettings() {
        UITheme.label("性能设置:", UITheme.DarkTheme.TEXT_SELECTED);
        
        int[] distance = {maxRenderDistance};
        if (ImGui.sliderInt("最大渲染距离##maxRenderDist", distance, 16, 128)) {
            maxRenderDistance = distance[0];
            applySettings(); // 实时应用
        }
        
        if (ImGui.checkbox("视锥剔除##frustum", enableFrustumCulling)) {
            enableFrustumCulling = !enableFrustumCulling;
            applySettings(); // 实时应用
        }
        
        if (ImGui.checkbox("遮挡剔除##occlusion", enableOcclusionCulling)) {
            enableOcclusionCulling = !enableOcclusionCulling;
            applySettings(); // 实时应用
        }
    }
    
    /**
     * 操作按钮
     * 注意：已移除"应用"按钮，所有设置实时生效
     */
    private void renderActionButtons() {
        UITheme.label("操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("重置##resetSettings")) {
            resetToDefaults();
        }
    }
    
    /**
     * 应用设置到渲染器
     * 确保所有选择工具都使用统一的渲染样式
     */
    private void applySettings() {
        try {
            SelectionRenderer renderer = SelectionRenderer.getInstance();
            
            // 应用主渲染器设置（所有选择工具都会通过SelectionRenderer统一渲染）
            renderer.setEnabled(true);
            renderer.setSelectionColor(highlightColor);
            renderer.setOutlineColor(borderColor);
            renderer.setOutlineWidth(borderWidth);
            renderer.setEnableAnimations(false);
            renderer.setRenderMode(renderMode);
            renderer.setHighlightIntensity(highlightIntensity);
            renderer.setEnableFill(renderMode == 1 || renderMode == 2);
            
            // 应用性能设置
            renderer.setMaxRenderDistance(maxRenderDistance);
            renderer.setEnableFrustumCulling(enableFrustumCulling);
            renderer.setEnableOcclusionCulling(enableOcclusionCulling);
            
            LOGGER.debug("[SelectionRenderSettingsPanel] 应用渲染设置成功 - 模式: {}, 距离: {}", 
                renderMode, maxRenderDistance);
            LOGGER.debug("[SelectionRenderSettingsPanel] SelectionOverlayRenderer保持启用，交互元素（面中心小方块、双向箭头）始终显示");
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderSettingsPanel] 应用设置失败", e);
        }
    }
    
    /**
     * 重置默认值
     * 使用常量定义，避免重复定义
     */
    private void resetToDefaults() {
        renderMode = DEFAULT_RENDER_MODE;
        selectedBorderPreset = 3; // 默认预设
        selectedFillPreset = 3; // 默认预设
        highlightColor = DEFAULT_HIGHLIGHT_COLOR.clone();
        borderColor = DEFAULT_BORDER_COLOR.clone();
        borderWidth = DEFAULT_BORDER_WIDTH;
        highlightIntensity = DEFAULT_HIGHLIGHT_INTENSITY;
        maxRenderDistance = DEFAULT_MAX_RENDER_DISTANCE;
        enableFrustumCulling = true;
        enableOcclusionCulling = false;
        
        // 重置后自动应用
        applySettings();
        
        LOGGER.info("[SelectionRenderSettingsPanel] 重置为默认值并已应用");
    }
}