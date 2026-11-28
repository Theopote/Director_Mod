package com.director.core.container.visibility;

import com.director.core.container.BlockStore;
import com.director.core.container.BlockEntityNbtIO;
import com.director.core.container.Container;
import com.director.timeline.playback.TimelineSamplingContext;
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
    private final Map<UUID, Set<BlockPos>> lastAcceptedBlocks = new HashMap<>();
    private final Map<UUID, TimelinePreparation> preparations = new LinkedHashMap<>();
    private final Set<UUID> preparedForTimeline = new HashSet<>();
    private final Map<UUID, QuarantineSignature> quarantined = new HashMap<>();

    /** Geometry/snapshot versions for which the symmetric backfill has already run. */
    private final Map<UUID, BackfillState> backfilledContainers = new HashMap<>();
    private record BackfillState(long geometryRevision, long snapshotRevision) {}
    private record TargetSignature(int size, long sum, long xor) {}
    private record QuarantineSignature(String reason, RegistryKey<World> levelKey,
                                       long geometryRevision, long snapshotRevision,
                                       TargetSignature target, boolean worldAvailable) {}

    private final Deque<VisibilityTask> pendingTasks = new ArrayDeque<>();
    private final Set<ServerWorld> lightingWorlds = new HashSet<>();
    private final Map<UUID, Long> firstTargetNanos = new HashMap<>();
    private long lastPerfLogNanos;
    private long perfAttempts, perfWrites, perfTicks, perfLightingSchedules;
    private long perfAcceptedTargets, perfReusedTargets;
    private long perfTransientWaits, perfPermanentFailures, perfRetriesExhausted;
    private long perfMissingSnapshots, perfMissingWorlds;
    private long tickSequence;
    private double perfMaxTickMs;
    private int tickWrites;

    public record Diagnostics(int preparing, int working, int submitted, int quarantined,
                              long transientWaits, long permanentFailures, long retriesExhausted,
                              long missingSnapshots, long missingWorlds) {}


    public ContainerVisibilityService(MinecraftServer server) {
        this.server = server;
        LOGGER.debug("[ContainerVisibilityService] 初始化");
    }

    // =========================================================================
    // 服务端 tick：分帧追平时间线可见性目标
    // =========================================================================

    public void tick() {
        long started = System.nanoTime();
        tickSequence++;
        boolean hadWork = !preparations.isEmpty() || !submittedTargets.isEmpty()
                || !pendingTasks.isEmpty() || !timelineWork.isEmpty();
        tickWrites = 0;
        int initialBudget = getBlocksPerTickBudget();
        int budget = initialBudget;
        long deadline = started + 4_000_000L;
        for (var entry : new ArrayList<>(preparations.entrySet())) {
            if (budget <= 0 || System.nanoTime() >= deadline) break;
            var preparation = entry.getValue();
            budget -= preparation.process(Math.min(budget, 512), deadline);
            if (preparation.complete()) {
                snapshots.put(entry.getKey(), preparation.store);
                lastShownBlocks.put(entry.getKey(), preparation.shown);
                backfilledContainers.put(entry.getKey(), new BackfillState(
                        preparation.container.getGeometryRevision(), preparation.store.getRevision()));
                preparedForTimeline.add(entry.getKey());
                preparations.remove(entry.getKey());
            } else if (preparation.failureReason != null) {
                quarantine(entry.getValue().container, (Set<BlockPos>) null,
                        preparation.failureReason, true);
                preparations.remove(entry.getKey());
            }
        }
        for (var entry : submittedTargets.entrySet()) {
            if (System.nanoTime() >= deadline) break;
            var target = entry.getValue();
            if (quarantineMatches(entry.getKey(), target.container(), target.blocks())) {
                submittedTargets.remove(entry.getKey(), target);
                continue;
            }
            if (!preparedForTimeline.contains(entry.getKey()) || !snapshots.containsKey(entry.getKey())) {
                prepareContainerForTimelineOnServer(target.container());
                continue;
            }
            if (submittedTargets.remove(entry.getKey(), target)) {
                BlockStore snapshot = snapshots.get(entry.getKey());
                if (snapshot == null) {
                    quarantine(target.container(), target.blocks(), "缺少运行时快照", false);
                    continue;
                }
                if (backfillSnapshotIfNeeded(target.container(), snapshot)) {
                    // Geometry/data changed: the same target object must be reconciled again.
                    lastAcceptedBlocks.remove(entry.getKey());
                }
                if (lastAcceptedBlocks.get(entry.getKey()) == target.blocks()) {
                    perfReusedTargets++;
                    continue;
                }
                enqueueTimelineVisibilityTarget(target.container(), new BlockPositionTarget(target.blocks()));
                lastAcceptedBlocks.put(entry.getKey(), target.blocks());
                perfAcceptedTargets++;
            }
        }

        // 1) 优先处理 pendingTasks（hideContainer/showContainer 等整体操作）
        while (budget > 0 && !pendingTasks.isEmpty() && System.nanoTime() < deadline) {
            VisibilityTask task = pendingTasks.peekFirst();
            int consumed = task.process(budget, deadline);
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
                if (world == null) {
                    perfMissingWorlds++;
                    quarantine(container, work.target(), "容器世界不可用", true);
                    continue;
                }
                if (store == null) {
                    perfMissingSnapshots++;
                    quarantine(container, work.target(), "缺少运行时快照", false);
                    continue;
                }
                var affectedChunks = new LongOpenHashSet();
                int quantum = Math.max(512, budget / ids.size());
                int consumed = work.process(Math.min(budget, quantum), deadline, (pos, visible) -> {
                    var result = visible
                            ? timelineShowAt(world, store, BlockPos.fromLong(pos), affectedChunks)
                            : timelineHideAt(world, store, BlockPos.fromLong(pos), affectedChunks);
                    if (result == TimelineVisibilityWork.ApplyResult.WAITING
                            || result == TimelineVisibilityWork.ApplyResult.RETRY) perfTransientWaits++;
                    if (result == TimelineVisibilityWork.ApplyResult.FAILED) perfPermanentFailures++;
                    return result;
                });
                budget -= Math.max(1, consumed);
                if (!affectedChunks.isEmpty()) flushLightingForChunks(world, affectedChunks);
                if (work.isComplete()) {
                    if (work.hasFailures()) {
                        perfRetriesExhausted += work.retriesExhausted();
                        quarantine(container, work.target(),
                                "可见性追平失败（永久失败=" + work.permanentFailures()
                                        + "，瞬态重试耗尽=" + work.retriesExhausted() + "）", false);
                        continue;
                    }
                    LongSet latest = timelineVisibilityTargets.get(containerId);
                    if (latest == work.target()) timelineVisibilityTargets.remove(containerId);
                    else if (latest != null) timelineWork.put(containerId,
                            new TimelineVisibilityWork(lastShownBlocks.get(containerId), latest));
                } else timelineWork.put(containerId, work); // Round-robin across containers.
            }
        }
        int lightingSchedules = lightingWorlds.size();
        // WorldChunk.setBlockState already queued the affected block/section checks.
        // ServerLightingProvider.tick dispatches those checks to its own executor.
        for (var world : lightingWorlds) world.getChunkManager().getLightingProvider().tick();
        lightingWorlds.clear();
        firstTargetNanos.keySet().removeIf(id -> !preparations.containsKey(id) && !submittedTargets.containsKey(id)
                && !timelineWork.containsKey(id));
        if (hadWork || tickWrites > 0) {
            perfTicks++;
            perfAttempts += initialBudget - budget;
            perfWrites += tickWrites;
            perfLightingSchedules += lightingSchedules;
            long now = System.nanoTime();
            perfMaxTickMs = Math.max(perfMaxTickMs, (now - started) / 1_000_000.0);
            if (now - lastPerfLogNanos >= 5_000_000_000L) {
                long oldest = now;
                for (long time : firstTargetNanos.values()) oldest = Math.min(oldest, time);
                LOGGER.info("[Perf][ContainerVisibility] ticks={} budgetUsed={} writes={} maxTickMs={} preparing={} working={} submitted={} quarantined={} acceptedTargets={} reusedTargets={} lightingSchedules={} transientWaits={} permanentFailures={} retriesExhausted={} missingSnapshots={} missingWorlds={} oldestTargetMs={}",
                        perfTicks, perfAttempts, perfWrites, String.format(java.util.Locale.ROOT, "%.2f", perfMaxTickMs),
                        preparations.size(), timelineWork.size(), submittedTargets.size(), quarantined.size(),
                        perfAcceptedTargets, perfReusedTargets, perfLightingSchedules,
                        perfTransientWaits, perfPermanentFailures, perfRetriesExhausted,
                        perfMissingSnapshots, perfMissingWorlds, (now - oldest) / 1_000_000L);
                lastPerfLogNanos = now;
                perfAttempts = perfWrites = perfTicks = perfLightingSchedules = 0;
                perfAcceptedTargets = perfReusedTargets = 0;
                perfTransientWaits = perfPermanentFailures = perfRetriesExhausted = 0;
                perfMissingSnapshots = perfMissingWorlds = 0;
                perfMaxTickMs = 0;
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

    public Diagnostics diagnostics() {
        return new Diagnostics(preparations.size(), timelineWork.size(), submittedTargets.size(),
                quarantined.size(), perfTransientWaits, perfPermanentFailures,
                perfRetriesExhausted, perfMissingSnapshots, perfMissingWorlds);
    }

    private static TargetSignature targetSignature(Set<BlockPos> target) {
        if (target == null) return new TargetSignature(-1, 0, 0);
        long sum = 0, xor = 0;
        for (BlockPos pos : target) {
            if (pos == null) continue;
            long value = pos.asLong();
            sum += value;
            xor ^= value;
        }
        return new TargetSignature(target.size(), sum, xor);
    }

    private static TargetSignature targetSignature(LongSet target) {
        if (target == null) return new TargetSignature(-1, 0, 0);
        long sum = 0, xor = 0;
        for (long value : target) {
            sum += value;
            xor ^= value;
        }
        return new TargetSignature(target.size(), sum, xor);
    }

    private boolean quarantineMatches(UUID containerId, Container container, Set<BlockPos> target) {
        QuarantineSignature signature = quarantined.get(containerId);
        if (signature == null) return false;
        BlockStore snapshot = snapshots.get(containerId);
        RegistryKey<World> level = container.getLevelKey();
        boolean worldAvailable = level != null && server != null && server.getWorld(level) != null;
        TargetSignature currentTarget = targetSignature(target);
        boolean matches = Objects.equals(signature.levelKey, level)
                && signature.geometryRevision == container.getGeometryRevision()
                && signature.snapshotRevision == (snapshot == null ? -1 : snapshot.getRevision())
                && signature.worldAvailable == worldAvailable
                && (signature.target.size() < 0 || signature.target.equals(currentTarget));
        if (!matches) quarantined.remove(containerId);
        return matches;
    }

    private void quarantine(Container container, Set<BlockPos> target, String reason, boolean worldMissing) {
        UUID id = container.getId();
        RegistryKey<World> level = container.getLevelKey();
        BlockStore snapshot = snapshots.get(id);
        quarantined.put(id, new QuarantineSignature(reason, level, container.getGeometryRevision(),
                snapshot == null ? -1 : snapshot.getRevision(), targetSignature(target),
                !worldMissing && level != null && server != null && server.getWorld(level) != null));
        timelineWork.remove(id);
        timelineVisibilityTargets.remove(id);
        preparations.remove(id);
        preparedForTimeline.remove(id);
        submittedTargets.remove(id);
        lastAcceptedBlocks.remove(id);
        lastShownBlocks.remove(id);
        firstTargetNanos.remove(id);
        LOGGER.warn("[Visibility] 容器 {} 已隔离：{}（geometryRevision={}, snapshotRevision={}）",
                container.getName(), reason, container.getGeometryRevision(),
                snapshot == null ? -1 : snapshot.getRevision());
    }

    private void quarantine(Container container, LongSet target, String reason, boolean worldMissing) {
        Set<BlockPos> positions = new HashSet<>();
        if (target != null) {
            for (long value : target) positions.add(BlockPos.fromLong(value));
        }
        quarantine(container, positions, reason, worldMissing);
    }

    private void quarantine(Container container, Collection<BlockPos> target, String reason, boolean worldMissing) {
        quarantine(container, target != null ? new HashSet<>(target) : Collections.<BlockPos>emptySet(),
                reason, worldMissing);
    }

    // =========================================================================
    // 容器面板操作：hideContainer / showContainer（整体操作，走 pendingTasks）
    // =========================================================================

    public void hideContainer(Container container) {
        if (container == null || !TimelineSamplingContext.current().allowsWorldMutation()) {
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
            backfilledContainers.put(container.getId(), new BackfillState(
                    container.getGeometryRevision(), store.getRevision()));
            lastShownBlocks.remove(container.getId());
            enqueueOrRun(createHideTask(container, world, store,
                    new java.util.ArrayList<>(container.getBlocksView())));
            LOGGER.debug("[Visibility] 容器 {} 隐藏任务已入队，快照 {} 个方块",
                    container.getName(), store.size());
        }
    }

    public void showContainer(Container container) {
        if (container == null || !TimelineSamplingContext.current().allowsWorldMutation()) {
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
                perfMissingSnapshots++;
                quarantine(container, Collections.<BlockPos>emptySet(), "无快照可恢复", false);
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，已隔离", container.getName());
                return;
            }
        }
        backfillSnapshotIfNeeded(container, store);

        LOGGER.debug("[Visibility] 显示容器 {} (恢复 {} 个方块)", container.getName(), store.size());
        enqueueOrRun(createShowTask(container, world, store, new java.util.ArrayList<>(store.positions())));
    }

    public void restoreContainerBlocks(Container container, Collection<BlockPos> blocks) {
        if (container == null || blocks == null || blocks.isEmpty()
                || !TimelineSamplingContext.current().allowsWorldMutation()) {
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
            perfMissingSnapshots++;
            quarantine(container, blocks, "释放操作缺少运行时快照", false);
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
        if (container == null || !TimelineSamplingContext.current().allowsWorldMutation()) {
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
        if (container == null || !TimelineSamplingContext.current().allowsWorldMutation()) {
            return;
        }
        runOnServer(() -> prepareContainerForTimelineOnServer(container));
    }

    private void prepareContainerForTimelineOnServer(Container container) {
        UUID id = container.getId();
        if ((preparedForTimeline.contains(id) && snapshots.containsKey(id))
                || preparations.containsKey(id)) return;
        quarantined.remove(id);
        cancelPendingTasks(id);
        firstTargetNanos.putIfAbsent(id, System.nanoTime());
        preparations.put(id, new TimelinePreparation(container));
    }

    /** Snapshot copy and initial world inspection share the tick's attempt/time budget. */
    private final class TimelinePreparation {
        final Container container;
        final BlockStore store;
        final LongSet shown = new LongOpenHashSet();
        Iterator<BlockPos> positions;
        long version;
        int worldWaitAttempts;
        long nextWorldAttemptTick;
        String failureReason;

        TimelinePreparation(Container container) {
            this.container = container;
            this.store = snapshots.getOrDefault(container.getId(), new BlockStore());
            restart();
        }

        private void restart() {
            version = container.getGeometryRevision();
            positions = container.getBlocksView().iterator();
            shown.clear();
            worldWaitAttempts = 0;
            nextWorldAttemptTick = 0;
            failureReason = null;
            backfillSnapshotFromBlockStore(container, store);
        }

        int process(int budget, long deadline) {
            if (version != container.getGeometryRevision()) restart();
            if (tickSequence < nextWorldAttemptTick) return 0;
            ServerWorld world = resolveWorld(container);
            if (world == null) {
                worldWaitAttempts++;
                perfMissingWorlds++;
                if (worldWaitAttempts >= TimelineVisibilityWork.MAX_TRANSIENT_ATTEMPTS) {
                    failureReason = "容器世界持续不可用";
                } else {
                    long delay = 1L << Math.min(worldWaitAttempts - 1, 5);
                    nextWorldAttemptTick = tickSequence + delay;
                }
                return 0;
            }
            worldWaitAttempts = 0;
            nextWorldAttemptTick = 0;
            int used = 0;
            while (used < budget && positions.hasNext() && System.nanoTime() < deadline) {
                BlockPos pos = positions.next();
                used++;
                if (!store.contains(pos)) {
                    BlockStore.Entry source = container.getBlockStore().get(pos);
                    if (source != null) store.add(pos, source.copy());
                    else store.capture(world, pos);
                }
                if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                        && !world.getBlockState(pos).isAir()) shown.add(pos.asLong());
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
    private void enqueueTimelineVisibilityTarget(Container container, LongSet targetLongs) {
        UUID containerId = container.getId();
        cancelPendingTasks(containerId);
        LongSet shown = lastShownBlocks.computeIfAbsent(containerId, id -> new LongOpenHashSet());
        firstTargetNanos.putIfAbsent(containerId, System.nanoTime());
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
        backfillSnapshotFromBlockStore(container, snapshot);
        snapshots.put(container.getId(), snapshot);
        backfilledContainers.put(container.getId(), new BackfillState(
                container.getGeometryRevision(), snapshot.getRevision()));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 已从 blockStore 创建快照 (方块数: {})",
                    container.getName(), snapshot.size());
        }
        return true;
    }

    /**
     * Runs the symmetric geometry diff only when container geometry or snapshot data changed.
     *
     * @return whether a backfill pass was performed
     */
    private boolean backfillSnapshotIfNeeded(Container container, BlockStore snapshot) {
        UUID id = container.getId();
        BackfillState state = backfilledContainers.get(id);
        if (state != null && state.geometryRevision == container.getGeometryRevision()
                && state.snapshotRevision == snapshot.getRevision()) {
            return false;
        }
        backfillSnapshotFromBlockStore(container, snapshot);
        backfilledContainers.put(id, new BackfillState(
                container.getGeometryRevision(), snapshot.getRevision()));
        return true;
    }

    /**
     * Symmetric diff between the runtime snapshot and the container's current geometry.
     * Stale snapshot entries are removed even when the persistent source is empty.
     */
    private void backfillSnapshotFromBlockStore(Container container, BlockStore snapshot) {
        if (container == null || snapshot == null) return;
        Set<BlockPos> geometry = new HashSet<>(container.getBlocksView());
        int removed = 0;
        for (BlockPos pos : new ArrayList<>(snapshot.positions())) {
            if (pos != null && geometry.contains(pos)) continue;
            snapshot.remove(pos);
            removed++;
        }

        int added = 0;
        com.director.core.container.BlockStore source = container.getBlockStore();
        if (source != null) {
            for (BlockPos pos : geometry) {
                if (pos == null) continue;
                BlockStore.Entry existing = snapshot.get(pos);
                if (existing != null && existing.state != null) continue;
                BlockStore.Entry value = source.get(pos);
                if (value == null || value.state == null) continue;
                snapshot.add(pos, value.copy());
                added++;
            }
        }
        if ((added > 0 || removed > 0) && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 快照几何同步: 补充={} 删除={} 总数={}",
                    container.getName(), added, removed, snapshot.size());
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
        lastAcceptedBlocks.clear();
        preparations.clear();
        preparedForTimeline.clear();
        quarantined.clear();
        lightingWorlds.clear();
        firstTargetNanos.clear();
        perfAttempts = perfWrites = perfTicks = perfLightingSchedules = 0;
        perfAcceptedTargets = perfReusedTargets = 0;
        perfTransientWaits = perfPermanentFailures = perfRetriesExhausted = 0;
        perfMissingSnapshots = perfMissingWorlds = 0;
        tickSequence = 0;
        perfMaxTickMs = 0;
        lastPerfLogNanos = 0;
        backfilledContainers.clear();
        ContainerVisibilityFeedback.reset();
        LOGGER.debug("[ContainerVisibilityService] 清除所有快照");
    }

    // =========================================================================
    // 内部：时间线 apply 逻辑（tick() 调用）
    // =========================================================================

    private TimelineVisibilityWork.ApplyResult timelineHideAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        return hideBlockAt(world, store, pos, affectedChunks);
    }

    private TimelineVisibilityWork.ApplyResult timelineShowAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) return TimelineVisibilityWork.ApplyResult.WAITING;
        BlockStore.Entry entry = store.get(pos);
        if (entry == null || entry.state == null) {
            LOGGER.warn("[Visibility] Timeline-show snapshot entry is missing/invalid at {}", pos);
            return TimelineVisibilityWork.ApplyResult.FAILED;
        }
        return restoreBlockEntry(world, pos, entry, affectedChunks);
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
        lastAcceptedBlocks.remove(task.containerId);
        preparations.remove(task.containerId);
        preparedForTimeline.remove(task.containerId);
        quarantined.remove(task.containerId);
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
        if (world != null && affectedChunks != null && !affectedChunks.isEmpty()) lightingWorlds.add(world);
    }

    private TimelineVisibilityWork.ApplyResult hideBlockAt(ServerWorld world, BlockStore store, BlockPos pos,
                                   LongOpenHashSet affectedChunks) {
        if (world == null || store == null || pos == null) {
            return TimelineVisibilityWork.ApplyResult.FAILED;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
            return TimelineVisibilityWork.ApplyResult.WAITING;
        }
        try {
            net.minecraft.block.BlockState current = world.getBlockState(pos);
            BlockStore.Entry entry = store.get(pos);
            if (entry == null || entry.state == null || current.hasBlockEntity() && entry.beNbt == null) {
                store.capture(world, pos);
                entry = store.get(pos);
            }
            if (entry == null || entry.state == null
                    || current.hasBlockEntity() && entry.beNbt == null) {
                LOGGER.warn("[Visibility] Refusing to hide block without a complete snapshot at {}", pos);
                return TimelineVisibilityWork.ApplyResult.FAILED;
            }
            if (current.isAir()) {
                return TimelineVisibilityWork.ApplyResult.SUCCESS;
            }
            if (world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH)) {
                tickWrites++;
            }
        } catch (Exception e) {
            LOGGER.warn("[Visibility] Failed to hide block at {}: {}", pos, e.toString());
            return TimelineVisibilityWork.ApplyResult.RETRY;
        }
        if (!world.getBlockState(pos).isAir()) {
            LOGGER.warn("[Visibility] Hide did not materialize at {}", pos);
            return TimelineVisibilityWork.ApplyResult.RETRY;
        }
        trackAffectedChunk(affectedChunks, cx, cz);
        return TimelineVisibilityWork.ApplyResult.SUCCESS;
    }

    private TimelineVisibilityWork.ApplyResult restoreBlockEntry(ServerWorld world, BlockPos pos, BlockStore.Entry entry,
                                   LongOpenHashSet affectedChunks) {
        if (world == null || pos == null || entry == null || entry.state == null) {
            return TimelineVisibilityWork.ApplyResult.FAILED;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
            return TimelineVisibilityWork.ApplyResult.WAITING;
        }

        boolean changed = false;
        boolean restoredNbt = false;
        try {
            net.minecraft.block.BlockState current = world.getBlockState(pos);
            boolean stateMatches = current.equals(entry.state);
            net.minecraft.block.entity.BlockEntity existing = entry.beNbt == null ? null : world.getBlockEntity(pos);
            if (!stateMatches || entry.beNbt != null && existing == null) {
                if (stateMatches && !world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                        ContainerBlockUpdateFlags.BATCH)) {
                    LOGGER.warn("[Visibility] Could not replace block with missing block entity at {}", pos);
                    return TimelineVisibilityWork.ApplyResult.RETRY;
                }
                changed = world.setBlockState(pos, entry.state, ContainerBlockUpdateFlags.BATCH);
                if (changed) tickWrites++;
            }
            if (!world.getBlockState(pos).equals(entry.state)) {
                LOGGER.warn("[Visibility] Restore did not materialize at {}", pos);
                return TimelineVisibilityWork.ApplyResult.RETRY;
            }

            if (entry.beNbt != null) {
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                if (be == null) {
                    LOGGER.warn("[Visibility] Snapshot block entity is missing at {}", pos);
                    return TimelineVisibilityWork.ApplyResult.RETRY;
                }
                if (!BlockEntityNbtIO.matches(world, be, entry.beNbt)) {
                    if (!BlockEntityNbtIO.restore(be, entry.beNbt, world.getRegistryManager())) {
                        LOGGER.warn("[Visibility] Snapshot block entity NBT is invalid at {}", pos);
                        return TimelineVisibilityWork.ApplyResult.FAILED;
                    }
                    restoredNbt = true;
                }
                if (!BlockEntityNbtIO.matches(world, be, entry.beNbt)) {
                    LOGGER.warn("[Visibility] Restored block entity data does not match snapshot at {}", pos);
                    return TimelineVisibilityWork.ApplyResult.FAILED;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Visibility] Failed to restore block at {} to {}: {}",
                    pos, entry.state, e.toString());
            return TimelineVisibilityWork.ApplyResult.RETRY;
        }
        if (changed || restoredNbt) trackAffectedChunk(affectedChunks, cx, cz);
        return TimelineVisibilityWork.ApplyResult.SUCCESS;
    }

    // =========================================================================
    // VisibilityTask
    // =========================================================================

    private final class VisibilityTask {
        final UUID containerId;
        final String containerName;
        final String actionLabel;
        private final Container container;
        private final ServerWorld world;
        private final BlockStore store;
        private final BlockStore fallbackStore;
        private final List<BlockPos> positions;
        private final TaskKind kind;
        private final LongSet shown;
        private final LongOpenHashSet affectedChunks = new LongOpenHashSet();
        private final Deque<BlockPos> retries = new ArrayDeque<>();
        private final Map<Long, Integer> transientAttempts = new HashMap<>();
        private int index = 0;
        private int changed = 0;
        private int permanentFailures;

        VisibilityTask(Container container, ServerWorld world, BlockStore store,
                       List<BlockPos> positions, TaskKind kind, String actionLabel) {
            this.containerId = container.getId();
            this.containerName = container.getName();
            this.container = container;
            this.world = world;
            this.store = store;
            this.fallbackStore = container.getBlockStore();
            this.positions = positions != null ? positions : Collections.emptyList();
            this.kind = kind;
            this.actionLabel = actionLabel;
            this.shown = lastShownBlocks.get(containerId);
        }

        int process(int budget, long deadline) {
            int processed = 0;
            int pendingRetries = retries.size();
            while (processed < budget && pendingRetries-- > 0 && System.nanoTime() < deadline) {
                BlockPos pos = retries.removeFirst();
                processed++;
                applyOne(pos);
            }
            while (processed < budget && index < positions.size() && System.nanoTime() < deadline) {
                BlockPos pos = positions.get(index++);
                processed++;
                if (pos != null) applyOne(pos);
            }
            return processed;
        }

        private void applyOne(BlockPos pos) {
            TimelineVisibilityWork.ApplyResult result;
            switch (kind) {
                case HIDE -> result = hideOne(pos);
                case SHOW -> result = showOne(pos);
                case RESTORE -> result = restoreOne(pos);
                default -> throw new IllegalStateException("Unknown visibility task: " + kind);
            }
            if (result == TimelineVisibilityWork.ApplyResult.SUCCESS) {
                transientAttempts.remove(pos.asLong());
                changed++;
                return;
            }
            if (result == TimelineVisibilityWork.ApplyResult.FAILED) {
                transientAttempts.remove(pos.asLong());
                permanentFailures++;
                return;
            }
            int attempts = transientAttempts.merge(pos.asLong(), 1, Integer::sum);
            if (attempts >= TimelineVisibilityWork.MAX_TRANSIENT_ATTEMPTS) {
                transientAttempts.remove(pos.asLong());
                permanentFailures++;
                LOGGER.warn("[Visibility] {} retries exhausted for container {} at {}",
                        actionLabel, containerName, pos);
            } else {
                retries.addLast(pos);
            }
        }

        private TimelineVisibilityWork.ApplyResult hideOne(BlockPos pos) {
            return hideBlockAt(world, store, pos, affectedChunks);
        }

        private TimelineVisibilityWork.ApplyResult showOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null) return TimelineVisibilityWork.ApplyResult.FAILED;
            return restoreBlockEntry(world, pos, entry, affectedChunks);
        }

        private TimelineVisibilityWork.ApplyResult restoreOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null && fallbackStore != null) entry = fallbackStore.get(pos);
            if (entry == null) return TimelineVisibilityWork.ApplyResult.FAILED;
            TimelineVisibilityWork.ApplyResult result = restoreBlockEntry(world, pos, entry, affectedChunks);
            if (result == TimelineVisibilityWork.ApplyResult.SUCCESS) {
                store.remove(pos);
                if (shown != null) shown.remove(pos.asLong());
            }
            return result;
        }

        boolean isComplete() { return index >= positions.size() && retries.isEmpty(); }
        int remaining() { return Math.max(0, positions.size() - index) + retries.size(); }

        void finish() {
            if (permanentFailures > 0) {
                ContainerVisibilityFeedback.reset();
                quarantine(container, positions, actionLabel + "失败（永久失败=" + permanentFailures + "）", false);
                return;
            }
            flushLightingForChunks(world, affectedChunks);
            ContainerVisibilityFeedback.onTaskComplete(containerName, actionLabel);
            if (kind == TaskKind.SHOW) {
                snapshots.remove(containerId);
                lastShownBlocks.remove(containerId);
                lastAcceptedBlocks.remove(containerId);
                preparedForTimeline.remove(containerId);
                backfilledContainers.remove(containerId);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Visibility] 容器 {} {}完成，处理 {} 个方块，实际改动 {} 个",
                        containerName, actionLabel, positions.size(), changed);
            }
        }
    }

    private enum TaskKind { HIDE, SHOW, RESTORE }
}
