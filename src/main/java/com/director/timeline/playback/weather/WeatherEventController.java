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
     */
    private float lastTimelineTime = Float.NaN;
    public void tick(WeatherSample sample, float time) {
        var effects = TimelineWeatherController.getInstance().effects();
        if (sample == null || !sample.enabled || effects == null || !Float.isFinite(time)) {
            clear(); return;
        }
        currentFlashBrightness = effects.flash(time);
        float previous = lastTimelineTime; lastTimelineTime = time;
        var client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        var model = com.director.timeline.playback.TimelinePlaybackSampler.resolveModel();
        // Seeking, preview and export never replay live sounds or world-side effects.
        if (model == null || !model.isPlaying() || !Float.isFinite(previous)
                || time <= previous || time-previous > .25f
                || com.director.export.video.VideoExportManager.getInstance().isExporting()) return;
        long start=(long)Math.floor(previous*20d)+1, end=(long)Math.floor(time*20d);
        for(long tick=start;tick<=end;tick++) if(effects.lightningAt(tick)) {
            random.setSeed(effects.seedFor(tick));
            triggerLightning(client.world, client, sample, sample.thunderIntensity);
        }
        currentFlashBrightness = effects.flash(time);
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
            var camera = com.director.timeline.playback.camera.CameraRuntime.getInstance();
            playerPos = camera.isTimelineOwner() ? camera.getCurrentSample().position()
                    : client.gameRenderer.getCamera().getPos();
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
        lastTimelineTime = Float.NaN;
    }
}
