package com.chronoblocks.ui.timeline.interaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Timeline 交互管理器
 * 统一管理所有交互状态，避免状态冲突
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineInteractionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineInteractionManager.class);
    
    /**
     * 交互模式枚举
     */
    public enum InteractionMode {
        NONE,                      // 无交互
        DRAG_CLIP,                 // 拖动片段
        RESIZE_CLIP_LEFT,          // 调整片段左边缘
        RESIZE_CLIP_RIGHT,         // 调整片段右边缘
        DRAG_PLAYHEAD,             // 拖动播放头
        DRAG_KEYFRAME,             // 拖动关键帧（Clip 内的关键帧）
        DRAG_AUTOMATION_KEYFRAME,  // 拖动自动化关键帧（轨道级别的关键帧）
        BOX_SELECT,                // 框选
        PAINT_ACTION               // ⭐ 涂抹 Action（Action 区间编辑）
    }
    
    private InteractionMode currentMode = InteractionMode.NONE;
    private Object activeObject = null;  // 当前操作的对象
    private float dragStartMouseX = 0f;
    private float dragStartMouseY = 0f;
    private float dragStartValue = 0f;   // 拖动开始时的值
    private float dragStartValue2 = 0f;  // 第二个值（如 endTime）
    
    /**
     * 开始交互
     */
    public void startInteraction(InteractionMode mode, Object object, float mouseX, float mouseY, 
                                float value1, float value2) {
        if (currentMode != InteractionMode.NONE) {
            LOGGER.warn("[Timeline] 试图在已有交互时开始新交互: {} -> {}", currentMode, mode);
            return;
        }
        
        this.currentMode = mode;
        this.activeObject = object;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        this.dragStartValue = value1;
        this.dragStartValue2 = value2;
        
        LOGGER.debug("[Timeline] 开始交互: {}", mode);
    }
    
    /**
     * 结束交互
     */
    public void endInteraction() {
        if (currentMode != InteractionMode.NONE) {
            LOGGER.debug("[Timeline] 结束交互: {}", currentMode);
        }
        
        this.currentMode = InteractionMode.NONE;
        this.activeObject = null;
        this.dragStartMouseX = 0f;
        this.dragStartMouseY = 0f;
        this.dragStartValue = 0f;
        this.dragStartValue2 = 0f;
    }
    
    /**
     * 取消交互
     */
    public void cancelInteraction() {
        if (currentMode != InteractionMode.NONE) {
            LOGGER.debug("[Timeline] 取消交互: {}", currentMode);
        }
        endInteraction();
    }
    
    /**
     * 静默取消所有交互（不触发任何回调，用于 UI 关闭时清理状态）
     */
    public void cancelAllInteractionsSilently() {
        if (currentMode != InteractionMode.NONE) {
            LOGGER.debug("[Timeline] 静默取消交互: {}", currentMode);
        }
        this.currentMode = InteractionMode.NONE;
        this.activeObject = null;
        this.dragStartMouseX = 0f;
        this.dragStartMouseY = 0f;
        this.dragStartValue = 0f;
        this.dragStartValue2 = 0f;
    }
    
    // ========== Getters ==========
    
    public InteractionMode getCurrentMode() {
        return currentMode;
    }
    
    public boolean isInteracting() {
        return currentMode != InteractionMode.NONE;
    }
    
    public boolean isInteracting(InteractionMode mode) {
        return currentMode == mode;
    }
    
    public Object getActiveObject() {
        return activeObject;
    }
    
    public float getDragStartMouseX() {
        return dragStartMouseX;
    }
    
    public float getDragStartMouseY() {
        return dragStartMouseY;
    }
    
    public float getDragStartValue() {
        return dragStartValue;
    }
    
    public float getDragStartValue2() {
        return dragStartValue2;
    }

    /**
     * 更新当前交互对象（用于拖动过程中替换关键帧等场景）
     */
    public void updateActiveObject(Object object) {
        this.activeObject = object;
    }
    
    /**
     * 计算鼠标偏移量（X轴）
     */
    public float calculateDeltaX(float currentMouseX) {
        return currentMouseX - dragStartMouseX;
    }
    
    /**
     * 计算鼠标偏移量（Y轴）
     */
    public float calculateDeltaY(float currentMouseY) {
        return currentMouseY - dragStartMouseY;
    }
}