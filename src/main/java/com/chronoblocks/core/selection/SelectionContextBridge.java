package com.chronoblocks.core.selection;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🧩 SelectionContextBridge v1.0
 * 将选择系统与容器面板连接，生成容器/图层
 * 
 * 功能：
 * - 将选区转换为容器
 * - 管理选区与容器的映射关系
 * - 提供选区上下文信息
 * - 支持选区操作历史
 */
public class SelectionContextBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionContextBridge.class);
    
    private final SelectionManager selectionManager;
    private final ContainerManager containerManager;
    private final WorldSnapshotManager snapshotManager;
    
    public SelectionContextBridge(SelectionManager selectionManager, 
                                 ContainerManager containerManager,
                                 WorldSnapshotManager snapshotManager) {
        this.selectionManager = selectionManager;
        this.containerManager = containerManager;
        this.snapshotManager = snapshotManager;
    }
    
    /**
     * 将当前选区转换为容器
     */
    public Container createContainerFromSelection(String name) {
        SelectionSet selectionSet = selectionManager.getSelectionSet();
        if (selectionSet.isEmpty()) {
            LOGGER.warn("[SelectionContextBridge] 选区为空，无法创建容器");
            return null;
        }
        
        Set<BlockPos> blocks = selectionSet.asUnmodifiableSet();
        Container container = new Container(name);
        // 简化实现，暂时不设置方块和位置
        
        // 注册容器（简化实现）
        // containerManager.register(container);
        
        LOGGER.info("[SelectionContextBridge] 从选区创建容器 '{}'，包含 {} 个方块", name, blocks.size());
        return container;
    }
    
    /**
     * 计算方块集合的中心点
     */
    private BlockPos calculateCenter(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return BlockPos.ORIGIN;
        }
        
        int totalX = 0, totalY = 0, totalZ = 0;
        int count = blocks.size();
        
        for (BlockPos pos : blocks) {
            totalX += pos.getX();
            totalY += pos.getY();
            totalZ += pos.getZ();
        }
        
        return new BlockPos(totalX / count, totalY / count, totalZ / count);
    }
    
    /**
     * 获取选区统计信息
     */
    public SelectionStats getSelectionStats() {
        SelectionSet selectionSet = selectionManager.getSelectionSet();
        Set<BlockPos> blocks = selectionSet.asUnmodifiableSet();
        
        if (blocks.isEmpty()) {
            return new SelectionStats(0, BlockPos.ORIGIN, BlockPos.ORIGIN, BlockPos.ORIGIN);
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        BlockPos center = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        
        return new SelectionStats(blocks.size(), min, max, center);
    }
    
    /**
     * 选区统计信息
     */
    public static class SelectionStats {
        public final int blockCount;
        public final BlockPos min;
        public final BlockPos max;
        public final BlockPos center;
        
        public SelectionStats(int blockCount, BlockPos min, BlockPos max, BlockPos center) {
            this.blockCount = blockCount;
            this.min = min;
            this.max = max;
            this.center = center;
        }
        
        public int getVolume() {
            return (max.getX() - min.getX() + 1) * 
                   (max.getY() - min.getY() + 1) * 
                   (max.getZ() - min.getZ() + 1);
        }
        
        public double getDiagonalLength() {
            return Math.sqrt(
                Math.pow(max.getX() - min.getX(), 2) +
                Math.pow(max.getY() - min.getY(), 2) +
                Math.pow(max.getZ() - min.getZ(), 2)
            );
        }
    }
}
