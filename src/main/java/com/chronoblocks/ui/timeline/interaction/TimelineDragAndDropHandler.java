package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.model.*;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 专门处理时间轴上的拖放逻辑
 * 
 * <p>职责：
 * <ul>
 *   <li>处理容器拖放到轨道的逻辑</li>
 *   <li>计算目标轨道索引</li>
 *   <li>验证轨道类型兼容性</li>
 *   <li>计算初始片段时长</li>
 *   <li>应用时间吸附</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineDragAndDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineDragAndDropHandler.class);
    
    private final TimelineModel model;
    private final Runnable onDataChangedCallback;

    public TimelineDragAndDropHandler(TimelineModel model, Runnable onDataChangedCallback) {
        this.model = model;
        this.onDataChangedCallback = onDataChangedCallback;
    }

    /**
     * 处理拖放目标区域
     * 
     * @param trackRegionTop 轨道区域顶部 Y 坐标
     * @param scrollY 垂直滚动偏移
     * @param contentX 内容区域 X 起点
     * @return 目标轨道索引（用于高亮显示），-1 表示无有效目标
     */
    public int handleDragDropTarget(float trackRegionTop, float scrollY, float contentX) {
        int dropTargetIndex = -1;
        
        // 在 invisibleButton 之后设置拖放目标
        if (ImGui.beginDragDropTarget()) {
            float mouseX = ImGui.getMousePosX();
            int targetTrackIndex = getTargetTrackIndex(trackRegionTop, scrollY);
            boolean isValidTrack = checkTrackValidity(targetTrackIndex);
            
            // 设置高亮索引返回给 Panel 渲染
            dropTargetIndex = isValidTrack ? targetTrackIndex : -1;
            
            // 接受载荷
            Object payload = ImGui.acceptDragDropPayload("CHRONOBLOCKS_CONTAINER");
            if (payload != null) {
                String containerId = (String) payload;
                // 拖放结束，重置高亮
                dropTargetIndex = -1; 
                
                if (isValidTrack) {
                    handleContainerDropToTrack(containerId, targetTrackIndex, mouseX, contentX);
                } else {
                    LOGGER.warn("[DragDrop] 轨道类型不匹配或无效，拒绝拖放");
                }
            }
            
            // 也接受其他类型的载荷（如果有的话）
            ImGui.acceptDragDropPayload("CHRONOBLOCKS_ITEM");
            ImGui.endDragDropTarget();
        }
        
        return dropTargetIndex;
    }

    /**
     * 根据鼠标位置计算目标轨道索引
     */
    private int getTargetTrackIndex(float trackRegionTop, float scrollY) {
        float mouseY = ImGui.getMousePosY();
        float totalRowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float relativeY = mouseY - trackRegionTop + scrollY;
        int index = (int) (relativeY / totalRowHeight);
        
        // 边界修正
        if (index < 0) index = 0;
        if (index >= model.getTrackCount()) {
            index = model.getTrackCount() > 0 ? model.getTrackCount() - 1 : 0;
        }
        return index;
    }

    /**
     * 检查轨道是否有效（类型兼容性检查）
     */
    private boolean checkTrackValidity(int index) {
        if (index < 0 || index >= model.getTrackCount()) return false;
        TimelineTrack track = model.getTrackAt(index);
        if (track == null) return false;
        
        TrackType type = track.getType();
        // 允许拖放到这些轨道类型
        return type == TrackType.CONTAINER 
            || type == TrackType.CAMERA 
            || type == TrackType.WEATHER 
            || type == TrackType.LIGHTING 
            || type == TrackType.MARKER;
    }

    /**
     * 处理容器拖放到轨道的具体逻辑
     */
    private void handleContainerDropToTrack(String containerId, int trackIndex, float mouseX, float contentX) {
        try {
            UUID containerUuid = UUID.fromString(containerId);
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            if (container == null) {
                LOGGER.warn("[DragDrop] 容器不存在: {}", containerId);
                return;
            }
            
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[DragDrop] 目标轨道不存在，索引: {}", trackIndex);
                return;
            }
            
            // 计算时间与吸附
            float dropTime = model.screenToTime(mouseX, contentX);
            if (model.isSnappingEnabled()) {
                TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, dropTime, targetTrack, null, 0f);
                dropTime = snap.snappedTime;
            }
            dropTime = Math.max(0f, Math.min(dropTime, model.getDuration() - 1f));
            
            // 计算初始片段时长
            float duration = calculateInitialClipDuration(container);
            
            // 创建容器片段
            TimelineClip clip = new TimelineClip(
                container.getName(), 
                dropTime, 
                dropTime + duration,
                0xFF4A90E2 // 蓝色
            );
            
            // 绑定容器ID和名称
            clip.bindToContainer(containerUuid, container.getName());
            
            // 添加到目标轨道
            targetTrack.addClip(clip);
            
            // ★ 优化：自动选中新创建的片段，使其可以立即被拖动
            model.setSelectedClip(clip);
            
            // 触发数据改变回调
            onDataChangedCallback.run();
            
            LOGGER.info("[DragDrop] Container {} 已添加到轨道 {}，时间: {}s，时长: {}s", 
                container.getName(), targetTrack.getName(), dropTime, duration);
        } catch (Exception e) {
            LOGGER.error("[DragDrop] 处理容器拖放失败", e);
        }
    }

    /**
     * 根据容器中的方块数量计算片段的初始时间长度
     * 
     * <p>规则：
     * <ul>
     *   <li>每10个方块2秒钟</li>
     *   <li>不足10个方块的，最少2秒</li>
     *   <li>最长不超过1分钟（60秒）</li>
     * </ul>
     * 
     * @param container 容器对象
     * @return 计算得到的时间长度（秒）
     */
    private float calculateInitialClipDuration(Container container) {
        if (container == null) {
            return 2.0f; // 默认2秒
        }
        
        // 获取容器中的方块数量（包括子容器的总方块数）
        int blockCount = container.getTotalBlockCount();
        
        // 计算时间长度：每10个方块2秒
        float duration = (blockCount / 10.0f) * 2.0f;
        
        // 最少2秒
        duration = Math.max(2.0f, duration);
        
        // 最长60秒（1分钟）
        duration = Math.min(60.0f, duration);
        
        LOGGER.debug("[DragDrop] 容器 {} 有 {} 个方块，计算初始时长: {}s", 
            container.getName(), blockCount, duration);
        
        return duration;
    }
}