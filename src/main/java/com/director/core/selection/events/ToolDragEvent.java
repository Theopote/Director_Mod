package com.director.core.selection.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 🎯 工具拖拽事件
 * <p>
 * 当选择工具进行拖拽操作时触发，用于处理拖拽选择逻辑。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ToolDragEvent extends SelectionEvent {
    
    private final String toolId;
    private final BlockPos startPos;
    private final BlockPos currentPos;
    private final BlockPos endPos;
    private final DragType dragType;
    
    public enum DragType {
        START,   // 拖拽开始
        UPDATE,  // 拖拽更新
        END      // 拖拽结束
    }
    
    public ToolDragEvent(World world, String toolId, BlockPos startPos, 
                         BlockPos currentPos, BlockPos endPos, DragType dragType) {
        super(world);
        this.toolId = toolId;
        this.startPos = startPos;
        this.currentPos = currentPos;
        this.endPos = endPos;
        this.dragType = dragType;
    }
    
    @Override
    public String getEventType() {
        return "TOOL_DRAG";
    }
    
    @Override
    public String getDescription() {
        return String.format("工具拖拽: %s 类型: %s 开始: %s 当前: %s 结束: %s", 
            toolId, dragType, startPos, currentPos, endPos);
    }
    
    /**
     * 获取工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取拖拽开始位置
     */
    public BlockPos getStartPos() {
        return startPos;
    }
    
    /**
     * 获取拖拽当前位置
     */
    public BlockPos getCurrentPos() {
        return currentPos;
    }
    
    /**
     * 获取拖拽结束位置
     */
    public BlockPos getEndPos() {
        return endPos;
    }
    
    /**
     * 获取拖拽类型
     */
    public DragType getDragType() {
        return dragType;
    }
    
    /**
     * 检查是否为拖拽开始
     */
    public boolean isDragStart() {
        return dragType == DragType.START;
    }
    
    /**
     * 检查是否为拖拽更新
     */
    public boolean isDragUpdate() {
        return dragType == DragType.UPDATE;
    }
    
    /**
     * 检查是否为拖拽结束
     */
    public boolean isDragEnd() {
        return dragType == DragType.END;
    }
}
