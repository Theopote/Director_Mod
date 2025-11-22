package com.chronoblocks.core.container.visibility;

import com.chronoblocks.core.container.BlockStore;
import com.chronoblocks.core.container.Container;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 容器可见性服务（v5.2）
 * 
 * <p>负责管理容器的隐藏/显示，通过真正将世界中的方块替换为AIR来实现完全隐藏。
 * 
 * <p>核心原理：
 * <ul>
 *   <li>隐藏时：保存方块快照，然后将世界中的方块设置为AIR</li>
 *   <li>显示时：从快照恢复方块状态和方块实体数据</li>
 * </ul>
 * 
 * <p>优点：
 * <ul>
 *   <li>渲染、碰撞、光照、粒子、更新全部自动正确</li>
 *   <li>不需要大量Mixin拦截</li>
 *   <li>服务端和客户端完全同步</li>
 *   <li>兼容所有Minecraft版本</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 5.2
 */
public class ContainerVisibilityService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerVisibilityService.class);
    
    private final MinecraftServer server;
    
    // 每个容器对应一个快照（隐藏时使用，显示时恢复）
    private final Map<UUID, BlockStore> snapshots = new HashMap<>();
    
    public ContainerVisibilityService(MinecraftServer server) {
        this.server = server;
        LOGGER.debug("[ContainerVisibilityService] 初始化");
    }
    
    /**
     * 获取容器所在维度
     */
    private ServerWorld resolveWorld(Container container) {
        RegistryKey<World> key = container.getLevelKey();
        if (key == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界", container.getName());
            return null;
        }
        return server.getWorld(key);
    }
    
    /**
     * 隐藏容器：保存方块快照，然后将世界中的方块设置为AIR
     * 如果容器已经有快照（已隐藏），则只处理新增的方块
     */
    public void hideContainer(Container container) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法隐藏", container.getName());
            return;
        }
        
        BlockStore existingStore = snapshots.get(container.getId());
        BlockStore store;
        
        if (existingStore != null) {
            // 容器已经隐藏，只处理新增的方块
            LOGGER.debug("[Visibility] 容器 {} 已隐藏，处理新增方块 (总方块数: {})", 
                container.getName(), container.getBlockCount());
            store = existingStore;
            
            // 找出新增的方块（在容器中但不在快照中）
            java.util.Set<BlockPos> existingBlocks = new java.util.HashSet<>(existingStore.positions());
            for (BlockPos pos : container.getBlocks()) {
                if (existingBlocks.contains(pos)) {
                    continue; // 已经在快照中，跳过
                }
                
                // 检查区块是否已加载
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                
                // 捕获方块状态和方块实体
                store.capture(world, pos);
                
                // 移除方块实体（如果存在）
                var blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    world.removeBlockEntity(pos);
                }
                
                // 将方块设置为AIR
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 
                    net.minecraft.block.Block.NOTIFY_ALL | net.minecraft.block.Block.FORCE_STATE);
            }
            
            LOGGER.debug("[Visibility] 容器 {} 新增方块已隐藏，快照总数: {}", 
                container.getName(), store.size());
        } else {
            // 容器未隐藏，完整隐藏流程
            LOGGER.debug("[Visibility] 隐藏容器 {} (方块数 {})", container.getName(), container.getBlockCount());
            
            // 保存当前世界中的真实方块快照
            store = new BlockStore();
            
            for (BlockPos pos : container.getBlocks()) {
                // 检查区块是否已加载（使用新方法避免弃用警告）
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                
                // 捕获方块状态和方块实体
                store.capture(world, pos);
                
                // 移除方块实体（如果存在）
                var blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    world.removeBlockEntity(pos);
                }
                
                // 将方块设置为AIR
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 
                    net.minecraft.block.Block.NOTIFY_ALL | net.minecraft.block.Block.FORCE_STATE);
            }
            
            snapshots.put(container.getId(), store);
            LOGGER.debug("[Visibility] 容器 {} 隐藏完成，保存了 {} 个方块快照", 
                container.getName(), store.size());
        }
    }
    
    /**
     * 显示容器：从快照恢复方块状态和方块实体
     */
    public void showContainer(Container container) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法恢复", container.getName());
            return;
        }
        
        BlockStore store = snapshots.get(container.getId());
        if (store == null) {
            LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过", container.getName());
            return;
        }
        
        LOGGER.debug("[Visibility] 显示容器 {} (恢复 {} 个方块)", 
            container.getName(), store.size());
        
        // 将方块恢复到世界
        store.apply(world);
        
        snapshots.remove(container.getId());
        LOGGER.debug("[Visibility] 容器 {} 显示完成", container.getName());
    }
    
    /**
     * 清除容器的快照（容器被删除时调用）
     */
    public void clearSnapshot(Container container) {
        snapshots.remove(container.getId());
    }
    
    /**
     * 检查容器是否已隐藏
     */
    public boolean isHidden(UUID containerId) {
        return snapshots.containsKey(containerId);
    }
    
    /**
     * 清除所有快照（服务器关闭时调用）
     */
    public void clearAll() {
        snapshots.clear();
        LOGGER.debug("[ContainerVisibilityService] 清除所有快照");
    }
}

