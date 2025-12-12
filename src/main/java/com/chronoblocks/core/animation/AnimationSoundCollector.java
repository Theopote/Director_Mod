package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
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
     * <p>根据片段的动画配置和序列条目，生成所有方块放置的声音事件。
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
        
        // 遍历序列条目，为每个方块生成放置声音事件
        for (BlockSequenceEntry entry : sequenceEntries) {
            if (entry == null) {
                continue;
            }
            
            BlockPos pos = entry.getPos();
            if (pos == null) {
                continue;
            }
            
            // 计算方块出现的时间（全局时间）
            float normalizedTime = entry.getNormalizedTime(); // 0..1
            float blockAppearTime = clipStart + normalizedTime * clipDuration;
            
            // 获取方块状态
            BlockState blockState = getBlockState(container, pos);
            
            // 获取方块放置声音ID
            String soundId = getBlockPlaceSound(blockState);
            
            // 生成方块放置声音事件
            String label = String.format("方块放置 @ %.2fs", blockAppearTime);
            AudioEvent placeEvent = new AudioEvent(label, soundId, blockAppearTime);
            placeEvent.setVolume(1.0f);
            placeEvent.setPitch(1.0f);
            
            events.add(placeEvent);
            
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[AnimationSoundCollector] 生成方块放置声音: {} at {:.2f}s (片段: {})",
                        pos, blockAppearTime, clip.getLabel());
            }
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[AnimationSoundCollector] 从片段 {} 收集到 {} 个声音事件",
                    clip.getLabel(), events.size());
        }
        
        return events;
    }
    
    /**
     * 收集方块移除声音事件（当片段结束时，如果方块被移除）
     * 
     * <p>注意：当前实现主要关注方块放置声音，移除声音可以在后续版本中添加。
     * 
     * @param clip 时间线片段
     * @param container 容器对象
     * @param sequenceEntries 方块序列条目列表
     * @return 收集到的声音事件列表
     */
    public List<AudioEvent> collectBlockBreakEvents(
            TimelineClip clip,
            Container container,
            List<BlockSequenceEntry> sequenceEntries) {

        // TODO: 实现方块移除声音事件收集
        // 这需要跟踪哪些方块在片段结束时被移除
        
        return new ArrayList<>();
    }
    
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
}
