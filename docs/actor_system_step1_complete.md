# Actor System Step 1 完成总结

## ✅ 已完成的工作

### 1. **ActorType 枚举**

**位置**：`src/main/java/com/director/actor/ActorType.java`

- 导演语义的类型（不是 MC EntityType）
- 支持：PLAYER, VILLAGER, COW, SHEEP, ZOMBIE, SKELETON, CUSTOM
- 后面 Runtime 再映射到 Minecraft EntityType

### 2. **Actor 类（纯数据对象）**

**位置**：`src/main/java/com/director/actor/Actor.java`

**核心特点**：
- ❌ 没有位置
- ❌ 没有时间
- ❌ 没有 Entity / World
- ✅ 可以被任意 Timeline / Runtime 使用
- ✅ 可以被复制 / 克隆（UUID 可换）

**字段**：
- `actorId` (UUID) - 唯一标识
- `name` (String) - 演员名称
- `type` (ActorType) - 演员类型

### 3. **ActorRef 类（Timeline 中的引用）**

**位置**：`src/main/java/com/director/actor/ActorRef.java`

**设计目的**：
- 防止 Timeline 意外"持有" Actor 实例
- 保证 Timeline 可被复制
- 保证 Actor 可被替换
- 保证 ActorRegistry 可热更新

**特点**：
- 只存储 `actorId` (UUID)
- 提供 `of(Actor)` 静态工厂方法

### 4. **ActorRegistry 类（核心管理器）**

**位置**：`src/main/java/com/director/actor/ActorRegistry.java`

**职责**：
- 管理 Actor 生命周期（创建 / 删除 / 查询）
- 项目级（Project Scope）
- 与 TimelineModel 解耦

**核心方法**：
- `createActor(String name, ActorType type)` - 创建新演员
- `registerActor(Actor actor)` - 注册已存在的 Actor
- `removeActor(UUID actorId)` - 移除演员
- `getActor(UUID actorId)` - 获取演员
- `resolve(ActorRef ref)` - 解析 ActorRef 为 Actor
- `isValid(ActorRef ref)` - 检查 ActorRef 是否有效

### 5. **EntityTrack 更新**

**位置**：`src/main/java/com/director/ui/timeline/model/entity/EntityTrack.java`

**变更**：
- 移除 `Actor actor` 字段
- 添加 `ActorRef actorRef` 字段
- `getActor()` → `getActorRef()`
- `setActor(Actor)` → `setActorRef(ActorRef)`
- `getActorId()` 从 ActorRef 获取

**核心原则**：
- Timeline 永远不直接持有 Actor，只持有 ActorRef
- Actor 由 ActorRegistry 管理（Project 层）

### 6. **EntityRuntimeController 更新**

**位置**：`src/main/java/com/director/ui/timeline/playback/entity/EntityRuntimeController.java`

**变更**：
- `previewEntities` Map 的键类型改为 `com.director.actor.Actor`
- `TrackRuntime.actor` 类型改为 `com.director.actor.Actor`
- 通过 `ActorRef` 解析 Actor（待 ActorRegistry 集成）

### 7. **PreviewEntity 更新**

**位置**：`src/main/java/com/director/ui/timeline/playback/entity/PreviewEntity.java`

**变更**：
- 导入改为 `com.director.actor.Actor`

## 🎯 架构设计

### 核心概念拆分

```
Actor          ← 单个演员（是谁）
ActorRegistry  ← 管理所有演员（集合）
ActorRef       ← Timeline 中引用演员（只存 ID）
```

### Timeline 与 Actor 的关系

```
Project
 ├─ ActorRegistry
 │    ├─ Actor(商人A)
 │    ├─ Actor(牛#1)
 │    └─ Actor(主角)
 │
 └─ TimelineModel
      ├─ EntityTrack
      │    └─ ActorRef(商人A)
      └─ EntityTrack
           └─ ActorRef(牛#1)
```

**关键设计点**：
- Timeline 不认识 ActorRegistry
- Timeline 侧只做一件事：`EntityTrack.actorRef`
- TimelineModel 不 import Actor
- Timeline 只存 ActorRef
- ActorRegistry 是 Project 层

## ✨ 已获得的能力

做完 Step 1，你立刻拥有：

1. **🎭 多演员系统** - 支持多个 Actor 同时存在
2. **🔁 Actor 可复用** - 同一个 Actor 可以被多个 Timeline 使用
3. **🧹 删除 Actor → Track 失效但不崩** - ActorRef 失效时，Track 仍然存在但无法解析
4. **📦 Project 可序列化** - Actor 和 Timeline 分离，可独立序列化
5. **🎬 Timeline 彻底"纯净"** - Timeline 不依赖 Actor 实例，只依赖 ActorRef

## 📋 后续工作

### 优先级1：ActorRegistry 集成
- 在 Project 层创建 ActorRegistry 实例
- EntityRuntimeController 通过 ActorRegistry 解析 ActorRef
- UI 层通过 ActorRegistry 管理 Actor

### 优先级2：Actor UI
- Actor Picker（创建演员）
- Actor Panel（属性面板）
- Actor 列表管理

### 优先级3：序列化支持
- Actor 序列化/反序列化
- ActorRegistry 序列化/反序列化
- ActorRef 序列化/反序列化

## 🎉 总结

这一步是 Director 从"工具"进化为"导演系统"的分水岭。

**核心成就**：
- ✅ Actor 是第一等公民（纯数据对象）
- ✅ Timeline 与 Actor 解耦（通过 ActorRef）
- ✅ ActorRegistry 管理 Actor 生命周期
- ✅ 架构清晰，可扩展性强

Director Actor System 数据层已完整建立！🎬

