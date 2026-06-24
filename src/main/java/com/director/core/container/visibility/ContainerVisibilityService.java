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
 * 容器可见性服务（v5.2）
 * 
 * <p>负责管理容器的隐藏/显示，通过真正将世界中的方块替换为AIR来实现完全隐藏。
 * 
 * <p>核心原理：
 * <ul>
 *   <li>隐藏时：保存方块快照，然后将世界中的方块设置为AIR</li>
 *   <li>显示时：从快照恢复方块状态和方块实体数据</li>
 * </ul>
 * 
 * <p>优点：
 * <ul>
 *   <li>渲染、碰撞、光照、粒子、更新全部自动正确</li>
 *   <li>不需要大量Mixin拦截</li>
 *   <li>服务端和客户端完全同步</li>
 *   <li>兼容所有Minecraft版本</li>
 * </ul>
 * 
 * @author Director Team
 * @version 5.2
 */
public class ContainerVisibilityService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerVisibilityService.class);
    
    private final MinecraftServer server;
    
    // 每个容器对应一个快照（隐藏时使用，显示时恢复）
    private final Map<UUID, BlockStore> snapshots = new HashMap<>();

    /**
     * 上一帧 showContainerBlocks() 的目标显示集合（posLong）。
     *
     * <p>性能关键：时间线播放会每帧调用 showContainerBlocks，原实现每次都遍历 container.getBlocks()
     * 并全量检查/置 AIR/恢复，导致 30~200ms 的主线程开销和大量区块重建，FPS 会掉到个位数。
     *
     * <p>这里改为 delta：只对 “新增显示” 与 “新增隐藏” 的少量方块做 setBlockState。
     */
    private final Map<UUID, LongSet> lastShownBlocks = new HashMap<>();

    /** 时间线播放/拖动播放头时期望落地的方块目标集（posLong），由服务端分帧追平。 */
    private final Map<UUID, LongSet> timelineVisibilityTargets = new HashMap<>();

    /** 静止预览时暂停分帧追平（不清 map，避免与 tick 迭代并发冲突）。 */
    private volatile boolean timelineDrivingPaused = true;

    private final Deque<VisibilityTask> pendingTasks = new ArrayDeque<>();
    
    public ContainerVisibilityService(MinecraftServer server) {
        this.server = server;
        LOGGER.debug("[ContainerVisibilityService] 初始化");
    }

    public void tick() {
        int budget = getBlocksPerTickBudget();
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

        if (!timelineDrivingPaused && budget > 0 && !timelineVisibilityTargets.isEmpty()) {
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
    
    /**
     * 获取容器所在维度
     */
    private ServerWorld resolveWorld(Container container) {
        RegistryKey<World> key = container.getLevelKey();
        if (key == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界", container.getName());
            return null;
        }
        return server.getWorld(key);
    }
    
    /**
     * 隐藏容器：保存方块快照，然后将世界中的方块设置为AIR
     * 如果容器已经有快照（已隐藏），则只处理新增的方块
     */
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
            // 容器已经隐藏，只处理新增的方块
            LOGGER.debug("[Visibility] 容器 {} 已隐藏，处理新增方块 (总方块数: {})", 
                container.getName(), container.getBlockCount());
            store = existingStore;
            backfillSnapshotFromBlockStore(container, store);
            
            // 已有快照时仍按整容器重新对齐。这样可以处理“大容器显示任务尚未完成又切回隐藏”的情况。
            // 具体快照保护由 hideOne() 负责：已有快照的位置不会被 AIR 覆盖。
            java.util.List<BlockPos> toHide = new java.util.ArrayList<>(container.getBlocksView());
            enqueueOrRun(createHideTask(container, world, store, toHide));
            
            LOGGER.debug("[Visibility] 容器 {} 新增方块已隐藏，快照总数: {}", 
                container.getName(), store.size());
        } else {
            // 容器未隐藏，完整隐藏流程
            LOGGER.debug("[Visibility] 隐藏容器 {} (方块数 {})", container.getName(), container.getBlockCount());
            
            // 保存当前世界中的真实方块快照
            store = new BlockStore();
            backfillSnapshotFromBlockStore(container, store);
            snapshots.put(container.getId(), store);
            lastShownBlocks.remove(container.getId());
            enqueueOrRun(createHideTask(container, world, store, new java.util.ArrayList<>(container.getBlocksView())));
            LOGGER.debug("[Visibility] 容器 {} 隐藏任务已入队，快照 {} 个方块",
                container.getName(), store.size());
        }
    }
    
    /**
     * 显示容器：从快照恢复方块状态和方块实体
     */
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
            // ⭐ 关键修复：重启游戏后 snapshots 是纯内存，会丢失。
            // 若容器处于隐藏状态，我们可以用 container.blockStore（持久化的原始快照）重建快照并恢复。
            if (container.isHidden() && createSnapshotFromBlockStore(container)) {
                store = snapshots.get(container.getId());
            }
            if (store == null) {
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过", container.getName());
                return;
            }
        }
        backfillSnapshotFromBlockStore(container, store);
        
        LOGGER.debug("[Visibility] 显示容器 {} (恢复 {} 个方块)", 
            container.getName(), store.size());
        
        enqueueOrRun(createShowTask(container, world, store, new java.util.ArrayList<>(store.positions())));
        LOGGER.debug("[Visibility] 容器 {} 显示任务已入队", container.getName());
    }

    /**
     * 从隐藏容器中释放一部分方块。
     *
     * <p>用于“从容器移除方块/撤销添加方块”这类元数据操作：这些方块不再受容器隐藏状态控制，
     * 因此应该立即从快照恢复到世界，并从运行时快照与增量显示缓存中移除。
     */
    public void restoreContainerBlocks(Container container, Collection<BlockPos> blocks) {
        if (container == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        runOnServer(() -> restoreContainerBlocksOnServer(container, blocks));
    }

    private void restoreContainerBlocksOnServer(Container container, Collection<BlockPos> blocks) {
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法释放方块", container.getName());
            return;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null && createSnapshotFromBlockStore(container)) {
            store = snapshots.get(container.getId());
        }
        if (store == null) {
            return;
        }
        backfillSnapshotFromBlockStore(container, store);

        enqueueOrRun(createRestoreTask(container, world, store, new java.util.ArrayList<>(blocks)));
    }
    
    /**
     * 检查容器是否已隐藏
     */
    public boolean isHidden(UUID containerId) {
        return snapshots.containsKey(containerId);
    }
    
    /**
     * 清除所有快照（服务器关闭时调用）
     */
    public void clearAll() {
        pendingTasks.clear();
        snapshots.clear();
        lastShownBlocks.clear();
        timelineVisibilityTargets.clear();
        ContainerVisibilityFeedback.reset();
        LOGGER.debug("[ContainerVisibilityService] 清除所有快照");
    }
    
    /**
     * 时间线专用：设置期望落地的方块目标集，由服务端分帧追平（不取消进行中的任务、不阻塞 UI 线程）。
     *
     * <p>拖动播放头 / 松手追帧时使用；正常播放仍走同步 {@link #showContainerBlocks}。
     */
    public void showContainerBlocksForTimeline(Container container, Set<BlockPos> blocksToShow) {
        if (container == null) {
            return;
        }
        final Set<BlockPos> target = blocksToShow != null ? blocksToShow : Collections.emptySet();
        runOnServer(() -> enqueueTimelineVisibilityTarget(container, target));
    }

    /**
     * 容器拖入时间线：仅建立快照并记录当前世界可见集，不触发全量 hideContainer。
     *
     * <p>世界方块由 {@link #showContainerBlocksForTimeline} 按播放头分帧追平。
     */
    public void prepareContainerForTimeline(Container container) {
        if (container == null) {
            return;
        }
        runOnServer(() -> prepareContainerForTimelineOnServer(container));
    }

    private void prepareContainerForTimelineOnServer(Container container) {
        cancelPendingTasks(container.getId());
        timelineVisibilityTargets.remove(container.getId());

        if (!createSnapshotFromBlockStore(container)) {
            ServerWorld world = resolveWorld(container);
            if (world == null) {
                LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法为时间线准备快照", container.getName());
                return;
            }
            BlockStore store = snapshots.get(container.getId());
            if (store == null) {
                store = new BlockStore();
                backfillSnapshotFromBlockStore(container, store);
                if (store.isEmpty()) {
                    for (BlockPos pos : container.getBlocksView()) {
                        store.capture(world, pos);
                    }
                }
                snapshots.put(container.getId(), store);
                LOGGER.debug("[Visibility] 容器 {} 已从世界捕获时间线快照 ({} 个方块)",
                        container.getName(), store.size());
            }
        }

        ServerWorld world = resolveWorld(container);
        LongOpenHashSet currentlyShown = new LongOpenHashSet(Math.max(16, container.getBlockCount()));
        for (BlockPos pos : container.getBlocksView()) {
            if (world == null || !world.getBlockState(pos).isAir()) {
                currentlyShown.add(pos.asLong());
            }
        }
        lastShownBlocks.put(container.getId(), currentlyShown);
        timelineDrivingPaused = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 已准备时间线控制 (快照方块: {}, 世界当前可见: {})",
                    container.getName(),
                    snapshots.containsKey(container.getId()) ? snapshots.get(container.getId()).size() : 0,
                    currentlyShown.size());
        }
    }

    /** 预览静止：暂停分帧追平（仅设标志，不在客户端线程上 clear map）。 */
    public void pauseTimelineDriving() {
        timelineDrivingPaused = true;
    }

    public void resumeTimelineDriving() {
        timelineDrivingPaused = false;
    }

    private void enqueueTimelineVisibilityTarget(Container container, Set<BlockPos> blocksToShow) {
        timelineDrivingPaused = false;
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            LOGGER.warn("[Visibility] 容器 {} 未绑定世界，无法设置时间线可见性目标", container.getName());
            return;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null) {
            if (createSnapshotFromBlockStore(container)) {
                store = snapshots.get(container.getId());
            }
            if (store == null) {
                LOGGER.warn("[Visibility] 容器 {} 无快照可恢复，跳过时间线可见性", container.getName());
                return;
            }
        }
        backfillSnapshotFromBlockStore(container, store);

        LongOpenHashSet targetLongs = new LongOpenHashSet(Math.max(16, blocksToShow.size() * 2));
        for (BlockPos pos : blocksToShow) {
            if (pos != null) {
                targetLongs.add(pos.asLong());
            }
        }

        UUID containerId = container.getId();
        if (isTimelineTargetSatisfied(containerId, targetLongs, world)) {
            timelineVisibilityTargets.remove(containerId);
            return;
        }

        LongSet previousTarget = timelineVisibilityTargets.get(containerId);
        if (previousTarget != null && longSetEquals(previousTarget, targetLongs)) {
            return;
        }

        timelineVisibilityTargets.put(containerId, targetLongs);
        lastShownBlocks.computeIfAbsent(containerId, id -> new LongOpenHashSet());

        int kickBudget = Math.max(256, getBlocksPerTickBudget() / 3);
        applyTimelineVisibilityForContainer(container, targetLongs, kickBudget);
    }

    private static boolean longSetEquals(LongSet a, LongSet b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (LongIterator it = a.iterator(); it.hasNext(); ) {
            if (!b.contains(it.nextLong())) {
                return false;
            }
        }
        return true;
    }

    private boolean isTimelineTargetSatisfied(UUID containerId, LongSet target, ServerWorld world) {
        LongSet last = lastShownBlocks.get(containerId);
        if (target == null || target.isEmpty()) {
            return last == null || last.isEmpty();
        }
        if (last == null) {
            last = new LongOpenHashSet();
        }
        for (LongIterator it = target.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (last.contains(posLong)) {
                continue;
            }
            if (world != null) {
                BlockPos pos = BlockPos.fromLong(posLong);
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
            }
            return false;
        }
        for (LongIterator it = last.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (target.contains(posLong)) {
                continue;
            }
            if (world != null) {
                BlockPos pos = BlockPos.fromLong(posLong);
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    private int applyTimelineVisibilityForContainer(Container container, LongSet target, int budget) {
        if (budget <= 0 || container == null || target == null) {
            return 0;
        }

        BlockStore store = snapshots.get(container.getId());
        if (store == null) {
            return 0;
        }
        ServerWorld world = resolveWorld(container);
        if (world == null) {
            return 0;
        }

        LongSet last = lastShownBlocks.computeIfAbsent(container.getId(), id -> new LongOpenHashSet());
        LongOpenHashSet affectedChunks = new LongOpenHashSet();
        int processed = 0;
        boolean preferShows = TimelinePlacementBudget.isAccelerated();

        if (preferShows) {
            processed += applyTimelineShows(world, store, last, target, affectedChunks, budget);
            processed += applyTimelineHides(world, store, last, target, affectedChunks, budget - processed);
        } else {
            processed += applyTimelineHides(world, store, last, target, affectedChunks, budget);
            processed += applyTimelineShows(world, store, last, target, affectedChunks, budget - processed);
        }

        if (processed > 0) {
            flushLightingForChunks(world, affectedChunks);
        }
        return processed;
    }

    private int applyTimelineHides(ServerWorld world,
            BlockStore store,
            LongSet last,
            LongSet target,
            LongOpenHashSet affectedChunks,
            int budget) {
        if (budget <= 0 || last.isEmpty()) {
            return 0;
        }
        int processed = 0;
        List<Long> toHide = new ArrayList<>();
        for (LongIterator it = last.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (!target.contains(posLong)) {
                toHide.add(posLong);
            }
        }
        for (long posLong : toHide) {
            if (processed >= budget) {
                break;
            }
            BlockPos pos = BlockPos.fromLong(posLong);
            if (timelineHideAt(world, store, pos, affectedChunks)) {
                last.remove(posLong);
                processed++;
            }
        }
        return processed;
    }

    private int applyTimelineShows(ServerWorld world,
            BlockStore store,
            LongSet last,
            LongSet target,
            LongOpenHashSet affectedChunks,
            int budget) {
        if (budget <= 0 || target.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (LongIterator it = target.iterator(); it.hasNext(); ) {
            if (processed >= budget) {
                break;
            }
            long posLong = it.nextLong();
            if (last.contains(posLong)) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(posLong);
            if (timelineShowAt(world, store, pos, affectedChunks)) {
                last.add(posLong);
                processed++;
            }
        }
        return processed;
    }

    private boolean timelineHideAt(ServerWorld world, BlockStore store, BlockPos pos, LongOpenHashSet affectedChunks) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        net.minecraft.block.BlockState currentState = world.getBlockState(pos);
        if (currentState.isAir()) {
            return false;
        }
        if (!store.contains(pos)) {
            store.capture(world, pos);
        }
        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            world.removeBlockEntity(pos);
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
        trackAffectedChunk(affectedChunks, chunkX, chunkZ);
        return true;
    }

    private boolean timelineShowAt(ServerWorld world, BlockStore store, BlockPos pos, LongOpenHashSet affectedChunks) {
        BlockStore.Entry entry = store.get(pos);
        if (entry == null) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        net.minecraft.block.BlockState currentState = world.getBlockState(pos);
        if (!currentState.isAir()) {
            return false;
        }
        restoreBlockEntry(world, pos, entry, affectedChunks);
        return true;
    }

    /**
     * 显示容器的部分方块（动画效果使用）
     * 
     * <p>根据指定的方块集合，只显示这些方块，其他方块保持隐藏
     * 
     * @param container 容器对象
     * @param blocksToShow 应该显示的方块位置集合
     */
    public void showContainerBlocks(Container container, java.util.Set<net.minecraft.util.math.BlockPos> blocksToShow) {
        cancelPendingTasks(container.getId());
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
        backfillSnapshotFromBlockStore(container, store);
        
        // blocksToShow 为空：表示“全部隐藏”（用于时间线播放/清除轨道）
        // 之前版本直接 return 会导致：当容器在世界中仍可见时，无法将其清空。
        // 因此这里统一走“隐藏不应显示的方块”的分支，实现全部置 AIR。
        if (blocksToShow == null) {
            blocksToShow = java.util.Collections.emptySet();
        }

        // ===== 性能关键：delta 更新（只处理新增显示/新增隐藏）=====
        // 但需要处理一个关键边界：第一次进入时间线控制时，世界里可能已经存在“全量方块”。
        // 若直接从 last=空 开始做 delta，则不会把“非目标方块”置 AIR，表现为“一碰到片段全出来”。
        // 因此首次调用做一次全量对齐：按 store.positions() 把不在 blocksToShow 的方块强制置 AIR。
        LongSet last = lastShownBlocks.get(container.getId());
        boolean firstCall = (last == null);
        if (firstCall) {
            last = new LongOpenHashSet();
            lastShownBlocks.put(container.getId(), last);
        }

        LongOpenHashSet now = new LongOpenHashSet(Math.max(16, blocksToShow.size() * 2));
        for (BlockPos p : blocksToShow) {
            if (p != null) {
                now.add(p.asLong());
            }
        }

        // 0) 首次调用：全量对齐（把非目标方块清空为 AIR）
        if (firstCall) {
            int hiddenCount = 0;
            int shownCount = 0;
            LongOpenHashSet affectedChunks = new LongOpenHashSet();

            // hide: allSnapshotPositions - now
            // 注意：这里遍历 store.positions()，不要在循环里修改 store（否则可能触发并发修改异常）。
            for (BlockPos pos : store.positions()) {
                if (pos == null) continue;
                long posLong = pos.asLong();
                if (now.contains(posLong)) {
                    continue;
                }

                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                net.minecraft.block.BlockState currentState = world.getBlockState(pos);
                if (!currentState.isAir()) {
                    net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                    if (be != null) {
                        world.removeBlockEntity(pos);
                    }
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
                    trackAffectedChunk(affectedChunks, chunkX, chunkZ);
                    hiddenCount++;
                }
            }

            // show: now (from snapshot)
            for (var it = now.iterator(); it.hasNext(); ) {
                long posLong = it.nextLong();
                BlockPos pos = BlockPos.fromLong(posLong);

                BlockStore.Entry entry = store.get(pos);
                if (entry == null) {
                    continue;
                }

                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                net.minecraft.block.BlockState currentState = world.getBlockState(pos);
                if (currentState.isAir()) {
                    restoreBlockEntry(world, pos, entry, affectedChunks);
                    shownCount++;
                }
            }

            flushLightingForChunks(world, affectedChunks);

            // 更新 last（复用对象避免分配）
            last.clear();
            last.addAll(now);

            if (LOGGER.isDebugEnabled() && (shownCount > 0 || hiddenCount > 0)) {
                LOGGER.debug("[Visibility] 容器 {} 部分方块更新(first-sync): +{} -{} (目标显示 {})",
                        container.getName(), shownCount, hiddenCount, now.size());
            }
            return;
        }

        // 1) 隐藏：last - now
        int hiddenCount = 0;
        LongOpenHashSet affectedChunks = new LongOpenHashSet();
        for (var it = last.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (now.contains(posLong)) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(posLong);
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }
            net.minecraft.block.BlockState currentState = world.getBlockState(pos);
            if (!currentState.isAir()) {
                // 确保快照存在（防止外部系统把方块改成非 AIR 后丢失恢复数据）
                if (!store.contains(pos)) {
                    store.capture(world, pos);
                }

                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                if (be != null) {
                    world.removeBlockEntity(pos);
                }
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
                trackAffectedChunk(affectedChunks, chunkX, chunkZ);
                hiddenCount++;
            }
        }

        // 2) 显示：now - last
        int shownCount = 0;
        for (var it = now.iterator(); it.hasNext(); ) {
            long posLong = it.nextLong();
            if (last.contains(posLong)) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(posLong);

            BlockStore.Entry entry = store.get(pos);
            if (entry == null) {
                continue;
            }

            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            net.minecraft.block.BlockState currentState = world.getBlockState(pos);
            if (currentState.isAir()) {
                restoreBlockEntry(world, pos, entry, affectedChunks);
                shownCount++;
            }
        }

        if (hiddenCount > 0 || shownCount > 0) {
            flushLightingForChunks(world, affectedChunks);
        }

        // 更新 last（复用对象避免分配）
        last.clear();
        last.addAll(now);

        if (LOGGER.isDebugEnabled() && (shownCount > 0 || hiddenCount > 0)) {
            LOGGER.debug("[Visibility] 容器 {} 部分方块更新(delta): +{} -{} (目标显示 {})",
                    container.getName(), shownCount, hiddenCount, now.size());
        }
    }

    private void enqueueOrRun(VisibilityTask task) {
        if (task == null || task.isComplete()) {
            return;
        }
        cancelPendingTasks(task.containerId);
        int blockCount = task.remaining();
        ContainerVisibilityFeedback.onTaskQueued(task.containerName, task.actionLabel, blockCount);
        pendingTasks.addLast(task);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 的 {} 操作已入队: {} 个方块",
                    task.containerName, task.actionLabel, blockCount);
        }
    }

    private VisibilityTask createHideTask(Container container, ServerWorld world, BlockStore store, List<BlockPos> positions) {
        return new VisibilityTask(container, world, store, positions, TaskKind.HIDE, "隐藏");
    }

    private VisibilityTask createShowTask(Container container, ServerWorld world, BlockStore store, List<BlockPos> positions) {
        return new VisibilityTask(container, world, store, positions, TaskKind.SHOW, "显示");
    }

    private VisibilityTask createRestoreTask(Container container, ServerWorld world, BlockStore store, List<BlockPos> positions) {
        return new VisibilityTask(container, world, store, positions, TaskKind.RESTORE, "释放");
    }

    private void cancelPendingTasks(UUID containerId) {
        if (containerId == null || pendingTasks.isEmpty()) {
            return;
        }
        boolean removed = pendingTasks.removeIf(task -> containerId.equals(task.containerId));
        if (removed) {
            ContainerVisibilityFeedback.reset();
        }
    }

    private int getBlocksPerTickBudget() {
        int base;
        try {
            base = Math.max(1, com.director.config.DirectorConfig.getConfig().performance.containerWorldBlocksPerTick);
        } catch (Exception ignored) {
            base = 3000;
        }
        return Math.max(1, base * TimelinePlacementBudget.getVisibilityTickMultiplier());
    }
    
    /**
     * 检查容器是否有快照（用于判断容器是否在隐藏状态）
     * 
     * @param container 容器对象
     * @return 如果有快照返回 true，否则返回 false
     */
    public boolean hasSnapshot(Container container) {
        if (container == null) {
            return false;
        }
        return snapshots.containsKey(container.getId());
    }
    
    /**
     * 从容器的 blockStore 创建快照（用于时间线播放）
     * 
     * <p>当容器已经被隐藏（方块已经是 AIR），但 ContainerVisibilityService 中没有快照时，
     * 可以使用容器的 blockStore（创建容器时捕获的原始状态）来创建快照。
     * 
     * @param container 容器对象
     * @return 如果成功创建快照返回 true，否则返回 false
     */
    public boolean createSnapshotFromBlockStore(Container container) {
        if (container == null) {
            return false;
        }
        
        // 如果已经有快照，不需要创建
        if (snapshots.containsKey(container.getId())) {
            return true;
        }
        
        // 检查容器的 blockStore 是否有数据
        com.director.core.container.BlockStore containerBlockStore = container.getBlockStore();
        if (containerBlockStore == null || containerBlockStore.isEmpty()) {
            return false;
        }
        
        // 复制容器的 blockStore 到快照
        BlockStore snapshot = new BlockStore();
        for (Map.Entry<net.minecraft.util.math.BlockPos, com.director.core.container.BlockStore.Entry> entry : containerBlockStore.raw().entrySet()) {
            snapshot.add(entry.getKey(), new BlockStore.Entry(entry.getValue().state, entry.getValue().beNbt));
        }
        
        snapshots.put(container.getId(), snapshot);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 已从 blockStore 创建快照 (方块数: {})",
                container.getName(), snapshot.size());
        }
        
        return true;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static void trackAffectedChunk(LongOpenHashSet affectedChunks, int chunkX, int chunkZ) {
        if (affectedChunks != null) {
            affectedChunks.add(chunkKey(chunkX, chunkZ));
        }
    }

    private void flushLightingForChunks(ServerWorld world, LongOpenHashSet affectedChunks) {
        if (world == null || affectedChunks == null || affectedChunks.isEmpty()) {
            return;
        }
        try {
            world.getChunkManager().getLightingProvider().doLightUpdates();
        } catch (Exception e) {
            LOGGER.debug("[Visibility] 光照刷新跳过: {}", e.getMessage());
        }
    }

    private void restoreBlockEntry(ServerWorld world,
                                   BlockPos pos,
                                   BlockStore.Entry entry,
                                   LongOpenHashSet affectedChunks) {
        if (world == null || pos == null || entry == null || entry.state == null) {
            return;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        try {
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                world.getChunk(chunkX, chunkZ);
            }
        } catch (Exception ignored) {
        }
        if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        world.setBlockState(pos, entry.state, ContainerBlockUpdateFlags.BATCH);
        trackAffectedChunk(affectedChunks, chunkX, chunkZ);

        if (entry.beNbt == null) {
            return;
        }

        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        BlockEntityNbtIO.restore(be, entry.beNbt, world.getRegistryManager());
    }

    /**
     * 用容器持久化的原始方块快照补齐运行时快照。
     *
     * <p>大容器经常跨很多区块。运行时隐藏时，未加载区块会被跳过；如果后续显示/释放只依赖这份
     * 不完整的内存快照，就会出现容器轮廓还在、真实方块回不来的情况。容器自身的 blockStore 是创建
     * 容器时记录的完整原始状态，因此这里把缺失位置补回来。
     */
    private void backfillSnapshotFromBlockStore(Container container, BlockStore snapshot) {
        if (container == null || snapshot == null) {
            return;
        }

        BlockStore source = container.getBlockStore();
        if (source == null || source.isEmpty()) {
            return;
        }

        int added = 0;
        for (Map.Entry<BlockPos, BlockStore.Entry> entry : source.raw().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockStore.Entry value = entry.getValue();
            if (pos == null || value == null || snapshot.contains(pos)) {
                continue;
            }
            snapshot.add(pos, new BlockStore.Entry(value.state, value.beNbt));
            added++;
        }

        if (added > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Visibility] 容器 {} 快照已从 blockStore 补齐 {} 个方块 (总数: {})",
                    container.getName(), added, snapshot.size());
        }
    }

    private boolean isChunkLoadedCached(ServerWorld world, Map<Long, Boolean> cache, int chunkX, int chunkZ) {
        long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        Boolean cached = cache.get(chunkKey);
        if (cached != null) {
            return cached;
        }
        boolean loaded = world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
        cache.put(chunkKey, loaded);
        return loaded;
    }

    private final class VisibilityTask {
        private final UUID containerId;
        private final String containerName;
        private final String actionLabel;
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

        private VisibilityTask(Container container,
                               ServerWorld world,
                               BlockStore store,
                               List<BlockPos> positions,
                               TaskKind kind,
                               String actionLabel) {
            this.containerId = container.getId();
            this.containerName = container.getName();
            this.world = world;
            this.store = store;
            this.fallbackStore = container.getBlockStore();
            this.positions = positions != null ? positions : java.util.Collections.emptyList();
            this.kind = kind;
            this.actionLabel = actionLabel;
            this.shown = lastShownBlocks.get(containerId);
        }

        int process(int budget) {
            int processed = 0;
            while (processed < budget && index < positions.size()) {
                BlockPos pos = positions.get(index++);
                processed++;
                if (pos == null) {
                    continue;
                }
                switch (kind) {
                    case HIDE -> hideOne(pos);
                    case SHOW -> showOne(pos);
                    case RESTORE -> restoreOne(pos);
                }
            }
            return processed;
        }

        private void hideOne(BlockPos pos) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (!isChunkLoadedCached(world, chunkLoadCache, chunkX, chunkZ)) {
                return;
            }
            if (!store.contains(pos)) {
                store.capture(world, pos);
            }
            net.minecraft.block.BlockState currentState = world.getBlockState(pos);
            if (currentState.isAir()) {
                return;
            }
            var blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                world.removeBlockEntity(pos);
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
            trackAffectedChunk(affectedChunks, chunkX, chunkZ);
            changed++;
        }

        private void showOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null) {
                return;
            }
            restoreBlockEntry(world, pos, entry, affectedChunks);
            changed++;
        }

        private void restoreOne(BlockPos pos) {
            BlockStore.Entry entry = store.get(pos);
            if (entry == null && fallbackStore != null) {
                entry = fallbackStore.get(pos);
            }
            if (entry == null) {
                return;
            }
            restoreBlockEntry(world, pos, entry, affectedChunks);
            store.remove(pos);
            if (shown != null) {
                shown.remove(pos.asLong());
            }
            changed++;
        }

        boolean isComplete() {
            return index >= positions.size();
        }

        int remaining() {
            return Math.max(0, positions.size() - index);
        }

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

    private enum TaskKind {
        HIDE,
        SHOW,
        RESTORE
    }
}

