package com.director.core.selection.events;

import com.director.core.selection.SelectionTool;
import net.minecraft.world.World;

/**
 * 🎯 工具停用事件
 * <p>
 * 当选择工具被停用时触发，用于清理工具状态和UI更新。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ToolDeactivatedEvent extends SelectionEvent {
    
    private final SelectionTool tool;
    private final String nextToolId;
    
    public ToolDeactivatedEvent(World world, SelectionTool tool, String nextToolId) {
        super(world);
        this.tool = tool;
        this.nextToolId = nextToolId;
    }
    
    @Override
    public String getEventType() {
        return "TOOL_DEACTIVATED";
    }
    
    @Override
    public String getDescription() {
        return String.format("工具停用: %s (下一个: %s)", 
            tool.getDisplayName(), 
            nextToolId != null ? nextToolId : "无");
    }
    
    /**
     * 获取停用的工具
     */
    public SelectionTool getTool() {
        return tool;
    }
    

    /**
     * 获取工具ID
     */
    public String getToolId() {
        return tool.getId();
    }

}
