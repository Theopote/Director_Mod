package com.director.timeline.playback.entity;

import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.entity.EntityKeyframe;
import org.joml.Vector3f;

/**
 * EntitySample v1.0
 *
 * <p>由 ActorTrackSampler 在某一时间采样得到的实体状态。
 * 用于驱动 Minecraft 世界中实体的位置、旋转、动作、状态等。
 *
 * <p>设计原则：
 * - 所有参数标准化为合理范围，方便插值
 * - 不负责逻辑，纯数据结构
 * - 用于实体控制系统的运行时采样
 *
 * <p>用于：
 * - TimelineActorController.apply(sample)
 * - 实体渲染系统：位置、旋转、动作、可见性等
 *
 * @author Director
 * @version 1.0
 */
public class EntitySample {

    // ========== 核心实体参数 ==========

    /** 实体位置（世界坐标） */
    public final Vector3f position = new Vector3f(0, 0, 0);

    /** 实体旋转（偏航、俯仰、翻滚，度） */
    public final Vector3f rotation = new Vector3f(0, 0, 0);

    /** 实体缩放（X, Y, Z） */
    public final Vector3f scale = new Vector3f(1, 1, 1);

    /** 当前动作类型 */
    public EntityKeyframe.ActionType actionType = EntityKeyframe.ActionType.IDLE;

    /** 动作速度 [0-2]：0 = 静止，1 = 正常，2 = 快速 */
    public float actionSpeed = 1.0f;

    /** 动作循环：true = 循环播放，false = 播放一次 */
    public boolean actionLoop = true;

    /** 实体可见性 [0-1]：0 = 完全隐藏，1 = 完全可见 */
    public float visibility = 1.0f;

    /** 实体是否激活（是否参与场景） */
    public boolean active = true;

    /** 实体名称标签（显示在实体上方） */
    public String displayName;

    /** 实体是否显示名称标签 */
    public boolean showNameTag = false;

    /** 实体类型预设 */
    public EntityKeyframe.EntityType entityType = EntityKeyframe.EntityType.CUSTOM;

    /** 是否启用实体覆盖（false 表示完全使用原版行为） */
    public boolean enabled = false;

    // ========== Clip 系统扩展字段 ==========

    /** 动画ID（来自 EntityAnimationClip） */
    public String animationId;

    /** 动画源类型（来自 EntityAnimationClip） */
    public EntityKeyframe.EntityType animationSource; // 临时复用，未来可扩展

    /** 动画循环（来自 EntityAnimationClip） */
    public boolean animationLoop = true;

    /** 动画速度（来自 EntityAnimationClip） */
    public float animationSpeed = 1.0f;

    /** LookAt 目标模式（来自 EntityLookAtClip） */
    public String lookAtMode; // "WORLD_POINT", "ENTITY_UUID", "CAMERA"

    /** LookAt 世界坐标点（来自 EntityLookAtClip） */
    public final Vector3f lookAtPoint = new Vector3f();

    /** LookAt 目标实体UUID（来自 EntityLookAtClip） */
    public java.util.UUID lookAtEntityUuid;

    /** LookAt 是否启用（来自 EntityLookAtClip） */
    public boolean lookAtEnabled = false;

    /** LookAt 强度（来自 EntityLookAtClip） */
    public float lookAtStrength = 0.0f;

    /** 状态：潜行（来自 EntityStateClip） */
    public boolean sneaking = false;

    /** 状态：冲刺（来自 EntityStateClip） */
    public boolean sprinting = false;

    /** 状态：发光（来自 EntityStateClip） */
    public boolean glowing = false;

    /** 状态：无敌（来自 EntityStateClip） */
    public boolean invulnerable = false;

    /** Action 事件列表（本帧触发的事件） */
    public final java.util.List<EntityActionEvent> actionEvents = new java.util.ArrayList<>();

    // ========== ActorActionClip 扩展字段 ==========

    /** 动作语义类型（来自 ActorActionClip） */
    public ActorActionType action = ActorActionType.IDLE;

    /** 动作强度（来自 ActorActionClip）[0.0-1.0] */
    public float actionIntensity = 1.0f;

    // ========== ActorPoseClip 扩展字段 ==========

    public boolean poseOverrideEnabled = false;
    public boolean poseOverrideBody = false;
    public boolean poseOverrideHead = false;
    public boolean poseOverrideArms = false;
    public boolean poseOverrideLegs = false;
    public float poseBodyPitch = 0f;
    public float poseBodyYaw = 0f;
    public float poseBodyRoll = 0f;
    public float poseHeadPitch = 0f;
    public float poseHeadYaw = 0f;
    public float poseLeftArmPitch = 0f;
    public float poseRightArmPitch = 0f;
    public float poseLeftLegPitch = 0f;
    public float poseRightLegPitch = 0f;

    // ========== 速度计算（用于自动动作推断）==========

    /** 移动速度（单位：blocks/second，只读给 Runtime） */
    public float speed = 0.0f;

    // ==========================================================
    // 工具方法
    // ==========================================================

    /**
     * 在两个采样之间插值。
     * 
     * @param a 起始采样
     * @param b 结束采样
     * @param t 插值系数 [0,1]
     * @param interpolation 插值类型
     */
    public void lerpFrom(EntitySample a, EntitySample b, float t, TimelineInterpolation interpolation) {
        if (a == null || !a.enabled) {
            if (b != null && b.enabled) {
                copyFrom(b);
            } else {
                copyFrom(disabled());
            }
            return;
        }
        if (b == null || !b.enabled) {
            copyFrom(a);
            return;
        }

        if (interpolation == TimelineInterpolation.STEP) {
            copyFrom(t < 0.5f ? a : b);
            return;
        }

        if (interpolation == TimelineInterpolation.SMOOTH) {
            // Smoothstep
            t = t * t * (3f - 2f * t);
        }

        // 位置插值（线性）
        position.x = lerp(a.position.x, b.position.x, t);
        position.y = lerp(a.position.y, b.position.y, t);
        position.z = lerp(a.position.z, b.position.z, t);

        // 旋转插值（角度插值，处理 360° 环绕）
        rotation.x = lerpAngle(a.rotation.x, b.rotation.x, t);
        rotation.y = lerpAngle(a.rotation.y, b.rotation.y, t);
        rotation.z = lerpAngle(a.rotation.z, b.rotation.z, t);

        // 缩放插值（线性）
        scale.x = lerp(a.scale.x, b.scale.x, t);
        scale.y = lerp(a.scale.y, b.scale.y, t);
        scale.z = lerp(a.scale.z, b.scale.z, t);

        // 动作速度插值
        actionSpeed = lerp(a.actionSpeed, b.actionSpeed, t);

        // 可见性插值
        visibility = lerp(a.visibility, b.visibility, t);

        // 枚举类型：根据 t 选择
        actionType = (t < 0.5f) ? a.actionType : b.actionType;
        entityType = (t < 0.5f) ? a.entityType : b.entityType;
        action = (t < 0.5f) ? a.action : b.action;

        // 动作强度插值
        actionIntensity = lerp(a.actionIntensity, b.actionIntensity, t);

        // 速度插值（线性）
        speed = lerp(a.speed, b.speed, t);

        // 布尔值：取最近关键帧的值（不插值）
        actionLoop = (t < 0.5f) ? a.actionLoop : b.actionLoop;
        active = (t < 0.5f) ? a.active : b.active;
        showNameTag = (t < 0.5f) ? a.showNameTag : b.showNameTag;

        // 字符串：取最近关键帧的值
        displayName = (t < 0.5f) ? a.displayName : b.displayName;

        enabled = true;
    }

    /** 复制所有字段 */
    public void copyFrom(EntitySample s) {
        if (s == null) {
            copyFrom(disabled());
            return;
        }

        enabled = s.enabled;
        position.set(s.position);
        rotation.set(s.rotation);
        scale.set(s.scale);
        actionType = s.actionType;
        actionSpeed = s.actionSpeed;
        actionLoop = s.actionLoop;
        visibility = s.visibility;
        active = s.active;
        displayName = s.displayName;
        showNameTag = s.showNameTag;
        entityType = s.entityType;

        // Clip 系统扩展字段
        animationId = s.animationId;
        animationSource = s.animationSource;
        animationLoop = s.animationLoop;
        animationSpeed = s.animationSpeed;
        lookAtMode = s.lookAtMode;
        lookAtPoint.set(s.lookAtPoint);
        lookAtEntityUuid = s.lookAtEntityUuid;
        lookAtEnabled = s.lookAtEnabled;
        lookAtStrength = s.lookAtStrength;
        sneaking = s.sneaking;
        sprinting = s.sprinting;
        glowing = s.glowing;
        invulnerable = s.invulnerable;
        actionEvents.clear();
        actionEvents.addAll(s.actionEvents);

        // ActorActionClip 扩展字段
        action = s.action;
        actionIntensity = s.actionIntensity;

        // ActorPoseClip 扩展字段
        poseOverrideEnabled = s.poseOverrideEnabled;
        poseOverrideBody = s.poseOverrideBody;
        poseOverrideHead = s.poseOverrideHead;
        poseOverrideArms = s.poseOverrideArms;
        poseOverrideLegs = s.poseOverrideLegs;
        poseBodyPitch = s.poseBodyPitch;
        poseBodyYaw = s.poseBodyYaw;
        poseBodyRoll = s.poseBodyRoll;
        poseHeadPitch = s.poseHeadPitch;
        poseHeadYaw = s.poseHeadYaw;
        poseLeftArmPitch = s.poseLeftArmPitch;
        poseRightArmPitch = s.poseRightArmPitch;
        poseLeftLegPitch = s.poseLeftLegPitch;
        poseRightLegPitch = s.poseRightLegPitch;

        // 速度
        speed = s.speed;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 角度插值（处理 360° 环绕，选择最短路径）。
     * 例如：350° 到 10° 会插值到 0° 而不是 180°
     */
    private float lerpAngle(float a, float b, float t) {
        // 归一化到 [0, 360)
        while (a < 0f) a += 360f;
        while (a >= 360f) a -= 360f;
        while (b < 0f) b += 360f;
        while (b >= 360f) b -= 360f;

        // 计算两个方向的距离
        float diff = b - a;
        if (diff > 180f) {
            diff -= 360f; // 走反向更短
        } else if (diff < -180f) {
            diff += 360f; // 走反向更短
        }

        float result = a + diff * t;
        // 归一化结果
        while (result < 0f) result += 360f;
        while (result >= 360f) result -= 360f;
        return result;
    }

    /**
     * 创建一个禁用状态的采样（不应用任何实体效果）。
     */
    public static EntitySample disabled() {
        EntitySample s = new EntitySample();
        s.enabled = false;
        return s;
    }

    /**
     * 深拷贝当前采样。
     */
    public EntitySample copy() {
        EntitySample s = new EntitySample();
        s.copyFrom(this);
        return s;
    }

    @Override
    public String toString() {
        return "EntitySample{" +
                "enabled=" + enabled +
                ", position=" + position +
                ", rotation=" + rotation +
                ", scale=" + scale +
                ", actionType=" + actionType +
                ", actionSpeed=" + actionSpeed +
                ", visibility=" + visibility +
                ", active=" + active +
                ", entityType=" + entityType +
                '}';
    }
}

