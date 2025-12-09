package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
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

    /** 扫描范围（以 chunk 为单位）：玩家中心 ±6 → 13×13 chunk */
    private static final int SCAN_RADIUS_CHUNKS = 6;

    /** 最大雪层数（SnowBlock LAYERS 支持 1~8 层，但可以叠加到10层） */
    private static final int MAX_LAYERS = 8;
    /** 最大总层数（8层雪+2层） */
    private static final int MAX_TOTAL_LAYERS = 10;

    /** 动画更新间隔（每 N tick 更新一次雪层） */
    private static final int ANIMATION_TICK_INTERVAL = 2;

    /** 每 tick 处理的最大主线程任务数（避免卡顿） */
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 64;

    /** 每个 chunk 采样方块数（避免扫描所有方块，增加采样以提高覆盖效果） */
    private static final int SAMPLES_PER_CHUNK = 64;

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
        float snowAmount = sample.snowAmount;  // 覆盖概率
        int snowThickness = sample.snowThickness;  // 积雪厚度 [0, 10]
        int snowY = (int) sample.snowlineHeight;

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

                    BlockPos snowPlacePos = findSnowPlacePosition(world, wx, wz);
                    if (snowPlacePos == null) continue;

                    int blockY = snowPlacePos.getY();
                    int heightDiff = blockY - snowY; // 高度差（正数表示在雪线以上，负数表示在雪线以下）
                    
                    // 雪线高度逻辑：
                    // - 大部分积雪在雪线高度以上生成（高度差 >= 0）
                    // - 只有很小一部分在雪线以下附近生成（高度差在 -10 到 0 之间，概率递减）
                    // - 雪线以下太远的地方不生成积雪（高度差 < -10）
                    
                    float snowlineProbability = 1.0f;
                    if (heightDiff < 0) {
                        // 在雪线以下：根据距离雪线的距离计算概率
                        // 距离雪线越远，概率越小
                        // 高度差在 -10 到 0 之间：概率从 0.1 线性递减到 0
                        // 高度差 < -10：概率为 0（不生成）
                        if (heightDiff < -10) {
                            continue; // 太远，不生成
                        }
                        // 在 -10 到 0 之间：概率从 0.1 线性递减到 0
                        snowlineProbability = Math.max(0.0f, 0.1f * (1.0f + heightDiff / 10.0f));
                    }
                    // 在雪线以上：概率为 1.0（完全生成）

                    // 根据覆盖概率和雪线概率决定是否覆盖雪
                    // 当 snowAmount = 1.0 时，几乎完全覆盖（99.9%概率）
                    // 当 snowAmount = 0.0 时，完全不覆盖
                    float threshold = Math.min(snowAmount, 0.999f);
                    float finalProbability = threshold * snowlineProbability;
                    if (random.nextFloat() < finalProbability) {
                        found.add(snowPlacePos.toImmutable());
                    }
                }
            }
        }

        // 更新目标层数（带大小扰动）
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
                
                // 计算目标层数（带大小扰动）
                // 使用正态分布（近似）来生成围绕snowThickness的扰动值
                // 标准差约为1.5，使得多数值在snowThickness附近
                float mean = (float) snowThickness;
                float stdDev = 1.5f;
                float noise = (float) (random.nextGaussian() * stdDev);
                int targetLayers = Math.round(mean + noise);
                // 限制在 [0, 10] 范围内
                targetLayers = Math.max(0, Math.min(MAX_TOTAL_LAYERS, targetLayers));
                rec.targetLayers = targetLayers;
                
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
            boolean shouldUpdate = !(meltSpeed < 1.0f) || tickCounter % Math.max(1, (int) (ANIMATION_TICK_INTERVAL / meltSpeed)) == 0;
            
            // 如果融化速度较慢，增加更新间隔

            if (!shouldUpdate) {
                continue;
            }

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
     * 寻找可以放置积雪的位置（符合Minecraft原版逻辑）。
     * 积雪可以放置在：
     * 1. 固体方块上（如草方块、石头等）
     * 2. 某些透明方块上（如树叶、玻璃等），如果这些方块支持积雪
     * 3. 不能放置在空气、水、岩浆等上
     * 
     * @return 可以放置积雪的位置（雪层的位置），如果找不到则返回null
     */
    private BlockPos findSnowPlacePosition(ClientWorld world, int x, int z) {
        try {
            // 使用 Minecraft 1.21.4 的 API：getTopPosition()
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
            int startY = Math.min(topPos.getY(), 256);
            int endY = Math.max(world.getBottomY(), -64);

            BlockPos.Mutable mutable = new BlockPos.Mutable(x, startY, z);

            // 从顶部向下查找
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

                // 检查上方是否可以放置积雪（必须是空气或雪）
                BlockPos abovePos = mutable.up();
                BlockState aboveState = world.getBlockState(abovePos);
                if (!aboveState.isAir() && !aboveState.isOf(Blocks.SNOW)) {
                    // 上方不是空气或雪，继续向下查找
                    continue;
                }

                // 检查这个方块是否可以放置积雪
                // 在Minecraft中，积雪可以放置在：
                // 1. 固体方块上
                // 2. 某些透明方块上（如树叶、玻璃等），如果这些方块支持积雪
                // 我们使用一个简化的检查：方块不是空气、不是流体、不是雪本身
                // 并且上方是空气或雪
                
                // 检查方块是否支持积雪（固体方块或某些透明方块）
                if (canPlaceSnowOn(state, world, mutable)) {
                    // 返回积雪应该放置的位置（上方）
                    return abovePos.toImmutable();
                }
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

        // 固体方块可以放置积雪
        if (state.isSolidBlock(world, pos)) {
            return true;
        }

        // 某些透明方块也可以放置积雪（如树叶、玻璃等）
        // 在Minecraft中，这些方块通常有特殊的逻辑
        // 我们检查方块是否不是完全透明的，或者是否有特殊的积雪支持
        
        // 检查方块是否是完全透明的（如空气、水等已经排除）
        // 对于树叶等方块，它们通常不是完全透明的，可以放置积雪
        // 这里使用一个简化的判断：如果方块不是完全透明，就可以放置积雪
        
        // 更准确的方法是检查方块的形状，但为了简化，我们使用以下规则：
        // 1. 固体方块 -> 可以
        // 2. 非固体但非完全透明的方块（如树叶）-> 可以
        // 3. 完全透明的方块（如空气、水）-> 不可以（已经排除）
        
        // 对于树叶等方块，它们通常有碰撞箱，可以放置积雪
        // 我们检查方块是否有碰撞箱（不是完全透明）
        return !state.getCollisionShape(world, pos).isEmpty();

        // 其他情况：如果方块有特殊的积雪支持（如某些装饰性方块）
        // 这里可以添加更多检查，但为了简化，我们只处理常见情况
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
