# Prop（道具）系统架构文档

## 一、设计总原则

### ❗ Prop 的本质定义

**Prop 是"演员表演时临时携带的道具"，不是世界中的真实方块/实体。**

因此：
- ❌ Prop ≠ Minecraft Block
- ❌ Prop ≠ Minecraft Entity
- ✅ Prop = Actor 的一个"表演附属物（Attachment）"

### ❗ 三个分离（非常重要）

```
Timeline      → 什么时候拿/放/扔（意图）
Runtime       → Prop 的出现/跟随/消失（表现）
World（可选）→ 是否真的生成方块/物品（落地）
```

👉 **Prop 系统必须严格遵守这个分层**

---

## 二、Prop 系统在整体架构中的位置

### 2.1 不新增 Track

Prop 完全不需要新 Track，它是：

**Action / State 的一个"表演对象"**

现有架构：
```
Actor
 └─ ActorTrack
     ├─ TransformClip
     ├─ LookAtClip
     ├─ StateClip
     └─ ActionClip  ← Prop 在这里
```

### 2.2 使用 ActionClip 表达 Prop 变化

**关键结论**：Prop 的变化是"事件"，不是持续动画

所以：
- ✅ 用 `ActionClip`，而不是 `TransformClip` / `StateClip`
- ✅ 新增 Action 类型：`ATTACH_PROP`, `DETACH_PROP`, `SWITCH_PROP`

---

## 三、数据模型

### 3.1 PropDescriptor（道具描述符）

**位置**：`com.director.actor.prop.PropDescriptor`

**职责**：定义道具的视觉表现和元数据

**特点**：
- ✅ 不依赖 Minecraft 类（Block / Entity）
- ✅ 只描述"视觉表现"，不描述"世界行为"
- ✅ 可序列化，可持久化

**字段**：
```java
public final class PropDescriptor {
    private final String id;           // "oak_planks"
    private final String displayName;  // "Oak Planks"
    private final PropVisualType visualType;  // BLOCK / ITEM / CUSTOM
    private final String blockStateId; // "minecraft:oak_planks"
}
```

### 3.2 ActorPropSlot（演员道具槽位）

**位置**：`com.director.actor.prop.ActorPropSlot`

**职责**：定义道具可以"附着"在演员的哪个部位

**槽位类型**：
- `MAIN_HAND` - 主手（右手）
- `OFF_HAND` - 副手（左手）
- `BOTH_HANDS` - 双手（抱方块）
- `HEAD` - 头部（戴帽子）
- `BACK` - 背部（背背包）

### 3.3 PropRegistry（道具注册表）

**位置**：`com.director.actor.prop.PropRegistry`

**职责**：管理所有可用的道具描述符

**特点**：
- ✅ 全局单例
- ✅ 可序列化、可持久化
- ✅ 支持运行时注册

---

## 四、Timeline 层：如何"表达"拿/放/切换道具

### 4.1 扩展 ActionType

**位置**：`com.director.ui.timeline.model.entity.EntityActionClip.ActionType`

**新增类型**：
```java
ATTACH_PROP("装备道具"),  // 装备道具到指定槽位
DETACH_PROP("卸下道具"),  // 从指定槽位卸下道具
SWITCH_PROP("切换道具")   // 切换道具（先卸下，再装备）
```

### 4.2 ActionClip 参数

**ATTACH_PROP 参数**：
```java
{
    "propId": "oak_planks",        // 道具 ID
    "slot": "BOTH_HANDS",          // 槽位
    "offsetX": 0.0,                // 局部偏移（可选）
    "offsetY": 0.0,
    "offsetZ": 0.0,
    "rotationX": 0.0,              // 局部旋转（可选）
    "rotationY": 0.0,
    "rotationZ": 0.0
}
```

**DETACH_PROP 参数**：
```java
{
    "slot": "BOTH_HANDS"  // 槽位
}
```

**SWITCH_PROP 参数**：
```java
{
    "oldSlot": "MAIN_HAND",        // 旧槽位
    "newPropId": "stone",          // 新道具 ID
    "newSlot": "MAIN_HAND"         // 新槽位
}
```

### 4.3 导演时间线表达示例

```
00:00  Spawn
00:01  Action: ATTACH_PROP (Oak Planks, BOTH_HANDS)
00:02  Walk Path
00:06  Action: DETACH_PROP
00:06.1 Action: PLACE_BLOCK
```

👉 **Timeline 不关心 Prop 如何跟着手动，它只说："现在拿/现在放"**

---

## 五、Runtime 层：Prop 如何"真实表现"

### 5.1 ActorPropRuntime（演员携带道具的状态）

**位置**：`com.director.actor.prop.ActorPropRuntime`

**职责**：
- ✅ 管理 `RuntimeProp` 的创建/销毁
- ✅ 更新道具的 Transform（跟随演员）
- ❌ 不关心 Timeline / Clip
- ❌ 不关心渲染细节（由 RuntimeProp 负责）

**核心方法**：
```java
public void attach(ActorPropSlot slot, RuntimeProp prop)  // 装备道具
public void detach(ActorPropSlot slot)                    // 卸下道具
public void update()                                       // 每帧更新（跟随演员）
```

### 5.2 RuntimeProp（运行时道具）

**位置**：`com.director.actor.prop.RuntimeProp`

**职责**：
- ✅ 纯客户端视觉对象
- ✅ 和世界完全解耦
- ✅ 跟随演员的位置/旋转

**特点**：
- ✅ 绑定到 `PreviewActorEntity`
- ✅ 支持局部偏移和旋转
- ✅ 每帧更新 Transform

### 5.3 ActorRuntimeController：处理 Prop Action

**位置**：`com.director.actor.runtime.ActorRuntimeController`

**处理流程**：
```java
applyActionEvents() 
    ↓
检测到 ATTACH_PROP / DETACH_PROP / SWITCH_PROP
    ↓
applyPropAction()
    ↓
getOrCreatePropRuntime(actor)
    ↓
创建/更新 RuntimeProp
    ↓
propRuntime.attach() / detach()
```

### 5.4 每帧更新：Prop 跟随演员

在 Runtime 的 update loop 中：
```java
// 4️⃣ Apply Sample
applySample(entity, sample, lastSample);

// 5️⃣ 更新 Prop Runtime（每帧更新道具 Transform）
ActorPropRuntime propRuntime = propRuntimes.get(actor.getActorId());
if (propRuntime != null) {
    propRuntime.update();
}
```

👉 **这样 Prop 会自然跟随：走路、转头、LookAt**

---

## 六、放置/扔/交接：为什么 Prop 设计很"干净"

### 6.1 村民抱方块 → 放下

**Timeline**：
```
AttachProp(OakPlanks)
Walk
DetachProp
PlaceBlock
```

**说明**：
- Prop 负责视觉（抱方块）
- PlaceBlock Action 决定是否真的 setBlock

### 6.2 NPC 扔方块

**Timeline**：
```
AttachProp
ThrowProp(target)
DetachProp
```

**Runtime**：
- Detach 时生成一个 `TransientPropProjectile`
- 抛物线飞行
- 落地消失 / 生成方块

👉 **Prop 系统不需要改一行**

---

## 七、为什么这个 Prop 设计"长期不会崩"

### ✅ 1. 不污染 Timeline

- Timeline 只有 Action
- 没有"持续携带"这种时间歧义

### ✅ 2. 不污染 World

- 编辑态全是 Preview
- 世界落地是可选阶段

### ✅ 3. 可无限扩展

未来可以自然加：
- 双手抱
- 背包
- 抛接
- 武器
- 工具
- 特效道具

---

## 八、一句话总结

**Prop 是"演员的表演道具"，不是"世界的物理对象"。**

只要你守住这条线，Director 就会一直是一个导演系统，而不是"半 AI + 半动画"的混乱工具。

---

## 九、实现状态

### ✅ 已完成

1. **核心数据模型**
   - ✅ `PropDescriptor` - 道具描述符
   - ✅ `PropVisualType` - 视觉类型枚举
   - ✅ `ActorPropSlot` - 槽位枚举
   - ✅ `PropRegistry` - 道具注册表

2. **Runtime 系统**
   - ✅ `ActorPropRuntime` - 演员道具运行时
   - ✅ `RuntimeProp` - 运行时道具

3. **Action 扩展**
   - ✅ `EntityActionClip.ActionType` 新增 `ATTACH_PROP`, `DETACH_PROP`, `SWITCH_PROP`
   - ✅ `ActorRuntimeController` 处理 Prop Action
   - ✅ 每帧更新 Prop Transform

### 🔄 待实现

1. **渲染系统**
   - ⏳ `RenderPropObject` - 渲染对象（未来扩展）
   - ⏳ 骨骼绑定（手/头/背的位置计算）

2. **UI 支持**
   - ⏳ ActionClip 属性面板支持 Prop 参数编辑
   - ⏳ Prop 选择器（从 PropRegistry 选择）

3. **序列化**
   - ⏳ PropDescriptor 持久化
   - ⏳ PropRegistry 从配置文件加载

---

## 十、使用示例

### 10.1 创建 ActionClip 装备道具

```java
EntityActionClip clip = new EntityActionClip("Attach Prop", 1.0f, 1.01f);
clip.setActionType(EntityActionClip.ActionType.ATTACH_PROP);
clip.setParam("propId", "oak_planks");
clip.setParam("slot", "BOTH_HANDS");
clip.setParam("offsetX", 0.0f);
clip.setParam("offsetY", 0.5f);
clip.setParam("offsetZ", 0.0f);
```

### 10.2 注册新道具

```java
PropRegistry registry = PropRegistry.getInstance();
PropDescriptor desc = new PropDescriptor(
    "custom_block",
    "Custom Block",
    PropVisualType.BLOCK,
    "minecraft:stone"
);
registry.register(desc);
```

---

## 十一、架构图

```
Timeline Layer
    ↓
EntityActionClip (ATTACH_PROP)
    ↓
EntityClipSampler.sampleAt()
    ↓
EntityActionEvent
    ↓
ActorRuntimeController.applyPropAction()
    ↓
ActorPropRuntime.attach()
    ↓
RuntimeProp (创建)
    ↓
每帧: RuntimeProp.updateTransform()
    ↓
RenderPropObject (未来)
```

---

## 十二、设计原则总结

1. **Prop ≠ World Object**
   - Prop 是表演道具，不是世界实体
   - 编辑态全是 Preview

2. **Timeline 只表达意图**
   - 用 Action 表达"拿/放/切换"
   - 不关心"如何跟随"

3. **Runtime 负责表现**
   - 每帧更新 Transform
   - 跟随演员的位置/旋转

4. **可扩展性**
   - 不污染现有系统
   - 未来可无限扩展

