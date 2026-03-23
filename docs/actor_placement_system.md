# Actor 放置系统文档

## 一、系统概述

Actor 放置系统实现了直观的 Actor 创建和放置流程，让用户可以通过鼠标预览和右键点击来放置 Actor。

### 核心功能

1. **鼠标附着预览**：创建 Actor 时立即在鼠标位置显示预览实体
2. **右键放置**：右键点击世界中的位置来放置 Actor
3. **自动创建轨道和片段**：放置时自动创建 EntityTrack 和 EntitySpawnClip
4. **片段长度可调整**：通过属性面板调整 Actor 的生命周期

---

## 二、工作流程

### 2.1 创建 Actor

```
用户在 UI 中点击"增加演员"
        ↓
ActorPlacementController.startPlacement()
        ↓
创建临时 Actor 和 PreviewEntity
        ↓
PreviewEntity 附着到鼠标（跟随摄像机视线）
        ↓
等待用户操作（右键放置 或 ESC 取消）
```

### 2.2 放置 Actor

```
用户右键点击世界中的方块
        ↓
ActorPlacementController.placeActorAt()
        ↓
1. 正式创建 Actor（保存到 Registry）
2. 创建 EntityTrack
3. 创建 TimelineTrack
4. 创建 EntitySpawnClip（默认 5 秒）
5. 设置位置和朝向
        ↓
清理临时预览实体
        ↓
完成
```

### 2.3 取消放置

```
用户按 ESC 键
        ↓
ActorPlacementController.cancelPlacement()
        ↓
清理临时 Actor 和 PreviewEntity
        ↓
取消完成（不创建任何轨道或片段）
```

---

## 三、实现细节

### 3.1 ActorPlacementController

**位置**：`com.director.ui.actor.placement.ActorPlacementController`

**职责**：
- ✅ 管理放置状态（`isPlacing`）
- ✅ 管理临时 Actor 和 PreviewEntity
- ✅ 更新预览实体位置（跟随鼠标/摄像机）
- ✅ 监听右键点击和 ESC 键
- ✅ 创建 EntityTrack 和 EntitySpawnClip

**关键方法**：
```java
// 开始放置
public boolean startPlacement(String actorName, ActorType actorType)

// 取消放置
public void cancelPlacement()

// 更新（每帧调用）
public void update()

// 检查是否正在放置
public boolean isPlacing()
```

### 3.2 预览实体位置更新

预览实体位置计算：
```java
// 获取摄像机位置和方向
Vec3d cameraPos = cameraSample.position();
Vec3d cameraDir = getCameraDirection(cameraSample.yaw(), cameraSample.pitch());

// 将预览实体放在摄像机前方 3 格处
Vec3d previewPos = cameraPos.add(cameraDir.multiply(3.0));

// 确保在地面上方
double groundY = world.getTopY(...);
if (previewPos.y < groundY + 1.0) {
    previewPos = new Vec3d(previewPos.x, groundY + 1.0, previewPos.z);
}
```

### 3.3 放置位置计算

放置位置来自右键点击的方块命中结果：
```java
BlockHitResult blockHit = (BlockHitResult) hit;
Vec3d placePos = blockHit.getPos(); // 方块表面的位置
```

### 3.4 默认片段长度

默认片段长度为 **5.0 秒**，可通过属性面板调整。

---

## 四、UI 集成

### 4.1 ActorProjectPropertyAdapter

**修改**：创建 Actor 时不再直接创建轨道，而是启动放置模式：

```java
if (ImGui.button("Create")) {
    String name = newActorName.get();
    if (name != null && !name.isBlank()) {
        ActorPlacementController placementController =
                ActorPlacementController.getInstance();
        
        if (placementController.startPlacement(name, newActorType)) {
            // 开始放置模式
        }
    }
}
```

### 4.2 DirectorScreen

**修改**：每帧调用 `ActorPlacementController.update()`：

```java
// ⭐ Actor 放置系统：每帧更新（鼠标附着预览和右键放置）
com.director.ui.actor.placement.ActorPlacementController.getInstance().update();
```

### 4.3 EntitySpawnClipPropertyAdapter

**增强**：添加了片段时长编辑功能：

```java
// 可编辑的结束时间和时长
ImFloat endTimeFloat = new ImFloat(clip.getEndTime());
if (ImGui.inputFloat("结束时间 (秒)", endTimeFloat)) {
    float newEndTime = Math.max(clip.getStartTime() + 0.1f, endTimeFloat.get());
    clip.setTimeRange(clip.getStartTime(), newEndTime);
    changed.set(true);
}

ImFloat durationFloat = new ImFloat(clip.getLength());
if (ImGui.inputFloat("时长 (秒)", durationFloat)) {
    float newDuration = Math.max(0.1f, durationFloat.get());
    clip.setTimeRange(clip.getStartTime(), clip.getStartTime() + newDuration);
    changed.set(true);
}
```

---

## 五、片段和关键帧

### 5.1 片段（Clip）

**每个 Actor 有自己的片段**：
- 片段长度 = Actor 在 Minecraft 中的生命周期
- 片段起点 = Actor 开始动作的时间
- 多个 Actor 可以共用一个轨道，但为了避免重叠可以创建多个轨道

**片段类型**：
- `EntitySpawnClip` - 出现/绑定片段（放置时自动创建）
- `EntityTransformClip` - 位移/旋转片段
- `EntityAnimationClip` - 动画片段
- `EntityActionClip` - 行为片段
- `EntityStateClip` - 状态片段
- `EntityLookAtClip` - 看向片段
- `EntityDespawnClip` - 离场片段

### 5.2 关键帧（Keyframe）

**片段上可以设置关键帧作为事件节点**：
- 位置关键帧
- 旋转关键帧
- 动作关键帧
- 其他属性关键帧

**关键帧属性适配器**：
- `EntityKeyframePropertyAdapter` - 已注册，支持编辑关键帧属性

### 5.3 属性面板

**选中片段时**：
- 显示对应的片段属性适配器（如 `EntitySpawnClipPropertyAdapter`）
- 可以编辑片段的时间范围、位置、旋转等属性

**选中关键帧时**：
- 显示关键帧属性适配器（`EntityKeyframePropertyAdapter`）
- 可以编辑关键帧的位置、旋转、动作等属性

---

## 六、设计原则

### 6.1 原子性操作

放置 Actor 是一个原子性操作：
- 要么成功（创建 Actor、Track、Clip）
- 要么失败（取消，不创建任何内容）

### 6.2 不破坏现有架构

- 不修改 Actor 的生命周期
- 不修改 TimelineModel 的结构
- 完全复用现有的 EntityTrack 和 EntityClip 系统

### 6.3 用户友好

- 鼠标预览让用户知道 Actor 会放在哪里
- ESC 键可以随时取消
- 右键点击直观地放置 Actor

---

## 七、使用示例

### 7.1 创建并放置 Actor

1. 在 Actor 面板中点击"增加演员"
2. 输入 Actor 名称和类型
3. 点击"Create"
4. 鼠标位置会出现 Actor 预览
5. 右键点击世界中的位置来放置 Actor
6. Actor 被创建，轨道和片段自动生成

### 7.2 调整片段长度

1. 选中 EntitySpawnClip
2. 在属性面板中找到"时长 (秒)"
3. 修改时长值
4. 片段结束时间自动更新

### 7.3 添加关键帧

1. 在时间线上选中 EntityTrack
2. 在片段上添加关键帧
3. 选中关键帧
4. 在属性面板中编辑关键帧属性

---

## 八、未来扩展

### 8.1 多 Actor 放置

- 支持连续放置多个 Actor（不取消放置模式）
- 支持批量放置（选择多个位置）

### 8.2 放置预览增强

- 显示放置位置的网格线
- 显示 Actor 的朝向指示器
- 支持旋转预览（鼠标滚轮）

### 8.3 智能放置

- 自动对齐到网格
- 自动检测地面高度
- 自动避免重叠

---

## 九、技术细节

### 9.1 临时 Actor 管理

临时 Actor 不保存到 Registry，只在放置过程中使用：
```java
// 创建临时 Actor（不保存到 Registry，只是预览）
pendingActor = new Actor(actorName.trim(), actorType);

// 放置时正式创建
Actor actor = actorRegistry.createActor(pendingActor.getName(), pendingActor.getType());
```

### 9.2 PreviewEntity 生命周期

PreviewEntity 的生命周期完全由 ActorPlacementController 控制：
```java
// 创建
previewEntity = new PreviewActorEntity(entityType, world);
world.addEntity(previewEntity);

// 清理
previewEntity.discard();
previewEntity = null;
```

### 9.3 摄像机采样

使用 `CameraAccessor.sampleFromGameCamera()` 获取当前摄像机状态：
```java
var cameraSample = CameraAccessor.sampleFromGameCamera(client);
Vec3d cameraPos = cameraSample.position();
float yaw = cameraSample.yaw();
float pitch = cameraSample.pitch();
```

---

## 十、总结

Actor 放置系统实现了直观、友好的 Actor 创建和放置流程：

- ✅ 鼠标预览让用户知道 Actor 会放在哪里
- ✅ 右键点击直观地放置 Actor
- ✅ 自动创建轨道和片段
- ✅ 片段长度可调整
- ✅ 支持关键帧编辑
- ✅ 完全复用现有架构

这是一个完整的、生产就绪的 Actor 放置系统。

