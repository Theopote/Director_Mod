package com.chronoblocks.season;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SeasonSnowManager v3.0
 *
 * <p>❄ 负责根据 SeasonSample 动态生成 / 融化「覆盖雪」（雪层方块），
 *    不改原本地形，只在合适表面上叠一层 snow_layer。
 *
 * <p>设计要点：
 * <ul>
 *   <li>采样：后台线程搜索候选位置（避免主线程卡顿）</li>
 *   <li>应用：主线程每 tick 把少量候选应用为雪层 / 融雪</li>
 *   <li>跟踪：记录我们放过的雪位置，方便融化和清理</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>这里只是客户端视觉效果（ClientWorld），不改服务器地形</li>
 *   <li>如果你希望录制出来的回放「内外一致」，可以考虑把同样逻辑搬一份到服务端</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 3.0 - 多线程采样 + 主线程改方块
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

    /** 单 tick 最多新放多少个雪方块（防止一次性铺太多卡顿） */
    private static final int MAX_SNOW_ADD_PER_TICK = 64;

    /** 单 tick 最多融化多少个雪方块 */
    private static final int MAX_SNOW_MELT_PER_TICK = 64;

    /** 搜索半径（以方块为单位） */
    private static final int SCAN_RADIUS_BLOCKS = 32;

    /** 以当前 sample 的 snowAmount ∈ [0,1] 线性缩放放雪速率 */
    private static final float SNOW_ADD_SPEED_SCALE = 1.0f;

    /** 以(1 - snowAmount)缩放融雪速率；雪量趋近 0 时加快融雪 */
    private static final float SNOW_MELT_SPEED_SCALE = 1.0f;

    /** 每个位置尝试的垂直搜索范围（从地表附近找合适表面） */
    private static final int VERTICAL_SCAN = 24;

    // ========= 状态 =========

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "ChronoBlocks-SeasonSnowWorker");
                t.setDaemon(true);
                return t;
            }
    );

    /** 当前的季节采样（由 TimelineSeasonController 推送） */
    private volatile SeasonSample currentSample = SeasonSample.defaultSample();
    private volatile boolean hasSeasonOverride = false;

    /** 我们自己放置的雪方块（只记录 top block pos） */
    private final Set<BlockPos> placedSnow = Collections.synchronizedSet(new HashSet<>());

    /** 后台线程搜到的候选"放雪"位置（主线程消费） */
    private final ConcurrentLinkedQueue<BlockPos> pendingAdd = new ConcurrentLinkedQueue<>();

    /** 后台线程搜到的候选"融雪"位置（也可以用；当前主要从 placedSnow 抽样） */
    private final ConcurrentLinkedQueue<BlockPos> pendingMelt = new ConcurrentLinkedQueue<>();

    /** 避免重复提交扫描任务 */
    private volatile boolean scanRunning = false;

    private SeasonSnowManager() {
    }

    // ====================================================
    // 入口 1：季节采样更新（由 TimelineSeasonController 调用）
    // ====================================================

    /**
     * 接收季节采样更新（v3.0 新 API）。
     * 
     * <p>由 TimelineSeasonController.propagateToWorld() 调用，不再自己获取 sample。
     * 这样可以确保雪系统 100% 由季节轨道驱动，不会产生不一致。
     * 
     * @param sample 季节采样，如果为 null 表示没有季节覆盖
     */
    public void onSeasonSampleUpdated(SeasonSample sample) {
        if (sample == null) {
            hasSeasonOverride = false;
            currentSample = SeasonSample.defaultSample();
        } else {
            hasSeasonOverride = true;
            currentSample = sample;
        }
    }

    // ====================================================
    // 入口 2：每帧 / 每 tick 在 Client 侧调用
    // 典型调用位置：ClientTickHandler 或 ChronoBlocksClientController.clientTick()
    // ====================================================

    /**
     * 每 tick 调用（v3.0 新 API，接受 cameraPos 参数）。
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

        // 先处理主线程上的改方块逻辑
        applyPendingSnow(world);
        applyAutoMelt(world);

        // 再启动异步扫描
        scheduleScanIfNeeded(world, cameraPos);
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
    // 主线程：应用候选雪 / 融雪
    // ====================================================

    private void applyPendingSnow(ClientWorld world) {
        SeasonSample sample = currentSample;

        // 没有季节覆盖，或者目标雪量接近 0：不放新雪
        if (!hasSeasonOverride || sample == null || sample.snowAmount <= 0.01f) {
            return;
        }

        int maxAdd = Math.max(1,
                (int) (MAX_SNOW_ADD_PER_TICK * sample.snowAmount * SNOW_ADD_SPEED_SCALE));

        int added = 0;
        BlockPos pos;
        while (added < maxAdd && (pos = pendingAdd.poll()) != null) {
            if (placeOrIncreaseSnowLayer(world, pos)) {
                placedSnow.add(pos.toImmutable());
                added++;
            }
        }
    }

    private void applyAutoMelt(ClientWorld world) {
        SeasonSample sample = currentSample;

        // 没有季节覆盖：加速清空我们放的所有雪
        if (!hasSeasonOverride || sample == null) {
            meltSomeSnow(world, MAX_SNOW_MELT_PER_TICK * 2);
            return;
        }

        // snowAmount 越小，融雪越快
        float factor = (1.0f - sample.snowAmount) * SNOW_MELT_SPEED_SCALE;
        if (factor <= 0.01f) {
            // 雪量接近1：保持不融（除非别的系统清除）
            return;
        }

        int maxMelt = Math.max(1, (int) (MAX_SNOW_MELT_PER_TICK * factor));
        meltSomeSnow(world, maxMelt);
    }

    private void meltSomeSnow(ClientWorld world, int count) {
        if (placedSnow.isEmpty()) {
            return;
        }

        // 简单抽样：从迭代器取前 N 个，或随机取一点
        Iterator<BlockPos> it = placedSnow.iterator();
        int melted = 0;

        while (it.hasNext() && melted < count) {
            BlockPos pos = it.next();
            if (meltSnowLayer(world, pos)) {
                it.remove();
                melted++;
            } else {
                // 方块已不是我们放的雪，直接移除记录
                it.remove();
            }
        }
    }

    // ====================================================
    // 异步扫描：找候选放雪位置
    // ====================================================

    private void scheduleScanIfNeeded(ClientWorld world, Vec3d cameraPos) {
        if (!hasSeasonOverride || currentSample == null) {
            return;
        }
        if (scanRunning) {
            return;
        }

        scanRunning = true;
        BlockPos camPos = BlockPos.ofFloored(cameraPos);
        SeasonSample sampleSnapshot = currentSample;

        worker.submit(() -> {
            try {
                scanAroundCamera(world, camPos, sampleSnapshot);
            } catch (Exception e) {
                LOGGER.warn("[SeasonSnowManager] 扫描任务出错", e);
            } finally {
                scanRunning = false;
            }
        });
    }

    private void scanAroundCamera(ClientWorld world, BlockPos camPos, SeasonSample sample) {
        // 雪线高度以下才考虑放雪
        int snowline = (int) sample.snowlineHeight;
        if (sample.snowAmount <= 0.01f) {
            return;
        }

        Random random = new Random(
                Objects.hash(camPos.getX(), camPos.getZ(), world.getTime()));

        int radius = SCAN_RADIUS_BLOCKS;
        int tries = radius * radius / 2; // 简单控制扫描密度

        for (int i = 0; i < tries; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            int x = camPos.getX() + dx;
            int z = camPos.getZ() + dz;

            BlockPos surface = findSurface(world, x, z, camPos.getY());

            if (surface == null) {
                continue;
            }
            if (surface.getY() > snowline) {
                continue;
            }

            BlockPos topPos = surface.up();

            // 避免重复
            if (placedSnow.contains(topPos) || pendingAdd.contains(topPos)) {
                continue;
            }

            pendingAdd.add(topPos.toImmutable());
        }
    }

    /**
     * 在垂直方向上找一个合适的"地表"方块：
     * - 上方是空气
     * - 自身是 solid / 可被雪覆盖的方块
     */
    private BlockPos findSurface(ClientWorld world, int x, int z, double centerY) {
        int startY = (int) centerY + VERTICAL_SCAN / 2;
        int endY = (int) centerY - VERTICAL_SCAN / 2;

        // Minecraft 1.21.4: 使用 getTopPosition() 获取地表高度
        BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        int topY = Math.min(topPos.getY() - 1, startY);
        int bottomY = Math.max(world.getBottomY(), endY);

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = topY; y >= bottomY; y--) {
            mutable.set(x, y, z);
            BlockState state = world.getBlockState(mutable);
            BlockState above = world.getBlockState(mutable.up());

            // 上方必须是空气或可被雪占据
            if (!above.isAir() && !above.isOf(Blocks.SNOW)) {
                continue;
            }

            // 当前方块要能承载雪
            if (state.isAir()) {
                continue;
            }
            // 使用 Minecraft 1.21.4 的 API：isSolidBlock(world, pos)
            if (!state.isSolidBlock(world, mutable) && !state.isOf(Blocks.SNOW)) {
                continue;
            }

            return mutable.toImmutable();
        }
        return null;
    }

    // ====================================================
    // 具体放雪 / 融雪逻辑（主线程调用）
    // ====================================================

    /** 放一个 snow_layer 或增加层数；成功返回 true */
    private boolean placeOrIncreaseSnowLayer(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isOf(Blocks.SNOW)) {
            // 叠加层数
            int layers = state.get(SnowBlock.LAYERS);
            if (layers < 8) {
                world.setBlockState(pos, state.with(SnowBlock.LAYERS, layers + 1), 3);
                return true;
            } else {
                return false;
            }
        }

        if (!world.getBlockState(pos.down()).isAir()) {
            // 下方必须有支撑（简化判断，可根据需要放宽）
            world.setBlockState(pos, Blocks.SNOW.getDefaultState(), 3);
            return true;
        }

        return false;
    }

    /** 融化一层雪，如果完全融化则删掉方块 */
    private boolean meltSnowLayer(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.SNOW)) {
            // 已经不是雪了
            return false;
        }

        int layers = state.get(SnowBlock.LAYERS);
        if (layers > 1) {
            world.setBlockState(pos, state.with(SnowBlock.LAYERS, layers - 1), 3);
        } else {
            world.removeBlock(pos, false);
        }
        return true;
    }

    // ====================================================
    // 清理接口：导演模式退出 / UI 点"重置季节效果"时调用
    // ====================================================

    /**
     * 退出导演模式 / 禁用季节系统时，完全恢复我们修改的所有雪。
     */
    public void restoreAll(ClientWorld world) {
        if (world == null) {
            return;
        }

        synchronized (placedSnow) {
            for (BlockPos pos : placedSnow) {
                BlockState state = world.getBlockState(pos);
                if (state.isOf(Blocks.SNOW)) {
                    world.removeBlock(pos, false);
                }
            }
            placedSnow.clear();
        }

        pendingAdd.clear();
        pendingMelt.clear();
    }

    // ====================================================
    // 可选：在世界卸载时关闭线程（如果需要）
    // ====================================================

    /**
     * 关闭工作线程（在世界卸载时调用，可选）。
     */
    public void shutdown() {
        worker.shutdownNow();
    }
}
