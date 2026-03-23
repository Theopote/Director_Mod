# Animation × Prop × Action 联动系统文档

## 一、设计总原则

### ❗ 核心结论

**投掷不是一个 Action，而是：**

**一段动画 + 一个精准时刻触发的 Action**

也就是说：

```
AnimationClip（投掷动作）
        ↓
     动画进度
        ↓
某一关键帧（Release）
        ↓
ActionEvent：THROW_PROP
```

👉 **Animation 决定"什么时候松手"**  
👉 **Action 决定"发生了什么事"**

### ❗ 为什么不能"只靠 Action"

如果你这样做：
```
00:02 Action: THROW_PROP
```

**问题是**：
- ❌ 手可能还没抬起来
- ❌ 方向不对
- ❌ 看起来像"瞬移丢出"

这在导演系统里是不可接受的。

---

## 二、正确的职责分工

| 系统                | 负责什么        |
| ----------------- | ----------- |
| AnimationClip     | 肢体运动（抬手、挥臂） |
| PropRuntime       | 道具跟随手       |
| ActionEvent        | 松手那一刻       |
| ProjectileRuntime | 飞行表现        |

👉 **每个系统都只做自己该做的事**

---

## 三、AnimationEvent（动画事件）

### 3.1 最小模型

**位置**：`com.director.ui.timeline.model.entity.AnimationEvent`

**字段**：
```java
public final class AnimationEvent {
    private float normalizedTime;  // 0.0 ~ 1.0
    private EntityActionEvent action;  // 触发的 Action
}
```

**特点**：
- ✅ 绑定到动画的归一化时间（0.0 ~ 1.0）
- ✅ 触发 Action（如 THROW_PROP）
- ✅ 可序列化，可持久化

### 3.2 EntityAnimationClip 增强

**位置**：`com.director.ui.timeline.model.entity.EntityAnimationClip`

**新增字段**：
```java
private final List<AnimationEvent> events = new ArrayList<>();
```

**新增方法**：
```java
public List<AnimationEvent> getEvents()  // 获取事件列表（只读）
public void addEvent(AnimationEvent event)  // 添加事件
public boolean removeEvent(AnimationEvent event)  // 移除事件
public void clearEvents()  // 清空所有事件
public boolean hasEvents()  // 检查是否有事件
```

**⚠️ 注意**：
- `events` 是可选的
- 不影响已有 `AnimationClip` 行为
- 没有事件 = 纯动画

---

## 四、导演视角：如何"正确地表达投掷"

### 4.1 时间线语义

```
00:01  AnimationClip: Villager_Throw
        └─ Event @ 0.62 → THROW_PROP
```

**导演只关心一件事**：

**"在这个动作的 0.62 位置，东西飞出去。"**

### 4.2 完整工作流示例

```
00:00  Spawn Actor
00:01  Action: ATTACH_PROP (Oak Planks, BOTH_HANDS)
00:02  AnimationClip: Villager_Throw
        └─ Event @ 0.62 → THROW_PROP (target: (12, 65, 8))
00:03  (动画继续播放，道具已飞走)
```

---

## 五、Runtime 层：Animation × Action 的联动逻辑

### 5.1 AnimationEventTriggerCache

**位置**：`com.director.ui.timeline.playback.entity.AnimationEventTriggerCache`

**职责**：
- ✅ 管理 AnimationEvent 的触发状态
- ✅ 防止重复触发
- ✅ 支持 rewind / loop

**规则**：
- 只在动画进度达到事件时间点时触发
- 同一事件只触发一次（除非 rewind）
- 当时间离开 Clip 时清除记录

### 5.2 EntityClipSampler：处理动画事件

**处理流程**：
```java
applyAnimation(result, animationClips, timeSeconds) {
    // 1. 找到活跃的 AnimationClip
    EntityAnimationClip active = findActiveClip(clips);
    
    // 2. 应用动画信息
    result.animationId = active.getAnimationId();
    result.animationLoop = active.isLoop();
    result.animationSpeed = active.getSpeed();
    
    // 3. 处理 AnimationEvent
    if (active.hasEvents()) {
        // 计算归一化时间
        float normalizedTime = (timeSeconds - active.getStartTime()) / clipDuration;
        
        // 检查并触发事件
        for (AnimationEvent event : active.getEvents()) {
            if (animationEventCache.shouldTrigger(active, event, normalizedTime)) {
                // 触发 Action 事件
                result.actionEvents.add(event.getAction());
            }
        }
    }
}
```

### 5.3 ActorRuntimeController：处理 Action 事件

**处理流程**：
```java
applyActionEvents(s, prev) {
    for (EntityActionEvent e : s.actionEvents) {
        // 处理 Prop 相关 Action（包括从 AnimationEvent 触发的）
        if (e.type() == THROW_PROP) {
            applyPropAction(actor, entity, e);
        }
    }
}
```

**关键点**：
- AnimationEvent 触发的 Action 和普通 ActionClip 触发的 Action 使用同一套处理逻辑
- 不需要特殊处理，完全复用现有代码

---

## 六、Prop 与 Animation 的"空间同步"

### 6.1 投掷动画播放期间

**Prop 一直绑定在手骨骼上**：
```java
RuntimeProp.updateTransform(PreviewActorEntity actor) {
    // 道具跟随 Actor 的位置/旋转
    // 在动画播放期间，道具会跟随手部运动
}
```

### 6.2 松手那一帧

**AnimationEvent 触发 THROW_PROP**：
```java
case THROW_PROP -> {
    // 1. 获取当前道具位置（作为起始位置）
    Vec3d startPos = prop.getWorldPosition();
    
    // 2. 卸下道具（从 Actor 手中移除）
    propRuntime.detach(slot);
    
    // 3. 创建投掷物
    RuntimeProjectile projectile = new RuntimeProjectile(...);
}
```

**👉 这保证了"松手那一帧"的空间连续性**

---

## 七、完整投掷流程（系统级总结）

```
Actor 持有 Prop
        ↓
AnimationClip 播放（手臂运动）
        ↓
Prop 跟随手部运动
        ↓
AnimationEvent 到达（归一化时间 0.62）
        ↓
THROW_PROP Action 触发
        ↓
从 Actor 手中 detach prop
        ↓
固定当前世界坐标
        ↓
RuntimeProjectile 生成
        ↓
抛物线飞行
```

**Timeline 清晰，Runtime 连贯，表演可信。**

---

## 八、为什么这个设计是"导演系统的正确答案"

### ✅ 动作是"人驱动"的，不是"事件驱动"的
- 所有时序都来源于动画
- 导演只关心"在动画的哪个位置触发"

### ✅ Action 永远是"结果"，不是"过程"
- Action 表达"发生了什么"
- Animation 表达"如何发生"

### ✅ Prop 系统完全复用，不需要任何 hack
- Prop 在动画期间正常跟随
- 事件触发时正常 detach
- 完全复用现有逻辑

---

## 九、你现在已经到达的高度

到目前为止，Director 已经具备：

- 🎭 演员（Actor）
- 🎬 动画（AnimationClip）
- 🧱 道具（Prop）
- 🎯 行为（Action）
- 🥏 投掷（Projectile）
- 🔗 动画事件（AnimationEvent）

这已经是：

**电影级角色导演系统**

而且是在 Minecraft 里。

---

## 十、实现状态

### ✅ 已完成

1. **AnimationEvent 类**
   - ✅ `AnimationEvent` - 动画事件模型
   - ✅ 归一化时间 + Action 事件

2. **EntityAnimationClip 扩展**
   - ✅ `events` 列表（可选）
   - ✅ 事件管理方法（add/remove/clear）

3. **AnimationEventTriggerCache**
   - ✅ 管理事件触发状态
   - ✅ 防止重复触发
   - ✅ 支持 rewind / loop

4. **EntityClipSampler 集成**
   - ✅ 计算归一化时间
   - ✅ 检查并触发 AnimationEvent
   - ✅ 将事件添加到 `actionEvents` 列表

5. **ActorRuntimeController 复用**
   - ✅ 完全复用现有的 Action 处理逻辑
   - ✅ 不需要特殊处理

### 🔄 待实现

1. **UI 支持**
   - ⏳ `EntityAnimationClipPropertyAdapter` 支持事件编辑
   - ⏳ 事件时间点滑块（0.0 ~ 1.0）
   - ⏳ Action 参数编辑

2. **序列化**
   - ⏳ AnimationEvent 持久化
   - ⏳ 从配置文件加载

---

## 十一、使用示例

### 11.1 创建带事件的 AnimationClip

```java
// 创建动画片段
EntityAnimationClip animClip = new EntityAnimationClip("Throw", 2.0f, 3.0f);
animClip.setAnimationId("villager_throw");
animClip.setLoop(false);

// 创建 THROW_PROP Action 事件
var throwParams = new HashMap<String, Object>();
throwParams.put("slot", "BOTH_HANDS");
throwParams.put("targetX", 12.0);
throwParams.put("targetY", 65.0);
throwParams.put("targetZ", 8.0);
throwParams.put("arcHeight", 1.5f);
throwParams.put("flightTime", 1.0f);

EntityActionEvent throwAction = new EntityActionEvent(
    EntityActionClip.ActionType.THROW_PROP,
    throwParams
);

// 在动画的 0.62 位置触发事件
AnimationEvent event = new AnimationEvent(0.62f, throwAction);
animClip.addEvent(event);
```

### 11.2 完整工作流示例

```
00:00  Spawn Actor
00:01  Action: ATTACH_PROP (Oak Planks, BOTH_HANDS)
00:02  AnimationClip: Villager_Throw
        └─ Event @ 0.62 → THROW_PROP
00:03  (动画继续播放，道具已飞走)
```

---

## 十二、架构图

```
Timeline Layer
    ↓
EntityAnimationClip
    └─ AnimationEvent (normalizedTime: 0.62, action: THROW_PROP)
    ↓
EntityClipSampler.sampleAt()
    ↓
计算归一化时间: (time - startTime) / duration
    ↓
AnimationEventTriggerCache.shouldTrigger()
    ↓
result.actionEvents.add(event.getAction())
    ↓
EntitySample.actionEvents
    ↓
ActorRuntimeController.applyActionEvents()
    ↓
applyPropAction() → THROW_PROP
    ↓
propRuntime.detach(slot)
    ↓
RuntimeProjectile (创建)
    ↓
ProjectileRuntimeManager.spawn()
```

---

## 十三、设计原则总结

1. **Animation 驱动，Action 触发**
   - Animation 决定"什么时候"
   - Action 决定"发生什么"

2. **归一化时间，不依赖绝对时间**
   - 0.0 ~ 1.0 表示动画进度
   - 易调、易复现

3. **完全复用现有系统**
   - Prop 系统不需要修改
   - Action 处理逻辑完全复用
   - 只是增加了事件触发机制

4. **不污染 Timeline**
   - 事件绑定在 AnimationClip 内部
   - Timeline 仍然清晰简洁

