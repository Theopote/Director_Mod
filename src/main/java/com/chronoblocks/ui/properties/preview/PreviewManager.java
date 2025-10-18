package com.chronoblocks.ui.properties.preview;

import com.chronoblocks.ui.properties.command.CommandIntegration;
import com.chronoblocks.ui.properties.command.PropertyModifyCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 🔍 实时预览管理器 v1.0 - ChronoBlocks Properties System
 * <p>
 * 管理属性修改的实时预览功能。
 * 支持预览→确认→提交的三态流程。
 * </p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>开始预览（保存原始值）</li>
 *   <li>更新预览（实时应用新值）</li>
 *   <li>提交预览（生成撤销命令）</li>
 *   <li>取消预览（恢复原始值）</li>
 *   <li>多对象预览管理</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在拖动滑块时
 * if (ImGui.isItemActive()) {
 *     PreviewManager.beginPreview(camera, "fov", 70.0f);
 * }
 * 
 * if (ImGui.dragFloat("FOV", fov, 0.1f)) {
 *     camera.setFov(fov[0]);
 *     PreviewManager.updatePreview(camera, "fov", fov[0]);
 * }
 * 
 * // 松开鼠标后
 * if (ImGui.isItemDeactivatedAfterEdit()) {
 *     PreviewManager.commitPreview(camera, "fov");
 * }
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class PreviewManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewManager.class);
    
    // ==================== 预览状态 ====================
    
    /** 预览状态映射：对象 → 属性预览状态 */
    private static final Map<Object, Map<String, PropertyPreviewState>> previewStates = new HashMap<>();
    
    /** 是否启用实时预览 */
    private static boolean enabled = true;
    
    // ==================== 预览流程 ====================
    
    /**
     * 开始预览
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @param originalValue 原始值
     */
    public static void beginPreview(Object target, String propertyName, Object originalValue) {
        if (!enabled || target == null || propertyName == null) {
            return;
        }
        
        // 获取或创建对象的预览状态映射
        Map<String, PropertyPreviewState> objectStates = previewStates.computeIfAbsent(
            target, k -> new HashMap<>()
        );
        
        // 检查是否已存在预览状态
        if (objectStates.containsKey(propertyName)) {
            LOGGER.debug("[PreviewManager] 预览已存在: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return;
        }
        
        // 创建新的预览状态
        PropertyPreviewState state = new PropertyPreviewState(target, propertyName, originalValue);
        objectStates.put(propertyName, state);
        
        LOGGER.debug("[PreviewManager] 开始预览: {}.{} = {}", 
            target.getClass().getSimpleName(), propertyName, originalValue);
    }
    
    /**
     * 更新预览
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @param newValue 新值
     */
    public static void updatePreview(Object target, String propertyName, Object newValue) {
        if (!enabled || target == null || propertyName == null) {
            return;
        }
        
        Map<String, PropertyPreviewState> objectStates = previewStates.get(target);
        if (objectStates == null) {
            LOGGER.warn("[PreviewManager] 预览未开始: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return;
        }
        
        PropertyPreviewState state = objectStates.get(propertyName);
        if (state == null) {
            LOGGER.warn("[PreviewManager] 预览状态不存在: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return;
        }
        
        // 更新当前值
        state.setCurrentValue(newValue);
        
        LOGGER.trace("[PreviewManager] 更新预览: {}.{} = {}", 
            target.getClass().getSimpleName(), propertyName, newValue);
    }
    
    /**
     * 提交预览（生成撤销命令）
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @return 如果提交成功则返回 true
     */
    public static boolean commitPreview(Object target, String propertyName) {
        return commitPreview(target, propertyName, null);
    }
    
    /**
     * 提交预览（生成撤销命令）
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @param setterName Setter方法名（可选）
     * @return 如果提交成功则返回 true
     */
    public static boolean commitPreview(Object target, String propertyName, String setterName) {
        if (!enabled || target == null || propertyName == null) {
            return false;
        }
        
        Map<String, PropertyPreviewState> objectStates = previewStates.get(target);
        if (objectStates == null) {
            LOGGER.warn("[PreviewManager] 预览未开始: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return false;
        }
        
        PropertyPreviewState state = objectStates.remove(propertyName);
        if (state == null) {
            LOGGER.warn("[PreviewManager] 预览状态不存在: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return false;
        }
        
        // 清理空映射
        if (objectStates.isEmpty()) {
            previewStates.remove(target);
        }
        
        // 检查值是否真的改变了
        if (state.isModified()) {
            // 创建撤销命令
            PropertyModifyCommand command = setterName != null
                ? new PropertyModifyCommand(target, propertyName, setterName, 
                    state.getOriginalValue(), state.getCurrentValue())
                : new PropertyModifyCommand(target, propertyName, 
                    state.getOriginalValue(), state.getCurrentValue());
            
            // 执行命令（已经应用了值，所以标记为已执行）
            CommandIntegration.execute(command);
            
            LOGGER.info("[PreviewManager] 提交预览: {}.{} = {} → {}", 
                target.getClass().getSimpleName(), propertyName, 
                state.getOriginalValue(), state.getCurrentValue());
            
            return true;
        } else {
            LOGGER.debug("[PreviewManager] 预览未修改，跳过提交: {}.{}", 
                target.getClass().getSimpleName(), propertyName);
            return false;
        }
    }
    
    /**
     * 取消预览（恢复原始值）
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @return 如果取消成功则返回 true
     */
    public static boolean cancelPreview(Object target, String propertyName) {
        if (!enabled || target == null || propertyName == null) {
            return false;
        }
        
        Map<String, PropertyPreviewState> objectStates = previewStates.get(target);
        if (objectStates == null) {
            return false;
        }
        
        PropertyPreviewState state = objectStates.remove(propertyName);
        if (state == null) {
            return false;
        }
        
        // 清理空映射
        if (objectStates.isEmpty()) {
            previewStates.remove(target);
        }
        
        // 恢复原始值
        if (state.isModified()) {
            state.restoreOriginalValue();
            
            LOGGER.info("[PreviewManager] 取消预览: {}.{} (已恢复原始值: {})", 
                target.getClass().getSimpleName(), propertyName, state.getOriginalValue());
            
            return true;
        }
        
        return false;
    }
    
    /**
     * 取消对象的所有预览
     * 
     * @param target 目标对象
     */
    public static void cancelAllPreviews(Object target) {
        if (!enabled || target == null) {
            return;
        }
        
        Map<String, PropertyPreviewState> objectStates = previewStates.remove(target);
        if (objectStates == null || objectStates.isEmpty()) {
            return;
        }
        
        // 恢复所有原始值
        for (PropertyPreviewState state : objectStates.values()) {
            if (state.isModified()) {
                state.restoreOriginalValue();
            }
        }
        
        LOGGER.info("[PreviewManager] 取消所有预览: {} ({} 个属性)", 
            target.getClass().getSimpleName(), objectStates.size());
    }
    
    // ==================== 查询方法 ====================
    
    /**
     * 检查是否正在预览
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @return 如果正在预览则返回 true
     */
    public static boolean isPreviewing(Object target, String propertyName) {
        Map<String, PropertyPreviewState> objectStates = previewStates.get(target);
        return objectStates != null && objectStates.containsKey(propertyName);
    }
    
    /**
     * 获取预览状态
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @return 预览状态，如果不存在则返回 null
     */
    public static PropertyPreviewState getPreviewState(Object target, String propertyName) {
        Map<String, PropertyPreviewState> objectStates = previewStates.get(target);
        return objectStates != null ? objectStates.get(propertyName) : null;
    }
    
    /**
     * 获取活动预览数量
     */
    public static int getActivePreviewCount() {
        int count = 0;
        for (Map<String, PropertyPreviewState> objectStates : previewStates.values()) {
            count += objectStates.size();
        }
        return count;
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 启用/禁用实时预览
     */
    public static void setEnabled(boolean enabled) {
        PreviewManager.enabled = enabled;
        LOGGER.info("[PreviewManager] 实时预览已{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 检查实时预览是否启用
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 清空所有预览状态
     */
    public static void clear() {
        int count = getActivePreviewCount();
        previewStates.clear();
        LOGGER.info("[PreviewManager] 已清空所有预览状态: {} 个", count);
    }
    
    /**
     * 获取调试信息
     */
    public static String getDebugInfo() {
        return String.format(
            "PreviewManager [启用: %s, 活动预览: %d, 对象数: %d]",
            enabled,
            getActivePreviewCount(),
            previewStates.size()
        );
    }
}

