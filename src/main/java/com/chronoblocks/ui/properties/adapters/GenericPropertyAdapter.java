package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImFloat;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * 通用属性适配器 - ChronoBlocks Properties System v4.5
 * 
 * <p>通过反射动态渲染任意对象的基础字段。
 * 用于那些没有专属适配器的对象，提供基本的属性查看和编辑功能。
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>🧠 自动反射对象字段</li>
 *   <li>✏ 支持基础类型编辑（int, float, double, boolean, String）</li>
 *   <li>🔄 支持枚举类型编辑</li>
 *   <li>🔄 自动更新回写</li>
 *   <li>🚫 跳过静态字段和瞬态字段</li>
 *   <li>🛡 完整的异常处理</li>
 * </ul>
 * 
 * <h3>支持的字段类型</h3>
 * <ul>
 *   <li>int, Integer - InputInt</li>
 *   <li>float, Float - InputFloat</li>
 *   <li>double, Double - InputFloat</li>
 *   <li>boolean, Boolean - Checkbox</li>
 *   <li>String - InputText</li>
 *   <li>Enum - Combo</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 设置为回退适配器
 * PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());
 * 
 * // 对于任何未注册的对象类型，都会自动使用此适配器
 * MyCustomObject obj = ...;
 * SelectionManager.get().setCurrentSelection(obj);
 * // 属性面板会使用 GenericPropertyAdapter 显示所有公共字段
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class GenericPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericPropertyAdapter.class);
    
    /** 支持的基础类型列表 */
    private static final List<Class<?>> SUPPORTED_TYPES = List.of(
        int.class, Integer.class,
        float.class, Float.class,
        double.class, Double.class,
        boolean.class, Boolean.class,
        String.class
    );
    
    @Override
    public boolean renderProperties(Object target) {
        if (target == null) {
            UITheme.spacingLarge();
            UITheme.label("对象为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        Class<?> clazz = target.getClass();
        
        // 标题
        UITheme.title("🔍 通用属性");
        
        // 类型信息
        UITheme.beginGroup("类型信息");
        UITheme.label("类名: " + clazz.getSimpleName(), UITheme.DarkTheme.TEXT);
        UITheme.label("完整类名: " + clazz.getName(), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 获取所有字段
        Field[] fields = clazz.getDeclaredFields();
        
        if (fields.length == 0) {
            UITheme.label("无可编辑字段", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 渲染字段
        UITheme.beginGroup("字段");
        
        for (Field field : fields) {
            // 跳过静态字段和瞬态字段
            if (Modifier.isStatic(field.getModifiers()) || 
                Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object value = field.get(target);
                boolean fieldModified = renderField(field, target, value);
                modified = modified || fieldModified;
            } catch (IllegalAccessException e) {
                LOGGER.warn("[GenericPropertyAdapter] 无法访问字段: {}", field.getName());
                ImGui.textDisabled(field.getName() + " (访问失败)");
            } catch (Exception e) {
                LOGGER.error("[GenericPropertyAdapter] 渲染字段时出错: {}", field.getName(), e);
                ImGui.textDisabled(field.getName() + " (错误)");
            }
        }
        
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 提示信息
        UITheme.beginGroup("提示");
        ImGui.textWrapped("这是通用属性编辑器，仅显示基础字段。");
        ImGui.textWrapped("创建专属 PropertyAdapter 以获得更好的编辑体验。");
        UITheme.endGroup();
        
        return modified;
    }
    
    /**
     * 渲染单个字段
     * 
     * @param field 字段
     * @param target 目标对象
     * @param value 字段值
     * @return 如果字段被修改则返回 true
     */
    private boolean renderField(Field field, Object target, Object value) {
        String fieldName = field.getName();
        Class<?> type = field.getType();
        
        // 使用字段名作为唯一ID
        ImGui.pushID(fieldName);
        
        boolean modified = false;
        
        try {
            // int / Integer
            if (type == int.class || type == Integer.class) {
                int currentValue = value != null ? (int) value : 0;
                ImInt buffer = new ImInt(currentValue);
                if (ImGui.inputInt(fieldName, buffer)) {
                    setValue(field, target, buffer.get());
                    modified = true;
                }
            }
            // float / Float
            else if (type == float.class || type == Float.class) {
                float currentValue = value != null ? (float) value : 0.0f;
                ImFloat buffer = new ImFloat(currentValue);
                if (ImGui.inputFloat(fieldName, buffer, 0.1f, 1.0f, "%.3f")) {
                    setValue(field, target, buffer.get());
                    modified = true;
                }
            }
            // double / Double
            else if (type == double.class || type == Double.class) {
                double currentValue = value != null ? (double) value : 0.0;
                ImFloat buffer = new ImFloat((float) currentValue);
                if (ImGui.inputFloat(fieldName, buffer, 0.1f, 1.0f, "%.3f")) {
                    setValue(field, target, (double) buffer.get());
                    modified = true;
                }
            }
            // boolean / Boolean
            else if (type == boolean.class || type == Boolean.class) {
                boolean currentValue = value != null && (boolean) value;
                ImBoolean buffer = new ImBoolean(currentValue);
                if (ImGui.checkbox(fieldName, buffer)) {
                    setValue(field, target, buffer.get());
                    modified = true;
                }
            }
            // String
            else if (type == String.class) {
                String currentValue = value != null ? (String) value : "";
                ImString buffer = new ImString(currentValue, 256);
                
                if (ImGui.inputText(fieldName, buffer, ImGuiInputTextFlags.None)) {
                    String newValue = buffer.get();
                    setValue(field, target, newValue);
                    modified = true;
                }
            }
            // Enum
            else if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    String[] names = Arrays.stream(constants)
                        .map(Object::toString)
                        .toArray(String[]::new);
                    
                    int currentIndex = value != null ? Arrays.asList(constants).indexOf(value) : 0;
                    ImInt selected = new ImInt(Math.max(0, currentIndex));
                    
                    if (ImGui.combo(fieldName, selected, names, names.length)) {
                        setValue(field, target, constants[selected.get()]);
                        modified = true;
                    }
                }
            }
            // 不支持的类型（只读显示）
            else {
                String valueStr = value != null ? value.toString() : "null";
                ImGui.textDisabled(fieldName + ": " + valueStr);
            }
        } catch (Exception e) {
            LOGGER.error("[GenericPropertyAdapter] 渲染字段失败: {}", fieldName, e);
            ImGui.textDisabled(fieldName + " (错误)");
        } finally {
            ImGui.popID();
        }
        
        return modified;
    }
    
    /**
     * 设置字段值
     * 
     * @param field 字段
     * @param target 目标对象
     * @param newValue 新值
     */
    private void setValue(Field field, Object target, Object newValue) {
        try {
            field.setAccessible(true);
            field.set(target, newValue);
            LOGGER.debug("[GenericPropertyAdapter] 字段已更新: {} = {}", field.getName(), newValue);
        } catch (IllegalAccessException e) {
            LOGGER.error("[GenericPropertyAdapter] 无法设置字段值: {}", field.getName(), e);
            ImGui.textDisabled("(更新失败: " + field.getName() + ")");
        } catch (Exception e) {
            LOGGER.error("[GenericPropertyAdapter] 设置字段值时出错: {}", field.getName(), e);
        }
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "通用属性编辑器";
    }
    
    /**
     * 检查字段类型是否受支持
     * 
     * @param type 字段类型
     * @return 如果支持则返回 true
     */
    private boolean isSupportedType(Class<?> type) {
        return SUPPORTED_TYPES.contains(type) || type.isEnum();
    }
    
    /**
     * 获取对象的可编辑字段数量
     * 
     * @param target 目标对象
     * @return 可编辑字段数量
     */
    public int getEditableFieldCount(Object target) {
        if (target == null) return 0;
        
        int count = 0;
        for (Field field : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || 
                Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (isSupportedType(field.getType())) {
                count++;
            }
        }
        return count;
    }
}