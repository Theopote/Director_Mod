package com.chronoblocks.ui.timeline;

import com.chronoblocks.core.timeline.TimelineCommandHistory;
import com.chronoblocks.ui.theme.Icons;

/**
 * 时间线上下文菜单工厂
 * 
 * 职责：创建各种类型的上下文菜单（轨道、片段、关键帧）
 * 使用流式API构建器模式，使菜单创建更加灵活和易于维护
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineContextMenuFactory {
    
    /**
     * 菜单操作回调接口
     */
    public interface MenuCallbacks {
        void onCutClip(TimelineClip clip, TimelineTrack track);
        void onCopyClip(TimelineClip clip);
        void onPasteClip(TimelineTrack track);
        void onDeleteClip();
        void onDuplicateClip();
        void onSetClipLength(TimelineClip clip, float length);
        void onDeleteTrack(TimelineTrack track);
        void onStartEditTrack(TimelineTrack track);
        void onDuplicateTrack(TimelineTrack track);
        void onMoveTrackUp(TimelineTrack track);
        void onMoveTrackDown(TimelineTrack track);
        void onToggleTrackLock(TimelineTrack track);
        void onToggleTrackVisible(TimelineTrack track);
        void onRemoveKeyframe(TimelineClip clip, float keyframeTime);
        void onDataChanged();
    }
    
    private final TimelineCommandHistory commandHistory;
    private final TimelineModel model;
    private final MenuCallbacks callbacks;
    
    public TimelineContextMenuFactory(
            TimelineCommandHistory commandHistory,
            TimelineModel model,
            MenuCallbacks callbacks) {
        this.commandHistory = commandHistory;
        this.model = model;
        this.callbacks = callbacks;
    }
    
    /**
     * 创建片段上下文菜单
     */
    public TimelineContextMenu createForClip(TimelineClip clip) {
        TimelineTrack track = model.findTrackForClip(clip);
        
        return TimelineContextMenuBuilder.forClip(commandHistory)
            .item("剪切", () -> callbacks.onCutClip(clip, track), "Ctrl+X")
            .item("复制", () -> callbacks.onCopyClip(clip), "Ctrl+C")
            .item("粘贴", () -> callbacks.onPasteClip(track), "Ctrl+V")
            .separator()
            .item("复制片段", callbacks::onDuplicateClip, "Ctrl+D")
            .separator()
            .item("删除", callbacks::onDeleteClip, "Del")
            .separator()
            .item("设置时长为 1s", () -> callbacks.onSetClipLength(clip, 1.0f))
            .item("设置时长为 2s", () -> callbacks.onSetClipLength(clip, 2.0f))
            .item("设置时长为 5s", () -> callbacks.onSetClipLength(clip, 5.0f))
            .build();
    }
    
    /**
     * 创建轨道上下文菜单
     */
    public TimelineContextMenu createForTrack(TimelineTrack track) {
        int currentIndex = model.getTracks().indexOf(track);
        boolean canMoveUp = currentIndex > 0;
        boolean canMoveDown = currentIndex < model.getTracks().size() - 1 && currentIndex >= 0;
        
        return TimelineContextMenuBuilder.forTrack(commandHistory)
            // 显示轨道信息（作为标题）
            .disabledItem(track.getType().getIconAndName())
            .disabledItem("\"" + track.getName() + "\"")
            .separator()
            // 编辑操作
            .item(Icons.EDIT + " 重命名", () -> callbacks.onStartEditTrack(track), "F2")
            .item(Icons.COPY + " 复制轨道", () -> callbacks.onDuplicateTrack(track))
            .separator()
            // 移动操作
            .item(Icons.ARROW_UP + " 上移轨道", () -> callbacks.onMoveTrackUp(track), null, canMoveUp)
            .item(Icons.ARROW_DOWN + " 下移轨道", () -> callbacks.onMoveTrackDown(track), null, canMoveDown)
            .separator()
            // 状态切换
            .item(track.isLocked() ? (Icons.UNLOCK + " 解锁") : (Icons.LOCK + " 锁定"), 
                  () -> callbacks.onToggleTrackLock(track))
            .item(track.isVisible() ? (Icons.INVISIBLE + " 隐藏") : (Icons.VISIBLE + " 显示"),
                  () -> callbacks.onToggleTrackVisible(track))
            .separator()
            // 删除操作
            .item(Icons.DELETE + " 删除轨道", () -> callbacks.onDeleteTrack(track))
            .build();
    }
    
    /**
     * 创建关键帧上下文菜单
     */
    public TimelineContextMenu createForKeyframe(TimelineClip clip, float keyframeTime) {
        return TimelineContextMenuBuilder.createStandardKeyframeMenu(
            commandHistory,
            keyframeTime,
            () -> callbacks.onRemoveKeyframe(clip, keyframeTime)
        );
    }
    
    /**
     * 创建"添加轨道"类型选择菜单
     */
    public static TrackType[] getAvailableTrackTypes() {
        return TrackType.values();
    }
}