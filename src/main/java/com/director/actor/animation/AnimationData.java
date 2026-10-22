package com.director.actor.animation;

import com.director.actor.rig.Pose;

import java.util.List;
import java.util.Set;
import com.director.actor.rig.skeleton.HumanoidRig;

/**
 * AnimationData v1.0
 *
 * <p>动画数据，包含动画的所有信息。
 *
 * <p>设计理念：
 * <ul>
 *   <li>动画数据是不可变的（关键帧只读）</li>
 *   <li>记录内建或程序化注册的自定义关键帧来源</li>
 *   <li>提供采样方法（sampleAt）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationData {

    /**
     * 动画来源类型
     */
    public enum AnimationSource {
        /** Director 随包提供的关键帧动画。 */
        BUILT_IN("内建动画"),

        /** 自定义关键帧动画 */
        CUSTOM("自定义");

        private final String displayName;

        AnimationSource(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Purpose {
        PERFORMANCE("表演",false), LOCOMOTION("循环步态",true), STATIC_POSE("静态姿态",true);
        private final String label; private final boolean loop;
        Purpose(String label,boolean loop) { this.label=label;this.loop=loop; }
        public String label() { return label; }
        public boolean defaultLoop() { return loop; }
    }
    private final Purpose purpose;
    public Purpose purpose() { return purpose; }
    private final String id;
    private final String name;
    private final float duration;
    private final List<AnimationKeyframe> keyframes;
    private final AnimationSource source;
    public static final Set<String> POSE_BONES = Set.of(HumanoidRig.BODY, HumanoidRig.HEAD,
            HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG);
    private final Set<String> controlledBones;
    public Set<String> controlledBones() { return controlledBones; }


    /**
     * 构造函数
     *
     * @param id 动画ID
     * @param name 动画名称
     * @param duration 时长（秒）
     * @param keyframes 关键帧列表
     * @param source 动画来源
     */
    public AnimationData(String id, String name, float duration,
                        List<AnimationKeyframe> keyframes, AnimationSource source) {
        this(id, name, duration, keyframes, source, POSE_BONES);
    }

    /** Ownership is explicit even on neutral keyframes; zero rotation is not an absent channel. */
    public AnimationData(String id, String name, float duration, List<AnimationKeyframe> keyframes,
                         AnimationSource source, Set<String> controlledBones) {
        this(id,name,duration,keyframes,source,controlledBones,Purpose.PERFORMANCE);
    }
    public AnimationData(String id,String name,float duration,List<AnimationKeyframe> keyframes,
                         AnimationSource source,Set<String> controlledBones,Purpose purpose) {
        this.purpose=java.util.Objects.requireNonNull(purpose);
        if (controlledBones == null || !POSE_BONES.containsAll(controlledBones))
            throw new IllegalArgumentException("Unsupported fixed-pose bone selection");
        this.controlledBones = Set.copyOf(controlledBones);
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.keyframes = List.copyOf(keyframes);
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float getDuration() {
        return duration;
    }

    public List<AnimationKeyframe> getKeyframes() {
        return keyframes;
    }

    public AnimationSource getSource() {
        return source;
    }

    /**
     * 在指定时间采样姿态
     *
     * @param time 时间（秒）
     * @return 采样的姿态
     */
    public Pose sampleAt(float time) {
        if (keyframes.isEmpty()) {
            return new Pose();
        }

        // 单个关键帧
        if (keyframes.size() == 1) {
            return keyframes.get(0).getPose();
        }

        // 找到前后两个关键帧
        AnimationKeyframe prev = null;
        AnimationKeyframe next = null;

        for (AnimationKeyframe kf : keyframes) {
            if (kf.getTime() <= time) {
                prev = kf;
            }
            if (kf.getTime() >= time && next == null) {
                next = kf;
                break;
            }
        }

        // 边界情况
        if (prev == null) {
            return keyframes.get(0).getPose();
        }
        if (next == null) {
            return keyframes.get(keyframes.size() - 1).getPose();
        }
        if (prev == next) {
            return prev.getPose();
        }

        // 线性插值
        float t = (time - prev.getTime()) / (next.getTime() - prev.getTime());
        return Pose.lerp(prev.getPose(), next.getPose(), t);
    }

    @Override
    public String toString() {
        return String.format("AnimationData{id='%s', name='%s', duration=%.2f, keyframes=%d, source=%s}",
                id, name, duration, keyframes.size(), source);
    }
}
