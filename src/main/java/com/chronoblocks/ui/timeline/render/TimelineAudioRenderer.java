package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.audio.TimelineAudioService;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import imgui.ImDrawList;
import imgui.ImGui;

/**
 * 时间线音频轨道渲染器
 * 
 * <p>负责渲染音频轨道上的声音事件点。
 * 音频轨道不像其他轨道那样显示片段，而是显示事件点（标记）。
 * 
 * <p>渲染特点：
 * <ul>
 *   <li>事件点以小圆点或标记的形式显示</li>
 *   <li>悬停时显示事件详情（时间、声音ID）</li>
 *   <li>不同事件类型使用不同颜色</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineAudioRenderer {

    // 事件点大小
    // 需求：点更小，不显示文字
    private static final float EVENT_POINT_SIZE = 3.0f;
    private static final float EVENT_POINT_HOVER_SIZE = 4.5f;
    
    // 事件点颜色（根据事件类型）
    private static final int COLOR_BLOCK_PLACE = TimelineColors.rgba(100, 200, 100, 255);  // 绿色
    private static final int COLOR_BLOCK_BREAK = TimelineColors.rgba(200, 100, 100, 255);  // 红色
    private static final int COLOR_CAMERA_WHOOSH = TimelineColors.rgba(100, 150, 255, 255); // 蓝色
    private static final int COLOR_CUSTOM = TimelineColors.rgba(200, 200, 200, 255);        // 灰色
    
    private final TimelineRenderViewport viewport;
    private com.chronoblocks.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery;
    
    public TimelineAudioRenderer(TimelineRenderViewport viewport) {
        this.viewport = viewport;
    }

    public void setDragStateQuery(com.chronoblocks.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery) {
        this.dragStateQuery = dragStateQuery;
    }
    
    /**
     * 渲染音频轨道上的所有事件点
     * 
     * @param drawList ImGui 绘制列表
     * @param track 音频轨道
     * @param contentX 内容区域起始X坐标
     * @param y 轨道Y坐标
     * @param contentW 内容区域宽度
     */
    public void renderAudioEvents(ImDrawList drawList, TimelineTrack track, 
                                  float contentX, float y, float contentW) {
        if (track == null || track.getType() != TrackType.AUDIO) {
            return;
        }
        
        if (!track.isVisible() || track.isLocked()) {
            return;
        }
        
        // 转换为 AudioTrack
        if (!(track instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack audioTrack)) {
            return;
        }

        // 检查轨道是否静音
        if (audioTrack.isMuted()) {
            return;
        }
        
        float trackCenterY = y + com.chronoblocks.ui.theme.UITheme.Spacing.TIMELINE_TRACK_HEIGHT / 2.0f;
        float pixelsPerSecond = viewport.getPixelsPerSecond();
        float scrollX = viewport.getScrollX();
        
        // 计算可见时间范围
        float startTime = scrollX / pixelsPerSecond;
        float endTime = startTime + (contentW / pixelsPerSecond);
        
        // 获取可见范围内的音频事件
        var events = audioTrack.getEventsInRange(startTime, endTime);
        
        // 获取鼠标位置（用于悬停检测）
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        
        // 绘制所有事件点
        for (AudioEvent event : events) {
            // 跳过未启用的事件
            if (!event.isEnabled()) {
                continue;
            }
            
            float eventTime = event.getTime();
            if (dragStateQuery != null) {
                float previewTime = dragStateQuery.getDraggedAutomationKeyframeTime(event);
                if (previewTime >= 0f) {
                    eventTime = previewTime;
                }
            }
            float eventX = contentX + (eventTime * pixelsPerSecond) - scrollX;
            
            // 检查是否在可见范围内
            if (eventX < contentX || eventX > contentX + contentW) {
                continue;
            }
            
            // 获取事件颜色（根据声音ID推断类型）
            int eventColor = getEventColor(event);
            
            // 检查鼠标悬停
            float pointSize = EVENT_POINT_SIZE;
            float distance = (float) Math.sqrt(
                Math.pow(mouseX - eventX, 2) + Math.pow(mouseY - trackCenterY, 2)
            );
            
            if (distance < EVENT_POINT_HOVER_SIZE) {
                pointSize = EVENT_POINT_HOVER_SIZE;
            }
            
            // 绘制事件点（小圆点）
            drawList.addCircleFilled(eventX, trackCenterY, pointSize, eventColor);
            
            // 绘制外圈（增强可见性）
            // 将 0.5f (50% 透明度) 转换为 0-255 范围：255 * 0.5 = 127
            int outerColor = TimelineColors.adjustAlpha(eventColor, 127);
            drawList.addCircle(eventX, trackCenterY, pointSize + 1.0f, outerColor, 12);
        }
        
        // 需求：不需要文字（包括 tooltip 文本），因此不显示 tooltip
    }
    
    /**
     * 根据声音ID推断事件类型并返回颜色
     */
    private int getEventColor(AudioEvent event) {
        if (event == null) {
            return COLOR_CUSTOM;
        }

        // ✅ 规则：清除轨道上的“清除片段”对应的音频点也使用绿色圆点（与放置一致）
        // 通过 TimelineAudioService 的“事件-片段链接关系”判断来源轨道类型。
        try {
            TimelineClip linked = TimelineAudioService.getInstance().getLinkedClip(event).orElse(null);
            if (linked != null) {
                // ✅ 基于容器类型判断是否为拆除容器（不再依赖轨道类型）
                try {
                    Container c = ContainerManager.getInstance() != null
                            ? ContainerManager.getInstance().getContainer(linked.getBoundContainerId())
                            : null;
                    if (c != null && c.getType() == ContainerType.DEMOLITION) {
                        return COLOR_BLOCK_PLACE;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        
        String soundId = event.getSoundId();
        if (soundId == null) {
            return COLOR_CUSTOM;
        }
        
        // 根据声音ID推断类型
        if (soundId.contains("place")) {
            return COLOR_BLOCK_PLACE;
        } else if (soundId.contains("break")) {
            return COLOR_BLOCK_BREAK;
        } else if (soundId.contains("camera") || soundId.contains("whoosh")) {
            return COLOR_CAMERA_WHOOSH;
        } else {
            return COLOR_CUSTOM;
        }
    }
    
}
