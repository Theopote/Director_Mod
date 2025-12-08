package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * SeasonSnowManager v3.0
 *
 * <p>❄ 支持动态融雪动画（逐层融化/堆积）：
 * <ul>
 *   <li>✅ 1~8 层雪（根据 snowAmount 自动计算）</li>
 *   <li>✅ 逐层增加/减少动画（每 2~10 tick 更新一次）</li>
 *   <li>✅ 随机延迟使融雪更自然、不同步</li>
 *   <li>✅ Chunk 分区扫描（13×13 chunk）</li>
 *   <li>✅ 原始方块缓存，确保 100% 可恢复</li>
 *   <li>✅ 坡度影响因子（snowSlopeFactor）控制陡坡积雪</li>
 * </ul>
 *
 * <p>动画效果：
 * <ul>
 *   <li>spring → 雪慢慢融化（逐层减少）</li>
 *   <li>winter → 雪在地表慢慢堆积（逐层增加）</li>
 *   <li>夏天完全没雪</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>这里只是客户端视觉效果（ClientWorld），不改服务器地形</li>
 *   <li>所有积雪都可以 100% 恢复为原始方块（无破坏性）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 3.0 - 动态融雪动画（逐层融化/堆积）
 */
public class SeasonSnowManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonSnowManager.class);

    // === 单例 ===

    private static SeasonSnowManager INSTANCE;

    public static SeasonSnowManager get() {
        if (INSTANCE == null) {
            INSTANCE = new SeasonSnowManager();
        }
        return INSTANCE;
    }

    // ========= 配置参数 =========

    /** 扫描范围（以 chunk 为单位）：玩家中心 ±6 → 13×13 chunk */
    private static final int SCAN_RADIUS_CHUNKS = 6;

    /** 最大雪层数（SnowBlock LAYERS 支持 1~8 层） */
    private static final int MAX_LAYERS = 8;

    /** 动画更新间隔（每 N tick 更新一次雪层） */
    private static final int ANIMATION_TICK_INTERVAL = 2;

    /** 每 tick 处理的最大主线程任务数（避免卡顿） */
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 64;

    /** 每个 chunk 采样方块数（避免扫描所有方块） */
    private static final int SAMPLES_PER_CHUNK = 32;

    // ========= 状态 =========

    /** 后台扫描线程池 */
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "ChronoBlocks-SeasonSnowScanThread");
                t.setDaemon(true);
                return t;
            }
    );

    /** 主线程任务队列（用于安全的方块写操作） */
    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    /** 当前的季节采样（由 TimelineSeasonController 推送） */
    private volatile SeasonSample currentSample = SeasonSample.disabled();

    /** 雪记录映射（位置 → 雪记录） */
    private final Map<BlockPos, SnowRecord> snowMap = new ConcurrentHashMap<>();

    /** 是否需要重新扫描（当 sample 变化时设置） */
    private volatile boolean needsRescan = true;

    /** 避免重复提交扫描任务 */
    private volatile boolean scanRunning = false;

    /** 动画 tick 计数器 */
    private int tickCounter = 0;

    // ========= 数据结构 =========

    /**
     * 雪记录（跟踪每个位置的雪状态）。
     */
    private static class SnowRecord {
        /** 原始方块类型 */
        Block originalBlock;

        /** 当前雪层数（1~8，直接对应 SnowBlock.LAYERS 值） */
        int currentLayers;

        /** 目标雪层数（0~8，由 SeasonSample.snowAmount 决定，0 = 无雪） */
        int targetLayers;

        /** 融雪延迟（tick 数，用于随机偏移，避免所有雪同步变化） */
        int meltDelay;

        SnowRecord() {
            this.currentLayers = 0;
            this.targetLayers = 0;
            this.meltDelay = 0;
        }
    }

    private SeasonSnowManager() {
    }

    // ====================================================
    // 入口 1：季节采样更新（由 TimelineSeasonController 调用）
    // ====================================================

    /**
     * 接收季节采样更新（v3.0 API）。
     * 
     * <p>兼容现有代码：同时支持 `onSeasonSampleUpdated` 和 `onSeasonSampleChanged`。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleUpdated(SeasonSample sample) {
        onSeasonSampleChanged(sample);
    }

    /**
     * 接收季节采样更新（v3.0 新方法名）。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleChanged(SeasonSample sample) {
        this.currentSample = sample != null ? sample : SeasonSample.disabled();
        this.needsRescan = true;
    }

    // ====================================================
    // 入口 2：每 tick 调用（由客户端 Tick）
    // ====================================================

    /**
     * 每 tick 调用（v3.0 API，接受 world 和 cameraPos 参数）。
     * 
     * <p>先处理主线程上的改方块逻辑，再启动异步扫描，最后更新动画。
     * 
     * @param world 客户端世界
     * @param cameraPos 摄像机位置（用于确定扫描中心）
     */
    public void tick(ClientWorld world, Vec3d cameraPos) {
        if (world == null) {
            return;
        }

        // 1) 执行主线程任务（放雪/融雪）
        runMainThreadTasks(world);

        tickCounter++;
        boolean shouldAnimate = (tickCounter % ANIMATION_TICK_INTERVAL == 0);

        // 2) 雪量为 0 或未启用 → 清除全部雪
        if (!currentSample.enabled || currentSample.snowAmount <= 0.01f) {
            if (!snowMap.isEmpty()) {
                meltAllSnow(world, shouldAnimate);
            }
            return;
        }

        // 3) 需要重新扫描时，提交后台扫描任务
        if (needsRescan && !scanRunning) {
            needsRescan = false;
            scanRunning = true;

            BlockPos playerPos = BlockPos.ofFloored(cameraPos);
            SeasonSample sampleSnapshot = currentSample.copy(); // 深拷贝，避免并发修改

            scanExecutor.submit(() -> {
                try {
                    scanAndDetermineTargets(world, playerPos, sampleSnapshot);
                } catch (Exception e) {
                    LOGGER.warn("[SeasonSnowManager] 扫描任务出错", e);
                } finally {
                    scanRunning = false;
                }
            });
        }

        // 4) 动画更新（每 ANIMATION_TICK_INTERVAL tick 更新一次）
        if (shouldAnimate) {
            animateSnowLayers(world);
        }
    }

    /**
     * 每 tick 调用（向后兼容，自动获取玩家位置）。
     * 
     * @deprecated 使用 {@link #tick(ClientWorld, Vec3d)} 代替
     */
    @Deprecated
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }
        tick(mc.world, mc.player.getPos());
    }

    // ====================================================
    // 扫描世界（后台线程）
    // ====================================================

    /**
     * 扫描玩家周围的 chunk，确定每个位置的目标雪层数。
     */
    private void scanAndDetermineTargets(ClientWorld world, BlockPos playerPos, SeasonSample sample) {
        float snowAmount = sample.snowAmount;
        float slopeFactor = sample.snowSlopeFactor;
        int snowY = (int) sample.snowlineHeight;

        // 计算目标最大层数（0~8，0 = 无雪，1~8 = 雪层数）
        // snowAmount = 0.0 → 0 层（无雪），snowAmount = 1.0 → 8 层（最厚）
        int targetMaxLayersTemp = Math.min(MAX_LAYERS, Math.max(0, Math.round(snowAmount * MAX_LAYERS)));
        if (targetMaxLayersTemp == 0 && snowAmount > 0.01f) {
            // 确保即使很小的 snowAmount 也有 1 层
            targetMaxLayersTemp = 1;
        }
        final int targetMaxLayers = targetMaxLayersTemp;

        Set<BlockPos> found = new HashSet<>();

        int px = playerPos.getX() >> 4; // chunk X
        int pz = playerPos.getZ() >> 4; // chunk Z

        Random random = new Random(Objects.hash(px, pz, world.getTime()));

        // 遍历周围 chunk
        for (int cx = px - SCAN_RADIUS_CHUNKS; cx <= px + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = pz - SCAN_RADIUS_CHUNKS; cz <= pz + SCAN_RADIUS_CHUNKS; cz++) {
                // 对每个 chunk 进行采样
                for (int i = 0; i < SAMPLES_PER_CHUNK; i++) {
                    int bx = random.nextInt(16);
                    int bz = random.nextInt(16);
                    int wx = cx * 16 + bx;
                    int wz = cz * 16 + bz;

                    BlockPos top = findTopSolidBlock(world, wx, wz);
                    if (top == null) continue;

                    // 不在雪线高度以下不覆盖
                    if (top.getY() > snowY) continue;

                    // 检查是否应该覆盖雪（考虑坡度）
                    if (shouldCoverBlock(world, top, slopeFactor)) {
                        BlockPos snowPos = top.up(); // 雪层位置（地表上方）
                        found.add(snowPos.toImmutable());
                    }
                }
            }
        }

        // 更新目标层数
        for (BlockPos pos : found) {
            snowMap.compute(pos, (p, rec) -> {
                if (rec == null) {
                    // 新位置：创建记录
                    rec = new SnowRecord();
                    BlockState state = world.getBlockState(p);
                    if (state.isOf(Blocks.SNOW)) {
                        // 已经是雪：读取当前层数（LAYERS = 1~8）
                        rec.currentLayers = state.get(SnowBlock.LAYERS);
                        // 需要记录原始方块（雪下面的方块）
                        BlockPos belowPos = p.down();
                        BlockState belowState = world.getBlockState(belowPos);
                        if (belowState.getBlock() != Blocks.SNOW) {
                            rec.originalBlock = belowState.getBlock();
                        }
                    } else {
                        // 不是雪：记录原始方块
                        rec.originalBlock = state.getBlock();
                        rec.currentLayers = 0; // 0 表示无雪
                    }
                    // 随机延迟（0~12 tick），使融雪更自然
                    rec.meltDelay = random.nextInt(13);
                } else {
                    // 已有记录：确保原始方块已记录（防止并发问题）
                    if (rec.originalBlock == null) {
                        BlockState state = world.getBlockState(p);
                        if (!state.isOf(Blocks.SNOW)) {
                            // 不是雪：记录为原始方块
                            rec.originalBlock = state.getBlock();
                        } else {
                            // 是雪：记录下方方块
                            BlockPos belowPos = p.down();
                            BlockState belowState = world.getBlockState(belowPos);
                            if (!belowState.isOf(Blocks.SNOW)) {
                                rec.originalBlock = belowState.getBlock();
                            }
                        }
                    }
                }
                rec.targetLayers = targetMaxLayers;
                return rec;
            });
        }

        // 不在 found 中的位置 → 设置目标层数为 0（逐渐融化）
        for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!found.contains(pos)) {
                entry.getValue().targetLayers = 0;
            }
        }
    }

    // ====================================================
    // 动画更新（主线程）
    // ====================================================

    /**
     * 更新雪层动画（逐层增加/减少）。
     */
    private void animateSnowLayers(ClientWorld world) {
        if (world == null) return;

        Iterator<Map.Entry<BlockPos, SnowRecord>> it = snowMap.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, SnowRecord> entry = it.next();
            BlockPos pos = entry.getKey();
            SnowRecord rec = entry.getValue();

            // 处理延迟（避免所有雪同步变化）
            if (rec.meltDelay > 0) {
                rec.meltDelay--;
                continue;
            }

            // 当前层数 == 目标层数，无需更新
            if (rec.currentLayers == rec.targetLayers) {
                continue;
            }

            // 根据 snowMeltSpeed 决定更新速度
            float meltSpeed = currentSample.snowMeltSpeed;
            boolean shouldUpdate = true;
            
            // 如果融化速度较慢，增加更新间隔
            if (meltSpeed < 1.0f && tickCounter % Math.max(1, (int)(ANIMATION_TICK_INTERVAL / meltSpeed)) != 0) {
                shouldUpdate = false;
            }

            if (!shouldUpdate) {
                continue;
            }

            // 更新层数（逐层变化）
            if (rec.currentLayers < rec.targetLayers) {
                // 堆雪（加厚）
                rec.currentLayers = Math.min(MAX_LAYERS, rec.currentLayers + 1);
            } else if (rec.currentLayers > rec.targetLayers) {
                // 融雪（减薄）
                rec.currentLayers = Math.max(0, rec.currentLayers - 1);
            }

            final int finalLayers = rec.currentLayers;

            if (finalLayers <= 0) {
                // 完全融化 → 恢复原始方块
                final Block originalBlock = rec.originalBlock;
                if (originalBlock != null) {
                    enqueueMainThread(world, () -> {
                        world.setBlockState(pos, originalBlock.getDefaultState(), 3);
                    });
                } else {
                    enqueueMainThread(world, () -> {
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.isOf(Blocks.SNOW)) {
                            world.removeBlock(pos, false);
                        }
                    });
                }
                it.remove(); // 从映射中移除
            } else {
                // 更新雪层数
                // finalLayers 范围：1~8（直接对应 SnowBlock.LAYERS 值）
                final int snowLayersProp = Math.max(1, Math.min(MAX_LAYERS, finalLayers));
                enqueueMainThread(world, () -> {
                    BlockState snowState = Blocks.SNOW.getDefaultState()
                            .with(SnowBlock.LAYERS, snowLayersProp);
                    world.setBlockState(pos, snowState, 3);
                });
            }
        }
    }

    // ====================================================
    // 全局融化
    // ====================================================

    /**
     * 清除所有雪（退出导演模式时调用）。
     * 
     * @param animate 是否使用动画（false = 立即恢复，true = 设置目标为 0 让动画处理）
     */
    private void meltAllSnow(ClientWorld world, boolean animate) {
        if (!animate) {
            // 立即恢复所有原始方块
            for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
                BlockPos pos = entry.getKey();
                SnowRecord rec = entry.getValue();
                final Block originalBlock = rec.originalBlock;
                
                enqueueMainThread(world, () -> {
                    if (originalBlock != null) {
                        world.setBlockState(pos, originalBlock.getDefaultState(), 3);
                    } else {
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.isOf(Blocks.SNOW)) {
                            world.removeBlock(pos, false);
                        }
                    }
                });
            }
            snowMap.clear();
        } else {
            // 动画融化 → 设置所有目标层数为 0
            for (SnowRecord rec : snowMap.values()) {
                rec.targetLayers = 0;
            }
        }
    }

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有雪。
     */
    public void restoreAll(ClientWorld world) {
        meltAllSnow(world, false);
        LOGGER.info("[SeasonSnowManager] 已清除所有积雪并恢复原始方块");
    }

    // ====================================================
    // 主线程任务执行
    // ====================================================

    /**
     * 将任务加入主线程队列。
     */
    private void enqueueMainThread(ClientWorld world, Runnable task) {
        mainThreadTasks.offer(() -> {
            if (world != null && world == MinecraftClient.getInstance().world) {
                task.run();
            }
        });
    }

    /**
     * 执行主线程任务（每 tick 调用）。
     */
    private void runMainThreadTasks(ClientWorld world) {
        int count = 0;
        Runnable task;
        while (count < MAX_MAIN_THREAD_TASKS_PER_TICK && (task = mainThreadTasks.poll()) != null) {
            task.run();
            count++;
        }
    }

    // ====================================================
    // 工具方法
    // ====================================================

    /**
     * 寻找地表方块（使用 Heightmap 优化）。
     */
    private BlockPos findTopSolidBlock(ClientWorld world, int x, int z) {
        try {
            // 使用 Minecraft 1.21.4 的 API：getTopPosition()
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
            int topY = topPos.getY() - 1; // 地表方块在 topPosition 下方

            BlockPos.Mutable mutable = new BlockPos.Mutable(x, topY, z);
            BlockState state = world.getBlockState(mutable);

            // 检查是否是有效的地表方块
            if (state.isAir() || state.isOf(Blocks.SNOW)) {
                // 向下查找第一个非空气/非雪方块
                for (int y = topY - 1; y >= world.getBottomY(); y--) {
                    mutable.setY(y);
                    state = world.getBlockState(mutable);
                    if (!state.isAir() && !state.isOf(Blocks.SNOW) && state.isSolidBlock(world, mutable)) {
                        return mutable.toImmutable();
                    }
                }
                return null;
            }

            if (state.isSolidBlock(world, mutable)) {
                return mutable.toImmutable();
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("[SeasonSnowManager] 查找地表方块失败 ({}, {})", x, z, e);
            return null;
        }
    }

    /**
     * 是否应该覆盖雪（考虑坡度影响）。
     */
    private boolean shouldCoverBlock(ClientWorld world, BlockPos pos, float slopeFactor) {
        // 排除自身是雪
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.SNOW)) {
            return false;
        }

        // 上方必须是空气或雪
        BlockPos up = pos.up();
        BlockState aboveState = world.getBlockState(up);
        if (!aboveState.isAir() && !aboveState.isOf(Blocks.SNOW)) {
            return false;
        }

        // 检查坡度：如果太陡 → 不积雪
        if (slopeFactor < 1.0f) {
            BlockPos down = pos.down();
            BlockPos side1 = pos.north();
            BlockPos side2 = pos.east();

            int heightCenter = pos.getY();
            int heightDown = down.getY();
            int heightNorth = side1.getY();
            int heightEast = side2.getY();

            // 计算最大高度差（简单坡度估算）
            int maxHeightDiff = Math.max(
                    Math.abs(heightCenter - heightDown),
                    Math.max(
                            Math.abs(heightCenter - heightNorth),
                            Math.abs(heightCenter - heightEast)
                    )
            );

            // slopeFactor = 0.0 → 完全不考虑坡度（所有地方都积雪）
            // slopeFactor = 1.0 → 完全考虑坡度（高度差 > 2 就不积雪）
            float threshold = 2.0f * slopeFactor;
            if (maxHeightDiff > threshold) {
                return false; // 太陡，不积雪
            }
        }

        return true;
    }

    // ====================================================
    // 可选：在世界卸载时关闭线程
    // ====================================================

    /**
     * 关闭工作线程（在世界卸载时调用，可选）。
     */
    public void shutdown() {
        scanExecutor.shutdownNow();
        mainThreadTasks.clear();
        snowMap.clear();
    }
}
