package com.chronoblocks.ui.properties.preset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🗂️ 预设管理器 v1.0 - ChronoBlocks Properties System
 * <p>
 * 管理属性预设的保存、加载、导入、导出。
 * </p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>从对象创建预设</li>
 *   <li>将预设应用到对象</li>
 *   <li>导入/导出预设文件</li>
 *   <li>预设库管理</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class PresetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetManager.class);
    
    // ==================== 预设库 ====================
    
    /** 预设库：类型 → 预设列表 */
    private static final Map<String, List<PropertyPreset>> presetLibrary = new HashMap<>();
    
    /** 预设目录 */
    private static Path presetDirectory = Paths.get("config/chronoblocks/presets");
    
    // ==================== 预设创建 ====================
    
    /**
     * 从对象创建预设
     */
    public static PropertyPreset createPreset(Object target, String name) {
        if (target == null) {
            throw new IllegalArgumentException("Target cannot be null");
        }
        
        String targetType = target.getClass().getSimpleName();
        PropertyPreset preset = new PropertyPreset(name, targetType);
        
        // 使用反射读取所有字段
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    
                    // 只保存可序列化的简单类型
                    if (isSerializable(value)) {
                        preset.setProperty(field.getName(), value);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[PresetManager] 无法读取字段: {} - {}", 
                        field.getName(), e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
        
        LOGGER.info("[PresetManager] 创建预设: {} ({}个属性)", 
            preset.getName(), preset.getAllProperties().size());
        
        return preset;
    }
    
    /**
     * 将预设应用到对象
     */
    public static void applyPreset(Object target, PropertyPreset preset) {
        if (target == null || preset == null) {
            throw new IllegalArgumentException("Target and preset cannot be null");
        }
        
        Map<String, Object> properties = preset.getAllProperties();
        int appliedCount = 0;
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            
            try {
                if (setProperty(target, propertyName, value)) {
                    appliedCount++;
                }
            } catch (Exception e) {
                LOGGER.warn("[PresetManager] 应用属性失败: {} - {}", 
                    propertyName, e.getMessage());
            }
        }
        
        LOGGER.info("[PresetManager] 应用预设: {} ({}个属性，成功{个})", 
            preset.getName(), properties.size(), appliedCount);
    }
    
    // ==================== 预设库操作 ====================
    
    /**
     * 保存预设到库
     */
    public static void saveToLibrary(PropertyPreset preset) {
        if (preset == null) {
            return;
        }
        
        List<PropertyPreset> presets = presetLibrary.computeIfAbsent(
            preset.getTargetType(), k -> new ArrayList<>()
        );
        
        // 检查是否已存在
        presets.removeIf(p -> p.getId().equals(preset.getId()));
        presets.add(preset);
        
        LOGGER.info("[PresetManager] 保存预设到库: {}", preset.getName());
    }
    
    /**
     * 从库中删除预设
     */
    public static boolean removeFromLibrary(PropertyPreset preset) {
        if (preset == null) {
            return false;
        }
        
        List<PropertyPreset> presets = presetLibrary.get(preset.getTargetType());
        if (presets != null) {
            boolean removed = presets.removeIf(p -> p.getId().equals(preset.getId()));
            if (removed) {
                LOGGER.info("[PresetManager] 从库中删除预设: {}", preset.getName());
            }
            return removed;
        }
        
        return false;
    }
    
    /**
     * 获取指定类型的所有预设
     */
    public static List<PropertyPreset> getPresets(String targetType) {
        List<PropertyPreset> presets = presetLibrary.get(targetType);
        return presets != null ? new ArrayList<>(presets) : new ArrayList<>();
    }
    
    /**
     * 获取所有预设
     */
    public static List<PropertyPreset> getAllPresets() {
        return presetLibrary.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
    
    // ==================== 文件操作 ====================
    
    /**
     * 导出预设到文件
     */
    public static void exportPreset(PropertyPreset preset, String filename) throws IOException {
        if (preset == null) {
            throw new IllegalArgumentException("Preset cannot be null");
        }
        
        Files.createDirectories(presetDirectory);
        Path filePath = presetDirectory.resolve(filename);
        
        String json = preset.toJson();
        Files.writeString(filePath, json);
        
        LOGGER.info("[PresetManager] 导出预设: {} → {}", preset.getName(), filePath);
    }
    
    /**
     * 从文件导入预设
     */
    public static PropertyPreset importPreset(String filename) throws IOException {
        Path filePath = presetDirectory.resolve(filename);
        
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("预设文件不存在: " + filePath);
        }
        
        String json = Files.readString(filePath);
        PropertyPreset preset = PropertyPreset.fromJson(json);
        
        LOGGER.info("[PresetManager] 导入预设: {} ← {}", preset.getName(), filePath);
        
        return preset;
    }
    
    /**
     * 加载所有预设文件
     */
    public static void loadAllPresets() throws IOException {
        if (!Files.exists(presetDirectory)) {
            return;
        }
        
        int count = 0;
        for (Path path : Files.list(presetDirectory).collect(Collectors.toList())) {
            if (path.toString().endsWith(".json")) {
                try {
                    PropertyPreset preset = importPreset(path.getFileName().toString());
                    saveToLibrary(preset);
                    count++;
                } catch (Exception e) {
                    LOGGER.error("[PresetManager] 加载预设失败: {} - {}", 
                        path.getFileName(), e.getMessage());
                }
            }
        }
        
        LOGGER.info("[PresetManager] 加载完成: {}个预设", count);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 设置对象属性
     */
    private static boolean setProperty(Object target, String propertyName, Object value) {
        try {
            // 尝试使用setter
            String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) 
                + propertyName.substring(1);
            
            Method setter = findSetter(target.getClass(), setterName, value);
            if (setter != null) {
                setter.invoke(target, value);
                return true;
            }
            
            // 回退到字段访问
            Field field = findField(target.getClass(), propertyName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[PresetManager] 设置属性失败: {} - {}", propertyName, e.getMessage());
        }
        return false;
    }
    
    private static Method findSetter(Class<?> clazz, String methodName, Object value) {
        if (value == null) return null;
        try {
            return clazz.getMethod(methodName, value.getClass());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
    
    private static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
        }
        return null;
    }
    
    private static boolean isSerializable(Object value) {
        if (value == null) return true;
        Class<?> type = value.getClass();
        return type.isPrimitive() || 
               type == String.class ||
               type == Integer.class ||
               type == Float.class ||
               type == Double.class ||
               type == Boolean.class ||
               type == Long.class;
    }
    
    /**
     * 设置预设目录
     */
    public static void setPresetDirectory(Path directory) {
        presetDirectory = directory;
        LOGGER.info("[PresetManager] 预设目录已设置为: {}", directory);
    }
}