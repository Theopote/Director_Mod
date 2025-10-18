package com.chronoblocks.timeline.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 🎯 TimelineEvent
 * ------------------------------------------------------------
 * ChronoBlocks 通用时间线事件。
 *
 * <p>这是一个通用的事件容器，可以携带任意类型的数据载荷（payload）。
 * 通过 {@link TimelineEventTypes} 中定义的事件类型常量来标识事件类别。
 *
 * <h3>核心特性：</h3>
 * <ul>
 *  <li>✅ 唯一 ID (UUID) - 每个事件都可唯一追踪，用于日志或调试</li>
 *  <li>🕒 时间戳 - 记录事件产生的时间，方便同步或回放</li>
 *  <li>🔍 来源标识 (source) - 区分事件来自哪个模块</li>
 *  <li>🧩 灵活载荷 (data map) - 支持键值对数据存储</li>
 *  <li>🔗 链式调用 - 便捷的 fluent API</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建播放事件
 * TimelineEvent event = new TimelineEvent(TimelineEventTypes.PLAY)
 *     .withData("speed", 1.5f)
 *     .withData("loop", true)
 *     .withSource("timeline");
 * 
 * // 发布事件
 * TimelineEventBus.get().post(event);
 * 
 * // 监听事件
 * TimelineEventBus.get().subscribe(TimelineEvent.class, e -> {
 *     if (e.getType().equals(TimelineEventTypes.PLAY)) {
 *         float speed = e.getFloat("speed", 1.0f);
 *         boolean loop = e.getBoolean("loop", false);
 *         System.out.println("事件ID: " + e.getId());
 *     }
 * });
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-18
 */
public class TimelineEvent {
    
    /** 事件唯一 ID，用于调试或跟踪 */
    private final String id;
    
    /** 事件类型 */
    private final String type;
    
    /** 事件源标识（可选，例如 "timeline", "ui", "container"） */
    private String source;
    
    /** 数据载荷 */
    private final Map<String, Object> data;
    
    /** 事件时间戳 */
    private final long timestamp;
    
    /**
     * 构造函数
     * 
     * @param type 事件类型（使用 {@link TimelineEventTypes} 中的常量）
     */
    public TimelineEvent(String type) {
        this.id = UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(type, "Event type cannot be null");
        this.data = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 构造函数（带事件源标识）
     * 
     * @param type 事件类型
     * @param source 事件源标识（例如 "timeline", "ui", "container"）
     */
    public TimelineEvent(String type, String source) {
        this(type);
        this.source = source;
    }
    
    /**
     * 构造函数（带事件源对象）
     * 
     * @param type 事件类型
     * @param source 事件源对象
     */
    public TimelineEvent(String type, Object source) {
        this(type);
        this.source = source != null ? source.getClass().getSimpleName() : null;
    }
    
    /**
     * 构造函数（带数据载荷）
     * 
     * @param type 事件类型
     * @param data 数据载荷
     */
    public TimelineEvent(String type, Map<String, Object> data) {
        this(type);
        if (data != null) {
            this.data.putAll(data);
        }
    }
    
    /**
     * 构造函数（完整参数 - 字符串源）
     * 
     * @param type 事件类型
     * @param source 事件源标识
     * @param data 数据载荷
     */
    public TimelineEvent(String type, String source, Map<String, Object> data) {
        this(type, data);
        this.source = source;
    }
    
    /**
     * 构造函数（完整参数 - 对象源）
     * 
     * @param type 事件类型
     * @param source 事件源对象
     * @param data 数据载荷
     */
    public TimelineEvent(String type, Object source, Map<String, Object> data) {
        this(type, data);
        this.source = source != null ? source.getClass().getSimpleName() : null;
    }
    
    // =========================================================
    // 🔧 数据设置（链式调用）
    // =========================================================
    
    /**
     * 添加数据（链式调用）
     * 
     * @param key 数据键
     * @param value 数据值
     * @return 当前事件对象
     */
    public TimelineEvent withData(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
    
    /**
     * 批量添加数据（链式调用）
     * 
     * @param data 数据映射
     * @return 当前事件对象
     */
    public TimelineEvent withAllData(Map<String, Object> data) {
        if (data != null) {
            this.data.putAll(data);
        }
        return this;
    }
    
    /**
     * 设置事件源（链式调用）
     * 
     * @param source 事件源标识字符串
     * @return 当前事件对象
     */
    public TimelineEvent withSource(String source) {
        this.source = source;
        return this;
    }
    
    /**
     * 设置事件源对象（链式调用）
     * 
     * @param source 事件源对象
     * @return 当前事件对象
     */
    public TimelineEvent withSourceObject(Object source) {
        this.source = source != null ? source.getClass().getSimpleName() : null;
        return this;
    }
    
    // =========================================================
    // 🔍 数据获取
    // =========================================================
    
    /**
     * 获取数据
     * 
     * @param key 数据键
     * @return 数据值（可能为 null）
     */
    public Object getData(String key) {
        return data.get(key);
    }
    
    /**
     * 获取数据（带默认值）
     * 
     * @param <T> 数据类型
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 数据值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 获取字符串数据
     * 
     * @param key 数据键
     * @return 字符串值
     */
    public String getString(String key) {
        return getString(key, null);
    }
    
    /**
     * 获取字符串数据（带默认值）
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    public String getString(String key, String defaultValue) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    /**
     * 获取整数数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 整数值或默认值
     */
    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取浮点数数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 浮点数值或默认值
     */
    public float getFloat(String key, float defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取双精度浮点数数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 双精度浮点数值或默认值
     */
    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取布尔数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = data.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }
    
    /**
     * 检查是否包含指定键
     * 
     * @param key 数据键
     * @return 如果包含则返回 true
     */
    public boolean hasData(String key) {
        return data.containsKey(key);
    }
    
    /**
     * 获取所有数据（不可修改）
     * 
     * @return 数据映射的只读视图
     */
    public Map<String, Object> getAllData() {
        return Collections.unmodifiableMap(data);
    }
    
    // =========================================================
    // 🧭 属性获取
    // =========================================================
    
    /**
     * 获取事件唯一ID
     * 
     * @return 事件UUID字符串
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取事件类型
     * 
     * @return 事件类型字符串
     */
    public String getType() {
        return type;
    }
    
    /**
     * 获取事件源标识
     * 
     * @return 事件源标识（可能为 null）
     */
    public String getSource() {
        return source;
    }
    
    /**
     * 获取事件时间戳
     * 
     * @return 事件创建时间（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取事件分类
     * 
     * @return 事件分类名称
     */
    public String getCategory() {
        return TimelineEventTypes.getCategory(type);
    }
    
    // =========================================================
    // 🔍 类型检查
    // =========================================================
    
    /**
     * 检查是否为播放控制事件
     * 
     * @return 如果是播放控制事件则返回 true
     */
    public boolean isPlaybackEvent() {
        return TimelineEventTypes.isPlaybackEvent(type);
    }
    
    /**
     * 检查是否为关键帧事件
     * 
     * @return 如果是关键帧事件则返回 true
     */
    public boolean isKeyframeEvent() {
        return TimelineEventTypes.isKeyframeEvent(type);
    }
    
    /**
     * 检查是否为轨道事件
     * 
     * @return 如果是轨道事件则返回 true
     */
    public boolean isTrackEvent() {
        return TimelineEventTypes.isTrackEvent(type);
    }
    
    /**
     * 检查是否为容器事件
     * 
     * @return 如果是容器事件则返回 true
     */
    public boolean isContainerEvent() {
        return TimelineEventTypes.isContainerEvent(type);
    }
    
    /**
     * 检查是否为属性事件
     * 
     * @return 如果是属性事件则返回 true
     */
    public boolean isPropertyEvent() {
        return TimelineEventTypes.isPropertyEvent(type);
    }
    
    /**
     * 检查是否为渲染事件
     * 
     * @return 如果是渲染事件则返回 true
     */
    public boolean isRenderEvent() {
        return TimelineEventTypes.isRenderEvent(type);
    }
    
    /**
     * 检查是否为UI事件
     * 
     * @return 如果是UI事件则返回 true
     */
    public boolean isUIEvent() {
        return TimelineEventTypes.isUIEvent(type);
    }
    
    // =========================================================
    // 🔧 辅助方法
    // =========================================================
    
    @Override
    public String toString() {
        return String.format("TimelineEvent{id='%s', type='%s', category='%s', source='%s', dataKeys=%s, timestamp=%d}",
            id.substring(0, 8) + "...", // 只显示前8位UUID
            type,
            getCategory(),
            source != null ? source : "null",
            data.keySet(),
            timestamp);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimelineEvent that = (TimelineEvent) o;
        return timestamp == that.timestamp &&
               id.equals(that.id) &&
               type.equals(that.type) &&
               Objects.equals(source, that.source) &&
               Objects.equals(data, that.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, type, source, data, timestamp);
    }
}

