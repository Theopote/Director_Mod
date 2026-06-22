# 阶段 A：包重组规划（从 `ui.timeline.*` 迁出领域层）

目标：消除 `core/`、`actor/`、`mixin/`、`export/` 对 `com.director.ui.timeline.*` 的反向依赖，让 `ui` 仅承担渲染与交互。

---

## 现状诊断（关键事实）

- `TimelineModel`/`TimelineTrack`/`EntitySample` 等“领域/播放”类型物理路径在 `com.director.ui.timeline.*`，导致非 UI 包依赖它们时形成“分层违规”。
- 目前最重耦合点是 `actor.runtime.ActorRuntimeController`，直接持有 `TimelineModel` 并依赖 `playback.entity.*` 的采样数据与采样器。

---

## 目标包结构（建议）

### 1) 时间线领域（Domain）

- `com.director.timeline.model.*`
  - `TimelineModel`、`TimelineTrack`、`TimelineClip`、`TrackType` 等
  - `model.entity.*`、`model.audio.*`、`model.camera.*`、`model.weather.*`…

### 2) 播放/采样（Playback）

- `com.director.timeline.playback.*`
  - `playback.entity.*`（采样器/采样数据/运行时缓存）
  - `playback.camera.*`、`playback.time.*`、`playback.weather.*`、`playback.season.*`…

### 3) 持久化（Persistence）

- `com.director.timeline.persistence.*`
  - `TimelineModelPersistence` 等

### 4) UI 层（仅渲染与交互）

- `com.director.ui.timeline.*`
  - `render/*`、`interaction/*`、`dialog/*`、`menu/*`、`ui/*`、`shortcuts/*`

---

## 执行策略（避免“大爆炸重命名”）

采用 **分批迁移（batch relocation）**：

1. **先迁纯播放数据/采样器（最少依赖）**，让 `actor/` 与 `mixin/` 立刻解除对 `ui.timeline.playback.entity` 的反向依赖。
2. 再迁 `playback.*` 其他子域（weather/time/season/camera）。
3. 然后迁 `timeline.model.*`（依赖面最广，放最后）。
4. 最后把 `ui.timeline.*` 中遗留的“领域逻辑”剥离/下沉。

每一批都要求：

- **移动文件路径（git mv）**
- **改 package 声明**
- **全项目替换 import/FQN**
- `./gradlew compileJava` 通过

---

## Batch 1（已完成）：`playback.entity` 迁出 `ui`

### 迁移前

- `com.director.ui.timeline.playback.entity.*`

### 迁移后

- `com.director.timeline.playback.entity.*`

### 涉及文件

- `ActorTrackSampler`
- `EntityClipSampler`
- `TimelineActorController`
- `EntityActionEvent`
- `EntitySample`
- `PreviewEntity`
- `ActionTriggerCache`
- `AnimationEventTriggerCache`

工具脚本：

- `tools/relocate_playback_entity.py`：修复 package 声明 + 全项目替换 import/FQN

---

## Batch 2（建议下一步）：`playback.weather/time/season/camera`

目标：把 mixin / season / client / export 的 `ui.timeline.playback.*` 依赖迁出。

候选包：

- `com.director.timeline.playback.weather.*`
- `com.director.timeline.playback.time.*`
- `com.director.timeline.playback.season.*`
- `com.director.timeline.playback.camera.*`

典型受影响文件（示例）：

- `mixin/*`：`TimelineWeatherController`、`TimelineWorldController`、`TimelineSeasonController`、`CameraRuntime`…
- `season/*`：`SeasonSample`
- `export/video/*`：`TimelineCameraController`（如用于离线渲染）

### 迁移状态

- ✅ 已完成：`playback.weather`
- ✅ 已完成：`playback.time`
- ✅ 已完成：`playback.season`
- ✅ 已完成：`playback.camera`

---

## Batch 3：`timeline.model.*`（最大批次）

迁移建议：

- `com.director.ui.timeline.model.*` → `com.director.timeline.model.*`
- 同步迁移子包：`model.entity/*`、`model.camera/*`、`model.audio/*`、`model.weather/*`…

### 3.1 当前进度（已开始）

- ✅ `TrackType` → `com.director.timeline.model.TrackType`
- ✅ `TimelineInterpolation` → `com.director.timeline.model.TimelineInterpolation`
- ✅ `ITimelineProject` → `com.director.timeline.model.ITimelineProject`
- ✅ `TimelineSelectable` → `com.director.timeline.model.TimelineSelectable`
- ✅ `TimelineClip` → `com.director.timeline.model.TimelineClip`
- ✅ `TimelineTrack` → `com.director.timeline.model.TimelineTrack`

说明：以上六项迁移后已完成全项目 import/FQN 更新并通过编译。`TimelineClip.setParentTrack` 改为 `public`，以便跨包由 `TimelineTrack` 维护父引用。

工具脚本：

- `tools/relocate_model_clip_selectable.py`：`TimelineSelectable` / `TimelineClip`
- `tools/relocate_model_timelinetrack.py`：`TimelineTrack`

批次拆分建议：

- 3.1 先迁 `TrackType`、`TimelineInterpolation`、轻量 enum/value、`TimelineSelectable`、`TimelineClip` ✅
- 3.2 再迁 `TimelineTrack` ✅
- 3.3 最后迁 `TimelineModel`（最中心）及子包

---

## Batch 4：跨域适配与端口（可并行推进）

### Actor 运行时端口（降低未来迁移成本）

在 `actor` 或 `timeline` 定义窄接口：

- `ActorTimelineSource`（提供 `ActorTrack`/采样输出的视图）
- UI/编辑器侧实现/转换

这样后续即使 timeline model 再演进，也不需要 `ActorRuntimeController` 直接持有 `TimelineModel`。

---

## 验收标准

完成阶段 A 的最终目标（可分阶段验收）：

1. `core/`、`actor/`、`mixin/`、`export/` **不再 import `com.director.ui.timeline.model.*` 或 `com.director.ui.timeline.playback.*`**
2. `ui.timeline.*` 仅保留 ImGui/交互/渲染/命令 UI glue，不承载领域模型
3. 全量编译通过：`./gradlew compileJava`

