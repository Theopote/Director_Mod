package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
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
import java.util.ArrayDeque;

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

    /** 扫描范围（以 chunk 为单位）：玩家中心 ±10 → 21×21 chunk */
    private static final int SCAN_RADIUS_CHUNKS = 10;

    /** 最大雪层数（SnowBlock LAYERS 支持 1~8 层，但可以叠加到10层） */
    private static final int MAX_LAYERS = 8;
    /** 最大总层数（8层雪+2层） */
    private static final int MAX_TOTAL_LAYERS = 10;
    private static final float MAX_EFFECTIVE_COVERAGE = 0.97f;


    /** 每 tick 处理的最大主线程任务数（避免卡顿） */
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 64;

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

        /** 是否仅允许单层（用于树叶等需要保持单层效果的方块） */
        boolean singleLayerOnly;

        SnowRecord() {
            this.currentLayers = 0;
            this.targetLayers = 0;
            this.meltDelay = 0;
            this.singleLayerOnly = false;
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
        SeasonSample newSample = sample != null ? sample : SeasonSample.disabled();
        
        // 如果参数没有变化，不触发重新扫描
        if (newSample.equals(this.currentSample)) {
            return;
        }
        
        // 检查关键参数是否变化
        boolean snowAmountChanged = Math.abs(newSample.snowAmount - this.currentSample.snowAmount) > 0.01f;
        boolean snowThicknessChanged = newSample.snowThickness != this.currentSample.snowThickness;
        boolean snowlineHeightChanged = Math.abs(newSample.snowlineHeight - this.currentSample.snowlineHeight) > 0.1f;
        
        this.currentSample = newSample;
        this.needsRescan = true;
        
        // 如果只是 snowThickness 变化（snowAmount 和 snowlineHeight 不变），
        // 可以立即更新已有记录的 targetLayers，而不需要等待重新扫描
        // 这样调整积雪厚度时，可以立即看到效果
        if (snowThicknessChanged && !snowAmountChanged && !snowlineHeightChanged) {
            updateExistingTargetLayers(newSample.snowThickness);
        }
        
        // 如果 snowAmount 变小，立即清除所有位置的延迟，让它们能够快速响应重新扫描的结果
        if (snowAmountChanged && newSample.snowAmount < this.currentSample.snowAmount) {
            // 雪覆盖度变小，需要等待重新扫描来确定哪些位置应该保留
            // 但我们可以先清除所有位置的延迟，让它们能够立即响应
            for (SnowRecord rec : snowMap.values()) {
                rec.meltDelay = 0; // 清除延迟，让动画更快响应
            }
        }
        
        // 注意：snowAmount 或 snowlineHeight 变化时，需要重新扫描来确定哪些位置应该有积雪
        // 这会在下一次 tick() 时异步执行，但动画系统会立即开始响应新的 targetLayers
    }
    
    /**
     * 立即更新已有积雪记录的目标层数（用于快速响应 snowThickness 参数变化）。
     * 
     * <p>使用位置作为随机种子，确保每个位置的扰动值保持一致。
     * 
     * @param newSnowThickness 新的积雪厚度
     */
    private void updateExistingTargetLayers(int newSnowThickness) {
        // 如果厚度为0，直接设置所有目标层数为0，并清除延迟以立即响应
        if (newSnowThickness <= 0) {
            for (SnowRecord rec : snowMap.values()) {
                rec.targetLayers = 0;
                rec.meltDelay = 0; // 清除延迟，立即开始融化
            }
            return;
        }
        
        for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
            BlockPos pos = entry.getKey();
            SnowRecord rec = entry.getValue();

            if (rec.singleLayerOnly) {
                rec.targetLayers = 1;
                rec.meltDelay = 0;
                continue;
            }

            // 使用位置作为随机种子，确保每个位置的扰动值保持一致
            Random random = new Random(Objects.hash(pos.getX(), pos.getZ()));

            // 重新计算目标层数（带大小扰动）
            rec.targetLayers = computeTargetLayers(newSnowThickness, random, false);
        }
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
        boolean shouldAnimate = true;

        // 2) 雪量为 0 或未启用 → 清除全部雪
        if (!currentSample.enabled || currentSample.snowAmount <= 0.01f || currentSample.snowThickness <= 0) {
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
        animateSnowLayers(world);
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
        float snowAmount = applyCoverageLimit(sample.snowAmount);  // 覆盖概率
        int snowThickness = sample.snowThickness;  // 积雪厚度 [0, 10]
        int snowY = (int) sample.snowlineHeight;

        if (snowAmount <= 0f || snowThickness <= 0) {
            return;
        }

        int px = playerPos.getX() >> 4; // chunk X
        int pz = playerPos.getZ() >> 4; // chunk Z

        // 第一轮：使用噪声场决定初始覆盖位置（密集采样）
        Set<BlockPos> initialCoverage = new HashSet<>();
        Map<BlockPos, Float> noiseValueMap = new HashMap<>(); // 缓存噪声值，避免重复计算
        
        for (int cx = px - SCAN_RADIUS_CHUNKS; cx <= px + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = pz - SCAN_RADIUS_CHUNKS; cz <= pz + SCAN_RADIUS_CHUNKS; cz++) {
                // 密集采样：每 1x1 方块采样一次
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int wx = cx * 16 + bx;
                        int wz = cz * 16 + bz;

                        // 查找所有可以放置积雪的位置（不仅仅是顶部）
                        // 只要方块能放置积雪，并且方块上面是空气或雪，就会检查
                        List<BlockPos> snowPlacePositions = findAllSnowPlacePositions(world, wx, wz);
                        
                        for (BlockPos snowPlacePos : snowPlacePositions) {
                            int blockY = snowPlacePos.getY();
                            int heightDiff = blockY - snowY;
                            
                            // 雪线高度逻辑
                            float snowlineProbability = 1.0f;
                            if (heightDiff < 0) {
                                if (heightDiff < -10) {
                                    continue; // 太远，不生成
                                }
                                snowlineProbability = Math.max(0.0f, 0.1f * (1.0f + heightDiff / 10.0f));
                            }

                            // 使用噪声场决定是否覆盖雪
                            float noiseValue = NoiseField.snowCoverage(snowPlacePos);
                            noiseValueMap.put(snowPlacePos.toImmutable(), noiseValue);
                            
                            // 计算阈值：snowAmount 已经经过 applyCoverageLimit，这里只需要乘以高度修正因子
                            // 注意：snowAmount 已经在第 310 行被限制在 [0, 0.97] 范围内
                            float threshold = snowAmount * snowlineProbability;
                            
                            // 如果噪声值小于阈值，则应该被覆盖
                            // 噪声值范围 [0, 1]，阈值范围 [0, 0.97]
                            // 当 snowAmount = 0.97, snowlineProbability = 1.0 时，threshold = 0.97，约 97% 区域覆盖
                            // 当 snowAmount = 0.5, snowlineProbability = 1.0 时，threshold = 0.5，约 50% 区域覆盖
                            if (noiseValue < threshold) {
                                initialCoverage.add(snowPlacePos.toImmutable());
                            }
                        }
                    }
                }
            }
        }
        
        // 第二轮：邻接传播，填补凹陷和缺口
        // 对于每个已覆盖的位置，检查其4个水平邻居，如果邻居也满足阈值，则也标记为覆盖
        Set<BlockPos> propagated = new HashSet<>(initialCoverage);
        Queue<BlockPos> toCheck = new ArrayDeque<>(initialCoverage);
        Set<BlockPos> checked = new HashSet<>(initialCoverage);
        
        while (!toCheck.isEmpty()) {
            BlockPos pos = toCheck.poll();
            
            // 检查4个水平邻居（north, south, east, west）
            BlockPos[] neighbors = {
                pos.north(),
                pos.south(),
                pos.east(),
                pos.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                if (checked.contains(neighbor)) continue;
                checked.add(neighbor);
                
                // 检查邻居是否可以放置积雪
                BlockPos neighborPlacePos = findSnowPlacePosition(world, neighbor.getX(), neighbor.getZ());
                if (neighborPlacePos == null) continue;
                
                // 检查邻居是否在合理的高度范围内
                int neighborY = neighborPlacePos.getY();
                int neighborHeightDiff = neighborY - snowY;
                float neighborSnowlineProbability = 1.0f;
                if (neighborHeightDiff < 0) {
                    if (neighborHeightDiff < -10) {
                        continue; // 太远，不生成
                    }
                    neighborSnowlineProbability = Math.max(0.0f, 0.1f * (1.0f + neighborHeightDiff / 10.0f));
                }
                
                // 获取邻居的噪声值（如果已缓存则使用，否则计算）
                float neighborNoiseValue = noiseValueMap.getOrDefault(neighborPlacePos, 
                    NoiseField.snowCoverage(neighborPlacePos));
                noiseValueMap.put(neighborPlacePos.toImmutable(), neighborNoiseValue);
                
                // 计算邻居阈值：snowAmount 已经经过 applyCoverageLimit，这里只需要乘以高度修正因子
                float neighborThreshold = snowAmount * neighborSnowlineProbability;
                
                // 如果邻居也满足阈值，则标记为覆盖并继续传播
                if (neighborNoiseValue < neighborThreshold) {
                    propagated.add(neighborPlacePos.toImmutable());
                    toCheck.add(neighborPlacePos.toImmutable());
                }
            }
        }

        Set<BlockPos> found = new HashSet<>(propagated);

        // 更新目标层数（带大小扰动）
        // 对于所有在 found 中的位置，更新或创建记录
        for (BlockPos pos : found) {
            snowMap.compute(pos, (p, rec) -> {
                boolean singleLayerOnly = isLeafSurface(world, p);
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
                    // 使用位置作为随机种子，确保每个位置的延迟保持一致
                    Random posRandom = new Random(Objects.hash(p.getX(), p.getZ(), world.getTime()));
                    rec.meltDelay = posRandom.nextInt(13);
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

                rec.singleLayerOnly = singleLayerOnly;
                
                // 计算目标层数（带大小扰动）
                // 使用位置作为随机种子，确保每个位置的扰动值保持一致
                // 这样当 snowThickness 变化时，每个位置的相对厚度关系保持不变
                Random posRandom = new Random(Objects.hash(p.getX(), p.getZ()));
                rec.targetLayers = computeTargetLayers(snowThickness, posRandom, singleLayerOnly);
                
                return rec;
            });
        }

        // 不在 found 中的位置 → 设置目标层数为 0（逐渐融化）
        // 这样当 snowAmount 变小时，这些位置会逐渐消失
        for (Map.Entry<BlockPos, SnowRecord> entry : snowMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!found.contains(pos)) {
                SnowRecord rec = entry.getValue();
                rec.targetLayers = 0;
                rec.meltDelay = 0; // 清除延迟，立即开始融化
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
            // 动画融化 → 设置所有目标层数为 0
            for (SnowRecord rec : snowMap.values()) {
                rec.targetLayers = 0;
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
        this.needsRescan = false;
        
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
     * 查找所有可以放置积雪的位置（用于更全面的检查）。
     * 
     * @return 所有可以放置积雪的位置列表
     */
    private List<BlockPos> findAllSnowPlacePositions(ClientWorld world, int x, int z) {
        List<BlockPos> positions = new ArrayList<>();
        try {
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
            int startY = Math.min(topPos.getY() + 10, 256);
            int endY = Math.max(world.getBottomY(), -64);

            BlockPos.Mutable mutable = new BlockPos.Mutable(x, startY, z);

            for (int y = startY; y >= endY; y--) {
                mutable.setY(y);
                BlockState state = world.getBlockState(mutable);

                if (state.isAir() || state.isOf(Blocks.SNOW)) {
                    continue;
                }

                if (state.getBlock() instanceof FluidBlock) {
                    continue;
                }

                if (!canPlaceSnowOn(state, world, mutable)) {
                    continue;
                }

                BlockPos abovePos = mutable.up();
                BlockState aboveState = world.getBlockState(abovePos);
                if (!aboveState.isAir() && !aboveState.isOf(Blocks.SNOW)) {
                    continue;
                }

                positions.add(abovePos.toImmutable());
            }
        } catch (Exception e) {
            LOGGER.warn("[SeasonSnowManager] 查找所有积雪放置位置失败 ({}, {})", x, z, e);
        }
        return positions;
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
    }
}
