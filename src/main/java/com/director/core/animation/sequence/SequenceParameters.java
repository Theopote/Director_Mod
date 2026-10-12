package com.director.core.animation.sequence;

import com.director.core.animation.sequence.evaluator.FillType;
import com.director.core.animation.sequence.evaluator.FlowType;
import com.director.core.animation.sequence.evaluator.FloodSeedMode;
import com.director.core.animation.sequence.evaluator.OriginType;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 新版 Sequence 行为配置：Flow + Origin + Fill + Weight。
 */
public class SequenceParameters implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 4L;

    // --- Flow & Fill 基础字段 ---
    private FlowType flowType = FlowType.PLANAR;
    private FillType fillType = FillType.LINEAR_SCAN;
    private OriginType originType = OriginType.BOTTOM_CENTER;

    private float planeYaw = 0.0f;
    private float planePitch = 90.0f;

    /**
     * Flow 层级量化步长（单位：方块）。
     *
     * <p>用于解决 PLANAR 自定义角度时 flowScore 过于“碎”的问题：
     * layerIndex = floor(flowScore / layerStep)。
     *
     * <p>默认 1.0 = 每 1 格一个层；设为 2.0 则每 2 格一个层，更粗、更有“片”感。
     */
    private float layerStep = 1.0f;
    
    // --- Flood Fill 参数 ---
    private FloodSeedMode floodSeedMode = FloodSeedMode.LAYER_CENTER;

    private int customX = 0;
    private int customY = 0;
    private int customZ = 0;

    private float priorityWeight = 20.0f;
    private boolean reverse = false;
    private boolean alternating = false;

    private final Map<String, Object> extraParams = new HashMap<>();

    // ===== Raster Fill (打印机扫描) 额外参数 key =====
    public static final String EXTRA_RASTER_SCAN_AXIS = "raster_scan_axis"; // "U" | "V"（行内扫描方向）
    public static final String EXTRA_RASTER_ROW_STEP = "raster_row_step";   // Float（换行步长，单位：方块）
    public static final String EXTRA_RASTER_ZIGZAG = "raster_zigzag";       // Boolean（行内蛇形往返）

    // --- 并发和时间映射 ---
    private ConcurrencyMode concurrencyMode = ConcurrencyMode.ONE_BY_ONE;
    private int batchSize = 10;
    private float batchInterval = 0.1f;
    private TimeMappingStrategy timeMappingStrategy = TimeMappingStrategy.NORMALIZED_LINEAR;
    private float perBlockDuration = 0.0f;
    private float globalDelay = 0.0f;

    public SequenceParameters() {
    }

    // --- Flow / Origin / Fill ---
    public FlowType getFlowType() {
        return flowType != null ? flowType : FlowType.PLANAR;
    }

    public void setFlowType(FlowType flowType) {
        this.flowType = flowType != null ? flowType : FlowType.PLANAR;
    }

    public FillType getFillType() {
        return fillType != null ? fillType : FillType.LINEAR_SCAN;
    }

    public void setFillType(FillType fillType) {
        this.fillType = fillType != null ? fillType : FillType.LINEAR_SCAN;
    }

    public OriginType getOriginType() {
        return originType != null ? originType : OriginType.BOTTOM_CENTER;
    }

    public void setOriginType(OriginType originType) {
        this.originType = originType != null ? originType : OriginType.BOTTOM_CENTER;
    }

    public float getPlaneYaw() {
        return planeYaw;
    }

    public void setPlaneYaw(float planeYaw) {
        this.planeYaw = planeYaw;
    }

    public float getPlanePitch() {
        return planePitch;
    }

    public void setPlanePitch(float planePitch) {
        this.planePitch = planePitch;
    }

    public float getLayerStep() {
        // 避免除零；允许很小但不为 0
        return Math.max(0.0001f, layerStep);
    }

    public void setLayerStep(float layerStep) {
        this.layerStep = Math.max(0.0001f, layerStep);
    }

    public int getCustomX() {
        return customX;
    }

    public int getCustomY() {
        return customY;
    }

    public int getCustomZ() {
        return customZ;
    }

    public void setCustomXYZ(int x, int y, int z) {
        this.customX = x;
        this.customY = y;
        this.customZ = z;
    }

    public float getPriorityWeight() {
        return priorityWeight;
    }

    public void setPriorityWeight(float priorityWeight) {
        this.priorityWeight = Math.max(0.0f, priorityWeight);
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public boolean isAlternating() {
        return alternating;
    }

    public void setAlternating(boolean alternating) {
        this.alternating = alternating;
    }

    // --- Flood Fill ---
    public FloodSeedMode getFloodSeedMode() {
        return floodSeedMode != null ? floodSeedMode : FloodSeedMode.LAYER_CENTER;
    }

    public void setFloodSeedMode(FloodSeedMode floodSeedMode) {
        this.floodSeedMode = floodSeedMode != null ? floodSeedMode : FloodSeedMode.LAYER_CENTER;
    }

    // --- Extra Params ---
    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParam(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            extraParams.remove(key);
        } else {
            extraParams.put(key, value);
        }
    }

    public Object getExtraParam(String key) {
        return extraParams.get(key);
    }

    // ===== Raster Fill helpers =====
    public String getRasterScanAxis() {
        Object v = extraParams.get(EXTRA_RASTER_SCAN_AXIS);
        if (v instanceof String s && (s.equalsIgnoreCase("U") || s.equalsIgnoreCase("V"))) {
            return s.equalsIgnoreCase("U") ? "U" : "V";
        }
        return "U";
    }

    public void setRasterScanAxis(String axis) {
        String a = (axis != null && axis.equalsIgnoreCase("V")) ? "V" : "U";
        extraParams.put(EXTRA_RASTER_SCAN_AXIS, a);
    }

    public float getRasterRowStep() {
        Object v = extraParams.get(EXTRA_RASTER_ROW_STEP);
        if (v instanceof Number n) {
            return Math.max(0.1f, n.floatValue());
        }
        return 1.0f;
    }

    public void setRasterRowStep(float step) {
        extraParams.put(EXTRA_RASTER_ROW_STEP, Math.max(0.1f, step));
    }

    public boolean isRasterZigZag() {
        Object v = extraParams.get(EXTRA_RASTER_ZIGZAG);
        if (v instanceof Boolean b) {
            return b;
        }
        return true;
    }

    public void setRasterZigZag(boolean zigzag) {
        extraParams.put(EXTRA_RASTER_ZIGZAG, zigzag);
    }

    // --- 并发 & 时间映射 ---
    public ConcurrencyMode getConcurrencyMode() {
        return concurrencyMode != null ? concurrencyMode : ConcurrencyMode.ONE_BY_ONE;
    }

    public void setConcurrencyMode(ConcurrencyMode concurrencyMode) {
        this.concurrencyMode = concurrencyMode != null ? concurrencyMode : ConcurrencyMode.ONE_BY_ONE;
    }

    public int getBatchSize() {
        return Math.max(1, batchSize);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public float getBatchInterval() {
        return Math.max(0.0f, batchInterval);
    }

    public void setBatchInterval(float batchInterval) {
        this.batchInterval = Math.max(0.0f, batchInterval);
    }

    public TimeMappingStrategy getTimeMappingStrategy() {
        return timeMappingStrategy != null ? timeMappingStrategy : TimeMappingStrategy.NORMALIZED_LINEAR;
    }

    public void setTimeMappingStrategy(TimeMappingStrategy timeMappingStrategy) {
        this.timeMappingStrategy = timeMappingStrategy != null ? timeMappingStrategy : TimeMappingStrategy.NORMALIZED_LINEAR;
    }

    public float getPerBlockDuration() {
        return Math.max(0.0f, perBlockDuration);
    }

    public void setPerBlockDuration(float perBlockDuration) {
        this.perBlockDuration = Math.max(0.0f, perBlockDuration);
    }

    public float getGlobalDelay() {
        return Math.max(0.0f, globalDelay);
    }

    public void setGlobalDelay(float globalDelay) {
        this.globalDelay = Math.max(0.0f, globalDelay);
    }

    /** Stable cache key for ordering and timing parameters. */
    public int fingerprint() {
        int h = 1;
        h = 31 * h + (this.getFlowType() != null ? this.getFlowType().name().hashCode() : 0);
        h = 31 * h + (this.getFillType() != null ? this.getFillType().name().hashCode() : 0);
        h = 31 * h + (this.getOriginType() != null ? this.getOriginType().name().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(this.getPlaneYaw());
        h = 31 * h + Float.floatToIntBits(this.getPlanePitch());
        h = 31 * h + Float.floatToIntBits(this.getLayerStep());
        h = 31 * h + this.getCustomX();
        h = 31 * h + this.getCustomY();
        h = 31 * h + this.getCustomZ();
        h = 31 * h + Float.floatToIntBits(this.getPriorityWeight());
        h = 31 * h + (this.isReverse() ? 1 : 0);
        h = 31 * h + (this.isAlternating() ? 1 : 0);
        h = 31 * h + (this.getFloodSeedMode() != null ? this.getFloodSeedMode().name().hashCode() : 0);
        h = 31 * h + (this.getConcurrencyMode() != null ? this.getConcurrencyMode().name().hashCode() : 0);
        h = 31 * h + this.getBatchSize();
        h = 31 * h + Float.floatToIntBits(this.getBatchInterval());
        h = 31 * h + (this.getTimeMappingStrategy() != null ? this.getTimeMappingStrategy().name().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(this.getPerBlockDuration());
        h = 31 * h + Float.floatToIntBits(this.getGlobalDelay());
        h = 31 * h + (this.getRasterScanAxis() != null ? this.getRasterScanAxis().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(this.getRasterRowStep());
        h = 31 * h + (this.isRasterZigZag() ? 1 : 0);
        return h;
    }

    // --- 复制 ---
    public SequenceParameters copy() {
        SequenceParameters clone = new SequenceParameters();
        clone.flowType = this.flowType;
        clone.fillType = this.fillType;
        clone.originType = this.originType;
        clone.planeYaw = this.planeYaw;
        clone.planePitch = this.planePitch;
        clone.layerStep = this.layerStep;
        clone.customX = this.customX;
        clone.customY = this.customY;
        clone.customZ = this.customZ;
        clone.priorityWeight = this.priorityWeight;
        clone.reverse = this.reverse;
        clone.alternating = this.alternating;
        clone.floodSeedMode = this.floodSeedMode;
        clone.extraParams.putAll(this.extraParams);
        clone.concurrencyMode = this.concurrencyMode;
        clone.batchSize = this.batchSize;
        clone.batchInterval = this.batchInterval;
        clone.timeMappingStrategy = this.timeMappingStrategy;
        clone.perBlockDuration = this.perBlockDuration;
        clone.globalDelay = this.globalDelay;
        return clone;
    }

    @Override
    public SequenceParameters clone() {
        return copy();
    }
}