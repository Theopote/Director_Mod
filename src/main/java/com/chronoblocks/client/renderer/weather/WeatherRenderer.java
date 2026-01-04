package com.chronoblocks.client.renderer.weather;

import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * WeatherRenderer v1.1
 * 
 * <p>负责渲染特殊天气特效（沙暴、暴雪等特殊效果）。
 * 
 * <p>设计要点：
 * <ul>
 *   <li>⚠️ 普通降水（雨/雪）已由 WeatherParticleController 统一处理，此处不再生成</li>
 *   <li>⚠️ 雷暴/闪电效果已由 WeatherEventController 统一处理，此处不再生成</li>
 *   <li>处理特殊天气事件（沙暴、暴雪、浓雾、热浪等）</li>
 *   <li>可选：地面湿度效果（未来可能迁移到 WeatherParticleController）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 移除重复的降水/闪电逻辑
 */
public class WeatherRenderer {
    
    private static final WeatherRenderer INSTANCE = new WeatherRenderer();
    
    /** 当前天气采样 */
    private WeatherSample currentSample = WeatherSample.disabled();
    
    /** 粒子生成计时器 */
    private int particleTickCounter = 0;
    
    /** 闪电生成计时器（用于控制闪电频率） */
    private int lightningTickCounter = 0;
    
    /** 上次闪电生成的时间（tick） */
    private int lastLightningTick = -1000;
    
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
        
        // ⚠️ 禁用降水粒子渲染（已由 WeatherParticleController 统一处理）
        // renderPrecipitation() 与 WeatherParticleController 重复，会导致粒子密度不一致
        // if (currentSample.rainStrength > 0.01f) {
        //     renderPrecipitation(world, particleManager, playerPos, playerBlockPos);
        // }
        
        // 渲染特殊天气事件（沙暴、暴雪等特殊效果）
        if (currentSample.specialWeatherType != com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe.SpecialWeatherType.NONE
            && currentSample.specialWeatherIntensity > 0.01f) {
            renderSpecialWeather(world, particleManager, playerPos, playerBlockPos);
        }
        
        // ⚠️ 禁用雷暴/闪电效果（已由 WeatherEventController 统一处理）
        // renderLightning() 与 WeatherEventController 重复
        // if (currentSample.thunderIntensity > 0.01f) {
        //     renderLightning(world, playerPos, playerBlockPos);
        // }
        
        // ⚠️ 保留地面湿度效果（可选：未来可以迁移到 WeatherParticleController）
        if (currentSample.wetness > 0.01f) {
            renderWetness(world, particleManager, playerPos, playerBlockPos);
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
     * 渲染雷暴/闪电效果。
     * 
     * <p>注意：Minecraft 的闪电是服务端控制的，客户端无法直接生成真实的闪电实体。
     * 这里通过生成闪电粒子效果和屏幕闪光来模拟闪电。
     */
    private void renderLightning(ClientWorld world, Vec3d playerPos, BlockPos playerBlockPos) {
        lightningTickCounter++;
        
        // 根据闪电频率计算生成间隔（每秒 lightningFrequency 次）
        float frequency = currentSample.lightningFrequency;
        if (frequency <= 0.01f) {
            return;
        }
        
        // 计算两次闪电之间的 tick 数（20 tick = 1秒）
        int interval = Math.max(20, (int) (20.0f / frequency));
        
        // 检查是否应该生成闪电
        if (lightningTickCounter - lastLightningTick < interval) {
            return;
        }
        
        lastLightningTick = lightningTickCounter;
        
        // 在玩家周围随机位置生成闪电效果
        // 注意：这里只能生成视觉效果，无法生成真实的闪电实体（需要服务端支持）
        double lightningX = playerPos.x + (world.random.nextDouble() - 0.5) * 64;
        double lightningY = playerPos.y + world.random.nextDouble() * 20 + 10;
        double lightningZ = playerPos.z + (world.random.nextDouble() - 0.5) * 64;
        
        // 生成闪电粒子效果（使用多种粒子模拟闪电）
        ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
        if (particleManager != null) {
            // 生成爆炸粒子模拟闪电闪光
            for (int i = 0; i < 5; i++) {
                particleManager.addParticle(
                    ParticleTypes.EXPLOSION,
                    lightningX + (world.random.nextDouble() - 0.5) * 3,
                    lightningY + world.random.nextDouble() * 8,
                    lightningZ + (world.random.nextDouble() - 0.5) * 3,
                    0, 0, 0
                );
            }
            // 生成烟雾粒子模拟闪电后的烟雾
            for (int i = 0; i < 8; i++) {
                particleManager.addParticle(
                    ParticleTypes.SMOKE,
                    lightningX + (world.random.nextDouble() - 0.5) * 2,
                    lightningY + world.random.nextDouble() * 5,
                    lightningZ + (world.random.nextDouble() - 0.5) * 2,
                    (world.random.nextDouble() - 0.5) * 0.05,
                    world.random.nextDouble() * 0.1,
                    (world.random.nextDouble() - 0.5) * 0.05
                );
            }
        }
        
        // ⭐ 注意：真实的闪电实体（LightningEntity）需要服务端生成
        // 客户端只能通过粒子效果和屏幕闪光来模拟
        // 如果需要真实的闪电，需要服务端支持或使用命令生成
        // 这里通过粒子效果和天空暗度变化来模拟闪电效果
    }
    
    /**
     * 渲染地面湿度效果（通过粒子模拟水坑/湿润效果）。
     * 
     * <p>注意：Minecraft 没有原生的地面湿度渲染系统，这里通过生成水滴粒子
     * 和反射效果粒子来模拟湿润的地面。
     */
    private void renderWetness(ClientWorld world, ParticleManager particleManager, Vec3d playerPos, BlockPos playerBlockPos) {
        float wetness = currentSample.wetness;
        if (wetness < 0.01f) {
            return;
        }
        
        // 每 10-20 tick 生成一次湿度粒子（根据湿度强度调整）
        int interval = Math.max(10, (int) (20 / (wetness + 0.1f)));
        if (particleTickCounter % interval != 0) {
            return;
        }
        
        // 在玩家周围的地面生成水滴粒子
        int radius = 16;
        int particleCount = (int) (wetness * 5); // 湿度越高，粒子越多
        
        for (int i = 0; i < particleCount; i++) {
            double x = playerPos.x + (world.random.nextDouble() - 0.5) * radius * 2;
            double y = playerPos.y - 0.5; // 地面附近
            double z = playerPos.z + (world.random.nextDouble() - 0.5) * radius * 2;
            
            // 使用水滴粒子模拟湿润效果
            particleManager.addParticle(
                ParticleTypes.DRIPPING_WATER,
                x, y, z,
                0,
                0.01 + world.random.nextDouble() * 0.02,
                0
            );
        }
    }
    
    /**
     * 获取当前天气采样（供外部查询）。
     */
    public WeatherSample getCurrentSample() {
        return currentSample;
    }
}

