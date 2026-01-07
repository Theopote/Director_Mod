# Prop Throw / Projectile Runtime 系统文档

## 一、设计总原则

### ❗ 核心结论

**Throw = 一次 Action**  
**Projectile = Runtime 中的临时视觉对象**  
**抛物线 = Runtime 的时间函数**  
**Timeline 不关心"飞怎么飞"**

### ❗ 关键原则

1. **Projectile 不属于 Actor**
   - 它是一个"临时舞台对象（Transient Prop）"
   - ❌ 不放进 `ActorPropRuntime`
   - ❌ 不绑定到 Actor 骨骼
   - ✅ 用一个 `ProjectileRuntimeManager` 统一管理

2. **Timeline 只表达意图**
   - Timeline 只记录"目标点"和"感觉参数"
   - 不记录速度、不记录每一帧位置

3. **Runtime 完全函数化**
   - `time → position`
   - 易调、易复现

4. **Actor / World 完全解耦**
   - 演出安全
   - 可反复拍

---

## 二、Timeline 层：如何"表达一次投掷"

### 2.1 新增 Action 类型

**位置**：`com.chronoblocks.ui.timeline.model.entity.EntityActionClip.ActionType`

**新增类型**：
```java
THROW_PROP("投掷道具")
```

### 2.2 THROW_PROP 的参数定义

**ActionClip 参数**：
```java
{
    "slot": "MAIN_HAND",           // 从哪个槽位投掷
    "targetX": 12.0,               // 目标位置 X
    "targetY": 65.0,               // 目标位置 Y
    "targetZ": 8.0,                // 目标位置 Z
    "arcHeight": 1.5,              // 抛物线高度（可选，默认 1.5）
    "flightTime": 1.0              // 飞行时长（可选，默认 1.0 秒）
}
```

### 2.3 导演时间线语义示例

```
00:00  Spawn
00:01  Action: ATTACH_PROP (Oak Planks, BOTH_HANDS)
00:02  Walk Path
00:06  Action: THROW_PROP
        slot = BOTH_HANDS
        target = (12, 65, 8)
        arc = 1.5
        time = 1.0
00:07  Action: PLACE_BLOCK (落地后放置方块)
```

**注意**：
- `THROW_PROP` 会自动从 Actor 手中卸下道具
- Timeline 不需要再写一个 `DETACH_PROP`

---

## 三、Runtime 层：Throw 的真实实现

### 3.1 RuntimeProjectile（一次投掷的实例）

**位置**：`com.chronoblocks.actor.prop.RuntimeProjectile`

**职责**：
- ✅ 管理投掷物的生命周期
- ✅ 计算抛物线轨迹（时间函数）
- ✅ 更新道具位置

**核心方法**：
```java
public void update(float deltaTime)  // 每帧更新
public boolean isFinished()          // 检查是否完成
```

**抛物线计算**：
```java
private Vec3d interpolateArc(Vec3d a, Vec3d b, float t, float height) {
    // 线性插值（水平方向）
    Vec3d linear = a.lerp(b, t);
    
    // 抛物线高度（sin 函数，在 t=0.5 时达到最高点）
    double yOffset = Math.sin(t * Math.PI) * height;
    
    return linear.add(0, yOffset, 0);
}
```

### 3.2 ProjectileRuntimeManager（统一管理）

**位置**：`com.chronoblocks.actor.prop.ProjectileRuntimeManager`

**职责**：
- ✅ 管理所有投掷中的道具
- ✅ 每帧更新所有投掷物
- ✅ 自动清理已完成的投掷物

**核心方法**：
```java
public void spawn(RuntimeProjectile projectile)  // 生成投掷物
public void update(float deltaTime)             // 每帧更新
public void clear()                             // 清空所有投掷物
```

### 3.3 RuntimeProp 扩展

**新增方法**：
```java
public void setWorldTransform(Vec3d pos)  // 设置世界位置（用于投掷）
public Vec3d getWorldPosition()          // 获取当前世界位置
public boolean isBoundToActor()          // 检查是否绑定到 Actor
```

**设计**：
- 当调用 `setWorldTransform()` 后，道具不再跟随 Actor
- 用于投掷时的独立位置控制

### 3.4 ActorRuntimeController：接入 THROW_PROP

**处理流程**：
```java
case THROW_PROP -> {
    // 1. 找到当前手中的 Prop
    RuntimeProp prop = propRuntime.getProp(slot);
    
    // 2. 计算起点（演员手的位置）
    Vec3d startPos = prop.getWorldPosition();
    
    // 3. 获取目标位置
    Vec3d targetPos = new Vec3d(targetX, targetY, targetZ);
    
    // 4. 卸下道具（从 Actor 手中移除）
    propRuntime.detach(slot);
    
    // 5. 创建 Projectile
    RuntimeProjectile projectile = new RuntimeProjectile(
        prop, startPos, targetPos, arcHeight, flightTime
    );
    
    // 6. 生成投掷物
    projectileManager.spawn(projectile);
}
```

**每帧更新**：
```java
public void update() {
    // ... 更新轨道运行时 ...
    
    // 更新投掷物（每帧更新，使用 deltaTime）
    projectileManager.update(deltaTime);
}
```

---

## 四、编辑态 vs 导出态

### 4.1 编辑态（Preview）

**投掷只生成**：
- ✅ `RenderProp`（视觉对象）
- ✅ 抛物线动画
- ❌ 不影响世界
- ✅ 可反复预览

### 4.2 导出 / 实拍 / Bake（未来）

**当你做"导出为真实世界行为"时**：

在 `RuntimeProjectile` 结束时：

**可选**：
- `world.setBlockState(targetPos)` - 放置方块
- 生成真实 `ItemEntity` - 生成掉落物

**这一步不属于 ChronoBlocks Timeline，而是 Export Pipeline。**

---

## 五、你现在已经能实现的导演级动作

有了 Throw / Projectile Runtime，你可以：

### 🥏 NPC 扔方块
```
AttachProp(OakPlanks, BOTH_HANDS)
Walk
ThrowProp(target: (12, 65, 8))
```

### 🧱 抛物线放置
```
ThrowProp(target: (12, 65, 8))
→ 落地生成方块（导出态）
```

### 🎭 表演动作
- 扔礼物
- 抛炸弹（视觉）
- 投掷工具

---

## 六、为什么这个设计是"干净而强大"的

### ✅ Timeline 极简
- 一个 Action = 一次投掷
- 不关心运动学

### ✅ Runtime 完全函数化
- `time → position`
- 易调、易复现

### ✅ Actor / World 完全解耦
- 演出安全
- 可反复拍

---

## 七、一个"非常重要的导演级提醒"

**ChronoBlocks 不追求"物理真实"，而追求"表演可信"。**

这个抛物线模型就是正确的选择。

---

## 八、实现状态

### ✅ 已完成

1. **Action 扩展**
   - ✅ `EntityActionClip.ActionType.THROW_PROP`

2. **Runtime 系统**
   - ✅ `RuntimeProjectile` - 投掷物实例
   - ✅ `ProjectileRuntimeManager` - 投掷物管理器

3. **RuntimeProp 扩展**
   - ✅ `setWorldTransform()` - 设置世界位置
   - ✅ `getWorldPosition()` - 获取世界位置
   - ✅ `isBoundToActor()` - 检查绑定状态

4. **ActorRuntimeController 集成**
   - ✅ 处理 `THROW_PROP` Action
   - ✅ 每帧更新 `ProjectileRuntimeManager`
   - ✅ 清理投掷物（当禁用时）

### 🔄 待实现

1. **渲染系统**
   - ⏳ `RenderPropObject` - 渲染对象（未来扩展）
   - ⏳ 投掷物的视觉表现

2. **UI 支持**
   - ⏳ ActionClip 属性面板支持 THROW_PROP 参数编辑
   - ⏳ 目标位置选择器（从世界点击选择）

3. **导出 Pipeline**
   - ⏳ 落地生成方块
   - ⏳ 生成真实 ItemEntity

---

## 九、使用示例

### 9.1 创建 THROW_PROP ActionClip

```java
EntityActionClip clip = new EntityActionClip("Throw Prop", 6.0f, 6.01f);
clip.setActionType(EntityActionClip.ActionType.THROW_PROP);
clip.setParam("slot", "BOTH_HANDS");
clip.setParam("targetX", 12.0);
clip.setParam("targetY", 65.0);
clip.setParam("targetZ", 8.0);
clip.setParam("arcHeight", 1.5f);
clip.setParam("flightTime", 1.0f);
```

### 9.2 完整工作流示例

```
00:00  Spawn Actor
00:01  Action: ATTACH_PROP (Oak Planks, BOTH_HANDS)
00:02  Transform: Walk to position
00:06  Action: THROW_PROP (target: (12, 65, 8))
00:07  Action: PLACE_BLOCK (落地后放置方块)
```

---

## 十、架构图

```
Timeline Layer
    ↓
EntityActionClip (THROW_PROP)
    ↓
EntityClipSampler.sampleAt()
    ↓
EntityActionEvent
    ↓
ActorRuntimeController.applyPropAction()
    ↓
propRuntime.detach(slot)  // 从 Actor 手中移除
    ↓
RuntimeProjectile (创建)
    ↓
ProjectileRuntimeManager.spawn()
    ↓
每帧: ProjectileRuntimeManager.update(deltaTime)
    ↓
RuntimeProjectile.update(deltaTime)
    ↓
RuntimeProp.setWorldTransform(pos)  // 更新位置
    ↓
RenderPropObject (未来)
```

---

## 十一、设计原则总结

1. **Throw = Action，不是持续动画**
   - Timeline 只表达"投掷意图"
   - Runtime 负责计算轨迹

2. **Projectile 不属于 Actor**
   - 临时舞台对象
   - 独立管理

3. **抛物线 = 时间函数**
   - `time → position`
   - 易调、易复现

4. **不污染 World**
   - 编辑态全是 Preview
   - 导出态是可选阶段

