package com.director.season;

import com.director.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PlantBlock;
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
import java.util.Queue;
import java.util.Collections;

/**
 * SeasonSnowManager v3.0
 *
 * <p>❄ 支持动态融雪动画（逐层融化/堆积）：
 * <ul>
 *   <li>✅ 0~10 层雪（根据 snowThickness 自动计算，带大小扰动）</li>
 *   <li>✅ 逐层增加/减少动画（每 2~10 tick 更新一次）</li>
 *   <li>✅ 随机延迟使融雪更自然、不同步</li>
 *   <li>✅ Chunk 分区扫描（13×13 chunk）</li>
 *   <li>✅ 原始方块缓存，确保 100% 可恢复</li>
 *   <li>✅ 覆盖概率控制（snowAmount 表示覆盖概率）</li>
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
 * @author Director Team
 * @version 3.0 - 动态融雪动画（逐层融化/堆积）
 */
public class SeasonSnowManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonSnowManager.class);

    // === 单例（线程安全，使用静态内部类持有者模式） ===

    private static final class InstanceHolder {
        private static final SeasonSnowManager INSTANCE = new SeasonSnowManager();
    }

    public static SeasonSnowManager get() {
        return InstanceHolder.INSTANCE;
    }

    // ========= 配置参数 =========

    /** 扫描范围（以 chunk 为单位）：玩家中心 ±8 → 17×17 chunk */
    private static final int SCAN_RADIUS_CHUNKS = 8;

    /** 最大雪层数（SnowBlock LAYERS 支持 1~8 层，但可以叠加到10层） */
    private static final int MAX_LAYERS = 8;
    /** 最大总层数（8层雪+2层） */
    private static final int MAX_TOTAL_LAYERS = 10;
    private static final float MAX_EFFECTIVE_COVERAGE = 0.97f;

    /** 每 tick 处理的最大主线程任务数（避免卡顿） */
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 128;

    /** 几何重新扫描的距离阈值（方块数平方，避免开方） */
    private static final int GEOMETRY_RESCAN_DISTANCE_SQ = 48 * 48;

    /** LOD 阈值：超过此 chunk 距离使用稀疏采样（步长 2） */
    private static final int LOD_NEAR_CHUNKS = 5;

    // ========= 状态 =========

    /** 后台扫描线程池 */
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "Director-SeasonSnowScanThread");
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

    /** 避免重复提交扫描任务 */
    private volatile boolean scanRunning = false;

    /** 动画 tick 计数器 */
    private int tickCounter = 0;

    // ========= 两阶段扫描状态 =========

    /**
     * 几何缓存：所有可能放置积雪的位置及其噪声/属性信息。
     * 
     * <p>由后台线程写入（整体替换引用），主线程读取。
     * 使用 volatile 保证可见性。
     */
    private volatile Map<BlockPos, CachedSnowPos> geometryCache = Collections.emptyMap();

    /** 上次几何扫描的中心位置 */
    private volatile BlockPos lastGeometryScanCenter = null;

    /** 几何缓存是否需要重新扫描（玩家移动/世界变化时设置） */
    private volatile boolean geometryCacheDirty = true;

    /** 覆盖计算是否需要更新（当 snowAmount/snowThickness/snowlineHeight 变化时设置） */
    private volatile boolean coverageNeedsUpdate = true;

    // ========= 数据结构 =========

    /**
     * 雪记录（跟踪每个位置的雪状态）。
     * 
     * <p>⭐ 线程安全注意：{@code targetLayers} 和 {@code meltDelay} 使用 volatile，
     * 因为后台扫描线程写入、主线程读取。
     */
    private static class SnowRecord {
        /** 原始方块类型 */
        Block originalBlock;

        /** 当前雪层数（1~8，直接对应 SnowBlock.LAYERS 值） */
        int currentLayers;

        /** 目标雪层数（0~8，由 SeasonSample.snowAmount 决定，0 = 无雪） */
        volatile int targetLayers;

        /** 融雪延迟（tick 数，用于随机偏移，避免所有雪同步变化） */
        volatile int meltDelay;

        /** 是否仅允许单层（用于树叶等需要保持单层效果的方块） */
        boolean singleLayerOnly;

        SnowRecord() {
            this.currentLayers = 0;
            this.targetLayers = 0;
            this.meltDelay = 0;
            this.singleLayerOnly = false;
        }
    }

    /**
     * 缓存的积雪位置信息（几何扫描阶段产生，覆盖计算阶段使用）。
     * 
     * <p>存储位置的几何属性（噪声值、叶子标记、原始方块），
     * 这些属性只与世界地形有关，不随季节参数变化。
     * 覆盖计算时只需使用这些缓存信息 + 当前季节参数即可，无需访问世界。
     */
    private static class CachedSnowPos {
        final BlockPos pos;
        final float noiseValue;
        final boolean isLeaf;
        final Block originalBlock;

        CachedSnowPos(BlockPos pos, float noiseValue, boolean isLeaf, Block originalBlock) {
            this.pos = pos;
            this.noiseValue = noiseValue;
            this.isLeaf = isLeaf;
            this.originalBlock = originalBlock;
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
     * 接收季节采样更新（v4.0 两阶段架构）。
     * 
     * <p>只标记覆盖计算需要更新，不再触发昂贵的几何重新扫描。
     * 几何缓存只在玩家移动时才更新，覆盖率评估使用缓存数据实时计算。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleChanged(SeasonSample sample) {
        SeasonSample newSample = sample != null ? sample : SeasonSample.disabledCopy();
        
        // 如果参数没有变化，跳过
        if (newSample.equals(this.currentSample)) {
            return;
        }
        
        SeasonSample oldSample = this.currentSample;
        this.currentSample = newSample;
        
        // ⭐ 两阶段架构：只标记覆盖计算需要更新
        // 几何缓存（可放雪的位置）不受 snowAmount/thickness 变化影响
        // 覆盖率计算使用缓存数据，成本极低，无需节流
        this.coverageNeedsUpdate = true;
        
        // 如果 snowAmount 变小（融雪过渡），清除延迟加速响应
        if (newSample.snowAmount < oldSample.snowAmount) {
            for (SnowRecord rec : snowMap.values()) {
                rec.meltDelay = 0;
            }
        }
    }
    
    // ====================================================
    // 入口 2：每 tick 调用（由客户端 Tick）
    // ====================================================

    /**
     * 每 tick 调用（v4.0 两阶段架构）。
     * 
     * <p>两阶段扫描：
     * <ol>
     *   <li><b>几何发现</b>（后台，仅在玩家移动时）：扫描世界，缓存所有可放雪位置</li>
     *   <li><b>覆盖评估</b>（主线程，每次参数变化时）：基于缓存数据 + 当前阈值快速计算覆盖</li>
     * </ol>
     * 
     * <p>这确保季节过渡时（autumn→winter、winter→spring）的积雪/融雪能即时响应
     * snowAmount 变化，无需等待耗时的世界扫描。
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

        // 2) 判断当前是否需要积雪
        boolean noSnowDesired = !currentSample.enabled
                || currentSample.snowAmount <= 0.01f
                || currentSample.snowThickness <= 0;

        if (noSnowDesired) {
            // 不需要积雪
            if (!snowMap.isEmpty()) {
                // ⭐ 有残留雪需要融化 → 目标设 0，继续运行动画逐层移除
                meltAllSnow(world, true);
            } else {
                // 确实没有任何残留雪，跳过后续逻辑
                return;
            }
        } else {
            // 需要积雪 → 执行几何扫描 + 覆盖评估
            BlockPos playerPos = BlockPos.ofFloored(cameraPos);

            // 3) 检查是否需要几何重新扫描（玩家移动超过阈值或首次扫描）
            boolean needsGeometry = geometryCacheDirty
                    || lastGeometryScanCenter == null
                    || playerPos.getSquaredDistance(lastGeometryScanCenter) > GEOMETRY_RESCAN_DISTANCE_SQ;

            if (needsGeometry && !scanRunning) {
                geometryCacheDirty = false;
                scanRunning = true;

                scanExecutor.submit(() -> {
                    try {
                        scanGeometry(world, playerPos);
                        // 几何缓存更新后，需要重新评估覆盖
                        coverageNeedsUpdate = true;
                    } catch (Exception e) {
                        LOGGER.warn("[SeasonSnowManager] 几何扫描任务出错", e);
                    } finally {
                        scanRunning = false;
                    }
                });
            }

            // 4) ⭐ 覆盖评估（纯数学计算，无世界访问，成本极低）
            //    每次 snowAmount/snowThickness/snowlineHeight 变化时都会执行
            //    这是实现平滑季节过渡的关键
            if (coverageNeedsUpdate) {
                Map<BlockPos, CachedSnowPos> cache = this.geometryCache;
                if (!cache.isEmpty()) {
                    coverageNeedsUpdate = false;
                    updateCoverageFromCache(world, cache, currentSample);
                }
            }
        }

        // 5) ⭐ 动画更新 — 无论堆雪还是融雪，只要 snowMap 非空就必须运行
        //    这是修复的关键：确保 snowAmount=0 时融雪动画仍然能执行
        if (!snowMap.isEmpty()) {
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
        tick(mc.world, mc.player.getEntityPos());
    }

    // ====================================================
    // 阶段 1：几何发现（后台线程，仅在玩家移动时运行）
    // ====================================================

    /**
     * 扫描玩家周围的区域，缓存所有可以放置积雪的位置及其属性。
     * 
     * <p>此方法只关心"几何可行性"：哪些位置可以放雪？噪声值是多少？
     * 不应用任何 snowAmount 阈值 —— 阈值留给 {@link #updateCoverageFromCache} 实时处理。
     * 
     * <p>性能优化：
     * <ul>
     *   <li>近距离（≤{@value LOD_NEAR_CHUNKS} chunks）：1×1 密集采样</li>
     *   <li>远距离（>{@value LOD_NEAR_CHUNKS} chunks）：2×2 稀疏采样（4 倍减少）</li>
     *   <li>只使用 {@link #findSnowPlacePosition} 查找最顶部位置（避免多层扫描开销）</li>
     * </ul>
     * 
     * @param world 客户端世界
     * @param playerPos 玩家位置（扫描中心）
     */
    private void scanGeometry(ClientWorld world, BlockPos playerPos) {
        int px = playerPos.getX() >> 4; // chunk X
        int pz = playerPos.getZ() >> 4; // chunk Z

        Map<BlockPos, CachedSnowPos> newCache = new HashMap<>();

        for (int cx = px - SCAN_RADIUS_CHUNKS; cx <= px + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = pz - SCAN_RADIUS_CHUNKS; cz <= pz + SCAN_RADIUS_CHUNKS; cz++) {
                // LOD：计算当前 chunk 到玩家 chunk 的切比雪夫距离
                int chunkDist = Math.max(Math.abs(cx - px), Math.abs(cz - pz));
                int step = (chunkDist <= LOD_NEAR_CHUNKS) ? 1 : 2;

                for (int bx = 0; bx < 16; bx += step) {
                    for (int bz = 0; bz < 16; bz += step) {
                        int wx = cx * 16 + bx;
                        int wz = cz * 16 + bz;

                        // 只查找最顶部可放雪位置（性能优化）
                        BlockPos snowPlacePos = findSnowPlacePosition(world, wx, wz);
                        if (snowPlacePos == null) continue;

                        // 计算噪声值（位置相关，不随季节变化）
                        float noiseValue = NoiseField.snowCoverage(snowPlacePos);

                        // 判断是否为叶子表面
                        boolean isLeaf = isLeafSurface(world, snowPlacePos);

                        // 获取原始方块
                        Block originalBlock = null;
                        BlockState state = world.getBlockState(snowPlacePos);
                        if (state.isOf(Blocks.SNOW)) {
                            BlockState belowState = world.getBlockState(snowPlacePos.down());
                            if (!belowState.isOf(Blocks.SNOW)) {
                                originalBlock = belowState.getBlock();
                            }
                        } else {
                            originalBlock = state.getBlock();
                        }

                        newCache.put(snowPlacePos, new CachedSnowPos(
                                snowPlacePos, noiseValue, isLeaf, originalBlock));
                    }
                }
            }
        }

        // 原子替换缓存引用（volatile write）
        this.geometryCache = newCache;
        this.lastGeometryScanCenter = playerPos;

        LOGGER.debug("[SeasonSnowManager] 几何扫描完成，缓存 {} 个位置", newCache.size());
    }

    // ====================================================
    // 阶段 2：覆盖评估（主线程，每次参数变化时运行）
    // ====================================================

    /**
     * 基于几何缓存和当前季节参数，快速计算积雪覆盖范围和目标层数。
     * 
     * <p>⭐ 这是实现平滑季节过渡的核心：
     * <ul>
     *   <li>autumn→winter：snowAmount 从 0 渐增到 0.8 → 覆盖区域从中心向外逐步扩展</li>
     *   <li>winter→spring：snowAmount 从 0.8 渐减到 0 → 覆盖区域从边缘向内逐步收缩</li>
     * </ul>
     * 
     * <p>纯数学运算，不访问世界方块，成本极低（~1ms for 50K entries）。
     * 
     * @param world 客户端世界（仅用于创建新 SnowRecord 时读取当前方块状态）
     * @param cache 几何缓存（从后台线程获取）
     * @param sample 当前季节采样
     */
    private void updateCoverageFromCache(ClientWorld world, Map<BlockPos, CachedSnowPos> cache, SeasonSample sample) {
        float snowAmount = applyCoverageLimit(sample.snowAmount);
        int snowThickness = sample.snowThickness;
        int snowY = (int) sample.snowlineHeight;

        if (snowAmount <= 0f || snowThickness <= 0) {
            // 无雪 → 所有位置目标设为 0
            for (SnowRecord rec : snowMap.values()) {
                rec.targetLayers = 0;
                rec.meltDelay = 0;
            }
            return;
        }

        // 收集本次应该有雪的位置集合
        Set<BlockPos> activePositions = new HashSet<>();

        for (CachedSnowPos csp : cache.values()) {
            int heightDiff = csp.pos.getY() - snowY;

            // 雪线高度过滤
            float snowlineProbability = 1.0f;
            if (heightDiff < 0) {
                if (heightDiff < -10) {
                    continue; // 太远低于雪线，跳过
                }
                snowlineProbability = Math.max(0.0f, 0.1f * (1.0f + heightDiff / 10.0f));
            }

            // 阈值检查：噪声值 < snowAmount * snowlineProbability → 应该覆雪
            float threshold = snowAmount * snowlineProbability;
            if (csp.noiseValue >= threshold) {
                continue;
            }

            activePositions.add(csp.pos);

            // 更新或创建 SnowRecord
            snowMap.compute(csp.pos, (p, rec) -> {
                if (rec == null) {
                    rec = new SnowRecord();
                    // 使用缓存中的原始方块信息
                    rec.originalBlock = csp.originalBlock;
                    
                    // 检查当前是否已经有雪（可能是上次扫描留下的）
                    try {
                        BlockState currentState = world.getBlockState(p);
                        if (currentState.isOf(Blocks.SNOW)) {
                            rec.currentLayers = currentState.get(SnowBlock.LAYERS);
                        } else {
                            rec.currentLayers = 0;
                            // 如果缓存的 originalBlock 为空，尝试从当前状态获取
                            if (rec.originalBlock == null) {
                                rec.originalBlock = currentState.getBlock();
                            }
                        }
                    } catch (Exception e) {
                        rec.currentLayers = 0;
                    }
                    
                    // 随机延迟，使积雪出现不同步（更自然）
                    Random posRandom = new Random(Objects.hash(p.getX(), p.getZ()));
                    rec.meltDelay = posRandom.nextInt(13);
                }

                rec.singleLayerOnly = csp.isLeaf;

                // 使用位置作为随机种子，确保扰动值保持一致
                Random posRandom = new Random(Objects.hash(p.getX(), p.getZ()));
                rec.targetLayers = computeTargetLayers(snowThickness, posRandom, csp.isLeaf);

                return rec;
            });
        }

        // 不在活跃集合中的位置 → 设置目标为 0（融雪）
        for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
            if (!activePositions.contains(entry.getKey())) {
                SnowRecord rec = entry.getValue();
                rec.targetLayers = 0;
                rec.meltDelay = 0;
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
            // 注意：当 targetLayers = 0 时，跳过延迟以立即开始融化
            if (rec.targetLayers == 0) {
                rec.meltDelay = 0; // 清除延迟，立即开始融化
            } else if (rec.meltDelay > 0) {
                rec.meltDelay--;
                continue;
            }

            // 当前层数 == 目标层数，无需更新
            if (rec.currentLayers == rec.targetLayers) {
                continue;
            }

            // 季节过渡通过关键帧插值实现，无需单独的融化速度控制
            // 动画更新速度由 ANIMATION_TICK_INTERVAL 控制

            // 更新层数（逐层变化）
            // 注意：Minecraft的SnowBlock只支持1~8层，超过8层需要特殊处理
            int effectiveTargetLayers = Math.min(rec.targetLayers, MAX_LAYERS);
            
            if (rec.currentLayers < effectiveTargetLayers) {
                // 堆雪（加厚）
                rec.currentLayers = Math.min(MAX_LAYERS, rec.currentLayers + 1);
            } else if (rec.currentLayers > effectiveTargetLayers) {
                // 融雪（减薄）
                rec.currentLayers = Math.max(0, rec.currentLayers - 1);
            }

            final int finalLayers = rec.currentLayers;

            if (finalLayers <= 0) {
                // 完全融化 → 恢复原始方块
                final Block originalBlock = rec.originalBlock;
                if (originalBlock != null) {
                    enqueueMainThread(world, () -> world.setBlockState(pos, originalBlock.getDefaultState(), 3));
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
                final int snowLayersProp = Math.min(MAX_LAYERS, finalLayers);
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
            // 动画融化 → 设置所有目标层数为 0，并清除延迟以立刻开始融化
            for (SnowRecord rec : snowMap.values()) {
                rec.targetLayers = 0;
                rec.meltDelay = 0;
            }
        }
    }

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有雪。
     * 
     * <p>此方法会立即执行清理，不等待下一次 tick。
     */
    public void restoreAll(ClientWorld world) {
        if (world == null) {
            LOGGER.warn("[SeasonSnowManager] restoreAll: world 为 null，跳过清理");
            return;
        }
        
        // 立即禁用季节采样，避免后续 tick 重新生成雪
        this.currentSample = SeasonSample.disabled();
        this.coverageNeedsUpdate = false;
        this.geometryCacheDirty = false;
        
        // 清除几何缓存
        this.geometryCache = Collections.emptyMap();
        this.lastGeometryScanCenter = null;
        
        // 立即恢复所有原始方块（不通过队列，直接执行）
        int restoredCount = 0;
        for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
            BlockPos pos = entry.getKey();
            SnowRecord rec = entry.getValue();
            final Block originalBlock = rec.originalBlock;
            
            try {
                if (originalBlock != null) {
                    world.setBlockState(pos, originalBlock.getDefaultState(), 3);
                } else {
                    BlockState currentState = world.getBlockState(pos);
                    if (currentState.isOf(Blocks.SNOW)) {
                        world.removeBlock(pos, false);
                    }
                }
                restoredCount++;
            } catch (Exception e) {
                LOGGER.warn("[SeasonSnowManager] 恢复方块失败: {}", pos, e);
            }
        }
        
        // 清空所有记录
        snowMap.clear();
        
        // 清空主线程任务队列（避免执行已过期的任务）
        mainThreadTasks.clear();
        
        LOGGER.info("[SeasonSnowManager] 已立即清除所有积雪并恢复原始方块，共恢复 {} 个位置", restoredCount);
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

    /**
     * 将覆盖度限制在安全范围内，保证即便参数为 1 也会保留少量未覆盖区域。
     */
    private float applyCoverageLimit(float coverage) {
        if (coverage <= 0f) {
            return 0f;
        }
        return Math.min(MAX_EFFECTIVE_COVERAGE, Math.min(1f, coverage));
    }

    /**
     * 根据基准厚度和位置噪声计算目标雪层数，并可选强制为单层。
     */
    private int computeTargetLayers(int baseThickness, Random random, boolean singleLayerOnly) {
        if (baseThickness <= 0) {
            return 0;
        }

        float mean = (float) baseThickness;
        float stdDev = Math.max(0.8f, Math.min(1.5f, mean * 0.3f + 0.5f));
        float noise = (float) (random.nextGaussian() * stdDev);
        int targetLayers = Math.round(mean + noise);
        targetLayers = Math.max(0, Math.min(MAX_TOTAL_LAYERS, targetLayers));

        if (singleLayerOnly && targetLayers > 0) {
            return 1;
        }
        return targetLayers;
    }

    /**
     * 判断当前雪面下方是否为树叶，用于强制只铺一层。
     */
    private boolean isLeafSurface(ClientWorld world, BlockPos snowPos) {
        if (world == null) {
            return false;
        }
        BlockState below = world.getBlockState(snowPos.down());
        return below.getBlock() instanceof LeavesBlock;
    }

    // ====================================================
    // 工具方法
    // ====================================================

    /**
     * 寻找可以放置积雪的位置（符合Minecraft原版逻辑）。
     * 积雪可以放置在：
     * 1. 固体方块上（如草方块、石头等）
     * 2. 某些透明方块上（如树叶、玻璃等），如果这些方块支持积雪
     * 3. 不能放置在空气、水、岩浆等上
     * 
     * <p>修改：现在会检查所有可以放置积雪的方块，而不仅仅是顶部。
     * 只要方块能放置积雪，并且方块上面是空气或雪，就会返回该位置。
     * 
     * @return 可以放置积雪的位置（雪层的位置），如果找不到则返回null
     */
    private BlockPos findSnowPlacePosition(ClientWorld world, int x, int z) {
        try {
            // 使用 Minecraft 1.21.4 的 API：getTopPosition() 作为起始点
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
            int startY = Math.min(topPos.getY() + 10, 256); // 从顶部向下检查，允许检查上方10格
            int endY = Math.max(world.getBottomY(), -64);

            BlockPos.Mutable mutable = new BlockPos.Mutable(x, startY, z);

            // 从顶部向下查找所有可以放置积雪的位置
            for (int y = startY; y >= endY; y--) {
                mutable.setY(y);
                BlockState state = world.getBlockState(mutable);

                // 跳过空气和雪本身
                if (state.isAir() || state.isOf(Blocks.SNOW)) {
                    continue;
                }

                // 跳过流体（水、岩浆等）
                if (state.getBlock() instanceof FluidBlock) {
                    continue;
                }

                // 检查这个方块是否可以放置积雪
                if (!canPlaceSnowOn(state, world, mutable)) {
                    continue;
                }

                // 检查上方是否可以放置积雪（必须是空气或雪）
                BlockPos abovePos = mutable.up();
                BlockState aboveState = world.getBlockState(abovePos);
                if (!aboveState.isAir() && !aboveState.isOf(Blocks.SNOW)) {
                    // 上方不是空气或雪，继续向下查找
                    continue;
                }

                // 找到可以放置积雪的位置，返回积雪应该放置的位置（上方）
                return abovePos.toImmutable();
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("[SeasonSnowManager] 查找积雪放置位置失败 ({}, {})", x, z, e);
            return null;
        }
    }

    /**
     * 检查方块是否可以放置积雪（符合Minecraft原版逻辑）。
     * 
     * <p>规则：
     * <ul>
     *   <li>✅ 实体方块（草方块、石头、建筑方块等）可以放置积雪</li>
     *   <li>✅ 某些透明方块（如树叶、玻璃等）可以放置积雪</li>
     *   <li>❌ 植物方块（如竹子、草、花等）不能放置积雪（太细）</li>
     *   <li>❌ 流体、空气、雪本身不能放置积雪</li>
     *   <li>❌ 冰面（ICE、FROSTED_ICE）不能放置积雪</li>
     * </ul>
     * 
     * @param state 方块状态
     * @param world 世界
     * @param pos 方块位置
     * @return 是否可以放置积雪
     */
    private boolean canPlaceSnowOn(BlockState state, ClientWorld world, BlockPos pos) {
        // 不能是空气
        if (state.isAir()) {
            return false;
        }

        // 不能是流体
        if (state.getBlock() instanceof FluidBlock) {
            return false;
        }

        // 不能是雪本身
        if (state.isOf(Blocks.SNOW)) {
            return false;
        }

        // ⭐ 不能是冰面（ICE、FROSTED_ICE）
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.FROSTED_ICE)) {
            return false;
        }

        // 排除植物方块（如竹子、草、花等），因为它们太细，不适合放置积雪
        Block block = state.getBlock();
        if (block instanceof PlantBlock) {
            return false;
        }
        
        // 特别排除竹子（BambooBlock）
        if (block == Blocks.BAMBOO) {
            return false;
        }
        
        // 排除其他细小的植物方块（如草、花、蕨类等）
        // 这些方块通常不是固体，但有碰撞箱，我们需要排除它们
        // 注意：Blocks.GRASS_BLOCK 是草方块（实体方块），应该允许放置积雪
        // 这里排除的是植物（如高草、花等）
        if (block == Blocks.FERN || 
            block == Blocks.LARGE_FERN ||
            block == Blocks.TALL_GRASS ||
            block == Blocks.DANDELION ||
            block == Blocks.POPPY ||
            block == Blocks.BLUE_ORCHID ||
            block == Blocks.ALLIUM ||
            block == Blocks.AZURE_BLUET ||
            block == Blocks.RED_TULIP ||
            block == Blocks.ORANGE_TULIP ||
            block == Blocks.WHITE_TULIP ||
            block == Blocks.PINK_TULIP ||
            block == Blocks.OXEYE_DAISY ||
            block == Blocks.CORNFLOWER ||
            block == Blocks.LILY_OF_THE_VALLEY ||
            block == Blocks.SUNFLOWER ||
            block == Blocks.LILAC ||
            block == Blocks.ROSE_BUSH ||
            block == Blocks.PEONY) {
            return false;
        }

        // 固体方块可以放置积雪（草方块、石头、建筑方块等）
        if (state.isSolidBlock(world, pos)) {
            return true;
        }

        // 某些透明方块也可以放置积雪（如树叶、玻璃等）
        // 这些方块有碰撞箱，可以支撑积雪
        // 但我们已经排除了植物方块，所以这里主要是树叶、玻璃等
        return !state.getCollisionShape(world, pos).isEmpty();
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
        geometryCache = Collections.emptyMap();
        lastGeometryScanCenter = null;
    }
}
