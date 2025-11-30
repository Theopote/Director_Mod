package com.chronoblocks.ui.selection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 选择管理器 - ChronoBlocks Properties System v4.5
 * 
 * <p>管理编辑器中的对象选择状态，支持单选和多选。
 * 当选择发生变化时，通知所有监听器（如属性面板）。
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>单例模式 - 全局唯一的选择状态</li>
 *   <li>事件通知 - 选择变化时通知所有监听器</li>
 *   <li>多选支持 - 支持选择多个对象</li>
 *   <li>类型安全 - 提供泛型方法获取选择对象</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 设置选择
 * Keyframe kf = ...;
 * SelectionManager.get().setCurrentSelection(kf);
 * 
 * // 监听选择变化
 * SelectionManager.get().addSelectionChangedListener(selection -> {
 *     System.out.println("Selection changed: " + selection);
 * });
 * 
 * // 获取选择
 * Object selection = SelectionManager.get().getCurrentSelection();
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public final class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    
    // 单例实例
    private static final SelectionManager INSTANCE = new SelectionManager();
    
    // 当前选择的对象
    private Object currentSelection;
    
    // 多选对象列表
    private final List<Object> multiSelection = new ArrayList<>();
    
    // 选择变化监听器
    private final List<Consumer<Object>> selectionListeners = new ArrayList<>();
    
    // 私有构造函数
    private SelectionManager() {}
    
    /**
     * 获取单例实例
     * 
     * @return SelectionManager 实例
     */
    public static SelectionManager get() {
        return INSTANCE;
    }
    
    /**
     * 设置当前选择的对象
     * 
     * @param selection 要选择的对象，可以为 null（表示取消选择）
     */
    public void setCurrentSelection(Object selection) {
        if (this.currentSelection == selection) {
            return; // 没有变化，不触发事件
        }
        
        Object oldSelection = this.currentSelection;
        this.currentSelection = selection;
        
        LOGGER.debug("[SelectionManager] Selection changed: {} → {}", 
            oldSelection != null ? oldSelection.getClass().getSimpleName() : "null",
            selection != null ? selection.getClass().getSimpleName() : "null");
        
        // 通知所有监听器
        notifySelectionChanged(selection);
    }
    
    /**
     * 获取当前选择的对象
     * 
     * @return 当前选择的对象，如果没有选择则返回 null
     */
    public Object getCurrentSelection() {
        return currentSelection;
    }

    /**
     * 清除当前选择
     */
    public void clearSelection() {
        setCurrentSelection(null);
    }
    
    /**
     * 添加选择变化监听器
     * 
     * @param listener 监听器
     */
    public void addSelectionChangedListener(Consumer<Object> listener) {
        if (listener != null && !selectionListeners.contains(listener)) {
            selectionListeners.add(listener);
            LOGGER.debug("[SelectionManager] Added selection listener: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除选择变化监听器
     * 
     * @param listener 要移除的监听器
     */
    public void removeSelectionChangedListener(Consumer<Object> listener) {
        if (selectionListeners.remove(listener)) {
            LOGGER.debug("[SelectionManager] Removed selection listener: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 通知所有监听器选择已变化
     * 
     * @param selection 新的选择对象
     */
    private void notifySelectionChanged(Object selection) {
        for (Consumer<Object> listener : selectionListeners) {
            try {
                listener.accept(selection);
            } catch (Exception e) {
                LOGGER.error("[SelectionManager] Error notifying listener: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 获取多选对象列表
     * 
     * @return 多选对象列表的副本
     */
    public List<Object> getMultiSelection() {
        return new ArrayList<>(multiSelection);
    }

}