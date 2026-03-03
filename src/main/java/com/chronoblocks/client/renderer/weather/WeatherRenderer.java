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
 * <p>
 * 负责渲染特殊天气特效（沙暴、暴雪等特殊效果）。
 *
 * <p>
 * 设计要点：
 * <ul>
 * <li>⚠️ 普通降水（雨/雪）已由 WeatherParticleController 统一处理，此处不再生成</li>
 * <li>⚠️ 雷暴/闪电效果已由 WeatherEventController 统一处理，此处不再生成</li>
 * <li>处理特殊天气事件（沙暴、暴雪、浓雾、热浪等）</li>
 * <li>可选：地面湿度效果（未来可能迁移到 WeatherParticleController）</li>
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
        if (client == null || client.world == null || client.gameRenderer == null) {
            return;
        }

        ClientWorld world = client.world;
        ParticleManager particleManager = client.particleManager;
        // ⭐ 使用游戏摄像机位置（而非玩家位置），确保导演模式下特效跟随摄像机
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        Vec3d playerPos = (camera != null) ? camera.getPos()
                : (client.player != null ? client.player.getEntityPos() : null);
        if (playerPos == null) return;
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
        Vec3d windVec = resolveWindVec(currentSample, windStrength);

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
                                windVec.z + (world.random.nextDouble() - 0.5) * 0.4 * intensity);
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
                                windVec.z + (world.random.nextDouble() - 0.5) * 0.35 * intensity);
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
                                windVec.z * 0.2 + (world.random.nextDouble() - 0.5) * 0.06);
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
                                windVec.z * 0.15 + (world.random.nextDouble() - 0.5) * 0.12);
                    }
                }
            }
            default -> {
                // NONE
            }
        }
    }

    /**
     * ✅ 修复：使用 WeatherSample 中的风方向，而不是时间变化的风。
     */
    private Vec3d resolveWindVec(WeatherSample sample, float windStrength) {
        if (windStrength <= 0.001f) {
            return Vec3d.ZERO;
        }

        // ✅ 使用 WeatherSample 中的风方向（度）
        // 0° = +X（东），90° = +Z（南），180° = -X（西），270° = -Z（北）
        double angleRad = Math.toRadians(sample.windDirectionDegrees);
        double speed = windStrength * 0.6;
        return new Vec3d(Math.cos(angleRad) * speed, 0.0, Math.sin(angleRad) * speed);
    }

    /**
     * ✅ 修复：增强地面湿度效果，使其更明显可见。
     * 
     * <p>
     * 改进：
     * - 增加粒子数量（从最多18个增加到最多60个）
     * - 减少生成间隔（从每4-12 tick改为每1-3 tick）
     * - 增加生成半径（从24格增加到32格）
     * - 使用多种粒子类型（水滴、雨滴、水花）增强视觉效果
     */
    private void renderWetness(ClientWorld world, ParticleManager particleManager, Vec3d playerPos) {
        float wetness = currentSample.wetness;
        if (wetness < 0.01f) {
            return;
        }

        // ✅ 修复：大幅增加生成频率，让效果密集连续
        // 湿度越高，生成越频繁（每1 tick生成一次，最低湿度也每2 tick）
        int interval = wetness > 0.5f ? 1 : 2;
        if (particleTickCounter % interval != 0) {
            return;
        }

        // ✅ 修复：进一步扩大生成半径和粒子数量，确保"全屏有雨"感
        int radius = 48; // 从32增加到48
        int baseParticleCount = Math.max(10, Math.round(wetness * 120f)); // 从60增加到120

        for (int i = 0; i < baseParticleCount; i++) {
            double x = playerPos.x + (world.random.nextDouble() - 0.5) * radius * 2;
            double z = playerPos.z + (world.random.nextDouble() - 0.5) * radius * 2;
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double y = topY + 0.02 + world.random.nextDouble() * 0.08;

            // 地面湿润效果：混合使用 RAIN 和 SPLASH 粒子
            // ⚠️ 不使用 DRIPPING_WATER（蓝色水滴，与原版雨风格不一致）
            float rand = world.random.nextFloat();
            if (rand < 0.5f) {
                // 50% 概率：雨滴粒子（地面溅起的小水滴）
                particleManager.addParticle(
                        ParticleTypes.RAIN,
                        x, y + 0.1 + world.random.nextDouble() * 0.3,
                        z,
                        (world.random.nextDouble() - 0.5) * 0.06,
                        -0.05 - world.random.nextDouble() * 0.1,
                        (world.random.nextDouble() - 0.5) * 0.06);
            } else {
                // 50% 概率：水花粒子（地面上的水花效果）
                particleManager.addParticle(
                        ParticleTypes.SPLASH,
                        x, y, z,
                        (world.random.nextDouble() - 0.5) * 0.05,
                        0.01 + world.random.nextDouble() * 0.02,
                        (world.random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    public WeatherSample getCurrentSample() {
        return currentSample;
    }
}
