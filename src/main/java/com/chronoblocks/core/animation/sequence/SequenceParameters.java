package com.chronoblocks.core.animation.sequence;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 序列参数配置
 * 
 * <p>封装序列策略所需的各种参数。
 * 不同的策略使用不同的参数，通过 Map 存储以保持灵活性。
 * 
 * <p>常用参数：
 * <ul>
 *   <li>batchSize：批量模式下的每批方块数</li>
 *   <li>randomSeed：随机模式的随机种子</li>
 *   <li>waveDirection：波浪模式的方向（"X", "Y", "Z"）</li>
 *   <li>waveInterval：波浪模式的波幅间隔</li>
 *   <li>spiralStart：螺旋模式的起始方向</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SequenceParameters implements Serializable, Cloneable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    // ========== 通用参数 ==========
    
    /** 批量模式：每批方块数（默认1，即一个接一个） */
    private int batchSize = 1;
    
    /** 随机模式：随机种子（null表示使用系统时间） */
    private Long randomSeed = null;
    
    // ========== 波浪模式参数 ==========
    
    /** 波浪方向：X, Y, Z 或组合（如 "XY"） */
    private String waveDirection = "Y";
    
    /** 波浪间隔：波幅间隔（单位：方块） */
    private float waveInterval = 1.0f;
    
    /** 波浪起始点：起始位置（"min", "max", "center"） */
    private String waveStart = "min";
    
    // ========== 螺旋模式参数 ==========
    
    /** 螺旋起始方向：X, Y, Z */
    private String spiralStart = "Y";
    
    /** 螺旋方向：顺时针（"clockwise"）或逆时针（"counterclockwise"） */
    private String spiralDirection = "clockwise";
    
    // ========== 扩展参数（用于未来功能）==========
    
    private final Map<String, Object> extraParams = new HashMap<>();
    
    // ========== 构造函数 ==========
    
    public SequenceParameters() {
    }
    
    public SequenceParameters(int batchSize) {
        this.batchSize = batchSize;
    }
    
    // ========== Getters and Setters ==========
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }
    
    public Long getRandomSeed() {
        return randomSeed;
    }
    
    public void setRandomSeed(Long randomSeed) {
        this.randomSeed = randomSeed;
    }
    
    public String getWaveDirection() {
        return waveDirection;
    }
    
    public void setWaveDirection(String waveDirection) {
        this.waveDirection = waveDirection != null ? waveDirection : "Y";
    }
    
    public float getWaveInterval() {
        return waveInterval;
    }
    
    public void setWaveInterval(float waveInterval) {
        this.waveInterval = Math.max(0.1f, waveInterval);
    }
    
    public String getWaveStart() {
        return waveStart;
    }
    
    public void setWaveStart(String waveStart) {
        this.waveStart = waveStart != null ? waveStart : "min";
    }
    
    public String getSpiralStart() {
        return spiralStart;
    }
    
    public void setSpiralStart(String spiralStart) {
        this.spiralStart = spiralStart != null ? spiralStart : "Y";
    }
    
    public String getSpiralDirection() {
        return spiralDirection;
    }
    
    public void setSpiralDirection(String spiralDirection) {
        this.spiralDirection = spiralDirection != null ? spiralDirection : "clockwise";
    }
    
    // ========== 扩展参数 ==========
    
    public Map<String, Object> getExtraParams() {
        return extraParams;
    }
    
    public void setExtraParam(String key, Object value) {
        if (key == null || key.isEmpty()) return;
        if (value == null) {
            extraParams.remove(key);
        } else {
            extraParams.put(key, value);
        }
    }
    
    public Object getExtraParam(String key) {
        return extraParams.get(key);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 复制参数
     */
    public SequenceParameters copy() {
        SequenceParameters clone = new SequenceParameters();
        clone.batchSize = this.batchSize;
        clone.randomSeed = this.randomSeed;
        clone.waveDirection = this.waveDirection;
        clone.waveInterval = this.waveInterval;
        clone.waveStart = this.waveStart;
        clone.spiralStart = this.spiralStart;
        clone.spiralDirection = this.spiralDirection;
        clone.extraParams.putAll(this.extraParams);
        return clone;
    }
    
    @Override
    public SequenceParameters clone() {
        return copy();
    }
}

