package com.director.ui.timeline.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线时间格式化工具（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>格式化时间标签（秒、分:秒、时:分:秒）</li>
 *   <li>格式化帧数标签</li>
 *   <li>零 GC 优化：使用 StringBuilder 复用</li>
 * </ul>
 * 
 * @author Director Team
 * @version 9.0
 */
public final class TimelineTimeFormatter {

    private static final Logger logger = LoggerFactory.getLogger(TimelineTimeFormatter.class);

    // ★ 性能优化：复用 StringBuilder 以避免字符串格式化时的垃圾分配
    // 注意：ImGui 在主线程渲染，StringBuilder 在多线程环境下不安全，
    // 但 ImGui 的单线程特性保证了此处的线程安全
    private final StringBuilder timeLabelBuilder = new StringBuilder(32);
    
    // 帧率（可配置，默认 20 FPS）
    private int fps;

    public TimelineTimeFormatter() {
        this(20); // 默认帧率
    }

    public TimelineTimeFormatter(int fps) {
        setFps(fps);
    }
    
    /**
     * 设置帧率（允许运行时修改）
     * 
     * @param fps 新的帧率值
     */
    public void setFps(int fps) {
        if (fps <= 0) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid fps value: {}, using default 20", fps);
            }
            this.fps = 20;
        } else {
            this.fps = fps;
        }
    }
    
    /**
     * 获取当前帧率
     * 
     * @return 当前帧率
     */
    public int getFps() {
        return fps;
    }

    /**
     * 格式化时间标签（性能优化版本）
     * 
     * <p>性能优化：使用 StringBuilder 替代 String.format，减少垃圾分配。
     * String.format 涉及复杂的正则解析和中间对象分配，性能较差。
     * 在每一帧渲染标尺（可能有几十个刻度）时调用会产生不少垃圾。
     * 
     * <p>格式：
     * <ul>
     *   <li>小于 60 秒：12.34s</li>
     *   <li>小于 3600 秒：mm:ss.ff</li>
     *   <li>大于 3600 秒：h:mm:ss</li>
     * </ul>
     * 
     * @param t 时间（秒）
     * @return 格式化的时间字符串
     */
    public String formatTimeLabel(float t) {
        if (t < 0) t = 0;
        
        // 简单的缓存重置
        timeLabelBuilder.setLength(0);
        
        if (t < 60) {
            // 格式：12.34s
            int seconds = (int)t;
            timeLabelBuilder.append(seconds).append('.');
            int decimals = (int)((t % 1) * 100);
            if (decimals < 10) timeLabelBuilder.append('0');
            timeLabelBuilder.append(decimals).append('s');
        } else if (t < 3600) {
            // 格式：mm:ss.ff
            int totalSeconds = (int)t;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            int decimals = (int)((t % 1) * 100);
            
            timeLabelBuilder.append(minutes).append(':');
            if (seconds < 10) timeLabelBuilder.append('0');
            timeLabelBuilder.append(seconds).append('.');
            if (decimals < 10) timeLabelBuilder.append('0');
            timeLabelBuilder.append(decimals);
        } else {
            // 格式：h:mm:ss
            int totalSeconds = (int)t;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            
            timeLabelBuilder.append(hours).append(':');
            if (minutes < 10) timeLabelBuilder.append('0');
            timeLabelBuilder.append(minutes).append(':');
            if (seconds < 10) timeLabelBuilder.append('0');
            timeLabelBuilder.append(seconds);
        }
        
        return timeLabelBuilder.toString();
    }

    /**
     * 格式化帧数标签
     * 
     * @param t 时间（秒）
     * @return 格式化的帧数字符串（例如："f120"）
     */
    public String formatFrameLabel(float t) {
        int frame = Math.round(t * fps);
        // 注意：这里使用 String.format 因为格式简单，且调用频率较低
        return String.format("f%d", frame);
    }
}

