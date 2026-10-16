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
 *   <li>暂停播放后仍完成已提交的目标，队列为空时自动休眠</li>
 * </ul>
 *
 * @author Director Team
 * @version 5.3
 */
public class ContainerVisibilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerVisibilityService.class);

    private final MinecraftServer server;

    // 每个容器对应一个快照（隐藏时使用，显示时恢复）
    private final Map<UUID, BlockStore> snapshots = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 上一帧 showContainerBlocksForTimeline() 已落地的方块集合（posLong）。
     *
     * <p>delta 追平：只对新增显示与新增隐藏的少量方块做 setBlockState。
     */
    private final Map<UUID, LongSet> lastShownBlocks = new HashMap<>();

    /** 时间线播放/拖动播放头时期望落地的方块目标集（posLong），由服务端分帧追平。 */
    private final Map<UUID, LongSet> timelineVisibilityTargets = new HashMap<>();
    private final Map<UUID, TimelineVisibilityWork> timelineWork = new LinkedHashMap<>();
    private record TimelineTarget(Container container, Set<BlockPos> blocks) {}
    // Latest target wins; client frames cannot flood the server executor with full-set conversions.
    private final Map<UUID, TimelineTarget> submittedTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, TimelineTargetConversion> targetConversions = new LinkedHashMap<>();
    private final Map<UUID, Set<BlockPos>> lastConvertedBlocks = new HashMap<>();
    private final Map<UUID, TimelinePreparation> preparations = new LinkedHashMap<>();
    private final Set<UUID> preparedForTimeline = new HashSet<>();


    /**
     * 已完成 backfill 的容器 ID 集合。
     *
     * <p>backfillSnapshotFromBlockStore 是 O(N) 遍历，只需在快照首次建立时执行一次。
     * 当快照被 clearAll 清除时，同步从这里移除。
     * 注意：cancelPendingTasks 不清除此标志，因为快照本身仍然有效。
     */
    private final Set<UUID> backfilledContainers = new HashSet<>();

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
        long deadline = System.nanoTime() + 4_000_000L;
        for (var entry : new ArrayList<>(preparations.entrySet())) {
            if (budget <= 0 || System.nanoTime() >= deadline) break;
            var preparation = entry.getValue();
            budget -= preparation.process(Math.min(budget, 512), deadline);
            if (preparation.complete()) {
                snapshots.put(entry.getKey(), preparation.store);
                lastShownBlocks.put(entry.getKey(), preparation.shown);
                backfilledContainers.add(entry.getKey());
                preparedForTimeline.add(entry.getKey());
                preparations.remove(entry.getKey());
            }
        }
        for (var entry : submittedTargets.entrySet()) {
            if (System.nanoTime() >= deadline) break;
            var target = entry.getValue();
            if (!preparedForTimeline.contains(entry.getKey())) {
                prepareContainerForTimelineOnServer(target.container());
                continue;
            }
            if (targetConversions.containsKey(entry.getKey())) continue;
            if (submittedTargets.remove(entry.getKey(), target)
                    && lastConvertedBlocks.get(entry.getKey()) != target.blocks())
                targetConversions.put(entry.getKey(), new TimelineTargetConversion(target));
        }
        int conversionBudget = Math.max(1, budget / 3);
        for (var entry : new ArrayList<>(targetConversions.entrySet())) {
            if (budget <= 0 || conversionBudget <= 0 || System.nanoTime() >= deadline) break;
            var conversion = entry.getValue();
            int used = conversion.process(Math.min(conversionBudget, 4096), deadline);
            budget -= used;
            conversionBudget -= used;
            if (!conversion.positions.hasNext()) {
                enqueueTimelineVisibilityTarget(conversion.source.container(), conversion.longs);
                lastConvertedBlocks.put(entry.getKey(), conversion.source.blocks());
                targetConversions.remove(entry.getKey());
            }
        }

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

        // Pausing playback never cancels the last requested world state.
        if (budget > 0 && !timelineWork.isEmpty()) {
            var ids = new ArrayList<>(timelineWork.keySet());
            for (UUID containerId : ids) {
                if (budget <= 0 || System.nanoTime() >= deadline) break;
                var work = timelineWork.remove(containerId);
                Container container = com.director.core.DirectorServices.get().containers().getContainer(containerId);
                if (container == null) {
                    timelineVisibilityTargets.remove(containerId);
                    continue;
                }
                ServerWorld world = resolveWorld(container);
                BlockStore store = snapshots.get(containerId);
                if (world == null || store == null) {
                    timelineWork.put(containerId, work);
                    continue;
                }
                var affectedChunks = new LongOpenHashSet();
                int consumed = work.process(Math.min(budget, Math.max(512, budget / ids.size())), deadline, (pos, visible) -> visible
                        ? timelineShowAt(world, store, BlockPos.fromLong(pos), affectedChunks)
                        : timelineHideAt(world, store, BlockPos.fromLong(pos), affectedChunks));
                budget -= Math.max(1, consumed);
                if (!affectedChunks.isEmpty()) flushLightingForChunks(world, affectedChunks);
                if (work.isComplete()) {
                    LongSet latest = timelineVisibilityTargets.get(containerId);
                    if (latest == work.target()) timelineVisibilityTargets.remove(containerId);
                    else if (latest != null) timelineWork.put(containerId,
                            new TimelineVisibilityWork(lastShownBlocks.get(containerId), latest));
                } else timelineWork.put(containerId, work); // Round-robin across containers.
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
     * 提交后调用方不得修改该集合；连续提交会合并，只保留最新的待处理目标。
     */
    public void showContainerBlocksForTimeline(Container container, Set<BlockPos> blocksToShow) {
        if (container == null) {
            return;
        }
        final Set<BlockPos> target = blocksToShow != null ? blocksToShow : Collections.emptySet();
        submittedTargets.put(container.getId(), new TimelineTarget(container, target));
    }

    /**
     * 容器拖入时间线：建立快照并记录当前世界可见集。
     *
     * <p>接管时取消旧的面板整体任务，保留快照和已恢复状态；不打断其他容器。
     */
    public void prepareContainerForTimeline(Container container) {
        if (container == null) {
            return;
        }
        runOnServer(() -> prepareContainerForTimelineOnServer(container));
    }

    private void prepareContainerForTimelineOnServer(Container container) {
        UUID id = container.getId();
        if (preparedForTimeline.contains(id) || preparations.containsKey(id)) return;
        cancelPendingTasks(id);
        preparations.put(id, new TimelinePreparation(container));
    }

    /** Snapshot copy and initial world inspection share the tick's attempt/time budget. */
    private final class TimelinePreparation {
        final Container container;
        final BlockStore store;
        final LongSet shown = new LongOpenHashSet();
        Iterator<BlockPos> positions;
        long version;

        TimelinePreparation(Container container) {
            this.container = container;
            this.store = snapshots.getOrDefault(container.getId(), new BlockStore());
            restart();
        }

        private void restart() {
            version = container.getModifiedTime();
            positions = container.getBlocksView().iterator();
            shown.clear();
        }

        int process(int budget, long deadline) {
            if (version != container.getModifiedTime()) restart();
            ServerWorld world = resolveWorld(container);
            if (world == null) return 0;
            int used = 0;
            while (used < budget && positions.hasNext() && System.nanoTime() < deadline) {
                BlockPos pos = positions.next();
                used++;
                if (!store.contains(pos)) {
                    BlockStore.Entry source = container.getBlockStore().get(pos);
                    if (source != null) store.add(pos, new BlockStore.Entry(source.state, source.beNbt));
                    else store.capture(world, pos);
                }
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                        || !world.getBlockState(pos).isAir()) shown.add(pos.asLong());
            }
            return used;
        }

        boolean complete() { return !positions.hasNext(); }
    }

    /** Compatibility hook: stopping the playhead does not stop pending world reconciliation. */
    public void pauseTimelineDriving() {}

    public void resumeTimelineDriving() {}

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
    private static final class TimelineTargetConversion {
        final TimelineTarget source;
        final Iterator<BlockPos> positions;
        final LongSet longs = new LongOpenHashSet();

        TimelineTargetConversion(TimelineTarget source) {
            this.source = source;
            positions = source.blocks().iterator();
        }

        int process(int budget, long deadline) {
            int used = 0;
            while (used < budget && positions.hasNext() && System.nanoTime() < deadline) {
                BlockPos pos = positions.next();
                if (pos != null) longs.add(pos.asLong());
                used++;
            }
            return used;
        }
    }

    private void enqueueTimelineVisibilityTarget(Container container, LongSet targetLongs) {
        UUID containerId = container.getId();
        cancelPendingTasks(containerId);
        LongSet shown = lastShownBlocks.computeIfAbsent(containerId, id -> new LongOpenHashSet());
        timelineVisibilityTargets.put(containerId, targetLongs);
        // Retain the current scan's progress; new targets cancel obsolete retries immediately.
        timelineWork.computeIfAbsent(containerId, id -> new TimelineVisibilityWork(shown, targetLongs))
                .updateLatestTarget(targetLongs);
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
        timelineWork.clear();
        submittedTargets.clear();
        targetConversions.clear();
        lastConvertedBlocks.clear();
        preparations.clear();
        preparedForTimeline.clear();
        backfilledContainers.clear();
        ContainerVisibilityFeedback.reset();
        LOGGER.debug("[ContainerVisibilityService] 清除所有快照");
    }

    // =========================================================================
    // 内部：时间线 apply 逻辑（tick() 调用）
    // =========================================================================

    private boolean timelineHideAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        if (world.getBlockState(pos).isAir()) return true;
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
        if (entry.state == null) return false;
        if (world.getBlockState(pos).equals(entry.state)) return true;
        restoreBlockEntry(world, pos, entry, affectedChunks);
        return world.getBlockState(pos).equals(entry.state);
    }


    // =========================================================================
    // 内部：pendingTasks 管理
    // =========================================================================

    private void enqueueOrRun(VisibilityTask task) {
        if (task == null || task.isComplete()) return;
        cancelPendingTasks(task.containerId);
        timelineWork.remove(task.containerId);
        timelineVisibilityTargets.remove(task.containerId);
        submittedTargets.remove(task.containerId);
        targetConversions.remove(task.containerId);
        lastConvertedBlocks.remove(task.containerId);
        preparations.remove(task.containerId);
        preparedForTimeline.remove(task.containerId);
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
