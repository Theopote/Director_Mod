package com.chronoblocks.core.selection.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 🎯 工具点击事件
 * 
 * 当选择工具被点击时触发，用于处理工具交互逻辑。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ToolClickEvent extends SelectionEvent {
    
    private final String toolId;
    private final BlockPos clickPos;
    private final boolean isShiftDown;
    private final boolean isRightClick;
    
    public ToolClickEvent(World world, String toolId, BlockPos clickPos, 
                         boolean isShiftDown, boolean isRightClick) {
        super(world);
        this.toolId = toolId;
        this.clickPos = clickPos;
        this.isShiftDown = isShiftDown;
        this.isRightClick = isRightClick;
    }
    
    @Override
    public String getEventType() {
        return "TOOL_CLICK";
    }
    
    @Override
    public String getDescription() {
        return String.format("工具点击: %s 位置: %s (Shift: %s, 右键: %s)", 
            toolId, clickPos, isShiftDown, isRightClick);
    }
    
    /**
     * 获取工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取点击位置
     */
    public BlockPos getClickPos() {
        return clickPos;
    }
    
    /**
     * 检查是否按下Shift键
     */
    public boolean isShiftDown() {
        return isShiftDown;
    }
    
    /**
     * 检查是否右键点击
     */
    public boolean isRightClick() {
        return isRightClick;
    }
    
    /**
     * 检查是否左键点击
     */
    public boolean isLeftClick() {
        return !isRightClick;
    }
}
