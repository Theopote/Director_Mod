# Director 演员系统分析报告

## 📊 当前实现状态

生成日期：2026-06-25

---

## 1. 核心架构概览

### ✅ 已完成的核心模块

#### 1.1 Actor 数据模型（完整）
- **Actor** - 虚拟演员实体，包含：
  - UUID、名称、类型（ActorType）
  - RigProfile（姿态/动作配置）
- **ActorType** - 演员类型枚举：PLAYER, VILLAGER, COW, SHEEP, ZOMBIE, SKELETON, CUSTOM
- **ActorRegistry** - 项目级演员管理器
- **ActorGroup** - 演员组/编队系统
  - FormationType: FREE, LINE, CIRCLE, GRID, FOLLOW_LEADER
  - DelayPolicy: NONE, SEQUENTIAL, WAVE, RANDOM

#### 1.2 Timeline 集成（完整）
- **ActorTrack** - 演员轨道
  - 通过 ActorRef 引用 Actor（解耦设计）
  - EntityBinding（生成/绑定配置）
  - Clips 管理（TreeSet 按时间排序）

#### 1.3 EntityClip 系统（部分完成）
已实现的 Clip 类型：
- ✅ **EntitySpawnClip** - 入场/生成片段
- ✅ **ActorActionClip** - 动作状态片段（IDLE, WALK, RUN, DANCE 等）
- ✅ **ActorPoseClip** - 姿态覆盖片段（头、身体、手臂、腿）
- ✅ **ActorRouteClip** - 路径移动片段
  - 支持 LINEAR/SMOOTH 插值
  - 支持 ONCE/LOOP/PING_PONG 循环模式
  - 路径点对齐旋转

#### 1.4 动作系统（完整）
- **ActorActionType** - 动作语义枚举（13种）：
  - 基础：IDLE, WALK, RUN
  - 表演：DANCE, CELEBRATE, SIT, MEDITATE
  - 操作：CARRY, THROW_PREPARE, ATTACK
  - 动物：GRAZE, ROAR
  - 扩展：CUSTOM

#### 1.5 Rig 系统（完整）
- **ActorRigProfile** - 姿态生成策略接口
  - supports() - 支持的动作检查
  - applyAction() - 应用动作到 Runtime
  - buildPose() - 生成姿态（用于混合）
- **HumanoidRigProfile** - 人形骨骼配置
- **QuadrupedRigProfile** - 四足动物骨骼配置
- **RigProfileStyle** - 风格参数（步伐频率、摆动幅度等）

#### 1.6 Runtime 系统（完整）
- **ActorRuntimeController** - 演员运行时总控制器
  - Actor → PreviewActorEntity 映射
  - Timeline + Sampler 驱动
  - Spawn/Despawn 生命周期
  - Transform/LookAt/State/Action 应用
  - Pose 混合系统（RigProfile + Animation）
- **ActorPreviewManager** - 预览实体管理器
- **EntityClipSampler** - Clip 采样器

#### 1.7 道具系统（完整）
- **ActorPropRuntime** - 道具运行时
- **ActorPropSlot** - 道具槽位（LEFT_HAND, RIGHT_HAND, BACK 等）
- **RuntimeProp** - 运行时道具
- **ProjectileRuntimeManager** - 投掷物管理器
- **RuntimeProjectile** - 运行时投掷物（抛物线轨迹）

#### 1.8 UI 工具（完整）
- **ActorSelectTool** - 演员选择工具
- **ActorMoveTool** - 演员移动工具
- **ActorRotateTool** - 演员旋转工具
- **ActorScaleTool** - 演员缩放工具
- **ActorLookAtTool** - 演员注视工具
- **ActorPoseTool** - 演员姿态工具
- **ActorPathTool** - 演员路径工具
- **ActorGizmoModeTool** - Gizmo 模式切换工具

---

## 2. ❌ 缺失的功能模块

### 2.1 高优先级缺失功能

#### A. EntityActionClip（瞬时动作片段）⚠️ **未实现**
**目的**：表示瞬时触发的动作事件（投掷、放置、拾取等）

**缺失内容**：
```java
// 需要实现的类
public class EntityActionClip extends EntityClip {
    private ActionType actionType;
    private Map<String, Object> params;
    
    public enum ActionType {
        THROW,           // 投掷
        PLACE,           // 放置物品
        PICKUP,          // 拾起物品
        JUMP,            // 跳跃
        CROUCH,          // 蹲下
        ATTACH_PROP,     // 装备道具
        DETACH_PROP,     // 卸下道具
        SWITCH_PROP,     // 切换道具
        THROW_PROP,      // 投掷道具
        PLAY_SOUND,      // 播放音效
        SPAWN_PARTICLE   // 生成粒子
    }
}
```

**影响**：
- 无法在 Timeline 上标记瞬时事件
- 投掷、跳跃等动作只能通过代码触发
- UI 缺少事件标记功能

---

#### B. EntityTransformClip（变换动画片段）⚠️ **未实现**
**目的**：实现位置、旋转、缩放的关键帧动画

**缺失内容**：
```java
// 需要实现的类
public class EntityTransformClip extends EntityClip {
    private List<TransformKeyframe> keyframes;
    private InterpolationMode interpolation;
    
    public static class TransformKeyframe {
        float time;
        Vector3f position;
        Vector3f rotation;  // Euler angles
        Vector3f scale;
        EasingFunction easing;
    }
    
    public enum InterpolationMode {
        LINEAR,
        CUBIC_BEZIER,
        HERMITE_SPLINE
    }
}
```

**影响**：
- 演员只能通过 RouteClip 移动（无法做复杂动画）
- 无法制作旋转、缩放动画
- 缺少关键帧编辑功能

---

#### C. EntityLookAtClip（注视目标片段）⚠️ **部分实现**
**当前状态**：
- ✅ 有 `EntityLookAtClip.java` 类
- ❌ 但没有集成到 EntityClipSampler
- ❌ UI 工具不完整

**需要补充**：
```java
// 需要在 EntityClipSampler 中添加
private void sampleLookAtClips(float time, EntitySample sample) {
    for (EntityClip clip : clips) {
        if (clip instanceof EntityLookAtClip lookAtClip) {
            if (isActive(clip, time)) {
                sample.lookAtEnabled = true;
                sample.lookAtPoint = lookAtClip.getTarget();
                sample.lookAtStrength = lookAtClip.getStrength();
            }
        }
    }
}
```

---

#### D. 动画播放器系统⚠️ **未实现**
**目的**：播放预制动画（.anim 文件 / Geckolib / 骨骼动画）

**缺失内容**：
```java
// 需要实现的接口和类
public interface AnimationPlayer {
    void play(String animationId, boolean loop);
    void stop();
    AnimationPoseResult getCurrentPose();
    float getProgress();
}

public class AnimationClip extends EntityClip {
    private String animationId;      // 动画资源 ID
    private boolean loop;
    private float speed;
    private float blendInTime;
    private float blendOutTime;
}

public class AnimationLibrary {
    Map<String, AnimationData> animations;
    AnimationData load(String id);
}
```

**影响**：
- 无法播放外部动画文件
- 只能使用 RigProfile 生成的程序化动画
- 无法制作复杂的战斗/舞蹈动画

---

#### E. 多演员交互系统⚠️ **未实现**
**目的**：实现演员之间的相对定位、注视、传递物品等

**缺失内容**：
```java
// 需要实现的类
public class ActorRelationClip extends EntityClip {
    private UUID targetActorId;          // 目标演员
    private RelationType relationType;    // 关系类型
    private float distance;               // 距离
    private float angle;                  // 角度
    
    public enum RelationType {
        FACE_TO_FACE,      // 面对面
        FOLLOW,            // 跟随
        CIRCLE_AROUND,     // 环绕
        MIRROR,            // 镜像动作
        PASS_OBJECT        // 传递物品
    }
}

public class ActorGroupController {
    void updateFormation(ActorGroup group, float time);
    void applyDelayPolicy(ActorGroup group, ActorActionType action);
}
```

**影响**：
- 无法实现"两人对话"场景
- 无法实现编队行进
- 无法实现物品传递

---

### 2.2 中优先级缺失功能

#### F. 物理模拟系统⚠️ **未实现**
**目的**：实现布娃娃效果、碰撞、重力等

**缺失内容**：
```java
public class PhysicsClip extends EntityClip {
    private boolean enableGravity;
    private boolean enableCollision;
    private PhysicsPreset preset;  // RAGDOLL, RIGID, SOFT_BODY
}

public class PhysicsSimulator {
    void update(Entity entity, float deltaTime);
    void applyForce(Entity entity, Vec3d force);
}
```

---

#### G. 表情/情绪系统⚠️ **未实现**
**目的**：实现面部表情、情绪状态

**缺失内容**：
```java
public class EmotionClip extends EntityClip {
    private EmotionType emotion;  // HAPPY, SAD, ANGRY, SURPRISED, FEAR
    private float intensity;
}

public class FacialAnimationController {
    void setExpression(EmotionType emotion, float intensity);
}
```

---

#### H. 音频同步系统⚠️ **未实现**
**目的**：让演员动作与音频同步（口型同步、音乐节奏）

**缺失内容**：
```java
public class AudioSyncClip extends EntityClip {
    private String audioTrackId;
    private SyncMode syncMode;  // LIP_SYNC, BEAT_SYNC, RHYTHM_SYNC
}
```

---

### 2.3 低优先级/未来功能

#### I. IK（反向动力学）系统
#### J. 布料模拟系统
#### K. 粒子特效绑定系统
#### L. 摄像机交互（演员看向镜头）
#### M. 多轨道混合（同时执行多个动作）

---

## 3. 补充实现优先级建议

### 第一阶段：核心动作系统（1-2周）
1. ⭐ **EntityActionClip** - 瞬时事件片段
2. ⭐ **EntityTransformClip** - 变换动画片段
3. ⭐ 完善 **EntityLookAtClip** 集成

**目标**：让演员能在 Timeline 上做完整的表演（移动 + 动作 + 注视）

### 第二阶段：高级动作系统（2-3周）
4. ⭐ **AnimationPlayer** 系统 - 播放预制动画
5. ⭐ **ActorRelationClip** - 多演员交互
6. ⭐ **ActorGroupController** - 编队控制器

**目标**：实现多演员场景（对话、追逐、战斗）

### 第三阶段：表现增强（1-2周）
7. **PhysicsClip** - 物理模拟
8. **EmotionClip** - 表情系统
9. **AudioSyncClip** - 音频同步

**目标**：提升表演质量和真实感

---

## 4. 代码质量评估

### ✅ 优秀的设计
1. **Actor ↔ Timeline 解耦**：通过 ActorRef 引用，设计清晰
2. **Rig 系统**：ActorRigProfile 接口设计优雅，支持多物种
3. **Pose 混合系统**：RigProfile + Animation 混合架构完整
4. **道具系统**：ActorPropRuntime 设计完整，支持投掷物
5. **UI 工具链**：Actor 工具集完整，Gizmo 系统成熟

### ⚠️ 需要注意的问题
1. **EntityClipSampler** 采样逻辑复杂，需要优化
2. **ActorRuntimeController** 职责过重（800+ 行），建议拆分
3. **缺少单元测试**：关键的 Sampler/Runtime 逻辑没有测试
4. **文档不完整**：部分类缺少使用示例

---

## 5. 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Director 演员系统                      │
└─────────────────────────────────────────────────────────┘

┌──────────────┐
│ ActorRegistry│  ← 项目级演员管理
│  - Actor     │
│  - ActorGroup│
└──────┬───────┘
       │
       │ ActorRef
       ↓
┌──────────────┐
│ActorTrack    │  ← Timeline 集成
│  - ActorRef  │
│  - Clips[]   │
└──────┬───────┘
       │
       ├─→ EntitySpawnClip       ✅ 完成
       ├─→ ActorActionClip       ✅ 完成
       ├─→ ActorPoseClip         ✅ 完成
       ├─→ ActorRouteClip        ✅ 完成
       ├─→ EntityLookAtClip      ⚠️ 部分完成
       ├─→ EntityActionClip      ❌ 未实现
       ├─→ EntityTransformClip   ❌ 未实现
       └─→ AnimationClip         ❌ 未实现
       
       ↓ EntityClipSampler
       
┌──────────────┐
│EntitySample  │  ← 采样结果
│  - position  │
│  - rotation  │
│  - action    │
│  - lookAt    │
└──────┬───────┘
       ↓
┌─────────────────┐
│ActorRuntime     │  ← 运行时控制器
│Controller       │
│  - Spawn/Despawn│
│  - Apply Sample │
│  - Pose Blend   │
└──────┬──────────┘
       ↓
┌─────────────────┐
│PreviewActor     │  ← 客户端预览实体
│Entity           │
│  - Render       │
│  - PropRuntime  │
└─────────────────┘
```

---

## 6. 下一步行动

### 立即开始（本次任务）
1. 实现 **EntityActionClip** 类
2. 实现 **EntityTransformClip** 类
3. 完善 **EntityLookAtClip** 集成
4. 补充 **EntityClipSampler** 采样逻辑
5. 添加 UI 属性适配器

### 后续任务（下次迭代）
6. 实现 **AnimationPlayer** 系统
7. 实现 **ActorRelationClip** 系统
8. 添加单元测试覆盖

---

## 7. 结论

Director 的演员系统**基础架构已完成 70%**，核心的数据模型、Timeline 集成、Runtime 系统都已经实现且设计优雅。

**最关键的缺失**是：
1. **EntityActionClip**（瞬时事件）
2. **EntityTransformClip**（关键帧动画）
3. **Animation播放器**（外部动画）

补充这三个模块后，演员系统就能支持完整的表演制作流程。
