package com.chronoblocks.ui.timeline.playback.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * WeatherParticleController v1.2
 * ------------------------------------------------------------
 * 目标：
 * 1) 雨/雪强度变化"肉眼明显"
 * 2) 生成高度更高（结合 Heightmap），更像从天空落下并能落地
 * 3) 风有方向且可见（降水偏斜）
 * 4) 发射率按"每秒粒子数"驱动，避免 FPS/每帧调用导致的密度漂移
 */
public class WeatherParticleController {

    private static final WeatherParticleController INSTANCE = new WeatherParticleController();

    public static WeatherParticleController getInstance() {
        return INSTANCE;
    }

    private WeatherParticleController() {
    }

    // ---------------- Config ----------------

    /** 以"每秒"为单位的最大雨滴发射率（强度=1） */
    private static final float MAX_RAIN_PPS = 120000f;

    /** 最大雪花发射率（强度=1） */
    private static final float MAX_SNOW_PPS = 80000f;

    /** 粒子生成半径（格） */
    private static final int BASE_RADIUS_RAIN = 72;
    private static final int BASE_RADIUS_SNOW = 64;
    private static final int MAX_RADIUS_RAIN = 200;
    private static final int MAX_RADIUS_SNOW = 200;

    /** 至少高于摄像机/玩家多少格开始生成（避免贴脸） */
    private static final int MIN_ABOVE_CAMERA_RAIN = 32;

    /** 在地形顶部再抬高多少格，确保"从天落下"的感觉 */
    private static final int ABOVE_SURFACE_PADDING = 24;
    private static final int ABOVE_SURFACE_PADDING_SNOW = 2;
    private static final int SNOW_SURFACE_EXTRA = 26;

    /** 每 tick 允许生成的最大粒子数（硬限，防止卡顿） */
    private static final int HARD_CAP_PER_TICK = 8000;

    /** 雨下落速度范围 */
    private static final float RAIN_FALL_MIN = 3.4f;
    private static final float RAIN_FALL_MAX = 6.2f;

    /** 雪下落速度范围 */
    private static final float SNOW_FALL_MIN = 0.45f;
    private static final float SNOW_FALL_MAX = 0.90f;

    /** 风对水平速度的放大（可见性关键） */
    private static final float WIND_HORIZONTAL_SCALE_RAIN = 3.50f;
    private static final float WIND_HORIZONTAL_SCALE_SNOW = 1.05f;

    // ---------------- State ----------------

    private final Random random = new Random();

    private boolean enabled = true;
    private WeatherSample currentSample;

    /** 发射率累加器（以粒子数为单位） */
    private float rainAcc = 0f;
    private float snowAcc = 0f;

    /** 上次更新的时间戳（纳秒），用于计算实际时间差 */
    private long lastUpdateNanos = -1L;

    public void setEnabled(boolean v) {
        this.enabled = v;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateSample(WeatherSample sample) {
        this.currentSample = sample;
    }

    /**
     * 每 tick 调用一次（建议挂到 ClientTickEvents.END_CLIENT_TICK）。
     */
    public void tick() {
        if (!enabled || currentSample == null || !currentSample.enabled)
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null)
            return;

        ClientWorld world = client.world;
        Vec3d camPos = client.player.getCameraPosVec(1.0f);
        float deltaSeconds = computeDeltaSeconds();
        if (deltaSeconds <= 0f) {
            return;
        }

        int viewDistance = Math.max(2, client.options.getViewDistance().getValue());
        int rainRadius = resolveRadius(viewDistance, BASE_RADIUS_RAIN, MAX_RADIUS_RAIN);
        int snowRadius = resolveRadius(viewDistance, BASE_RADIUS_SNOW, MAX_RADIUS_SNOW);

        // 强度
        float rainI = clamp01(currentSample.rainIntensity);
        float snowI = clamp01(currentSample.snowIntensity);

        // 雷暴增强雨幕（让 storm 的"雨更大"直观）
        float thunderBoost = clamp01(currentSample.thunderIntensity) * 0.65f;
        float finalRainI = clamp01(rainI + thunderBoost);

        // 发射率（每秒粒子数）
        float rainPps = MAX_RAIN_PPS * finalRainI;
        float snowPps = MAX_SNOW_PPS * snowI;

        rainRadius = resolveIntensityRadius(rainRadius, BASE_RADIUS_RAIN, MAX_RADIUS_RAIN, finalRainI);
        snowRadius = resolveIntensityRadius(snowRadius, BASE_RADIUS_SNOW, MAX_RADIUS_SNOW, snowI);
        snowRadius = MathHelper.clamp(Math.round(snowRadius * (1.0f + snowI * 0.8f)), BASE_RADIUS_SNOW,
                MAX_RADIUS_SNOW);
        // accumulator: integrate by deltaSeconds for stable PPS
        rainAcc += rainPps * deltaSeconds;
        snowAcc += snowPps * deltaSeconds;

        int rainToSpawn = (int) rainAcc;
        int snowToSpawn = (int) snowAcc;

        if (rainToSpawn > 0)
            rainAcc -= rainToSpawn;
        if (snowToSpawn > 0)
            snowAcc -= snowToSpawn;

        int total = rainToSpawn + snowToSpawn;
        if (total <= 0)
            return;

        // 硬限：避免强度过高时瞬间爆量
        int hardCap = computeHardCap(deltaSeconds);
        if (total > hardCap) {
            float scale = (float) hardCap / (float) total;
            rainToSpawn = Math.max(0, (int) (rainToSpawn * scale));
            snowToSpawn = Math.max(0, (int) (snowToSpawn * scale));
        }

        if (rainToSpawn > 0)
            spawnRain(world, camPos, rainRadius, rainToSpawn, currentSample, finalRainI);
        if (snowToSpawn > 0)
            spawnSnow(world, camPos, snowRadius, snowToSpawn, currentSample);
    }

    // ---------------- Spawn ----------------

    private void spawnRain(ClientWorld world, Vec3d camPos, int radius, int count, WeatherSample sample,
            float intensity) {
        if (count <= 0)
            return;

        // ✅ 修复：使用 WeatherSample 中的风方向和强度
        float wind = clamp01(sample.windStrength);
        Vec3d windVec = computeWindVector(sample, wind);

        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double yFromSurface = topY + ABOVE_SURFACE_PADDING + random.nextDouble() * 32.0;
            double yFromCamera = camPos.y + MIN_ABOVE_CAMERA_RAIN + random.nextDouble() * 18.0;
            double y = Math.max(yFromSurface, yFromCamera);

            // 垂直速度：下落更快更像雨
            double vy = -(RAIN_FALL_MIN + random.nextDouble() * (RAIN_FALL_MAX - RAIN_FALL_MIN));

            // 水平速度：风主导 + 少量抖动
            double vx = windVec.x + (random.nextDouble() - 0.5) * 0.08;
            double vz = windVec.z + (random.nextDouble() - 0.5) * 0.08;

            int streakSegments = intensity >= 0.85f ? 4 : (intensity >= 0.65f ? 3 : (intensity >= 0.35f ? 2 : 1));
            double segStep = intensity >= 0.85f ? 1.4 : (intensity >= 0.65f ? 1.7 : 2.2);
            for (int s = 0; s < streakSegments; s++) {
                double segY = y - s * segStep;
                if (segY <= topY + 0.3) {
                    break;
                }
                world.addImportantParticleClient(ParticleTypes.FALLING_WATER, x, segY, z, vx, vy, vz);
            }

            if (intensity > 0.25f && random.nextFloat() < intensity * 0.18f) {
                double splashX = x + (random.nextDouble() - 0.5) * 0.6;
                double splashZ = z + (random.nextDouble() - 0.5) * 0.6;
                double splashY = topY + 0.05;
                world.addParticleClient(ParticleTypes.RAIN, splashX, splashY, splashZ, 0, 0, 0);
            }
        }
    }

    private void spawnSnow(ClientWorld world, Vec3d camPos, int radius, int count, WeatherSample sample) {
        if (count <= 0)
            return;

        // ✅ 修复：使用 WeatherSample 中的风方向和强度
        float wind = clamp01(sample.windStrength);
        Vec3d windVec = computeWindVectorSnow(sample, wind);

        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double y = topY + ABOVE_SURFACE_PADDING_SNOW + random.nextDouble() * SNOW_SURFACE_EXTRA;

            // 雪更慢、更飘
            double vy = -(SNOW_FALL_MIN + random.nextDouble() * (SNOW_FALL_MAX - SNOW_FALL_MIN));

            // 风更明显
            double vx = windVec.x + (random.nextDouble() - 0.5) * 0.04;
            double vz = windVec.z + (random.nextDouble() - 0.5) * 0.04;

            world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, vx, vy, vz);
        }
    }

    // ---------------- Wind model ----------------

    /**
     * ✅ 修复：使用 WeatherSample 中的风方向，而不是时间变化的风。
     * 
     * <p>
     * 现在风方向由导演在时间线上控制，可以实现：
     * - 固定风向
     * - 风向随时间轴动画
     * - 镜头级导演控制
     */
    private Vec3d computeWindVector(WeatherSample sample, float windStrength01) {
        if (windStrength01 <= 0.001f)
            return Vec3d.ZERO;

        // ✅ 使用 WeatherSample 中的风方向（度）
        // 0° = +X（东），90° = +Z（南），180° = -X（西），270° = -Z（北）
        double angleRad = Math.toRadians(sample.windDirectionDegrees);
        double dirX = Math.cos(angleRad);
        double dirZ = Math.sin(angleRad);

        // 速度大小（关键：让肉眼看得见）- 使用雨的风速作为基础
        double speed = windStrength01 * WIND_HORIZONTAL_SCALE_RAIN;
        return new Vec3d(dirX * speed, 0.0, dirZ * speed);
    }

    /**
     * ✅ 修复：使用 WeatherSample 中的风方向，而不是时间变化的风。
     * 雪使用与雨相同的风向，但风速比例更大。
     */
    private Vec3d computeWindVectorSnow(WeatherSample sample, float windStrength01) {
        if (windStrength01 <= 0.001f)
            return Vec3d.ZERO;

        // ✅ 使用 WeatherSample 中的风方向（与雨相同）
        double angleRad = Math.toRadians(sample.windDirectionDegrees);
        double dirX = Math.cos(angleRad);
        double dirZ = Math.sin(angleRad);

        // 雪的风速更大（更明显）
        double speed = windStrength01 * WIND_HORIZONTAL_SCALE_SNOW;
        return new Vec3d(dirX * speed, 0.0, dirZ * speed);
    }

    // ---------------- Utils ----------------

    /**
     * ✅ 修复：计算实际时间差，但确保最小值为游戏 tick 时间（0.05秒）。
     * 
     * <p>
     * 原因：此方法在 UI 渲染循环中调用，UI 帧率可能很高（60fps+），
     * 导致 deltaSeconds 很小（如 0.016秒），进而限制了粒子生成。
     * 
     * <p>
     * 解决方案：
     * - 使用实际时间差，但设置最小值为游戏 tick 时间（0.05秒）
     * - 这样可以确保粒子生成率稳定，不受 UI 帧率影响
     * - 同时避免在 UI 帧率很高时过度生成粒子
     */
    private float computeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastUpdateNanos <= 0L) {
            lastUpdateNanos = now;
            return 1f / 20f; // 首次调用返回一个 tick 时间
        }
        float delta = (now - lastUpdateNanos) / 1_000_000_000f;
        lastUpdateNanos = now;

        // ✅ 关键修复：确保最小值为游戏 tick 时间（0.05秒）
        // 这样可以避免 UI 高帧率时 delta 太小导致粒子生成受限
        // 同时限制最大值为 0.2 秒，避免长时间暂停后突然生成大量粒子
        return MathHelper.clamp(Math.max(delta, 1f / 20f), 1f / 20f, 0.2f);
    }

    private int resolveRadius(int viewDistanceChunks, int baseRadius, int maxRadius) {
        int view = Math.max(2, viewDistanceChunks);
        int scaled = view * 8;
        return MathHelper.clamp(scaled, baseRadius, maxRadius);
    }

    private int resolveIntensityRadius(int baseRadius, int minRadius, int maxRadius, float intensity) {
        float scale = 0.75f + intensity * 0.55f;
        return MathHelper.clamp(Math.round(baseRadius * scale), minRadius, maxRadius);
    }

    /**
     * ✅ 修复：基于实际时间差计算硬限。
     * 
     * <p>
     * 硬限应该基于实际时间差，但确保最小值为基于游戏 tick 时间的硬限。
     * 这样可以避免在 UI 高帧率时过度生成粒子。
     */
    private int computeHardCap(float deltaSeconds) {
        // 基于实际时间差计算，但确保最小值为基于游戏 tick 时间的硬限
        float scaled = HARD_CAP_PER_TICK * deltaSeconds * 20f;
        // 最小硬限（基于一个游戏 tick）
        return Math.max(HARD_CAP_PER_TICK, Math.round(scaled));
    }

    private double spread(int radius) {
        // 使用近远混合：大多数点较近，少数点更远，视觉更自然
        double u = random.nextDouble();
        double r = (u < 0.75) ? (radius * 0.55) : radius;
        return (random.nextDouble() - 0.5) * 2.0 * r;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
