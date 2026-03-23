package com.director.season;

import net.minecraft.util.math.BlockPos;

/**
 * 简单的噪声场生成器，用于生成连续的覆盖场。
 * 
 * <p>使用改进的 Perlin 噪声算法生成平滑的连续值，
 * 使得积雪和冰冻效果能够成片出现，而不是零零散散。
 * 
 * @author Director Team
 * @version 1.0
 */
public class NoiseField {
    
    /** 噪声的缩放因子（控制噪声的频率，值越大，噪声越密集） */
    private static final float NOISE_SCALE = 0.05f; // 每 20 方块一个周期
    
    /** 用于生成不同噪声模式的种子偏移 */
    private static final long SEED_OFFSET_SNOW = 12345L;
    private static final long SEED_OFFSET_ICE = 67890L;
    
    /**
     * 计算指定位置的噪声值（0.0 到 1.0）。
     * 
     * @param x X 坐标
     * @param z Z 坐标
     * @param seed 种子值（用于生成不同的噪声模式）
     * @return 噪声值 [0.0, 1.0]
     */
    public static float noise2D(float x, float z, long seed) {
        // 使用改进的 Perlin 噪声算法
        // 简化版本：使用分形布朗运动（fBm）生成平滑的连续值
        
        float value = 0.0f;
        float amplitude = 1.0f;
        float frequency = NOISE_SCALE;
        float maxValue = 0.0f;
        
        // 3 层八度（octaves）生成更自然的噪声
        for (int i = 0; i < 3; i++) {
            value += amplitude * perlinNoise2D(x * frequency, z * frequency, seed + i);
            maxValue += amplitude;
            amplitude *= 0.5f;  // 每层振幅减半
            frequency *= 2.0f;   // 每层频率翻倍
        }
        
        // 归一化到 [0, 1]
        return (value / maxValue + 1.0f) * 0.5f;
    }
    
    /**
     * 简化的 2D Perlin 噪声实现。
     */
    private static float perlinNoise2D(float x, float z, long seed) {
        // 网格坐标
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        
        // 小数部分（用于插值）
        float fx = x - x0;
        float fz = z - z0;
        
        // 平滑插值函数（smoothstep）
        float u = smoothstep(fx);
        float v = smoothstep(fz);
        
        // 四个网格点的梯度值
        float n00 = grad2D(x0, z0, seed);
        float n10 = grad2D(x1, z0, seed);
        float n01 = grad2D(x0, z1, seed);
        float n11 = grad2D(x1, z1, seed);
        
        // 双线性插值
        float nx0 = lerp(n00, n10, u);
        float nx1 = lerp(n01, n11, u);
        return lerp(nx0, nx1, v);
    }
    
    /**
     * 计算网格点的梯度值。
     */
    private static float grad2D(int x, int z, long seed) {
        // 使用哈希函数生成伪随机梯度
        long hash = hash(x, z, seed);
        // 将哈希值转换为 [-1, 1] 范围
        return ((hash % 20000) / 10000.0f) - 1.0f;
    }
    
    /**
     * 简单的哈希函数。
     */
    private static long hash(int x, int z, long seed) {
        long h = seed;
        h = h * 31 + x;
        h = h * 31 + z;
        h ^= (h << 13);
        h ^= (h >>> 17);
        h ^= (h << 5);
        return h;
    }
    
    /**
     * 平滑插值函数（smoothstep）。
     */
    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
    
    /**
     * 线性插值。
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * 计算积雪覆盖场的值。
     * 
     * @param pos 方块位置
     * @return 覆盖场值 [0.0, 1.0]，值越大，越应该被积雪覆盖
     */
    public static float snowCoverage(BlockPos pos) {
        return noise2D(pos.getX(), pos.getZ(), SEED_OFFSET_SNOW);
    }
    
    /**
     * 计算冰冻覆盖场的值。
     * 
     * @param pos 方块位置
     * @return 覆盖场值 [0.0, 1.0]，值越大，越应该被冰冻覆盖
     */
    public static float iceCoverage(BlockPos pos) {
        return noise2D(pos.getX(), pos.getZ(), SEED_OFFSET_ICE);
    }
    
    /**
     * 判断指定位置是否应该被覆盖（基于噪声场和覆盖概率）。
     * 
     * @param pos 方块位置
     * @param coverageProbability 覆盖概率 [0.0, 1.0]
     * @param noiseValue 噪声场值 [0.0, 1.0]
     * @return 是否应该覆盖
     */
    public static boolean shouldCover(BlockPos pos, float coverageProbability, float noiseValue) {
        // 使用噪声场值作为阈值
        // 当 coverageProbability = 1.0 时，所有噪声值 < 1.0 的位置都应该被覆盖
        // 当 coverageProbability = 0.0 时，所有位置都不应该被覆盖
        // 当 coverageProbability = 0.5 时，噪声值 < 0.5 的位置应该被覆盖
        
        // 将覆盖概率映射到阈值：coverageProbability = 1.0 -> threshold = 1.0
        // coverageProbability = 0.0 -> threshold = 0.0

        // 如果噪声值小于阈值，则应该被覆盖
        // 这样，噪声值小的区域（低地）更容易被覆盖，噪声值大的区域（高地）不容易被覆盖
        // 但由于噪声是连续的，相邻位置的值也接近，所以会形成成片的覆盖区域
        return noiseValue < coverageProbability;
    }
}

