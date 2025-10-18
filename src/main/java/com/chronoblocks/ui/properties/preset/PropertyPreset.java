package com.chronoblocks.ui.properties.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 📦 属性预设 v1.0 - ChronoBlocks Properties System
 * <p>
 * 存储对象属性的配置快照。
 * 支持保存/加载/导入/导出。
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class PropertyPreset {
    
    /** 预设ID */
    private String id;
    
    /** 预设名称 */
    private String name;
    
    /** 预设描述 */
    private String description;
    
    /** 目标类型 */
    private String targetType;
    
    /** 属性值映射 */
    private Map<String, Object> properties;
    
    /** 创建时间 */
    private long createTime;
    
    /** 修改时间 */
    private long modifyTime;
    
    /** 作者 */
    private String author;
    
    /** 标签 */
    private String[] tags;
    
    // ==================== 构造函数 ====================
    
    public PropertyPreset() {
        this.id = UUID.randomUUID().toString();
        this.properties = new HashMap<>();
        this.createTime = System.currentTimeMillis();
        this.modifyTime = this.createTime;
    }
    
    public PropertyPreset(String name, String targetType) {
        this();
        this.name = name;
        this.targetType = targetType;
    }
    
    // ==================== 属性操作 ====================
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
        modifyTime = System.currentTimeMillis();
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    public Map<String, Object> getAllProperties() {
        return new HashMap<>(properties);
    }
    
    // ==================== JSON序列化 ====================
    
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    
    public static PropertyPreset fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, PropertyPreset.class);
    }
    
    // ==================== Getters/Setters ====================
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; modifyTime = System.currentTimeMillis(); }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    
    public long getCreateTime() { return createTime; }
    public long getModifyTime() { return modifyTime; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    
    @Override
    public String toString() {
        return String.format("PropertyPreset[%s: %s (%s), %d properties]",
            name, targetType, id.substring(0, 8), properties.size());
    }
}

