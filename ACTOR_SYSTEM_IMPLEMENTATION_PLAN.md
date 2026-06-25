# Director 演员系统补充实现计划

## 📅 生成日期
2026-06-25

---

## 🎯 实施目标

经过详细代码检查，发现 **Director 演员系统的基础架构已经完成约 85%**。

**已完成的模块**：
- ✅ Actor 数据模型（完整）
- ✅ ActorRegistry 管理器（完整）
- ✅ Timeline 集成（完整）
- ✅ 所有核心 Clip 类型（EntitySpawnClip, EntityTransformClip, EntityActionClip, EntityLookAtClip, ActorActionClip, ActorPoseClip, ActorRouteClip）
- ✅ EntityClipSampler（完整采样逻辑）
- ✅ ActorRuntimeController（完整运行时控制）
- ✅ Rig 系统（HumanoidRigProfile, QuadrupedRigProfile）
- ✅ 道具系统（ActorPropRuntime, 投掷物系统）
- ✅ UI 工具链（完整）

**需要补充的内容**：
1. 动画播放器系统（AnimationPlayer）
2. 多演员交互系统（ActorRelationClip）
3. 物理模拟系统（可选）
4. 表情/情绪系统（可选）
5. 一些工具类和辅助功能

---

## 📋 第一阶段：动画播放器系统（高优先级）

### 1.1 AnimationPlayer 接口

**目的**：统一的动画播放器接口，支持多种动画源

**文件**：`src/main/java/com/director/actor/animation/AnimationPlayer.java`

```java
package com.director.actor.animation;

import com.director.actor.rig.Pose;
import com.director.actor.rig.AnimationPoseResult;

/**
 * AnimationPlayer v1.0
 *
 * <p>统一的动画播放器接口，支持多种动画源：
 * <ul>
 *   <li>Vanilla Minecraft 原版动画</li>
 *   <li>GeckoLib 动画</li>
 *   <li>自定义关键帧动画</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public interface AnimationPlayer {
    
    /**
     * 播放动画
     *
     * @param animationId 动画ID
     * @param loop 是否循环
     * @param speed 播放速度倍率
     */
    void play(String animationId, boolean loop, float speed);
    
    /**
     * 停止当前动画
     */
    void stop();
    
    /**
     * 暂停动画
     */
    void pause();
    
    /**
     * 恢复播放
     */
    void resume();
    
    /**
     * 更新动画状态（每帧调用）
     *
     * @param deltaTime 时间增量（秒）
     */
    void update(float deltaTime);
    
    /**
     * 获取当前动画的姿态
     *
     * @return 当前姿态，如果没有动画则返回 null
     */
    AnimationPoseResult getCurrentPose();
    
    /**
     * 获取当前播放进度
     *
     * @return 进度 [0.0-1.0]
     */
    float getProgress();
    
    /**
     * 获取当前动画时长
     *
     * @return 时长（秒），如果没有动画则返回 0
     */
    float getDuration();
    
    /**
     * 检查是否正在播放
     *
     * @return 如果正在播放则返回 true
     */
    boolean isPlaying();
    
    /**
     * 获取当前动画ID
     *
     * @return 动画ID，如果没有动画则返回 null
     */
    String getCurrentAnimationId();
}
```

---

### 1.2 AnimationLibrary 动画资源库

**文件**：`src/main/java/com/director/actor/animation/AnimationLibrary.java`

```java
package com.director.actor.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AnimationLibrary v1.0
 *
 * <p>动画资源库，管理项目中所有可用的动画。
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationLibrary {
    
    private static final AnimationLibrary INSTANCE = new AnimationLibrary();
    
    private final Map<String, AnimationData> animations = new HashMap<>();
    
    private AnimationLibrary() {}
    
    public static AnimationLibrary getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册动画
     *
     * @param animationData 动画数据
     */
    public void register(AnimationData animationData) {
        if (animationData != null && animationData.getId() != null) {
            animations.put(animationData.getId(), animationData);
        }
    }
    
    /**
     * 获取动画
     *
     * @param animationId 动画ID
     * @return 动画数据，如果不存在则返回 empty
     */
    public Optional<AnimationData> get(String animationId) {
        return Optional.ofNullable(animations.get(animationId));
    }
    
    /**
     * 移除动画
     *
     * @param animationId 动画ID
     * @return 如果成功移除则返回 true
     */
    public boolean remove(String animationId) {
        return animations.remove(animationId) != null;
    }
    
    /**
     * 清空所有动画
     */
    public void clear() {
        animations.clear();
    }
    
    /**
     * 获取所有动画ID
     *
     * @return 动画ID列表
     */
    public java.util.List<String> getAllAnimationIds() {
        return new java.util.ArrayList<>(animations.keySet());
    }
}
```

---

### 1.3 AnimationData 动画数据

**文件**：`src/main/java/com/director/actor/animation/AnimationData.java`

```java
package com.director.actor.animation;

import com.director.actor.rig.Pose;
import java.util.List;

/**
 * AnimationData v1.0
 *
 * <p>动画数据，包含动画的所有信息。
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationData {
    
    private final String id;
    private final String name;
    private final float duration;
    private final List<AnimationKeyframe> keyframes;
    private final AnimationSource source;
    
    /**
     * 动画来源类型
     */
    public enum AnimationSource {
        /** Minecraft 原版动画 */
        VANILLA,
        
        /** GeckoLib 动画 */
        GECKOLIB,
        
        /** 自定义关键帧动画 */
        CUSTOM,
        
        /** 外部文件（.anim）*/
        FILE
    }
    
    public AnimationData(String id, String name, float duration, 
                        List<AnimationKeyframe> keyframes, AnimationSource source) {
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.keyframes = keyframes;
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
        
        // 插值
        float t = (time - prev.getTime()) / (next.getTime() - prev.getTime());
        return Pose.lerp(prev.getPose(), next.getPose(), t);
    }
}
```

---

### 1.4 EntityAnimationClip 动画片段

**检查状态**：需要验证是否已存在

**文件**：`src/main/java/com/director/timeline/model/entity/EntityAnimationClip.java`

```java
package com.director.timeline.model.entity;

/**
 * EntityAnimationClip v1.0
 *
 * <p>动画片段，用于播放预制动画。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityAnimationClip extends EntityClip {
    
    /** 动画ID（从 AnimationLibrary 中加载） */
    private String animationId;
    
    /** 是否循环 */
    private boolean loop = true;
    
    /** 播放速度倍率 */
    private float speed = 1.0f;
    
    /** 淡入时间（秒） */
    private float blendInTime = 0.2f;
    
    /** 淡出时间（秒） */
    private float blendOutTime = 0.2f;
    
    public EntityAnimationClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }
    
    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ANIMATION;
    }
    
    // Getters and Setters...
    public String getAnimationId() {
        return animationId;
    }
    
    public void setAnimationId(String animationId) {
        this.animationId = animationId;
    }
    
    public boolean isLoop() {
        return loop;
    }
    
    public void setLoop(boolean loop) {
        this.loop = loop;
    }
    
    public float getSpeed() {
        return speed;
    }
    
    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }
    
    public float getBlendInTime() {
        return blendInTime;
    }
    
    public void setBlendInTime(float blendInTime) {
        this.blendInTime = Math.max(0f, blendInTime);
    }
    
    public float getBlendOutTime() {
        return blendOutTime;
    }
    
    public void setBlendOutTime(float blendOutTime) {
        this.blendOutTime = Math.max(0f, blendOutTime);
    }
}
```

---

## 📋 第二阶段：多演员交互系统（高优先级）

### 2.1 ActorRelationClip 演员关系片段

**文件**：`src/main/java/com/director/timeline/model/entity/ActorRelationClip.java`

```java
package com.director.timeline.model.entity;

import java.util.UUID;

/**
 * ActorRelationClip v1.0
 *
 * <p>演员关系片段，用于定义演员之间的相对位置、朝向等关系。
 *
 * <p>典型场景：
 * <ul>
 *   <li>两人面对面对话</li>
 *   <li>跟随移动</li>
 *   <li>环绕目标</li>
 *   <li>传递物品</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorRelationClip extends EntityClip {
    
    /**
     * 关系类型
     */
    public enum RelationType {
        /** 面对面（保持一定距离，互相注视） */
        FACE_TO_FACE("面对面"),
        
        /** 跟随（保持距离，跟随移动） */
        FOLLOW("跟随"),
        
        /** 环绕（围绕目标旋转） */
        CIRCLE_AROUND("环绕"),
        
        /** 镜像动作（模仿目标动作） */
        MIRROR("镜像"),
        
        /** 传递物品（从 A 到 B 的物品传递） */
        PASS_OBJECT("传递物品"),
        
        /** 相对定位（固定相对位置） */
        RELATIVE_POSITION("相对定位");
        
        private final String displayName;
        
        RelationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /** 目标演员ID */
    private UUID targetActorId;
    
    /** 关系类型 */
    private RelationType relationType = RelationType.FACE_TO_FACE;
    
    /** 距离（方块数） */
    private float distance = 2.0f;
    
    /** 角度（度，用于 CIRCLE_AROUND） */
    private float angle = 0f;
    
    /** 高度偏移（方块数） */
    private float heightOffset = 0f;
    
    /** 是否保持注视 */
    private boolean maintainLookAt = true;
    
    public ActorRelationClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }
    
    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTOR_RELATION;
    }
    
    @Override
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.BLOCK;
    }
    
    // Getters and Setters...
    
    public UUID getTargetActorId() {
        return targetActorId;
    }
    
    public void setTargetActorId(UUID targetActorId) {
        this.targetActorId = targetActorId;
    }
    
    public RelationType getRelationType() {
        return relationType;
    }
    
    public void setRelationType(RelationType relationType) {
        this.relationType = relationType != null ? relationType : RelationType.FACE_TO_FACE;
    }
    
    public float getDistance() {
        return distance;
    }
    
    public void setDistance(float distance) {
        this.distance = Math.max(0.1f, distance);
    }
    
    public float getAngle() {
        return angle;
    }
    
    public void setAngle(float angle) {
        this.angle = angle;
    }
    
    public float getHeightOffset() {
        return heightOffset;
    }
    
    public void setHeightOffset(float heightOffset) {
        this.heightOffset = heightOffset;
    }
    
    public boolean isMaintainLookAt() {
        return maintainLookAt;
    }
    
    public void setMaintainLookAt(boolean maintainLookAt) {
        this.maintainLookAt = maintainLookAt;
    }
}
```

---

### 2.2 ActorGroupController 编队控制器

**文件**：`src/main/java/com/director/actor/group/ActorGroupController.java`

```java
package com.director.actor.group;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistry;
import com.director.actor.action.ActorActionType;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ActorGroupController v1.0
 *
 * <p>演员组控制器，负责：
 * <ul>
 *   <li>计算编队位置</li>
 *   <li>应用延迟策略</li>
 *   <li>同步组动作</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorGroupController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGroupController.class);
    private static final ActorGroupController INSTANCE = new ActorGroupController();
    
    private ActorGroupController() {}
    
    public static ActorGroupController getInstance() {
        return INSTANCE;
    }
    
    /**
     * 计算编队位置
     *
     * @param group 演员组
     * @param centerPosition 中心位置
     * @param facingYaw 朝向（度）
     * @param registry Actor注册表
     * @return 每个演员的目标位置
     */
    public List<FormationPosition> calculateFormation(
            ActorGroup group,
            Vec3d centerPosition,
            float facingYaw,
            ActorRegistry registry) {
        
        List<FormationPosition> positions = new ArrayList<>();
        List<UUID> actorIds = group.getActorIds();
        
        if (actorIds.isEmpty()) {
            return positions;
        }
        
        float spacing = group.getFormationSpacing();
        
        switch (group.getFormationType()) {
            case FREE -> {
                // 自由模式：不改变位置
                for (UUID actorId : actorIds) {
                    positions.add(new FormationPosition(actorId, centerPosition, facingYaw));
                }
            }
            
            case LINE -> {
                // 横线排列
                int count = actorIds.size();
                float startOffset = -(count - 1) * spacing / 2f;
                
                for (int i = 0; i < count; i++) {
                    float offset = startOffset + i * spacing;
                    Vec3d pos = offsetPosition(centerPosition, offset, 0, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
            
            case CIRCLE -> {
                // 圆形排列
                int count = actorIds.size();
                float radius = spacing * count / (2f * (float) Math.PI);
                
                for (int i = 0; i < count; i++) {
                    float angle = 360f * i / count;
                    Vec3d pos = offsetPosition(centerPosition, 
                            (float) (radius * Math.cos(Math.toRadians(angle))),
                            (float) (radius * Math.sin(Math.toRadians(angle))),
                            facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw + angle));
                }
            }
            
            case GRID -> {
                // 网格排列
                int count = actorIds.size();
                int cols = (int) Math.ceil(Math.sqrt(count));
                
                for (int i = 0; i < count; i++) {
                    int row = i / cols;
                    int col = i % cols;
                    
                    float x = (col - cols / 2f) * spacing;
                    float z = row * spacing;
                    
                    Vec3d pos = offsetPosition(centerPosition, x, z, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
            
            case FOLLOW_LEADER -> {
                // 跟随领队（第一个演员是领队）
                if (actorIds.isEmpty()) {
                    break;
                }
                
                // 领队位置
                positions.add(new FormationPosition(actorIds.get(0), centerPosition, facingYaw));
                
                // 其他演员跟随
                for (int i = 1; i < actorIds.size(); i++) {
                    Vec3d pos = offsetPosition(centerPosition, 0, -i * spacing, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * 应用延迟策略
     *
     * @param group 演员组
     * @param action 动作类型
     * @param baseTime 基础时间
     * @return 每个演员的触发时间
     */
    public List<ActionTrigger> applyDelayPolicy(
            ActorGroup group,
            ActorActionType action,
            float baseTime) {
        
        List<ActionTrigger> triggers = new ArrayList<>();
        List<UUID> actorIds = group.getActorIds();
        
        if (actorIds.isEmpty()) {
            return triggers;
        }
        
        float delayStep = group.getDelayStepSeconds();
        
        switch (group.getDelayPolicy()) {
            case NONE -> {
                // 同时触发
                for (UUID actorId : actorIds) {
                    triggers.add(new ActionTrigger(actorId, action, baseTime));
                }
            }
            
            case SEQUENTIAL -> {
                // 顺序触发
                for (int i = 0; i < actorIds.size(); i++) {
                    triggers.add(new ActionTrigger(
                            actorIds.get(i),
                            action,
                            baseTime + i * delayStep));
                }
            }
            
            case WAVE -> {
                // 波浪触发（从两端到中间）
                int count = actorIds.size();
                for (int i = 0; i < count; i++) {
                    int waveIndex = (i % 2 == 0) ? i / 2 : count - 1 - i / 2;
                    triggers.add(new ActionTrigger(
                            actorIds.get(i),
                            action,
                            baseTime + waveIndex * delayStep));
                }
            }
            
            case RANDOM -> {
                // 随机延迟
                java.util.Random random = new java.util.Random();
                for (UUID actorId : actorIds) {
                    float randomDelay = random.nextFloat() * delayStep * actorIds.size();
                    triggers.add(new ActionTrigger(actorId, action, baseTime + randomDelay));
                }
            }
        }
        
        return triggers;
    }
    
    /**
     * 计算相对偏移位置
     */
    private Vec3d offsetPosition(Vec3d center, float xOffset, float zOffset, float yaw) {
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        
        float x = center.x + xOffset * cos - zOffset * sin;
        float z = center.z + xOffset * sin + zOffset * cos;
        
        return new Vec3d(x, center.y, z);
    }
    
    /**
     * 编队位置
     */
    public record FormationPosition(UUID actorId, Vec3d position, float yaw) {}
    
    /**
     * 动作触发
     */
    public record ActionTrigger(UUID actorId, ActorActionType action, float triggerTime) {}
}
```

---

## 📋 第三阶段：补充工具和辅助功能

### 3.1 需要添加到 EntityClipType 的新类型

在 `EntityClipType.java` 中添加：

```java
/**
 * 演员关系片段
 */
ACTOR_RELATION("演员关系", "定义演员之间的相对位置和关系");
```

---

### 3.2 EntityClipSampler 需要补充的采样逻辑

在 `EntityClipSampler.java` 的 `sampleAt()` 方法中添加对 `ACTOR_RELATION` 的支持。

---

### 3.3 UI 属性适配器

需要为新的 Clip 类型创建属性适配器：
- `ActorRelationClipPropertyAdapter.java`
- `EntityAnimationClipPropertyAdapter.java`（如果不存在）

---

## 📝 实施检查清单

### 阶段一：动画系统
- [ ] 创建 `AnimationPlayer.java` 接口
- [ ] 创建 `AnimationLibrary.java` 资源库
- [ ] 创建 `AnimationData.java` 数据类
- [ ] 创建 `AnimationKeyframe.java` 关键帧类
- [ ] 检查/创建 `EntityAnimationClip.java`
- [ ] 在 `EntityClipSampler` 中实现动画采样逻辑
- [ ] 在 `ActorRuntimeController` 中集成 AnimationPlayer
- [ ] 创建 `EntityAnimationClipPropertyAdapter.java`

### 阶段二：多演员交互
- [ ] 创建 `ActorRelationClip.java`
- [ ] 在 `EntityClipType` 中添加 `ACTOR_RELATION`
- [ ] 创建 `ActorGroupController.java`
- [ ] 在 `EntityClipSampler` 中实现关系采样逻辑
- [ ] 创建 `ActorRelationClipPropertyAdapter.java`
- [ ] 测试两人对话场景
- [ ] 测试编队移动场景

### 阶段三：测试和优化
- [ ] 编写单元测试
- [ ] 性能测试
- [ ] 文档补充
- [ ] 示例场景制作

---

## 🎬 示例场景设计

### 场景1：两人对话
```
时间轴：
0.0s - Spawn Actor A (位置: 0, 0, 0)
0.0s - Spawn Actor B (位置: 3, 0, 0)
0.5s - Actor A: ActorRelationClip(FACE_TO_FACE, target=B)
0.5s - Actor B: ActorRelationClip(FACE_TO_FACE, target=A)
1.0s - Actor A: ActorActionClip(DANCE)
2.0s - Actor B: ActorActionClip(CELEBRATE)
```

### 场景2：编队行进
```
Group: 士兵小队
FormationType: LINE
DelayPolicy: SEQUENTIAL
Actions:
- 0.0s: 全体 WALK
- 1.0s: 依次 ATTACK（延迟 0.2s）
- 3.0s: 全体 RUN
```

---

## 🚀 总结

Director 的演员系统已经有了非常扎实的基础，补充以下内容后将达到 95% 完成度：

1. **动画播放器系统**（3-5天） - 支持外部动画文件
2. **多演员交互系统**（2-3天） - 支持多人场景
3. **测试和优化**（2-3天） - 确保稳定性

**总预计时间**：7-11 个工作日

完成后，Director 将能够支持完整的虚拟演员导演workflow，包括：
- ✅ 单演员完整表演
- ✅ 多演员协同场景
- ✅ 复杂动画播放
- ✅ 道具和投掷物
- ✅ 编队和关系系统

这将是一个功能完整、设计优雅的演员系统！
