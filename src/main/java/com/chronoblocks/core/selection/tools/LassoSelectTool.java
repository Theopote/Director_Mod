package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
// import net.minecraft.util.math.Vec3d; // 暂时不使用
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 🪃 套索选择工具
 * 
 * 通过绘制自由形状的路径来选择方块。
 * 类似图像编辑软件中的套索工具。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class LassoSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(LassoSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private final List<BlockPos> lassoPath = new ArrayList<>(); // 套索路径
    private boolean isDrawing = false; // 是否正在绘制
    private float tolerance = 2.0f; // 容差值

    @Override 
    public String getId() { 
        return "lasso"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "套索选择"; 
    }
    
    @Override
    public String getDescription() {
        return "通过绘制自由形状的路径来选择方块。类似图像编辑软件中的套索工具。";
    }
    
    @Override
    public String getIcon() {
        return "\uE954"; // Icons.LASSO (套索)
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        if (!isDrawing) {
            // 开始绘制套索
            startLassoDrawing(pos);
        } else {
            // 结束绘制套索并执行选择
            finishLassoDrawing(world, isShiftDown);
        }
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        startLassoDrawing(start);
        LOGGER.debug("[LassoSelectTool] 开始套索拖拽，起点: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (isDrawing && current != null) {
            // 添加到套索路径
            addToLassoPath(current);
            LOGGER.debug("[LassoSelectTool] 套索路径更新，当前点: {}", current);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (isDrawing) {
            // 结束套索绘制
            finishLassoDrawing(world, false);
        }
    }

    /**
     * 开始套索绘制
     */
    private void startLassoDrawing(BlockPos start) {
        lassoPath.clear();
        lassoPath.add(start);
        isDrawing = true;
        LOGGER.debug("[LassoSelectTool] 开始套索绘制，起点: {}", start);
    }

    /**
     * 添加到套索路径
     */
    private void addToLassoPath(BlockPos pos) {
        // 避免重复添加相同的点
        if (lassoPath.isEmpty() || !lassoPath.get(lassoPath.size() - 1).equals(pos)) {
            lassoPath.add(pos);
        }
    }

    /**
     * 完成套索绘制
     */
    private void finishLassoDrawing(World world, boolean isShiftDown) {
        if (lassoPath.size() < 3) {
            LOGGER.debug("[LassoSelectTool] 套索路径太短，无法形成封闭区域");
            isDrawing = false;
            return;
        }
        
        // 确保路径封闭（如果起点和终点不同，添加起点）
        if (!lassoPath.get(0).equals(lassoPath.get(lassoPath.size() - 1))) {
            lassoPath.add(lassoPath.get(0));
        }
        
        LOGGER.debug("[LassoSelectTool] 完成套索绘制，路径点数: {}", lassoPath.size());
        
        // 执行套索选择
        Set<BlockPos> lassoBlocks = selectWithLasso(world);
        
        if (lassoBlocks.isEmpty()) {
            LOGGER.debug("[LassoSelectTool] 套索区域内无方块");
        } else {
            if (isShiftDown) {
                // Shift+点击：添加到现有选择
                currentSelection.addBlocks(lassoBlocks);
            } else {
                // 普通点击：替换当前选择
                currentSelection.clear();
                currentSelection.addBlocks(lassoBlocks);
            }
            
            LOGGER.info("[LassoSelectTool] 套索选择完成，选择 {} 个方块", lassoBlocks.size());
        }
        
        // 重置状态
        lassoPath.clear();
        isDrawing = false;
    }

    /**
     * 套索选择算法
     */
    private Set<BlockPos> selectWithLasso(World world) {
        Set<BlockPos> result = new HashSet<>();
        
        if (lassoPath.size() < 3) {
            return result;
        }
        
        // 计算套索的边界框
        BlockPos min = lassoPath.get(0);
        BlockPos max = lassoPath.get(0);
        
        for (BlockPos pos : lassoPath) {
            min = new BlockPos(
                Math.min(min.getX(), pos.getX()),
                Math.min(min.getY(), pos.getY()),
                Math.min(min.getZ(), pos.getZ())
            );
            max = new BlockPos(
                Math.max(max.getX(), pos.getX()),
                Math.max(max.getY(), pos.getY()),
                Math.max(max.getZ(), pos.getZ())
            );
        }
        
        // 在边界框内检查每个方块是否在套索路径内
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    if (isPointInPolygon(pos)) {
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
     * 检查点是否在多边形内（点在多边形内算法）
     */
    private boolean isPointInPolygon(BlockPos point) {
        // 简化实现：使用射线投射算法
        // 实际应该实现更精确的多边形内点检测
        
        int intersections = 0;
        int n = lassoPath.size() - 1; // 排除重复的终点
        
        for (int i = 0; i < n; i++) {
            BlockPos p1 = lassoPath.get(i);
            BlockPos p2 = lassoPath.get((i + 1) % n);
            
            // 检查射线是否与边相交
            if (rayIntersectsSegment(point, p1, p2)) {
                intersections++;
            }
        }
        
        return (intersections % 2) == 1;
    }

    /**
     * 检查射线是否与线段相交
     */
    private boolean rayIntersectsSegment(BlockPos point, BlockPos p1, BlockPos p2) {
        // 简化实现：检查水平射线是否与线段相交
        if (p1.getY() > point.getY() && p2.getY() > point.getY()) {
            return false;
        }
        if (p1.getY() < point.getY() && p2.getY() < point.getY()) {
            return false;
        }
        if (p1.getY() == p2.getY()) {
            return false;
        }
        
        // 计算交点的X坐标
        float x = p1.getX() + (float) (point.getY() - p1.getY()) * (p2.getX() - p1.getX()) / (p2.getY() - p1.getY());
        
        return x > point.getX();
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
        lassoPath.clear();
        isDrawing = false;
        LOGGER.debug("[LassoSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || isDrawing;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[LassoSelectTool] 工具激活");
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[LassoSelectTool] 工具停用");
        lassoPath.clear();
        isDrawing = false;
    }
    
    /**
     * 设置容差值
     */
    public void setTolerance(float tolerance) {
        this.tolerance = Math.max(0.1f, Math.min(10.0f, tolerance));
    }
    
    /**
     * 获取容差值
     */
    public float getTolerance() {
        return tolerance;
    }
    
    /**
     * 获取套索路径
     */
    public List<BlockPos> getLassoPath() {
        return new ArrayList<>(lassoPath);
    }
    
    /**
     * 是否正在绘制
     */
    public boolean isDrawing() {
        return isDrawing;
    }
}
