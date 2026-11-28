package com.director.core.container.visibility;

import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.container.Container;
import com.director.core.visual.BlockRenderMode;
import com.director.timeline.model.TimelineClip;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.UUID;

/**
 * Client-side visual overrides, render entries and their frame journal.
 *
 * <p>Every stored position is qualified by a world registry key. In addition, timeline
 * writes record a container owner and reverse position indexes, so deletion and geometry
 * shrink can release historical coordinates without consulting the container registry or
 * the container's current block set.</p>
 */
public class BlockVisualStateManager {
    private static BlockVisualStateManager instance;

    /** A dimension-qualified block coordinate. A null level is retained for headless legacy APIs. */
    public record PositionKey(RegistryKey<World> level, BlockPos pos) {
        public PositionKey {
            pos = Objects.requireNonNull(pos, "pos");
        }
    }

    public static class BlockVisualState {
        private BlockRenderMode mode = BlockRenderMode.SOLID;
        private float alphaMultiplier = 1.0f;
        private Boolean depthWriteOverride = null;
        private boolean suppressWorldRendering = false;
        private boolean useOverlay = false;
        private int managedReferences;

        private void beforeMutation() {
            if (managedReferences > 0 && instance != null) {
                instance.rememberMutation(this);
                ClientSectionInvalidation.getInstance().invalidateAllVisual();
            }
        }

        public BlockVisualState() {
        }

        public BlockVisualState(BlockRenderMode mode, float alphaMultiplier, Boolean depthWriteOverride) {
            this(mode, alphaMultiplier, depthWriteOverride, false);
        }

        public BlockVisualState(BlockRenderMode mode, float alphaMultiplier, Boolean depthWriteOverride,
                                boolean suppressWorldRendering) {
            this.mode = mode != null ? mode : BlockRenderMode.SOLID;
            this.alphaMultiplier = Math.clamp(alphaMultiplier, 0f, 1f);
            this.depthWriteOverride = depthWriteOverride;
            this.suppressWorldRendering = suppressWorldRendering;
            this.useOverlay = mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        /** Backward-compatible constructor. */
        public BlockVisualState(boolean hidden, float alpha, boolean useOverlay) {
            if (hidden) {
                this.mode = BlockRenderMode.HIDDEN;
                this.alphaMultiplier = 0.0f;
            } else if (alpha >= 1.0f && !useOverlay) {
                // Keep the solid default.
            } else if (useOverlay) {
                this.mode = BlockRenderMode.GHOST_NO_DEPTH;
                this.alphaMultiplier = alpha;
            } else {
                this.alphaMultiplier = alpha;
            }
            this.useOverlay = useOverlay;
        }

        public BlockRenderMode getMode() {
            return mode;
        }

        public void setMode(BlockRenderMode mode) {
            BlockRenderMode next = mode != null ? mode : BlockRenderMode.SOLID;
            boolean overlay = next == BlockRenderMode.GHOST_WITH_DEPTH || next == BlockRenderMode.GHOST_NO_DEPTH;
            if (this.mode != next || this.useOverlay != overlay) beforeMutation();
            this.mode = next;
            this.useOverlay = overlay;
        }

        public float getAlphaMultiplier() {
            return alphaMultiplier;
        }

        public boolean shouldWriteDepth() {
            return Objects.requireNonNullElseGet(depthWriteOverride,
                    () -> mode == BlockRenderMode.SOLID || mode == BlockRenderMode.GHOST_WITH_DEPTH);
        }

        public boolean shouldSuppressWorldRendering() {
            return suppressWorldRendering;
        }

        public void setSuppressWorldRendering(boolean suppressWorldRendering) {
            if (this.suppressWorldRendering != suppressWorldRendering) beforeMutation();
            this.suppressWorldRendering = suppressWorldRendering;
        }

        public BlockVisualState withSuppressWorldRendering(boolean suppressWorldRendering) {
            BlockVisualState newState = new BlockVisualState(mode, alphaMultiplier, depthWriteOverride,
                    suppressWorldRendering);
            newState.useOverlay = this.useOverlay;
            return newState;
        }

        public boolean isHidden() {
            return mode == BlockRenderMode.HIDDEN;
        }

        public void setHidden(boolean hidden) {
            if (hidden ? mode != BlockRenderMode.HIDDEN || alphaMultiplier != 0 : mode == BlockRenderMode.HIDDEN) {
                beforeMutation();
            }
            if (hidden) {
                this.mode = BlockRenderMode.HIDDEN;
                this.alphaMultiplier = 0.0f;
            } else if (mode == BlockRenderMode.HIDDEN) {
                this.mode = BlockRenderMode.SOLID;
                this.alphaMultiplier = 1.0f;
            }
        }

        public float getAlpha() {
            return alphaMultiplier;
        }

        public void setAlpha(float alpha) {
            if (Float.compare(this.alphaMultiplier, Math.clamp(alpha, 0f, 1f)) != 0) beforeMutation();
            this.alphaMultiplier = Math.clamp(alpha, 0f, 1f);
        }

        public boolean shouldUseOverlay() {
            return useOverlay || mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        public boolean isGhostMode() {
            return mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        public boolean isTranslucentMode() {
            return useOverlay || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        public void setUseOverlay(boolean useOverlay) {
            if (this.useOverlay != useOverlay) beforeMutation();
            this.useOverlay = useOverlay;
        }

        public boolean isNormal() {
            return mode == BlockRenderMode.SOLID && alphaMultiplier >= 1.0f && !useOverlay;
        }

        public static BlockVisualState solid() {
            return new BlockVisualState(BlockRenderMode.SOLID, 1.0f, null);
        }

        public static BlockVisualState ghost(float alpha, boolean depthWrite) {
            return ghost(alpha, depthWrite, true);
        }

        public static BlockVisualState ghost(float alpha, boolean depthWrite, boolean suppressWorldRendering) {
            return new BlockVisualState(
                    depthWrite ? BlockRenderMode.GHOST_WITH_DEPTH : BlockRenderMode.GHOST_NO_DEPTH,
                    alpha, depthWrite, suppressWorldRendering);
        }

        public static BlockVisualState hidden() {
            return new BlockVisualState(BlockRenderMode.HIDDEN, 0.0f, false);
        }

        public static BlockVisualState normal() {
            return solid();
        }

        public static BlockVisualState translucent(float alpha) {
            return ghost(alpha, false);
        }
    }

    /** Immutable value before the first write at a position in this frame. */
    private record FrameState(boolean present, BlockRenderMode mode, float alpha, boolean overlay, boolean suppress) {
        static final FrameState ABSENT = new FrameState(false, null, 1, false, false);

        static FrameState of(BlockVisualState state) {
            return state == null ? ABSENT : new FrameState(true, state.getMode(), state.getAlpha(),
                    state.shouldUseOverlay(), state.shouldSuppressWorldRendering());
        }
    }

    private final Map<RegistryKey<World>, Map<BlockPos, BlockVisualState>> visualByLevel = new HashMap<>();
    private final Map<RegistryKey<World>, Map<BlockPos, RenderEntry>> renderByLevel = new HashMap<>();
    private final Map<RegistryKey<World>, Map<BlockPos, BlockVisualState>> animationBaseByLevel = new HashMap<>();
    private final Map<RegistryKey<World>, Set<BlockPos>> overlayByLevel = new HashMap<>();
    private final Map<RegistryKey<World>, Set<BlockPos>> overlayViewByLevel = new HashMap<>();
    private final Map<RegistryKey<World>, Set<BlockPos>> overlayRefreshByLevel = new HashMap<>();

    private Map<PositionKey, FrameState> frameBefore = new HashMap<>();
    private final Map<PositionKey, UUID> visualOwners = new HashMap<>();
    private final Map<PositionKey, UUID> renderOwners = new HashMap<>();
    private final Map<UUID, Set<PositionKey>> positionsByOwner = new HashMap<>();
    private final Map<UUID, Set<PositionKey>> renderPositionsByOwner = new HashMap<>();
    private final Map<UUID, ContainerCheckpoint> openCheckpoints = new HashMap<>();
    private RegistryKey<World> activeWorld = currentClientWorldKey();
    private boolean explicitWorldPending;
    private RegistryKey<World> explicitWorld;

    private BlockVisualStateManager() {
    }

    public static BlockVisualStateManager getInstance() {
        if (instance == null) instance = new BlockVisualStateManager();
        instance.syncWorld();
        return instance;
    }

    private static RegistryKey<World> currentClientWorldKey() {
        try {
            var world = MinecraftClient.getInstance().world;
            return world == null ? null : world.getRegistryKey();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<BlockPos, BlockVisualState> visuals(RegistryKey<World> level) {
        return visualByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private Map<BlockPos, RenderEntry> renders(RegistryKey<World> level) {
        return renderByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private Map<BlockPos, BlockVisualState> bases(RegistryKey<World> level) {
        return animationBaseByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private Set<BlockPos> overlays(RegistryKey<World> level) {
        return overlayByLevel.computeIfAbsent(level, ignored -> new HashSet<>());
    }

    private Set<BlockPos> overlayView(RegistryKey<World> level) {
        return overlayViewByLevel.computeIfAbsent(level, ignored -> Collections.unmodifiableSet(overlays(level)));
    }

    private Set<BlockPos> overlayRefresh(RegistryKey<World> level) {
        return overlayRefreshByLevel.computeIfAbsent(level, ignored -> new HashSet<>());
    }

    private void syncWorld() {
        RegistryKey<World> next = currentClientWorldKey();
        if (explicitWorldPending) {
            // WorldRenderer#setWorld runs at HEAD, before MinecraftClient.world is replaced.
            // Keep the explicit target until the client catches up (also supports headless tests).
            if (Objects.equals(next, explicitWorld)) explicitWorldPending = false;
            else return;
        }
        if (!Objects.equals(activeWorld, next)) {
            RegistryKey<World> previous = activeWorld;
            // Rollback while the old dimension is still active, then discard it. Otherwise
            // finishFrame could resurrect an old-world baseline after the handoff.
            activeWorld = next;
            for (var checkpoint : new ArrayList<>(openCheckpoints.values())) rollback(checkpoint);
            clearDimension(previous);
            frameBefore = new HashMap<>();
        }
    }

    /** Explicit world-handoff hook; clears the previous dimension instead of leaking coordinates. */
    public void onClientWorldChanged(RegistryKey<World> nextWorld) {
        if (Objects.equals(activeWorld, nextWorld) && Objects.equals(explicitWorld, nextWorld)) {
            explicitWorldPending = false;
            return;
        }
        for (var checkpoint : new ArrayList<>(openCheckpoints.values())) rollback(checkpoint);
        clearDimension(activeWorld);
        activeWorld = nextWorld;
        explicitWorld = nextWorld;
        explicitWorldPending = true;
        frameBefore = new HashMap<>();
    }

    private PositionKey key(RegistryKey<World> level, BlockPos pos) {
        return pos == null ? null : new PositionKey(level, pos);
    }

    private void rememberBefore(PositionKey key, BlockVisualState previous) {
        frameBefore.putIfAbsent(key, FrameState.of(previous));
    }

    private void rememberMutation(BlockVisualState state) {
        // Compatibility path for callers retaining and directly mutating an installed state.
        for (var levelEntry : visualByLevel.entrySet()) {
            for (var blockEntry : levelEntry.getValue().entrySet()) {
                if (blockEntry.getValue() == state) {
                    var key = key(levelEntry.getKey(), blockEntry.getKey());
                    rememberBefore(key, state);
                    overlayRefresh(levelEntry.getKey()).add(blockEntry.getKey());
                }
            }
        }
    }

    private void trackOwner(PositionKey key, UUID owner) {
        if (owner == null) return;
        visualOwners.put(key, owner);
        positionsByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
        var checkpoint = openCheckpoints.get(owner);
        if (checkpoint != null) checkpoint.touched.add(key);
    }

    private void trackRenderOwner(PositionKey key, UUID owner) {
        if (owner == null) return;
        renderOwners.put(key, owner);
        renderPositionsByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
        var checkpoint = openCheckpoints.get(owner);
        if (checkpoint != null) checkpoint.touched.add(key);
    }

    private void releaseOwner(PositionKey key, UUID owner, boolean requireMatch) {
        if (owner == null) return;
        if (requireMatch && !owner.equals(visualOwners.get(key))) return;
        visualOwners.remove(key);
        var positions = positionsByOwner.get(owner);
        if (positions != null) positions.remove(key);
        if (positions != null && positions.isEmpty()) positionsByOwner.remove(owner);
    }

    private void releaseRenderOwner(PositionKey key, UUID owner, boolean requireMatch) {
        if (owner == null) return;
        if (requireMatch && !owner.equals(renderOwners.get(key))) return;
        renderOwners.remove(key);
        var positions = renderPositionsByOwner.get(owner);
        if (positions != null) positions.remove(key);
        if (positions != null && positions.isEmpty()) renderPositionsByOwner.remove(owner);
    }

    private void invalidateVisual(PositionKey key) {
        if (Objects.equals(key.level(), activeWorld)) {
            ClientSectionInvalidation.getInstance().invalidateVisual(key.pos());
        }
    }

    private void updateOverlay(RegistryKey<World> level, BlockPos pos, BlockVisualState state) {
        if (state != null && state.shouldUseOverlay() && !state.isHidden()) overlays(level).add(pos);
        else overlays(level).remove(pos);
    }

    private void removeVisual(PositionKey key, UUID owner, boolean allowUnowned) {
        if (owner != null && !allowUnowned && !owner.equals(visualOwners.get(key))) return;
        if (owner != null && allowUnowned) {
            var currentOwner = visualOwners.get(key);
            if (currentOwner != null && !currentOwner.equals(owner)) return;
        }
        var levelMap = visualByLevel.get(key.level());
        var previous = levelMap == null ? null : levelMap.get(key.pos());
        if (previous != null) {
            rememberBefore(key, previous);
            previous.managedReferences--;
            levelMap.remove(key.pos());
            if (levelMap.isEmpty()) visualByLevel.remove(key.level());
            updateOverlay(key.level(), key.pos(), null);
            invalidateVisual(key);
        }
        releaseOwner(key, owner, false);
    }

    private void removeRender(PositionKey key, UUID owner, boolean allowUnowned) {
        if (owner != null && !allowUnowned && !owner.equals(renderOwners.get(key))) return;
        if (owner != null && allowUnowned) {
            var currentOwner = renderOwners.get(key);
            if (currentOwner != null && !currentOwner.equals(owner)) return;
        }
        var levelMap = renderByLevel.get(key.level());
        if (levelMap != null && levelMap.remove(key.pos()) != null && levelMap.isEmpty()) {
            renderByLevel.remove(key.level());
        }
        releaseRenderOwner(key, owner, false);
    }

    private void removeBase(PositionKey key, UUID owner) {
        if (owner != null && !owner.equals(visualOwners.get(key))) return;
        var levelMap = animationBaseByLevel.get(key.level());
        if (levelMap != null && levelMap.remove(key.pos()) != null && levelMap.isEmpty()) {
            animationBaseByLevel.remove(key.level());
        }
    }

    private void clearDimension(RegistryKey<World> level) {
        boolean hadVisuals = visualByLevel.containsKey(level);
        var visuals = visualByLevel.remove(level);
        if (visuals != null) {
            for (var state : visuals.values()) state.managedReferences--;
            if (Objects.equals(level, activeWorld)) ClientSectionInvalidation.getInstance().invalidateAllVisual();
        }
        renderByLevel.remove(level);
        animationBaseByLevel.remove(level);
        overlayByLevel.remove(level);
        overlayViewByLevel.remove(level);
        overlayRefreshByLevel.remove(level);
        frameBefore.keySet().removeIf(key -> Objects.equals(key.level(), level));
        visualOwners.keySet().removeIf(key -> Objects.equals(key.level(), level));
        renderOwners.keySet().removeIf(key -> Objects.equals(key.level(), level));
        for (var iterator = positionsByOwner.entrySet().iterator(); iterator.hasNext();) {
            var owner = iterator.next().getValue();
            owner.removeIf(key -> Objects.equals(key.level(), level));
            if (owner.isEmpty()) iterator.remove();
        }
        for (var iterator = renderPositionsByOwner.entrySet().iterator(); iterator.hasNext();) {
            var owner = iterator.next().getValue();
            owner.removeIf(key -> Objects.equals(key.level(), level));
            if (owner.isEmpty()) iterator.remove();
        }
        if (hadVisuals && !Objects.equals(level, activeWorld)) {
            // Old-dimension invalidation must never dirty a same-numbered section in the new world.
            overlayByLevel.remove(level);
        }
    }

    public void clearFrame() {
        syncWorld();
        for (var checkpoint : new ArrayList<>(openCheckpoints.values())) rollback(checkpoint);
        ClientSectionInvalidation.getInstance().invalidateAllVisual();
        for (var levelEntry : visualByLevel.entrySet()) {
            for (var blockEntry : levelEntry.getValue().entrySet()) {
                rememberBefore(key(levelEntry.getKey(), blockEntry.getKey()), blockEntry.getValue());
                blockEntry.getValue().managedReferences--;
            }
        }
        visualByLevel.clear();
        renderByLevel.clear();
        animationBaseByLevel.clear();
        overlayByLevel.clear();
        overlayViewByLevel.clear();
        overlayRefreshByLevel.clear();
        visualOwners.clear();
        renderOwners.clear();
        positionsByOwner.clear();
        renderPositionsByOwner.clear();
    }

    /** Restore last frame's animation baselines, then discard its render entries. */
    public void beginTimelineFrame() {
        syncWorld();
        for (var levelEntry : animationBaseByLevel.entrySet()) {
            for (var blockEntry : levelEntry.getValue().entrySet()) {
                setBlockState(key(levelEntry.getKey(), blockEntry.getKey()), blockEntry.getValue());
            }
        }
        animationBaseByLevel.clear();
        renderByLevel.clear();
        renderOwners.clear();
        renderPositionsByOwner.clear();
    }

    public void setAnimatedBlockState(BlockPos pos, BlockVisualState state) {
        setAnimatedBlockState(pos, state, null, activeWorld);
    }

    public void setAnimatedBlockState(BlockPos pos, BlockVisualState state, UUID owner, RegistryKey<World> level) {
        syncWorld();
        if (pos == null || state == null) return;
        var key = key(level, pos);
        bases(level).putIfAbsent(pos, getBlockState(level, pos));
        setBlockState(key, state, owner);
    }

    public boolean hasActiveOverrides() {
        syncWorld();
        return visualByLevel.containsKey(activeWorld) && !visualByLevel.get(activeWorld).isEmpty();
    }

    public boolean hasActiveOverrides(RegistryKey<World> level) {
        syncWorld();
        return visualByLevel.containsKey(level) && !visualByLevel.get(level).isEmpty();
    }

    /** Release all historical coordinates owned by a container, including removed geometry. */
    public void clearOverridesFor(Container container) {
        if (container == null) return;
        syncWorld();
        UUID owner = container.getId();
        RegistryKey<World> level = container.getLevelKey();
        var keys = new HashSet<>(positionsByOwner.getOrDefault(owner, Collections.emptySet()));
        keys.addAll(renderPositionsByOwner.getOrDefault(owner, Collections.emptySet()));
        for (BlockPos pos : container.getBlocksView()) keys.add(key(level, pos));
        clearOwnedKeys(owner, keys);
    }

    /** Registry-independent deletion fallback for an object which is already unregistered. */
    public void clearOverridesFor(UUID owner) {
        if (owner == null) return;
        syncWorld();
        var keys = new HashSet<>(positionsByOwner.getOrDefault(owner, Collections.emptySet()));
        keys.addAll(renderPositionsByOwner.getOrDefault(owner, Collections.emptySet()));
        clearOwnedKeys(owner, keys);
    }

    private void clearOwnedKeys(UUID owner, Set<PositionKey> keys) {
        for (var key : keys) {
            removeVisual(key, owner, true);
            removeRender(key, owner, true);
            var bases = animationBaseByLevel.get(key.level());
            if (bases != null && bases.remove(key.pos()) != null && bases.isEmpty()) {
                animationBaseByLevel.remove(key.level());
            }
            overlays(key.level()).remove(key.pos());
            overlayRefresh(key.level()).remove(key.pos());
        }
    }

    /** Geometry-shrink path: release only the positions which left this owner's domain. */
    public void releaseContainerPositions(UUID owner, RegistryKey<World> level, Collection<BlockPos> positions) {
        if (owner == null || level == null || positions == null || positions.isEmpty()) return;
        syncWorld();
        for (BlockPos pos : positions) {
            var key = key(level, pos);
            // Base ownership follows the visual owner, so remove it before releasing that index.
            removeBase(key, owner);
            removeVisual(key, owner, true);
            removeRender(key, owner, true);
            overlays(level).remove(pos);
            overlayRefresh(level).remove(pos);
        }
    }

    public void clearRenderEntriesFor(UUID owner) {
        syncWorld();
        if (owner == null) return;
        for (var key : new HashSet<>(renderPositionsByOwner.getOrDefault(owner, Collections.emptySet()))) {
            removeRender(key, owner, false);
        }
    }

    public void refreshRenderEntryVisualStates(UUID owner, RegistryKey<World> level, BlockVisualState state) {
        syncWorld();
        if (owner == null || level == null || state == null) return;
        for (var key : new HashSet<>(renderPositionsByOwner.getOrDefault(owner, Collections.emptySet()))) {
            if (!Objects.equals(key.level(), level) || !owner.equals(renderOwners.get(key))) continue;
            var entry = renderByLevel.getOrDefault(level, Collections.emptyMap()).get(key.pos());
            if (entry != null) {
                renderByLevel.get(level).put(key.pos(), new RenderEntry(entry.pos(), entry.blockState(), state,
                        entry.clip(), entry.seqEntry(), entry.overrideSnapshot()));
            }
        }
    }

    public void setBlockState(BlockPos pos, BlockVisualState state) {
        syncWorld();
        if (pos == null) return;
        setBlockState(key(activeWorld, pos), state, null);
    }

    public void setBlockState(PositionKey key, BlockVisualState state) {
        syncWorld();
        setBlockState(key, state, null);
    }

    public void setBlockState(PositionKey key, BlockVisualState state, UUID owner) {
        syncWorld();
        if (key == null || key.pos() == null || state == null) return;
        var levelMap = visuals(key.level());
        var previous = levelMap.get(key.pos());
        boolean same = sameVisualState(previous, state);
        if (previous == null || !same) rememberBefore(key, previous);
        if (previous != state) {
            if (previous != null) previous.managedReferences--;
            state.managedReferences++;
            levelMap.put(key.pos(), state);
        }
        if (!same) invalidateVisual(key);
        updateOverlay(key.level(), key.pos(), state);
        overlayRefresh(key.level()).remove(key.pos());
        trackOwner(key, owner);
    }

    private static boolean sameVisualState(BlockVisualState previous, BlockVisualState next) {
        if (previous == next) return true;
        if (previous == null) return next.isNormal() && !next.shouldSuppressWorldRendering() && next.shouldWriteDepth();
        return previous.mode == next.mode && Float.compare(previous.alphaMultiplier, next.alphaMultiplier) == 0
                && previous.useOverlay == next.useOverlay && previous.shouldWriteDepth() == next.shouldWriteDepth()
                && previous.suppressWorldRendering == next.suppressWorldRendering;
    }

    public void setBlocksState(Collection<BlockPos> blocks, BlockVisualState state) {
        setBlocksState(blocks, state, null, activeWorld);
    }

    public void setBlocksState(Collection<BlockPos> blocks, BlockVisualState state, UUID owner,
                               RegistryKey<World> level) {
        if (blocks == null || state == null) return;
        for (BlockPos pos : blocks) if (pos != null) setBlockState(key(level, pos), state, owner);
    }

    public BlockVisualState getBlockState(BlockPos pos) {
        syncWorld();
        return getBlockState(activeWorld, pos);
    }

    public BlockVisualState getBlockState(RegistryKey<World> level, BlockPos pos) {
        syncWorld();
        if (pos == null) return BlockVisualState.normal();
        var state = visualByLevel.getOrDefault(level, Collections.emptyMap()).get(pos);
        return state != null ? state : BlockVisualState.normal();
    }

    public BlockVisualState getBlockState(PositionKey key) {
        syncWorld();
        return key == null ? BlockVisualState.normal() : getBlockState(key.level(), key.pos());
    }

    public boolean isBlockHidden(BlockPos pos) {
        return getBlockState(pos).isHidden();
    }

    public float getBlockAlpha(BlockPos pos) {
        return getBlockState(pos).getAlpha();
    }

    public boolean shouldUseOverlay(BlockPos pos) {
        return getBlockState(pos).shouldUseOverlay();
    }

    public void addRenderEntry(RenderEntry entry) {
        syncWorld();
        addRenderEntry(entry, null, activeWorld);
    }

    public void addRenderEntry(RenderEntry entry, UUID owner, RegistryKey<World> level) {
        syncWorld();
        if (entry == null || entry.pos() == null || owner != null && level == null) return;
        // Owner-aware timeline writes must name their dimension. The legacy unowned API may
        // still use a null key in headless tests before a client world exists.
        RegistryKey<World> targetLevel = level != null ? level : activeWorld;
        var key = key(targetLevel, entry.pos());
        renders(targetLevel).put(entry.pos(), entry);
        trackRenderOwner(key, owner);
    }

    public void addRenderEntries(UUID owner, RegistryKey<World> level, Collection<RenderEntry> entries) {
        if (entries == null || owner != null && level == null) return;
        for (var entry : entries) addRenderEntry(entry, owner, level);
    }

    /** Only entries belonging to the active client dimension are exposed to the renderer. */
    public Collection<RenderEntry> getRenderEntries() {
        syncWorld();
        return renderByLevel.getOrDefault(activeWorld, Collections.emptyMap()).values();
    }

    public Collection<RenderEntry> getRenderEntries(RegistryKey<World> level) {
        syncWorld();
        return renderByLevel.getOrDefault(level, Collections.emptyMap()).values();
    }

    public boolean hasRenderEntry(BlockPos pos) {
        syncWorld();
        return renderByLevel.getOrDefault(activeWorld, Collections.emptyMap()).containsKey(pos);
    }

    public boolean hasRenderEntry(PositionKey key) {
        syncWorld();
        return key != null && renderByLevel.getOrDefault(key.level(), Collections.emptyMap()).containsKey(key.pos());
    }

    public Set<BlockPos> getOverlayBlocks() {
        syncWorld();
        return getOverlayBlocks(activeWorld);
    }

    public Set<BlockPos> getOverlayBlocks(RegistryKey<World> level) {
        syncWorld();
        var refresh = overlayRefresh(level);
        if (!refresh.isEmpty()) {
            for (var pos : refresh) updateOverlay(level, pos, getBlockState(level, pos));
            refresh.clear();
        }
        return overlayView(level);
    }

    public Set<BlockPos> getSpecialBlocks() {
        syncWorld();
        return getSpecialBlocks(activeWorld);
    }

    public Set<BlockPos> getSpecialBlocks(RegistryKey<World> level) {
        syncWorld();
        Set<BlockPos> result = new HashSet<>();
        for (var entry : visualByLevel.getOrDefault(level, Collections.emptyMap()).entrySet()) {
            if (!entry.getValue().isNormal()) result.add(entry.getKey());
        }
        return result;
    }

    public void finishFrame() {
        syncWorld();
        for (var checkpoint : new ArrayList<>(openCheckpoints.values())) rollback(checkpoint);
        if (!frameBefore.isEmpty()) frameBefore = new HashMap<>();
    }

    public boolean hasWorldSuppressionChanged(BlockPos pos) {
        syncWorld();
        return hasWorldSuppressionChanged(key(activeWorld, pos));
    }

    public boolean hasWorldSuppressionChanged(PositionKey key) {
        syncWorld();
        var current = key == null ? null : getBlockState(key);
        var previous = key == null ? null : frameBefore.get(key);
        if (previous == null) return false;
        return (current != null && current.shouldSuppressWorldRendering()) != previous.suppress();
    }

    public boolean hasStateChanged(BlockPos pos) {
        syncWorld();
        return hasStateChanged(key(activeWorld, pos));
    }

    public boolean hasStateChanged(PositionKey key) {
        syncWorld();
        if (key == null) return false;
        BlockVisualState current = getBlockState(key);
        FrameState last = frameBefore.get(key);
        if (last == null) return false;
        if (current == null) return last.present();
        if (!last.present()) return true;
        return Math.abs(current.getAlpha() - last.alpha()) > 0.01f
                || current.shouldUseOverlay() != last.overlay() || current.getMode() != last.mode()
                || current.shouldSuppressWorldRendering() != last.suppress();
    }

    public Set<BlockPos> getChangedBlocks() {
        syncWorld();
        Set<BlockPos> changed = new HashSet<>();
        for (var key : frameBefore.keySet()) {
            if (Objects.equals(key.level(), activeWorld) && hasStateChanged(key)) changed.add(key.pos());
        }
        return changed;
    }

    public Set<PositionKey> getChangedPositions() {
        syncWorld();
        Set<PositionKey> changed = new HashSet<>();
        for (var key : frameBefore.keySet()) if (hasStateChanged(key)) changed.add(key);
        return changed;
    }

    public int getChangeCandidateCount() {
        syncWorld();
        return frameBefore.size();
    }

    public void clearAll() {
        syncWorld();
        for (var checkpoint : new ArrayList<>(openCheckpoints.values())) rollback(checkpoint);
        ClientSectionInvalidation.getInstance().invalidateAllVisual();
        for (var states : visualByLevel.values()) for (var state : states.values()) state.managedReferences--;
        visualByLevel.clear();
        renderByLevel.clear();
        animationBaseByLevel.clear();
        overlayByLevel.clear();
        overlayViewByLevel.clear();
        overlayRefreshByLevel.clear();
        visualOwners.clear();
        renderOwners.clear();
        positionsByOwner.clear();
        renderPositionsByOwner.clear();
        frameBefore = new HashMap<>();
    }

    public void setContainerOpacity(UUID containerId, Collection<BlockPos> blocks, float alpha, boolean useOverlay) {
        setContainerOpacity(containerId, blocks, alpha, useOverlay, activeWorld);
    }

    public void setContainerOpacity(UUID containerId, Collection<BlockPos> blocks, float alpha, boolean useOverlay,
                                    RegistryKey<World> level) {
        if (blocks == null || blocks.isEmpty()) return;
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        setBlocksState(blocks, state, containerId, level);
    }

    public void setContainerBlocksOpacity(Container container, Collection<BlockPos> blockPositions,
                                         float alpha, boolean useOverlay) {
        if (container == null || blockPositions == null || blockPositions.isEmpty() || container.getLevelKey() == null) {
            return;
        }
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        Set<BlockPos> containerBlocks = container.getBlocksView();
        for (BlockPos pos : blockPositions) {
            if (pos != null && containerBlocks.contains(pos)) {
                setBlockState(key(container.getLevelKey(), pos), state, container.getId());
            }
        }
    }

    public void hideContainer(Container container) {
        if (container == null || container.getLevelKey() == null) return;
        setBlocksState(container.getBlocksView(), BlockVisualState.hidden(), container.getId(), container.getLevelKey());
    }

    public void hideContainerBlocks(Container container, Collection<BlockPos> blockPositions) {
        if (container == null || blockPositions == null || blockPositions.isEmpty() || container.getLevelKey() == null) {
            return;
        }
        Set<BlockPos> containerBlocks = container.getBlocksView();
        for (BlockPos pos : blockPositions) {
            if (pos != null && containerBlocks.contains(pos)) {
                setBlockState(key(container.getLevelKey(), pos), BlockVisualState.hidden(), container.getId());
            }
        }
    }

    public void restoreContainer(Container container) {
        if (container == null || container.getLevelKey() == null) return;
        setBlocksState(container.getBlocksView(), BlockVisualState.normal(), container.getId(), container.getLevelKey());
    }

    public void setBlockOpacity(BlockPos pos, float alpha, boolean useOverlay) {
        if (pos == null) return;
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        setBlockState(pos, state);
    }

    public void hideBlock(BlockPos pos) {
        setBlockState(pos, BlockVisualState.hidden());
    }

    public void restoreBlock(BlockPos pos) {
        setBlockState(pos, BlockVisualState.normal());
    }

    public Set<BlockPos> getContainerSpecialBlocks(Container container) {
        if (container == null) return Collections.emptySet();
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : container.getBlocksView()) {
            if (!getBlockState(container.getLevelKey(), pos).isNormal()) result.add(pos);
        }
        return result;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /**
     * Per-container rollback unit. Baselines are captured before beginTimelineFrame so a
     * failed container can retain exactly its previous stable visual/render state while
     * other containers commit normally.
     */
    public final class ContainerCheckpoint {
        private final UUID owner;
        private final Set<PositionKey> baselineKeys;
        private final Map<PositionKey, BlockVisualState> visuals = new HashMap<>();
        private final Map<PositionKey, BlockVisualState> bases = new HashMap<>();
        private final Map<PositionKey, RenderEntry> entries = new HashMap<>();
        private final Set<PositionKey> touched = new HashSet<>();

        private ContainerCheckpoint(UUID owner) {
            this.owner = owner;
            var keys = new HashSet<>(positionsByOwner.getOrDefault(owner, Collections.emptySet()));
            keys.addAll(renderPositionsByOwner.getOrDefault(owner, Collections.emptySet()));
            baselineKeys = keys;
            for (var key : keys) {
                var visual = visualByLevel.getOrDefault(key.level(), Collections.emptyMap()).get(key.pos());
                visuals.put(key, visual);
                var base = animationBaseByLevel.getOrDefault(key.level(), Collections.emptyMap()).get(key.pos());
                if (base != null) bases.put(key, base);
                var entry = renderByLevel.getOrDefault(key.level(), Collections.emptyMap()).get(key.pos());
                if (entry != null) entries.put(key, entry);
            }
        }
    }

    public ContainerCheckpoint beginContainerUpdate(UUID owner) {
        syncWorld();
        if (owner == null) throw new IllegalArgumentException("owner");
        var previous = openCheckpoints.remove(owner);
        if (previous != null) rollback(previous);
        var checkpoint = new ContainerCheckpoint(owner);
        openCheckpoints.put(owner, checkpoint);
        return checkpoint;
    }

    public void commitContainerUpdate(UUID owner) {
        openCheckpoints.remove(owner);
    }

    public void rollbackContainerUpdate(UUID owner) {
        var checkpoint = openCheckpoints.remove(owner);
        if (checkpoint != null) rollback(checkpoint);
    }

    private void rollback(ContainerCheckpoint checkpoint) {
        openCheckpoints.remove(checkpoint.owner, checkpoint);
        var keys = new HashSet<>(checkpoint.baselineKeys);
        keys.addAll(checkpoint.touched);
        for (var key : keys) {
            var currentVisualOwner = visualOwners.get(key);
            boolean ownsVisual = currentVisualOwner == null || currentVisualOwner.equals(checkpoint.owner);
            if (ownsVisual) {
                var baseline = checkpoint.visuals.get(key);
                if (checkpoint.baselineKeys.contains(key)) {
                    if (baseline == null) removeVisual(key, checkpoint.owner, false);
                    else setBlockState(key, baseline, checkpoint.owner);
                } else {
                    removeVisual(key, checkpoint.owner, false);
                }
            }
            var currentRenderOwner = renderOwners.get(key);
            if (currentRenderOwner == null || currentRenderOwner.equals(checkpoint.owner)) {
                var entry = checkpoint.entries.get(key);
                if (checkpoint.baselineKeys.contains(key)) {
                    if (entry == null) removeRender(key, checkpoint.owner, false);
                    else addRenderEntry(entry, checkpoint.owner, key.level());
                } else {
                    removeRender(key, checkpoint.owner, false);
                }
            }
            if (ownsVisual) {
                var baseMap = animationBaseByLevel.get(key.level());
                var baselineBase = checkpoint.bases.get(key);
                if (baselineBase != null) {
                    animationBaseByLevel.computeIfAbsent(key.level(), ignored -> new HashMap<>())
                            .put(key.pos(), baselineBase);
                } else if (baseMap != null) {
                    baseMap.remove(key.pos());
                    if (baseMap.isEmpty()) animationBaseByLevel.remove(key.level());
                }
            }
        }
    }

    public record RenderEntry(BlockPos pos, BlockState blockState, BlockVisualState visualState, TimelineClip clip,
                              BlockSequenceEntry seqEntry, BlockAnimationSnapshot overrideSnapshot) {
        public RenderEntry {
            visualState = visualState != null ? visualState : BlockVisualState.normal();
        }

        public boolean hasOverrideSnapshot() {
            return overrideSnapshot != null;
        }
    }
}
