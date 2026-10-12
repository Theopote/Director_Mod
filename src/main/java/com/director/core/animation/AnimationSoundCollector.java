package com.director.core.animation;

import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.Container;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.audio.AudioEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import com.director.util.ParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 动画声音收集器
 * 
 * <p>收集容器动画播放过程中产生的声音事件（方块放置/移除等），
 * 并将这些事件转换为 AnimationSoundEvent 对象。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>监听容器片段的动画播放</li>
 *   <li>当方块出现时，生成方块放置声音事件</li>
 *   <li>当方块消失时，生成方块移除声音事件</li>
 *   <li>将事件的时间转换为全局时间线时间</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public class AnimationSoundCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationSoundCollector.class);
    
    private static AnimationSoundCollector instance;
    
    private AnimationSoundCollector() {
    }
    
    /**
     * 获取单例实例
     */
    public static AnimationSoundCollector getInstance() {
        if (instance == null) {
            instance = new AnimationSoundCollector();
        }
        return instance;
    }
    
    /**
     * 从容器片段收集声音事件
     * 
     * <p>根据片段的动画配置（Enter/Exit）和序列条目，生成方块放置/破坏的声音事件。
     * 
     * @param clip 时间线片段
     * @param container 容器对象
     * @param sequenceEntries 方块序列条目列表
     * @return 收集到的声音事件列表
     */
    public List<AudioEvent> collectFromClip(
            TimelineClip clip,
            Container container,
            List<BlockSequenceEntry> sequenceEntries) {
        
        List<AudioEvent> events = new ArrayList<>();
        
        if (clip == null || container == null) {
            return events;
        }

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) config = ClipAnimationConfig.defaultConfig();
        if (config.isWholeContainer()) {
            // A single placement/removal sound for the assembly, rather than thousands of simultaneous sounds.
            BlockPos pos = container.getBlocksView().stream().min(java.util.Comparator.comparingLong(BlockPos::asLong)).orElse(null);
            if (pos == null) return events;
            float time = wholeContainerEventTime(clip);
            boolean exit = config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT;
            BlockState state = getBlockState(container, pos);
            events.add(new AudioEvent(clip.getLabel(), exit ? getBlockBreakSound(state) : getBlockPlaceSound(state), time));
            return events;
        }
        if (sequenceEntries == null || sequenceEntries.isEmpty()) return events;
        
        float clipStart = clip.getStartTime();
        float clipDuration = clip.getLength();

        var timing = PerBlockAnimationTiming.layout(config, clipStart, clipStart + clipDuration);
        float animDuration = timing.duration();

        boolean isExit = config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT;
        boolean isInstant = config.getMode() == com.director.core.container.AnimationMode.INSTANT;

        var params = config.getParams();
        float enterSoundOffsetSeconds = ParseUtils.asFloat(
                params != null ? params.get("enterSoundOffsetSeconds") : null, 0.0f, "enterSoundOffsetSeconds");
        float exitSoundOffsetSeconds = ParseUtils.asFloat(
                params != null ? params.get("exitSoundOffsetSeconds") : null, 0.0f, "exitSoundOffsetSeconds");

        String enterTiming = soundTimingParam(params, "enterSoundTiming");

        float impactT = 1.0f;
        if (!isExit && !isInstant && "IMPACT".equalsIgnoreCase(enterTiming)) {
            // 从 easing 曲线中找“第一次到达 1.0”的时刻（数值扫描）
            impactT = findFirstReachOne(config.getEasing());
        }

        String exitTiming = soundTimingParam(params, "exitSoundTiming");
        float exitImpactT = 1.0f;
        if (isExit && !isInstant && "IMPACT".equalsIgnoreCase(exitTiming)) {
            exitImpactT = findFirstReachOne(config.getEasing());
        }

        // 遍历序列条目，为每个方块生成声音事件
        for (BlockSequenceEntry entry : sequenceEntries) {
            if (entry == null) {
                continue;
            }
            
            BlockPos pos = entry.getPos();
            if (pos == null) {
                continue;
            }
            
            // 计算方块事件时间（全局时间）
            float normalizedTime = entry.getNormalizedTime(); // 0..1
            float eventTime;
            var window = timing.window(normalizedTime);
            float blockStartTime = window.start();
            float blockEndTime = window.end();
            if (isExit) {

                if (isInstant) {
                    eventTime = blockStartTime;
                } else if ("START".equalsIgnoreCase(exitTiming)) {
                    eventTime = blockStartTime;
                } else if ("END".equalsIgnoreCase(exitTiming)) {
                    eventTime = blockEndTime;
                } else if ("PRE_LAND".equalsIgnoreCase(exitTiming)) {
                    float pre = blockEndTime - (1.0f / 60.0f);
                    if (pre < blockStartTime) pre = blockStartTime;
                    eventTime = pre;
                } else {
                    float impactTime = blockStartTime + exitImpactT * animDuration;
                    if (impactTime < blockStartTime) impactTime = blockStartTime;
                    if (impactTime > blockEndTime) impactTime = blockEndTime;
                    eventTime = impactTime;
                }
                if (exitSoundOffsetSeconds != 0f) {
                    eventTime += exitSoundOffsetSeconds;
                }
            } else {
                if (isInstant) {
                    eventTime = blockStartTime;
                } else if ("START".equalsIgnoreCase(enterTiming)) {
                    eventTime = blockStartTime;
                } else if ("END".equalsIgnoreCase(enterTiming)) {
                    eventTime = blockEndTime;
                } else if ("PRE_LAND".equalsIgnoreCase(enterTiming)) {
                    // 取“归位前一帧”：用 60FPS 的 1 帧作为经验值（比 END 早一点点）
                    float pre = blockEndTime - (1.0f / 60.0f);
                    if (pre < blockStartTime) pre = blockStartTime;
                    eventTime = pre;
                } else {
                    // IMPACT：第一下到位（通常 <= blockEndTime）
                    float impactTime = blockStartTime + impactT * animDuration;
                    if (impactTime < blockStartTime) impactTime = blockStartTime;
                    if (impactTime > blockEndTime) impactTime = blockEndTime;
                    eventTime = impactTime;
                }

                if (enterSoundOffsetSeconds != 0f) {
                    eventTime += enterSoundOffsetSeconds;
                }
            }

            // clamp：不允许负时间
            if (eventTime < 0.0f) {
                eventTime = 0.0f;
            }
            
            // 获取方块状态
            BlockState blockState = getBlockState(container, pos);
            
            // 获取声音ID
            String soundId = isExit ? getBlockBreakSound(blockState) : getBlockPlaceSound(blockState);
            
            // 生成声音事件
            String label = isExit
                    ? String.format("方块破坏 @ %.2fs", eventTime)
                    : String.format("方块放置 @ %.2fs", eventTime);
            AudioEvent ev = new AudioEvent(label, soundId, eventTime);
            ev.setVolume(1.0f);
            ev.setPitch(1.0f);
            events.add(ev);
            
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[AnimationSoundCollector] 生成方块声音: {} at {:.2f}s (片段: {} phase={})",
                        pos, eventTime, clip.getLabel(), isExit ? "EXIT" : "ENTER");
            }
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[AnimationSoundCollector] 从片段 {} 收集到 {} 个声音事件",
                    clip.getLabel(), events.size());
        }
        
        return events;
    }

    /**
     * 对给定 easing 名称，数值扫描找出 t_raw∈[0,1] 内第一次达到 1.0 的位置。
     * <p>
     * 对于有 overshoot 的曲线（如 EaseOutBack/EaseOutElastic），该值通常 < 1；
     * 对于不 overshoot 的曲线，结果为 1。
     */
    private static float findFirstReachOne(String easingName) {
        EasingFunction easing = EasingFunction.fromString(easingName);

        // 轻量扫描：步数越大越精确，但也更耗；这里足够用
        final int steps = 200;
        final float threshold = 0.9995f;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float v = easing.apply(t);
            if (v >= threshold) {
                return t;
            }
        }
        return 1.0f;
    }
    
    // 旧的 collectBlockBreakEvents 已并入 collectFromClip（根据 config.phase 自动选择放置/破坏语义）
    
    /**
     * 获取方块状态
     */
    private BlockState getBlockState(Container container, BlockPos pos) {
        if (container == null || pos == null) {
            return null;
        }
        
        // 优先从容器的 blockStore 获取
        if (container.getBlockStore() != null) {
            var entry = container.getBlockStore().get(pos);
            if (entry != null && entry.state != null) {
                return entry.state;
            }
        }
        
        // 如果容器没有存储，尝试从世界获取
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                return client.world.getBlockState(pos);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[AnimationSoundCollector] 无法从世界获取方块状态: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 根据方块状态获取放置声音
     */
    private String getBlockPlaceSound(BlockState blockState) {
        if (blockState == null) {
            return "minecraft:block.stone.place";
        }
        
        // 根据方块类型返回对应的声音
        // 这里简化处理，实际应该根据 Minecraft 的方块声音组来获取
        net.minecraft.block.Block block = blockState.getBlock();
        String blockName = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        
        // 简化映射：根据方块名称推断声音
        if (blockName.contains("stone") || blockName.contains("cobblestone")) {
            return "minecraft:block.stone.place";
        } else if (blockName.contains("wood") || blockName.contains("log") || blockName.contains("planks")) {
            return "minecraft:block.wood.place";
        } else if (blockName.contains("grass") || blockName.contains("dirt")) {
            return "minecraft:block.grass.place";
        } else if (blockName.contains("gravel")) {
            return "minecraft:block.gravel.place";
        } else if (blockName.contains("sand")) {
            return "minecraft:block.sand.place";
        } else {
            return "minecraft:block.stone.place"; // 默认声音
        }
    }

    /**
     * 根据方块状态获取破坏声音
     */
    private String getBlockBreakSound(BlockState blockState) {
        if (blockState == null) {
            return "minecraft:block.stone.break";
        }

        net.minecraft.block.Block block = blockState.getBlock();
        String blockName = net.minecraft.registry.Registries.BLOCK.getId(block).toString();

        if (blockName.contains("stone") || blockName.contains("cobblestone")) {
            return "minecraft:block.stone.break";
        } else if (blockName.contains("wood") || blockName.contains("log") || blockName.contains("planks")) {
            return "minecraft:block.wood.break";
        } else if (blockName.contains("grass") || blockName.contains("dirt")) {
            return "minecraft:block.grass.break";
        } else if (blockName.contains("gravel")) {
            return "minecraft:block.gravel.break";
        } else if (blockName.contains("sand")) {
            return "minecraft:block.sand.break";
        } else {
            return "minecraft:block.stone.break";
        }
    }

    /** One assembly sound just before the world handoff, independent of per-block audio settings. */
    static float wholeContainerEventTime(TimelineClip clip) {
        var window = WholeContainerAnimation.window(clip.getAnimationConfig(), clip.getStartTime(), clip.getEndTime());
        return Math.max(0, Math.max(window.start(), window.end() - 1.0f / 60.0f));
    }

    private static String soundTimingParam(Map<String, Object> params, String key) {
        if (params == null) {
            return "END";
        }
        Object value = params.get(key);
        if (value == null) {
            return "END";
        }
        String text = value.toString();
        return text.isBlank() ? "END" : text;
    }
}
