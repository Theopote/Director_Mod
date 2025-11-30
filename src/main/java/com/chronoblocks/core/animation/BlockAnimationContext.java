package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 方块动画上下文
 * 
 * <p>封装单个方块在当前时间点的动画状态，包括：
 * <ul>
 *   <li>时间映射：全局时间 → 片段时间 → 序列时间 → 动画进度</li>
 *   <li>动画配置和序列信息</li>
 *   <li>方块位置和世界引用</li>
 * </ul>
 * 
 * <p>用于 BlockAnimationRenderer 和 BlockAnimationEngine 之间的数据传递。
 * 
 * @author ChronoBlocks Team
 * @version 3.0
 */
public final class BlockAnimationContext {
    
    /** 当前世界 */
    @NotNull
    private final World world;
    
    /** 方块位置 */
    @NotNull
    private final BlockPos pos;
    
    /** 所属时间线片段 */
    @NotNull
    private final TimelineClip clip;
    
    /** 动画配置 */
    @NotNull
    private final ClipAnimationConfig config;
    
    /** 序列条目（包含序列时间等信息） */
    @NotNull
    private final BlockSequenceEntry sequenceEntry;
    
    /** 全局时间（秒） */
    private final double globalTimeSec;
    
    /** 片段内本地时间（秒）[0, clip.length] */
    private final double clipLocalTime;
    
    /** 序列归一化时间 [0..1]，表示该方块在序列中的位置 */
    private final float seqNormalizedTime;
    
    /** 动画归一化进度 [0..1]，已应用 Easing */
    private final float animProgress;
    
    /** 动画原始进度 [0..1]，未应用 Easing（用于特效时间计算） */
    private final float animProgressRaw;
    
    /**
     * 创建动画上下文
     * 
     * @param world 世界
     * @param pos 方块位置
     * @param clip 时间线片段
     * @param config 动画配置
     * @param sequenceEntry 序列条目
     * @param globalTimeSec 全局时间（秒）
     * @param clipLocalTime 片段内本地时间（秒）
     * @param seqNormalizedTime 序列归一化时间 [0..1]
     * @param animProgress 动画归一化进度 [0..1]（已应用 Easing）
     * @param animProgressRaw 动画原始进度 [0..1]（未应用 Easing）
     */
    public BlockAnimationContext(
            @NotNull World world,
            @NotNull BlockPos pos,
            @NotNull TimelineClip clip,
            @NotNull ClipAnimationConfig config,
            @NotNull BlockSequenceEntry sequenceEntry,
            double globalTimeSec,
            double clipLocalTime,
            float seqNormalizedTime,
            float animProgress,
            float animProgressRaw
    ) {
        this.world = world;
        this.pos = pos;
        this.clip = clip;
        this.config = config;
        this.sequenceEntry = sequenceEntry;
        this.globalTimeSec = globalTimeSec;
        this.clipLocalTime = clipLocalTime;
        this.seqNormalizedTime = seqNormalizedTime;
        this.animProgress = animProgress;
        this.animProgressRaw = animProgressRaw;
    }
    
    /**
     * 从时间线状态构建动画上下文
     * 
     * <p>将全局时间映射到该方块的局部动画时间，计算动画进度。
     * 
     * @param world 世界
     * @param pos 方块位置
     * @param clip 时间线片段
     * @param config 动画配置
     * @param sequenceEntry 序列条目
     * @param globalTimeSec 全局播放时间（秒）
     * @return 动画上下文，如果方块尚未开始动画或时间超出范围则返回 null
     */
    @Nullable
    public static BlockAnimationContext build(
            @NotNull World world,
            @NotNull BlockPos pos,
            @NotNull TimelineClip clip,
            @NotNull ClipAnimationConfig config,
            @NotNull BlockSequenceEntry sequenceEntry,
            double globalTimeSec
    ) {
        double clipStart = clip.getStartTime();
        double clipEnd = clipStart + clip.getLength();
        
        // 不在片段时间范围内
        if (globalTimeSec < clipStart || globalTimeSec > clipEnd) {
            return null;
        }
        
        // 片段内本地时间 [0, clip.length]
        double clipLocalTime = globalTimeSec - clipStart;
        
        // 序列时间：该方块在片段内什么时候开始动画
        float seqT = sequenceEntry.getNormalizedTime(); // 0..1
        double seqStartTime = clip.getLength() * seqT; // 片段内绝对秒
        
        // 动画时长
        double animDuration = config.getDuration();
        if (animDuration <= 0.0) {
            // duration 为 0 相当于瞬间到位
            return new BlockAnimationContext(
                    world, pos, clip, config, sequenceEntry,
                    globalTimeSec, clipLocalTime, seqT,
                    1.0f, 1.0f
            );
        }
        
        // 对于这个方块来说，当前的动画进度
        double animLocalTime = clipLocalTime - seqStartTime; // 该方块的动画已经运行了多少秒
        float animProgressRaw = (float) (animLocalTime / animDuration);
        
        if (animProgressRaw < 0.0f) {
            // 动画还没开始
            return null;
        }
        if (animProgressRaw > 1.0f) {
            // 动画已经结束
            animProgressRaw = 1.0f;
        }
        
        // 应用缓动函数
        float animProgress = animProgressRaw;
        try {
            com.chronoblocks.core.animation.easing.EasingFunction easing = 
                com.chronoblocks.core.animation.easing.EasingFunction.fromString(config.getEasing());
            animProgress = easing.apply(animProgressRaw);
        } catch (Exception e) {
            // 如果解析失败，使用原始进度
        }
        
        return new BlockAnimationContext(
                world, pos, clip, config, sequenceEntry,
                globalTimeSec, clipLocalTime, seqT,
                animProgress, animProgressRaw
        );
    }
    
    // ========== Getters ==========
    
    @NotNull
    public World getWorld() {
        return world;
    }
    
    @NotNull
    public BlockPos getPos() {
        return pos;
    }
    
    @NotNull
    public TimelineClip getClip() {
        return clip;
    }
    
    @NotNull
    public ClipAnimationConfig getConfig() {
        return config;
    }
    
    @NotNull
    public BlockSequenceEntry getSequenceEntry() {
        return sequenceEntry;
    }
    
    public double getGlobalTimeSec() {
        return globalTimeSec;
    }
    
    public double getClipLocalTime() {
        return clipLocalTime;
    }
    
    public float getSeqNormalizedTime() {
        return seqNormalizedTime;
    }
    
    /**
     * 获取动画归一化进度 [0..1]（已应用 Easing）
     * 用于应用变换（位移、旋转、缩放）
     */
    public float getAnimProgress() {
        return animProgress;
    }
    
    /**
     * 获取动画原始进度 [0..1]（未应用 Easing）
     * 用于特效时间计算
     */
    public float getAnimProgressRaw() {
        return animProgressRaw;
    }
}

