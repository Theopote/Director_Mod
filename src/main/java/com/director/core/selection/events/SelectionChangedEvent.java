package com.director.core.selection.events;

import com.director.core.selection.SelectionState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Set;

/**
 * 🎯 选择变化事件
 * <p>
 * 当选择状态发生变化时触发，用于通知UI组件更新选择显示。
 * 
 * @author Director Team
 * @version 1.0
 */
public class SelectionChangedEvent extends SelectionEvent {
    
    private final SelectionState selectionState;
    private final Set<BlockPos> addedBlocks;
    private final Set<BlockPos> removedBlocks;
    private final String toolId;
    
    public SelectionChangedEvent(World world, SelectionState selectionState, 
                               Set<BlockPos> addedBlocks, Set<BlockPos> removedBlocks) {
        super(world);
        this.selectionState = selectionState;
        this.addedBlocks = addedBlocks;
        this.removedBlocks = removedBlocks;
        this.toolId = selectionState.getSourceToolId() != null ? selectionState.getSourceToolId() : "unknown";
    }
    
    @Override
    public String getEventType() {
        return "SELECTION_CHANGED";
    }
    
    @Override
    public String getDescription() {
        return String.format("选择变化: +%d -%d 总计:%d (工具: %s)", 
            addedBlocks.size(), 
            removedBlocks.size(), 
            selectionState.getSelectedBlocks().size(),
            toolId);
    }
    
    /**
     * 获取选择状态
     */
    public SelectionState getSelectionState() {
        return selectionState;
    }
    
    /**
     * 获取新增的方块
     */
    public Set<BlockPos> getAddedBlocks() {
        return addedBlocks;
    }
    
    /**
     * 获取移除的方块
     */
    public Set<BlockPos> getRemovedBlocks() {
        return removedBlocks;
    }
    
    /**
     * 获取工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取选择的总方块数
     */
    public int getTotalBlockCount() {
        return selectionState.getSelectedBlocks().size();
    }
    
    /**
     * 检查是否有新增方块
     */
    public boolean hasAddedBlocks() {
        return !addedBlocks.isEmpty();
    }
    
    /**
     * 检查是否有移除方块
     */
    public boolean hasRemovedBlocks() {
        return !removedBlocks.isEmpty();
    }
}
