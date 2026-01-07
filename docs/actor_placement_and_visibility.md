# Actor 在 Minecraft 中的显示与放置机制

## 一、Actor 在 Minecraft 中能看到吗？

### 1.1 显示条件

Actor 在 Minecraft 世界中显示需要满足以下条件：

1. ✅ **ActorRuntimeController 已启用**
   - `ActorRuntimeController.getInstance().setEnabled(true)`
   - 默认已启用

2. ✅ **Timeline 中有 EntityTrack**
   - 创建 Actor 时会自动创建 EntityTrack

3. ✅ **EntityTrack 中有 EntitySpawnClip**
   - 创建 Actor 时会自动创建默认的 `EntitySpawnClip`
   - 默认位置：`(0, 0, 0)`

4. ✅ **当前时间在 SpawnClip 的时间范围内**
   - `EntitySpawnClip.startTime <= currentTime <= EntitySpawnClip.endTime`
   - 如果时间线播放头不在 SpawnClip 范围内，Actor 不会显示

### 1.2 显示流程

```
创建 Actor
    ↓
自动创建 EntityTrack + EntitySpawnClip
    ↓
ActorRuntimeController.update()（每帧调用）
    ↓
EntityClipSampler.sampleAt(currentTime)
    ↓
如果 sample.spawned == true
    ↓
ActorPreviewManager.ensurePreview(actor)
    ↓
PreviewActorEntity 出现在世界中
```

### 1.3 检查 Actor 是否显示

**方法 1：检查时间线播放头位置**
- 确保播放头在 `EntitySpawnClip` 的时间范围内
- 如果不在范围内，Actor 不会显示（因为 `sample.spawned == false`）

**方法 2：检查 ActorRuntimeController 状态**
```java
ActorRuntimeController controller = ActorRuntimeController.getInstance();
boolean enabled = controller.isEnabled(); // 应该为 true
```

**方法 3：检查 PreviewActorEntity**
```java
ActorPreviewManager manager = ActorPreviewManager.getInstance();
PreviewActorEntity entity = manager.getPreview(actorId);
if (entity != null) {
    // Actor 已显示
}
```

---

## 二、如何将 Actor 放到指定位置？

### 2.1 当前机制

**创建 Actor 时的默认位置**：
- 位置：`(0, 0, 0)`（世界原点）
- 朝向：`yaw = 0, pitch = 0`

**调整位置的方法**：

1. **通过 Gizmo 拖动**（推荐）
   - 在世界中点击 Actor（左键）
   - Gizmo 自动激活
   - 拖动 Gizmo 箭头调整位置
   - 系统自动创建/更新 `EntityTransformClip`

2. **通过属性面板编辑**
   - 选中 `EntitySpawnClip` 或 `EntityTransformClip`
   - 在属性面板中直接编辑位置坐标

3. **通过"Focus in World"按钮**
   - 在 Actor 面板中点击 "Focus in World"
   - 系统会自动选中 Actor 并激活 Gizmo

### 2.2 右键放置 Actor（待实现）

**设计目标**：
- 用户右键点击世界中的方块
- 如果当前选中了 Actor，将 Actor 移动到点击位置
- 如果未选中 Actor，创建新的 Actor 并放置到点击位置

**实现方案**：
1. 在 `ActorPickController` 中添加右键点击处理
2. 检测右键点击世界方块
3. 获取点击位置（BlockPos → Vec3d）
4. 创建/更新 `EntitySpawnClip` 或 `EntityTransformClip`

---

## 三、用户交互方式

### 3.1 当前交互方式

| 操作 | 方式 | 功能 |
|------|------|------|
| **选中 Actor** | 左键点击 `PreviewActorEntity` | 选中 Actor，激活 Gizmo |
| **移动 Actor** | 拖动 Gizmo（Translate 模式） | 调整 Actor 位置 |
| **旋转 Actor** | 拖动 Gizmo（Rotate 模式） | 调整 Actor 朝向 |
| **设置 LookAt** | 切换到 LookAt 模式，点击世界点 | 设置 Actor 看向目标 |

### 3.2 右键放置（待实现）

**右键点击世界方块**：
- 如果选中了 Actor：将 Actor 移动到点击位置
- 如果未选中 Actor：创建新 Actor 并放置到点击位置

**实现位置**：
- `ActorPickController` 或新建 `ActorPlacementController`
- 监听右键点击事件
- 检测点击的是方块（不是实体）
- 获取点击位置并创建/更新 Clip

---

## 四、实现右键放置功能

### 4.1 设计原则

1. **不破坏现有架构**
   - 不修改 Actor 的生命周期
   - 不修改 Timeline 的数据结构
   - 只添加新的交互方式

2. **遵循导演工作流**
   - 右键放置 → 创建/更新 `EntitySpawnClip` 或 `EntityTransformClip`
   - 位置由 Timeline 控制，不是"世界实体"

3. **支持撤销/重做**
   - 通过 Command 系统实现
   - 可以撤销放置操作

### 4.2 实现步骤

1. **创建 ActorPlacementController**
   - 监听右键点击事件
   - 检测点击位置
   - 创建/更新 Clip

2. **集成到 ChronoBlocksScreen**
   - 在 `tick()` 中调用 `ActorPlacementController.update()`

3. **UI 提示**
   - 在 Actor 面板中显示"右键点击世界放置 Actor"的提示

---

## 五、常见问题

### Q1: 创建了 Actor，但在世界中看不到？

**可能原因**：
1. 时间线播放头不在 `EntitySpawnClip` 的时间范围内
2. `ActorRuntimeController` 未启用
3. `EntitySpawnClip` 的 `visibleOnSpawn` 为 `false`

**解决方法**：
1. 检查时间线播放头位置
2. 确保 `ActorRuntimeController.getInstance().isEnabled() == true`
3. 检查 `EntitySpawnClip` 的属性

### Q2: Actor 显示在错误的位置？

**可能原因**：
1. `EntitySpawnClip` 的位置是 `(0, 0, 0)`
2. 有多个 `EntityTransformClip` 叠加

**解决方法**：
1. 选中 `EntitySpawnClip`，在属性面板中调整位置
2. 或使用 Gizmo 拖动调整

### Q3: 如何快速将 Actor 放到摄像机位置？

**当前方法**：
1. 使用 Gizmo 拖动到摄像机位置
2. 或手动输入摄像机坐标

**未来改进**：
- 添加"放置到摄像机位置"按钮
- 右键点击时自动使用摄像机位置

---

## 六、总结

**当前状态**：
- ✅ Actor 可以在 Minecraft 中显示（通过 `PreviewActorEntity`）
- ✅ 可以通过 Gizmo 调整位置
- ❌ 还没有右键放置功能

**推荐工作流**：
1. 创建 Actor（默认位置 `(0, 0, 0)`）
2. 在世界中点击 Actor（左键）选中它
3. 拖动 Gizmo 调整到目标位置
4. 或选中 `EntitySpawnClip`，在属性面板中直接输入坐标

**未来改进**：
- 实现右键放置功能
- 添加"放置到摄像机位置"快捷按钮
- 支持拖拽放置（按住右键拖动）

