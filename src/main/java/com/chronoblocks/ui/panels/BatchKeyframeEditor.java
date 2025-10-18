package com.chronoblocks.ui.panels;

import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量关键帧编辑器 v4.5 - 批量操作多个关键帧
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>批量设置插值类型</li>
 *   <li>批量应用贝塞尔预设</li>
 *   <li>时间缩放（Scale Time）</li>
 *   <li>时间偏移（Offset Time）</li>
 *   <li>对齐功能（Align）</li>
 *   <li>分布功能（Distribute）</li>
 * </ul>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class BatchKeyframeEditor extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchKeyframeEditor.class);
    
    // ==================== 选中的关键帧 ====================
    
    private List<Keyframe> selectedKeyframes = new ArrayList<>();
    
    // ==================== UI 状态 ====================
    
    /** 时间缩放因子 */
    private final float[] scaleValue = {1.0f};
    
    /** 时间偏移量 */
    private final float[] offsetValue = {0.0f};
    
    /** 选中的插值类型索引 */
    private int selectedTypeIndex = 0;
    
    // ==================== 面板配置 ====================
    
    @Override
    public String getPanelName() {
        return "Batch Editor";
    }
    
    @Override
    public String getWindowName() {
        return "📦 批量编辑器###BatchKeyframeEditor";
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
        if (selectedKeyframes.isEmpty()) {
            renderNoSelectionMessage();
            return;
        }
        
        // 标题
        UITheme.title("📦 批量编辑");
        
        // 选择信息组
        UITheme.beginGroup("选择信息");
        renderSelectionInfo();
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 插值类型组
        UITheme.beginGroup("插值类型");
        renderInterpolationSection();
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 时间操作组
        UITheme.beginGroup("时间操作");
        renderTimeSection();
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 对齐与分布组
        UITheme.beginGroup("对齐与分布");
        renderAlignSection();
        UITheme.endGroup();
    }
    
    /**
     * 渲染无选择消息
     */
    private void renderNoSelectionMessage() {
        UITheme.spacingLarge();
        UITheme.label("未选择关键帧", UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.spacingSmall();
        ImGui.textWrapped("在 Timeline 中选择多个关键帧（Ctrl + 点击）以使用批量编辑功能");
    }
    
    /**
     * 渲染选择信息
     */
    private void renderSelectionInfo() {
        UITheme.label(String.format("已选中 %d 个关键帧", selectedKeyframes.size()), UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        
        // 时间范围
        if (selectedKeyframes.size() > 1) {
            float minTime = Float.MAX_VALUE;
            float maxTime = Float.MIN_VALUE;
            
            for (Keyframe kf : selectedKeyframes) {
                minTime = Math.min(minTime, kf.getTime());
                maxTime = Math.max(maxTime, kf.getTime());
            }
            
            ImGui.text(String.format("⏱ 时间范围: %.2fs - %.2fs (%.2fs)", 
                minTime, maxTime, maxTime - minTime));
        }
    }
    
    /**
     * 渲染插值类型设置区域
     */
    private void renderInterpolationSection() {
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "📊 插值类型");
        ImGui.spacing();
        
        // 插值类型下拉框
        InterpolationType[] types = InterpolationType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].getDisplayName();
        }
        
        ImInt currentIdx = new ImInt(selectedTypeIndex);
        if (ImGui.combo("##batchInterpType", currentIdx, typeNames)) {
            selectedTypeIndex = currentIdx.get();
        }
        
        ImGui.sameLine();
        if (ImGui.button("应用##applyInterp")) {
            applyInterpolationType(types[selectedTypeIndex]);
        }
        
        // 快捷按钮
        ImGui.spacing();
        ImGui.text("快捷设置:");
        
        if (ImGui.smallButton("线性")) {
            applyInterpolationType(InterpolationType.LINEAR);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("缓入缓出")) {
            applyInterpolationType(InterpolationType.EASE_IN_OUT);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("贝塞尔")) {
            applyInterpolationType(InterpolationType.BEZIER);
        }
    }
    
    /**
     * 渲染时间操作区域
     */
    private void renderTimeSection() {
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "⏱ 时间操作");
        ImGui.spacing();
        
        // 时间缩放
        ImGui.text("时间缩放:");
        ImGui.setNextItemWidth(120);
        ImGui.dragFloat("##timeScale", scaleValue, 0.01f, 0.1f, 10.0f, "%.2f");
        ImGui.sameLine();
        if (ImGui.button("应用缩放##applyScale")) {
            scaleTime(scaleValue[0]);
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("缩放选中关键帧的时间间隔\n> 1.0 = 拉长, < 1.0 = 压缩");
        }
        
        // 时间偏移
        ImGui.text("时间偏移:");
        ImGui.setNextItemWidth(120);
        ImGui.dragFloat("##timeOffset", offsetValue, 0.01f, -10.0f, 10.0f, "%.2f");
        ImGui.sameLine();
        if (ImGui.button("应用偏移##applyOffset")) {
            offsetTime(offsetValue[0]);
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("向前或向后移动所有选中的关键帧");
        }
    }
    
    /**
     * 渲染对齐和分布区域
     */
    private void renderAlignSection() {
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "📐 对齐与分布");
        ImGui.spacing();
        
        // 对齐按钮
        if (ImGui.button("对齐到第一个##alignFirst")) {
            alignToFirst();
        }
        ImGui.sameLine();
        if (ImGui.button("对齐到最后##alignLast")) {
            alignToLast();
        }
        
        // 分布按钮
        ImGui.spacing();
        if (ImGui.button("均匀分布##distribute")) {
            distributeEvenly();
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在第一个和最后一个关键帧之间均匀分布");
        }
    }
    
    // ==================== 操作方法 ====================
    
    /**
     * 批量应用插值类型
     */
    private void applyInterpolationType(InterpolationType type) {
        for (Keyframe kf : selectedKeyframes) {
            kf.setInterpolationType(type);
            
            // 如果是贝塞尔类型，初始化默认控制点
            if (type == InterpolationType.BEZIER && kf.getBezierControlPoints() == null) {
                kf.setBezierControlPoints(type.getDefaultBezierControlPoints());
            }
        }
        
        LOGGER.info("[BatchKeyframeEditor] 批量设置 {} 个关键帧为 {}", 
            selectedKeyframes.size(), type.getDisplayName());
    }
    
    /**
     * 时间缩放
     */
    private void scaleTime(float scale) {
        if (selectedKeyframes.isEmpty() || scale <= 0) {
            return;
        }
        
        // 使用第一个关键帧作为缩放中心
        float pivot = selectedKeyframes.get(0).getTime();
        
        for (Keyframe kf : selectedKeyframes) {
            float offset = kf.getTime() - pivot;
            kf.setTime(pivot + offset * scale);
        }
        
        LOGGER.info("[BatchKeyframeEditor] 时间缩放: {} 倍, 中心: {}s", scale, pivot);
    }
    
    /**
     * 时间偏移
     */
    private void offsetTime(float offset) {
        for (Keyframe kf : selectedKeyframes) {
            kf.setTime(Math.max(0, kf.getTime() + offset));
        }
        
        LOGGER.info("[BatchKeyframeEditor] 时间偏移: {} 秒", offset);
    }
    
    /**
     * 对齐到第一个关键帧
     */
    private void alignToFirst() {
        if (selectedKeyframes.size() < 2) {
            return;
        }
        
        float targetTime = selectedKeyframes.get(0).getTime();
        
        for (int i = 1; i < selectedKeyframes.size(); i++) {
            selectedKeyframes.get(i).setTime(targetTime);
        }
        
        LOGGER.info("[BatchKeyframeEditor] 对齐到 {} 秒", targetTime);
    }
    
    /**
     * 对齐到最后一个关键帧
     */
    private void alignToLast() {
        if (selectedKeyframes.size() < 2) {
            return;
        }
        
        float targetTime = selectedKeyframes.get(selectedKeyframes.size() - 1).getTime();
        
        for (int i = 0; i < selectedKeyframes.size() - 1; i++) {
            selectedKeyframes.get(i).setTime(targetTime);
        }
        
        LOGGER.info("[BatchKeyframeEditor] 对齐到 {} 秒", targetTime);
    }
    
    /**
     * 均匀分布
     */
    private void distributeEvenly() {
        if (selectedKeyframes.size() < 3) {
            return;
        }
        
        // 按时间排序
        selectedKeyframes.sort((a, b) -> Float.compare(a.getTime(), b.getTime()));
        
        float startTime = selectedKeyframes.get(0).getTime();
        float endTime = selectedKeyframes.get(selectedKeyframes.size() - 1).getTime();
        float interval = (endTime - startTime) / (selectedKeyframes.size() - 1);
        
        for (int i = 1; i < selectedKeyframes.size() - 1; i++) {
            selectedKeyframes.get(i).setTime(startTime + interval * i);
        }
        
        LOGGER.info("[BatchKeyframeEditor] 均匀分布: {} - {} 秒, 间隔: {} 秒", 
            startTime, endTime, interval);
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 设置选中的关键帧列表
     */
    public void setSelectedKeyframes(List<Keyframe> keyframes) {
        this.selectedKeyframes = keyframes != null ? new ArrayList<>(keyframes) : new ArrayList<>();
        LOGGER.debug("[BatchKeyframeEditor] 选中 {} 个关键帧", this.selectedKeyframes.size());
    }
    
    /**
     * 获取选中的关键帧列表
     */
    public List<Keyframe> getSelectedKeyframes() {
        return new ArrayList<>(selectedKeyframes);
    }
    
    /**
     * 清除选择
     */
    public void clearSelection() {
        selectedKeyframes.clear();
    }
}

