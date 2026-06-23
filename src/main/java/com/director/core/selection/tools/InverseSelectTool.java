package com.director.core.selection.tools;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
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
public class InverseSelectTool implements SelectionTool, SelectionOperationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(InverseSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private SelectionOperation operation = SelectionOperation.NEW;
    private BlockPos selectionCenter = null;
    private int selectionRadius = 10;

    @Override 
    public String getId() { 
        return "inverse"; 
    }
    
    @Override
    public String getIcon() {
        return Icons.UNDO;
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        selectionCenter = pos;
        
        LOGGER.debug("[InverseSelectTool] 反向选择中心: {}，半径: {}", pos, selectionRadius);
        
        Set<BlockPos> inverseBlocks = selectInverse(world, selectionCenter, selectionRadius);
        
        if (inverseBlocks.isEmpty()) {
            LOGGER.debug("[InverseSelectTool] 反向选择区域无方块");
            return;
        }
        
        SelectionOperation actualOperation = isShiftDown ? SelectionOperation.ADD : operation;
        applySelection(inverseBlocks, actualOperation);
        
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
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(current));
            selectionRadius = Math.max(1, Math.min(50, distance));
            LOGGER.debug("[InverseSelectTool] 调整半径: {}", selectionRadius);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (selectionCenter != null) {
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(end));
            selectionRadius = Math.max(1, Math.min(50, distance));
            
            Set<BlockPos> inverseBlocks = selectInverse(world, selectionCenter, selectionRadius);
            applySelection(inverseBlocks, operation);
            
            LOGGER.info("[InverseSelectTool] 反向选择完成，半径: {}，选择 {} 个方块", 
                       selectionRadius, inverseBlocks.size());
        }
    }

    private void applySelection(Set<BlockPos> blocks, SelectionOperation op) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        SelectionManager manager = DirectorServices.get().selection();
        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.addAllToSelection(blocks);
            }
            case ADD -> manager.addAllToSelection(blocks);
            case SUBTRACT -> blocks.forEach(manager::removeFromSelection);
            case INTERSECT -> {
                var current = manager.getSelectionSet().asUnmodifiableSet();
                manager.clearSelection();
                for (BlockPos blockPos : blocks) {
                    if (current.contains(blockPos)) {
                        manager.addToSelection(blockPos);
                    }
                }
            }
        }
        syncCurrentSelection(manager);
    }

    private void syncCurrentSelection(SelectionManager manager) {
        currentSelection = SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
    }

    /**
     * 反向选择算法：在球形区域内选择当前未选中的非空气方块。
     */
    private Set<BlockPos> selectInverse(World world, BlockPos center, int radius) {
        Set<BlockPos> result = new HashSet<>();
        Set<BlockPos> allBlocks = getAllBlocksInRadius(center, radius);
        Set<BlockPos> selectedBlocks = getCurrentSelectedBlocks();
        
        for (BlockPos pos : allBlocks) {
            if (!selectedBlocks.contains(pos) && isValidBlock(world, pos)) {
                result.add(pos);
            }
        }
        
        return result;
    }
    
    private Set<BlockPos> getAllBlocksInRadius(BlockPos center, int radius) {
        Set<BlockPos> blocks = new HashSet<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (center.getSquaredDistance(pos) <= radius * radius) {
                        blocks.add(pos);
                    }
                }
            }
        }
        
        return blocks;
    }
    
    private Set<BlockPos> getCurrentSelectedBlocks() {
        return new HashSet<>(DirectorServices.get().selection().getSelectionSet().asUnmodifiableSet());
    }
    
    private boolean isValidBlock(World world, BlockPos pos) {
        return !world.getBlockState(pos).isAir();
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        SelectionManager manager = DirectorServices.get().selection();
        manager.clearSelection();
        currentSelection = SelectionState.empty();
        selectionCenter = null;
        LOGGER.debug("[InverseSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !DirectorServices.get().selection().getSelectionSet().isEmpty() || selectionCenter != null;
    }

    @Override
    public void onActivate(World world) {
        syncCurrentSelection(DirectorServices.get().selection());
        LOGGER.debug("[InverseSelectTool] 工具激活，半径: {}", selectionRadius);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[InverseSelectTool] 工具停用");
        selectionCenter = null;
    }
    
    public void setSelectionRadius(int radius) {
        this.selectionRadius = Math.max(1, Math.min(50, radius));
    }
    
    public int getSelectionRadius() {
        return selectionRadius;
    }
    
    public BlockPos getSelectionCenter() {
        return selectionCenter;
    }

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    @Override
    public void setOperation(SelectionOperation operation) {
        if (operation != null) {
            this.operation = operation;
        }
    }
}
