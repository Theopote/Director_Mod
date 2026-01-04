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

    private WeatherParticleController() {}

    // ---------------- Config ----------------

    /** 以"每秒"为单位的最大雨滴发射率（强度=1） */
    private static final float MAX_RAIN_PPS = 4200f;

    /** 最大雪花发射率（强度=1） */
    private static final float MAX_SNOW_PPS = 2400f;

    /** 粒子生成半径（格） */
    private static final int BASE_RADIUS_RAIN = 42;
    private static final int BASE_RADIUS_SNOW = 48;

    /** 至少高于摄像机/玩家多少格开始生成（避免贴脸） */
    private static final int MIN_ABOVE_CAMERA_RAIN = 26;
    private static final int MIN_ABOVE_CAMERA_SNOW = 22;

    /** 在地形顶部再抬高多少格，确保"从天落下"的感觉 */
    private static final int ABOVE_SURFACE_PADDING = 14;

    /** 每 tick 允许生成的最大粒子数（硬限，防止卡顿） */
    private static final int HARD_CAP_PER_TICK = 900;

    /** 雨下落速度范围 */
    private static final float RAIN_FALL_MIN = 0.9f;
    private static final float RAIN_FALL_MAX = 1.4f;

    /** 雪下落速度范围 */
    private static final float SNOW_FALL_MIN = 0.12f;
    private static final float SNOW_FALL_MAX = 0.22f;

    /** 风对水平速度的放大（可见性关键） */
    private static final float WIND_HORIZONTAL_SCALE_RAIN = 0.55f;
    private static final float WIND_HORIZONTAL_SCALE_SNOW = 0.85f;

    // ---------------- State ----------------

    private final Random random = new Random();

    private boolean enabled = true;
    private WeatherSample currentSample;

    /** 发射率累加器（以粒子数为单位） */
    private float rainAcc = 0f;
    private float snowAcc = 0f;

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
        if (!enabled || currentSample == null || !currentSample.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;

        ClientWorld world = client.world;
        Vec3d camPos = client.player.getCameraPosVec(1.0f);

        // 强度
        float rainI = clamp01(currentSample.rainIntensity);
        float snowI = clamp01(currentSample.snowIntensity);

        // 雷暴增强雨幕（让 storm 的"雨更大"直观）
        float thunderBoost = clamp01(currentSample.thunderIntensity) * 0.65f;
        float finalRainI = clamp01(rainI + thunderBoost);

        // 发射率（每秒粒子数）
        float rainPps = MAX_RAIN_PPS * finalRainI;
        float snowPps = MAX_SNOW_PPS * snowI;

        // accumulator：每 tick（20Hz）增加 pps/20
        rainAcc += rainPps / 20f;
        snowAcc += snowPps / 20f;

        int rainToSpawn = (int) rainAcc;
        int snowToSpawn = (int) snowAcc;

        if (rainToSpawn > 0) rainAcc -= rainToSpawn;
        if (snowToSpawn > 0) snowAcc -= snowToSpawn;

        int total = rainToSpawn + snowToSpawn;
        if (total <= 0) return;

        // 硬限：避免强度过高时瞬间爆量
        if (total > HARD_CAP_PER_TICK) {
            float scale = (float) HARD_CAP_PER_TICK / (float) total;
            rainToSpawn = Math.max(0, (int) (rainToSpawn * scale));
            snowToSpawn = Math.max(0, (int) (snowToSpawn * scale));
        }

        if (rainToSpawn > 0) spawnRain(world, camPos, rainToSpawn, currentSample);
        if (snowToSpawn > 0) spawnSnow(world, camPos, snowToSpawn, currentSample);
    }

    // ---------------- Spawn ----------------

    private void spawnRain(ClientWorld world, Vec3d camPos, int count, WeatherSample sample) {
        if (count <= 0) return;

        // 风方向：用时间做一个缓慢旋转 + 小噪声（你后续可以替换为 keyframe windDir）
        float wind = clamp01(sample.windStrength);
        Vec3d windVec = computeWindVector(world.getTime(), wind);

        int radius = BASE_RADIUS_RAIN;
        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double yFromSurface = topY + ABOVE_SURFACE_PADDING + random.nextDouble() * 18.0;
            double yFromCamera = camPos.y + MIN_ABOVE_CAMERA_RAIN + random.nextDouble() * 10.0;
            double y = Math.max(yFromSurface, yFromCamera);

            // 垂直速度：下落更快更像雨
            double vy = -(RAIN_FALL_MIN + random.nextDouble() * (RAIN_FALL_MAX - RAIN_FALL_MIN));

            // 水平速度：风主导 + 少量抖动
            double vx = windVec.x + (random.nextDouble() - 0.5) * 0.08;
            double vz = windVec.z + (random.nextDouble() - 0.5) * 0.08;

            world.addParticleClient(ParticleTypes.RAIN, x, y, z, vx, vy, vz);
        }
    }

    private void spawnSnow(ClientWorld world, Vec3d camPos, int count, WeatherSample sample) {
        if (count <= 0) return;

        float wind = clamp01(sample.windStrength);
        Vec3d windVec = computeWindVector(world.getTime() + 200, wind); // 稍微错相位

        int radius = BASE_RADIUS_SNOW;
        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double yFromSurface = topY + (ABOVE_SURFACE_PADDING + 6) + random.nextDouble() * 14.0;
            double yFromCamera = camPos.y + MIN_ABOVE_CAMERA_SNOW + random.nextDouble() * 10.0;
            computeWindVector(world.getTime() + 200, wind); // 稍微错相位
            double y = Math.max(yFromSurface, yFromCamera);

            // 雪更慢、更飘
            double vy = -(SNOW_FALL_MIN + random.nextDouble() * (SNOW_FALL_MAX - SNOW_FALL_MIN));

            // 风更明显
            double vx = windVec.x + (random.nextDouble() - 0.5) * 0.04;
            double vz = windVec.z + (random.nextDouble() - 0.5) * 0.04;

            world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, vx, vy, vz);
        }
    }

    // ---------------- Wind model ----------------

    private Vec3d computeWindVector(long worldTime, float windStrength01) {
        if (windStrength01 <= 0.001f) return Vec3d.ZERO;

        // 方向随时间缓慢变化（你后续可以用 keyframe 固定 direction）
        double t = worldTime * 0.015;
        double angle = t * 0.35 + Math.sin(t * 0.17) * 0.6;

        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);

        // 速度大小（关键：让肉眼看得见）
        double speedRain = windStrength01 * WIND_HORIZONTAL_SCALE_RAIN;
        return new Vec3d(dirX * speedRain, 0.0, dirZ * speedRain);
    }

    // ---------------- Utils ----------------

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
