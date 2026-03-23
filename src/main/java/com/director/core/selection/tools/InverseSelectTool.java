package com.director.core.selection.tools;

import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 🔄 反向选择工具
 * 
 * 选择未被选择的方块。用于快速反选区域。
 * 
 * @author Director Team
 * @version 1.0
 */
public class InverseSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(InverseSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private BlockPos selectionCenter = null;
    private int selectionRadius = 10; // 选择半径

    @Override 
    public String getId() { 
        return "inverse"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "反向选择"; 
    }
    
    @Override
    public String getDescription() {
        return "选择未被选择的方块。用于快速反选区域。";
    }
    
    @Override
    public String getIcon() {
        return "\uE996"; // Icons.UNDO (反向)
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        selectionCenter = pos;
        
        LOGGER.debug("[InverseSelectTool] 反向选择中心: {}，半径: {}", pos, selectionRadius);
        
        // 执行反向选择
        Set<BlockPos> inverseBlocks = selectInverse(world, selectionCenter, selectionRadius);
        
        if (inverseBlocks.isEmpty()) {
            LOGGER.debug("[InverseSelectTool] 反向选择区域无方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(inverseBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(inverseBlocks);
        }
        
        LOGGER.info("[InverseSelectTool] 反向选择完成，选择 {} 个方块", inverseBlocks.size());
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        selectionCenter = start;
        LOGGER.debug("[InverseSelectTool] 开始调整反向选择区域，中心: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (selectionCenter != null) {
            // 根据拖拽距离调整半径
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(current));
            selectionRadius = Math.max(1, Math.min(50, distance));
            LOGGER.debug("[InverseSelectTool] 调整半径: {}", selectionRadius);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (selectionCenter != null) {
            // 最终确定半径
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(end));
            selectionRadius = Math.max(1, Math.min(50, distance));
            
            // 重新执行选择
            Set<BlockPos> inverseBlocks = selectInverse(world, selectionCenter, selectionRadius);
            
            currentSelection.clear();
            currentSelection.addBlocks(inverseBlocks);
            
            LOGGER.info("[InverseSelectTool] 反向选择完成，半径: {}，选择 {} 个方块", 
                       selectionRadius, inverseBlocks.size());
        }
    }

    /**
     * 反向选择算法
     */
    private Set<BlockPos> selectInverse(World world, BlockPos center, int radius) {
        Set<BlockPos> result = new HashSet<>();
        
        // 获取指定半径内的所有方块
        Set<BlockPos> allBlocks = getAllBlocksInRadius(world, center, radius);
        
        // 获取当前已选择的方块（这里需要从SelectionManager获取）
        Set<BlockPos> selectedBlocks = getCurrentSelectedBlocks();
        
        // 反向选择：选择未选中的方块
        for (BlockPos pos : allBlocks) {
            if (!selectedBlocks.contains(pos) && isValidBlock(world, pos)) {
                result.add(pos);
            }
        }
        
        return result;
    }
    
    /**
     * 获取指定半径内的所有方块
     */
    private Set<BlockPos> getAllBlocksInRadius(World world, BlockPos center, int radius) {
        Set<BlockPos> blocks = new HashSet<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    
                    // 检查是否在半径内
                    if (center.getSquaredDistance(pos) <= radius * radius) {
                        blocks.add(pos);
                    }
                }
            }
        }
        
        return blocks;
    }
    
    /**
     * 获取当前已选择的方块
     * 这里需要从SelectionManager获取，简化实现
     */
    private Set<BlockPos> getCurrentSelectedBlocks() {
        // 简化实现：返回空集合
        // 实际应该从SelectionManager获取当前选择状态
        return new HashSet<>();
    }
    
    /**
     * 检查方块是否有效（非空气）
     */
    private boolean isValidBlock(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return !state.isAir();
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        selectionCenter = null;
        LOGGER.debug("[InverseSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || selectionCenter != null;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[InverseSelectTool] 工具激活，半径: {}", selectionRadius);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[InverseSelectTool] 工具停用");
        selectionCenter = null;
    }
    
    /**
     * 设置选择半径
     */
    public void setSelectionRadius(int radius) {
        this.selectionRadius = Math.max(1, Math.min(50, radius));
    }
    
    /**
     * 获取选择半径
     */
    public int getSelectionRadius() {
        return selectionRadius;
    }
    
    /**
     * 获取选择中心
     */
    public BlockPos getSelectionCenter() {
        return selectionCenter;
    }
}
