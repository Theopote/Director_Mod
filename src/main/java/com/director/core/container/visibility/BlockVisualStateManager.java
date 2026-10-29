package com.director.core.container.visibility;

import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.container.Container;
import com.director.core.visual.BlockRenderMode;
import com.director.timeline.model.TimelineClip;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.UUID;

/**
 * 方块视觉状态管理器
 * 
 * <p>管理每个方块在当前帧的视觉状态（透明度、隐藏等）
 * 用于实现时间线播放时的遮挡淡化和动画效果
 * 
 * <p>核心概念：
 * <ul>
 *   <li>真实方块层：永久存在，不透明，普通 Minecraft 渲染</li>
 *   <li>视觉覆盖层：可控制透明度、隐藏、特效的渲染层</li>
 * </ul>
 */
public class BlockVisualStateManager {
    private static BlockVisualStateManager instance;
    
    /**
     * 方块视觉状态（扩展版，支持渲染模式和深度控制）
     * 
     * <p>封装方块的最终视觉状态，包括：
     * <ul>
     *   <li>渲染模式（SOLID / GHOST_WITH_DEPTH / GHOST_NO_DEPTH / HIDDEN）</li>
     *   <li>透明度乘数（与动画 alpha 相乘）</li>
     *   <li>深度写入覆盖（可选，null 表示使用 mode 的默认值）</li>
     * </ul>
     */
    public static class BlockVisualState {
        private BlockRenderMode mode = BlockRenderMode.SOLID;
        private float alphaMultiplier = 1.0f;  // 与动画 alpha 相乘
        private Boolean depthWriteOverride = null; // null = 交给 mode 决定；true/false = 强制
        private boolean suppressWorldRendering = false; // 是否阻止 Minecraft 世界方块渲染
        
        // 向后兼容字段
        private boolean useOverlay = false;  // 是否使用覆盖层渲染（用于半透明效果）
        private int managedReferences;

        private void beforeMutation() {
            if (managedReferences > 0 && instance != null) {
                instance.rememberMutation(this);
                ClientSectionInvalidation.getInstance().invalidateAllVisual();
            }
        }

        public BlockVisualState() {
        }
        
        /**
         * 创建视觉状态（新 API）
         * 
         * @param mode 渲染模式
         * @param alphaMultiplier 透明度乘数 [0..1]
         * @param depthWriteOverride 深度写入覆盖（null = 使用 mode 默认值）
         */
        public BlockVisualState(BlockRenderMode mode, float alphaMultiplier, Boolean depthWriteOverride) {
            this(mode, alphaMultiplier, depthWriteOverride, false);
        }
        
        /**
         * 创建视觉状态（完整 API）
         * 
         * @param mode 渲染模式
         * @param alphaMultiplier 透明度乘数 [0..1]
         * @param depthWriteOverride 深度写入覆盖（null = 使用 mode 默认值）
         * @param suppressWorldRendering 是否阻止 Minecraft 世界方块渲染
         */
        public BlockVisualState(BlockRenderMode mode, float alphaMultiplier, Boolean depthWriteOverride, boolean suppressWorldRendering) {
            this.mode = mode != null ? mode : BlockRenderMode.SOLID;
            this.alphaMultiplier = Math.clamp(alphaMultiplier, 0f, 1f);
            this.depthWriteOverride = depthWriteOverride;
            this.suppressWorldRendering = suppressWorldRendering;
            this.useOverlay = (mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH);
        }

        /**
         * 向后兼容构造函数
         */
        public BlockVisualState(boolean hidden, float alpha, boolean useOverlay) {
            if (hidden) {
                this.mode = BlockRenderMode.HIDDEN;
                this.alphaMultiplier = 0.0f;
            } else if (alpha >= 1.0f && !useOverlay) {
            } else if (useOverlay) {
                this.mode = BlockRenderMode.GHOST_NO_DEPTH; // 默认不写深度
                this.alphaMultiplier = alpha;
            } else {
                this.alphaMultiplier = alpha;
            }
            this.useOverlay = useOverlay;
        }

        // ========== 新 API ==========
        
        public BlockRenderMode getMode() {
            return mode;
        }
        
        public void setMode(BlockRenderMode mode) {
            BlockRenderMode next = mode != null ? mode : BlockRenderMode.SOLID;
            boolean overlay = next == BlockRenderMode.GHOST_WITH_DEPTH || next == BlockRenderMode.GHOST_NO_DEPTH;
            if (this.mode != next || this.useOverlay != overlay) {
                beforeMutation();
            }
            this.mode = next;
            this.useOverlay = overlay;
        }
        
        public float getAlphaMultiplier() {
            return alphaMultiplier;
        }

        
        /**
         * 计算深度写入策略
         * 
         * @return 是否应该写入深度
         */
        public boolean shouldWriteDepth() {
            return Objects.requireNonNullElseGet(depthWriteOverride, () -> (mode == BlockRenderMode.SOLID || mode == BlockRenderMode.GHOST_WITH_DEPTH));
            // 默认策略：SOLID / GHOST_WITH_DEPTH 写深度；GHOST_NO_DEPTH / HIDDEN 不写
        }
        
        /**
         * 检查是否应该阻止 Minecraft 世界方块渲染
         * 
         * <p>当返回 true 时，Minecraft 的世界渲染应该被跳过，
         * 由 BlockAnimationRenderer 接管渲染（透明/ghost 版本）。
         * 
         * @return 是否应该阻止世界方块渲染
         */
        public boolean shouldSuppressWorldRendering() {
            return suppressWorldRendering;
        }

        /**
         * 设置是否阻止世界方块渲染
         *
         * @param suppressWorldRendering 是否阻止世界方块渲染
         */
        public void setSuppressWorldRendering(boolean suppressWorldRendering) {
            if (this.suppressWorldRendering != suppressWorldRendering) {
                beforeMutation();
            }
            this.suppressWorldRendering = suppressWorldRendering;
        }

        /**
         * 创建一个新的视觉状态，并设置 suppressWorldRendering 标志
         * 
         * @param suppressWorldRendering 是否阻止世界方块渲染
         * @return 新的视觉状态实例
         */
        public BlockVisualState withSuppressWorldRendering(boolean suppressWorldRendering) {
            BlockVisualState newState = new BlockVisualState(
                    this.mode,
                    this.alphaMultiplier,
                    this.depthWriteOverride,
                    suppressWorldRendering
            );
            newState.useOverlay = this.useOverlay;
            return newState;
        }
        
        // ========== 向后兼容 API ==========
        
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
            if (Float.compare(this.alphaMultiplier, Math.clamp(alpha, 0f, 1f)) != 0) {
                beforeMutation();
            }
            this.alphaMultiplier = Math.clamp(alpha, 0f, 1f);
        }
        
        public boolean shouldUseOverlay() {
            return useOverlay || (mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH);
        }

        /**
         * 是否处于幽灵（遮挡透明）模式
         */
        public boolean isGhostMode() {
            return mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        /**
         * 是否处于半透明覆盖模式
         */
        public boolean isTranslucentMode() {
            return useOverlay || mode == BlockRenderMode.GHOST_NO_DEPTH;
        }

        public void setUseOverlay(boolean useOverlay) {
            if (this.useOverlay != useOverlay) {
                beforeMutation();
            }
            this.useOverlay = useOverlay;
        }

        /**
         * 检查是否为正常状态（不隐藏，完全不透明，不使用覆盖层）
         */
        public boolean isNormal() {
            return mode == BlockRenderMode.SOLID && alphaMultiplier >= 1.0f && !useOverlay;
        }
        
        // ========== 静态工厂方法 ==========
        
        /**
         * 创建实体方块状态（完全不透明，写入深度）
         */
        public static BlockVisualState solid() {
            return new BlockVisualState(BlockRenderMode.SOLID, 1.0f, null);
        }

        /**
         * 创建半透明幽灵状态
         *
         * @param alpha 透明度 [0..1]
         * @param depthWrite 是否写入深度
         */
        public static BlockVisualState ghost(float alpha, boolean depthWrite) {
            return ghost(alpha, depthWrite, true); // 默认阻止世界渲染
        }
        
        /**
         * 创建半透明幽灵状态（完整参数）
         * 
         * @param alpha 透明度 [0..1]
         * @param depthWrite 是否写入深度
         * @param suppressWorldRendering 是否阻止 Minecraft 世界方块渲染
         */
        public static BlockVisualState ghost(float alpha, boolean depthWrite, boolean suppressWorldRendering) {
            return new BlockVisualState(
                    depthWrite ? BlockRenderMode.GHOST_WITH_DEPTH : BlockRenderMode.GHOST_NO_DEPTH,
                    alpha,
                    depthWrite,
                    suppressWorldRendering
            );
        }
        
        /**
         * 创建隐藏状态
         */
        public static BlockVisualState hidden() {
            return new BlockVisualState(BlockRenderMode.HIDDEN, 0.0f, false);
        }
        
        /**
         * 创建默认正常状态（向后兼容）
         */
        public static BlockVisualState normal() {
            return solid();
        }

        /**
         * 创建半透明状态（向后兼容）
         */
        public static BlockVisualState translucent(float alpha) {
            return ghost(alpha, false); // 默认不写深度
        }
    }
    
    /**
     * 当前帧的方块视觉状态映射
     * BlockPos -> BlockVisualState
     */
    private final Map<BlockPos, BlockVisualState> visualOverrides = new HashMap<>();
    private final Map<BlockPos, RenderEntry> renderEntries = new HashMap<>();
    
    /** Immutable values before the first write this frame. Untouched positions need no snapshot. */
    private record FrameState(boolean present, BlockRenderMode mode, float alpha, boolean overlay, boolean suppress) {
        static final FrameState ABSENT = new FrameState(false, null, 1, false, false);
        static FrameState of(BlockVisualState state) {
            return state == null ? ABSENT : new FrameState(true, state.getMode(), state.getAlpha(),
                    state.shouldUseOverlay(), state.shouldSuppressWorldRendering());
        }
    }
    private Map<BlockPos, FrameState> frameBefore = new HashMap<>();

    private void rememberBefore(BlockPos pos, BlockVisualState previous) {
        if (!frameBefore.containsKey(pos)) frameBefore.put(pos, FrameState.of(previous));
    }

    private void rememberMutation(BlockVisualState state) {
        // Direct mutation of an installed shared object is rare. Only that compatibility path
        // scans ownership; ordinary replacement writes and newly constructed states do not.
        for (var entry : visualOverrides.entrySet()) if (entry.getValue() == state) {
            rememberBefore(entry.getKey(), state);
            overlayRefresh.add(entry.getKey());
        }
    }
    
    private BlockVisualStateManager() {
    }
    
    /**
     * 获取单例实例
     */
    public static BlockVisualStateManager getInstance() {
        if (instance == null) {
            instance = new BlockVisualStateManager();
        }
        return instance;
    }
    
    /**
     * 清除当前帧的所有视觉状态（每帧开始时调用）
     */
    public void clearFrame() {
        ClientSectionInvalidation.getInstance().invalidateAllVisual();
        for (var entry : visualOverrides.entrySet()) {
            rememberBefore(entry.getKey(), entry.getValue());
            entry.getValue().managedReferences--;
        }
        animationBaseStates.clear();
        visualOverrides.clear();
        renderEntries.clear();
        cachedOverlayBlocks.clear();
        overlayRefresh = new HashSet<>();
    }

    private Map<BlockPos, BlockVisualState> animationBaseStates = new HashMap<>();

    /** Keep persistent visibility deltas, but remove last frame's temporary animation suppression. */
    public void beginTimelineFrame() {
        for (var entry : animationBaseStates.entrySet()) setBlockState(entry.getKey(), entry.getValue());
        if (!animationBaseStates.isEmpty()) animationBaseStates = new HashMap<>();
        renderEntries.clear();
    }

    public void setAnimatedBlockState(BlockPos pos, BlockVisualState state) {
        if (pos == null || state == null) return;
        if (!animationBaseStates.containsKey(pos)) animationBaseStates.put(pos, getBlockState(pos));
        setBlockState(pos, state);
    }

    /** 是否存在需要 Mixin / 覆盖层处理的方块状态。 */
    public boolean hasActiveOverrides() {
        return !visualOverrides.isEmpty();
    }

    /**
     * 移除指定容器方块的视觉覆盖（时间线预览模式下世界方块由 Minecraft 直接渲染，不需要覆盖层）。
     */
    public void clearOverridesFor(Container container) {
        if (container == null) {
            return;
        }
        for (BlockPos pos : container.getBlocksView()) {
            if (pos == null) {
                continue;
            }
            var previous = visualOverrides.get(pos);
            if (previous != null) {
                rememberBefore(pos, previous);
                visualOverrides.remove(pos);
                previous.managedReferences--;
                ClientSectionInvalidation.getInstance().invalidateVisual(pos);
            }
            animationBaseStates.remove(pos);
            renderEntries.remove(pos);
            cachedOverlayBlocks.remove(pos);
            overlayRefresh.remove(pos);
        }
    }

    /**
     * 设置方块的视觉状态
     *
     * @param pos 方块位置
     * @param state 视觉状态
     */
    public void setBlockState(BlockPos pos, BlockVisualState state) {
        if (pos == null || state == null) {
            return;
        }
        var previous = visualOverrides.get(pos);
        boolean same = sameVisualState(previous, state);
        if (previous == null || !same) rememberBefore(pos, previous);
        if (previous != state) {
            if (previous != null) previous.managedReferences--;
            state.managedReferences++;
            visualOverrides.put(pos, state);
        }
        if (!same) ClientSectionInvalidation.getInstance().invalidateVisual(pos);
        updateOverlay(pos, state);
        overlayRefresh.remove(pos);
    }

    private static boolean sameVisualState(BlockVisualState previous, BlockVisualState next) {
        if (previous == next) return true;
        if (previous == null) return next.isNormal() && !next.shouldSuppressWorldRendering() && next.shouldWriteDepth();
        return previous.mode == next.mode && Float.compare(previous.alphaMultiplier, next.alphaMultiplier) == 0
                && previous.useOverlay == next.useOverlay && previous.shouldWriteDepth() == next.shouldWriteDepth()
                && previous.suppressWorldRendering == next.suppressWorldRendering;
    }

    /**
     * 批量设置方块的视觉状态
     *
     * @param blocks 方块位置集合
     * @param state 视觉状态
     */
    public void setBlocksState(Collection<BlockPos> blocks, BlockVisualState state) {
        if (blocks == null || state == null) {
            return;
        }
        for (BlockPos pos : blocks) {
            if (pos != null) {
                setBlockState(pos, state);
            }
        }
    }
    
    /**
     * 获取方块的视觉状态
     * 
     * @param pos 方块位置
     * @return 视觉状态，如果未设置则返回正常状态
     */
    public BlockVisualState getBlockState(BlockPos pos) {
        if (pos == null) {
            return BlockVisualState.normal();
        }
        var state = visualOverrides.get(pos);
        return state != null ? state : BlockVisualState.normal();
    }

    /**
     * 检查方块是否应该隐藏
     *
     * @param pos 方块位置
     * @return 如果应该隐藏返回 true
     */
    public boolean isBlockHidden(BlockPos pos) {
        BlockVisualState state = getBlockState(pos);
        return state.isHidden();
    }
    
    /**
     * 获取方块的透明度
     * 
     * @param pos 方块位置
     * @return 透明度值 0~1
     */
    public float getBlockAlpha(BlockPos pos) {
        BlockVisualState state = getBlockState(pos);
        return state.getAlpha();
    }

    /**
     * 检查方块是否应该使用覆盖层渲染
     *
     * @param pos 方块位置
     * @return 如果应该使用覆盖层返回 true
     */
    public boolean shouldUseOverlay(BlockPos pos) {
        BlockVisualState state = getBlockState(pos);
        return state.shouldUseOverlay();
    }

    /**
     * 记录当前帧需要覆盖渲染的方块（包含动画快照与视觉状态）
     *
     * @param entry 渲染条目
     */
    public void addRenderEntry(RenderEntry entry) {
        if (entry == null || entry.pos() == null) {
            return;
        }
        // 🔥 RenderEntry 2.0：不再检查 snapshot，因为 snapshot 将在渲染时通过 sampleSnapshot() 生成
        renderEntries.put(entry.pos(), entry);
    }

    /**
     * 获取当前帧需要覆盖渲染的所有条目
     */
    public Collection<RenderEntry> getRenderEntries() {
        return renderEntries.values();
    }

    public boolean hasRenderEntry(BlockPos pos) { return renderEntries.containsKey(pos); }
    
    // ⭐ 性能关键：缓存覆盖层方块集合，避免每帧重新构建
    private final Set<BlockPos> cachedOverlayBlocks = new HashSet<>();
    private final Set<BlockPos> overlayView = Collections.unmodifiableSet(cachedOverlayBlocks);
    private Set<BlockPos> overlayRefresh = new HashSet<>();

    private void updateOverlay(BlockPos pos, BlockVisualState state) {
        if (state != null && state.shouldUseOverlay() && !state.isHidden()) cachedOverlayBlocks.add(pos);
        else cachedOverlayBlocks.remove(pos);
    }

    /**
     * 获取所有需要覆盖层渲染的方块
     *
     * @return 方块位置集合
     */
    public Set<BlockPos> getOverlayBlocks() {
        if (!overlayRefresh.isEmpty()) {
            for (var pos : overlayRefresh) updateOverlay(pos, visualOverrides.get(pos));
            overlayRefresh = new HashSet<>();
        }
        return overlayView;
    }

    /**
     * 获取所有需要特殊处理的方块（非正常状态）
     *
     * @return 方块位置集合
     */
    public Set<BlockPos> getSpecialBlocks() {
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, BlockVisualState> entry : visualOverrides.entrySet()) {
            if (!entry.getValue().isNormal()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 完成当前帧（将当前状态保存为上一帧状态）
     */
    public void finishFrame() {
        // Replacing avoids retaining a huge initial journal's backing table for tiny later deltas.
        if (!frameBefore.isEmpty()) frameBefore = new HashMap<>();
    }
    
    /**
     * 检查世界网格的渲染抑制是否切换，忽略动画透明度等逐帧变化。
     */
    public boolean hasWorldSuppressionChanged(BlockPos pos) {
        var current = visualOverrides.get(pos);
        var previous = frameBefore.get(pos);
        if (previous == null) return false;
        return (current != null && current.shouldSuppressWorldRendering())
                != previous.suppress();
    }

    /** 检查包含透明度、模式及世界渲染抑制在内的全部视觉状态变化。 */
    public boolean hasStateChanged(BlockPos pos) {
        BlockVisualState current = visualOverrides.get(pos);
        FrameState last = frameBefore.get(pos);
        if (last == null) return false;
        if (current == null) return last.present();
        if (!last.present()) return true;
        return Math.abs(current.getAlpha() - last.alpha()) > 0.01f
                || current.shouldUseOverlay() != last.overlay() || current.getMode() != last.mode()
                || current.shouldSuppressWorldRendering() != last.suppress();
    }
    
    /**
     * 获取所有状态发生变化的方块
     * 
     * @return 方块位置集合
     */
    public Set<BlockPos> getChangedBlocks() {
        Set<BlockPos> changed = new HashSet<>();
        
        for (BlockPos pos : frameBefore.keySet()) {
            if (hasStateChanged(pos)) {
                changed.add(pos);
            }
        }
        
        return changed;
    }

    /** Positions written/mutated this frame, including net-zero transitions. */
    public int getChangeCandidateCount() { return frameBefore.size(); }
    
    /**
     * 清除所有状态
     */
    public void clearAll() {
        ClientSectionInvalidation.getInstance().invalidateAllVisual();
        for (var state : visualOverrides.values()) state.managedReferences--;
        animationBaseStates.clear();
        visualOverrides.clear();
        frameBefore = new HashMap<>();
        cachedOverlayBlocks.clear();
        overlayRefresh = new HashSet<>();
        renderEntries.clear();
    }

    /**
     * 为容器中的所有方块设置透明度
     *
     * @param containerId 容器ID
     * @param blocks 方块位置集合
     * @param alpha 透明度 0~1
     * @param useOverlay 是否使用覆盖层渲染
     */
    public void setContainerOpacity(UUID containerId, Collection<BlockPos> blocks, float alpha, boolean useOverlay) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        setBlocksState(blocks, state);
    }

    /**
     * 为容器中的特定方块设置透明度
     *
     * @param container 容器对象
     * @param blockPositions 要设置的方块位置集合
     * @param alpha 透明度 0~1
     * @param useOverlay 是否使用覆盖层渲染
     */
    public void setContainerBlocksOpacity(Container container, Collection<BlockPos> blockPositions,
                                         float alpha, boolean useOverlay) {
        if (container == null || blockPositions == null || blockPositions.isEmpty()) {
            return;
        }
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        Set<BlockPos> containerBlocks = container.getBlocksView();
        for (BlockPos pos : blockPositions) {
            if (pos != null && containerBlocks.contains(pos)) {
                setBlockState(pos, state);
            }
        }
    }

    /**
     * 隐藏容器中的所有方块
     *
     * @param container 容器对象
     */
    public void hideContainer(Container container) {
        if (container == null) {
            return;
        }
        setBlocksState(container.getBlocksView(), BlockVisualState.hidden());
    }

    /**
     * 隐藏容器中的特定方块
     *
     * @param container 容器对象
     * @param blockPositions 要隐藏的方块位置集合
     */
    public void hideContainerBlocks(Container container, Collection<BlockPos> blockPositions) {
        if (container == null || blockPositions == null || blockPositions.isEmpty()) {
            return;
        }
        Set<BlockPos> containerBlocks = container.getBlocksView();
        for (BlockPos pos : blockPositions) {
            if (pos != null && containerBlocks.contains(pos)) {
                setBlockState(pos, BlockVisualState.hidden());
            }
        }
    }

    /**
     * 恢复容器中的所有方块为正常状态（不隐藏，完全不透明）
     *
     * @param container 容器对象
     */
    public void restoreContainer(Container container) {
        if (container == null) {
            return;
        }
        setBlocksState(container.getBlocksView(), BlockVisualState.normal());
    }

    /**
     * 为单个方块设置透明度（便捷方法）
     *
     * @param pos 方块位置
     * @param alpha 透明度 0~1
     * @param useOverlay 是否使用覆盖层渲染
     */
    public void setBlockOpacity(BlockPos pos, float alpha, boolean useOverlay) {
        if (pos == null) {
            return;
        }
        BlockVisualState state = BlockVisualState.translucent(clamp01(alpha));
        state.setUseOverlay(useOverlay);
        setBlockState(pos, state);
    }

    /**
     * 隐藏单个方块（便捷方法）
     *
     * @param pos 方块位置
     */
    public void hideBlock(BlockPos pos) {
        if (pos == null) {
            return;
        }
        setBlockState(pos, BlockVisualState.hidden());
    }

    /**
     * 恢复单个方块为正常状态（便捷方法）
     *
     * @param pos 方块位置
     */
    public void restoreBlock(BlockPos pos) {
        if (pos == null) {
            return;
        }
        setBlockState(pos, BlockVisualState.normal());
    }

    /**
     * 获取容器的所有特殊状态方块
     *
     * @param container 容器对象
     * @return 需要特殊处理的方块集合
     */
    public Set<BlockPos> getContainerSpecialBlocks(Container container) {
        if (container == null) {
            return Collections.emptySet();
        }
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : container.getBlocksView()) {
            BlockVisualState state = getBlockState(pos);
            if (!state.isNormal()) {
                result.add(pos);
            }
        }
        return result;
    }

    /**
     * 将值限制在 [0, 1] 范围内
     */
    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }

    /**
     * 渲染条目 2.0（用于批量渲染，包含动画语义）
     *
     * <p>设计目标：
     * <ul>
     *   <li>包含动画相关关键字段（clip, seqEntry），让 renderEntries() 能够真正执行动画</li>
     *   <li>支持可选的 overrideSnapshot，允许外部覆盖动画结果</li>
     *   <li>移除 snapshot 字段，改为在渲染时通过 sampleSnapshot() 生成</li>
     * </ul>
     *
     * @param clip             🔥 新增动画语义
     * @param overrideSnapshot 可选：外部覆盖动画快照（通常不使用）
     */
        public record RenderEntry(BlockPos pos, BlockState blockState, BlockVisualState visualState, TimelineClip clip,
                                  BlockSequenceEntry seqEntry, BlockAnimationSnapshot overrideSnapshot) {

            public RenderEntry(
                    BlockPos pos,
                    BlockState blockState,
                    BlockVisualState visualState,
                    TimelineClip clip,
                    BlockSequenceEntry seqEntry,
                    BlockAnimationSnapshot overrideSnapshot
            ) {
                this.pos = pos;
                this.blockState = blockState;
                this.visualState = visualState != null ? visualState : BlockVisualState.normal();
                this.clip = clip;
                this.seqEntry = seqEntry;
                this.overrideSnapshot = overrideSnapshot;
            }

        public boolean hasOverrideSnapshot() {
            return overrideSnapshot != null;
        }
        }

}
