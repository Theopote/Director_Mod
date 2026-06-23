package com.director.core.selection.tools;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🔗 关联选择工具
 * 
 * 选择一个方块，然后自动延伸选择所有与之相连的同类方块。
 * 比如一整面墙或一整棵树的树叶。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ConnectedSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectedSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private final int maxSelectionSize = 50000; // 关联选择可以更大
    private boolean includeDiagonal = false; // 是否包含对角线连接

    @Override 
    public String getId() { 
        return "connected"; 
    }
    
    @Override
    public String getIcon() {
        return Icons.LINK;
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        BlockState targetState = world.getBlockState(pos);
        if (targetState.isAir()) {
            LOGGER.debug("[ConnectedSelectTool] 目标方块是空气，跳过");
            return;
        }
        
        LOGGER.debug("[ConnectedSelectTool] 关联选择，从: {}，方块类型: {}", pos, targetState.getBlock());
        
        // 执行关联选择算法
        Set<BlockPos> connectedBlocks = selectConnected(world, pos, targetState);
        
        if (connectedBlocks.isEmpty()) {
            LOGGER.debug("[ConnectedSelectTool] 未找到关联方块");
            return;
        }
        
        SelectionManager manager = DirectorServices.get().selection();
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            manager.addAllToSelection(connectedBlocks);
        } else {
            // 普通点击：替换当前选择
            manager.clearSelection();
            manager.addAllToSelection(connectedBlocks);
        }

        // 与 SelectionManager 保持一致，确保渲染/面板读取的是同一份选区。
        currentSelection = SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
        
        LOGGER.info("[ConnectedSelectTool] 关联选择完成，选择 {} 个方块", connectedBlocks.size());
    }

    /**
     * 关联选择算法（改进的洪填充算法）
     */
    private Set<BlockPos> selectConnected(World world, BlockPos startPos, BlockState targetState) {
        Set<BlockPos> result = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.offer(startPos);
        visited.add(startPos);
        
        int count = 0;
        while (!queue.isEmpty() && count < maxSelectionSize) {
            BlockPos current = queue.poll();
            result.add(current);
            count++;
            
            // 检查相邻方块
            for (BlockPos neighbor : getNeighbors(current)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                BlockState neighborState = world.getBlockState(neighbor);
                if (isConnectedBlock(targetState, neighborState)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        
        if (count >= maxSelectionSize) {
            LOGGER.warn("[ConnectedSelectTool] 达到最大选择数量限制: {}", maxSelectionSize);
        }
        
        return result;
    }
    
    /**
     * 获取相邻方块
     */
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        // 6个直接相邻方向
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        neighbors.add(pos.up());
        neighbors.add(pos.down());
        
        // 如果包含对角线连接
        if (includeDiagonal) {
            // 12个对角线方向
            neighbors.add(pos.north().east());
            neighbors.add(pos.north().west());
            neighbors.add(pos.north().up());
            neighbors.add(pos.north().down());
            neighbors.add(pos.south().east());
            neighbors.add(pos.south().west());
            neighbors.add(pos.south().up());
            neighbors.add(pos.south().down());
            neighbors.add(pos.east().up());
            neighbors.add(pos.east().down());
            neighbors.add(pos.west().up());
            neighbors.add(pos.west().down());
        }
        
        return neighbors;
    }
    
    /**
     * 判断两个方块是否关联
     */
    private boolean isConnectedBlock(BlockState state1, BlockState state2) {
        if (state1 == null || state2 == null) {
            return false;
        }
        
        // 基础类型比较
        return state1.getBlock().equals(state2.getBlock());
        
        // 可以添加更复杂的关联逻辑
        // 例如：相同材质的方块、特定属性的方块等
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
        LOGGER.debug("[ConnectedSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !DirectorServices.get().selection().getSelectionSet().isEmpty();
    }

    @Override
    public void onActivate(World world) {
        SelectionManager manager = DirectorServices.get().selection();
        currentSelection = SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
        LOGGER.debug("[ConnectedSelectTool] 工具激活");
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[ConnectedSelectTool] 工具停用");
    }
    
    /**
     * 设置是否包含对角线连接
     */
    public void setIncludeDiagonal(boolean includeDiagonal) {
        this.includeDiagonal = includeDiagonal;
    }
    
    /**
     * 获取是否包含对角线连接
     */
    public boolean isIncludeDiagonal() {
        return includeDiagonal;
    }
    
    /**
     * 设置最大选择数量
     */
    public void setMaxSelectionSize(int maxSize) {
        // 这里可以添加设置最大选择数量的逻辑
        LOGGER.debug("[ConnectedSelectTool] 最大选择数量设置为: {}", maxSize);
    }
    
    /**
     * 获取最大选择数量
     */
    public int getMaxSelectionSize() {
        return maxSelectionSize;
    }
}
