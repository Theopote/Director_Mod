package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 🔺 金字塔选择工具
 * 
 * 选择金字塔形状区域内的所有方块。
 * 支持不同大小和方向的金字塔。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class PyramidSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(PyramidSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private BlockPos baseCenter = null;
    private int baseSize = 5; // 底边大小
    private int height = 5; // 金字塔高度
    private Direction direction = Direction.UP; // 金字塔朝向
    private final int minSize = 1;
    private final int maxSize = 50;

    @Override 
    public String getId() { 
        return "pyramid"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "金字塔选择"; 
    }
    
    @Override
    public String getDescription() {
        return "选择金字塔形状区域内的方块。支持不同大小和方向。";
    }
    
    @Override
    public String getIcon() {
        return "\uE939"; // Icons.DIAMOND (金字塔)
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        baseCenter = pos;
        
        LOGGER.debug("[PyramidSelectTool] 金字塔选择底边中心: {}，底边大小: {}，高度: {}，方向: {}", 
                    pos, baseSize, height, direction);
        
        // 执行金字塔选择
        Set<BlockPos> pyramidBlocks = selectPyramid(world, baseCenter, baseSize, height, direction);
        
        if (pyramidBlocks.isEmpty()) {
            LOGGER.debug("[PyramidSelectTool] 金字塔内无方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(pyramidBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(pyramidBlocks);
        }
        
        LOGGER.info("[PyramidSelectTool] 金字塔选择完成，选择 {} 个方块", pyramidBlocks.size());
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        baseCenter = start;
        LOGGER.debug("[PyramidSelectTool] 开始调整金字塔大小，底边中心: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (baseCenter != null) {
            // 根据拖拽距离调整底边大小和高度
            updatePyramidParameters(baseCenter, current);
            LOGGER.debug("[PyramidSelectTool] 调整底边大小: {}，高度: {}", baseSize, height);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (baseCenter != null) {
            // 最终确定参数
            updatePyramidParameters(baseCenter, end);
            
            // 重新执行选择
            Set<BlockPos> pyramidBlocks = selectPyramid(world, baseCenter, baseSize, height, direction);
            
            currentSelection.clear();
            currentSelection.addBlocks(pyramidBlocks);
            
            LOGGER.info("[PyramidSelectTool] 金字塔选择完成，底边大小: {}，高度: {}，选择 {} 个方块", 
                       baseSize, height, pyramidBlocks.size());
        }
    }

    /**
     * 更新金字塔参数
     */
    private void updatePyramidParameters(BlockPos center, BlockPos current) {
        int dx = Math.abs(current.getX() - center.getX());
        int dy = Math.abs(current.getY() - center.getY());
        int dz = Math.abs(current.getZ() - center.getZ());
        
        // 确定金字塔方向（朝向拖拽的主要方向）
        if (dy >= dx && dy >= dz) {
            direction = current.getY() > center.getY() ? Direction.UP : Direction.DOWN;
            baseSize = Math.max(minSize, Math.min(maxSize, Math.max(dx, dz) * 2 + 1));
            height = Math.max(minSize, Math.min(maxSize, dy));
        } else if (dx >= dz) {
            direction = current.getX() > center.getX() ? Direction.EAST : Direction.WEST;
            baseSize = Math.max(minSize, Math.min(maxSize, Math.max(dy, dz) * 2 + 1));
            height = Math.max(minSize, Math.min(maxSize, dx));
        } else {
            direction = current.getZ() > center.getZ() ? Direction.SOUTH : Direction.NORTH;
            baseSize = Math.max(minSize, Math.min(maxSize, Math.max(dx, dy) * 2 + 1));
            height = Math.max(minSize, Math.min(maxSize, dz));
        }
    }

    /**
     * 金字塔选择算法
     */
    private Set<BlockPos> selectPyramid(World world, BlockPos baseCenter, int baseSize, int height, Direction direction) {
        Set<BlockPos> result = new HashSet<>();
        
        for (int level = 0; level < height; level++) {
            int currentLevelSize = calculateLevelSize(baseSize, level, height);
            BlockPos levelCenter = getLevelCenter(baseCenter, level, direction);
            
            // 在每一层中选择正方形区域
            for (int x = -currentLevelSize / 2; x <= currentLevelSize / 2; x++) {
                for (int z = -currentLevelSize / 2; z <= currentLevelSize / 2; z++) {
                    BlockPos pos = getLevelPos(levelCenter, x, z, direction);
                    
                    if (isValidBlock(world, pos)) {
                        result.add(pos);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 计算层的尺寸
     */
    private int calculateLevelSize(int baseSize, int level, int height) {
        // 金字塔每层递减
        float ratio = 1.0f - (float) level / height;
        return Math.max(1, (int) (baseSize * ratio));
    }
    
    /**
     * 获取层的中心点
     */
    private BlockPos getLevelCenter(BlockPos baseCenter, int level, Direction direction) {
        return switch (direction) {
            case UP -> baseCenter.add(0, level, 0);
            case DOWN -> baseCenter.add(0, -level, 0);
            case EAST -> baseCenter.add(level, 0, 0);
            case WEST -> baseCenter.add(-level, 0, 0);
            case SOUTH -> baseCenter.add(0, 0, level);
            case NORTH -> baseCenter.add(0, 0, -level);
        };
    }
    
    /**
     * 获取层中的位置
     */
    private BlockPos getLevelPos(BlockPos levelCenter, int x, int z, Direction direction) {
        return switch (direction) {
            case UP, DOWN -> levelCenter.add(x, 0, z);
            case EAST, WEST -> levelCenter.add(0, x, z);
            case SOUTH, NORTH -> levelCenter.add(x, z, 0);
        };
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
        baseCenter = null;
        LOGGER.debug("[PyramidSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || baseCenter != null;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[PyramidSelectTool] 工具激活，底边大小: {}，高度: {}", baseSize, height);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[PyramidSelectTool] 工具停用");
        baseCenter = null;
    }
    
    /**
     * 设置底边大小
     */
    public void setBaseSize(int baseSize) {
        this.baseSize = Math.max(minSize, Math.min(maxSize, baseSize));
    }
    
    /**
     * 设置高度
     */
    public void setHeight(int height) {
        this.height = Math.max(minSize, Math.min(maxSize, height));
    }
    
    /**
     * 设置方向
     */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }
    
    /**
     * 获取底边大小
     */
    public int getBaseSize() {
        return baseSize;
    }
    
    /**
     * 获取高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 获取方向
     */
    public Direction getDirection() {
        return direction;
    }
}
