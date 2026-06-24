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
            this.alphaMultiplier = Math.max(0f, Math.min(1f, alphaMultiplier));
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
            this.mode = mode != null ? mode : BlockRenderMode.SOLID;
            this.useOverlay = (mode == BlockRenderMode.GHOST_WITH_DEPTH || mode == BlockRenderMode.GHOST_NO_DEPTH);
        }
        
        public float getAlphaMultiplier() {
            return alphaMultiplier;
        }
        
        public void setAlphaMultiplier(float alphaMultiplier) {
            this.alphaMultiplier = Math.max(0f, Math.min(1f, alphaMultiplier));
        }
        
        public Boolean getDepthWriteOverride() {
            return depthWriteOverride;
        }
        
        public void setDepthWriteOverride(Boolean depthWriteOverride) {
            this.depthWriteOverride = depthWriteOverride;
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
            this.alphaMultiplier = Math.max(0f, Math.min(1f, alpha));
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
    
    /**
     * 上一帧的状态（用于检测变化）
     */
    private final Map<BlockPos, BlockVisualState> lastFrameStates = new HashMap<>();
    
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
        visualOverrides.clear();
        renderEntries.clear();
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
            visualOverrides.remove(pos);
            renderEntries.remove(pos);
            lastFrameStates.remove(pos);
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
        visualOverrides.put(pos, state);
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
                visualOverrides.put(pos, state);
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
        return visualOverrides.getOrDefault(pos, BlockVisualState.normal());
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
    
    /**
     * 获取所有需要覆盖层渲染的方块
     * 
     * @return 方块位置集合
     */
    public Set<BlockPos> getOverlayBlocks() {
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, BlockVisualState> entry : visualOverrides.entrySet()) {
            if (entry.getValue().shouldUseOverlay() && !entry.getValue().isHidden()) {
                result.add(entry.getKey());
            }
        }
        return result;
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
        lastFrameStates.clear();
        lastFrameStates.putAll(visualOverrides);
    }
    
    /**
     * 检查方块状态是否发生变化
     * 
     * @param pos 方块位置
     * @return 如果状态发生变化返回 true
     */
    public boolean hasStateChanged(BlockPos pos) {
        BlockVisualState current = visualOverrides.get(pos);
        BlockVisualState last = lastFrameStates.get(pos);
        
        if (current == null && last == null) {
            return false;
        }
        if (current == null || last == null) {
            return true;
        }
        
        return current.isHidden() != last.isHidden() ||
               Math.abs(current.getAlpha() - last.getAlpha()) > 0.01f ||
               current.shouldUseOverlay() != last.shouldUseOverlay() ||
               current.getMode() != last.getMode() ||
               current.shouldSuppressWorldRendering() != last.shouldSuppressWorldRendering();
    }
    
    /**
     * 获取所有状态发生变化的方块
     * 
     * @return 方块位置集合
     */
    public Set<BlockPos> getChangedBlocks() {
        Set<BlockPos> changed = new HashSet<>();
        
        // 检查当前帧的所有方块
        for (BlockPos pos : visualOverrides.keySet()) {
            if (hasStateChanged(pos)) {
                changed.add(pos);
            }
        }
        
        // 检查上一帧存在但当前帧不存在的方块（状态被清除）
        for (BlockPos pos : lastFrameStates.keySet()) {
            if (!visualOverrides.containsKey(pos)) {
                changed.add(pos);
            }
        }
        
        return changed;
    }
    
    /**
     * 清除所有状态
     */
    public void clearAll() {
        visualOverrides.clear();
        lastFrameStates.clear();
        renderEntries.clear();
    }
    
    // ========== Per-Container 便捷方法（问题4修复）==========
    
    /**
     * 为容器中的所有方块设置透明度
     * 
     * @param container 容器对象
     * @param alpha 透明度 0~1
     * @param useOverlay 是否使用覆盖层渲染
     */
    public void setContainerOpacity(Container container, float alpha, boolean useOverlay) {
        if (container == null) {
            return;
        }
        setContainerOpacity(container.getId(), container.getBlocksView(), alpha, useOverlay);
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
     */
    public static final class RenderEntry {

        private final BlockPos pos;
        private final BlockState blockState;
        private final BlockVisualState visualState;

        // 🔥 新增动画语义
        private final TimelineClip clip;
        private final BlockSequenceEntry seqEntry;

        // 可选：外部覆盖动画快照（通常不使用）
        private final BlockAnimationSnapshot overrideSnapshot;

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

        public BlockPos pos() { return pos; }
        public BlockState blockState() { return blockState; }
        public BlockVisualState visualState() { return visualState; }

        public TimelineClip clip() { return clip; }
        public BlockSequenceEntry seqEntry() { return seqEntry; }

        public BlockAnimationSnapshot overrideSnapshot() { return overrideSnapshot; }
        public boolean hasOverrideSnapshot() { return overrideSnapshot != null; }
    }

}
