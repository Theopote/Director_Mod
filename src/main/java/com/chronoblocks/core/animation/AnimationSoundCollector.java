package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.easing.EasingFunction;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
 * @author ChronoBlocks Team
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
        
        if (clip == null || container == null || sequenceEntries == null || sequenceEntries.isEmpty()) {
            return events;
        }
        
        float clipStart = clip.getStartTime();
        float clipDuration = clip.getLength();

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            config = ClipAnimationConfig.defaultConfig();
        }

        float animDuration = Math.max(0.05f, Math.min(config.getDuration(), clipDuration));
        float totalDelay = Math.max(0.0f, config.getDelay());
        try {
            if (config.getSequenceParameters() != null) {
                totalDelay += Math.max(0.0f, config.getSequenceParameters().getGlobalDelay());
            }
        } catch (Exception ignored) {
        }

        float baseSpan = clipDuration - animDuration - totalDelay;
        boolean fullWindow = baseSpan > 0.0001f;
        float availableSpan = fullWindow ? baseSpan : Math.max(0.0001f, clipDuration - totalDelay);

        boolean isExit = config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT;
        boolean isInstant = config.getMode() == com.chronoblocks.core.container.AnimationMode.INSTANT;

        // ENTER 音效触发时机：
        // - START    : 动画开始
        // - IMPACT   : 第一次到达目标位置（对某些弹性曲线可能过早）
        // - PRE_LAND : 在落地/归位前一帧触发（更贴近“看到真方块落地前那一下”）
        // - END      : 动画完全结束（归位/回弹结束）
        //
        // 默认使用 PRE_LAND：避免弹性曲线“很早就第一次触碰到位”造成音频超前，
        // 同时又比 END 更靠前一点点，贴近你的“归位前一帧/差不多在动画时长附近”的体感。
        String enterTiming = null;
        try {
            Object t = config.getParams().get("enterSoundTiming");
            if (t != null) enterTiming = t.toString();
        } catch (Exception ignored) {}
        if (enterTiming == null || enterTiming.isBlank()) {
            enterTiming = "PRE_LAND";
        }

        float impactT = 1.0f;
        if (!isExit && !isInstant && "IMPACT".equalsIgnoreCase(enterTiming)) {
            // 从 easing 曲线中找“第一次到达 1.0”的时刻（数值扫描）
            impactT = findFirstReachOne(config.getEasing());
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
            if (isExit) {
                // EXIT：事件在“方块被移除”时刻触发（动画结束端点）
                eventTime = clipStart + totalDelay + (fullWindow ? animDuration : 0.0f) + normalizedTime * availableSpan;
            } else {
                float blockStartTime = clipStart + totalDelay + normalizedTime * availableSpan;
                float blockEndTime = Math.min(clipStart + clipDuration, blockStartTime + animDuration);
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
    private float findFirstReachOne(String easingName) {
        EasingFunction easing = EasingFunction.LINEAR;
        try {
            easing = EasingFunction.fromString(easingName);
        } catch (Exception ignored) {
        }

        // 轻量扫描：步数越大越精确，但也更耗；这里足够用
        final int steps = 200;
        final float threshold = 0.9995f;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float v;
            try {
                v = easing.apply(t);
            } catch (Exception e) {
                return 1.0f;
            }
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
        } catch (Exception e) {
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
}
