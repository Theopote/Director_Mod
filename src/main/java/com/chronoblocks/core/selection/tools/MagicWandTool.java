package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ✨ 魔棒工具
 * 
 * 根据类型或连接性选择相连方块（类似填充算法）。
 * - 单击：选择相连的同类型方块
 * - Shift+单击：添加到现有选择
 * - 支持最大选择数量限制
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class MagicWandTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicWandTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private final int maxSelectionSize = 10000; // 防止选择过多方块

    @Override 
    public String getId() { 
        return "wand"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "魔棒工具"; 
    }
    
    @Override
    public String getDescription() {
        return "选择相连的同类型方块。类似图像编辑软件的魔棒工具。";
    }
    
    @Override
    public String getIcon() {
        return "\uE958"; // Icons.MAGIC
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        LOGGER.debug("[MagicWandTool] 点击方块: {} (Shift: {})", pos, isShiftDown);
        
        BlockState targetState = world.getBlockState(pos);
        if (targetState.isAir()) {
            LOGGER.debug("[MagicWandTool] 目标方块是空气，跳过");
            return;
        }
        
        // 执行洪填充算法选择相连的同类型方块
        Set<BlockPos> connectedBlocks = floodFill(world, pos, targetState);
        
        if (connectedBlocks.isEmpty()) {
            LOGGER.debug("[MagicWandTool] 未找到相连方块");
            return;
        }
        
        LOGGER.debug("[MagicWandTool] 找到 {} 个相连方块", connectedBlocks.size());
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(connectedBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(connectedBlocks);
        }
        
        LOGGER.info("[MagicWandTool] 魔棒选择完成，当前选择包含 {} 个方块", currentSelection.size());
    }

    /**
     * 洪填充算法 - 选择相连的同类型方块
     */
    private Set<BlockPos> floodFill(World world, BlockPos startPos, BlockState targetState) {
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
            
            // 检查6个相邻方向
            for (BlockPos neighbor : getNeighbors(current)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                BlockState neighborState = world.getBlockState(neighbor);
                if (isSameBlockType(targetState, neighborState)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        
        if (count >= maxSelectionSize) {
            LOGGER.warn("[MagicWandTool] 达到最大选择数量限制: {}", maxSelectionSize);
        }
        
        return result;
    }
    
    /**
     * 获取方块的6个相邻位置
     */
    private List<BlockPos> getNeighbors(BlockPos pos) {
        return Arrays.asList(
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.up(),
            pos.down()
        );
    }
    
    /**
     * 判断两个方块状态是否为相同类型
     */
    private boolean isSameBlockType(BlockState state1, BlockState state2) {
        if (state1 == null || state2 == null) {
            return false;
        }
        
        // 比较方块类型
        if (!state1.getBlock().equals(state2.getBlock())) {
            return false;
        }
        
        // 可以在这里添加更复杂的比较逻辑
        // 例如：比较特定的属性值
        
        return true;
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        LOGGER.debug("[MagicWandTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty();
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[MagicWandTool] 工具激活");
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[MagicWandTool] 工具停用");
    }
    
    /**
     * 设置最大选择数量
     */
    public void setMaxSelectionSize(int maxSize) {
        // 这里可以添加设置最大选择数量的逻辑
        LOGGER.debug("[MagicWandTool] 最大选择数量设置为: {}", maxSize);
    }
    
    /**
     * 获取最大选择数量
     */
    public int getMaxSelectionSize() {
        return maxSelectionSize;
    }
}
