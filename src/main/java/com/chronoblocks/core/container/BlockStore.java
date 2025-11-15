package com.chronoblocks.core.container;

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
 * 
 * 可被 ContainerVisibilityService 与动画系统调用。
 * 
 * @author ChronoBlocks Team
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

    public void clear() {
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
            try {
                // Minecraft 1.21+ 使用 createNbt
                tag = be.createNbt(world.getRegistryManager());
            } catch (Exception e) {
                // 忽略
            }
        }

        data.put(pos.toImmutable(), new Entry(state, tag));
    }

    /** 
     * 恢复所有快照到世界
     */
    public void apply(net.minecraft.server.world.ServerWorld world) {
        if (world == null) {
            return;
        }
        
        for (Map.Entry<BlockPos, Entry> e : data.entrySet()) {
            BlockPos pos = e.getKey();
            Entry entry = e.getValue();

            // 检查区块是否已加载
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            // 写回方块
            world.setBlockState(pos, entry.state, 
                net.minecraft.block.Block.NOTIFY_ALL | net.minecraft.block.Block.FORCE_STATE);

            // 写回 BlockEntity
            if (entry.beNbt != null) {
                BlockEntity newBe = world.getBlockEntity(pos);
                if (newBe != null) {
                    try {
                        // Minecraft 1.21+ 使用 loadWithComponents
                        java.lang.reflect.Method loadMethod = newBe.getClass()
                            .getMethod("loadWithComponents", NbtCompound.class, 
                                net.minecraft.registry.RegistryWrapper.WrapperLookup.class);
                        loadMethod.invoke(newBe, entry.beNbt, world.getRegistryManager());
                        newBe.markDirty();
                    } catch (NoSuchMethodException e1) {
                        // 如果上面的方法失败，尝试使用旧的方法签名
                        try {
                            java.lang.reflect.Method readNbtMethod = newBe.getClass()
                                .getMethod("readNbt", NbtCompound.class, 
                                    net.minecraft.registry.RegistryWrapper.WrapperLookup.class);
                            readNbtMethod.invoke(newBe, entry.beNbt, world.getRegistryManager());
                            newBe.markDirty();
                        } catch (NoSuchMethodException e2) {
                            try {
                                java.lang.reflect.Method readNbtMethod = newBe.getClass()
                                    .getMethod("readNbt", NbtCompound.class);
                                readNbtMethod.invoke(newBe, entry.beNbt);
                                newBe.markDirty();
                            } catch (Exception e3) {
                                // 忽略恢复失败
                            }
                        } catch (Exception e2) {
                            // 忽略恢复失败
                        }
                    } catch (Exception e1) {
                        // 忽略恢复失败
                    }
                }
            }
        }
    }

    /**
     * 移除某个方块快照（容器删除方块时使用）
     */
    public void remove(BlockPos pos) {
        data.remove(pos);
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
     * 添加方块快照（兼容旧代码）
     */
    public void add(BlockPos pos, BlockSnapshot snapshot) {
        if (pos == null || snapshot == null) {
            return;
        }
        data.put(pos.toImmutable(), new Entry(snapshot.getBlockState(), snapshot.getBlockEntityNbt()));
    }

    /**
     * 添加方块快照（v5.3新增，使用 Entry）
     */
    public void add(BlockPos pos, Entry entry) {
        if (pos == null || entry == null) {
            return;
        }
        data.put(pos.toImmutable(), entry);
    }

    /**
     * 从世界中抓取方块快照并添加（兼容旧代码）
     */
    public void addFromWorld(BlockPos pos, World world) {
        capture(world, pos);
    }

    /**
     * 批量移除方块（兼容旧代码）
     */
    public int removeAll(java.util.Collection<BlockPos> positions) {
        if (positions == null) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : positions) {
            if (data.remove(pos) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 是否包含指定方块（兼容旧代码）
     */
    public boolean contains(BlockPos pos) {
        return data.containsKey(pos);
    }

    /**
     * 方块快照（兼容旧代码）
     * @deprecated 使用 Entry 替代
     */
    @Deprecated
    public static class BlockSnapshot {
        private final BlockState blockState;
        private final NbtCompound blockEntityNbt;
        private final int lightLevel;
        private final long captureTime;
        
        public BlockSnapshot(BlockState blockState, NbtCompound blockEntityNbt, int lightLevel) {
            this.blockState = blockState;
            this.blockEntityNbt = blockEntityNbt;
            this.lightLevel = lightLevel;
            this.captureTime = System.currentTimeMillis();
        }
        
        public static BlockSnapshot capture(BlockPos pos, World world) {
            if (pos == null || world == null) {
                return null;
            }
            
            BlockState state = world.getBlockState(pos);
            NbtCompound nbt = null;
            
            var blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                try {
                    nbt = blockEntity.createNbt(world.getRegistryManager());
                } catch (Exception e) {
                    // 忽略
                }
            }
            
            int lightLevel = world.getLightLevel(pos);
            
            return new BlockSnapshot(state, nbt, lightLevel);
        }
        
        public BlockState getBlockState() {
            return blockState;
        }
        
        public NbtCompound getBlockEntityNbt() {
            return blockEntityNbt;
        }
        
        public int getLightLevel() {
            return lightLevel;
        }
        
        public long getCaptureTime() {
            return captureTime;
        }
        
        public boolean hasBlockEntity() {
            return blockEntityNbt != null;
        }
    }
}
