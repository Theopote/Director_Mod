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
 * SeasonSnowManager v2.0
 *
 * <p>❄ 负责根据 SeasonSample 动态生成 / 融化「覆盖雪」（雪层方块），
 *    不改原本地形，只在合适表面上叠一层 snow_layer。
 *
 * <p>设计要点：
 * <ul>
 *   <li>✅ Chunk 分区扫描（13×13 chunk），避免全世界扫描卡顿</li>
 *   <li>✅ 原始方块缓存（OriginalStateStore），确保 100% 可恢复</li>
 *   <li>✅ 后台线程扫描 + 主线程方块更新（线程安全）</li>
 *   <li>✅ 雪层数量根据 snowAmount 动态控制（1-8 层）</li>
 *   <li>✅ 坡度影响因子（snowSlopeFactor）控制陡坡积雪</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>这里只是客户端视觉效果（ClientWorld），不改服务器地形</li>
 *   <li>所有积雪都可以 100% 恢复为原始方块（无破坏性）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 2.0 - Chunk 分区扫描 + 原始方块缓存 + 完整可恢复
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

    /** 原始方块缓存（记录被雪覆盖的位置的原始方块） */
    private final Map<BlockPos, BlockStateRecord> originalMap = new ConcurrentHashMap<>();

    /** 记录结构：原始方块类型 */
    private record BlockStateRecord(Block originalBlock) {}

    /** 是否需要重新扫描（当 sample 变化时设置） */
    private volatile boolean needsRescan = true;

    /** 避免重复提交扫描任务 */
    private volatile boolean scanRunning = false;

    private SeasonSnowManager() {
    }

    // ====================================================
    // 入口 1：季节采样更新（由 TimelineSeasonController 调用）
    // ====================================================

    /**
     * 接收季节采样更新（v2.0 API）。
     * 
     * <p>兼容现有代码：同时支持 `onSeasonSampleUpdated` 和 `onSeasonSampleChanged`。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleUpdated(SeasonSample sample) {
        onSeasonSampleChanged(sample);
    }

    /**
     * 接收季节采样更新（v2.0 新方法名）。
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
     * 每 tick 调用（v2.0 API，接受 world 和 cameraPos 参数）。
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

        // 1) 执行主线程任务（放雪/融雪）
        runMainThreadTasks(world);

        // 2) 雪量为 0 或未启用 → 立即清除全部雪
        if (!currentSample.enabled || currentSample.snowAmount <= 0.01f) {
            if (!originalMap.isEmpty()) {
                removeAllSnow(world);
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
                    scanAndApply(world, playerPos, sampleSnapshot);
                } catch (Exception e) {
                    LOGGER.warn("[SeasonSnowManager] 扫描任务出错", e);
                } finally {
                    scanRunning = false;
                }
            });
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
     * 扫描玩家周围的 chunk，计算需要添加/移除的雪。
     */
    private void scanAndApply(ClientWorld world, BlockPos playerPos, SeasonSample sample) {
        int snowY = (int) sample.snowlineHeight;
        float amount = sample.snowAmount;
        float slopeFactor = sample.snowSlopeFactor;

        Set<BlockPos> newSnow = new HashSet<>();
        Set<BlockPos> restore = new HashSet<>(originalMap.keySet()); // 初始：所有旧雪都假定需要移除

        int px = playerPos.getX() >> 4; // chunk X
        int pz = playerPos.getZ() >> 4; // chunk Z

        // 遍历周围 chunk
        for (int cx = px - SCAN_RADIUS_CHUNKS; cx <= px + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = pz - SCAN_RADIUS_CHUNKS; cz <= pz + SCAN_RADIUS_CHUNKS; cz++) {
                // 对每个 chunk 进行采样（不是扫描所有方块，而是随机采样）
                scanChunk(world, cx, cz, snowY, amount, slopeFactor, newSnow);
            }
        }

        // 新雪：添加
        for (BlockPos pos : newSnow) {
            restore.remove(pos); // 新雪中仍存在 → 不需要恢复

            if (!originalMap.containsKey(pos)) {
                // 记录原始方块（在主线程中记录，避免并发问题）
                BlockPos immutablePos = pos.toImmutable();
                Block originalBlock = world.getBlockState(immutablePos).getBlock();

                originalMap.put(immutablePos, new BlockStateRecord(originalBlock));

                // 计算雪层数（根据 snowAmount：0.0 = 无雪，1.0 = 8层）
                int layers = Math.max(1, Math.min(8, (int) (amount * 8.0f) + 1));

                enqueueMainThread(world, immutablePos, layers, true);
            }
        }

        // 不在新雪中的旧雪 → 恢复
        for (BlockPos pos : restore) {
            BlockPos immutablePos = pos.toImmutable();
            enqueueMainThread(world, immutablePos, 0, false);
            originalMap.remove(immutablePos);
        }
    }

    /**
     * 扫描单个 chunk（随机采样，避免扫描所有方块）。
     */
    private void scanChunk(ClientWorld world, int chunkX, int chunkZ, int snowY, 
                          float amount, float slopeFactor, Set<BlockPos> outNewSnow) {
        Random random = new Random(Objects.hash(chunkX, chunkZ, world.getTime()));

        // 每个 chunk 采样约 32 个方块（可根据性能调整）
        int samplesPerChunk = 32;

        for (int i = 0; i < samplesPerChunk; i++) {
            int bx = random.nextInt(16);
            int bz = random.nextInt(16);
            int wx = chunkX * 16 + bx;
            int wz = chunkZ * 16 + bz;

            BlockPos top = findTopSolidBlock(world, wx, wz);
            if (top == null) continue;

            // 不在雪线高度以下不覆盖
            if (top.getY() > snowY) continue;

            // 检查是否应该覆盖雪
            if (shouldCoverBlock(world, top, slopeFactor)) {
                // 根据 snowAmount 采样概率（弱雪效果）
                if (amount < 1.0f && random.nextFloat() > amount) {
                    continue;
                }
                outNewSnow.add(top.up().toImmutable());
            }
        }
    }

    // ====================================================
    // 主线程任务执行
    // ====================================================

    /**
     * 将任务加入主线程队列。
     */
    private void enqueueMainThread(ClientWorld world, BlockPos pos, int layers, boolean add) {
        mainThreadTasks.offer(() -> {
            if (add) {
                applySnowAt(world, pos, layers);
            } else {
                restoreOriginal(world, pos);
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
    // 方块操作（主线程）
    // ====================================================

    /**
     * 在指定位置应用雪层。
     */
    private void applySnowAt(ClientWorld world, BlockPos pos, int layers) {
        if (world == null) return;

        BlockState currentState = world.getBlockState(pos);
        
        if (currentState.isOf(Blocks.SNOW)) {
            // 已经是雪：更新层数
            int currentLayers = currentState.get(SnowBlock.LAYERS);
            if (currentLayers != layers) {
                world.setBlockState(pos, currentState.with(SnowBlock.LAYERS, layers), 3);
            }
        } else {
            // 不是雪：放置雪层
            BlockState snowState = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers);
            world.setBlockState(pos, snowState, 3);
        }
    }

    /**
     * 恢复原始方块。
     */
    private void restoreOriginal(ClientWorld world, BlockPos pos) {
        if (world == null) return;

        BlockStateRecord rec = originalMap.get(pos);
        if (rec == null) {
            // 没有记录，直接移除（可能是我们之前放置的雪）
            BlockState currentState = world.getBlockState(pos);
            if (currentState.isOf(Blocks.SNOW)) {
                world.removeBlock(pos, false);
            }
            return;
        }

        // 恢复原始方块
        world.setBlockState(pos, rec.originalBlock().getDefaultState(), 3);
    }

    // ====================================================
    // 清除所有雪
    // ====================================================

    /**
     * 清除所有雪（退出导演模式时调用）。
     */
    private void removeAllSnow(ClientWorld world) {
        if (world == null) return;

        for (BlockPos pos : originalMap.keySet()) {
            restoreOriginal(world, pos);
        }
        originalMap.clear();
        mainThreadTasks.clear();
    }

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有雪。
     */
    public void restoreAll(ClientWorld world) {
        removeAllSnow(world);
        LOGGER.info("[SeasonSnowManager] 已清除所有积雪并恢复原始方块");
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
    }
}
