package com.chronoblocks.client.renderer.weather;

import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

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
        particleTickCounter++;

        // 渲染特殊天气事件（沙暴、暴雪等特殊效果）
        if (currentSample.specialWeatherType != com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe.SpecialWeatherType.NONE
            && currentSample.specialWeatherIntensity > 0.01f) {
            renderSpecialWeather(world, particleManager, playerPos);
        }

        // ⚠️ 保留地面湿度效果（可选：未来可以迁移到 WeatherParticleController）
        if (currentSample.wetness > 0.01f) {
            renderWetness(world, particleManager, playerPos);
        }
    }

    /**
     * Renders special weather particles (sandstorm, blizzard, etc).
     */
    private void renderSpecialWeather(ClientWorld world, ParticleManager particleManager, Vec3d playerPos) {
        float intensity = currentSample.specialWeatherIntensity;
        float windStrength = MathHelper.clamp(currentSample.windStrength, 0f, 1f);
        Vec3d windVec = resolveWindVec(world, windStrength);

        switch (currentSample.specialWeatherType) {
            case SANDSTORM -> {
                if (particleTickCounter % 3 == 0) {
                    int count = Math.max(1, Math.round(intensity * 45));
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 34;
                        double y = playerPos.y + world.random.nextDouble() * 6;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 34;

                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            windVec.x + (world.random.nextDouble() - 0.5) * 0.4 * intensity,
                            world.random.nextDouble() * 0.15 * intensity,
                            windVec.z + (world.random.nextDouble() - 0.5) * 0.4 * intensity
                        );
                    }
                }
            }
            case BLIZZARD -> {
                if (particleTickCounter % 2 == 0) {
                    int count = Math.max(1, Math.round(intensity * 55));
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 28;
                        double y = playerPos.y + world.random.nextDouble() * 20 + 8;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 28;

                        particleManager.addParticle(
                            ParticleTypes.SNOWFLAKE,
                            x, y, z,
                            windVec.x + (world.random.nextDouble() - 0.5) * 0.35 * intensity,
                            -0.7 - world.random.nextDouble() * 0.8,
                            windVec.z + (world.random.nextDouble() - 0.5) * 0.35 * intensity
                        );
                    }
                }
            }
            case FOGBURST -> {
                if (particleTickCounter % 5 == 0) {
                    int count = Math.max(1, Math.round(intensity * 18));
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 24;
                        double y = playerPos.y + world.random.nextDouble() * 10;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 24;

                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            windVec.x * 0.2 + (world.random.nextDouble() - 0.5) * 0.06,
                            world.random.nextDouble() * 0.03,
                            windVec.z * 0.2 + (world.random.nextDouble() - 0.5) * 0.06
                        );
                    }
                }
            }
            case HEATWAVE -> {
                if (particleTickCounter % 10 == 0) {
                    int count = Math.max(1, Math.round(intensity * 12));
                    for (int i = 0; i < count; i++) {
                        double x = playerPos.x + (world.random.nextDouble() - 0.5) * 18;
                        double y = playerPos.y + world.random.nextDouble() * 4;
                        double z = playerPos.z + (world.random.nextDouble() - 0.5) * 18;

                        particleManager.addParticle(
                            ParticleTypes.CLOUD,
                            x, y, z,
                            windVec.x * 0.15 + (world.random.nextDouble() - 0.5) * 0.12,
                            world.random.nextDouble() * 0.05,
                            windVec.z * 0.15 + (world.random.nextDouble() - 0.5) * 0.12
                        );
                    }
                }
            }
            default -> {
                // NONE
            }
        }
    }

    private Vec3d resolveWindVec(ClientWorld world, float windStrength) {
        if (windStrength <= 0.001f) {
            return Vec3d.ZERO;
        }

        double t = world.getTime() * 0.02;
        double angle = t * 0.4 + Math.sin(t * 0.15) * 0.4;
        double speed = windStrength * 0.6;
        return new Vec3d(Math.cos(angle) * speed, 0.0, Math.sin(angle) * speed);
    }

    /**
     * Renders ground wetness particles around the player.
     */
    private void renderWetness(ClientWorld world, ParticleManager particleManager, Vec3d playerPos) {
        float wetness = currentSample.wetness;
        if (wetness < 0.01f) {
            return;
        }

        int interval = Math.max(4, (int) (12 / (wetness + 0.05f)));
        if (particleTickCounter % interval != 0) {
            return;
        }

        int radius = 24;
        int particleCount = Math.max(1, Math.round(wetness * 18f));

        for (int i = 0; i < particleCount; i++) {
            double x = playerPos.x + (world.random.nextDouble() - 0.5) * radius * 2;
            double z = playerPos.z + (world.random.nextDouble() - 0.5) * radius * 2;
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double y = topY + 0.02 + world.random.nextDouble() * 0.06;

            particleManager.addParticle(
                ParticleTypes.DRIPPING_WATER,
                x, y, z,
                0,
                0.02 + world.random.nextDouble() * 0.03,
                0
            );
        }
    }

    public WeatherSample getCurrentSample() {
        return currentSample;
    }
}

