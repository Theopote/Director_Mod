package com.director.ui.timeline.snapping;

/**
 * 时间线吸附设置（Director 风格）
 * 
 * <p>负责所有吸附参数、开关、网格等配置。
 * 由 TimelineModel 持有，提供统一的吸附配置管理。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>集中管理所有吸附相关配置</li>
 *   <li>支持细粒度的吸附开关（网格/片段边缘/关键帧）</li>
 *   <li>可扩展（未来可添加吸附标尺、播放头等）</li>
 *   <li>易于序列化和持久化</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public class TimelineSnapSettings {

    /** 是否启用吸附系统 */
    private boolean enabled = true;

    /** 网格间隔（秒）例如 0.1s、0.25s、0.5s 等 */
    private float gridInterval = 0.1f;

    /** 是否启用网格吸附 */
    private boolean snapToGrid = true;

    /** 是否吸附到片段边缘 */
    private boolean snapToClipEdges = true;

    /** 是否吸附到关键帧 */
    private boolean snapToKeyframes = true;

    /** 鼠标像素吸附阈值（px）→ 时间阈值会通过 pps 转换 */
    private float pixelThreshold = 8f;

    // ========== Getter / Setter ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }
    
    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
    }

    public boolean isSnapToClipEdges() {
        return snapToClipEdges;
    }
    
    public void setSnapToClipEdges(boolean snapToClipEdges) {
        this.snapToClipEdges = snapToClipEdges;
    }

    public boolean isSnapToKeyframes() {
        return snapToKeyframes;
    }
    
    public void setSnapToKeyframes(boolean snapToKeyframes) {
        this.snapToKeyframes = snapToKeyframes;
    }

    public float getGridInterval() {
        return gridInterval;
    }

    public void setGridInterval(float gridInterval) {
        this.gridInterval = Math.max(0.001f, gridInterval);
    }

    public float getPixelThreshold() {
        return pixelThreshold;
    }

    public void setPixelThreshold(float pixelThreshold) {
        this.pixelThreshold = Math.max(1f, pixelThreshold);
    }
}