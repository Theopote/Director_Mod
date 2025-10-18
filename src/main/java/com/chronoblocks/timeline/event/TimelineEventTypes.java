package com.chronoblocks.timeline.event;

/**
 * 🧭 TimelineEventTypes
 * ------------------------------------------------------------
 * ChronoBlocks 时间线事件类型常量集。
 *
 * <p>所有模块通过这些标准化事件名通信，
 * 包括：播放控制、关键帧修改、轨道操作、容器更新、属性变更等。
 *
 * <h3>设计理念：</h3>
 * <ul>
 *  <li>使用命名空间前缀（timeline., container., property.等）组织事件</li>
 *  <li>提供清晰的语义，便于理解事件含义</li>
 *  <li>支持插件系统的自定义事件扩展</li>
 *  <li>实现模块间的完全解耦通信</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 发布播放事件
 * TimelineEventBus.get().post(
 *     new TimelineEvent(TimelineEventTypes.PLAY, Map.of("speed", 1.0f))
 * );
 * 
 * // 监听关键帧更新
 * TimelineEventBus.get().subscribe(TimelineEvent.class, e -> {
 *     if (e.type.equals(TimelineEventTypes.KEYFRAME_UPDATED)) {
 *         System.out.println("关键帧已更新：" + e.payload);
 *     }
 * });
 * }</pre>
 *
 * <h3>事件命名规范：</h3>
 * <ul>
 *  <li>timeline.* - 时间线相关事件</li>
 *  <li>container.* - 容器系统事件</li>
 *  <li>property.* - 属性编辑事件</li>
 *  <li>render.* - 渲染系统事件</li>
 *  <li>project.* - 项目管理事件</li>
 *  <li>ui.* - 用户界面事件</li>
 *  <li>plugin.* - 插件扩展事件</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-18
 */
public final class TimelineEventTypes {

    private TimelineEventTypes() {
        // 私有构造函数，防止实例化
    }

    // =========================================================
    // 🎬 播放控制事件
    // =========================================================
    
    /** 播放事件 - 动画开始播放 */
    public static final String PLAY = "timeline.play";
    
    /** 暂停事件 - 动画暂停播放 */
    public static final String PAUSE = "timeline.pause";
    
    /** 停止事件 - 动画停止并重置 */
    public static final String STOP = "timeline.stop";
    
    /** 循环状态变化 - 循环播放开关切换 */
    public static final String LOOP_CHANGED = "timeline.loop_changed";
    
    /** 速度变化 - 播放速度倍率改变 */
    public static final String SPEED_CHANGED = "timeline.speed_changed";
    
    /** 跳转事件 - 跳转到指定时间/帧 */
    public static final String SEEK = "timeline.seek";

    // =========================================================
    // 🕒 帧与时间同步事件
    // =========================================================
    
    /** 帧变化 - 当前帧位置改变 */
    public static final String FRAME_CHANGED = "timeline.frame_changed";
    
    /** 帧更新 - 每帧更新事件 */
    public static final String FRAME_TICK = "timeline.frame_tick";
    
    /** 帧结束 - 播放到达终点 */
    public static final String FRAME_END = "timeline.frame_end";
    
    /** 时间标记移动 - 时间线光标移动 */
    public static final String TIME_MARKER_MOVED = "timeline.time_marker_moved";

    // =========================================================
    // 🎞 关键帧事件
    // =========================================================
    
    /** 关键帧添加 - 新增关键帧 */
    public static final String KEYFRAME_ADDED = "timeline.keyframe_added";
    
    /** 关键帧移除 - 删除关键帧 */
    public static final String KEYFRAME_REMOVED = "timeline.keyframe_removed";
    
    /** 关键帧更新 - 修改关键帧属性 */
    public static final String KEYFRAME_UPDATED = "timeline.keyframe_updated";
    
    /** 关键帧选中 - 选择关键帧 */
    public static final String KEYFRAME_SELECTED = "timeline.keyframe_selected";
    
    /** 关键帧移动 - 关键帧时间位置改变 */
    public static final String KEYFRAME_MOVED = "timeline.keyframe_moved";
    
    /** 关键帧复制 - 复制关键帧 */
    public static final String KEYFRAME_COPIED = "timeline.keyframe_copied";

    // =========================================================
    // 📋 轨道事件
    // =========================================================
    
    /** 轨道添加 - 新增轨道 */
    public static final String TRACK_ADDED = "timeline.track_added";
    
    /** 轨道移除 - 删除轨道 */
    public static final String TRACK_REMOVED = "timeline.track_removed";
    
    /** 轨道选中 - 选择轨道 */
    public static final String TRACK_SELECTED = "timeline.track_selected";
    
    /** 轨道静音 - 轨道静音状态切换 */
    public static final String TRACK_MUTED = "timeline.track_muted";
    
    /** 轨道独奏 - 轨道独奏状态切换 */
    public static final String TRACK_SOLOED = "timeline.track_soloed";
    
    /** 轨道重排序 - 轨道顺序改变 */
    public static final String TRACK_REORDERED = "timeline.track_reordered";
    
    /** 轨道锁定 - 轨道锁定状态切换 */
    public static final String TRACK_LOCKED = "timeline.track_locked";
    
    /** 轨道重命名 - 轨道名称改变 */
    public static final String TRACK_RENAMED = "timeline.track_renamed";

    // =========================================================
    // 🧱 容器事件
    // =========================================================
    
    /** 容器添加 - 新增容器 */
    public static final String CONTAINER_ADDED = "container.added";
    
    /** 容器移除 - 删除容器 */
    public static final String CONTAINER_REMOVED = "container.removed";
    
    /** 容器更新 - 容器属性改变 */
    public static final String CONTAINER_UPDATED = "container.updated";
    
    /** 容器选中 - 选择容器 */
    public static final String CONTAINER_SELECTED = "container.selected";
    
    /** 容器绑定方块变化 - 容器关联的方块改变 */
    public static final String CONTAINER_BOUND_BLOCKS_CHANGED = "container.bound_blocks_changed";
    
    /** 容器变换 - 容器位置/旋转/缩放改变 */
    public static final String CONTAINER_TRANSFORMED = "container.transformed";
    
    /** 容器可见性变化 - 容器显示/隐藏切换 */
    public static final String CONTAINER_VISIBILITY_CHANGED = "container.visibility_changed";

    // =========================================================
    // 💫 特效 / 渲染事件
    // =========================================================
    
    /** 特效添加 - 新增特效 */
    public static final String EFFECT_ADDED = "effect.added";
    
    /** 特效移除 - 删除特效 */
    public static final String EFFECT_REMOVED = "effect.removed";
    
    /** 特效更新 - 特效参数改变 */
    public static final String EFFECT_UPDATED = "effect.updated";
    
    /** 特效启用变化 - 特效启用/禁用切换 */
    public static final String EFFECT_ENABLED_CHANGED = "effect.enabled_changed";
    
    /** 后期处理变化 - 后期处理参数改变 */
    public static final String POST_PROCESS_CHANGED = "render.postprocess_changed";
    
    /** 相机变化 - 相机参数改变 */
    public static final String CAMERA_CHANGED = "render.camera_changed";
    
    /** 光照变化 - 光照参数改变 */
    public static final String LIGHTING_CHANGED = "render.lighting_changed";
    
    /** 材质变化 - 材质参数改变 */
    public static final String MATERIAL_CHANGED = "render.material_changed";

    // =========================================================
    // 🧩 属性与编辑事件
    // =========================================================
    
    /** 属性变化 - 单个属性值改变 */
    public static final String PROPERTY_CHANGED = "property.changed";
    
    /** 属性批量更新 - 多个属性同时改变 */
    public static final String PROPERTY_BATCH_UPDATE = "property.batch_update";
    
    /** 属性选择变化 - 当前选中对象改变 */
    public static final String PROPERTY_SELECTION_CHANGED = "property.selection_changed";
    
    /** 属性动画开始 - 属性动画开始播放 */
    public static final String PROPERTY_ANIMATION_STARTED = "property.animation_started";
    
    /** 属性动画结束 - 属性动画播放结束 */
    public static final String PROPERTY_ANIMATION_ENDED = "property.animation_ended";
    
    /** 属性预览开始 - 实时预览开始 */
    public static final String PROPERTY_PREVIEW_STARTED = "property.preview_started";
    
    /** 属性预览提交 - 实时预览提交 */
    public static final String PROPERTY_PREVIEW_COMMITTED = "property.preview_committed";
    
    /** 属性预览取消 - 实时预览取消 */
    public static final String PROPERTY_PREVIEW_CANCELLED = "property.preview_cancelled";

    // =========================================================
    // 🧮 项目与序列事件
    // =========================================================
    
    /** 项目加载 - 项目文件加载完成 */
    public static final String PROJECT_LOADED = "project.loaded";
    
    /** 项目保存 - 项目保存完成 */
    public static final String PROJECT_SAVED = "project.saved";
    
    /** 项目关闭 - 项目关闭 */
    public static final String PROJECT_CLOSED = "project.closed";
    
    /** 项目修改 - 项目内容被修改 */
    public static final String PROJECT_MODIFIED = "project.modified";
    
    /** 序列添加 - 新增序列 */
    public static final String SEQUENCE_ADDED = "sequence.added";
    
    /** 序列移除 - 删除序列 */
    public static final String SEQUENCE_REMOVED = "sequence.removed";
    
    /** 序列选中 - 选择序列 */
    public static final String SEQUENCE_SELECTED = "sequence.selected";
    
    /** 序列重命名 - 序列名称改变 */
    public static final String SEQUENCE_RENAMED = "sequence.renamed";

    // =========================================================
    // ⚙️ 系统与工具事件
    // =========================================================
    
    /** 工具切换 - 当前工具改变 */
    public static final String TOOL_CHANGED = "ui.tool_changed";
    
    /** 面板聚焦 - UI面板获得焦点 */
    public static final String PANEL_FOCUSED = "ui.panel_focused";
    
    /** 面板关闭 - UI面板关闭 */
    public static final String PANEL_CLOSED = "ui.panel_closed";
    
    /** 停靠布局变化 - UI布局改变 */
    public static final String DOCK_LAYOUT_CHANGED = "ui.dock_layout_changed";
    
    /** 时间线调整大小 - 时间线面板尺寸改变 */
    public static final String TIMELINE_RESIZED = "ui.timeline_resized";
    
    /** 吸附设置变化 - 吸附参数改变 */
    public static final String SNAP_SETTINGS_CHANGED = "timeline.snap_settings_changed";
    
    /** 主题变化 - UI主题切换 */
    public static final String THEME_CHANGED = "ui.theme_changed";
    
    /** 缩放变化 - 视图缩放级别改变 */
    public static final String ZOOM_CHANGED = "ui.zoom_changed";

    // =========================================================
    // 📝 命令与历史事件
    // =========================================================
    
    /** 撤销 - 执行撤销操作 */
    public static final String UNDO = "command.undo";
    
    /** 重做 - 执行重做操作 */
    public static final String REDO = "command.redo";
    
    /** 命令执行 - 新命令被执行 */
    public static final String COMMAND_EXECUTED = "command.executed";
    
    /** 历史清空 - 命令历史被清空 */
    public static final String HISTORY_CLEARED = "command.history_cleared";

    // =========================================================
    // 🧠 自定义扩展事件（留给插件系统）
    // =========================================================
    
    /** 插件事件 - 插件自定义事件 */
    public static final String PLUGIN_EVENT = "plugin.event";
    
    /** 用户自定义事件 - 用户扩展事件 */
    public static final String USER_DEFINED = "user.event";
    
    /** 调试事件 - 调试信息事件 */
    public static final String DEBUG_EVENT = "debug.event";
    
    /** 性能事件 - 性能监控事件 */
    public static final String PERFORMANCE_EVENT = "performance.event";

    // =========================================================
    // 🔧 辅助方法
    // =========================================================
    
    /**
     * 检查事件类型是否为播放控制事件
     * 
     * @param eventType 事件类型
     * @return 如果是播放控制事件则返回 true
     */
    public static boolean isPlaybackEvent(String eventType) {
        return eventType != null && (
            eventType.equals(PLAY) ||
            eventType.equals(PAUSE) ||
            eventType.equals(STOP) ||
            eventType.equals(SEEK)
        );
    }
    
    /**
     * 检查事件类型是否为关键帧事件
     * 
     * @param eventType 事件类型
     * @return 如果是关键帧事件则返回 true
     */
    public static boolean isKeyframeEvent(String eventType) {
        return eventType != null && eventType.startsWith("timeline.keyframe");
    }
    
    /**
     * 检查事件类型是否为轨道事件
     * 
     * @param eventType 事件类型
     * @return 如果是轨道事件则返回 true
     */
    public static boolean isTrackEvent(String eventType) {
        return eventType != null && eventType.startsWith("timeline.track");
    }
    
    /**
     * 检查事件类型是否为容器事件
     * 
     * @param eventType 事件类型
     * @return 如果是容器事件则返回 true
     */
    public static boolean isContainerEvent(String eventType) {
        return eventType != null && eventType.startsWith("container.");
    }
    
    /**
     * 检查事件类型是否为属性事件
     * 
     * @param eventType 事件类型
     * @return 如果是属性事件则返回 true
     */
    public static boolean isPropertyEvent(String eventType) {
        return eventType != null && eventType.startsWith("property.");
    }
    
    /**
     * 检查事件类型是否为渲染事件
     * 
     * @param eventType 事件类型
     * @return 如果是渲染事件则返回 true
     */
    public static boolean isRenderEvent(String eventType) {
        return eventType != null && (
            eventType.startsWith("render.") ||
            eventType.startsWith("effect.")
        );
    }
    
    /**
     * 检查事件类型是否为UI事件
     * 
     * @param eventType 事件类型
     * @return 如果是UI事件则返回 true
     */
    public static boolean isUIEvent(String eventType) {
        return eventType != null && eventType.startsWith("ui.");
    }
    
    /**
     * 获取事件类型的分类
     * 
     * @param eventType 事件类型
     * @return 事件分类名称
     */
    public static String getCategory(String eventType) {
        if (eventType == null || !eventType.contains(".")) {
            return "unknown";
        }
        return eventType.substring(0, eventType.indexOf('.'));
    }
    
    /**
     * 获取所有播放控制事件类型
     * 
     * @return 播放控制事件类型数组
     */
    public static String[] getPlaybackEvents() {
        return new String[]{PLAY, PAUSE, STOP, LOOP_CHANGED, SPEED_CHANGED, SEEK};
    }
    
    /**
     * 获取所有关键帧事件类型
     * 
     * @return 关键帧事件类型数组
     */
    public static String[] getKeyframeEvents() {
        return new String[]{
            KEYFRAME_ADDED, KEYFRAME_REMOVED, KEYFRAME_UPDATED,
            KEYFRAME_SELECTED, KEYFRAME_MOVED, KEYFRAME_COPIED
        };
    }
    
    /**
     * 获取所有轨道事件类型
     * 
     * @return 轨道事件类型数组
     */
    public static String[] getTrackEvents() {
        return new String[]{
            TRACK_ADDED, TRACK_REMOVED, TRACK_SELECTED, TRACK_MUTED,
            TRACK_SOLOED, TRACK_REORDERED, TRACK_LOCKED, TRACK_RENAMED
        };
    }
}

