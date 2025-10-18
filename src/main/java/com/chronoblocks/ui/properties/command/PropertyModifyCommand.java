package com.chronoblocks.ui.properties.command;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 🔄 属性修改命令 v1.0 - ChronoBlocks Properties System
 * <p>
 * 用于撤销/重做系统的属性修改命令。
 * 记录对象属性的修改，支持撤销和重做操作。
 * </p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>记录属性的旧值和新值</li>
 *   <li>支持撤销操作（恢复旧值）</li>
 *   <li>支持重做操作（应用新值）</li>
 *   <li>使用反射访问属性</li>
 *   <li>触发属性变更回调</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 记录关键帧时间修改
 * PropertyModifyCommand cmd = new PropertyModifyCommand(
 *     keyframe,           // 目标对象
 *     "time",             // 属性名称
 *     "setTime",          // Setter方法名
 *     0.5f,               // 旧值
 *     1.0f                // 新值
 * );
 * 
 * // 执行命令（应用新值）
 * cmd.execute();
 * 
 * // 撤销命令（恢复旧值）
 * cmd.undo();
 * 
 * // 重做命令（重新应用新值）
 * cmd.redo();
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class PropertyModifyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyModifyCommand.class);
    
    // ==================== 命令数据 ====================
    
    /** 目标对象 */
    private final Object target;
    
    /** 属性名称 */
    private final String propertyName;
    
    /** Setter 方法名 */
    private final String setterName;
    
    /** 旧值 */
    private final Object oldValue;
    
    /** 新值 */
    private final Object newValue;
    
    /** 命令描述 */
    private final String description;
    
    /** 是否已执行 */
    private boolean executed = false;
    
    // ==================== 构造函数 ====================
    
    /**
     * 创建属性修改命令
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @param setterName Setter方法名
     * @param oldValue 旧值
     * @param newValue 新值
     */
    public PropertyModifyCommand(Object target, String propertyName, String setterName, 
                                  Object oldValue, Object newValue) {
        this.target = target;
        this.propertyName = propertyName;
        this.setterName = setterName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.description = String.format("修改 %s.%s: %s → %s", 
            target.getClass().getSimpleName(), 
            propertyName, 
            oldValue, 
            newValue);
    }
    
    /**
     * 创建属性修改命令（自动推断setter名称）
     * 
     * @param target 目标对象
     * @param propertyName 属性名称
     * @param oldValue 旧值
     * @param newValue 新值
     */
    public PropertyModifyCommand(Object target, String propertyName, Object oldValue, Object newValue) {
        this(target, propertyName, generateSetterName(propertyName), oldValue, newValue);
    }
    
    // ==================== 命令执行 ====================
    
    /**
     * 执行命令（应用新值）
     */
    public void execute() {
        if (executed) {
            LOGGER.warn("[PropertyModifyCommand] 命令已执行: {}", description);
            return;
        }
        
        applyValue(newValue);
        executed = true;
        
        LOGGER.debug("[PropertyModifyCommand] 执行命令: {}", description);
    }
    
    /**
     * 撤销命令（恢复旧值）
     */
    public void undo() {
        if (!executed) {
            LOGGER.warn("[PropertyModifyCommand] 命令未执行，无法撤销: {}", description);
            return;
        }
        
        applyValue(oldValue);
        executed = false;
        
        LOGGER.debug("[PropertyModifyCommand] 撤销命令: {}", description);
    }
    
    /**
     * 重做命令（重新应用新值）
     */
    public void redo() {
        if (executed) {
            LOGGER.warn("[PropertyModifyCommand] 命令已执行，无需重做: {}", description);
            return;
        }
        
        applyValue(newValue);
        executed = true;
        
        LOGGER.debug("[PropertyModifyCommand] 重做命令: {}", description);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 应用属性值
     */
    private void applyValue(Object value) {
        try {
            // 1. 尝试使用 Setter 方法
            if (setterName != null && !setterName.isEmpty()) {
                Method setter = findSetter(target.getClass(), setterName, value);
                if (setter != null) {
                    setter.invoke(target, value);
                    notifyPropertyChanged();
                    return;
                }
            }
            
            // 2. 回退到直接字段访问
            Field field = findField(target.getClass(), propertyName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
                notifyPropertyChanged();
                return;
            }
            
            LOGGER.error("[PropertyModifyCommand] 无法应用属性值: {} (找不到setter或field)", propertyName);
            
        } catch (Exception e) {
            LOGGER.error("[PropertyModifyCommand] 应用属性值失败: {} - {}", propertyName, e.getMessage(), e);
        }
    }
    
    /**
     * 通知属性已变更
     */
    @SuppressWarnings("unchecked")
    private void notifyPropertyChanged() {
        try {
            IPropertyAdapter<Object> adapter = PropertyAdapterRegistry.getAdapterFor(target);
            if (adapter != null) {
                adapter.onPropertyChanged(target);
            }
        } catch (Exception e) {
            LOGGER.warn("[PropertyModifyCommand] 通知属性变更失败: {}", e.getMessage());
        }
    }
    
    /**
     * 查找 Setter 方法
     */
    private Method findSetter(Class<?> clazz, String methodName, Object value) {
        try {
            // 尝试使用值的类型
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
            LOGGER.debug("[PropertyModifyCommand] 找不到setter方法: {}", methodName);
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
            // 尝试父类
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
        }
        return null;
    }
    
    /**
     * 生成 Setter 方法名
     */
    private static String generateSetterName(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return null;
        }
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
    
    // ==================== Getters ====================
    
    public Object getTarget() {
        return target;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public Object getOldValue() {
        return oldValue;
    }
    
    public Object getNewValue() {
        return newValue;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * 检查命令是否可合并
     * 
     * @param other 另一个命令
     * @return 如果可以合并则返回 true
     */
    public boolean canMergeWith(PropertyModifyCommand other) {
        if (other == null) {
            return false;
        }
        
        // 相同对象、相同属性才能合并
        return this.target == other.target 
            && this.propertyName.equals(other.propertyName);
    }
    
    /**
     * 与另一个命令合并
     * 
     * @param other 另一个命令
     * @return 合并后的新命令
     */
    public PropertyModifyCommand mergeWith(PropertyModifyCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("无法合并命令");
        }
        
        // 保留第一个命令的旧值，使用第二个命令的新值
        return new PropertyModifyCommand(
            this.target,
            this.propertyName,
            this.setterName,
            this.oldValue,
            other.newValue
        );
    }
    
    @Override
    public String toString() {
        return String.format("PropertyModifyCommand[%s, executed=%s]", description, executed);
    }
}

