package com.director.core.container.visibility;

import com.director.core.container.BlockStore;
import com.director.core.container.BlockEntityNbtIO;
import com.director.core.container.Container;
import com.director.timeline.playback.block.TimelinePlacementBudget;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.*;

/**
 * 容器可见性服务（v5.3）
 *
 * <p>负责管理容器的隐藏/显示，通过真正将世界中的方块替换为AIR来实现完全隐藏。
 *
 * <p>核心原理：
 * <ul>
 *   <li>隐藏时：保存方块快照，然后将世界中的方块设置为AIR</li>
 *   <li>显示时：从快照恢复方块状态和方块实体数据</li>
 * </ul>
 *
 * <p>时间线控制路径（showContainerBlocksForTimeline）：
 * <ul>
 *   <li>完全依赖 tick() 里的分帧预算追平，不在 enqueue 时做任何同步 setBlockState</li>
 *   <li>backfillSnapshotFromBlockStore 幂等保护：快照首次建立后不重复全量扫描</li>
 *   <li>pauseTimelineDriving / resumeTimelineDriving 由 TimelineClipBlockController 驱动</li>
 * </ul>
 *
 * @author Director Team
 * @version 5.3
 */
public class ContainerVisibilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerVisibilityService.class);

    private final MinecraftServer server;

    // 每个容器对应一个快照（隐藏时使用，显示时恢复）
    private final Map<UUID, BlockStore> snapshots = new HashMap<>();

    /**
     * 上一帧 showContainerBlocksForTimeline() 已落地的方块集合（posLong）。
     *
     * <p>delta 追平：只对新增显示与新增隐藏的少量方块做 setBlockState。
     */
    private final Map<UUID, LongSet> lastShownBlocks = new HashMap<>();

    /** 时间线播放/拖动播放头时期望落地的方块目标集（posLong），由服务端分帧追平。 */
    private final Map<UUID, LongSet> timelineVisibilityTargets = new HashMap<>();

    /**
     * 已完成 backfill 的容器 ID 集合。
     *
     * <p>backfillSnapshotFromBlockStore 是 O(N) 遍历，只需在快照首次建立时执行一次。
     * 当快照被 clearAll 清除时，同步从这里移除。
     * 注意：cancelPendingTasks 不清除此标志，因为快照本身仍然有效。
     */
    private final Set<UUID> backfilledContainers = new HashSet<>();

    /**
     * 时间线分帧追平暂停标志。
     *
     * <p>volatile 确保服务端线程（tick）和客户端线程（update）之间的可见性。
     * 初始为 true：没有任何时间线控制时，tick() 不做无用工作。
     */
    private volatile boolean timelineDrivingPaused = true;

    private final Deque<VisibilityTask> pendingTasks = new ArrayDeque<>();

    public ContainerVisibilityService(MinecraftServer server) {
        this.server = server;
        LOGGER.debug("[ContainerVisibilityService] 初始化");
    }

    // =========================================================================
    // 服务端 tick：分帧追平时间线可见性目标
    // =========================================================================

    public void tick() {
        int budget = getBlocksPerTickBudget();

        // 1) 优先处理 pendingTasks（hideContainer/showContainer 等整体操作）
        while (budget > 0 && !pendingTasks.isEmpty()) {
            VisibilityTask task = pendingTasks.peekFirst();
            int consumed = task.process(budget);
            budget -= Math.max(1, consumed);
            ContainerVisibilityFeedback.onTaskProgress(
                    task.containerName, task.actionLabel, task.remaining());
            if (task.isComplete()) {
                pendingTasks.removeFirst();
                task.finish();
            }
        }

        // 2) 时间线分帧追平：仅在 resume 状态下才处理，避免静止预览时浪费服务端 tick
        if (!timelineDrivingPaused && budget > 0 && !timelineVisibilityTargets.isEmpty()) {
            // 快照一份，避免在迭代期间 map 被 enqueueTimelineVisibilityTarget 修改
            List<Map.Entry<UUID, LongSet>> pendingTimeline = new ArrayList<>(timelineVisibilityTargets.entrySet());
            for (Map.Entry<UUID, LongSet> entry : pendingTimeline) {
                if (budget <= 0) {
                    break;
                }
                UUID containerId = entry.getKey();
                Container container = com.director.core.DirectorServices.get().containers().getContainer(containerId);
                if (container == null) {
                    timelineVisibilityTargets.remove(containerId);
                    continue;
                }
                int consumed = applyTimelineVisibilityForContainer(container, entry.getValue(), budget);
                budget -= Math.max(1, consumed);
                if (isTimelineTargetSatisfied(containerId, entry.getValue(), resolveWorld(container))) {
                    timelineVisibilityTargets.remove(containerId);
                }
            }
        }
    }

    // =========================================================================
    // 线程调度
    // =========================================================================

    private void runOnServer(Runnable action) {
        if (action == null) {
            return;
        }
        if (server.isOnThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    private ServerWorld resolveWorld(Container container) {
        RegistryKey<World> key = container.getLevelKey();
        if (key == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界", container.getName());
            return null;
        }
        return server.getWorld(key);
    }

    // =========================================================================
    // 容器面板操作：hideContainer / showContainer（整体操作，走 pendingTasks）
    // =========================================================================

    public void hideContainer(Container container) {
        if (container == null) {
            return;
        }
        runOnServer(() -> hideContainerOnServer(container));
    }

    private void hideContainerOnServer(Container container) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法隐藏", container.getName());
            return;
        }

        BlockStore existingStore = snapshots.get(container.getId());
        BlockStore store;

        if (existingStore != null) {
            store = existingStore;
            backfillSnapshotIfNeeded(container, store);
            java.util.List<BlockPos> toHide = new java.util.ArrayList<>(container.getBlocksView());
            enqueueOrRun(createHideTask(container, world, store, toHide));
        } else {
            store = new BlockStore();
            backfillSnapshotFromBlockStore(container, store);
            snapshots.put(container.getId(), store);
            backfilledContainers.add(container.getId());
            lastShownBlocks.remove(container.getId());
            enqueueOrRun(createHideTask(container, world, store,
                    new java.util.ArrayList<>(container.getBlocksView())));
            LOGGER.debug("[Visibility] 容器 {} 隐藏任务已入队，快照 {} 个方块",
                    container.getName(), store.size());
        }
    }

    public void showContainer(Container container) {
        if (container == null) {
            return;
        }
        runOnServer(() -> showContainerOnServer(container));
    }

    private void showContainerOnServer(Container container) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法恢复", container.getName());
            return;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null) {
            if (container.isHidden() && createSnapshotFromBlockStore(container)) {
                store = snapshots.get(container.getId());
            }
            if (store == null) {
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过", container.getName());
                return;
            }
        }
        backfillSnapshotIfNeeded(container, store);

        LOGGER.debug("[Visibility] 显示容器 {} (恢复 {} 个方块)", container.getName(), store.size());
        enqueueOrRun(createShowTask(container, world, store, new java.util.ArrayList<>(store.positions())));
    }

    public void restoreContainerBlocks(Container container, Collection<BlockPos> blocks) {
        if (container == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        runOnServer(() -> restoreContainerBlocksOnServer(container, blocks));
    }

    private void restoreContainerBlocksOnServer(Container container, Collection<BlockPos> blocks) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            return;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null && createSnapshotFromBlockStore(container)) {
            store = snapshots.get(container.getId());
        }
        if (store == null) {
            return;
        }
        backfillSnapshotIfNeeded(container, store);
        enqueueOrRun(createRestoreTask(container, world, store, new java.util.ArrayList<>(blocks)));
    }

    // =========================================================================
    // 时间线控制路径（核心修复区域）
    // =========================================================================

    /**
     * 时间线专用：设置期望落地的方块目标集，由服务端分帧追平。
     *
     * <p>【性能关键】此方法不做任何同步 setBlockState。
     * 所有实际方块操作均在 {@link #tick()} 的分帧预算中完成，不阻塞服务端 tick。
     */
    public void showContainerBlocksForTimeline(Container container, Set<BlockPos> blocksToShow) {
        if (container == null) {
            return;
        }
        final Set<BlockPos> target = blocksToShow != null ? blocksToShow : Collections.emptySet();
        runOnServer(() -> enqueueTimelineVisibilityTarget(container, target));
    }

    /**
     * 容器拖入时间线：建立快照并记录当前世界可见集。
     *
     * <p>【修复】不再调用 cancelPendingTasks，避免清除正在分帧处理的任务。
     * 不调用 timelineVisibilityTargets.remove，避免打断 tick() 的追平进度。
     * 快照已存在时直接跳过，不重复扫描世界。
     */
    public void prepareContainerForTimeline(Container container) {
        if (container == null) {
            return;
        }
        runOnServer(() -> prepareContainerForTimelineOnServer(container));
    }

    private void prepareContainerForTimelineOnServer(Container container) {
        UUID containerId = container.getId();

        // ---- 1. 建立快照（幂等：已有则跳过）----
        if (!snapshots.containsKey(containerId)) {
            // 优先从持久化 blockStore 快速构建，避免扫描世界
            if (!createSnapshotFromBlockStore(container)) {
                ServerWorld world = resolveWorld(container);
                if (world == null) {
                    LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法为时间线准备快照", container.getName());
                    return;
                }
                BlockStore store = new BlockStore();
                backfillSnapshotFromBlockStore(container, store);
                if (store.isEmpty()) {
                    // blockStore 也为空，只能从世界捕获（代价高，但属于边界情况）
                    for (BlockPos pos : container.getBlocksView()) {
                        store.capture(world, pos);
                    }
                }
                snapshots.put(containerId, store);
                LOGGER.debug("[Visibility] 容器 {} 已从世界捕获时间线快照 ({} 个方块)",
                        container.getName(), store.size());
            }
            backfilledContainers.add(containerId);
        }

        // ---- 2. 记录当前世界可见集（仅首次，避免重复扫描）----
        // 【修复】只在 lastShownBlocks 没有记录时才扫描，防止每次拖入都 O(N) 扫世界
        if (!lastShownBlocks.containsKey(containerId)) {
            ServerWorld world = resolveWorld(container);
            LongOpenHashSet currentlyShown = new LongOpenHashSet(Math.max(16, container.getBlockCount()));
            if (world != null) {
                for (BlockPos pos : container.getBlocksView()) {
                    if (!world.getBlockState(pos).isAir()) {
                        currentlyShown.add(pos.asLong());
                    }
                }
            }
            lastShownBlocks.put(containerId, currentlyShown);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Visibility] 容器 {} 已准备时间线控制 (快照方块: {}, 世界当前可见: {})",
                        container.getName(),
                        snapshots.containsKey(containerId) ? snapshots.get(containerId).size() : 0,
                        currentlyShown.size());
            }
        }

        // ---- 3. 初始状态：paused=true，等待 TimelineClipBlockController 首次 resume ----
        // 【修复】不再无条件设 true，避免覆盖 enqueueTimelineVisibilityTarget 设置的 false。
        // prepareContainerForTimeline 只在容器"刚被拖入"那一次执行，
        // 而 enqueueTimelineVisibilityTarget 是在同一服务端队列里后续执行的，
        // 两者顺序确定：prepare 先，enqueue 后，所以这里设 true 是安全的。
        timelineDrivingPaused = true;
    }

    /** 预览静止：暂停分帧追平（仅设标志）。 */
    public void pauseTimelineDriving() {
        timelineDrivingPaused = true;
    }

    /** 恢复分帧追平。 */
    public void resumeTimelineDriving() {
        timelineDrivingPaused = false;
    }

    /**
     * 写入时间线可见性目标，等待 tick() 分帧追平。
     *
     * <p>【性能关键修复】：
     * <ol>
     *   <li>backfill 幂等保护：快照完整后不再重复 O(N) 扫描</li>
     *   <li>不做同步 kick：原 kickBudget 每次同步执行 1000 个 setBlockState + doLightUpdates，
     *       对大容器每帧阻塞服务端 30~100ms，直接导致 TPS/FPS 双降</li>
     *   <li>目标未变时 early-return：避免重复写入 map</li>
     * </ol>
     */
    private void enqueueTimelineVisibilityTarget(Container container, Set<BlockPos> blocksToShow) {
        // resume：让 tick() 开始处理
        timelineDrivingPaused = false;

        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法设置时间线可见性目标", container.getName());
            return;
        }

        UUID containerId = container.getId();

        BlockStore store = snapshots.get(containerId);
        if (store == null) {
            if (createSnapshotFromBlockStore(container)) {
                store = snapshots.get(containerId);
            }
            if (store == null) {
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过时间线可见性", container.getName());
                return;
            }
        }

        // 【修复 Bug1】backfill 幂等：快照已完整时跳过 O(N) 扫描
        if (!backfilledContainers.contains(containerId)) {
            backfillSnapshotFromBlockStore(container, store);
            backfilledContainers.add(containerId);
        }

        // 构建目标 LongSet
        LongOpenHashSet targetLongs = new LongOpenHashSet(Math.max(16, blocksToShow.size() * 2));
        for (BlockPos pos : blocksToShow) {
            if (pos != null) {
                targetLongs.add(pos.asLong());
            }
        }

        // 已满足：直接移除目标，下次 tick 不做任何工作
        if (isTimelineTargetSatisfied(containerId, targetLongs, world)) {
            timelineVisibilityTargets.remove(containerId);
            return;
        }

        // 目标与上次相同：tick() 已在追平，无需重复写入
        LongSet previousTarget = timelineVisibilityTargets.get(containerId);
        if (previousTarget != null && longSetEquals(previousTarget, targetLongs)) {
            return;
        }

        // 【修复 Bug2】只写入目标，不做任何同步 setBlockState
        // 所有实际方块操作由 tick() 按预算分帧执行
        timelineVisibilityTargets.put(containerId, targetLongs);
        lastShownBlocks.computeIfAbsent(containerId, id -> new LongOpenHashSet());
    }

    // =========================================================================
    // showContainerBlocks（容器面板动画路径，非时间线）
    // =========================================================================

    /**
     * 显示容器的部分方块（容器面板动画效果使用，非时间线路径）。
     *
     * <p>此方法在服务端主线程同步执行 delta setBlockState，
     * 适用于容器面板的实时交互（方块数量少、调用频率低）。
     * 时间线路径请使用 {@link #showContainerBlocksForTimeline}。
     */
    public void showContainerBlocks(Container container, java.util.Set<net.minecraft.util.math.BlockPos> blocksToShow) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法显示方块", container.getName());
            return;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null) {
            if (createSnapshotFromBlockStore(container)) {
                store = snapshots.get(container.getId());
            }
            if (store == null) {
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过", container.getName());
                return;
            }
        }
        backfillSnapshotIfNeeded(container, store);

        if (blocksToShow == null) {
            blocksToShow = java.util.Collections.emptySet();
        }

        UUID containerId = container.getId();
        LongSet last = lastShownBlocks.get(containerId);
        boolean firstCall = (last == null);
        if (firstCall) {
            last = new LongOpenHashSet();
            lastShownBlocks.put(containerId, last);
        }

        LongOpenHashSet now = new LongOpenHashSet(Math.max(16, blocksToShow.size() * 2));
        for (BlockPos p : blocksToShow) {
            if (p != null) {
                now.add(p.asLong());
            }
        }

        if (firstCall) {
            // 首次：入队分帧任务，避免大容器卡死服务端 tick
            java.util.ArrayList<BlockPos> toHide = new java.util.ArrayList<>();
            for (BlockPos pos : store.positions()) {
                if (pos == null) continue;
                if (!now.contains(pos.asLong())) {
                    toHide.add(pos);
                }
            }
            java.util.ArrayList<BlockPos> toShow = new java.util.ArrayList<>();
            for (var it = now.iterator(); it.hasNext(); ) {
                long posLong = it.nextLong();
                BlockPos pos = BlockPos.fromLong(posLong);
                if (store.get(pos) != null) {
                    toShow.add(pos);
                }
            }
            if (!toHide.isEmpty()) {
                enqueueOrRun(createHideTask(container, world, store, toHide));
            }
            if (!toShow.isEmpty()) {
                enqueueOrRun(createShowTask(container, world, store, toShow));
            }
            last.clear();
            last.addAll(now);
            return;
        }

        // delta 更新
        LongOpenHashSet affectedChunks = new LongOpenHashSet();
        int hiddenCount = 0;
        for (var it = last.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (now.contains(posLong)) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(posLong);
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
            net.minecraft.block.BlockState cur = world.getBlockState(pos);
            if (!cur.isAir()) {
                if (!store.contains(pos)) store.capture(world, pos);
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                if (be != null) world.removeBlockEntity(pos);
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
                trackAffectedChunk(affectedChunks, cx, cz);
                hiddenCount++;
            }
        }
        int shownCount = 0;
        for (var it = now.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (last.contains(posLong)) continue;
            BlockPos pos = BlockPos.fromLong(posLong);
            BlockStore.Entry entry = store.get(pos);
            if (entry == null) continue;
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
            if (world.getBlockState(pos).isAir()) {
                restoreBlockEntry(world, pos, entry, affectedChunks);
                shownCount++;
            }
        }
        if (hiddenCount > 0 || shownCount > 0) {
            flushLightingForChunks(world, affectedChunks);
        }
        last.clear();
        last.addAll(now);
    }

    // =========================================================================
    // 内部：快照管理
    // =========================================================================

    public boolean hasSnapshot(Container container) {
        return container != null && snapshots.containsKey(container.getId());
    }

    /**
     * 从容器持久化 blockStore 快速创建运行时快照（不扫描世界）。
     *
     * @return 是否成功（blockStore 非空时为 true）
     */
    public boolean createSnapshotFromBlockStore(Container container) {
        if (container == null) return false;
        if (snapshots.containsKey(container.getId())) return true;

        com.director.core.container.BlockStore src = container.getBlockStore();
        if (src == null || src.isEmpty()) return false;

        BlockStore snapshot = new BlockStore();
        for (Map.Entry<net.minecraft.util.math.BlockPos, com.director.core.container.BlockStore.Entry> e
                : src.raw().entrySet()) {
            snapshot.add(e.getKey(), new BlockStore.Entry(e.getValue().state, e.getValue().beNbt));
        }
        snapshots.put(container.getId(), snapshot);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 已从 blockStore 创建快照 (方块数: {})",
                    container.getName(), snapshot.size());
        }
        return true;
    }

    /**
     * 幂等 backfill：仅在 backfilledContainers 中没有记录时才做全量扫描，之后跳过。
     */
    private void backfillSnapshotIfNeeded(Container container, BlockStore snapshot) {
        UUID id = container.getId();
        if (!backfilledContainers.contains(id)) {
            backfillSnapshotFromBlockStore(container, snapshot);
            backfilledContainers.add(id);
        }
    }

    /**
     * 用容器持久化 blockStore 补齐运行时快照中缺失的方块条目（O(N) 操作，请通过幂等门控调用）。
     */
    private void backfillSnapshotFromBlockStore(Container container, BlockStore snapshot) {
        if (container == null || snapshot == null) return;
        com.director.core.container.BlockStore source = container.getBlockStore();
        if (source == null || source.isEmpty()) return;

        int added = 0;
        for (Map.Entry<BlockPos, BlockStore.Entry> entry : source.raw().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockStore.Entry value = entry.getValue();
            if (pos == null || value == null || snapshot.contains(pos)) continue;
            snapshot.add(pos, new BlockStore.Entry(value.state, value.beNbt));
            added++;
        }
        if (added > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 快照已从 blockStore 补齐 {} 个方块 (总数: {})",
                    container.getName(), added, snapshot.size());
        }
    }

    public boolean isHidden(UUID containerId) {
        return snapshots.containsKey(containerId);
    }

    public void clearAll() {
        pendingTasks.clear();
        snapshots.clear();
        lastShownBlocks.clear();
        timelineVisibilityTargets.clear();
        backfilledContainers.clear();
        ContainerVisibilityFeedback.reset();
        LOGGER.debug("[ContainerVisibilityService] 清除所有快照");
    }

    // =========================================================================
    // 内部：时间线 apply 逻辑（tick() 调用）
    // =========================================================================

    private int applyTimelineVisibilityForContainer(Container container, LongSet target, int budget) {
        if (budget <= 0 || container == null || target == null) return 0;

        BlockStore store = snapshots.get(container.getId());
        if (store == null) return 0;

        ServerWorld world = resolveWorld(container);
        if (world == null) return 0;

        LongSet last = lastShownBlocks.computeIfAbsent(container.getId(), id -> new LongOpenHashSet());
        LongOpenHashSet affectedChunks = new LongOpenHashSet();
        int processed = 0;
        boolean preferShows = TimelinePlacementBudget.isPlacementAccelerated();

        if (preferShows) {
            processed += applyTimelineShows(world, store, last, target, affectedChunks, budget);
            processed += applyTimelineHides(world, store, last, target, affectedChunks, budget - processed);
        } else {
            processed += applyTimelineHides(world, store, last, target, affectedChunks, budget);
            processed += applyTimelineShows(world, store, last, target, affectedChunks, budget - processed);
        }

        // 光照刷新：仅在本 tick 有实际方块变更时才调用（doLightUpdates 有一定开销）
        if (processed > 0) {
            flushLightingForChunks(world, affectedChunks);
        }
        return processed;
    }

    private int applyTimelineHides(ServerWorld world, BlockStore store, LongSet last,
                                   LongSet target, LongOpenHashSet affectedChunks, int budget) {
        if (budget <= 0 || last.isEmpty()) return 0;
        int processed = 0;
        List<Long> toHide = new ArrayList<>();
        for (LongIterator it = last.iterator(); it.hasNext(); ) {
            long l = it.nextLong();
            if (!target.contains(l)) toHide.add(l);
        }
        for (long posLong : toHide) {
            if (processed >= budget) break;
            BlockPos pos = BlockPos.fromLong(posLong);
            if (timelineHideAt(world, store, pos, affectedChunks)) {
                last.remove(posLong);
                processed++;
            }
        }
        return processed;
    }

    private int applyTimelineShows(ServerWorld world, BlockStore store, LongSet last,
                                   LongSet target, LongOpenHashSet affectedChunks, int budget) {
        if (budget <= 0 || target.isEmpty()) return 0;
        int processed = 0;
        for (LongIterator it = target.iterator(); it.hasNext(); ) {
            if (processed >= budget) break;
            long posLong = it.nextLong();
            if (last.contains(posLong)) continue;
            BlockPos pos = BlockPos.fromLong(posLong);
            if (timelineShowAt(world, store, pos, affectedChunks)) {
                last.add(posLong);
                processed++;
            }
        }
        return processed;
    }

    private boolean timelineHideAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        if (world.getBlockState(pos).isAir()) return false;
        if (!store.contains(pos)) store.capture(world, pos);
        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (be != null) world.removeBlockEntity(pos);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
        trackAffectedChunk(affectedChunks, cx, cz);
        return true;
    }

    private boolean timelineShowAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        BlockStore.Entry entry = store.get(pos);
        if (entry == null) return false;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        if (!world.getBlockState(pos).isAir()) return false;
        restoreBlockEntry(world, pos, entry, affectedChunks);
        return true;
    }

    private boolean isTimelineTargetSatisfied(UUID containerId, LongSet target, ServerWorld world) {
        LongSet last = lastShownBlocks.get(containerId);
        if (target == null || target.isEmpty()) {
            return last == null || last.isEmpty();
        }
        if (last == null) last = new LongOpenHashSet();

        // ⭐ 修复：检查目标方块是否全部已显示
        for (LongIterator it = target.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (last.contains(posLong)) continue;  // 已显示

            // ⚠️ 关键修复：如果方块所在区块未加载，返回 false（未满足）
            // 之前的逻辑是 continue（跳过），导致在区块未加载时误判为"已满足"
            if (world != null) {
                BlockPos pos = BlockPos.fromLong(posLong);
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    return false;  // 区块未加载，无法确认是否满足，保持目标继续追平
                }
            }
            return false;  // 有方块未显示
        }

        // ⭐ 修复：检查是否有多余的已显示方块需要隐藏
        for (LongIterator it = last.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (target.contains(posLong)) continue;  // 仍需显示

            // ⚠️ 关键修复：如果方块所在区块未加载，返回 false（未满足）
            if (world != null) {
                BlockPos pos = BlockPos.fromLong(posLong);
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    return false;  // 区块未加载，无法确认是否满足，保持目标继续追平
                }
            }
            return false;  // 有多余方块需要隐藏
        }
        return true;
    }

    private static boolean longSetEquals(LongSet a, LongSet b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (LongIterator it = a.iterator(); it.hasNext(); ) {
            if (!b.contains(it.nextLong())) return false;
        }
        return true;
    }

    // =========================================================================
    // 内部：pendingTasks 管理
    // =========================================================================

    private void enqueueOrRun(VisibilityTask task) {
        if (task == null || task.isComplete()) return;
        cancelPendingTasks(task.containerId);
        int blockCount = task.remaining();
        ContainerVisibilityFeedback.onTaskQueued(task.containerName, task.actionLabel, blockCount);
        pendingTasks.addLast(task);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 的 {} 操作已入队: {} 个方块",
                    task.containerName, task.actionLabel, blockCount);
        }
    }

    /**
     * 取消该容器所有待处理的整体操作任务（hideContainer/showContainer 级别）。
     *
     * <p>【修复】不再清除 backfilledContainers，快照内容未变，无需重复 backfill。
     * 也不清除 timelineVisibilityTargets，避免中断 tick() 的分帧追平。
     */
    private void cancelPendingTasks(UUID containerId) {
        if (containerId == null || pendingTasks.isEmpty()) return;
        boolean removed = pendingTasks.removeIf(task -> containerId.equals(task.containerId));
        if (removed) {
            ContainerVisibilityFeedback.reset();
        }
    }

    private VisibilityTask createHideTask(Container c, ServerWorld w, BlockStore s, List<BlockPos> pos) {
        return new VisibilityTask(c, w, s, pos, TaskKind.HIDE, "隐藏");
    }

    private VisibilityTask createShowTask(Container c, ServerWorld w, BlockStore s, List<BlockPos> pos) {
        return new VisibilityTask(c, w, s, pos, TaskKind.SHOW, "显示");
    }

    private VisibilityTask createRestoreTask(Container c, ServerWorld w, BlockStore s, List<BlockPos> pos) {
        return new VisibilityTask(c, w, s, pos, TaskKind.RESTORE, "释放");
    }

    // =========================================================================
    // 内部：方块操作工具
    // =========================================================================

    private int getBlocksPerTickBudget() {
        int base;
        try {
            base = Math.max(1, com.director.config.DirectorConfig.getConfig().performance.containerWorldBlocksPerTick);
        } catch (Exception ignored) {
            base = 3000;
        }
        return Math.max(1, base * TimelinePlacementBudget.getVisibilityTickMultiplier());
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private static void trackAffectedChunk(LongOpenHashSet set, int cx, int cz) {
        if (set != null) set.add(chunkKey(cx, cz));
    }

    private void flushLightingForChunks(ServerWorld world, LongOpenHashSet affectedChunks) {
        if (world == null || affectedChunks == null || affectedChunks.isEmpty()) return;
        try {
            world.getChunkManager().getLightingProvider().doLightUpdates();
        } catch (Exception e) {
            LOGGER.debug("[Visibility] 光照刷新跳过: {}", e.getMessage());
        }
    }

    private void restoreBlockEntry(ServerWorld world, BlockPos pos, BlockStore.Entry entry,
                                   LongOpenHashSet affectedChunks) {
        if (world == null || pos == null || entry == null || entry.state == null) return;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        try {
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) world.getChunk(cx, cz);
        } catch (Exception ignored) {}
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) return;

        world.setBlockState(pos, entry.state, ContainerBlockUpdateFlags.BATCH);
        trackAffectedChunk(affectedChunks, cx, cz);

        if (entry.beNbt == null) return;
        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;
        BlockEntityNbtIO.restore(be, entry.beNbt, world.getRegistryManager());
    }

    // =========================================================================
    // VisibilityTask
    // =========================================================================

    private final class VisibilityTask {
        final UUID containerId;
        final String containerName;
        final String actionLabel;
        private final ServerWorld world;
        private final BlockStore store;
        private final BlockStore fallbackStore;
        private final List<BlockPos> positions;
        private final TaskKind kind;
        private final LongSet shown;
        private final Map<Long, Boolean> chunkLoadCache = new HashMap<>();
        private final LongOpenHashSet affectedChunks = new LongOpenHashSet();
        private int index = 0;
        private int changed = 0;

        VisibilityTask(Container container, ServerWorld world, BlockStore store,
                       List<BlockPos> positions, TaskKind kind, String actionLabel) {
            this.containerId = container.getId();
            this.containerName = container.getName();
            this.world = world;
            this.store = store;
            this.fallbackStore = container.getBlockStore();
            this.positions = positions != null ? positions : Collections.emptyList();
            this.kind = kind;
            this.actionLabel = actionLabel;
            this.shown = lastShownBlocks.get(containerId);
        }

        int process(int budget) {
            int processed = 0;
            while (processed < budget && index < positions.size()) {
                BlockPos pos = positions.get(index++);
                processed++;
                if (pos == null) continue;
                switch (kind) {
                    case HIDE -> hideOne(pos);
                    case SHOW -> showOne(pos);
                    case RESTORE -> restoreOne(pos);
                }
            }
            return processed;
        }

        private boolean isChunkLoaded(int cx, int cz) {
            long key = chunkKey(cx, cz);
            Boolean cached = chunkLoadCache.get(key);
            if (cached != null) return cached;
            boolean loaded = world.getChunkManager().isChunkLoaded(cx, cz);
            chunkLoadCache.put(key, loaded);
            return loaded;
        }

        private void hideOne(BlockPos pos) {
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (!isChunkLoaded(cx, cz)) return;
            if (!store.contains(pos)) store.capture(world, pos);
            if (world.getBlockState(pos).isAir()) return;
            var be = world.getBlockEntity(pos);
            if (be != null) world.removeBlockEntity(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
            trackAffectedChunk(affectedChunks, cx, cz);
            changed++;
        }

        private void showOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null) return;
            restoreBlockEntry(world, pos, entry, affectedChunks);
            changed++;
        }

        private void restoreOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null && fallbackStore != null) entry = fallbackStore.get(pos);
            if (entry == null) return;
            restoreBlockEntry(world, pos, entry, affectedChunks);
            store.remove(pos);
            if (shown != null) shown.remove(pos.asLong());
            changed++;
        }

        boolean isComplete() { return index >= positions.size(); }
        int remaining() { return Math.max(0, positions.size() - index); }

        void finish() {
            flushLightingForChunks(world, affectedChunks);
            ContainerVisibilityFeedback.onTaskComplete(containerName, actionLabel);
            if (kind == TaskKind.SHOW) {
                snapshots.remove(containerId);
                lastShownBlocks.remove(containerId);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Visibility] 容器 {} {}完成，处理 {} 个方块，实际改动 {} 个",
                        containerName, actionLabel, positions.size(), changed);
            }
        }
    }

    private enum TaskKind { HIDE, SHOW, RESTORE }
}
