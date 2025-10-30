package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.block.BlockState;
// import net.minecraft.util.hit.BlockHitResult; // 暂时不使用
// import net.minecraft.util.hit.HitResult; // 暂时不使用
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🔲 面选择工具
 * 
 * 选择某个方块的特定面，并自动延伸至相连的同类方块（或指定材质）。
 * 对大型平面非常高效。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FaceSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(FaceSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    @SuppressWarnings("unused")
    private Direction selectedFace = null;
    private final int maxSelectionSize = 50000; // 面选择可以更大

    @Override 
    public String getId() { 
        return "face"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "面选择工具"; 
    }
    
    @Override
    public String getDescription() {
        return "选择方块的特定面，并延伸至相连的同类方块。适合选择大型平面。";
    }
    
    @Override
    public String getIcon() {
        return "\uE91F"; // Icons.COLOR (面选择)
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        // 获取点击的面
        Direction face = getClickedFace(world, pos);
        if (face == null) {
            LOGGER.debug("[FaceSelectTool] 无法确定点击的面");
            return;
        }
        
        selectedFace = face;
        BlockState targetState = world.getBlockState(pos);
        
        LOGGER.debug("[FaceSelectTool] 面选择: {} 面，方块类型: {}", face, targetState.getBlock());
        
        // 执行面选择算法
        Set<BlockPos> faceBlocks = selectFace(world, pos, face, targetState);
        
        if (faceBlocks.isEmpty()) {
            LOGGER.debug("[FaceSelectTool] 未找到面方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(faceBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(faceBlocks);
        }
        
        LOGGER.info("[FaceSelectTool] 面选择完成，选择 {} 个方块", faceBlocks.size());
    }

    /**
     * 获取点击的面（简化实现）
     */
    private Direction getClickedFace(World world, BlockPos pos) {
        // 这里应该根据鼠标点击位置和方块几何计算点击的面
        // 简化实现：默认选择北面
        return Direction.NORTH;
    }

    /**
     * 面选择算法
     */
    private Set<BlockPos> selectFace(World world, BlockPos startPos, Direction face, BlockState targetState) {
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
            
            // 在当前面的平面上查找相邻方块
            for (Direction direction : getFaceDirections(face)) {
                BlockPos neighbor = current.offset(direction);
                
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
            LOGGER.warn("[FaceSelectTool] 达到最大选择数量限制: {}", maxSelectionSize);
        }
        
        return result;
    }
    
    /**
     * 获取面的方向（在面的平面上的方向）
     */
    private List<Direction> getFaceDirections(Direction face) {
        return switch (face) {
            case NORTH, SOUTH -> Arrays.asList(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);
            case EAST, WEST -> Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN);
            case UP, DOWN -> Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        };
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
        return true;
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        selectedFace = null;
        LOGGER.debug("[FaceSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty();
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[FaceSelectTool] 工具激活");
        selectedFace = null;
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[FaceSelectTool] 工具停用");
        selectedFace = null;
    }
}
