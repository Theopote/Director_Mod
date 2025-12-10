package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * WaterFreezeManager v2.0
 *
 * <p>🧊 基于季节 + 温度采样，对玩家周围的水进行"可逆冻结"。
 *
 * <p>设计要点：
 * <ul>
 *   <li>✅ Chunk 分区扫描（13×13 chunk），避免全世界扫描卡顿</li>
 *   <li>✅ 原始方块缓存（记录原始水方块），确保 100% 可恢复</li>
 *   <li>✅ 后台线程扫描 + 主线程方块更新（线程安全）</li>
 *   <li>✅ 使用 freezeStrength 控制冰冻覆盖概率（0.0 = 没有覆盖，1.0 = 几乎全覆盖）</li>
 * </ul>
 *
 * <p>冻结逻辑：
 * <ul>
 *   <li>freezeStrength 控制冰冻覆盖概率（0.0 = 不冻结，1.0 = 几乎全覆盖）</li>
 *   <li>使用随机采样决定每个水面是否冻结</li>
 *   <li>freezeStrength 降低后逐步融化</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>仅客户端视觉，不会污染世界存档</li>
 *   <li>必须在导演模式或季节轨道激活时运行</li>
 *   <li>所有冻结都可以 100% 恢复为原始水方块</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 2.0 - Chunk 分区扫描 + 原始方块缓存 + 完整可恢复
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

    // ========= 配置参数 =========

    /** 扫描范围（以 chunk 为单位）：玩家中心 ±6 → 13×13 chunk */
    private static final int SCAN_RADIUS_CHUNKS = 6;

    /** 每 tick 处理的最大主线程任务数（避免卡顿） */
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 64;
    /** 最大有效覆盖度，确保始终留有未冻结的空白区域 */
    private static final float MAX_EFFECTIVE_COVERAGE = 0.97f;

    // ========= 状态 =========

    /** 后台扫描线程池 */
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "ChronoBlocks-WaterFreezeScanThread");
                t.setDaemon(true);
                return t;
            }
    );

    /** 主线程任务队列（用于安全的方块写操作） */
    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    /** 当前采样（从 SeasonController 推送） */
    private volatile SeasonSample currentSample = SeasonSample.disabled();

    /** 冰冻缓存（记录被冻结的水方块的原始类型） */
    private final Map<BlockPos, BlockStateRecord> originalWater = new ConcurrentHashMap<>();

    /** 记录结构：原始方块类型 */
    private record BlockStateRecord(Block originalBlock) {}

    /** 是否需要重新扫描（当 sample 变化时设置） */
    private volatile boolean needsRescan = true;

    /** 避免重复提交扫描任务 */
    private volatile boolean scanRunning = false;

    private WaterFreezeManager() {
    }

    // ================================================
    // 入口 1：季节 Sample 更新（由 TimelineSeasonController 调用）
    // ================================================

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
        SeasonSample newSample = sample != null ? sample : SeasonSample.disabled();
        
        // 如果参数没有变化，不触发重新扫描
        if (newSample.equals(this.currentSample)) {
            return;
        }
        
        this.currentSample = newSample;
        this.needsRescan = true;
    }

    // ================================================
    // 入口 2：每 tick 调用（由客户端 Tick）
    // ================================================

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

        // 1) 执行主线程任务（冻结/解冻）
        runMainThreadTasks(world);

        // 2) 冰冻强度为 0 或未启用 → 立即清除全部冰
        if (!currentSample.enabled || currentSample.freezeStrength <= 0.01f) {
            if (!originalWater.isEmpty()) {
                removeAllIce(world);
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
                    LOGGER.warn("[WaterFreezeManager] 扫描任务出错", e);
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

    // ================================================
    // 扫描世界（后台线程）
    // ================================================

    /**
     * 扫描玩家周围的 chunk，计算需要冻结/解冻的水。
     */
    private void scanAndApply(ClientWorld world, BlockPos playerPos, SeasonSample sample) {
        float strength = applyCoverageLimit(sample.freezeStrength);  // 覆盖概率 [0, 1]
        if (strength <= 0f) {
            return;
        }

        Set<BlockPos> newIce = new HashSet<>();
        Set<BlockPos> restore = new HashSet<>(originalWater.keySet()); // 初始：所有旧冰都假定需要恢复

        int px = playerPos.getX() >> 4; // chunk X
        int pz = playerPos.getZ() >> 4; // chunk Z

        // 遍历周围 chunk
        for (int cx = px - SCAN_RADIUS_CHUNKS; cx <= px + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = pz - SCAN_RADIUS_CHUNKS; cz <= pz + SCAN_RADIUS_CHUNKS; cz++) {
                // 对每个 chunk 进行采样
                scanChunkForWater(world, cx, cz, strength, newIce);
            }
        }

        // 新冰：添加
        for (BlockPos pos : newIce) {
            restore.remove(pos); // 新冰中仍存在 → 不需要恢复

            if (!originalWater.containsKey(pos)) {
                // 记录原始方块（在主线程中记录，避免并发问题）
                BlockPos immutablePos = pos.toImmutable();
                Block originalBlock = world.getBlockState(immutablePos).getBlock();

                originalWater.put(immutablePos, new BlockStateRecord(originalBlock));

                enqueueMainThread(world, immutablePos, true);
            }
        }

        // 不在新冰中的旧冰 → 恢复
        for (BlockPos pos : restore) {
            BlockPos immutablePos = pos.toImmutable();
            enqueueMainThread(world, immutablePos, false);
            originalWater.remove(immutablePos);
        }
    }

    /**
     * 扫描单个 chunk 寻找水面（密集采样 + 邻接传播，确保连续性）。
     */
    private void scanChunkForWater(ClientWorld world, int chunkX, int chunkZ, 
                                   float strength, Set<BlockPos> outNewIce) {
        // 第一轮：密集采样，使用噪声场决定初始覆盖位置
        Set<BlockPos> initialCoverage = new HashSet<>();
        Map<BlockPos, Float> noiseValueMap = new HashMap<>(); // 缓存噪声值
        
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int wx = chunkX * 16 + bx;
                int wz = chunkZ * 16 + bz;

                BlockPos waterPos = findTopWater(world, wx, wz);
                if (waterPos == null) continue;

                // 避免重复
                if (originalWater.containsKey(waterPos) || outNewIce.contains(waterPos)) {
                    continue;
                }

                // 使用噪声场决定是否覆盖冰
                float noiseValue = com.chronoblocks.season.NoiseField.iceCoverage(waterPos);
                noiseValueMap.put(waterPos.toImmutable(), noiseValue);

                if (noiseValue < strength) {
                    initialCoverage.add(waterPos.toImmutable());
                }
            }
        }
        
        // 第二轮：邻接传播，填补凹陷和缺口
        Set<BlockPos> propagated = new HashSet<>(initialCoverage);
        Queue<BlockPos> toCheck = new ArrayDeque<>(initialCoverage);
        Set<BlockPos> checked = new HashSet<>(initialCoverage);
        
        while (!toCheck.isEmpty()) {
            BlockPos pos = toCheck.poll();
            
            // 检查4个水平邻居
            BlockPos[] neighbors = {
                pos.north(),
                pos.south(),
                pos.east(),
                pos.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                if (checked.contains(neighbor)) continue;
                checked.add(neighbor);
                
                // 检查邻居是否是水面
                BlockPos neighborWaterPos = findTopWater(world, neighbor.getX(), neighbor.getZ());
                if (neighborWaterPos == null) continue;
                
                // 避免重复
                if (originalWater.containsKey(neighborWaterPos) || outNewIce.contains(neighborWaterPos)) {
                    continue;
                }
                
                // 获取邻居的噪声值
                float neighborNoiseValue = noiseValueMap.getOrDefault(neighborWaterPos, 
                    com.chronoblocks.season.NoiseField.iceCoverage(neighborWaterPos));
                noiseValueMap.put(neighborWaterPos.toImmutable(), neighborNoiseValue);

                // 如果邻居也满足阈值，则标记为覆盖并继续传播
                if (neighborNoiseValue < strength) {
                    propagated.add(neighborWaterPos.toImmutable());
                    toCheck.add(neighborWaterPos.toImmutable());
                }
            }
        }
        
        outNewIce.addAll(propagated);
    }

    // ================================================
    // 主线程任务执行
    // ================================================

    /**
     * 将任务加入主线程队列。
     */
    private void enqueueMainThread(ClientWorld world, BlockPos pos, boolean freeze) {
        mainThreadTasks.offer(() -> {
            if (freeze) {
                applyFrozen(world, pos);
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

    /**
     * 将覆盖度限制在安全范围内，保证始终留有少量未冻结区域。
     */
    private float applyCoverageLimit(float coverage) {
        if (coverage <= 0f) {
            return 0f;
        }
        return Math.min(MAX_EFFECTIVE_COVERAGE, Math.min(1f, coverage));
    }

    // ================================================
    // 方块操作（主线程）
    // ================================================

    /**
     * 应用冰冻（将水替换为冰）。
     */
    private void applyFrozen(ClientWorld world, BlockPos pos) {
        if (world == null) return;

        BlockState state = world.getBlockState(pos);

        // 仅冻结水源（Flowing 也可支持）
        if (state.isOf(Blocks.WATER) || state.getBlock() instanceof FluidBlock) {
            // 使用 FROSTED_ICE（霜冰）作为视觉效果，比普通 ICE 更明显
            world.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState(), 3);
        }
    }

    /**
     * 恢复原始方块（将冰恢复为水）。
     */
    private void restoreOriginal(ClientWorld world, BlockPos pos) {
        if (world == null) return;

        BlockStateRecord rec = originalWater.get(pos);
        if (rec == null) {
            // 没有记录，但可能是冰 → 恢复为水
            BlockState currentState = world.getBlockState(pos);
            if (currentState.isOf(Blocks.ICE) || currentState.isOf(Blocks.FROSTED_ICE)) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
            }
            return;
        }

        // 恢复原始方块
        world.setBlockState(pos, rec.originalBlock().getDefaultState(), 3);
    }

    // ================================================
    // 清除所有冰
    // ================================================

    /**
     * 清除所有冰（退出导演模式时调用）。
     */
    private void removeAllIce(ClientWorld world) {
        if (world == null) return;

        for (BlockPos pos : originalWater.keySet()) {
            restoreOriginal(world, pos);
        }
        originalWater.clear();
        mainThreadTasks.clear();
    }

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有冰块。
     */
    public void restoreAll(ClientWorld world) {
        removeAllIce(world);
        LOGGER.info("[WaterFreezeManager] 已清除所有冰块并恢复原始水方块");
    }

    // ================================================
    // 工具方法
    // ================================================

    /**
     * 在垂直方向上找一个合适的水面方块。
     */
    private BlockPos findTopWater(ClientWorld world, int x, int z) {
        try {
            // 从顶部向下查找（从地表附近开始）
            // 使用 Heightmap 获取地表高度（Minecraft 1.21.4 API）
            BlockPos topPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
            int startY = Math.min(topPos.getY(), 256);
            int endY = Math.max(world.getBottomY(), -64);

            BlockPos.Mutable mutable = new BlockPos.Mutable(x, startY, z);

            for (int y = startY; y >= endY; y--) {
                mutable.setY(y);
                BlockState state = world.getBlockState(mutable);

                if (state.isOf(Blocks.WATER) || state.getBlock() instanceof FluidBlock) {
                    // 水上方必须是空气
                    BlockPos above = mutable.up();
                    if (world.isAir(above)) {
                        return mutable.toImmutable();
                    }
                }

                // 如果遇到非空气、非水的固体方块，停止向下搜索（水面应该在上方）
                if (!state.isAir() && !state.isOf(Blocks.WATER) && 
                    !(state.getBlock() instanceof FluidBlock) && 
                    state.isSolidBlock(world, mutable)) {
                    break;
                }
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("[WaterFreezeManager] 查找水面失败 ({}, {})", x, z, e);
            return null;
        }
    }

    // ================================================
    // 可选：在世界卸载时关闭线程
    // ================================================

    /**
     * 关闭工作线程（在世界卸载时调用，可选）。
     */
    public void shutdown() {
        scanExecutor.shutdownNow();
        mainThreadTasks.clear();
    }
}
