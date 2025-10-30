package com.chronoblocks.core.selection.events;

import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.world.World;

/**
 * 🎯 工具激活事件
 * 
 * 当选择工具被激活时触发，用于通知UI组件更新工具状态。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ToolActivatedEvent extends SelectionEvent {
    
    private final SelectionTool tool;
    private final String previousToolId;
    
    public ToolActivatedEvent(World world, SelectionTool tool, String previousToolId) {
        super(world);
        this.tool = tool;
        this.previousToolId = previousToolId;
    }
    
    @Override
    public String getEventType() {
        return "TOOL_ACTIVATED";
    }
    
    @Override
    public String getDescription() {
        return String.format("工具激活: %s (之前: %s)", 
            tool.getDisplayName(), 
            previousToolId != null ? previousToolId : "无");
    }
    
    /**
     * 获取激活的工具
     */
    public SelectionTool getTool() {
        return tool;
    }
    
    /**
     * 获取之前使用的工具ID
     */
    public String getPreviousToolId() {
        return previousToolId;
    }
    
    /**
     * 获取工具ID
     */
    public String getToolId() {
        return tool.getId();
    }
    
    /**
     * 获取工具显示名称
     */
    public String getToolDisplayName() {
        return tool.getDisplayName();
    }
}
