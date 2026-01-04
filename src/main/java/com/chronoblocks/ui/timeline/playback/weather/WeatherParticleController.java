package com.chronoblocks.ui.timeline.playback.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * WeatherParticleController v1.0
 * -----------------------------------------
 * ChronoBlocks 天气轨道：雨滴/雪花粒子生成控制器
 *
 * <p>核心能力：
 * <ul>
 *   <li>禁用原版随机雨雪粒子（通过 Mixin 实现）</li>
 *   <li>根据 WeatherSample 控制粒子数量（雨量、雪量）</li>
 *   <li>根据风力为粒子添加水平速度</li>
 *   <li>根据雷暴强度增加雨滴密度</li>
 *   <li>自动限制粒子数量避免卡顿</li>
 * </ul>
 *
 * <p>高度轻量，tick 驱动，无阻塞
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WeatherParticleController {

    // --------- Singleton ---------
    private static final WeatherParticleController INSTANCE = new WeatherParticleController();
    
    public static WeatherParticleController getInstance() { 
        return INSTANCE; 
    }

    private WeatherParticleController() {}

    // --------- Configurable Limits ---------
    /** 屏幕可见区域的最大雨滴数量 */
    private static final int MAX_RAIN_PARTICLES = 250;

    /** 最大雪花粒子数量（比雨大一些） */
    private static final int MAX_SNOW_PARTICLES = 350;

    private final Random random = new Random();

    /** 当前是否启用天气粒子系统 */
    private boolean enabled = true;

    /**
     * 设置是否启用粒子系统。
     */
    public void setEnabled(boolean v) { 
        this.enabled = v; 
    }

    /**
     * 检查是否启用。
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ============================================================
    // 主入口：每 tick 调用一次（TimelineWeatherController -> update()）
    // ============================================================
    
    /** 当前天气采样（缓存，供每tick使用） */
    private WeatherSample currentSample = null;
    
    /**
     * 更新当前天气采样（由 TimelineWeatherController 调用）。
     * 这个方法只更新缓存，不生成粒子。
     */
    public void updateSample(WeatherSample sample) {
        this.currentSample = sample;
    }
    
    /**
     * 每 tick 调用，持续生成粒子。
     * 由 TimelinePanel 在 doRender() 中每帧调用。
     */
    public void tick() {
        if (!enabled || currentSample == null || !currentSample.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }

        ClientWorld world = client.world;
        Vec3d playerPos = client.player.getEntityPos();

        // 计算与天气强度相关的生成数量
        float rainIntensity = currentSample.rainIntensity;
        float snowIntensity = currentSample.snowIntensity;
        float thunderBoost = currentSample.thunderIntensity * 0.6f;

        // 最终雨滴强度（雷暴时增强）
        float finalRainIntensity = Math.min(1f, rainIntensity + thunderBoost);

        // 每 tick 生成粒子数量（动态）
        int targetRain = (int) (MAX_RAIN_PARTICLES * finalRainIntensity);
        int targetSnow = (int) (MAX_SNOW_PARTICLES * snowIntensity);

        spawnRain(world, playerPos, targetRain, currentSample);
        spawnSnow(world, playerPos, targetSnow, currentSample);
    }
    
    /**
     * 根据 WeatherSample 更新粒子生成（向后兼容方法）。
     * 
     * <p>由 TimelineWeatherController 在 applyToWorld() 中调用。
     * 现在只更新缓存，实际粒子生成由 tick() 方法每帧执行。
     * 
     * @param sample 当前天气采样
     */
    public void updateParticles(WeatherSample sample) {
        updateSample(sample);
    }

    // ============================================================
    // 雨粒子生成
    // ============================================================
    
    /**
     * 生成雨滴粒子。
     * 
     * @param world 客户端世界
     * @param playerPos 玩家位置
     * @param count 目标粒子数量
     * @param sample 天气采样
     */
    private void spawnRain(ClientWorld world, Vec3d playerPos, int count, WeatherSample sample) {
        if (count <= 0) {
            return;
        }

        float wind = sample.windStrength;
        // 风力影响：强风时粒子会有水平偏移（增强风力效果）
        float windX = (random.nextFloat() - 0.5f) * 1.5f * wind;
        float windZ = (random.nextFloat() - 0.5f) * 1.5f * wind;

        // 生成范围（半径）- 增大范围，避免看起来像一圈实体
        int radius = 32;

        for (int i = 0; i < count; i++) {
            double x = playerPos.x + randomSpread(radius);
            double y = playerPos.y + 8 + random.nextDouble() * 3; // 从头顶高处落下
            double z = playerPos.z + randomSpread(radius);

            BlockPos pos = BlockPos.ofFloored(x, y, z);
            // 简单检查：如果位置太低（在基岩层以下），跳过
            if (pos.getY() < -64) {
                continue;
            }

            world.addParticleClient(
                    ParticleTypes.RAIN,
                    x, y, z,
                    windX,
                    -0.7 - random.nextDouble() * 0.3, // 雨滴下落速度
                    windZ
            );
        }
    }

    // ============================================================
    // 雪粒子生成（更大、更轻、更受风力影响）
    // ============================================================
    
    /**
     * 生成雪花粒子。
     * 
     * <p>雪花比雨滴更大、更轻、更受风力影响，下落速度更慢。
     * 
     * @param world 客户端世界
     * @param playerPos 玩家位置
     * @param count 目标粒子数量
     * @param sample 天气采样
     */
    private void spawnSnow(ClientWorld world, Vec3d playerPos, int count, WeatherSample sample) {
        if (count <= 0) {
            return;
        }

        float wind = sample.windStrength;
        // 雪花更受风力影响（水平偏移更大，增强风力效果）
        float windX = (random.nextFloat() - 0.5f) * 0.8f * wind;
        float windZ = (random.nextFloat() - 0.5f) * 0.8f * wind;

        // 雪花生成范围稍大一些 - 增大范围，避免看起来像一圈实体
        int radius = 36;

        for (int i = 0; i < count; i++) {
            double x = playerPos.x + randomSpread(radius);
            double y = playerPos.y + 8 + random.nextDouble() * 3;
            double z = playerPos.z + randomSpread(radius);

            BlockPos pos = BlockPos.ofFloored(x, y, z);
            // 简单检查：如果位置太低（在基岩层以下），跳过
            if (pos.getY() < -64) {
                continue;
            }

            world.addParticleClient(
                    ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    windX,
                    -0.2 - random.nextDouble() * 0.15, // 雪花下落速度更慢
                    windZ
            );
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================
    
    /**
     * 生成半径范围内的随机偏移。
     * 
     * @param radius 半径
     * @return 随机偏移值
     */
    private double randomSpread(int radius) {
        return (random.nextDouble() - 0.5) * radius * 2;
    }
}

