package com.chronoblocks.core.container;

/**
 * 容器变换数据结构
 * 
 * 存储容器在某个时刻的完整变换状态：
 * - 位置（Position）
 * - 旋转（Rotation）
 * - 缩放（Scale）
 * - 不透明度（Opacity）
 * 
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class ContainerTransform {
    
    // ========== 变换属性 ==========
    private final float[] position = new float[]{0f, 0f, 0f};  // x, y, z (方块单位)
    private final float[] rotation = new float[]{0f, 0f, 0f};  // pitch, yaw, roll (度数)
    private final float[] scale = new float[]{1f, 1f, 1f};     // x, y, z (倍数)
    private float opacity = 1.0f;                               // 0.0 ~ 1.0
    
    // ========== 构造函数 ==========
    
    public ContainerTransform() {
        // 默认值：位置原点，无旋转，正常缩放，完全不透明
    }
    
    public ContainerTransform(float[] position, float[] rotation, float[] scale, float opacity) {
        if (position != null && position.length >= 3) {
            System.arraycopy(position, 0, this.position, 0, 3);
        }
        if (rotation != null && rotation.length >= 3) {
            System.arraycopy(rotation, 0, this.rotation, 0, 3);
        }
        if (scale != null && scale.length >= 3) {
            System.arraycopy(scale, 0, this.scale, 0, 3);
        }
        this.opacity = clamp(opacity, 0f, 1f);
    }
    
    public ContainerTransform(ContainerTransform other) {
        if (other != null) {
            System.arraycopy(other.position, 0, this.position, 0, 3);
            System.arraycopy(other.rotation, 0, this.rotation, 0, 3);
            System.arraycopy(other.scale, 0, this.scale, 0, 3);
            this.opacity = other.opacity;
        }
    }
    
    // ========== 位置 ==========
    
    public float[] getPosition() {
        return position;
    }
    
    public void setPosition(float x, float y, float z) {
        position[0] = x;
        position[1] = y;
        position[2] = z;
    }
    
    public float getX() { return position[0]; }
    public float getY() { return position[1]; }
    public float getZ() { return position[2]; }
    
    // ========== 旋转 ==========
    
    public float[] getRotation() {
        return rotation;
    }
    
    public void setRotation(float pitch, float yaw, float roll) {
        rotation[0] = pitch;
        rotation[1] = yaw;
        rotation[2] = roll;
    }
    
    public float getPitch() { return rotation[0]; }
    public float getYaw() { return rotation[1]; }
    public float getRoll() { return rotation[2]; }
    
    // ========== 缩放 ==========
    
    public float[] getScale() {
        return scale;
    }
    
    public void setScale(float x, float y, float z) {
        scale[0] = Math.max(0.01f, x);  // 防止负数或零
        scale[1] = Math.max(0.01f, y);
        scale[2] = Math.max(0.01f, z);
    }
    
    public float getScaleX() { return scale[0]; }
    public float getScaleY() { return scale[1]; }
    public float getScaleZ() { return scale[2]; }
    
    // ========== 不透明度 ==========
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        this.opacity = clamp(opacity, 0f, 1f);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 重置到默认状态
     */
    public void reset() {
        setPosition(0f, 0f, 0f);
        setRotation(0f, 0f, 0f);
        setScale(1f, 1f, 1f);
        setOpacity(1f);
    }
    
    /**
     * 从另一个变换复制值
     */
    public void copyFrom(ContainerTransform other) {
        if (other != null) {
            System.arraycopy(other.position, 0, this.position, 0, 3);
            System.arraycopy(other.rotation, 0, this.rotation, 0, 3);
            System.arraycopy(other.scale, 0, this.scale, 0, 3);
            this.opacity = other.opacity;
        }
    }
    
    /**
     * 检查是否是默认状态（无变换）
     */
    public boolean isIdentity() {
        return position[0] == 0f && position[1] == 0f && position[2] == 0f &&
               rotation[0] == 0f && rotation[1] == 0f && rotation[2] == 0f &&
               scale[0] == 1f && scale[1] == 1f && scale[2] == 1f &&
               opacity == 1f;
    }
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    @Override
    public String toString() {
        return String.format("ContainerTransform{pos=(%.2f,%.2f,%.2f), rot=(%.1f,%.1f,%.1f), scale=(%.2f,%.2f,%.2f), opacity=%.2f}",
            position[0], position[1], position[2],
            rotation[0], rotation[1], rotation[2],
            scale[0], scale[1], scale[2],
            opacity);
    }
}

