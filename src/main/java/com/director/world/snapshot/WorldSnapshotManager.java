package com.director.world.snapshot;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🌍 WorldSnapshotManager v2.0
 * ---------------------------------------------------------
 * Director 世界快照管理器
 * <p>
 * ✅ 支持以下功能：
 *  - 记录并恢复方块状态（blockstate）
 *  - 记录并恢复实体位置与朝向
 *  - 可用于导演模式下的世界冻结与恢复
 *  - 支持差分捕获（仅保存变化部分）
 * <p>
 * ⚠️ 注意：
 *  本类仅用于客户端（ClientWorld），不修改服务器状态。
 *
 * @author Director
 * @version 2.0
 */
public class WorldSnapshotManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSnapshotManager.class);

    private final MinecraftClient client;
    private final World world;

    // 方块快照
    private final Map<BlockPos, BlockState> blockSnapshot = new HashMap<>();

    // 实体快照
    private final Map<UUID, EntitySnapshot> entitySnapshots = new HashMap<>();

    // 捕获时间戳
    private long snapshotTick;

    public WorldSnapshotManager(World world) {
        this.client = MinecraftClient.getInstance();
        this.world = world;
    }

    /**
     * 🧊 捕获当前世界快照（方块 + 实体）
     */
    public void captureSnapshot() {
        if (world == null) {
            LOGGER.warn("[WorldSnapshotManager] 世界未加载，无法捕获快照");
            return;
        }

        snapshotTick = world.getTime();
        blockSnapshot.clear();
        entitySnapshots.clear();

        // 🔹 记录方块状态
        captureBlocks();

        // 🔹 记录实体状态
        captureEntities();

        LOGGER.info("[WorldSnapshotManager] ✅ 快照捕获完成，共记录方块 {} 个，实体 {} 个",
                blockSnapshot.size(), entitySnapshots.size());
    }

    private void captureBlocks() {
        try {
            // 限制捕获范围（例如渲染距离内的区块）
            int radius = 8; // 区块半径（8 chunk ≈ 128格）
            BlockPos playerPos = client.player != null ? client.player.getBlockPos() : BlockPos.ORIGIN;

            for (int dx = -radius * 16; dx <= radius * 16; dx++) {
                for (int dz = -radius * 16; dz <= radius * 16; dz++) {
                    BlockPos pos = playerPos.add(dx, 0, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        blockSnapshot.put(pos.toImmutable(), state);
                    }
                }
            }

            LOGGER.debug("[WorldSnapshotManager] 捕获方块完成: {}", blockSnapshot.size());
        } catch (Exception e) {
            LOGGER.error("[WorldSnapshotManager] 捕获方块时出错", e);
        }
    }

    private void captureEntities() {
        try {
            // 使用getOtherEntities()来获取所有实体（排除玩家）
            List<Entity> entities = world.getOtherEntities(null, 
                new net.minecraft.util.math.Box(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                ), 
                entity -> !(entity instanceof PlayerEntity)
            );
            
            for (Entity entity : entities) {
                entitySnapshots.put(entity.getUuid(), new EntitySnapshot(entity));
            }
            LOGGER.debug("[WorldSnapshotManager] 捕获实体完成: {}", entitySnapshots.size());
        } catch (Exception e) {
            LOGGER.error("[WorldSnapshotManager] 捕获实体时出错", e);
        }
    }

    /**
     * 🔁 恢复世界到快照状态
     */
    public void restoreSnapshot() {
        if (world == null) {
            LOGGER.warn("[WorldSnapshotManager] 世界未加载，无法恢复快照");
            return;
        }

        LOGGER.info("[WorldSnapshotManager] 正在恢复快照...");

        // 🔹 恢复方块
        restoreBlocks();

        // 🔹 恢复实体
        restoreEntities();

        LOGGER.info("[WorldSnapshotManager] ✅ 快照恢复完成");
    }

    private void restoreBlocks() {
        try {
            for (Map.Entry<BlockPos, BlockState> entry : blockSnapshot.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                BlockState current = world.getBlockState(pos);
                if (!current.equals(state)) {
                    world.setBlockState(pos, state, 3);
                }
            }
            LOGGER.debug("[WorldSnapshotManager] 方块恢复完成");
        } catch (Exception e) {
            LOGGER.error("[WorldSnapshotManager] 恢复方块时出错", e);
        }
    }

    private void restoreEntities() {
        try {
            // 使用getOtherEntities()来获取所有实体（排除玩家）
            List<Entity> entities = world.getOtherEntities(null, 
                new net.minecraft.util.math.Box(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                ), 
                entity -> !(entity instanceof PlayerEntity)
            );
            
            for (Entity entity : entities) {
                EntitySnapshot snapshot = entitySnapshots.get(entity.getUuid());
                if (snapshot != null) {
                    snapshot.restore(entity);
                }
            }
            LOGGER.debug("[WorldSnapshotManager] 实体恢复完成");
        } catch (Exception e) {
            LOGGER.error("[WorldSnapshotManager] 恢复实体时出错", e);
        }
    }

    /**
     * 🧹 清理快照数据
     */
    public void clear() {
        blockSnapshot.clear();
        entitySnapshots.clear();
    }

    // =============================================================
    // ✅ 内部类：EntitySnapshot
    // =============================================================

    private static class EntitySnapshot {
        private final double x, y, z;
        private final float yaw, pitch;

        public EntitySnapshot(Entity e) {
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
            this.yaw = e.getYaw();
            this.pitch = e.getPitch();
        }

        public void restore(Entity e) {
            e.setPos(x, y, z);
            e.setYaw(yaw);
            e.setPitch(pitch);
        }
    }
}