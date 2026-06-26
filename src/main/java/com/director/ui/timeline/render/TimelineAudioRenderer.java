package com.director.ui.timeline.render;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.timeline.model.TimelineClip;
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
 * @author Director Team
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
    private com.director.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery;
    
    public TimelineAudioRenderer(TimelineRenderViewport viewport) {
        this.viewport = viewport;
    }

    public void setDragStateQuery(com.director.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery) {
        this.dragStateQuery = dragStateQuery;
    }
    
    /**
     * 渲染音频轨道上的所有事件点
     *
     * ⭐ 性能优化：当事件数量过多时（>1000），使用连续波形线代替单个圆点
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
        if (!(track instanceof com.director.timeline.model.audio.AudioTrack audioTrack)) {
            return;
        }

        // 检查轨道是否静音
        if (audioTrack.isMuted()) {
            return;
        }

        float trackCenterY = y + com.director.ui.theme.UITheme.Spacing.TIMELINE_TRACK_HEIGHT / 2.0f;
        float pixelsPerSecond = viewport.getPixelsPerSecond();
        float scrollX = viewport.getScrollX();

        // 计算可见时间范围
        float startTime = scrollX / pixelsPerSecond;
        float endTime = startTime + (contentW / pixelsPerSecond);

        // 获取可见范围内的音频事件
        var events = audioTrack.getEventsInRange(startTime, endTime);

        // ⭐ 性能关键优化：根据事件数量选择渲染模式
        if (events.size() > 1000) {
            // 大量事件：使用连续波形线渲染
            renderAudioEventsAsWaveform(drawList, events, contentX, trackCenterY, contentW, pixelsPerSecond, scrollX);
        } else {
            // 少量事件：使用传统圆点渲染
            renderAudioEventsAsDots(drawList, events, contentX, trackCenterY, contentW, pixelsPerSecond, scrollX);
        }
    }

    /**
     * ⭐ 新方法：将大量音频事件渲染为连续波形线（高性能）
     *
     * 不绘制单个圆点，而是将事件密度转换为波形高度，用线段连接
     */
    private void renderAudioEventsAsWaveform(ImDrawList drawList, java.util.List<AudioEvent> events,
                                             float contentX, float trackCenterY, float contentW,
                                             float pixelsPerSecond, float scrollX) {
        if (events.isEmpty()) {
            return;
        }

        // 将时间轴分成若干段（每段2-5像素），统计每段的事件密度
        float segmentWidth = 3.0f;  // 每段3像素
        int segmentCount = (int) Math.ceil(contentW / segmentWidth);
        float[] densities = new float[segmentCount];

        // 统计每段的事件密度
        for (AudioEvent event : events) {
            if (!event.isEnabled()) {
                continue;
            }

            float eventTime = event.getTime();
            float eventX = contentX + (eventTime * pixelsPerSecond) - scrollX;

            // 计算事件属于哪个段
            int segmentIndex = (int) ((eventX - contentX) / segmentWidth);
            if (segmentIndex >= 0 && segmentIndex < segmentCount) {
                densities[segmentIndex] += 1.0f;
            }
        }

        // 归一化密度（找到最大值）
        float maxDensity = 1.0f;
        for (float density : densities) {
            if (density > maxDensity) {
                maxDensity = density;
            }
        }

        // 绘制波形线
        float maxWaveHeight = 15.0f;  // 最大波形高度（像素）
        int waveColor = TimelineColors.rgba(100, 200, 100, 200);  // 半透明绿色

        // 用连续的线段绘制波形
        for (int i = 0; i < segmentCount - 1; i++) {
            float x1 = contentX + i * segmentWidth;
            float x2 = contentX + (i + 1) * segmentWidth;

            // 根据密度计算波形高度
            float height1 = (densities[i] / maxDensity) * maxWaveHeight;
            float height2 = (densities[i + 1] / maxDensity) * maxWaveHeight;

            // 绘制上半波形
            float y1Top = trackCenterY - height1;
            float y2Top = trackCenterY - height2;
            drawList.addLine(x1, y1Top, x2, y2Top, waveColor, 2.0f);

            // 绘制下半波形（镜像）
            float y1Bottom = trackCenterY + height1;
            float y2Bottom = trackCenterY + height2;
            drawList.addLine(x1, y1Bottom, x2, y2Bottom, waveColor, 2.0f);

            // 填充中间区域（可选，创建实心效果）
            if (height1 > 0 || height2 > 0) {
                int fillColor = TimelineColors.rgba(100, 200, 100, 80);  // 更透明的填充
                drawList.addRectFilled(x1, y1Top, x2, y1Bottom, fillColor);
            }
        }

        // 绘制中心线（参考线）
        int centerLineColor = TimelineColors.rgba(150, 150, 150, 100);
        drawList.addLine(contentX, trackCenterY, contentX + contentW, trackCenterY, centerLineColor, 1.0f);
    }

    /**
     * 传统圆点渲染方法（用于少量事件）
     */
    private void renderAudioEventsAsDots(ImDrawList drawList, java.util.List<AudioEvent> events,
                                         float contentX, float trackCenterY, float contentW,
                                         float pixelsPerSecond, float scrollX) {
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
            int outerColor = TimelineColors.adjustAlpha(eventColor, 127);
            drawList.addCircle(eventX, trackCenterY, pointSize + 1.0f, outerColor, 12);
        }
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
                    Container c = DirectorServices.get().containers().getContainer(linked.getBoundContainerId());
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
