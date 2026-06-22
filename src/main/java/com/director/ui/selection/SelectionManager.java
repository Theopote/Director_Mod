package com.director.ui.selection;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.clip.ClipKeyframe;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.selection.TimelineKeyframeMultiSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 选择管理器 - Director Properties System v5.0
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
 *   <li>SelectionType 支持 - 明确区分不同的选择语义（v5.0 新增）</li>
 * </ul>
 * 
 * <h3>SelectionType 系统（v5.0）</h3>
 * <ul>
 *   <li>PROJECT - 项目级资产（环境、全局设置）</li>
 *   <li>ACTOR - 演员（Cast），Project 级资产</li>
 *   <li>TRACK - 时间线轨道（TimelineTrack）</li>
 *   <li>CLIP - 表演片段（EntityClip）</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 推荐：通过 DirectorServices 获取
 * DirectorServices.get().uiSelection().selectTrack(track);
 *
 * // 遗留（已废弃）
 * SelectionManager.get().setCurrentSelection(kf);
 * }</pre>
 * 
 * @version 5.0
 * @since 2025-10-15
 */
public final class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    
    // 单例实例
    private static SelectionManager instance;
    
    // =========================
    // SelectionType 系统（v5.0 新增）
    // =========================
    
    /** 当前选择类型 */
    private SelectionType selectionType = SelectionType.PROJECT;
    
    /** 当前选择的有效载荷（根据类型不同而不同） */
    private Object selectionPayload;
    
    // =========================
    // 向后兼容字段（保留）
    // =========================
    
    /** 当前选择的对象（向后兼容，映射到 selectionPayload） */
    private Object currentSelection;
    
    // 多选对象列表
    private final List<Object> multiSelection = new ArrayList<>();
    
    // 选择变化监听器
    private final List<Consumer<Object>> selectionListeners = new ArrayList<>();
    
    // 私有构造函数
    private SelectionManager() {}

    /**
     * 在模组启动时显式初始化（幂等）。由 {@link com.director.core.DirectorServices} 统一调用。
     */
    public static void initialize() {
        if (instance != null) {
            return;
        }
        synchronized (SelectionManager.class) {
            if (instance == null) {
                instance = new SelectionManager();
            }
        }
    }
    
    /**
     * @throws IllegalStateException 若尚未初始化
     * @deprecated 新代码请使用 {@link com.director.core.DirectorServices#get()}{@code .uiSelection()}
     */
    @Deprecated
    public static SelectionManager get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "[UiSelectionManager] Not initialized — call DirectorServices.initialize() during mod startup");
        }
        return instance;
    }

    /** @deprecated 请使用 {@link com.director.core.DirectorServices#get()}{@code .uiSelection()} */
    @Deprecated
    public static SelectionManager getInstance() {
        return get();
    }
    
    // =========================
    // SelectionType API（v5.0 新增）
    // =========================
    
    /**
     * 设置选择（带类型）
     * 
     * @param type 选择类型
     * @param payload 选择的有效载荷
     */
    public void setSelection(SelectionType type, Object payload) {
        if (type == null) {
            type = SelectionType.PROJECT;
        }
        
        // 检查是否有实际变化
        if (this.selectionType == type && this.selectionPayload == payload) {
            return; // 没有变化，不触发事件
        }
        
        SelectionType oldType = this.selectionType;
        Object oldPayload = this.selectionPayload;
        
        this.selectionType = type;
        this.selectionPayload = payload;
        this.currentSelection = payload; // 向后兼容
        
        LOGGER.debug("[SelectionManager] Selection changed: {} ({}) → {} ({})", 
            oldType, oldPayload != null ? oldPayload.getClass().getSimpleName() : "null",
            type, payload != null ? payload.getClass().getSimpleName() : "null");
        
        // 通知所有监听器
        notifySelectionChanged(payload);
    }
    
    /**
     * 获取当前选择类型
     * 
     * @return 当前选择类型
     */
    public SelectionType getSelectionType() {
        return selectionType;
    }
    
    /**
     * 获取当前选择的有效载荷
     * 
     * @return 当前选择的有效载荷，如果没有选择则返回 null
     */
    public Object getSelectionPayload() {
        return selectionPayload;
    }
    
    // =========================
    // Convenience helpers（v5.0 新增）
    // =========================
    
    /**
     * 选择 Actor
     * 
     * @param actorId Actor ID（UUID）
     */
    public void selectActor(UUID actorId) {
        setSelection(SelectionType.ACTOR, actorId);
    }
    
    /**
     * 获取选中的 Actor ID
     * 
     * @return Actor ID，如果未选中 Actor 则返回 null
     */
    public UUID getSelectedActorId() {
        return selectionType == SelectionType.ACTOR && selectionPayload instanceof UUID
                ? (UUID) selectionPayload
                : null;
    }
    
    /**
     * 选择 Track
     * 
     * @param track TimelineTrack
     */
    public void selectTrack(TimelineTrack track) {
        setSelection(SelectionType.TRACK, track);
    }
    
    /**
     * 获取选中的 Track
     * 
     * @return TimelineTrack，如果未选中 Track 则返回 null
     */
    public TimelineTrack getSelectedTrack() {
        return selectionType == SelectionType.TRACK && selectionPayload instanceof TimelineTrack
                ? (TimelineTrack) selectionPayload
                : null;
    }
    
    /**
     * 选择 Clip
     * 
     * @param clip EntityClip
     */
    public void selectClip(EntityClip clip) {
        setSelection(SelectionType.CLIP, clip);
    }
    
    /**
     * 获取选中的 Clip
     * 
     * @return EntityClip，如果未选中 Clip 则返回 null
     */
    public Object getSelectedClip() {
        return selectionType == SelectionType.CLIP ? selectionPayload : null;
    }
    
    // =========================
    // 向后兼容 API（保留）
    // =========================
    
    /**
     * 设置当前选择的对象（向后兼容）
     * <p>此方法会自动推断 SelectionType：
     * <ul>
     *   <li>Actor → ACTOR</li>
     *   <li>TimelineTrack → TRACK</li>
     *   <li>EntityClip → CLIP</li>
     *   <li>其他 → PROJECT</li>
     * </ul>
     * 
     * @param selection 要选择的对象，可以为 null（表示取消选择）
     */
    public void setCurrentSelection(Object selection) {
        if (selection == null) {
            clearSelection();
            return;
        }
        
        // 自动推断 SelectionType
        SelectionType inferredType = inferSelectionType(selection);
        setSelection(inferredType, selection);
    }
    
    /**
     * 获取当前选择的对象（向后兼容）
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
        setSelection(SelectionType.PROJECT, null);
    }
    
    /**
     * 根据对象类型推断 SelectionType
     * 
     * @param obj 对象
     * @return 推断的 SelectionType
     */
    private SelectionType inferSelectionType(Object obj) {
        switch (obj) {
            case null -> {
                return SelectionType.PROJECT;
            }


            // 检查是否是 Actor（通过 UUID 或 Actor 对象）
            case UUID ignored -> {
                // 可能是 Actor ID，但无法确定，默认返回 PROJECT
                // 实际使用时应该明确调用 selectActor()
                return SelectionType.PROJECT;
                // 可能是 Actor ID，但无法确定，默认返回 PROJECT
                // 实际使用时应该明确调用 selectActor()
            }


            // 检查是否是 TimelineTrack
            case TimelineTrack ignored -> {
                return SelectionType.TRACK;
            }
            default -> {
            }
        }

        // 检查是否是 EntityClip
        if (obj instanceof TimelineClip || obj instanceof EntityClip) {
            return SelectionType.CLIP;
        }

        if (obj instanceof ClipKeyframe
                || obj instanceof TimelineKeyframeMultiSelection
                || obj instanceof com.director.core.animation.container.Keyframe
                || obj instanceof TimelineSelectable) {
            return SelectionType.KEYFRAME;
        }
        
        // 检查是否是 Actor（通过类名，避免直接 import）
        String className = obj.getClass().getName();
        if (className.equals("com.director.actor.Actor")) {
            // 如果是 Actor 对象，提取其 actorId
            try {
                java.lang.reflect.Method getIdMethod = obj.getClass().getMethod("getActorId");
                UUID actorId = (UUID) getIdMethod.invoke(obj);
                return SelectionType.ACTOR;
            } catch (Exception e) {
                LOGGER.warn("[SelectionManager] 无法从 Actor 对象提取 actorId", e);
            }
        }
        
        // 默认返回 PROJECT
        return SelectionType.PROJECT;
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