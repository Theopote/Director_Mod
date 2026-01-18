# Actor Gizmo 拖动时重复创建 TransformClip 问题分析

## 问题描述

**用户报告**：
- 新建一个演员，放置在 Minecraft 中
- 时间线上生成了这个演员对应的片段（`EntitySpawnClip`）
- 但是拖动 Gizmo 的时候，又会拖出一个同一个演员的另一个片段（`EntityTransformClip`）

## 问题根源分析

### 当前逻辑

1. **放置演员时**（`ActorPlacementController.placeActorAt()`）：
   - 创建 `EntitySpawnClip`（默认时长 5 秒）
   - 设置位置和朝向
   - **不创建** `EntityTransformClip`

2. **拖动 Gizmo 时**（`ActorGizmoController.handleGizmoDragStart()`）：
   - 查找当前时间覆盖的 `EntityTransformClip`（`findActiveTransformClip()`）
   - 如果找不到，创建新的 `EntityTransformClip`

### 问题所在

**问题**：`findActiveTransformClip()` 只查找 `EntityTransformClip`，不查找 `EntitySpawnClip`。所以：
- 放置演员后，时间线上只有 `EntitySpawnClip`
- 拖动 Gizmo 时，找不到任何 `EntityTransformClip`
- 因此每次拖动都会创建新的 `EntityTransformClip`

**但是，这看起来是正常的逻辑**：
- `SpawnClip` 用于"生成"演员（设置初始位置）
- `TransformClip` 用于"变换"演员（动画位置变化）
- 两者应该是分离的

### 可能的问题场景

1. **播放头位置问题**：
   - 如果播放头不在 SpawnClip 的时间范围内（比如在 clip 之后）
   - `findActiveTransformClip()` 找不到任何 clip（正确）
   - 创建新 TransformClip（可能合理）

2. **时间范围重叠问题**：
   - 如果播放头在 SpawnClip 的时间范围内（比如 0-5 秒）
   - `findActiveTransformClip()` 找不到 TransformClip（正确，因为只有 SpawnClip）
   - 创建新 TransformClip（可能合理，因为 TransformClip 覆盖 SpawnClip）

3. **重复创建问题**：
   - 如果用户在同一时间多次拖动，每次都会创建新的 TransformClip
   - 这可能不是期望的行为

## 解决方案

### 方案 1：扩展或重用现有 TransformClip（推荐）

如果当前时间已经有一个 TransformClip（即使是刚刚创建的），应该重用它，而不是创建新的。

**修改逻辑**：
```java
// 2️⃣ 找到当前时间覆盖的 TransformClip（last-wins）
editingClip = findActiveTransformClip(entityTrack, time);

// 3️⃣ 如果没有，则自动创建一个
if (editingClip == null) {
    editingClip = createTransformClip(entityTrack, time, sampledPos);
    LOGGER.debug("[ActorGizmoController] 创建新的 TransformClip: start={}, end={}",
            editingClip.getStartTime(), editingClip.getEndTime());
} else {
    // ⭐ 如果找到了现有 clip，确保其时间范围包含当前时间
    // 如果需要，扩展 clip 的结束时间
    if (time > editingClip.getEndTime()) {
        editingClip.setEndTime(time + DEFAULT_CLIP_DURATION);
        LOGGER.debug("[ActorGizmoController] 扩展 TransformClip 结束时间: {}", editingClip.getEndTime());
    }
}
```

### 方案 2：检查是否已经有 TransformClip（避免重复创建）

在创建之前，检查是否已经有任何 TransformClip（即使不在当前时间范围内）。

**修改逻辑**：
```java
// 2️⃣ 找到当前时间覆盖的 TransformClip（last-wins）
editingClip = findActiveTransformClip(entityTrack, time);

// 3️⃣ 如果没有，检查是否已经有 TransformClip（避免重复创建）
if (editingClip == null) {
    // ⭐ 检查是否已经存在 TransformClip
    List<EntityClip> clips = entityTrack.getClips();
    boolean hasTransformClip = clips.stream()
            .anyMatch(c -> c instanceof EntityTransformClip);
    
    if (hasTransformClip) {
        // 如果已有 TransformClip，找到最近的并扩展它
        editingClip = findNearestTransformClip(entityTrack, time);
        if (editingClip != null) {
            // 扩展时间范围
            if (time < editingClip.getStartTime()) {
                editingClip.setStartTime(time);
            }
            if (time > editingClip.getEndTime()) {
                editingClip.setEndTime(time + DEFAULT_CLIP_DURATION);
            }
            LOGGER.debug("[ActorGizmoController] 重用并扩展 TransformClip: start={}, end={}",
                    editingClip.getStartTime(), editingClip.getEndTime());
        }
    }
    
    // 如果还是没有，创建新的
    if (editingClip == null) {
        editingClip = createTransformClip(entityTrack, time, sampledPos);
        LOGGER.debug("[ActorGizmoController] 创建新的 TransformClip: start={}, end={}",
                editingClip.getStartTime(), editingClip.getEndTime());
    }
}
```

### 方案 3：合并相邻的 TransformClip

如果创建的 TransformClip 与现有的相邻，可以合并它们。

**这个方案比较复杂，可能不是必需的。**

## 推荐的修复方案

**方案 1 最简单，最符合当前设计**：

1. 如果在当前时间找到 TransformClip，重用它
2. 如果时间超出 clip 范围，扩展 clip 的时间范围
3. 只有在完全没有 TransformClip 时才创建新的

这样可以避免在同一时间创建多个 TransformClip。
