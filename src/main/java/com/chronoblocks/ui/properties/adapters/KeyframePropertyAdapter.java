package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 关键帧属性适配器 v4.7 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 动画系统的关键帧对象提供专用属性编辑器。
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>⏱ 时间点（Time）</li>
 *   <li>📍 位置（Position XYZ）</li>
 *   <li>🔄 旋转（Rotation XYZ - 度）</li>
 *   <li>📏 缩放（Scale XYZ）</li>
 *   <li>👁 不透明度（Opacity 0-1）</li>
 *   <li>📈 插值类型（Interpolation Type）</li>
 *   <li>🎨 贝塞尔控制点（Bezier Control Points）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>快捷操作 - 归零、重置等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 * </ul>
 * 
 * @version 4.7
 * @since 2025-10-15
 */
public class KeyframePropertyAdapter implements IPropertyAdapter<Keyframe> {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyframePropertyAdapter.class);

    @Override
    public boolean renderProperties(Keyframe kf) {
        if (kf == null) {
            UITheme.spacingLarge();
            UITheme.label("关键帧为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 标题
        UITheme.title("🎞 关键帧属性编辑器");
        
        // ========== 时间 ==========
        if (ImGui.collapsingHeader("⏱ 时间", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] time = {kf.getTime()};
            if (ImGui.dragFloat("时间点 (秒)", time, 0.01f, 0.0f, Float.MAX_VALUE)) {
                kf.setTime(time[0]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 时间已更新: {}", time[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("关键帧在时间轴上的位置");
            }
        }
        
        // ========== 位置 ==========
        if (ImGui.collapsingHeader("📍 位置 (Position)", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] position = kf.getPosition().clone();
            if (ImGui.dragFloat3("XYZ", position, 0.1f)) {
                kf.setPosition(position[0], position[1], position[2]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 位置已更新: ({}, {}, {})", 
                    position[0], position[1], position[2]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器在世界空间中的位置");
            }
            
            // 快捷操作
            UITheme.spacingSmall();
            if (ImGui.button("归零##pos", 60, UITheme.Spacing.BUTTON_HEIGHT)) {
                kf.setPosition(0, 0, 0);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将位置重置为 (0, 0, 0)");
            }
        }
        
        // ========== 旋转 ==========
        if (ImGui.collapsingHeader("🔄 旋转 (Rotation)", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] rotation = kf.getRotation().clone();
            if (ImGui.dragFloat3("XYZ (度)", rotation, 1.0f, -180.0f, 180.0f)) {
                kf.setRotation(rotation[0], rotation[1], rotation[2]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 旋转已更新: ({}, {}, {})", 
                    rotation[0], rotation[1], rotation[2]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的旋转角度（欧拉角）");
            }
            
            // 快捷操作
            UITheme.spacingSmall();
            if (ImGui.button("归零##rot", 60, UITheme.Spacing.BUTTON_HEIGHT)) {
                kf.setRotation(0, 0, 0);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将旋转重置为 (0°, 0°, 0°)");
            }
        }
        
        // ========== 缩放 ==========
        if (ImGui.collapsingHeader("📏 缩放 (Scale)", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] scale = kf.getScale().clone();
            if (ImGui.dragFloat3("XYZ", scale, 0.01f, 0.001f, 10.0f)) {
                kf.setScale(scale[0], scale[1], scale[2]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 缩放已更新: ({}, {}, {})", 
                    scale[0], scale[1], scale[2]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的缩放比例");
            }
            
            // 快捷操作
            UITheme.spacingSmall();
            if (ImGui.button("重置为 1##scale", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
                kf.setScale(1, 1, 1);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将缩放重置为 (1, 1, 1)");
            }
            ImGui.sameLine();
            if (ImGui.button("统一缩放##scale", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
                float avg = (scale[0] + scale[1] + scale[2]) / 3.0f;
                kf.setScale(avg, avg, avg);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将 XYZ 缩放设为平均值");
            }
        }
        
        // ========== 不透明度 ==========
        if (ImGui.collapsingHeader("👁 不透明度 (Opacity)", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] opacity = {kf.getOpacity()};
            if (ImGui.sliderFloat("不透明度", opacity, 0.0f, 1.0f, "%.2f")) {
                kf.setOpacity(opacity[0]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 不透明度已更新: {}", opacity[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的可见度 (0 = 完全透明, 1 = 完全不透明)");
            }
            
            // 百分比显示
            UITheme.spacingSmall();
            UITheme.label(String.format("%.0f%%", opacity[0] * 100), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        // ========== 插值类型 ==========
        if (ImGui.collapsingHeader("📈 插值 (Interpolation)", ImGuiTreeNodeFlags.DefaultOpen)) {
            InterpolationType currentType = kf.getInterpolationType();
            InterpolationType[] types = InterpolationType.values();
            
            int currentIdx = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == currentType) {
                    currentIdx = i;
                    break;
                }
            }
            
            ImInt selectedIdx = new ImInt(currentIdx);
            String[] typeNames = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                typeNames[i] = types[i].getDisplayName();
            }
            
            if (ImGui.combo("插值类型", selectedIdx, typeNames)) {
                kf.setInterpolationType(types[selectedIdx.get()]);
                modified = true;
                LOGGER.debug("[KeyframePropertyAdapter] 插值类型已更新: {}", 
                    types[selectedIdx.get()].getDisplayName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("控制到下一个关键帧的动画曲线");
            }
            
            // 贝塞尔控制点（仅 BEZIER 类型显示）
            if (currentType == InterpolationType.BEZIER) {
                UITheme.spacingSmall();
                
                float[] bezier = kf.getBezierControlPoints();
                if (bezier == null || bezier.length != 4) {
                    bezier = new float[]{0.25f, 0.1f, 0.25f, 1.0f};
                    kf.setBezierControlPoints(bezier);
                }
                
                float[] p1 = {bezier[0], bezier[1]};
                if (ImGui.dragFloat2("控制点 P1", p1, 0.01f, 0.0f, 1.0f)) {
                    kf.setBezierControlPoints(p1[0], p1[1], bezier[2], bezier[3]);
                    modified = true;
                }
                
                float[] p2 = {bezier[2], bezier[3]};
                if (ImGui.dragFloat2("控制点 P2", p2, 0.01f, 0.0f, 1.0f)) {
                    kf.setBezierControlPoints(bezier[0], bezier[1], p2[0], p2[1]);
                    modified = true;
                }
            }
            
            // 自定义参数（Spring, Bounce 等）
            if (currentType.requiresCustomParameters() && currentType != InterpolationType.BEZIER) {
                UITheme.spacingSmall();
                ImGui.textWrapped("此插值类型支持自定义参数，详细编辑请使用关键帧属性面板。");
            }
        }
        
        UITheme.separator();
        
        // ========== 快捷操作 ==========
        UITheme.beginGroup("快捷操作");
        
        if (ImGui.button("重置所有变换", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            kf.setPosition(0, 0, 0);
            kf.setRotation(0, 0, 0);
            kf.setScale(1, 1, 1);
            kf.setOpacity(1.0f);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("将位置、旋转、缩放、不透明度重置为默认值");
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("复制当前状态", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            // TODO: 实现复制到剪贴板
            LOGGER.info("[KeyframePropertyAdapter] 复制关键帧状态");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("复制当前关键帧的所有属性");
        }
        
        UITheme.endGroup();
        
        return modified;
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
    
    @Override
    public String getAdapterName() {
        return "关键帧属性编辑器";
    }
}

