package com.director.core.container;

import com.director.core.container.visibility.ContainerBlockUpdateFlags;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * BlockStore：用于存储一个容器中的全部原始方块快照：
 * - BlockState
 * - BlockEntity（NBT）
 * <p>
 * 可被 ContainerVisibilityService 与动画系统调用。
 * 
 * @author Director Team
 * @version 5.3
 */
public class BlockStore {

    public static class Entry {
        public BlockState state;
        public NbtCompound beNbt; // 可为空

        public Entry(BlockState state, NbtCompound beNbt) {
            this.state = state;
            this.beNbt = beNbt;
        }
    }

    private final Map<BlockPos, Entry> data = new HashMap<>();
    private volatile long revision;

    /** Runtime snapshot-write version for client readiness proofs. */
    public long getRevision() { return revision; }

    public void clear() {
        if (!data.isEmpty()) {
            revision++;
            com.director.core.container.visibility.ClientSectionInvalidation.getInstance().invalidateAllVisual();
        }
        data.clear();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Map<BlockPos, Entry> raw() {
        return data;
    }

    /** 
     * 捕获某个 pos 当前世界中的方块 (state + be)
     */
    public void capture(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        
        // 检查区块是否已加载
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            if (!serverWorld.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
        } else {
            // 客户端世界使用 isChunkLoaded 检查
            int chunkX2 = pos.getX() >> 4;
            int chunkZ2 = pos.getZ() >> 4;
            if (world.getChunkManager().getChunk(chunkX2, chunkZ2) == null) {
                return;
            }
        }

        BlockState state = world.getBlockState(pos);
        NbtCompound tag = null;

        BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            tag = BlockEntityNbtIO.capture(world, be);
        }

        add(pos, new Entry(state, tag));
    }

    /** 
     * 恢复所有快照到世界
     */
    public void apply(net.minecraft.server.world.ServerWorld world) {
        if (world == null) {
            return;
        }

        // 缓存已处理的区块，避免重复加载造成卡顿放大
        java.util.Set<Long> ensuredChunks = new java.util.HashSet<>();

        for (Map.Entry<BlockPos, Entry> e : data.entrySet()) {
            BlockPos pos = e.getKey();
            Entry entry = e.getValue();

            // 确保区块已加载：容器在隐藏状态下会把世界方块写成 AIR 并落盘；
            // 若重启后直接“显示/释放”，这些区块可能尚未加载，若跳过就会导致方块无法归还。
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            if (!ensuredChunks.contains(chunkKey)) {
                ensuredChunks.add(chunkKey);
                try {
                    if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                        // 强制加载（同步）
                        world.getChunk(chunkX, chunkZ);
                    }
                } catch (Exception ignored) {
                    // 若区块加载失败，则后续 setBlockState 也很可能无效，直接继续下一块
                }
            }

            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue; // 确实无法加载时，保底跳过
            }

            // 写回方块
            world.setBlockState(pos, entry.state, ContainerBlockUpdateFlags.BATCH);

            // 写回 BlockEntity
            if (entry.beNbt != null) {
                BlockEntity newBe = world.getBlockEntity(pos);
                if (newBe != null) {
                    BlockEntityNbtIO.restore(newBe, entry.beNbt, world.getRegistryManager());
                }
            }
        }
    }

    /**
     * 移除某个方块快照（容器删除方块时使用）
     */
    public void remove(BlockPos pos) {
        if (data.remove(pos) != null) {
            revision++;
            com.director.core.container.visibility.ClientSectionInvalidation.getInstance().invalidateVisual(pos);
        }
    }

    /**
     * 获取方块快照
     */
    public Entry get(BlockPos pos) {
        return data.get(pos);
    }

    /**
     * 获取所有方块位置
     */
    public java.util.Collection<BlockPos> positions() {
        return data.keySet();
    }

    /**
     * 获取方块数量
     */
    public int size() {
        return data.size();
    }

    /**
     * 批量从世界中抓取方块快照（兼容旧代码）
     */
    public void addAllFromWorld(java.util.Collection<BlockPos> positions, World world) {
        if (positions == null || world == null) {
            return;
        }
        for (BlockPos pos : positions) {
            capture(world, pos);
        }
    }

    /**
     * 添加方块快照（v5.3新增，使用 Entry）
     */
    public void add(BlockPos pos, Entry entry) {
        if (pos == null || entry == null) {
            return;
        }
        data.put(pos.toImmutable(), entry);
        revision++;
        com.director.core.container.visibility.ClientSectionInvalidation.getInstance().invalidateVisual(pos);
    }

    /**
     * 是否包含指定方块（兼容旧代码）
     */
    public boolean contains(BlockPos pos) {
        return data.containsKey(pos);
    }
}
