package com.chronoblocks.ui.panels;

import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.ui.widgets.CurvePreviewWidget;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 关键帧属性面板 v4.5 - 编辑关键帧的插值类型和曲线参数
 * 
 * <p>提供直观的 UI 来配置关键帧的插值曲线类型和参数
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>插值类型选择器（下拉菜单）</li>
 *   <li>贝塞尔曲线控制点编辑器</li>
 *   <li>预设曲线快速选择</li>
 *   <li>实时曲线预览（TODO）</li>
 * </ul>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class KeyframePropertiesPanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyframePropertiesPanel.class);
    
    // ==================== 选中的关键帧 ====================
    
    /** 当前选中的关键帧 */
    private Keyframe selectedKeyframe = null;
    
    // ==================== UI 状态 ====================
    
    /** 当前选中的插值类型索引 */
    private int selectedInterpTypeIndex = 0;
    
    /** 贝塞尔控制点 */
    private final float[] bezierControls = {0.25f, 0.1f, 0.25f, 1.0f};
    
    /** 插值类型数组（用于下拉菜单） */
    private static final InterpolationType[] INTERP_TYPES = InterpolationType.values();
    
    /** 贝塞尔预设名称 */
    private static final String[] BEZIER_PRESETS = {
        "Linear", "Ease", "Ease-In", "Ease-Out", "Ease-In-Out",
        "Ease-In-Quad", "Ease-Out-Quad", "Ease-In-Cubic", "Ease-Out-Cubic"
    };
    
    // ==================== 面板配置 ====================
    
    @Override
    public String getPanelName() {
        return "Keyframe Properties";
    }
    
    @Override
    public String getWindowName() {
        return Icons.KEYFRAME + " 关键帧属性###KeyframeProperties";
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }
    
    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }
    
    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    // ==================== 渲染方法 ====================
    
    @Override
    protected void doRender() {
        if (selectedKeyframe == null) {
            renderNoSelectionMessage();
            return;
        }
        
        // 标题
        UITheme.title(Icons.KEYFRAME + " 关键帧属性");
        
        // 基本信息组
        UITheme.beginGroup("基本信息");
        renderKeyframeInfo();
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 曲线预览组
        UITheme.beginGroup("曲线预览");
        renderCurvePreview();
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 插值设置组
        UITheme.beginGroup("插值设置");
        renderInterpolationTypeSelector();
        UITheme.spacing();
        UITheme.spacingSmall();
        
        InterpolationType currentType = selectedKeyframe.getInterpolationType();
        
        if (currentType == InterpolationType.BEZIER) {
            renderBezierControlsEditor();
        } else if (currentType.requiresCustomParameters()) {
            renderCustomParametersEditor(currentType);
        }
        UITheme.endGroup();
        
        UITheme.separator();
        
        UITheme.beginGroup("快捷提示");
        renderQuickTips();
        UITheme.endGroup();
    }
    
    /**
     * 渲染无选择消息
     */
    private void renderNoSelectionMessage() {
        UITheme.spacingLarge();
        UITheme.label("未选择关键帧", UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.spacingSmall();
        ImGui.textWrapped("在 Timeline 中选择一个关键帧以编辑其插值属性");
    }
    
    /**
     * 渲染关键帧信息
     */
    private void renderKeyframeInfo() {
        UITheme.label(String.format(Icons.TIME + " 时间: %.2fs", selectedKeyframe.getTime()), UITheme.DarkTheme.TEXT);
        UITheme.spacingSmall();
        
        float[] pos = selectedKeyframe.getPosition();
        UITheme.label(String.format(Icons.PIN + " 位置: (%.1f, %.1f, %.1f)", pos[0], pos[1], pos[2]), UITheme.DarkTheme.TEXT);
        UITheme.spacingSmall();
        
        UITheme.label("类型: " + selectedKeyframe.getInterpolationType().getDisplayName(), UITheme.DarkTheme.TEXT);
    }
    
    /**
     * 渲染插值类型选择器
     */
    private void renderInterpolationTypeSelector() {
        ImGui.text(Icons.STATS + " 插值类型");
        ImGui.spacing();
        
        // 获取当前类型
        InterpolationType currentType = selectedKeyframe.getInterpolationType();
        selectedInterpTypeIndex = currentType.ordinal();
        
        // 创建显示名称数组
        String[] typeNames = new String[INTERP_TYPES.length];
        for (int i = 0; i < INTERP_TYPES.length; i++) {
            typeNames[i] = INTERP_TYPES[i].getDisplayName();
        }
        
        // 渲染下拉框（ImGui combo 需要使用 ImInt）
        imgui.type.ImInt currentIdx = new imgui.type.ImInt(selectedInterpTypeIndex);
        if (ImGui.combo("##interpType", currentIdx, typeNames)) {
            selectedInterpTypeIndex = currentIdx.get();
            InterpolationType newType = INTERP_TYPES[selectedInterpTypeIndex];
            selectedKeyframe.setInterpolationType(newType);
            
            // 如果是贝塞尔类型，初始化控制点
            if (newType == InterpolationType.BEZIER && selectedKeyframe.getBezierControlPoints() == null) {
                selectedKeyframe.setBezierControlPoints(newType.getDefaultBezierControlPoints());
            }
            
            LOGGER.info("[KeyframeProperties] 插值类型已更改为: {}", newType.getDisplayName());
        }
        
        // 显示描述
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, Icons.INFO + " " + currentType.getDescription());
    }
    
    /**
     * 渲染贝塞尔控制点编辑器
     */
    private void renderBezierControlsEditor() {
        ImGui.text(Icons.PALETTE + " 贝塞尔控制点");
        ImGui.spacing();
        
        // 从关键帧获取当前控制点
        float[] currentControls = selectedKeyframe.getBezierControlPoints();
        if (currentControls != null && currentControls.length == 4) {
            System.arraycopy(currentControls, 0, bezierControls, 0, 4);
        }
        
        // 控制点编辑器（使用单元素数组访问）
        boolean changed = false;
        
        ImGui.text("P1:");
        ImGui.sameLine();
        float[] x1 = {bezierControls[0]};
        if (ImGui.sliderFloat("X1##bezier", x1, 0.0f, 1.0f)) {
            bezierControls[0] = x1[0];
            changed = true;
        }
        ImGui.sameLine();
        float[] y1 = {bezierControls[1]};
        if (ImGui.sliderFloat("Y1##bezier", y1, -0.5f, 1.5f)) {
            bezierControls[1] = y1[0];
            changed = true;
        }
        
        ImGui.text("P2:");
        ImGui.sameLine();
        float[] x2 = {bezierControls[2]};
        if (ImGui.sliderFloat("X2##bezier", x2, 0.0f, 1.0f)) {
            bezierControls[2] = x2[0];
            changed = true;
        }
        ImGui.sameLine();
        float[] y2 = {bezierControls[3]};
        if (ImGui.sliderFloat("Y2##bezier", y2, -0.5f, 1.5f)) {
            bezierControls[3] = y2[0];
            changed = true;
        }
        
        if (changed) {
            selectedKeyframe.setBezierControlPoints(bezierControls);
            LOGGER.debug("[KeyframeProperties] 贝塞尔控制点已更新: [{}, {}, {}, {}]", 
                bezierControls[0], bezierControls[1], bezierControls[2], bezierControls[3]);
        }
        
        // 预设按钮
        ImGui.spacing();
        ImGui.text(Icons.PACKAGE + " 预设:");
        renderBezierPresets();
    }
    
    /**
     * 渲染贝塞尔预设按钮
     */
    private void renderBezierPresets() {
        for (int i = 0; i < BEZIER_PRESETS.length; i++) {
            if (i > 0 && i % 3 != 0) {
                ImGui.sameLine();
            }
            
            if (ImGui.smallButton(BEZIER_PRESETS[i] + "##preset" + i)) {
                String presetName = BEZIER_PRESETS[i].toLowerCase();
                float[] preset = com.chronoblocks.core.animation.CurveInterpolator.getBezierPreset(presetName);
                
                System.arraycopy(preset, 0, bezierControls, 0, 4);
                selectedKeyframe.setBezierControlPoints(bezierControls);
                
                LOGGER.info("[KeyframeProperties] 应用预设: {}", BEZIER_PRESETS[i]);
            }
        }
    }
    
    /**
     * 渲染自定义参数编辑器
     */
    private void renderCustomParametersEditor(InterpolationType type) {
        ImGui.text(Icons.SETTINGS + " 自定义参数");
        ImGui.spacing();
        
        if (type == InterpolationType.SPRING) {
            renderSpringParametersEditor();
        } else if (type == InterpolationType.BOUNCE) {
            ImGui.textColored(0.8f, 0.8f, 0.0f, 1.0f, Icons.INFO + " 弹跳效果使用默认参数");
        }
    }
    
    /**
     * 渲染弹簧参数编辑器
     */
    private void renderSpringParametersEditor() {
        float[] params = selectedKeyframe.getCustomParams();
        if (params == null || params.length < 2) {
            params = new float[]{6.0f, 6.0f}; // 默认阻尼和频率
        }
        
        boolean changed = false;
        
        float[] damping = {params[0]};
        if (ImGui.sliderFloat("阻尼##spring", damping, 1.0f, 10.0f)) {
            params[0] = damping[0];
            changed = true;
        }
        
        float[] frequency = {params[1]};
        if (ImGui.sliderFloat("频率##spring", frequency, 1.0f, 10.0f)) {
            params[1] = frequency[0];
            changed = true;
        }
        
        if (changed) {
            selectedKeyframe.setCustomParams(params);
            LOGGER.debug("[KeyframeProperties] 弹簧参数已更新: damping={}, frequency={}", 
                params[0], params[1]);
        }
    }
    
    /**
     * 渲染曲线预览 v4.5 - 支持交互式编辑
     */
    private void renderCurvePreview() {
        ImGui.text(Icons.CHART + " 曲线预览");
        ImGui.spacing();
        
        // 获取当前插值类型和参数
        InterpolationType type = selectedKeyframe.getInterpolationType();
        float[] params = null;
        
        if (type == InterpolationType.BEZIER) {
            params = selectedKeyframe.getBezierControlPoints();
            if (params == null) {
                params = type.getDefaultBezierControlPoints();
                selectedKeyframe.setBezierControlPoints(params);
            }
        } else if (type.requiresCustomParameters()) {
            params = selectedKeyframe.getCustomParams();
        }
        
        // 渲染曲线预览（宽度自适应）
        float availableWidth = ImGui.getContentRegionAvailX();
        float previewWidth = Math.min(availableWidth, 250.0f);
        float previewHeight = previewWidth * 0.75f; // 4:3 比例
        
        // 使用交互式预览（仅对贝塞尔曲线）
        if (type == InterpolationType.BEZIER && params != null) {
            if (CurvePreviewWidget.renderInteractive(type, params, previewWidth, previewHeight)) {
                // 控制点被拖拽修改，更新到关键帧
                selectedKeyframe.setBezierControlPoints(params);
                // 同步更新 bezierControls 数组（用于滑动条显示）
                System.arraycopy(params, 0, bezierControls, 0, 4);
                LOGGER.debug("[KeyframeProperties] 通过拖拽更新贝塞尔控制点: [{}, {}, {}, {}]", 
                    params[0], params[1], params[2], params[3]);
            }
        } else {
            // 其他类型使用只读预览
            CurvePreviewWidget.render(type, params, previewWidth, previewHeight);
        }
        
        // 提示信息
        if (type == InterpolationType.BEZIER) {
            ImGui.spacing();
            ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, Icons.LIGHTBULB + " 提示: 拖拽橙色控制点调整曲线");
        }
    }
    
    /**
     * 渲染快速提示
     */
    private void renderQuickTips() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.8f, 0.5f, 1.0f);
        ImGui.textWrapped(Icons.LIGHTBULB + " 提示: 修改插值类型会影响到下一个关键帧之间的过渡效果");
        ImGui.popStyleColor();
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 设置当前选中的关键帧
     * @param keyframe 关键帧或 null
     */
    public void setSelectedKeyframe(Keyframe keyframe) {
        this.selectedKeyframe = keyframe;
        
        if (keyframe != null) {
            // 更新 UI 状态
            selectedInterpTypeIndex = keyframe.getInterpolationType().ordinal();
            
            if (keyframe.getBezierControlPoints() != null) {
                System.arraycopy(keyframe.getBezierControlPoints(), 0, bezierControls, 0, 4);
            }
            
            LOGGER.debug("[KeyframeProperties] 选中关键帧: time={}, type={}", 
                keyframe.getTime(), keyframe.getInterpolationType().getDisplayName());
        }
    }
    
    /**
     * 获取当前选中的关键帧
     * @return 关键帧或 null
     */
    public Keyframe getSelectedKeyframe() {
        return selectedKeyframe;
    }
    
    /**
     * 清除选择
     */
    public void clearSelection() {
        setSelectedKeyframe(null);
    }
}

