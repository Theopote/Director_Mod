package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ❄ WaterFreezeManager v1.0
 *
 * <p>基于季节 + 温度采样，对玩家周围的水进行"可逆冻结"。
 *
 * <p>设计：
 * <ul>
 *   <li>背景线程扫描候选水块（减少主线程卡顿）</li>
 *   <li>主线程应用冰冻/解冻（保证方块操作安全）</li>
 *   <li>记录所有被冻结的方块，温度回升后逐步恢复</li>
 * </ul>
 *
 * <p>冻结逻辑：
 * <ul>
 *   <li>温度低（temperature <= -0.2）时逐步冻结</li>
 *   <li>温度高（temperature >= 0）时逐步融化</li>
 *   <li>中间区域会缓慢冻结/融化</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>仅客户端视觉，不会污染世界存档</li>
 *   <li>必须在导演模式或季节轨道激活时运行</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WaterFreezeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaterFreezeManager.class);

    // === 单例 ===

    private static WaterFreezeManager INSTANCE;

    public static WaterFreezeManager get() {
        if (INSTANCE == null) {
            INSTANCE = new WaterFreezeManager();
        }
        return INSTANCE;
    }

    // ================================================
    // 配置参数（可调）
    // ================================================

    /** 扫描半径（方块距离） */
    private static final int SCAN_RADIUS = 32;

    /** 每 tick 最多冻结多少个水块 */
    private static final int FREEZE_PER_TICK = 48;

    /** 每 tick 最多融化多少个冰块 */
    private static final int MELT_PER_TICK = 48;

    /** 垂直搜索范围，用来找水面 */
    private static final int SURFACE_SCAN_HEIGHT = 16;

    /** 冻结温度阈值（低于此值开始冻结） */
    private static final float FREEZE_THRESHOLD = -0.2f;

    /** 完全融化温度阈值（高于此值快速融化） */
    private static final float MELT_THRESHOLD = 0.0f;

    // ================================================
    // 状态管理
    // ================================================

    /** 当前采样（从 SeasonController 推送） */
    private volatile SeasonSample currentSample = SeasonSample.defaultSample();

    /** 当前是否有季节覆盖 */
    private volatile boolean hasOverride = false;

    /** 多线程候选池：要冻结的水块 */
    private final ConcurrentLinkedQueue<BlockPos> pendingFreeze = new ConcurrentLinkedQueue<>();

    /** 成功冻结的水块（用于自动恢复） */
    private final Set<BlockPos> frozenWater = Collections.synchronizedSet(new HashSet<>());

    /** 避免重复启动扫描任务 */
    private volatile boolean scanRunning = false;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChronoBlocks-WaterFreezeWorker");
        t.setDaemon(true);
        return t;
    });

    private WaterFreezeManager() {
    }

    // ================================================
    // 入口 1：季节 Sample 更新（由 TimelineSeasonController 调用）
    // ================================================

    /**
     * 接收季节采样更新（v1.0 API）。
     * 
     * <p>由 TimelineSeasonController.propagateToWorld() 调用，不再自己获取 sample。
     * 这样可以确保冰冻系统 100% 由季节轨道驱动，不会产生不一致。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleUpdated(SeasonSample sample) {
        if (sample == null) {
            hasOverride = false;
            currentSample = SeasonSample.defaultSample();
        } else {
            hasOverride = true;
            currentSample = sample;
        }
    }

    // ================================================
    // 入口 2：每 tick 调用（由客户端 Tick）
    // ================================================

    /**
     * 每 tick 调用（v1.0 API，接受 cameraPos 参数）。
     * 
     * <p>先处理主线程上的改方块逻辑，再启动异步扫描。
     * 
     * @param world 客户端世界
     * @param cameraPos 摄像机位置（用于确定扫描中心）
     */
    public void tick(ClientWorld world, Vec3d cameraPos) {
        if (world == null) {
            return;
        }

        if (!hasOverride) {
            // 没有季节覆盖时，快速融化所有冰块
            applyMelting(world, 1.0f); // 使用最大融化速率
            return;
        }

        applyFreezing(world);
        applyMelting(world);

        scheduleScan(world, cameraPos);
    }

    // ================================================
    // 主线程：冻结逻辑
    // ================================================

    private void applyFreezing(ClientWorld world) {
        SeasonSample sample = currentSample;
        if (sample == null) {
            return;
        }

        float temp = sample.temperature;
        if (temp > FREEZE_THRESHOLD) {
            // 温度不够低，不冻结
            return;
        }

        // 温度越低，冻结越快
        // temp = -0.2 → factor = 0.0, temp = -1.0 → factor = 1.0
        float freezeFactor = Math.max(0f, (FREEZE_THRESHOLD - temp) / (FREEZE_THRESHOLD - (-1.0f)));
        int limit = Math.max(1, (int) (FREEZE_PER_TICK * freezeFactor));

        int count = 0;
        BlockPos pos;

        while (count < limit && (pos = pendingFreeze.poll()) != null) {
            if (freezeBlock(world, pos)) {
                frozenWater.add(pos.toImmutable());
                count++;
            }
        }
    }

    /**
     * 冻结一个水方块。
     * 
     * @param world 世界
     * @param pos 位置
     * @return 是否成功冻结
     */
    private boolean freezeBlock(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        // 仅冻结水源（Flowing 也可支持）
        if (state.isOf(Blocks.WATER) || state.getBlock() instanceof FluidBlock) {
            world.setBlockState(pos, Blocks.ICE.getDefaultState(), 3);
            return true;
        }

        return false;
    }

    // ================================================
    // 主线程：融化逻辑
    // ================================================

    private void applyMelting(ClientWorld world) {
        SeasonSample sample = currentSample;
        if (sample == null) {
            return;
        }

        float temp = sample.temperature;

        // 温度高于 0：快速融化
        // temp = -1.0 → factor = 0.0, temp = 0.0 → factor = 1.0
        float meltFactor = (temp >= MELT_THRESHOLD ? 1f : Math.max(0f, 1f + temp));

        applyMelting(world, meltFactor);
    }

    /**
     * 应用融化逻辑（内部方法，可指定融化因子）。
     * 
     * @param world 世界
     * @param meltFactor 融化因子 [0, 1]
     */
    private void applyMelting(ClientWorld world, float meltFactor) {
        if (meltFactor <= 0.01f) {
            // 仍然很冷，不融化
            return;
        }

        int limit = (int) (MELT_PER_TICK * meltFactor);
        int melted = 0;

        synchronized (frozenWater) {
            Iterator<BlockPos> it = frozenWater.iterator();

            while (it.hasNext() && melted < limit) {
                BlockPos pos = it.next();
                if (meltBlock(world, pos)) {
                    it.remove();
                    melted++;
                } else {
                    // 方块不再是冰 → 也移除记录
                    it.remove();
                }
            }
        }
    }

    /**
     * 融化一个冰块。
     * 
     * @param world 世界
     * @param pos 位置
     * @return 是否成功融化
     */
    private boolean meltBlock(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(Blocks.ICE)) {
            return false;
        }

        // 融化回水
        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
        return true;
    }

    // ================================================
    // 异步扫描：寻找水块
    // ================================================

    private void scheduleScan(ClientWorld world, Vec3d pos) {
        if (scanRunning) {
            return;
        }

        scanRunning = true;

        BlockPos cam = BlockPos.ofFloored(pos);
        SeasonSample sampleSnapshot = currentSample;

        worker.submit(() -> {
            try {
                scanForWater(world, cam, sampleSnapshot);
            } catch (Exception e) {
                LOGGER.warn("[WaterFreezeManager] 扫描任务出错", e);
            } finally {
                scanRunning = false;
            }
        });
    }

    private void scanForWater(ClientWorld world, BlockPos cam, SeasonSample sample) {
        if (sample == null || sample.temperature > FREEZE_THRESHOLD) {
            // 气温不够冷，不需要扫描
            return;
        }

        Random rand = new Random(Objects.hash(cam.getX(), cam.getZ(), world.getTime()));

        int tries = SCAN_RADIUS * SCAN_RADIUS / 2;

        for (int i = 0; i < tries; i++) {
            int dx = rand.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            int dz = rand.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;

            int x = cam.getX() + dx;
            int z = cam.getZ() + dz;

            BlockPos surface = findWaterSurface(world, x, z, cam.getY());
            if (surface == null) {
                continue;
            }

            // 避免重复
            if (frozenWater.contains(surface) || pendingFreeze.contains(surface)) {
                continue;
            }

            pendingFreeze.add(surface.toImmutable());
        }
    }

    /**
     * 在垂直方向上找一个合适的水面方块。
     * 
     * @param world 世界
     * @param x X 坐标
     * @param z Z 坐标
     * @param centerY 中心 Y 坐标
     * @return 水面位置，如果未找到则返回 null
     */
    private BlockPos findWaterSurface(ClientWorld world, int x, int z, double centerY) {
        int top = (int) centerY + SURFACE_SCAN_HEIGHT / 2;
        int bottom = (int) centerY - SURFACE_SCAN_HEIGHT / 2;

        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int y = top; y >= bottom; y--) {
            m.set(x, y, z);

            BlockState state = world.getBlockState(m);

            if (state.isOf(Blocks.WATER) || state.getBlock() instanceof FluidBlock) {
                // 水上方必须是空气
                if (world.isAir(m.up())) {
                    return m.toImmutable();
                }
            }
        }

        return null;
    }

    // ================================================
    // 清理接口：退出导演模式 / 重置季节时调用
    // ================================================

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有冰块。
     */
    public void restoreAll(ClientWorld world) {
        if (world == null) {
            return;
        }

        synchronized (frozenWater) {
            for (BlockPos pos : frozenWater) {
                BlockState state = world.getBlockState(pos);
                if (state.isOf(Blocks.ICE)) {
                    world.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
                }
            }
            frozenWater.clear();
        }

        pendingFreeze.clear();
    }

    // ================================================
    // 可选：在世界卸载时关闭线程（如果需要）
    // ================================================

    /**
     * 关闭工作线程（在世界卸载时调用，可选）。
     */
    public void shutdown() {
        worker.shutdownNow();
    }
}

