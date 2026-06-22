package com.director.ui.timeline.playback.audio;

import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AudioTrackSampler v1.0
 *
 * <p>时间线上用于"实时输出声音事件"的采样器。
 *
 * <p>功能：
 * <ul>
 *   <li>根据当前时间触发 AudioEvent</li>
 *   <li>避免重复触发</li>
 *   <li>处理 seek（跳转）逻辑</li>
 *   <li>支持轨道静音</li>
 *   <li>支持全局暂停/播放控制</li>
 * </ul>
 *
 * <p>这是一个轻量的事件触发器，不做混音。
 * 
 * <p>设计特点：
 * <ul>
 *   <li>事件只触发一次 - 通过 playedEvents 记录已经播放过的事件</li>
 *   <li>倒放也能触发（可选）- 如果 lastTime > time，表示用户拖动时间轴回去了，也能触发事件</li>
 *   <li>支持静音轨道 - 检查 track.isMuted() 来避免发声</li>
 *   <li>支持暂停 / 重置 - 暂停播放时不会持续触发事件，Reset 时重置播放状态</li>
 *   <li>与 Minecraft 声音引擎直接集成 - 使用 PositionedSoundInstance.master() 作为全局 UI 声音处理方案</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public class AudioTrackSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AudioTrackSampler.class);

    private final AudioTrack track;

    /** 当前时间（用于判断是否跳转） */
    private float lastTime = -1f;

    /** 是否正在播放（由 Timeline 控制） */
    private boolean playing = false;

    /**
     * 构造函数
     * 
     * @param track 音频轨道
     */
    public AudioTrackSampler(AudioTrack track) {
        this.track = track;
    }

    // =====================================================================
    // Playback Control
    // =====================================================================

    /**
     * 设置播放状态
     * 
     * @param playing 是否正在播放
     */
    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    /**
     * 重置采样器状态
     */
    public void reset() {
        lastTime = -1f;
    }

    /**
     * 获取关联的音频轨道
     * 
     * @return 音频轨道
     */
    public AudioTrack getTrack() {
        return track;
    }

    // =====================================================================
    // Main sampling function
    // =====================================================================

    /**
     * 在时间线上每一帧调用。
     *
     * <p>根据当前时间检测并触发需要播放的音频事件。
     * 
     * @param time 当前播放时间（秒）
     */
    public void sample(float time) {
        if (!playing) {
            lastTime = time;
            return;
        }

        boolean hasLast = lastTime >= 0f;
        boolean isReverse = hasLast && lastTime > time; // 是否倒放
        float prevTime = lastTime;
        lastTime = time;

        // 检查轨道是否静音
        if (track.isMuted()) {
            return;
        }

        List<AudioEvent> events = track.getEvents();
        if (events.isEmpty()) {
            return;
        }

        final float eps = 1e-5f;

        // 性能关键：事件数量可能极大（每个方块一个音频事件）。
        // 这里用“时间窗口 + 二分查找”只触发本帧跨过的事件，避免每帧从头扫描 O(N)。
        if (!hasLast) {
            // 首帧：补触发一个很小窗口内的事件，避免“片段开头声音全丢导致明显滞后”，同时避免中途开始时爆音
            // 经验值：50ms 内的事件听感上属于“立刻发生”，且数量通常可控
            final float warmupWindow = 0.05f;
            float fromTime = Math.max(0f, time - warmupWindow);
            int from = upperBound(events, fromTime - eps);
            int to = upperBound(events, time + eps);
            for (int i = from; i < to; i++) {
                AudioEvent ev = events.get(i);
                if (ev == null) continue;
                triggerEvent(ev);
            }
            return;
        }

        if (!isReverse) {
            // (prevTime, time] 的事件触发
            if (time <= prevTime + eps) {
                return;
            }
            int from = upperBound(events, prevTime + eps);
            int to = upperBound(events, time + eps);
            for (int i = from; i < to; i++) {
                AudioEvent ev = events.get(i);
                if (ev == null) continue;
                triggerEvent(ev);
            }
        } else {
            // 倒放：[time, prevTime) 的事件也触发（从后往前）
            if (prevTime <= time + eps) {
                return;
            }
            int from = lowerBound(events, time - eps);
            int to = lowerBound(events, prevTime - eps);
            for (int i = to - 1; i >= from; i--) {
                AudioEvent ev = events.get(i);
                if (ev == null) continue;
                triggerEvent(ev);
            }
        }
    }

    /**
     * 返回第一个 event.time >= t 的索引。
     */
    private static int lowerBound(List<AudioEvent> events, float t) {
        int lo = 0, hi = events.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            AudioEvent ev = events.get(mid);
            float mt = ev != null ? ev.getStartTime() : Float.NEGATIVE_INFINITY;
            if (mt < t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * 返回第一个 event.time > t 的索引。
     */
    private static int upperBound(List<AudioEvent> events, float t) {
        int lo = 0, hi = events.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            AudioEvent ev = events.get(mid);
            float mt = ev != null ? ev.getStartTime() : Float.NEGATIVE_INFINITY;
            if (mt <= t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // =====================================================================
    // Play Minecraft sound
    // =====================================================================

    /**
     * 触发音频事件，播放 Minecraft 声音
     * 
     * @param ev 音频事件
     */
    private void triggerEvent(AudioEvent ev) {
        if (ev == null || !ev.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }

        try {
            // 解析声音ID
            String soundId = ev.getSoundId();
            if (soundId == null || soundId.isEmpty()) {
                return;
            }

            Identifier soundIdentifier = Identifier.tryParse(soundId);
            if (soundIdentifier == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[AudioTrackSampler] 无效的声音ID: {}", soundId);
                }
                return;
            }

            // 从注册表获取声音事件（直接使用 Registries 静态访问）
            SoundEvent soundEvent = Registries.SOUND_EVENT.get(soundIdentifier);
            
            if (soundEvent == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[AudioTrackSampler] 未找到声音: {}", soundId);
                }
                return;
            }

            // 计算最终音量（应用轨道音量）
            float volume = ev.getVolume() * track.getTrackVolume();
            float volumeMultiplier = TimelineAudioService.getInstance().getVolumeMultiplierForEvent(ev);
            volume *= volumeMultiplier;
            float pitch = ev.getPitch();

            // 使用 PositionedSoundInstance 创建全局 UI 声音
            // 使用 NONE 衰减类型实现全局声音效果（不受距离影响）
            PositionedSoundInstance soundInstance = PositionedSoundInstance.master(
                    soundEvent,
                    pitch,
                    volume
            );
            
            // 播放声音
            client.getSoundManager().play(soundInstance);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[AudioTrackSampler] 触发声音事件: {} at {:.2f}s (音量: {:.2f}, 音调: {:.2f})",
                        soundId, ev.getTime(), volume, pitch);
            }

        } catch (Exception e) {
            LOGGER.error("[AudioTrackSampler] 播放声音失败: {}", e.getMessage(), e);
        }
    }
}
