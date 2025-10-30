package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
// import net.minecraft.util.math.Vec3d; // 暂时不使用
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 🔵 圆柱体选择工具
 * 
 * 选择指定半径和高度的圆柱体区域内的所有方块。
 * 支持垂直和水平圆柱体。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class CylinderSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(CylinderSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private BlockPos center = null;
    private float radius = 5.0f; // 默认半径
    private int height = 10; // 默认高度
    private Direction.Axis axis = Direction.Axis.Y; // 默认垂直圆柱体
    private final float minRadius = 1.0f;
    private final float maxRadius = 50.0f;
    private final int minHeight = 1;
    private final int maxHeight = 100;

    @Override 
    public String getId() { 
        return "cylinder"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "圆柱体选择"; 
    }
    
    @Override
    public String getDescription() {
        return "选择圆柱体区域内的方块。支持垂直和水平圆柱体。";
    }
    
    @Override
    public String getIcon() {
        return "\uE928"; // Icons.CYLINDER
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        center = pos;
        
        LOGGER.debug("[CylinderSelectTool] 圆柱体选择中心: {}，半径: {}，高度: {}，轴向: {}", 
                    pos, radius, height, axis);
        
        // 执行圆柱体选择
        Set<BlockPos> cylinderBlocks = selectCylinder(world, center, radius, height, axis);
        
        if (cylinderBlocks.isEmpty()) {
            LOGGER.debug("[CylinderSelectTool] 圆柱体内无方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(cylinderBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(cylinderBlocks);
        }
        
        LOGGER.info("[CylinderSelectTool] 圆柱体选择完成，选择 {} 个方块", cylinderBlocks.size());
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        center = start;
        LOGGER.debug("[CylinderSelectTool] 开始调整圆柱体大小，中心: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (center != null) {
            // 根据拖拽距离调整半径和高度
            updateCylinderParameters(center, current);
            LOGGER.debug("[CylinderSelectTool] 调整半径: {}，高度: {}", radius, height);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (center != null) {
            // 最终确定参数
            updateCylinderParameters(center, end);
            
            // 重新执行选择
            Set<BlockPos> cylinderBlocks = selectCylinder(world, center, radius, height, axis);
            
            currentSelection.clear();
            currentSelection.addBlocks(cylinderBlocks);
            
            LOGGER.info("[CylinderSelectTool] 圆柱体选择完成，半径: {}，高度: {}，选择 {} 个方块", 
                       radius, height, cylinderBlocks.size());
        }
    }

    /**
     * 更新圆柱体参数
     */
    private void updateCylinderParameters(BlockPos center, BlockPos current) {
        int dx = Math.abs(current.getX() - center.getX());
        int dy = Math.abs(current.getY() - center.getY());
        int dz = Math.abs(current.getZ() - center.getZ());
        
        // 根据主要变化方向确定轴向
        if (dy >= dx && dy >= dz) {
            axis = Direction.Axis.Y; // 垂直圆柱体
            radius = Math.max(minRadius, Math.min(maxRadius, (float) Math.max(dx, dz)));
            height = Math.max(minHeight, Math.min(maxHeight, dy * 2 + 1));
        } else if (dx >= dz) {
            axis = Direction.Axis.X; // X轴圆柱体
            radius = Math.max(minRadius, Math.min(maxRadius, (float) Math.max(dy, dz)));
            height = Math.max(minHeight, Math.min(maxHeight, dx * 2 + 1));
        } else {
            axis = Direction.Axis.Z; // Z轴圆柱体
            radius = Math.max(minRadius, Math.min(maxRadius, (float) Math.max(dx, dy)));
            height = Math.max(minHeight, Math.min(maxHeight, dz * 2 + 1));
        }
    }

    /**
     * 圆柱体选择算法
     */
    private Set<BlockPos> selectCylinder(World world, BlockPos center, float radius, int height, Direction.Axis axis) {
        Set<BlockPos> result = new HashSet<>();
        
        int radiusInt = (int) Math.ceil(radius);
        int halfHeight = height / 2;
        
        for (int i = 0; i < height; i++) {
            int offset = i - halfHeight;
            BlockPos layerCenter = getLayerCenter(center, offset, axis);
            
            // 在每一层中选择圆形区域
            for (int x = -radiusInt; x <= radiusInt; x++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    BlockPos pos = getLayerPos(layerCenter, x, z, axis);
                    
                    // 计算距离
                    double distance = calculateDistance(x, z);
                    
                    // 如果在圆形内
                    if (distance <= radius) {
                        if (isValidBlock(world, pos)) {
                            result.add(pos);
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取层的中心点
     */
    private BlockPos getLayerCenter(BlockPos center, int offset, Direction.Axis axis) {
        return switch (axis) {
            case Y -> center.add(0, offset, 0);
            case X -> center.add(offset, 0, 0);
            case Z -> center.add(0, 0, offset);
        };
    }
    
    /**
     * 获取层中的位置
     */
    private BlockPos getLayerPos(BlockPos layerCenter, int x, int z, Direction.Axis axis) {
        return switch (axis) {
            case Y -> layerCenter.add(x, 0, z);
            case X -> layerCenter.add(0, x, z);
            case Z -> layerCenter.add(x, z, 0);
        };
    }
    
    /**
     * 计算距离
     */
    private double calculateDistance(int x, int z) {
        return Math.sqrt(x * x + z * z);
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
        center = null;
        LOGGER.debug("[CylinderSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || center != null;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[CylinderSelectTool] 工具激活，半径: {}，高度: {}", radius, height);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[CylinderSelectTool] 工具停用");
        center = null;
    }
    
    /**
     * 设置半径
     */
    public void setRadius(float radius) {
        this.radius = Math.max(minRadius, Math.min(maxRadius, radius));
    }
    
    /**
     * 设置高度
     */
    public void setHeight(int height) {
        this.height = Math.max(minHeight, Math.min(maxHeight, height));
    }
    
    /**
     * 设置轴向
     */
    public void setAxis(Direction.Axis axis) {
        this.axis = axis;
    }
    
    /**
     * 获取半径
     */
    public float getRadius() {
        return radius;
    }
    
    /**
     * 获取高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 获取轴向
     */
    public Direction.Axis getAxis() {
        return axis;
    }
}
