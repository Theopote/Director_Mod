package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.List;

/**
 * 列表/数组属性适配器 - ChronoBlocks Properties System v4.5
 * 
 * <p>通用的集合 / 数组显示适配器。
 * 会自动为每个元素调用对应的 PropertyAdapter，实现递归编辑。
 * 
 * <h3>支持的类型</h3>
 * <ul>
 *   <li>java.util.List</li>
 *   <li>Java 数组 (Object[])</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>树形展开 - 每个元素可折叠</li>
 *   <li>递归渲染 - 自动为元素查找适配器</li>
 *   <li>索引显示 - 清晰的元素编号</li>
 *   <li>统计信息 - 元素数量、类型分布</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 自动注册（在 Bootstrap 中）
 * PropertyAdapterRegistry.registerAdapter(List.class, new ListPropertyAdapter());
 * 
 * // 使用
 * List<Keyframe> keyframes = Arrays.asList(...);
 * SelectionManager.get().setCurrentSelection(keyframes);
 * 
 * // 属性面板自动显示可展开的列表
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class ListPropertyAdapter implements IPropertyAdapter<Object> {
    // Logger 暂时保留，用于未来的调试需求
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(ListPropertyAdapter.class);
    
    @Override
    public boolean renderProperties(Object target) {
        if (target == null) {
            UITheme.spacingLarge();
            UITheme.label("列表为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 判断类型
        boolean isArray = target.getClass().isArray();
        List<?> list = null;
        int size;
        
        if (isArray) {
            size = Array.getLength(target);
        } else if (target instanceof List) {
            list = (List<?>) target;
            size = list.size();
        } else {
            UITheme.spacingLarge();
            UITheme.label("不支持的集合类型: " + target.getClass().getSimpleName(), 
                UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 标题
        UITheme.title(isArray ? "📊 数组属性" : "📊 列表属性");
        
        // 统计信息
        UITheme.beginGroup("统计信息");
        UITheme.label("元素数量: " + size, UITheme.DarkTheme.TEXT);
        
        if (size > 0) {
            Object firstElement = isArray ? Array.get(target, 0) : list.get(0);
            if (firstElement != null) {
                UITheme.label("元素类型: " + firstElement.getClass().getSimpleName(), 
                    UITheme.DarkTheme.TEXT_DISABLED);
            }
        }
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 空列表提示
        if (size == 0) {
            UITheme.label("列表为空", UITheme.DarkTheme.TEXT_DISABLED);
            return modified;
        }
        
        // 元素列表
        UITheme.beginGroup("元素");
        
        // 限制显示数量（性能优化）
        int maxDisplay = Math.min(size, 50);
        if (size > maxDisplay) {
            UITheme.label(String.format("显示前 %d 个元素（共 %d 个）", maxDisplay, size), 
                UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.spacingSmall();
        }
        
        for (int i = 0; i < maxDisplay; i++) {
            ImGui.pushID(i);
            
            Object element = isArray ? Array.get(target, i) : list.get(i);
            
            // 元素树节点
            String nodeLabel = String.format("[%d] %s", i, 
                element != null ? element.getClass().getSimpleName() : "null");
            
            int treeFlags = ImGuiTreeNodeFlags.DefaultOpen;
            if (i >= 10) { // 10 个以后默认折叠
                treeFlags = ImGuiTreeNodeFlags.None;
            }
            
            if (ImGui.treeNodeEx(nodeLabel, treeFlags)) {
                if (element != null) {
                    // 查找元素的适配器并渲染
                    IPropertyAdapter<Object> elementAdapter = 
                        PropertyAdapterRegistry.getAdapterFor(element);
                    
                    if (elementAdapter != null) {
                        boolean elementModified = elementAdapter.renderProperties(element);
                        modified = modified || elementModified;
                    } else {
                        UITheme.label("无可用适配器", UITheme.DarkTheme.TEXT_DISABLED);
                        ImGui.text("toString: " + element.toString());
                    }
                } else {
                    UITheme.label("null", UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                ImGui.treePop();
            }
            
            ImGui.popID();
        }
        
        if (size > maxDisplay) {
            UITheme.spacingSmall();
            UITheme.label(String.format("... 还有 %d 个元素未显示", size - maxDisplay), 
                UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        UITheme.endGroup();
        
        return modified;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Object> getTargetType() {
        // 返回 List.class，但由于泛型擦除，使用 Object.class
        // 实际类型检查通过 supports() 方法
        return (Class<Object>) (Class<?>) List.class;
    }
    
    @Override
    public String getAdapterName() {
        return "列表/数组属性";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        return target instanceof List || target.getClass().isArray();
    }
}

