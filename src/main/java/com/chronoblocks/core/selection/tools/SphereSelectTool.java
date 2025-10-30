package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * ⭕ 球体选择工具
 * 
 * 选择指定半径的球形区域内的所有方块。
 * 支持动态调整半径大小。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SphereSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(SphereSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private BlockPos center = null;
    private float radius = 5.0f; // 默认半径
    private final float minRadius = 1.0f;
    private final float maxRadius = 50.0f;

    @Override 
    public String getId() { 
        return "sphere"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "球体选择"; 
    }
    
    @Override
    public String getDescription() {
        return "选择球形区域内的方块。支持动态调整半径大小。";
    }
    
    @Override
    public String getIcon() {
        return "\uE98C"; // Icons.SPHERE
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        center = pos;
        
        LOGGER.debug("[SphereSelectTool] 球体选择中心: {}，半径: {}", pos, radius);
        
        // 执行球体选择
        Set<BlockPos> sphereBlocks = selectSphere(world, center, radius);
        
        if (sphereBlocks.isEmpty()) {
            LOGGER.debug("[SphereSelectTool] 球体内无方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(sphereBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(sphereBlocks);
        }
        
        LOGGER.info("[SphereSelectTool] 球体选择完成，选择 {} 个方块", sphereBlocks.size());
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        center = start;
        LOGGER.debug("[SphereSelectTool] 开始调整球体大小，中心: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (center != null) {
            // 根据拖拽距离调整半径
            float distance = (float) Math.sqrt(center.getSquaredDistance(current));
            radius = Math.max(minRadius, Math.min(maxRadius, distance));
            LOGGER.debug("[SphereSelectTool] 调整半径: {}", radius);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (center != null) {
            // 最终确定半径
            float distance = (float) Math.sqrt(center.getSquaredDistance(end));
            radius = Math.max(minRadius, Math.min(maxRadius, distance));
            
            // 重新执行选择
            Set<BlockPos> sphereBlocks = selectSphere(world, center, radius);
            
            currentSelection.clear();
            currentSelection.addBlocks(sphereBlocks);
            
            LOGGER.info("[SphereSelectTool] 球体选择完成，半径: {}，选择 {} 个方块", radius, sphereBlocks.size());
        }
    }

    /**
     * 球体选择算法
     */
    private Set<BlockPos> selectSphere(World world, BlockPos center, float radius) {
        Set<BlockPos> result = new HashSet<>();
        
        int radiusInt = (int) Math.ceil(radius);
        Vec3d centerVec = Vec3d.ofCenter(center);
        
        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    BlockPos pos = center.add(x, y, z);
                    
                    // 计算距离
                    Vec3d posVec = Vec3d.ofCenter(pos);
                    double distance = centerVec.distanceTo(posVec);
                    
                    // 如果在球体内
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
        LOGGER.debug("[SphereSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || center != null;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[SphereSelectTool] 工具激活，半径: {}", radius);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[SphereSelectTool] 工具停用");
        center = null;
    }
    
    /**
     * 设置半径
     */
    public void setRadius(float radius) {
        this.radius = Math.max(minRadius, Math.min(maxRadius, radius));
    }
    
    /**
     * 获取半径
     */
    public float getRadius() {
        return radius;
    }
    
    /**
     * 获取中心点
     */
    public BlockPos getCenter() {
        return center;
    }
}
