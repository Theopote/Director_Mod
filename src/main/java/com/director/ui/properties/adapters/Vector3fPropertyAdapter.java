package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vector3f 属性适配器 - Director Properties System v4.5
 * 
 * <p>专用于 JOML {@link Vector3f} 类型的属性编辑器。
 * 提供直观的 XYZ 三轴拖动条。
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>位置 (Position)</li>
 *   <li>旋转 (Rotation)</li>
 *   <li>缩放 (Scale)</li>
 *   <li>速度 (Velocity)</li>
 *   <li>方向 (Direction)</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>DragFloat3 - 三轴同时显示</li>
 *   <li>实时预览 - 修改即时生效</li>
 *   <li>精度控制 - 0.1 步长</li>
 *   <li>范围提示 - 可选的最小/最大值</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 自动注册（在 Bootstrap 中）
 * PropertyAdapterRegistry.registerAdapter(Vector3f.class, new Vector3fPropertyAdapter());
 * 
 * // 使用
 * Vector3f position = new Vector3f(5.0f, 2.0f, 0.0f);
 * SelectionManager.get().setCurrentSelection(position);
 * 
 * // 属性面板自动显示 XYZ 拖动条
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class Vector3fPropertyAdapter implements IPropertyAdapter<Vector3f> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Vector3fPropertyAdapter.class);
    
    @Override
    public boolean renderProperties(Vector3f target) {
        if (target == null) {
            UITheme.spacingLarge();
            UITheme.label("Vector3f 为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 标题
        UITheme.title("📐 Vector3f 属性");
        
        // XYZ 编辑
        UITheme.beginGroup("坐标");
        
        float[] values = {target.x, target.y, target.z};
        
        if (ImGui.dragFloat3("XYZ", values, 0.1f)) {
            target.set(values[0], values[1], values[2]);
            modified = true;
            LOGGER.debug("[Vector3fPropertyAdapter] Vector3f 已更新: ({}, {}, {})", 
                values[0], values[1], values[2]);
        }
        
        UITheme.spacingSmall();
        
        // 显示当前值（格式化）
        UITheme.label(String.format("当前值: (%.2f, %.2f, %.2f)", target.x, target.y, target.z), 
            UITheme.DarkTheme.TEXT_DISABLED);
        
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 快捷操作
        UITheme.beginGroup("快捷操作");
        
        if (ImGui.button("归零", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            target.set(0, 0, 0);
            modified = true;
        }
        ImGui.sameLine();
        
        if (ImGui.button("重置为 1", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            target.set(1, 1, 1);
            modified = true;
        }
        ImGui.sameLine();
        
        if (ImGui.button("归一化", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            target.normalize();
            modified = true;
        }
        
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 高级信息
        UITheme.beginGroup("向量信息");
        float length = target.length();
        UITheme.label(String.format("长度: %.3f", length), UITheme.DarkTheme.TEXT_DISABLED);
        
        float lengthSquared = target.lengthSquared();
        UITheme.label(String.format("长度平方: %.3f", lengthSquared), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.endGroup();
        
        return modified;
    }

    @Override
    public Class<Vector3f> getTargetType() {
        return Vector3f.class;
    }
    
    @Override
    public String getAdapterName() {
        return "Vector3f 属性";
    }
}

