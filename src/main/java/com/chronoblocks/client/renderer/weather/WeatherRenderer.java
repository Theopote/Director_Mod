package com.chronoblocks.client.renderer.weather;

import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * WeatherRenderer v1.0
 * 
 * <p>负责渲染天气特效（雨雪粒子、雾、风等）。
 * 
 * <p>设计要点：
 * <ul>
 *   <li>根据 WeatherSample 渲染雨雪粒子</li>
 *   <li>处理特殊天气事件（沙暴、暴雪等）</li>
 *   <li>与 Minecraft 原版天气系统兼容</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WeatherRenderer {
    
    private static final WeatherRenderer INSTANCE = new WeatherRenderer();
    
    /** 当前天气采样 */
    private WeatherSample currentSample = WeatherSample.disabled();
    
    /** 粒子生成计时器 */
    private int particleTickCounter = 0;
    
    private WeatherRenderer() {
    }
    
    /**
     * 获取单例实例。
     */
    public static WeatherRenderer get() {
        return INSTANCE;
    }
    
    /**
     * 更新天气采样（由 TimelineWeatherController 调用）。
     */
    public void onWeatherSampleUpdated(WeatherSample sample) {
        this.currentSample = sample != null ? sample : WeatherSample.disabled();
    }
    
    /**
     * 每 tick 调用，渲染天气特效。
     */
    public void tick() {
        if (currentSample == null || !currentSample.enabled) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        
        ClientWorld world = client.world;
        ParticleManager particleManager = client.particleManager;
        Vec3d playerPos = client.player.getEntityPos();
        BlockPos playerBlockPos = client.player.getBlockPos();
        
        particleTickCounter++;
        
        // 渲染降水粒子
        if (currentSample.rainStrength > 0.01f) {
            renderPrecipitation(world, particleManager, playerPos, playerBlockPos);
        }
        
        // 渲染特殊天气事件
        if (currentSample.specialWeatherType != com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe.SpecialWeatherType.NONE
            && currentSample.specialWeatherIntensity > 0.01f) {
            renderSpecialWeather(world, particleManager, playerPos, playerBlockPos);
        }
    }
    
    /**
     * 渲染降水粒子（雨、雪、夹雪）。
     */
    private void renderPrecipitation(ClientWorld world, ParticleManager particleManager, Vec3d playerPos, BlockPos playerBlockPos) {
        // 根据降水类型和强度生成粒子
        float density = currentSample.rainParticleDensity * currentSample.rainStrength;
        if (density < 0.01f) {
            return;
        }
        
        // 每 2-5 tick 生成一次粒子（根据密度调整）
        int interval = Math.max(2, (int) (5 / (density + 0.1f)));
        if (particleTickCounter % interval != 0) {
            return;
        }
        
        // 在玩家周围生成粒子
        int radius = 16; // 粒子生成半径
        int particleCount = (int) (density * 20); // 每次生成的粒子数量
        
        for (int i = 0; i < particleCount; i++) {
            double x = playerPos.x + (world.random.nextDouble() - 0.5) * radius * 2;
            double y = playerPos.y + world.random.nextDouble() * 10 + 5;
            double z = playerPos.z + (world.random.nextDouble() - 0.5) * radius * 2;
            
            // 根据降水类型选择粒子
            switch (currentSample.rainType) {
                case RAIN -> // 雨粒子
                        particleManager.addParticle(
                            ParticleTypes.RAIN,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.1,
                            -0.5 - world.random.nextDouble() * 0.5,
                            (world.random.nextDouble() - 0.5) * 0.1
                        );
                case SNOW -> // 雪粒子
                        particleManager.addParticle(
                            ParticleTypes.SNOWFLAKE,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            -0.1 - world.random.nextDouble() * 0.2,
                            (world.random.nextDouble() - 0.5) * 0.05
                        );
                case SLEET -> {
                    // 夹雪：混合雨和雪
                    if (world.random.nextBoolean()) {
                        particleManager.addParticle(
                            ParticleTypes.RAIN,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.1,
                            -0.5 - world.random.nextDouble() * 0.5,
                            (world.random.nextDouble() - 0.5) * 0.1
                        );
                    } else {
                        particleManager.addParticle(
                            ParticleTypes.SNOWFLAKE,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            -0.1 - world.random.nextDouble() * 0.2,
                            (world.random.nextDouble() - 0.5) * 0.05
                        );
                    }
                }
                default -> {
                    // NONE: 不生成粒子
                }
            }
        }
    }
    
    /**
     * 渲染特殊天气事件。
     */
    private void renderSpecialWeather(ClientWorld world, ParticleManager particleManager, Vec3d playerPos, BlockPos playerBlockPos) {
        float intensity = currentSample.specialWeatherIntensity;
        
        switch (currentSample.specialWeatherType) {
            case SANDSTORM -> {
                // 沙暴：生成大量沙尘粒子
                if (particleTickCounter % 3 == 0) {
                    int count = (int) (intensity * 30);
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 32;
                        double y = playerPos.y + world.random.nextDouble() * 5;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 32;
                        
                        // 使用灰尘粒子模拟沙尘
                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.5 * intensity,
                            world.random.nextDouble() * 0.2 * intensity,
                            (world.random.nextDouble() - 0.5) * 0.5 * intensity
                        );
                    }
                }
            }
            case BLIZZARD -> {
                // 暴雪：密集的雪粒子 + 强风效果
                if (particleTickCounter % 2 == 0) {
                    int count = (int) (intensity * 40);
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 24;
                        double y = playerPos.y + world.random.nextDouble() * 15 + 5;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 24;
                        
                        particleManager.addParticle(
                            ParticleTypes.SNOWFLAKE,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.3 * intensity, // 强风效果
                            -0.3 - world.random.nextDouble() * 0.5,
                            (world.random.nextDouble() - 0.5) * 0.3 * intensity
                        );
                    }
                }
            }
            case FOGBURST -> {
                // 浓雾：生成大量雾粒子
                if (particleTickCounter % 5 == 0) {
                    int count = (int) (intensity * 15);
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 20;
                        double y = playerPos.y + world.random.nextDouble() * 8;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 20;
                        
                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            world.random.nextDouble() * 0.02,
                            (world.random.nextDouble() - 0.5) * 0.05
                        );
                    }
                }
            }
            case HEATWAVE -> {
                // 热浪：生成热扭曲效果（使用云粒子模拟）
                if (particleTickCounter % 10 == 0) {
                    int count = (int) (intensity * 10);
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 16;
                        double y = playerPos.y + world.random.nextDouble() * 3;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 16;
                        
                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            (world.random.nextDouble() - 0.5) * 0.1,
                            world.random.nextDouble() * 0.05,
                            (world.random.nextDouble() - 0.5) * 0.1
                        );
                    }
                }
            }
            default -> {
                // NONE: 不生成粒子
            }
        }
    }
    
    /**
     * 获取当前天气采样（供外部查询）。
     */
    public WeatherSample getCurrentSample() {
        return currentSample;
    }
}

