# Director Actor System · 核心架构

## 🎭 核心宪法（Architecture Law）

### 1️⃣ Actor 是"导演概念"，不是游戏对象

```
Actor（演员）
  ├─ 存在于 Timeline / Project
  ├─ 有角色、名字、类型
  ├─ 有表演（EntityTrack + Clips）
  └─ 不直接等同于任何 Minecraft Entity
```

**Minecraft Entity 只是 Actor 的一种"表现形式（Projection）"**

### 2️⃣ Timeline 永远只认识 Actor，不认识 Entity

Timeline 里：
- ❌ 不出现 Entity UUID
- ❌ 不直接引用 world entity
- ✅ 只出现 ActorId

这条非常重要，它保证了：
- 时间线可复制
- 项目可导出
- 演出可重放
- 世界不会被污染

### 3️⃣ Minecraft Entity 只存在于 Runtime / Preview 层

```
[Timeline / Actor / Clip]  ← 纯数据
          ↓
[Sampler / EntitySample]   ← 状态快照
          ↓
[ActorRuntimeController]   ← 运行时
          ↓
[Preview Entity / Render Proxy] ← 客户端投影
```

## 🧱 Actor System（最终形态）

### Actor（纯数据对象）

```java
public final class Actor {
    UUID actorId;
    String name;
    ActorType type;          // VILLAGER / COW / ZOMBIE / PLAYER / CUSTOM
    ActorModel model;        // 外观（后期扩展）
}
```

**特点**：
- 不包含世界信息
- 不包含位置
- 不包含 Entity UUID
- 只描述"我是谁"

### EntityTrack = Actor 的表演轨道

```
Actor
 └─ EntityTrack
      ├─ SpawnClip
      ├─ TransformClip
      ├─ AnimationClip
      ├─ ActionClip
      └─ DespawnClip
```

## 👻 Preview Entity（Ghost / Proxy）

### 定义

一个**只存在于客户端、不写入世界、不参与逻辑**的"演员投影"

### 能力要求

| 能力   | 说明                 |
| ---- | ------------------ |
| 只客户端 | 不进存档               |
| 无 AI | 完全受 Timeline 控制    |
| 无碰撞  | 不影响场景              |
| 可渲染  | 看起来像真实实体           |
| 可选中  | Selection / Gizmo  |
| 可消失  | 跟随 Spawn / Despawn |

### 实现

```java
public class PreviewEntity {
    private final Actor actor;
    private Entity entity;  // Minecraft 实体（客户端投影）
    
    public boolean create(MinecraftClient client) {
        // 创建实体（客户端）
        // 禁用 AI
        // 无重力
        // 添加到世界（仅客户端）
    }
    
    public void destroy(MinecraftClient client) {
        // 从世界中移除
    }
}
```

## 🎛️ ActorRuntimeController 职责

### 新职责（升级后）

```
ActorRuntimeController
 ├─ 管理 Actor → PreviewEntity 映射
 ├─ 在 SpawnClip 出现时创建 PreviewEntity
 ├─ 在 DespawnClip 时移除
 └─ 每帧：Sample → Apply 到 PreviewEntity
```

**不再"寻找世界实体"**  
**只负责"让演员活起来"**

## 🚫 明确不做的事情

既然选择了方案 C，以下内容主动放弃：

- ❌ 直接控制世界里已有的实体（v1 不做）
- ❌ Timeline 直接存 Entity UUID
- ❌ 在世界中生成真实实体作为演员
- ❌ 把时间线和世界状态绑定在一起

这些放弃，会让 Director 长期可维护、可扩展、可导出。

## 📋 实现状态

| 层级                        | 状态    |
| ------------------------- | ----- |
| Timeline / Track / Clip   | ✅ 已完成 |
| EntityClipSampler         | ✅ 已完成 |
| EntityRuntimeController   | ✅ 已完成 |
| **Actor（虚拟演员）**           | ✅ 已完成 |
| **Preview Entity（客户端投影）** | ✅ 已完成 |
| Actor UI（选择 / 管理）         | ⏳ 待实现 |

## 🎯 后续工作

### 优先级1：Actor UI
1. **Actor Picker（创建演员）**
   - Villager / Cow / Zombie / Player / Custom Model
   - 点击一个 → 创建 Actor + EntityTrack + SpawnClip

2. **Actor Panel（属性面板）**
   - Name / Type / Model
   - Add Spawn Clip / Add Path / Add Action
   - Visibility

### 优先级2：Preview Entity 完善
1. **Mixin 支持**
   - 确保实体不会被保存到世界
   - 禁用 AI（通过 Mixin）

2. **渲染优化**
   - 支持 Selection / Gizmo
   - 支持自定义模型

### 优先级3：LookAt 实现
1. **世界坐标点**
2. **实体目标**
3. **摄像机目标**

## ✨ 总结

Director Actor System 已确立：

1. ✅ **Actor 是第一等公民** - 纯数据对象，不依赖世界
2. ✅ **Preview Entity 是投影** - 只存在于客户端，不污染世界
3. ✅ **Timeline 只认识 Actor** - 可复制、可导出、可重放
4. ✅ **架构清晰** - 数据层、采样层、运行时层、投影层分离

这是 Director 的"导演级"设计。🎬

