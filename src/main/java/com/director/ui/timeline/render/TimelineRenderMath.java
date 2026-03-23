package com.director.ui.timeline.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线渲染数学工具（Director v9.0）
 * 
 * <p>负责所有时间与像素之间的精确转换。
 * 这是渲染器的数学核心，所有坐标转换都通过此类。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>支持无限缩放（0.001× 到 5000×）</li>
 *   <li>支持负时间和无限延展</li>
 *   <li>精确的像素级对齐</li>
 *   <li>可见区域裁剪</li>
 * </ul>
 * 
 * @author Director Team
 * @version 9.0
 */
public class TimelineRenderMath {

    private static final Logger logger = LoggerFactory.getLogger(TimelineRenderMath.class);

    private final TimelineRenderViewport viewport;
    
    // ★ 性能优化：缓存 rowHeight 计算，避免重复计算
    private float cachedRowHeight = -1f;
    private float cachedTrackHeight = -1f;
    private float cachedTrackGap = -1f;

    public TimelineRenderMath(TimelineRenderViewport viewport) {
        this.viewport = viewport;
        // 初始化缓存
        invalidateRowHeightCache();
    }

    // ========== 核心坐标转换 ==========

    /**
     * 时间 → 屏幕 X 坐标
     * 
     * <p>公式：x = viewX + leftLabelWidth + (time * pps) - scrollX
     * 
     * @param time 时间（秒）
     * @return 屏幕 X 坐标（像素）
     */
    public float timeToX(float time) {
        float pps = viewport.getPixelsPerSecond();
        float contentX = viewport.getContentX();
        return contentX + (time * pps) - viewport.getScrollX();
    }

    /**
     * 屏幕 X 坐标 → 时间
     * 
     * <p>公式：time = (x - viewX - leftLabelWidth + scrollX) / pps
     * 
     * @param x 屏幕 X 坐标（像素）
     * @return 时间（秒）
     */
    public float xToTime(float x) {
        float pps = viewport.getPixelsPerSecond();
        float contentX = viewport.getContentX();
        return (x - contentX + viewport.getScrollX()) / pps;
    }

    /**
     * 时间跨度 → 像素宽度
     * 
     * @param timeSpan 时间跨度（秒）
     * @return 像素宽度
     */
    public float timeToPixels(float timeSpan) {
        return timeSpan * viewport.getPixelsPerSecond();
    }

    /**
     * 像素宽度 → 时间跨度
     * 
     * @param pixels 像素宽度
     * @return 时间跨度（秒）
     */
    public float pixelsToTime(float pixels) {
        float pps = viewport.getPixelsPerSecond();
        if (pps <= 0.0001f) return 0f;
        return pixels / pps;
    }

    // ========== 轨道坐标转换 ==========

    /**
     * 轨道索引 → 屏幕 Y 坐标（相对于视口顶部）
     * 
     * <p>公式：y = trackIndex * (trackHeight + trackGap) - scrollY
     * 
     * @param trackIndex 轨道索引
     * @return 屏幕 Y 坐标（相对于视口顶部，已考虑 scrollY）
     */
    public float trackIndexToY(int trackIndex) {
        float rowHeight = getRowHeight();
        return trackIndex * rowHeight - viewport.getScrollY();
    }

    /**
     * 屏幕 Y 坐标 → 轨道索引
     * 
     * <p>公式：trackIndex = (y + scrollY) / (trackHeight + trackGap)
     * 
     * <p>★ 优化：改进负值处理，考虑浮点精度问题
     * 
     * @param y 屏幕 Y 坐标（相对于视口顶部）
     * @return 轨道索引，如果无效返回 -1
     */
    public int yToTrackIndex(float y) {
        float rowHeight = getRowHeight();
        if (rowHeight <= 0f) return -1;
        
        float scrollY = viewport.getScrollY();
        float relativeY = y + scrollY;
        
        // ★ 优化：改进负值处理，考虑浮点精度问题
        // 如果 relativeY 只是稍微小于 0（可能是浮点精度问题），且 scrollY 也是 0 或很小，可能是第一行轨道
        if (relativeY < 0) {
            if (relativeY > -0.5f && scrollY <= 0.5f) {
                relativeY = 0f; // 修正为 0，确保第一行轨道能被检测到
            } else {
                return -1;
            }
        }

        return (int) (relativeY / rowHeight);
    }
    
    /**
     * 获取行高（缓存计算，避免重复计算）
     * 
     * <p>★ 性能优化：缓存 rowHeight 值，只有当 trackHeight 或 trackGap 改变时才重新计算
     * 
     * @return 行高（trackHeight + trackGap）
     */
    private float getRowHeight() {
        float currentTrackHeight = viewport.getTrackHeight();
        float currentTrackGap = viewport.getTrackGap();
        
        // 检查缓存是否有效
        if (cachedRowHeight < 0 || cachedTrackHeight != currentTrackHeight || cachedTrackGap != currentTrackGap) {
            cachedTrackHeight = currentTrackHeight;
            cachedTrackGap = currentTrackGap;
            cachedRowHeight = currentTrackHeight + currentTrackGap;
            
            if (logger.isTraceEnabled()) {
                logger.trace("RowHeight cache updated: {} (trackHeight={}, trackGap={})", 
                    cachedRowHeight, currentTrackHeight, currentTrackGap);
            }
        }
        
        return cachedRowHeight;
    }
    
    /**
     * 使 rowHeight 缓存失效（当 trackHeight 或 trackGap 改变时调用）
     */
    public void invalidateRowHeightCache() {
        cachedRowHeight = -1f;
        cachedTrackHeight = -1f;
        cachedTrackGap = -1f;
    }

    // ========== 可见区域裁剪 ==========

    /**
     * 检查时间点是否在可见区域内
     * 
     * @param time 时间（秒）
     * @return 是否可见
     */
    public boolean isTimeVisible(float time) {
        float x = timeToX(time);
        float contentX = viewport.getContentX();
        float contentW = viewport.getContentWidth();
        return x >= contentX && x <= contentX + contentW;
    }

    /**
     * 检查轨道是否在可见区域内
     * 
     * @param trackIndex 轨道索引
     * @return 是否可见
     */
    public boolean isTrackVisible(int trackIndex) {
        float y = trackIndexToY(trackIndex);
        float trackBottom = y + viewport.getTrackHeight();
        return trackBottom >= 0 && y <= viewport.getViewHeight();
    }

    /**
     * 获取可见时间范围
     * 
     * @return [startTime, endTime] 数组
     */
    public float[] getVisibleTimeRange() {
        float contentX = viewport.getContentX();
        float contentW = viewport.getContentWidth();
        float startTime = xToTime(contentX);
        float endTime = xToTime(contentX + contentW);
        return new float[]{startTime, endTime};
    }

    /**
     * 获取可见轨道范围
     * 
     * <p>★ 优化：考虑部分可见轨道（例如轨道底部超出视口但顶部可见）
     * 
     * @param totalTracks 总轨道数
     * @return [startIndex, endIndex] 数组
     */
    public int[] getVisibleTrackRange(int totalTracks) {
        if (totalTracks <= 0) {
            return new int[]{-1, -1};
        }
        
        float viewH = viewport.getViewHeight();
        float rowHeight = getRowHeight();
        if (rowHeight <= 0f) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid rowHeight: {}, returning empty range", rowHeight);
            }
            return new int[]{-1, -1};
        }
        
        float scrollY = viewport.getScrollY();
        
        // ★ 优化：处理负 scrollY（虽然通常不应该发生，但防御性编程）
        if (scrollY < 0) {
            scrollY = 0;
        }
        
        // ★ 优化：使用 Math.floor/ceil 计算可见范围，考虑部分可见轨道
        // 例如：如果轨道底部超出视口但顶部可见，也应该包含在可见范围内
        int startIndex = Math.max(0, (int) Math.floor(scrollY / rowHeight));
        int endIndex = Math.min(totalTracks - 1, (int) Math.ceil((scrollY + viewH) / rowHeight));
        
        return new int[]{startIndex, endIndex};
    }

    // ========== 工具方法 ==========

    /**
     * 获取当前缩放级别下的像素/秒
     * 
     * @return 像素/秒
     */
    public float getPixelsPerSecond() {
        return viewport.getPixelsPerSecond();
    }

    /**
     * 获取视口对象（用于高级操作）
     * 
     * @return 视口对象
     */
    public TimelineRenderViewport getViewport() {
        return viewport;
    }
}

