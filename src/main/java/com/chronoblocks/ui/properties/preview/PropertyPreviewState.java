package com.chronoblocks.ui.properties.preview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 📊 属性预览状态 v1.0 - ChronoBlocks Properties System
 * <p>
 * 存储单个属性的预览状态数据。
 * 记录原始值、当前值和setter方法。
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class PropertyPreviewState {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyPreviewState.class);
    
    // ==================== 状态数据 ====================
    
    /** 目标对象 */
    private final Object target;
    
    /** 属性名称 */
    private final String propertyName;
    
    /** 原始值 */
    private final Object originalValue;
    
    /** 当前值 */
    private Object currentValue;
    
    /** 创建时间 */
    private final long createTime;
    
    // ==================== 构造函数 ====================
    
    public PropertyPreviewState(Object target, String propertyName, Object originalValue) {
        this.target = target;
        this.propertyName = propertyName;
        this.originalValue = originalValue;
        this.currentValue = originalValue;
        this.createTime = System.currentTimeMillis();
    }
    
    // ==================== 状态操作 ====================
    
    /**
     * 恢复原始值
     */
    public void restoreOriginalValue() {
        if (!isModified()) {
            return;
        }
        
        try {
            // 尝试使用setter方法
            String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) 
                + propertyName.substring(1);
            
            Method setter = findSetter(target.getClass(), setterName, originalValue);
            if (setter != null) {
                setter.invoke(target, originalValue);
                currentValue = originalValue;
                return;
            }
            
            // 回退到字段访问
            Field field = findField(target.getClass(), propertyName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, originalValue);
                currentValue = originalValue;
                return;
            }
            
            LOGGER.error("[PropertyPreviewState] 无法恢复原始值: {}", propertyName);
            
        } catch (Exception e) {
            LOGGER.error("[PropertyPreviewState] 恢复原始值失败: {} - {}", 
                propertyName, e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否已修改
     */
    public boolean isModified() {
        if (originalValue == null && currentValue == null) {
            return false;
        }
        if (originalValue == null || currentValue == null) {
            return true;
        }
        return !originalValue.equals(currentValue);
    }
    
    /**
     * 获取修改时长（毫秒）
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - createTime;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 查找setter方法
     */
    private Method findSetter(Class<?> clazz, String methodName, Object value) {
        try {
            if (value != null) {
                Class<?> valueType = value.getClass();
                
                // 处理基本类型
                if (valueType == Integer.class) {
                    try {
                        return clazz.getMethod(methodName, int.class);
                    } catch (NoSuchMethodException e) {
                        return clazz.getMethod(methodName, Integer.class);
                    }
                } else if (valueType == Float.class) {
                    try {
                        return clazz.getMethod(methodName, float.class);
                    } catch (NoSuchMethodException e) {
                        return clazz.getMethod(methodName, Float.class);
                    }
                } else if (valueType == Double.class) {
                    try {
                        return clazz.getMethod(methodName, double.class);
                    } catch (NoSuchMethodException e) {
                        return clazz.getMethod(methodName, Double.class);
                    }
                } else if (valueType == Boolean.class) {
                    try {
                        return clazz.getMethod(methodName, boolean.class);
                    } catch (NoSuchMethodException e) {
                        return clazz.getMethod(methodName, Boolean.class);
                    }
                } else {
                    return clazz.getMethod(methodName, valueType);
                }
            }
        } catch (NoSuchMethodException e) {
            // 忽略
        }
        return null;
    }
    
    /**
     * 查找字段
     */
    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
        }
        return null;
    }
    
    // ==================== Getters/Setters ====================
    
    public Object getTarget() {
        return target;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public Object getOriginalValue() {
        return originalValue;
    }
    
    public Object getCurrentValue() {
        return currentValue;
    }
    
    public void setCurrentValue(Object currentValue) {
        this.currentValue = currentValue;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    @Override
    public String toString() {
        return String.format("PropertyPreviewState[%s.%s: %s → %s, modified=%s]",
            target.getClass().getSimpleName(),
            propertyName,
            originalValue,
            currentValue,
            isModified());
    }
}

