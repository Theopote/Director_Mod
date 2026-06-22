package com.director.ui.timeline.model.entity;

import com.director.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineSelectable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * EntityKeyframe v1.0
 *
 * <p>时间线上的「实体关键帧」模型，用于 Actor Track（演员轨道）。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>表示某一时刻的实体状态（位置、旋转、动作、行为等）</li>
 *   <li>用于驱动：NPC/玩家/动物/模型的位置、动作、状态变化</li>
 *   <li>支持实体类型预设和自定义配置</li>
 * </ul>
 *
 * <h3>核心定位</h3>
 * <p>演员轨道 = 在时间线上"导演"一个会动、会演戏的角色</p>
 * <p>实体不是"被生成一次"，而是被持续调度、被表演、被控制状态与动作</p>
 *
 * <p>实现 {@link TimelineSelectable}，可以作为自动化关键帧出现在时间线交互系统中。</p>
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityKeyframe implements TimelineSelectable, Comparable<EntityKeyframe> {

    // ========== 枚举类型 ==========

    /**
     * 实体类型预设。
     */
    public enum EntityType {
        NPC("NPC"),
        PLAYER("玩家"),
        ANIMAL("动物"),
        MODEL("模型"),
        CUSTOM("自定义");

        private final String displayName;

        EntityType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 动作类型。
     */
    public enum ActionType {
        IDLE("待机"),
        WALK("行走"),
        RUN("奔跑"),
        JUMP("跳跃"),
        ATTACK("攻击"),
        INTERACT("交互"),
        CUSTOM("自定义");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ========== 基础字段 ==========

    /** 唯一 ID，用于在轨道中标识和删除该关键帧 */
    private final UUID id;

    /** 关键帧显示名称（例如 "Entity @ 5.2s"） */
    private String name;

    /** 关键帧时间（秒）—— 对应时间线上的位置 */
    private float time;

    /** 实体类型预设 */
    private EntityType entityType;

    /** 插值类型，用于 ActorTrackSampler 在关键帧之间插值的方式 */
    private TimelineInterpolation interpolation;

    // ========== ① 位置和旋转 ==========

    /** 实体位置（世界坐标） */
    private final Vector3f position = new Vector3f(0, 0, 0);

    /** 实体旋转（偏航、俯仰、翻滚，度） */
    private final Vector3f rotation = new Vector3f(0, 0, 0);

    /** 实体缩放（X, Y, Z） */
    private final Vector3f scale = new Vector3f(1, 1, 1);

    // ========== ② 动作和状态 ==========

    /** 当前动作类型 */
    private ActionType actionType = ActionType.IDLE;

    /** 动作速度 [0-2]：0 = 静止，1 = 正常，2 = 快速 */
    private float actionSpeed = 1.0f;

    /** 动作循环：true = 循环播放，false = 播放一次 */
    private boolean actionLoop = true;

    /** 实体可见性 [0-1]：0 = 完全隐藏，1 = 完全可见 */
    private float visibility = 1.0f;

    // ========== ③ 行为和交互 ==========

    /** 实体是否激活（是否参与场景） */
    private boolean active = true;

    /** 实体名称标签（显示在实体上方） */
    private String displayName;

    /** 实体是否显示名称标签 */
    private boolean showNameTag = false;

    // ========== 构造 ==========

    /**
     * 创建一个默认实体关键帧。
     *
     * @param time 关键帧时间（秒）
     */
    public EntityKeyframe(float time) {
        this(UUID.randomUUID(), "Entity @" + String.format("%.2f", time), time,
                EntityType.CUSTOM, TimelineInterpolation.SMOOTH);
    }

    /**
     * 创建一个具有指定类型的实体关键帧。
     *
     * @param name         关键帧名称
     * @param time         时间（秒）
     * @param entityType   实体类型预设
     * @param interpolation 插值类型
     */
    public EntityKeyframe(String name, float time, EntityType entityType, TimelineInterpolation interpolation) {
        this(UUID.randomUUID(), name, time, entityType, interpolation);
    }

    /**
     * 兼容旧版本的构造函数。
     *
     * @param name 关键帧名称
     * @param time 时间（秒）
     */
    public EntityKeyframe(String name, float time) {
        this(UUID.randomUUID(), name, time, EntityType.CUSTOM, TimelineInterpolation.SMOOTH);
    }

    /**
     * 完整构造函数（内部/工具使用）。
     */
    public EntityKeyframe(UUID id, String name, float time,
                           EntityType entityType,
                           TimelineInterpolation interpolation) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.time = time;
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
    }

    // ========== TimelineSelectable 实现 ==========

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return "Entity @" + String.format("%.2fs", time);
    }

    /**
     * TimelineSelectable 中时间字段统一叫 startTime。
     */
    @Override
    public float getStartTime() {
        return time;
    }

    /**
     * 对于「点关键帧」，结束时间与开始时间相同。
     */
    @Override
    public float getEndTime() {
        return time;
    }

    @Override
    public String getType() {
        return "EntityKeyframe";
    }

    // ========== 比较与调试 ==========

    @Override
    public int compareTo(@NotNull EntityKeyframe other) {
        int timeCompare = Float.compare(this.time, other.time);
        if (timeCompare != 0) {
            return timeCompare;
        }
        // 时间相同时，用 ID 作为次排序键，保证 TreeSet 等结构中不重复
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return "EntityKeyframe{" +
                "id=" + id +
                ", time=" + String.format("%.3f", time) +
                ", type=" + entityType +
                ", position=" + position +
                ", rotation=" + rotation +
                ", action=" + actionType +
                ", visibility=" + visibility +
                '}';
    }

    // ========== 基础 getter / setter ==========

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * 获取关键帧时间（秒）
     */
    public float getTime() {
        return time;
    }

    /**
     * 设置关键帧时间（秒）
     */
    public void setTime(float time) {
        this.time = time;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    public TimelineInterpolation getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(TimelineInterpolation interpolation) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
    }

    // ========== 位置和旋转 getter/setter ==========

    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(Vector3f position) {
        if (position != null) {
            this.position.set(position);
        }
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(Vector3f rotation) {
        if (rotation != null) {
            this.rotation.set(rotation);
        }
    }

    public void setRotation(float yaw, float pitch, float roll) {
        this.rotation.set(yaw, pitch, roll);
    }

    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {
        if (scale != null) {
            this.scale.set(scale);
        }
    }

    public void setScale(float x, float y, float z) {
        this.scale.set(x, y, z);
    }

    // ========== 动作和状态 getter/setter ==========

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType != null ? actionType : ActionType.IDLE;
    }

    public float getActionSpeed() {
        return actionSpeed;
    }

    public void setActionSpeed(float actionSpeed) {
        this.actionSpeed = Math.max(0.0f, Math.min(2.0f, actionSpeed));
    }

    public boolean isActionLoop() {
        return actionLoop;
    }

    public void setActionLoop(boolean actionLoop) {
        this.actionLoop = actionLoop;
    }

    public float getVisibility() {
        return visibility;
    }

    public void setVisibility(float visibility) {
        this.visibility = clamp01(visibility);
    }

    // ========== 行为和交互 getter/setter ==========

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isShowNameTag() {
        return showNameTag;
    }

    public void setShowNameTag(boolean showNameTag) {
        this.showNameTag = showNameTag;
    }

    // ========== 工具方法 ==========

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        return Math.min(v, 1.0f);
    }
}

