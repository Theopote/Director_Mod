package com.director.ui.timeline.render;

import com.director.ui.timeline.interaction.TimelineHitResult;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线渲染命中检测（Director v9.0）
 * 
 * <p>基于渲染器坐标系统进行像素级命中检测。
 * 所有命中检测都通过统一的坐标转换 API。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>像素级精确检测</li>
 *   <li>支持可见区域裁剪</li>
 *   <li>统一的坐标转换</li>
 *   <li>高效的检测算法</li>
 * </ul>
 * 
 * @author Director Team
 * @version 9.0
 */
public class TimelineRenderHitTest {

    private static final Logger logger = LoggerFactory.getLogger(TimelineRenderHitTest.class);

    // ★ 优化：从 TimelineClipRenderer 导入常量，避免重复定义
    private static final float KEYFRAME_HIT_RADIUS = TimelineClipRenderer.KEYFRAME_HIT_RADIUS;
    private static final float RESIZE_HANDLE_WIDTH = TimelineClipRenderer.getResizeHandleWidth();

    private final TimelineRenderMath math;
    private final TimelineRenderViewport viewport;
    private final TimelineModel model;

    public TimelineRenderHitTest(TimelineRenderMath math, 
                                 TimelineRenderViewport viewport,
                                 TimelineModel model) {
        this.math = math;
        this.viewport = viewport;
        this.model = model;
    }

    /**
     * 执行命中检测
     * 
     * @param mouseX 鼠标 X 坐标（屏幕坐标）
     * @param mouseY 鼠标 Y 坐标（屏幕坐标）
     * @return 命中结果
     */
    public TimelineHitResult hit(float mouseX, float mouseY) {
        // 1. 检查是否在视口内
        if (!viewport.contains(mouseX, mouseY)) {
            return TimelineHitResult.none();
        }

        // 2. 检查是否在轨道头区域
        float headerStartX = viewport.getViewX();
        float headerEndX = viewport.getContentX();
        if (mouseX >= headerStartX && mouseX < headerEndX) {
            return hitTrackHeader(mouseX, mouseY);
        }

        // 3. 检查是否在内容区域
        float contentX = viewport.getContentX();
        float contentW = viewport.getContentWidth();
        if (mouseX >= contentX && mouseX <= contentX + contentW) {
            return hitContentArea(mouseX, mouseY);
        }

        return TimelineHitResult.none();
    }

    /**
     * 检测轨道头区域命中
     * 
     * <p>★ 修复：使用 getTrackAreaY() 而不是 getViewY()，因为轨道区域实际上从头部下方开始。
     */
    private TimelineHitResult hitTrackHeader(float mouseX, float mouseY) {
        // ★ 关键修复：轨道区域从头部下方开始，所以 relativeY 应该相对于轨道区域起始位置
        float trackAreaY = viewport.getTrackAreaY();
        float relativeY = mouseY - trackAreaY;
        int trackIndex = math.yToTrackIndex(relativeY);
        
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            return TimelineHitResult.none();
        }

        TimelineTrack track = model.getTrackAt(trackIndex);
        if (track == null) {
            return TimelineHitResult.none();
        }

        // 检查 Y 坐标是否在轨道范围内
        // ★ 修复：轨道顶部位置也应该相对于轨道区域计算
        float trackTopY = trackAreaY + math.trackIndexToY(trackIndex);
        float trackBottomY = trackTopY + viewport.getTrackHeight();
        
        if (mouseY >= trackTopY && mouseY <= trackBottomY) {
            return TimelineHitResult.track(track, trackIndex);
        }

        return TimelineHitResult.none();
    }

    /**
     * 检测内容区域命中（片段、关键帧）
     * 
     * <p>★ 修复：使用 getTrackAreaY() 而不是 getViewY()，因为轨道区域实际上从头部下方开始。
     */
    private TimelineHitResult hitContentArea(float mouseX, float mouseY) {
        // ★ 关键修复：轨道区域从头部下方开始，所以 relativeY 应该相对于轨道区域起始位置
        float trackAreaY = viewport.getTrackAreaY();
        float relativeY = mouseY - trackAreaY;
        int trackIndex = math.yToTrackIndex(relativeY);
        
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            // 超出轨道区域，返回空白区域
            float time = math.xToTime(mouseX);
            return TimelineHitResult.empty(time);
        }

        TimelineTrack track = model.getTrackAt(trackIndex);
        if (track == null) {
            float time = math.xToTime(mouseX);
            return TimelineHitResult.empty(time);
        }

        // 检查 Y 坐标是否在轨道范围内
        // ★ 修复：轨道顶部位置也应该相对于轨道区域计算
        float trackTopY = trackAreaY + math.trackIndexToY(trackIndex);
        float trackBottomY = trackTopY + viewport.getTrackHeight();
        
        if (mouseY < trackTopY || mouseY > trackBottomY) {
            float time = math.xToTime(mouseX);
            return TimelineHitResult.empty(time);
        }

        // 计算时间
        float timeAtMouse = math.xToTime(mouseX);

        // 检测片段和关键帧
        for (TimelineClip clip : track.getClips()) {
            float clipStartX = math.timeToX(clip.getStartTime());
            float clipEndX = math.timeToX(clip.getEndTime());

            // 检查是否在片段范围内
            if (mouseX >= clipStartX && mouseX <= clipEndX) {
                // 检查是否命中关键帧（优先级高于片段）
                TimelineHitResult keyframeHit = hitKeyframe(clip, mouseX, track, trackIndex);
                if (keyframeHit.type() == TimelineHitResult.HitType.KEYFRAME) {
                    return keyframeHit;
                }

                // 检查是否命中 resize handle
                float leftHandleX2 = clipStartX + RESIZE_HANDLE_WIDTH;
                float rightHandleX1 = clipEndX - RESIZE_HANDLE_WIDTH;

                if (mouseX >= clipStartX && mouseX <= leftHandleX2) {
                    // 命中左边缘
                    return TimelineHitResult.clip(track, clip, timeAtMouse, trackIndex);
                }
                if (mouseX >= rightHandleX1 && mouseX <= clipEndX) {
                    // 命中右边缘
                    return TimelineHitResult.clip(track, clip, timeAtMouse, trackIndex);
                }

                // 命中片段主体
                return TimelineHitResult.clip(track, clip, timeAtMouse, trackIndex);
            }
        }

        // 轨道正文区域但未命中任何片段 → 空白区域
        return TimelineHitResult.empty(timeAtMouse);
    }

    /**
     * 检测关键帧命中
     * 
     * <p>★ 性能优化：使用可见时间范围过滤关键帧，避免检查不可见的关键帧
     */
    private TimelineHitResult hitKeyframe(TimelineClip clip, float mouseX, 
                                         TimelineTrack track, int trackIndex) {
        if (clip == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("hitKeyframe called with null clip");
            }
            return TimelineHitResult.none();
        }
        
        List<Float> keyframeTimes = clip.getKeyframeTimes();
        if (keyframeTimes == null || keyframeTimes.isEmpty()) {
            return TimelineHitResult.none();
        }
        
        // ★ 性能优化：获取可见时间范围，只检查可见区域内的关键帧
        float[] visibleTimeRange = math.getVisibleTimeRange();
        float visibleStart = visibleTimeRange[0] - (KEYFRAME_HIT_RADIUS / math.getPixelsPerSecond());
        float visibleEnd = visibleTimeRange[1] + (KEYFRAME_HIT_RADIUS / math.getPixelsPerSecond());
        
        // ⭐⭐ 修复：优先使用 ClipKeyframe 对象列表
        java.util.List<com.director.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = 
                clip.getClipKeyframes();
        if (clipKeyframes != null && !clipKeyframes.isEmpty()) {
            for (com.director.ui.timeline.model.clip.ClipKeyframe kf : clipKeyframes) {
                if (kf == null) continue;
                
                float kfTime = kf.getTime();
                // ★ 优化：早期跳过不可见的关键帧
                if (kfTime < visibleStart || kfTime > visibleEnd) {
                    continue;
                }
                
                float keyX = math.timeToX(kfTime);
                // ★ 优化：使用平方距离比较，避免开方运算
                float dx = mouseX - keyX;
                if (Math.abs(dx) <= KEYFRAME_HIT_RADIUS) {
                    return TimelineHitResult.keyframe(track, clip, kf, trackIndex);
                }
            }
        }
        
        // 回退到旧的 Float 关键帧系统（向后兼容）
        int keyframeIdx = 0;
        for (Float kfTime : keyframeTimes) {
            if (kfTime == null) continue;
            
            // ★ 优化：早期跳过不可见的关键帧
            if (kfTime < visibleStart || kfTime > visibleEnd) {
                keyframeIdx++;
                continue;
            }
            
            float keyX = math.timeToX(kfTime);
            // ★ 优化：使用平方距离比较，避免开方运算
            float dx = mouseX - keyX;
            if (Math.abs(dx) <= KEYFRAME_HIT_RADIUS) {
                // 尝试在 ClipKeyframe 列表中查找匹配的对象
                final float EPS = 0.001f;
                com.director.ui.timeline.model.clip.ClipKeyframe found = clip.findKeyframeAt(kfTime, EPS);
                if (found != null) {
                    return TimelineHitResult.keyframe(track, clip, found, trackIndex);
                }
                // 如果没有找到，创建一个临时对象（向后兼容）
                com.director.ui.timeline.model.clip.ClipKeyframe temp = 
                        new com.director.ui.timeline.model.clip.ClipKeyframe(kfTime);
                return TimelineHitResult.keyframe(track, clip, temp, trackIndex);
            }
            keyframeIdx++;
        }
        
        return TimelineHitResult.none();
    }
    
    // ========== 边界矩形查询方法（从 TimelineRenderer 迁移） ==========
    
    /**
     * 轨道头部边界矩形（用于命中检测）
     */
    public record Rect(float x1, float y1, float x2, float y2) {
        public boolean contains(float x, float y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }
    
    /**
     * 关键帧圆形区域（用于命中检测）
     */
    public record Circle(float x, float y, float radius) {
        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy <= radius * radius;
        }
    }
    
    /**
     * 获取指定轨道的头部边界矩形（完整版本，指定参数）
     * 
     * <p>从 TimelineRenderer 迁移的方法，用于边界矩形查询
     * 
     * @param trackIndex 轨道索引
     * @param contentX 轨道内容区域的屏幕 X 起点（== winX + leftLabelWidth）
     * @param trackAreaTop 轨道区域顶部 Y 坐标
     * @param scrollY 垂直滚动偏移
     * @param leftLabelWidth 左侧标签宽度
     * @return 轨道头部边界矩形
     */
    public Rect getTrackHeaderBounds(int trackIndex, float contentX, float trackAreaTop, 
                                     float scrollY, float leftLabelWidth) {
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            return new Rect(0, 0, 0, 0);
        }
        
        float rowHeight = viewport.getTrackHeight() + viewport.getTrackGap();
        float x1 = contentX - leftLabelWidth;
        float y1 = trackAreaTop + (trackIndex * rowHeight - scrollY);
        float y2 = y1 + viewport.getTrackHeight();
        
        return new Rect(x1, y1, contentX, y2);
    }
    
    /**
     * 获取 Clip 的边界矩形
     * 
     * <p>从 TimelineRenderer 迁移的方法，用于边界矩形查询
     * 
     * @param track 轨道
     * @param clip 片段
     * @param trackIndex 轨道索引
     * @param trackAreaTop 轨道区域顶部 Y 坐标
     * @param scrollY 垂直滚动偏移
     * @return Clip 边界矩形
     */
    public Rect getClipRect(TimelineTrack track, TimelineClip clip, int trackIndex, 
                           float trackAreaTop, float scrollY) {
        if (track == null || clip == null || trackIndex < 0) {
            return new Rect(0, 0, 0, 0);
        }
        
        float x1 = math.timeToX(clip.getStartTime());
        float x2 = math.timeToX(clip.getEndTime());
        float rowHeight = viewport.getTrackHeight() + viewport.getTrackGap();
        float y1 = trackAreaTop + (trackIndex * rowHeight - scrollY);
        float y2 = y1 + viewport.getTrackHeight();
        
        return new Rect(x1, y1, x2, y2);
    }
    
    /**
     * 获取关键帧的圆形区域
     * 
     * <p>从 TimelineRenderer 迁移的方法，用于边界圆形查询
     * 
     * @param track 轨道
     * @param clip 片段
     * @param keyTime 关键帧时间
     * @param trackAreaTop 轨道区域顶部 Y 坐标
     * @param scrollY 垂直滚动偏移
     * @return 关键帧圆形区域
     */
    public Circle getKeyframeCircle(TimelineTrack track, TimelineClip clip, float keyTime, 
                                   float trackAreaTop, float scrollY) {
        if (track == null || clip == null) {
            return new Circle(0, 0, 0);
        }
        
        float x = math.timeToX(keyTime);
        
        // 查找轨道索引
        int trackIndex = -1;
        for (int i = 0; i < model.getTrackCount(); i++) {
            if (model.getTrackAt(i) == track) {
                trackIndex = i;
                break;
            }
        }
        if (trackIndex < 0) {
            return new Circle(0, 0, 0);
        }
        
        float rowHeight = viewport.getTrackHeight() + viewport.getTrackGap();
        float y = trackAreaTop + (trackIndex * rowHeight - scrollY) + viewport.getTrackHeight() / 2.0f;
        
        return new Circle(x, y, KEYFRAME_HIT_RADIUS);
    }
}