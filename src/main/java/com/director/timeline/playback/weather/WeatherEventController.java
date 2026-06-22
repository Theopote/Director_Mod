package com.director.timeline.playback.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
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
 *   <li>使用视觉事件 + 声音，适合 Director 的用途</li>
 *   <li>屏幕闪白通过临时提高天空亮度实现</li>
 * </ul>
 * 
 * @author Director Team
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
    
    /** 闪白衰减速度（每 tick 衰减量） — 原来 0.15 衰减太快，改为 0.08 让闪白持续更久 */
    private static final float FLASH_DECAY = 0.08f;
    
    /**
     * ✅ v1.2 修复：使用内部 tick 计数器替代 world.getTime()。
     * 导演模式下 ClientWorldMixin.onTickTime() 冻结了世界时间，
     * 导致 world.getTime() 每帧返回相同值，闪电只触发一次就永远不再触发。
     */
    private long internalTickCounter = 0;
    
    /** 上次触发闪电的内部 tick */
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
        
        // ✅ v1.2 修复：使用内部 tick 计数器，不依赖 world.getTime()
        // 导演模式下世界时间被冻结，world.getTime() 永远返回相同值
        internalTickCounter++;
        long currentTick = internalTickCounter;
        
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
     * ✅ 重构：触发闪电事件（雷声 + 闪白 + 可选真实闪电实体）。
     * 
     * <p>核心逻辑：
     * <ul>
     *   <li>1️⃣ 屏幕/天空闪白（永远触发）</li>
     *   <li>2️⃣ 雷声音效（永远触发）</li>
     *   <li>3️⃣ 真实闪电实体（仅非导演模式）</li>
     *   <li>4️⃣ 视觉粒子效果（永远触发）</li>
     * </ul>
     */
    private void triggerLightning(ClientWorld world, MinecraftClient client, WeatherSample sample, float thunderIntensity) {
        Vec3d playerPos = null;
        if (client.player != null) {
            playerPos = client.player.getEntityPos();
        }

        // === 1️⃣ 屏幕/天空闪白（永远触发） ===
        // ✅ v1.2：增强闪白强度，让闪电更明显
        // 闪白强度基于 thunderIntensity：0.6 ~ 1.0（原来 0.3 ~ 0.8 太暗）
        currentFlashBrightness = 0.6f + thunderIntensity * 0.4f;
        
        // === 2️⃣ 雷声音效（永远触发） ===
        playThunderSound(world, playerPos, thunderIntensity);
        
        // === 3️⃣ 真实闪电实体（仅非导演模式） ===
        if (!sample.visualOnlyThunder && playerPos != null) {
            spawnRealLightning(world, playerPos);
        }
        
        // === 4️⃣ 视觉粒子效果（永远触发） ===
        if (playerPos != null) {
            spawnLightningParticles(world, playerPos);
        }
    }
    
    /**
     * ✅ 新增：生成真实闪电实体（会破坏世界）。
     * 
     * <p>⚠️ 警告：此方法会生成真实的 LightningEntity，可能：
     * <ul>
     *   <li>点燃方块</li>
     *   <li>伤害实体</li>
     *   <li>影响世界状态</li>
     * </ul>
     * 
     * <p>仅在 visualOnlyThunder = false 时调用。
     */
    private void spawnRealLightning(ClientWorld world, Vec3d playerPos) {
        try {
            // 在玩家周围随机位置生成闪电
            int offsetX = random.nextInt(32) - 16;
            int offsetZ = random.nextInt(32) - 16;
            
            // 获取地面高度
            int x = (int) Math.floor(playerPos.x) + offsetX;
            int z = (int) Math.floor(playerPos.z) + offsetZ;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            
            // 创建闪电实体（使用构造函数）
            LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
            lightning.refreshPositionAfterTeleport(x, y, z);
            world.spawnEntity(lightning);
            LOGGER.debug("[WeatherEventController] 生成真实闪电实体 @ ({}, {}, {})", x, y, z);
        } catch (Exception e) {
            LOGGER.debug("[WeatherEventController] 生成真实闪电失败", e);
        }
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
        internalTickCounter = 0;
    }
}
