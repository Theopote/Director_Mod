package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * 独立于 Minecraft 默认 Camera 的「动画摄像机」实体。
 * 
 * 渲染阶段通过 CameraRuntime 提供的数据覆盖 MC 的 Camera。
 */
public class CinematicCamera {
    private double x;
    private double y;
    private double z;

    // yaw: 偏航，pitch: 俯仰，roll: 翻滚
    private float yaw;
    private float pitch;
    private float roll;

    // 视野
    private float fov = 70.0f;

    // 裁剪面，可选用于后处理或自定义投影
    private float nearPlane = 0.05f;
    private float farPlane = 512.0f;

    // 是否有效采样数据
    private boolean hasSample = false;

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vec3d getPosition() {
        return new Vec3d(x, y, z);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getRoll() {
        return roll;
    }

    public float getFov() {
        return fov;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    public boolean hasSample() {
        return hasSample;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasSample = true;
    }

    public void setPosition(Vec3d pos) {
        if (pos != null) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.hasSample = true;
        }
    }

    public void setPosition(Vector3f pos) {
        if (pos != null) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.hasSample = true;
        }
    }

    public void setRotation(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    public void setRotation(Vector3f rot) {
        // 约定 rot: (yaw, pitch, roll)
        if (rot != null) {
            this.yaw = rot.x;
            this.pitch = rot.y;
            this.roll = rot.z;
        }
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public void setNearPlane(float nearPlane) {
        this.nearPlane = nearPlane;
    }

    public void setFarPlane(float farPlane) {
        this.farPlane = farPlane;
    }

    /**
     * 从一个「采样结果」复制数据。
     * 适配现有的 CameraSample record。
     */
    public void applySample(CameraSample sample) {
        if (sample == null) {
            clear();
            return;
        }
        Vec3d pos = sample.position();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yaw = sample.yaw();
        this.pitch = sample.pitch();
        this.roll = sample.roll();
        this.fov = sample.fov();
        this.nearPlane = sample.nearPlane();
        this.farPlane = sample.farPlane();
        this.hasSample = true;
    }

    public void copyFrom(CinematicCamera other) {
        if (other == null) {
            return;
        }
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.yaw = other.yaw;
        this.pitch = other.pitch;
        this.roll = other.roll;
        this.fov = other.fov;
        this.nearPlane = other.nearPlane;
        this.farPlane = other.farPlane;
        this.hasSample = other.hasSample;
    }

    public void clear() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
        this.roll = 0.0f;
        this.fov = 70.0f;
        this.nearPlane = 0.05f;
        this.farPlane = 512.0f;
        this.hasSample = false;
    }
}
