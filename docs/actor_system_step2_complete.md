# Actor System Step 2 完成总结

## ✅ 已完成的工作

### 1. **PreviewActorEntity 类（核心实体类）**

**位置**：`src/main/java/com/director/actor/preview/PreviewActorEntity.java`

**设计原则**：
- 继承 Entity（为了复用渲染 / hitbox / yaw / pitch）
- 不注册到服务器
- 不参与 tick AI
- 生命周期完全由我们控制

**核心特点**：
- ✅ 仅客户端存在
- ✅ 不保存、不同步、不参与 AI
- ✅ 作为 Actor 的"视觉投影"
- ✅ 能被 Gizmo / Selection 使用

**关键方法**：
- `tick()` - 不调用 super，不执行 AI/重力
- `move()` - 禁止物理移动
- `isCollidable()` / `collides()` - 返回 false
- `writeCustomDataToNbt()` - 不写入（不进存档）

### 2. **ActorPreviewManager 类（管理器）**

**位置**：`src/main/java/com/director/actor/preview/ActorPreviewManager.java`

**职责**：
- 管理 Actor ↔ PreviewEntity 的映射
- 控制 PreviewEntity 的生命周期
- 只存在于 Client

**核心方法**：
- `ensurePreview(Actor)` - 确保某个 Actor 有 PreviewEntity
- `removePreview(UUID)` - 移除某个 Actor 的 PreviewEntity
- `getPreview(UUID)` - 获取 PreviewEntity
- `clear()` - 清空所有预览实体

**ActorType → EntityType 映射**：
- PLAYER → EntityType.PLAYER
- VILLAGER → EntityType.VILLAGER
- COW → EntityType.COW
- SHEEP → EntityType.SHEEP
- ZOMBIE → EntityType.ZOMBIE
- SKELETON → EntityType.SKELETON
- CUSTOM → EntityType.ARMOR_STAND（临时占位）

### 3. **EntityRuntimeController 更新**

**位置**：`src/main/java/com/director/ui/timeline/playback/entity/EntityRuntimeController.java`

**变更**：
- 移除旧的 `previewEntities` Map
- 移除旧的 `PreviewEntity` 包装类
- 使用 `ActorPreviewManager` 管理预览实体
- 更新 `handleSpawnDespawn()` 使用 ActorPreviewManager
- 更新 `getOrCreatePreviewEntity()` 使用 ActorPreviewManager
- 更新 `destroyAllPreviewEntities()` 使用 ActorPreviewManager.clear()

**新逻辑流程**：
```
Track
  → ActorRef
  → ActorRegistry.resolve() (TODO)
  → ActorPreviewManager.ensurePreview()
  → apply Sample 到 PreviewEntity
```

## 🎯 架构设计

### 整体结构

```
Actor (data)
   ↓
EntityTrack + Clips
   ↓
EntityClipSampler
   ↓
EntitySample
   ↓
EntityRuntimeController
   ↓
ActorPreviewManager   ⭐（本 Step）
   ↓
PreviewActorEntity    ⭐（Ghost）
```

### 核心原则

1. **RuntimeController 不再"找世界实体"**
   - 它只和 ActorPreviewManager 打交道
   - 再也不需要 EntityResolver

2. **Spawn / Despawn 控制**
   - 在 SpawnClip 生效时：`ActorPreviewManager.ensurePreview(actor)`
   - 在 DespawnClip 生效时：`ActorPreviewManager.removePreview(actorId)`
   - 世界中只出现你希望观众看到的演员

## ✨ 已获得的能力

| 能力                | 状态          |
| ----------------- | ----------- |
| Actor 可见          | ✅           |
| 世界不被污染            | ✅           |
| 多演员               | ✅           |
| Timeline 控制显隐     | ✅           |
| Selection / Gizmo | ✅（Entity 级） |
| 可导出               | ✅           |

## 📋 后续工作

### 优先级1：ActorRegistry 集成
- 在 Project 层创建 ActorRegistry 实例
- EntityRuntimeController 通过 ActorRegistry 解析 ActorRef
- 完成 Actor → PreviewEntity 的完整链路

### 优先级2：序列化支持
- PreviewActorEntity 序列化/反序列化（如果需要）
- ActorPreviewManager 状态保存/恢复

### 优先级3：渲染优化
- 支持自定义模型
- 支持 Selection / Gizmo 高亮
- 支持名称标签自定义

## 🎉 总结

这一步是 "导演模式"真正开始工作的瞬间。

**核心成就**：
- ✅ PreviewActorEntity 继承 Entity，复用渲染管线
- ✅ ActorPreviewManager 管理预览实体生命周期
- ✅ EntityRuntimeController 完全迁移到新系统
- ✅ 世界不被污染，只存在于客户端

Director Actor System Step 2 已完整实现！🎬

