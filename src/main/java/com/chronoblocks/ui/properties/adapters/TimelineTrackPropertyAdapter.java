package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线轨道属性适配器 v4.8 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 时间线系统的轨道对象提供专用属性编辑器。
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 基本信息（名称、启用状态、轨道类型、ID）</li>
 *   <li>📦 容器绑定（绑定容器名称、选择器）</li>
 *   <li>🎨 混合与权重（混合模式、权重、叠加模式）</li>
 *   <li>⏱ 对齐与范围（对齐模式、开始/结束时间）</li>
 *   <li>👁 可视化参数（轨道颜色、显示偏移）</li>
 *   <li>📊 统计信息（关键帧数量、持续时间）</li>
 *   <li>⚡ 快捷操作（复制、清空、锁定等）</li>
 * </ul>
 * 
 * <h3>混合模式</h3>
 * <ul>
 *   <li>Override - 覆盖模式（默认）</li>
 *   <li>Additive - 叠加模式</li>
 *   <li>Multiply - 乘法模式</li>
 *   <li>Subtract - 减法模式</li>
 *   <li>Custom - 自定义模式</li>
 * </ul>
 * 
 * <h3>对齐模式</h3>
 * <ul>
 *   <li>Global Time - 全局时间轴对齐</li>
 *   <li>Container Time - 容器时间对齐</li>
 *   <li>Local Offset - 本地偏移对齐</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>快捷操作 - 复制、清空等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>颜色拾取 - 轨道颜色编辑</li>
 *   <li>统计信息 - 实时计算显示</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于时间线系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。当时间线系统完整后，可以
 * 替换为具体的 TimelineTrack 类型。
 * 
 * @author ChronoBlocks Team
 * @version 4.8
 * @since 2025-10-16
 */
public class TimelineTrackPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTrackPropertyAdapter.class);
    
    // 混合模式选项
    private static final String[] BLEND_MODES = {
        "覆盖 (Override)",
        "叠加 (Additive)",
        "乘法 (Multiply)",
        "减法 (Subtract)",
        "自定义 (Custom)"
    };
    
    // 对齐模式选项
    private static final String[] ALIGNMENT_MODES = {
        "全局时间 (Global Time)",
        "容器时间 (Container Time)",
        "本地偏移 (Local Offset)"
    };

    @Override
    public boolean renderProperties(Object track) {
        if (track == null) {
            UITheme.spacingLarge();
            UITheme.label("时间线轨道为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🎞 时间线轨道属性编辑器");
        
        try {
            // ========== 基本信息 ==========
            if (ImGui.collapsingHeader("📋 基本信息", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 轨道名称
                String currentName = getStringProperty(track, "name", "getName", "未命名轨道");
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText("轨道名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(track, "name", "setName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 轨道名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("轨道的显示名称");
                }
                
                // 启用状态
                boolean currentEnabled = getBooleanProperty(track, "enabled", "isEnabled", true);
                ImBoolean enabled = new ImBoolean(currentEnabled);
                if (ImGui.checkbox("启用轨道", enabled)) {
                    if (setBooleanProperty(track, "enabled", "setEnabled", enabled.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 启用状态已更新: {}", enabled.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制轨道是否参与播放");
                }
                
                // 轨道类型（只读）
                String type = getStringProperty(track, "type", "getType", "未知");
                UITheme.spacingSmall();
                UITheme.label("轨道类型: " + type, UITheme.DarkTheme.TEXT);
                
                // UUID 显示
                String id = getStringProperty(track, "id", "getId", null);
                if (id != null) {
                    UITheme.label("轨道 ID: " + id, UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 容器绑定 ==========
            if (ImGui.collapsingHeader("📦 容器绑定")) {
                String currentContainer = getStringProperty(track, "boundContainerName", "getBoundContainerName", "(未绑定)");
                ImString containerBuffer = new ImString(currentContainer, 256);
                if (ImGui.inputText("绑定容器", containerBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(track, "boundContainerName", "setBoundContainerName", containerBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 绑定容器已更新: {}", containerBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("指定此轨道控制的容器名称");
                }
                
                UITheme.spacingSmall();
                
                if (ImGui.button("选择容器...", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[TimelineTrackPropertyAdapter] 打开容器选择对话框（待实现）");
                    // TODO: 打开容器选择对话框
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("从列表中选择容器");
                }
                
                ImGui.sameLine();
                
                if (ImGui.button("清除绑定", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    if (setStringProperty(track, "boundContainerName", "setBoundContainerName", null)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 已清除容器绑定");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("移除容器绑定");
                }
            }
            
            // ========== 混合与权重 ==========
            if (ImGui.collapsingHeader("🎨 混合与权重", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 混合模式
                int currentBlendMode = getIntProperty(track, "blendMode", "getBlendMode", 0);
                ImInt blendModeIdx = new ImInt(currentBlendMode);
                if (ImGui.combo("混合模式", blendModeIdx, BLEND_MODES)) {
                    if (setIntProperty(track, "blendMode", "setBlendMode", blendModeIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 混合模式已更新: {}", 
                            BLEND_MODES[blendModeIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制此轨道如何与其他轨道混合");
                }
                
                // 权重
                float currentWeight = getFloatProperty(track, "weight", "getWeight", 1.0f);
                float[] weight = {currentWeight};
                if (ImGui.dragFloat("权重", weight, 0.01f, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(track, "weight", "setWeight", weight[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 权重已更新: {}", weight[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制此轨道的影响强度 (0.0 = 无影响, 1.0 = 完全影响)");
                }
                
                // 百分比显示
                UITheme.spacingSmall();
                UITheme.label(String.format("%.0f%%", weight[0] * 100), UITheme.DarkTheme.TEXT_DISABLED);
                
                UITheme.spacingSmall();
                
                // 叠加模式
                boolean currentAdditive = getBooleanProperty(track, "additive", "isAdditive", false);
                ImBoolean additive = new ImBoolean(currentAdditive);
                if (ImGui.checkbox("叠加模式", additive)) {
                    if (setBooleanProperty(track, "additive", "setAdditive", additive.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 叠加模式已更新: {}", additive.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("启用后，此轨道的效果会叠加到其他轨道上");
                }
            }
            
            // ========== 对齐与范围 ==========
            if (ImGui.collapsingHeader("⏱ 对齐与范围", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 对齐模式
                int currentAlignment = getIntProperty(track, "alignmentMode", "getAlignmentMode", 0);
                ImInt alignmentIdx = new ImInt(currentAlignment);
                if (ImGui.combo("对齐模式", alignmentIdx, ALIGNMENT_MODES)) {
                    if (setIntProperty(track, "alignmentMode", "setAlignmentMode", alignmentIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 对齐模式已更新: {}", 
                            ALIGNMENT_MODES[alignmentIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制轨道时间如何与时间轴对齐");
                }
                
                UITheme.spacingSmall();
                
                // 开始时间
                float currentStart = getFloatProperty(track, "startTime", "getStartTime", 0.0f);
                float[] startTime = {currentStart};
                if (ImGui.dragFloat("开始时间 (秒)", startTime, 0.05f, 0.0f, 600.0f)) {
                    if (setFloatProperty(track, "startTime", "setStartTime", startTime[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 开始时间已更新: {}", startTime[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("轨道在时间轴上的起始位置");
                }
                
                // 结束时间
                float currentEnd = getFloatProperty(track, "endTime", "getEndTime", 5.0f);
                float[] endTime = {currentEnd};
                if (ImGui.dragFloat("结束时间 (秒)", endTime, 0.05f, 0.0f, 600.0f)) {
                    if (setFloatProperty(track, "endTime", "setEndTime", endTime[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 结束时间已更新: {}", endTime[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("轨道在时间轴上的结束位置");
                }
                
                // 持续时间显示
                float duration = Math.max(0, endTime[0] - startTime[0]);
                UITheme.spacingSmall();
                UITheme.label(String.format("持续时间: %.2f 秒", duration), UITheme.DarkTheme.TEXT_SELECTED);
            }
            
            // ========== 可视化参数 ==========
            if (ImGui.collapsingHeader("👁 可视化参数")) {
                // 轨道颜色
                float[] colorRGBA = getFloatArrayProperty(track, "colorRGBA", "getColorRGBA");
                if (colorRGBA == null || colorRGBA.length < 4) {
                    colorRGBA = new float[]{0.4f, 0.8f, 1.0f, 1.0f};
                }
                
                if (ImGui.colorEdit4("轨道颜色", colorRGBA)) {
                    if (setFloatArrayProperty(track, "colorRGBA", "setColorRGBA", colorRGBA)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 轨道颜色已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("轨道在时间线编辑器中的显示颜色");
                }
                
                UITheme.spacingSmall();
                
                // 可视化偏移（使用 Vector3fPropertyAdapter）
                Object visualOffset = getObjectProperty(track, "visualOffset", "getVisualOffset");
                if (visualOffset != null) {
                    var vector3fAdapter = PropertyAdapterRegistry.getAdapterFor(visualOffset);
                    if (vector3fAdapter != null) {
                        UITheme.label("可视化偏移", UITheme.DarkTheme.TEXT);
                        if (vector3fAdapter.renderProperties(visualOffset)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader("📊 统计信息")) {
                // 关键帧统计
                int keyframeCount = getIntProperty(track, "keyframeCount", "getKeyframeCount", 0);
                UITheme.label("关键帧总数: " + keyframeCount, UITheme.DarkTheme.TEXT);
                
                int enabledCount = getIntProperty(track, "enabledKeyframeCount", "countEnabledKeyframes", 0);
                UITheme.label("已启用关键帧: " + enabledCount, UITheme.DarkTheme.TEXT);
                
                // 持续时间统计
                float start = getFloatProperty(track, "startTime", "getStartTime", 0.0f);
                float end = getFloatProperty(track, "endTime", "getEndTime", 0.0f);
                float duration = Math.max(0, end - start);
                
                UITheme.spacingSmall();
                UITheme.label(String.format("时间范围: %.2f ~ %.2f 秒", start, end), 
                    UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label(String.format("持续时间: %.2f 秒", duration), 
                    UITheme.DarkTheme.TEXT_DISABLED);
                
                // 权重信息
                float weight = getFloatProperty(track, "weight", "getWeight", 1.0f);
                UITheme.spacingSmall();
                UITheme.label(String.format("当前权重: %.0f%%", weight * 100), 
                    UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            // 第一行
            if (ImGui.button("复制轨道", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineTrackPropertyAdapter] 复制轨道");
                // TODO: 实现复制逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建此轨道的副本");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("清空关键帧", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineTrackPropertyAdapter] 清空关键帧");
                // TODO: 实现清空逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("移除轨道上的所有关键帧");
            }
            
            ImGui.sameLine();
            
            boolean locked = getBooleanProperty(track, "locked", "isLocked", false);
            String lockText = locked ? "解锁轨道" : "锁定轨道";
            if (ImGui.button(lockText, 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (setBooleanProperty(track, "locked", "setLocked", !locked)) {
                    modifiedRef[0] = true;
                    LOGGER.info("[TimelineTrackPropertyAdapter] 轨道锁定状态: {}", !locked);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(locked ? "解锁后可以编辑" : "锁定后禁止编辑");
            }
            
            // 第二行
            if (ImGui.button("重置权重", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (setFloatProperty(track, "weight", "setWeight", 1.0f)) {
                    modifiedRef[0] = true;
                    LOGGER.debug("[TimelineTrackPropertyAdapter] 重置权重为 1.0");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将权重重置为 1.0 (100%)");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("重置时间", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                setFloatProperty(track, "startTime", "setStartTime", 0.0f);
                setFloatProperty(track, "endTime", "setEndTime", 5.0f);
                modifiedRef[0] = true;
                LOGGER.debug("[TimelineTrackPropertyAdapter] 重置时间范围");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将时间范围重置为 0~5 秒");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[TimelineTrackPropertyAdapter] 渲染轨道属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    // ========== 反射辅助方法 ==========
    
    private String getStringProperty(Object obj, String fieldName, String getterName, String defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setStringProperty(Object obj, String fieldName, String setterName, String value) {
        try {
            var method = obj.getClass().getMethod(setterName, String.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private boolean getBooleanProperty(Object obj, String fieldName, String getterName, boolean defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Boolean ? (Boolean) result : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Boolean ? (Boolean) result : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setBooleanProperty(Object obj, String fieldName, String setterName, boolean value) {
        try {
            var method = obj.getClass().getMethod(setterName, boolean.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private int getIntProperty(Object obj, String fieldName, String getterName, int defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Integer ? (Integer) result : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Integer ? (Integer) result : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setIntProperty(Object obj, String fieldName, String setterName, int value) {
        try {
            var method = obj.getClass().getMethod(setterName, int.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private float getFloatProperty(Object obj, String fieldName, String getterName, float defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Float) return (Float) result;
            if (result instanceof Double) return ((Double) result).floatValue();
            return defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                if (result instanceof Float) return (Float) result;
                if (result instanceof Double) return ((Double) result).floatValue();
                return defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setFloatProperty(Object obj, String fieldName, String setterName, float value) {
        try {
            var method = obj.getClass().getMethod(setterName, float.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private Object getObjectProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private float[] getFloatArrayProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof float[] ? (float[]) result : null;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof float[] ? (float[]) result : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private boolean setFloatArrayProperty(Object obj, String fieldName, String setterName, float[] value) {
        try {
            var method = obj.getClass().getMethod(setterName, float[].class);
            method.invoke(obj, (Object) value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种轨道类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "时间线轨道属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        // 检查是否是时间线轨道对象
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("track") || 
               className.contains("timeline");
    }
}

