package com.chronoblocks.ui.timeline.playback.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * WeatherParticleController v2.0
 * ------------------------------------------------------------
 * 职责：
 *   - 雨：只负责地面飞溅粒子（RAIN / SPLASH）。
 *         雨的视觉主体由原版 WorldRenderer.renderWeather() 渲染
 *         （通过 WorldMixin 覆盖 getRainGradient/isRaining 驱动）。
 *   - 雪：负责全部雪花粒子（SNOWFLAKE），因为需要自定义风向和强度。
 *
 * 设计：
 *   1) 发射率以"每秒粒子数 (PPS)"为单位，乘以真实 deltaSeconds
 *   2) 通过累加器实现帧率无关的稳定生成
 *   3) 硬限制防止爆量
 */
public class WeatherParticleController {

    private static final WeatherParticleController INSTANCE = new WeatherParticleController();

    public static WeatherParticleController getInstance() {
        return INSTANCE;
    }

    private WeatherParticleController() {
    }

    // ---------------- Config ----------------

    /** 地面雨滴飞溅最大发射率（强度=1），每秒粒子数 */
    private static final float MAX_RAIN_SPLASH_PPS = 6000f;

    /** 最大雪花发射率（强度=1），每秒粒子数 */
    private static final float MAX_SNOW_PPS = 200000f;

    /** 粒子生成半径（格） */
    private static final int BASE_RADIUS_RAIN = 48;
    private static final int BASE_RADIUS_SNOW = 120;
    private static final int MAX_RADIUS_RAIN = 128;
    private static final int MAX_RADIUS_SNOW = 200;

    /** 每 tick 允许生成的最大粒子数（硬限，防止卡顿） */
    private static final int HARD_CAP_PER_TICK = 12000;

    /** 雪下落速度范围 */
    private static final float SNOW_FALL_MIN = 0.45f;
    private static final float SNOW_FALL_MAX = 0.90f;

    /** 风对水平速度的放大 */
    private static final float WIND_HORIZONTAL_SCALE_SNOW = 1.05f;

    // ---------------- State ----------------

    private final Random random = new Random();

    private boolean enabled = true;
    private WeatherSample currentSample;

    /** 发射率累加器（以粒子数为单位） */
    private float rainSplashAcc = 0f;
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
     * 每 tick 调用一次。
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

        // 强度
        float rainI = clamp01(currentSample.rainIntensity);
        float snowI = clamp01(currentSample.snowIntensity);

        // 雷暴增强雨（让暴风雨时地面飞溅更强）
        float thunderBoost = clamp01(currentSample.thunderIntensity) * 0.5f;
        float finalRainI = clamp01(rainI + thunderBoost);

        // ========== 雨：只做地面飞溅 ==========
        // 雨的视觉主体由 Minecraft 原版 WorldRenderer.renderWeather() 渲染
        // （通过 WorldMixin 覆盖 getRainGradient / isRaining 驱动）
        int rainSplashRadius = resolveRadius(viewDistance, BASE_RADIUS_RAIN, MAX_RADIUS_RAIN);
        float splashPps = MAX_RAIN_SPLASH_PPS * finalRainI;
        rainSplashAcc += splashPps * deltaSeconds;
        int splashToSpawn = (int) rainSplashAcc;
        if (splashToSpawn > 0) rainSplashAcc -= splashToSpawn;

        // ========== 雪 ==========
        int snowRadius = resolveRadius(viewDistance, BASE_RADIUS_SNOW, MAX_RADIUS_SNOW);
        snowRadius = MathHelper.clamp(Math.round(snowRadius * (1.0f + snowI * 0.8f)), BASE_RADIUS_SNOW, MAX_RADIUS_SNOW);
        float snowPps = MAX_SNOW_PPS * snowI;
        snowAcc += snowPps * deltaSeconds;
        int snowToSpawn = (int) snowAcc;
        if (snowToSpawn > 0) snowAcc -= snowToSpawn;

        // 硬限
        int total = splashToSpawn + snowToSpawn;
        if (total <= 0) return;

        int hardCap = computeHardCap(deltaSeconds);
        if (total > hardCap) {
            float scale = (float) hardCap / (float) total;
            splashToSpawn = Math.max(0, (int) (splashToSpawn * scale));
            snowToSpawn = Math.max(0, (int) (snowToSpawn * scale));
        }

        if (splashToSpawn > 0)
            spawnRainSplash(world, camPos, rainSplashRadius, splashToSpawn, finalRainI);
        if (snowToSpawn > 0)
            spawnSnow(world, camPos, snowRadius, snowToSpawn, currentSample);
    }

    // ---------------- Spawn ----------------

    /**
     * 雨地面飞溅粒子。
     * <p>
     * 只在地面高度生成 RAIN / SPLASH 粒子，模拟雨滴落地效果。
     * 雨的主视觉（空中雨丝）由原版 WorldRenderer.renderWeather() 负责。
     */
    private void spawnRainSplash(ClientWorld world, Vec3d camPos, int radius, int count, float intensity) {
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double splashY = topY + 0.05;

            // 基础类型：RAIN（原版雨滴飞溅）
            world.addParticleClient(ParticleTypes.RAIN, x, splashY, z, 0, 0, 0);

            // 高强度时追加 SPLASH 粒子增强水花感
            if (intensity > 0.5f && random.nextFloat() < (intensity - 0.5f) * 0.6f) {
                double sx = x + (random.nextDouble() - 0.5) * 0.4;
                double sz = z + (random.nextDouble() - 0.5) * 0.4;
                world.addParticleClient(ParticleTypes.SPLASH, sx, splashY, sz, 0, 0.02, 0);
            }
        }
    }

    /**
     * 雪花粒子。
     * <p>
     * 在摄像机周围的体积内生成 SNOWFLAKE 粒子，并施加导演控制的风向和风速。
     */
    private void spawnSnow(ClientWorld world, Vec3d camPos, int radius, int count, WeatherSample sample) {
        if (count <= 0)
            return;

        float wind = clamp01(sample.windStrength);
        Vec3d windVec = computeWindVectorSnow(sample, wind);

        for (int i = 0; i < count; i++) {
            double x = camPos.x + spread(radius);
            double z = camPos.z + spread(radius);

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));

            // 体积化降雪：在摄像机周围 [-10, +40] 的垂直范围内生成
            double y = camPos.y + (random.nextDouble() * 50.0 - 10.0);
            y = Math.max(y, topY + 0.1); // 确保不穿入地下

            // 雪更慢、更飘
            double vy = -(SNOW_FALL_MIN + random.nextDouble() * (SNOW_FALL_MAX - SNOW_FALL_MIN));

            // 风
            double vx = windVec.x + (random.nextDouble() - 0.5) * 0.04;
            double vz = windVec.z + (random.nextDouble() - 0.5) * 0.04;

            world.addImportantParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, vx, vy, vz);
        }
    }

    // ---------------- Wind model ----------------

    /**
     * 使用 WeatherSample 中的风方向计算雪的风向量。
     */
    private Vec3d computeWindVectorSnow(WeatherSample sample, float windStrength01) {
        if (windStrength01 <= 0.001f)
            return Vec3d.ZERO;

        double angleRad = Math.toRadians(sample.windDirectionDegrees);
        double dirX = Math.cos(angleRad);
        double dirZ = Math.sin(angleRad);

        double speed = windStrength01 * WIND_HORIZONTAL_SCALE_SNOW;
        return new Vec3d(dirX * speed, 0.0, dirZ * speed);
    }

    // ---------------- Utils ----------------

    /**
     * 计算实际时间差，使用真实帧间隔驱动粒子发射率。
     */
    private float computeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastUpdateNanos <= 0L) {
            lastUpdateNanos = now;
            return 1f / 20f;
        }
        float delta = (now - lastUpdateNanos) / 1_000_000_000f;
        lastUpdateNanos = now;

        if (delta <= 0f) return 0f;
        return Math.min(delta, 0.2f);
    }

    private int resolveRadius(int viewDistanceChunks, int baseRadius, int maxRadius) {
        int view = Math.max(2, viewDistanceChunks);
        int scaled = view * 8;
        return MathHelper.clamp(scaled, baseRadius, maxRadius);
    }

    private int computeHardCap(float deltaSeconds) {
        float scaled = HARD_CAP_PER_TICK * deltaSeconds * 20f;
        return Math.max(HARD_CAP_PER_TICK, Math.round(scaled));
    }

    private double spread(int radius) {
        double u = random.nextDouble();
        double r = (u < 0.75) ? (radius * 0.55) : radius;
        return (random.nextDouble() - 0.5) * 2.0 * r;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
