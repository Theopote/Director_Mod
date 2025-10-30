package com.chronoblocks.core.selection;

import java.util.*;

/**
 * 🧰 SelectionRegistry - 工具注册表
 * 
 * 管理所有已注册的 SelectionTool。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionRegistry {

    private final Map<String, SelectionTool> tools = new LinkedHashMap<>();
    private final List<String> toolOrder = new ArrayList<>();

    /**
     * 注册选择工具
     */
    public void register(SelectionTool tool) {
        tools.put(tool.getId(), tool);
        if (!toolOrder.contains(tool.getId())) {
            toolOrder.add(tool.getId());
        }
    }

    /**
     * 获取指定ID的工具
     */
    public SelectionTool getTool(String id) {
        return tools.get(id);
    }

    /**
     * 获取所有工具
     */
    public Collection<SelectionTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 按注册顺序获取所有工具
     */
    public List<SelectionTool> getToolsInOrder() {
        List<SelectionTool> orderedTools = new ArrayList<>();
        for (String toolId : toolOrder) {
            SelectionTool tool = tools.get(toolId);
            if (tool != null) {
                orderedTools.add(tool);
            }
        }
        return orderedTools;
    }

    /**
     * 检查工具是否已注册
     */
    public boolean isRegistered(String id) {
        return tools.containsKey(id);
    }

    /**
     * 获取工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 清除所有工具
     */
    public void clear() {
        tools.clear();
        toolOrder.clear();
    }

    /**
     * 获取工具ID列表
     */
    public Set<String> getToolIds() {
        return new HashSet<>(tools.keySet());
    }

    /**
     * 获取下一个工具（循环）
     */
    public SelectionTool getNextTool(SelectionTool currentTool) {
        if (currentTool == null || toolOrder.isEmpty()) {
            return toolOrder.isEmpty() ? null : tools.get(toolOrder.get(0));
        }
        
        int currentIndex = toolOrder.indexOf(currentTool.getId());
        if (currentIndex == -1) {
            return tools.get(toolOrder.get(0));
        }
        
        int nextIndex = (currentIndex + 1) % toolOrder.size();
        return tools.get(toolOrder.get(nextIndex));
    }

    /**
     * 获取上一个工具（循环）
     */
    public SelectionTool getPreviousTool(SelectionTool currentTool) {
        if (currentTool == null || toolOrder.isEmpty()) {
            return toolOrder.isEmpty() ? null : tools.get(toolOrder.get(0));
        }
        
        int currentIndex = toolOrder.indexOf(currentTool.getId());
        if (currentIndex == -1) {
            return tools.get(toolOrder.get(0));
        }
        
        int prevIndex = (currentIndex - 1 + toolOrder.size()) % toolOrder.size();
        return tools.get(toolOrder.get(prevIndex));
    }
}
