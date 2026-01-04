package com.chronoblocks.ui.timeline.playback.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * WeatherEventController v1.1
 * -----------------------------------------
 * 天气事件控制子系统：负责雷暴、闪电、声音等事件
 * 
 * <p>职责：
 * <ul>
 *   <li>雷暴效果（屏幕闪白、天空闪白）</li>
 *   <li>雷声音效</li>
 *   <li>可选假闪电线条渲染（纯视觉，不破坏世界）</li>
 *   <li>与时间轴绑定的事件触发</li>
 * </ul>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>导演用雷暴：不生成真实 LightningEntity（会破坏世界）</li>
 *   <li>使用视觉事件 + 声音，适合 ChronoBlocks 的用途</li>
 *   <li>屏幕闪白通过临时提高天空亮度实现</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 实现基础雷暴事件
 */
public class WeatherEventController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherEventController.class);
    
    private static final WeatherEventController INSTANCE = new WeatherEventController();
    
    public static WeatherEventController getInstance() {
        return INSTANCE;
    }
    
    private WeatherEventController() {}
    
    // ========== 状态字段 ==========
    
    /** 当前天空闪白强度（用于瞬时亮度变化） */
    private float currentFlashBrightness = 0f;
    
    /** 闪白衰减速度（每 tick 衰减量） */
    private static final float FLASH_DECAY = 0.15f;
    
    /** 上次触发闪电的时间（tick） */
    private long lastLightningTick = -1000;
    
    /** 随机数生成器 */
    private final Random random = new Random();
    
    // ========== 公共 API ==========
    
    /**
     * 每 tick 调用，处理天气事件（雷暴等）。
     * 
     * <p>实现：
     * <ul>
     *   <li>根据 lightningFrequency 触发闪电事件</li>
     *   <li>触发时：播放雷声、增加闪白亮度、生成粒子效果</li>
     *   <li>闪白亮度每 tick 衰减</li>
     * </ul>
     * 
     * @param sample 天气采样
     * @param timelineTimeSeconds 时间线时间（秒）
     */
    public void tick(WeatherSample sample, float timelineTimeSeconds) {
        if (sample == null || !sample.enabled) {
            // 清除闪白状态
            currentFlashBrightness = 0f;
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        
        ClientWorld world = client.world;
        long currentTick = world.getTime();
        
        // 闪白亮度衰减
        if (currentFlashBrightness > 0f) {
            currentFlashBrightness = Math.max(0f, currentFlashBrightness - FLASH_DECAY);
        }
        
        // 检查是否应该触发闪电事件
        float thunderIntensity = sample.thunderIntensity;
        float lightningFrequency = sample.lightningFrequency;
        
        if (thunderIntensity <= 0.01f || lightningFrequency <= 0.01f) {
            return;
        }
        
        // 计算触发间隔（每秒 lightningFrequency 次）
        // 使用概率模型：每个 tick 有 probability 的概率触发
        float probabilityPerTick = lightningFrequency / 20f; // 每秒 lightningFrequency 次，每 tick 概率
        probabilityPerTick = Math.min(1f, probabilityPerTick); // 限制最大概率为 1
        
        // 为了避免过于频繁，增加最小间隔限制
        long minInterval = Math.max(10, (long) (20f / lightningFrequency)); // 最小间隔（tick）
        if (currentTick - lastLightningTick < minInterval) {
            return;
        }
        
        // 概率触发
        if (random.nextFloat() > probabilityPerTick) {
            return;
        }
        
        // 触发闪电事件
        triggerLightning(world, client, sample, thunderIntensity);
        lastLightningTick = currentTick;
    }
    
    /**
     * 触发闪电事件（雷声 + 闪白 + 粒子效果）。
     */
    private void triggerLightning(ClientWorld world, MinecraftClient client, WeatherSample sample, float thunderIntensity) {
        Vec3d playerPos = client.player.getEntityPos();
        
        // 1. 播放雷声音效
        playThunderSound(world, playerPos, thunderIntensity);
        
        // 2. 触发屏幕闪白（通过提高天空亮度）
        // 闪白强度基于 thunderIntensity
        currentFlashBrightness = 0.3f + thunderIntensity * 0.5f; // 0.3 ~ 0.8
        
        // 3. 生成闪电粒子效果（在远处）
        spawnLightningParticles(world, playerPos);
    }
    
    /**
     * 播放雷声音效。
     */
    private void playThunderSound(ClientWorld world, Vec3d playerPos, float thunderIntensity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }
        
        try {
            // 计算音量（基于 thunderIntensity）
            float volume = 0.5f + thunderIntensity * 1.5f; // 0.5 ~ 2.0
            volume = Math.min(2f, volume);
            
            // 计算音调（随机变化，模拟真实雷声）
            float pitch = 0.8f + random.nextFloat() * 0.4f; // 0.8 ~ 1.2
            
            // 播放雷声音效
            PositionedSoundInstance soundInstance = PositionedSoundInstance.master(
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                pitch,
                volume
            );
            
            client.getSoundManager().play(soundInstance);
            
        } catch (Exception e) {
            LOGGER.debug("[WeatherEventController] 播放雷声失败", e);
        }
    }
    
    /**
     * 生成闪电粒子效果（在远处，模拟闪电闪光）。
     */
    private void spawnLightningParticles(ClientWorld world, Vec3d playerPos) {
        // 在玩家周围随机位置生成闪电效果（远处）
        double lightningX = playerPos.x + (random.nextDouble() - 0.5) * 64;
        double lightningY = playerPos.y + random.nextDouble() * 20 + 10;
        double lightningZ = playerPos.z + (random.nextDouble() - 0.5) * 64;
        
        var particleManager = MinecraftClient.getInstance().particleManager;
        if (particleManager == null) {
            return;
        }
        
        // 生成爆炸粒子模拟闪电闪光
        for (int i = 0; i < 5; i++) {
            particleManager.addParticle(
                ParticleTypes.EXPLOSION,
                lightningX + (random.nextDouble() - 0.5) * 3,
                lightningY + random.nextDouble() * 8,
                lightningZ + (random.nextDouble() - 0.5) * 3,
                0, 0, 0
            );
        }
        
        // 生成烟雾粒子模拟闪电后的烟雾
        for (int i = 0; i < 8; i++) {
            particleManager.addParticle(
                ParticleTypes.SMOKE,
                lightningX + (random.nextDouble() - 0.5) * 2,
                lightningY + random.nextDouble() * 5,
                lightningZ + (random.nextDouble() - 0.5) * 2,
                (random.nextDouble() - 0.5) * 0.05,
                random.nextDouble() * 0.1,
                (random.nextDouble() - 0.5) * 0.05
            );
        }
    }
    
    /**
     * 获取当前天空闪白亮度（供 WeatherSkyController 使用）。
     * 
     * @return 闪白亮度 [0-1]，0 表示无闪白
     */
    public float getCurrentFlashBrightness() {
        return currentFlashBrightness;
    }
    
    /**
     * 清除事件状态。
     */
    public void clear() {
        currentFlashBrightness = 0f;
        lastLightningTick = -1000;
    }
}
