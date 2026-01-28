package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线轨道控制区域命中检测（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>定义轨道控制类型（锁定、可见性）</li>
 *   <li>管理控制区域的命中检测</li>
 *   <li>对象池优化（Zero GC）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public final class TimelineTrackControlHit {

    private static final Logger logger = LoggerFactory.getLogger(TimelineTrackControlHit.class);

    /**
     * 轨道控制类型枚举
     */
    public enum TrackControlType {
        ADD_KEYFRAME,
        LOCK,
        VISIBILITY,
        ACTIVE_CAMERA,  // ⭐ v5.0：激活摄像机轨道按钮（仅 CAMERA 轨道）
        ACTIVE_TIME,    // ⭐ v3.5：激活时间轨道按钮（仅 TIME 轨道）
        ACTIVE_SEASON,  // ⭐ v1.1：激活季节轨道按钮（仅 SEASON 轨道）
        ACTIVE_WEATHER  // ⭐ v1.0：激活天气轨道按钮（仅 WEATHER 轨道）
    }

    /**
     * 轨道控制区域命中检测（对象池优化版本）
     * 
     * <p>性能优化：字段改为非 final，支持对象池复用。
     * 在渲染循环中，每帧可能有数百个轨道需要调用此方法，
     * 使用对象池可以避免频繁的对象分配和 GC 压力。
     */
    public static final class Hit {
        public TimelineTrack track;
        public TrackControlType type;
        public float x1;
        public float y1;
        public float x2;
        public float y2;

        public Hit(TimelineTrack track, TrackControlType type, float x1, float y1, float x2, float y2) {
            set(track, type, x1, y1, x2, y2);
        }
        
        /**
         * 设置对象状态（用于对象池复用）
         * 
         * @param track 轨道
         * @param type 控制类型
         * @param x1 左上角 X
         * @param y1 左上角 Y
         * @param x2 右下角 X
         * @param y2 右下角 Y
         */
        public void set(TimelineTrack track, TrackControlType type, float x1, float y1, float x2, float y2) {
            this.track = track;
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    // ========== 对象池管理 ==========
    
    private final List<Hit> trackControlHits = new ArrayList<>();
    // 对象池：缓存已分配的对象，避免每帧创建新对象
    private final List<Hit> hitPool = new ArrayList<>();
    private int poolIndex = 0;
    
    // ★ 优化：预分配对象池大小，减少 ArrayList resize
    private static final int INITIAL_POOL_SIZE = 100;

    // 布局常量
    private static final float CONTROL_ICON_SIZE = 16f;

    /**
     * 注册轨道控制区域（使用对象池优化）
     * 
     * <p>性能优化：使用对象池复用 Hit 对象，避免每帧创建新对象。
     * 在 50 个轨道、60 FPS 的场景下，可以避免每秒创建 3000 个小对象，显著减少 GC 压力。
     * 
     * @param track 轨道
     * @param winX 窗口 X 坐标
     * @param y 轨道 Y 坐标
     * @param leftLabelWidth 左侧标签宽度
     */
    public void registerTrackControlAreas(TimelineTrack track, float winX, float y, float leftLabelWidth) {
        TrackType type = track != null ? track.getType() : null;
        boolean supportsKeyframes = type != null && type.supportsKeyframes();

        // ✅ 修复：按钮区域两侧边距设置为 6px，按钮之间的间距保持 20px 不变，与 TimelineTrackRenderer 保持一致
        float BUTTON_AREA_MARGIN = 6f;
        float BUTTON_SPACING = 20f;
        
        // 从右边界向左计算按钮位置（与 TimelineTrackRenderer 保持一致）
        float visibleX1 = winX + leftLabelWidth - (BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE);
        float visibleX2 = visibleX1 + CONTROL_ICON_SIZE;
        float lockX1 = winX + leftLabelWidth - (BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE);
        float lockX2 = lockX1 + CONTROL_ICON_SIZE;
        float keyframeX1 = winX + leftLabelWidth - (BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE);
        float keyframeX2 = keyframeX1 + CONTROL_ICON_SIZE;
        float y2 = y + CONTROL_ICON_SIZE + 2f;
        
        // ⭐ v5.0：仅对 CAMERA 轨道注册激活按钮（放在原来隐藏按钮的位置）
        if (type == TrackType.CAMERA) {
            addHitToPool(track, TrackControlType.ACTIVE_CAMERA, visibleX1, y + 2f, visibleX2, y2);
        }
        
        // ⭐ v3.5：仅对 TIME 轨道注册激活按钮（放在原来隐藏按钮的位置）
        if (type == TrackType.TIME) {
            addHitToPool(track, TrackControlType.ACTIVE_TIME, visibleX1, y + 2f, visibleX2, y2);
        }
        
        // ⭐ v1.1：仅对 SEASON 轨道注册激活按钮（放在原来隐藏按钮的位置）
        if (type == TrackType.SEASON) {
            addHitToPool(track, TrackControlType.ACTIVE_SEASON, visibleX1, y + 2f, visibleX2, y2);
        }
        
        // ⭐ v1.0：仅对 WEATHER 轨道注册激活按钮（放在原来隐藏按钮的位置）
        if (type == TrackType.WEATHER) {
            addHitToPool(track, TrackControlType.ACTIVE_WEATHER, visibleX1, y + 2f, visibleX2, y2);
        }
        
        // ⭐ v5.0：对于摄像机轨道，只有在激活状态下才允许添加关键帧
        // ⭐ v3.5：对于时间轨道，只有在激活状态下才允许添加关键帧
        // ⭐ v1.1：对于季节轨道，只有在激活状态下才允许添加关键帧
        // ⭐ v1.0：对于天气轨道，只有在激活状态下才允许添加关键帧
        if (supportsKeyframes) {
            if (type == TrackType.CAMERA) {
                // 检查是否是激活的摄像机轨道
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                
                // 只有激活的摄像机轨道才注册关键帧按钮
                if (activeTrack == track) {
                    addHitToPool(track, TrackControlType.ADD_KEYFRAME, keyframeX1, y + 2f, keyframeX2, y2);
                }
            } else if (type == TrackType.TIME) {
                // 检查是否是激活的时间轨道
                com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                        com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveTimeTrack();
                
                // 只有激活的时间轨道才注册关键帧按钮
                if (activeTrack == track) {
                    addHitToPool(track, TrackControlType.ADD_KEYFRAME, keyframeX1, y + 2f, keyframeX2, y2);
                }
            } else if (type == TrackType.SEASON) {
                // 检查是否是激活的季节轨道
                com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveSeasonTrack();
                
                // 只有激活的季节轨道才注册关键帧按钮
                if (activeTrack == track) {
                    addHitToPool(track, TrackControlType.ADD_KEYFRAME, keyframeX1, y + 2f, keyframeX2, y2);
                }
            } else if (type == TrackType.WEATHER) {
                // 检查是否是激活的天气轨道
                com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController controller =
                        com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveWeatherTrack();
                
                // 只有激活的天气轨道才注册关键帧按钮
                if (activeTrack == track) {
                    addHitToPool(track, TrackControlType.ADD_KEYFRAME, keyframeX1, y + 2f, keyframeX2, y2);
                }
            } else {
                // ✅ 修复：容器轨道和清除轨道不支持轨道级关键帧，跳过注册
                if (type != TrackType.CONTAINER) {
                    // 非摄像机/时间/季节轨道，正常注册
                    addHitToPool(track, TrackControlType.ADD_KEYFRAME, keyframeX1, y + 2f, keyframeX2, y2);
                }
            }
        }
        // 使用池化方法获取对象
        addHitToPool(track, TrackControlType.LOCK, lockX1, y + 2f, lockX2, y2);
        
        // ⭐ v5.0：摄像机轨道不注册隐藏/显示按钮
        // ⭐ v3.5：时间轨道不注册隐藏/显示按钮
        // ⭐ v1.1：季节轨道不注册隐藏/显示按钮
        // ⭐ v1.0：天气轨道不注册隐藏/显示按钮
        if (type != TrackType.CAMERA && type != TrackType.TIME && type != TrackType.SEASON && type != TrackType.WEATHER) {
            addHitToPool(track, TrackControlType.VISIBILITY, visibleX1, y + 2f, visibleX2, y2);
        }
    }
    
    /**
     * 添加到对象池（Zero GC 优化）
     * 
     * <p>★ 优化：添加边界检查，防止数组越界
     * 
     * @param track 轨道
     * @param type 控制类型
     * @param x1 左上角 X
     * @param y1 左上角 Y
     * @param x2 右下角 X
     * @param y2 右下角 Y
     */
    private void addHitToPool(TimelineTrack track, TrackControlType type, float x1, float y1, float x2, float y2) {
        if (track == null || type == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid hit parameters: track={}, type={}", track, type);
            }
            return;
        }
        
        // 边界检查：确保坐标有效
        if (x2 <= x1 || y2 <= y1) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid hit bounds: x1={}, y1={}, x2={}, y2={}", x1, y1, x2, y2);
            }
            return;
        }
        
        Hit hit;
        if (poolIndex < hitPool.size()) {
            // 复用已有对象
            hit = hitPool.get(poolIndex);
            // 更新对象状态
            hit.set(track, type, x1, y1, x2, y2);
        } else {
            // 池不够大，新建并添加
            hit = new Hit(track, type, x1, y1, x2, y2);
            hitPool.add(hit);
        }
        poolIndex++;
        trackControlHits.add(hit); // 添加到当前帧的活跃列表
    }

    /**
     * 重置对象池（每帧开始时调用）
     * 
     * <p>★ 优化：预分配对象池大小，减少 ArrayList resize
     */
    public void reset() {
        trackControlHits.clear();
        poolIndex = 0;
        
        // 预分配对象池大小（如果池太小）
        if (hitPool.size() < INITIAL_POOL_SIZE) {
            int needed = INITIAL_POOL_SIZE - hitPool.size();
            for (int i = 0; i < needed; i++) {
                hitPool.add(new Hit(null, TrackControlType.LOCK, 0, 0, 0, 0));
            }
        }
    }

    /**
     * 获取当前帧的轨道控制区域列表
     * 
     * @return 轨道控制区域列表
     */
    public List<Hit> getTrackControlHitAreas() {
        return trackControlHits;
    }

    /**
     * 获取控制图标大小
     * 
     * @return 图标大小（像素）
     */
    public static float getControlIconSize() {
        return CONTROL_ICON_SIZE;
    }
}

