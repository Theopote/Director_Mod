package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WaterFreezeManager v3.6
 *
 * <p>负责基于季节轨道的采样结果，在玩家周围区域：
 * <ul>
 *   <li>将水面按「冰覆盖度 + 噪声场」转为冰块</li>
 *   <li>将不再需要的冰块恢复为原始水状态</li>
 *   <li>保证可以在删除轨道 / 关闭 ChronoBlocks 时完全恢复世界</li>
 *   <li>⭐ v3.6：支持动态扫描，随镜头/玩家移动持续更新冰冻效果</li>
 * </ul>
 *
 * <p>核心设计：
 * <ul>
 *   <li>使用 {@link NoiseField#iceCoverage(BlockPos)} 生成 0~1 连续噪声值</li>
 *   <li>通过 freezeStrength（0~1）作为阈值：noise < strength → 结冰</li>
 *   <li>异步扫描 + 主线程应用 blockState，避免卡顿 & 线程问题</li>
 *   <li>⭐ v3.6：每 tick 检测玩家位置变化，移动超过阈值时重新扫描</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 3.6 - 动态扫描支持
 */
public class WaterFreezeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaterFreezeManager.class);

    /** 单例 */
    private static WaterFreezeManager INSTANCE;

    /** 冻结作用半径（以方块为单位） */
    private static final int FREEZE_RADIUS = 48;

    /** 最小覆盖度阈值，低于此值视为不需要结冰 */
    private static final float MIN_COVERAGE_EPS = 0.01f;

    /** 异步线程池：只做「扫描 + 计算」，不直接改方块 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChronoBlocks-WaterFreeze");
        t.setDaemon(true);
        return t;
    });

    /**
     * 记录已经被我们替换成冰的方块位置。
     * <p>注意：不代表所有冰块，只代表「由本系统生成的冰」。
     */
    private final Set<BlockPos> currentIcePositions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 记录每个被替换位置的「原始水 BlockState」，用于恢复。
     */
    private final Map<BlockPos, BlockState> originalWaterStates =
            new ConcurrentHashMap<>();

    /** 最近一次应用的 sample（用于动态扫描） */
    private volatile SeasonSample lastSample;

    /** 当前是否正在异步扫描（防止堆积过多任务） */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** 上次扫描时的玩家位置（用于检测移动） */
    private BlockPos lastScanPos = null;

    /** 位置变化阈值：玩家移动超过此距离时重新扫描（以方块为单位） */
    private static final int POSITION_CHANGE_THRESHOLD = 16;

    private WaterFreezeManager() {}

    public static WaterFreezeManager get() {
        if (INSTANCE == null) {
            INSTANCE = new WaterFreezeManager();
        }
        return INSTANCE;
    }

    // ========================================================================
    // 对外 API（兼容旧接口）
    // ========================================================================

    /**
     * 兼容旧接口：onSeasonSampleUpdated
     * 
     * @deprecated 使用 {@link #applySeasonSample(SeasonSample)} 代替
     */
    @Deprecated
    public void onSeasonSampleUpdated(SeasonSample sample) {
        applySeasonSample(sample);
    }

    /**
     * 兼容旧接口：onSeasonSampleChanged
     * 
     * @deprecated 使用 {@link #applySeasonSample(SeasonSample)} 代替
     */
    @Deprecated
    public void onSeasonSampleChanged(SeasonSample sample) {
        applySeasonSample(sample);
    }

    /**
     * 兼容旧接口：restoreAll
     */
    public void restoreAll(ClientWorld world) {
        clearAll();
    }

    /**
     * 每 tick 调用：检查玩家位置变化，如果移动超过阈值则重新扫描。
     * 
     * <p>v3.6：恢复 tick 机制以支持动态扫描，但只用于检测位置变化。
     * 
     * @param world 客户端世界
     * @param cameraPos 摄像机位置（用于确定扫描中心）
     */
    public void tick(ClientWorld world, net.minecraft.util.math.Vec3d cameraPos) {
        if (world == null) {
            return;
        }

        // 如果没有有效的季节采样，不需要扫描
        if (lastSample == null || !lastSample.enabled || lastSample.freezeStrength <= MIN_COVERAGE_EPS) {
            return;
        }

        // 检查玩家位置变化
        BlockPos currentPos = BlockPos.ofFloored(cameraPos);
        if (lastScanPos == null) {
            // 首次扫描
            lastScanPos = currentPos;
            scheduleScanAndApply(lastSample);
            return;
        }

        // 计算位置变化距离
        int dx = Math.abs(currentPos.getX() - lastScanPos.getX());
        int dz = Math.abs(currentPos.getZ() - lastScanPos.getZ());
        int distance = Math.max(dx, dz);

        // 如果移动超过阈值，重新扫描
        if (distance >= POSITION_CHANGE_THRESHOLD) {
            lastScanPos = currentPos;
            scheduleScanAndApply(lastSample);
        }
    }

    /**
     * 每 tick 调用（向后兼容，自动获取玩家位置）。
     * 
     * @deprecated 使用 {@link #tick(ClientWorld, net.minecraft.util.math.Vec3d)} 代替
     */
    @Deprecated
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }
        tick(mc.world, mc.player.getPos());
    }

    // ========================================================================
    // 新 API（v3.5）
    // ========================================================================

    /**
     * 由 TimelineSeasonController 调用：
     * <p>每当 SeasonSample 更新时（Timeline 播放 / 拖动 / 预览），调用一次。
     *
     * @param sample 当前时间点的季节采样结果
     */
    public void applySeasonSample(SeasonSample sample) {
        this.lastSample = sample;
        
        // 重置扫描位置，确保下次 tick 时会立即扫描新位置
        if (sample != null && sample.enabled && sample.freezeStrength > MIN_COVERAGE_EPS) {
            lastScanPos = null;
        }
        
        if (sample == null || !sample.enabled) {
            // 没有季节数据 → 解冻所有我们修改过的冰
            lastScanPos = null;
            scheduleFullClear();
            return;
        }

        // 如果覆盖度非常低，只做"解冻"操作即可
        // 注意：使用 freezeStrength 而不是 iceCoverageStrength（保持兼容性）
        if (sample.freezeStrength <= MIN_COVERAGE_EPS) {
            lastScanPos = null;
            scheduleMeltOnly();
            return;
        }

        scheduleScanAndApply(sample);
    }

    /**
     * 删除季节轨道 / 删除所有关键帧 / 关闭 ChronoBlocks 时调用：
     * <p>完全恢复世界（所有由本系统生成的冰块都恢复为原始水）。
     */
    public void clearAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            currentIcePositions.clear();
            originalWaterStates.clear();
            lastScanPos = null;
            return;
        }

        client.execute(() -> {
            int restored = 0;
            for (BlockPos pos : new HashSet<>(currentIcePositions)) {
                BlockState original = originalWaterStates.remove(pos);
                if (original != null) {
                    world.setBlockState(pos, original);
                    restored++;
                }
            }
            currentIcePositions.clear();
            lastScanPos = null;

            if (restored > 0) {
                LOGGER.info("[WaterFreezeManager] 恢复 {} 个冰块为原始水状态（clearAll）", restored);
            }
        });
    }

    /**
     * 关闭工作线程（在世界卸载时调用，可选）。
     */
    public void shutdown() {
        executor.shutdownNow();
        currentIcePositions.clear();
        originalWaterStates.clear();
    }

    // ========================================================================
    // 调度逻辑
    // ========================================================================

    /**
     * 安排一次完整扫描：根据 sample 对玩家周围区域「结冰 + 解冻」。
     */
    private void scheduleScanAndApply(SeasonSample sample) {
        if (!scanning.compareAndSet(false, true)) {
            // 已有扫描任务在执行，略过这次，避免堆积
            return;
        }

        executor.submit(() -> {
            try {
                scanAndApplyFrozenField(sample);
            } catch (Throwable t) {
                LOGGER.error("[WaterFreezeManager] 扫描/应用冰冻区域时出错", t);
            } finally {
                scanning.set(false);
            }
        });
    }

    /**
     * 覆盖度接近 0 时，只需融化我们之前冻住的冰块。
     */
    private void scheduleMeltOnly() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            try {
                meltAllCurrentIce();
            } catch (Throwable t) {
                LOGGER.error("[WaterFreezeManager] 融化冰块时出错", t);
            } finally {
                scanning.set(false);
            }
        });
    }

    /**
     * 没有 sample 时（轨道被清空），完全恢复所有冰块。
     */
    private void scheduleFullClear() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            try {
                meltAllCurrentIce();
            } catch (Throwable t) {
                LOGGER.error("[WaterFreezeManager] 完全清除冰块时出错", t);
            } finally {
                scanning.set(false);
            }
        });
    }

    // ========================================================================
    // 核心：扫描 + 计算 + 主线程应用
    // ========================================================================

    /**
     * 扫描玩家周围区域，根据：
     * <ul>
     *   <li>SeasonSample.freezeStrength</li>
     *   <li>{@link NoiseField#iceCoverage(BlockPos)}</li>
     * </ul>
     * 计算出需要结冰 / 解冻的方块，然后在主线程应用。
     */
    private void scanAndApplyFrozenField(SeasonSample sample) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        // 使用 freezeStrength（保持与现有 SeasonSample 兼容）
        float coverage = sample.freezeStrength; // [0,1]

        // 收集需要结冰 / 解冻的方块
        Set<BlockPos> toFreeze = new HashSet<>();
        Set<BlockPos> toMelt = new HashSet<>();

        // 1. 遍历玩家周围的水面（简化为以地表高度为准）
        for (int dx = -FREEZE_RADIUS; dx <= FREEZE_RADIUS; dx++) {
            for (int dz = -FREEZE_RADIUS; dz <= FREEZE_RADIUS; dz++) {

                BlockPos surface = world.getTopPosition(
                        Heightmap.Type.WORLD_SURFACE,
                        playerPos.add(dx, 0, dz)
                ).down();

                BlockState state = world.getBlockState(surface);

                boolean isWater = state.getBlock() == Blocks.WATER;
                boolean isIce = state.getBlock() == Blocks.ICE || state.getBlock() == Blocks.FROSTED_ICE;

                // 计算噪声场值（0~1）
                float noise = NoiseField.iceCoverage(surface);

                // 使用 coverage 作为阈值：noise < coverage → 该位置「应该结冰」
                boolean shouldBeFrozen = noise < coverage;

                if (isWater && shouldBeFrozen) {
                    toFreeze.add(surface);
                } else if (isIce && !shouldBeFrozen && currentIcePositions.contains(surface)) {
                    // 只对「由本系统生成」的冰块做解冻
                    toMelt.add(surface);
                }
            }
        }

        if (toFreeze.isEmpty() && toMelt.isEmpty()) {
            return;
        }

        // 2. 在主线程应用方块状态修改
        client.execute(() -> applyFreezeChanges(world, toFreeze, toMelt));
    }

    /**
     * 只做"融化所有当前冰块"。
     */
    private void meltAllCurrentIce() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            currentIcePositions.clear();
            originalWaterStates.clear();
            return;
        }

        Set<BlockPos> toMelt = new HashSet<>(currentIcePositions);
        if (toMelt.isEmpty()) {
            return;
        }

        client.execute(() -> applyFreezeChanges(world, Collections.emptySet(), toMelt));
    }

    /**
     * 真正执行方块替换的地方（必须在客户端主线程调用）。
     */
    private void applyFreezeChanges(ClientWorld world,
                                    Set<BlockPos> toFreeze,
                                    Set<BlockPos> toMelt) {

        int freezeCount = 0;
        int meltCount = 0;

        // 先融化不需要的冰
        for (BlockPos pos : toMelt) {
            BlockState original = originalWaterStates.remove(pos);
            if (original != null) {
                world.setBlockState(pos, original);
                meltCount++;
            }
            currentIcePositions.remove(pos);
        }

        // 再冻结需要的水
        for (BlockPos pos : toFreeze) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.WATER) {
                // 如果之前没记录过原始状态，则记录一次
                originalWaterStates.putIfAbsent(pos, state);
                // 使用 FROSTED_ICE（霜冰）作为视觉效果，比普通 ICE 更明显
                world.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState());
                currentIcePositions.add(pos);
                freezeCount++;
            }
        }

        if (freezeCount > 0 || meltCount > 0) {
            LOGGER.debug("[WaterFreezeManager] 冻结 {}，融化 {}（当前冰块总数={}）",
                    freezeCount, meltCount, currentIcePositions.size());
        }
    }
}
