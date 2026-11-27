# Director 容器与容器片段代码审查报告

- 审查日期：2026-11-26
- 主工作区基线：`main` 分支，提交 `0c984fafd952cecbc280f0d957106d9e4b208ece`
- 容器验证基线：提交 `ee2f39c87bbb1f4409eadfaf35fcffaea50144c2`
- 审查方式：源码调用链审查、跨文件一致性检查、全量单元测试、临时缺陷特征化探针
- 产品代码修改：无
- 提交与推送：无
- 审查范围：容器模型与管理器、容器树拖放、容器片段与轨道约束、属性编辑、命令与撤销重做、剪贴板与音频联动、采样播放、整容器/逐块动画、客户端渲染、服务端世界写入、持久化、缓存与测试

## 1. 结论摘要

容器功能的主链路已经建立：容器通过 `boundContainerId` 与时间线片段绑定，播放时区分渲染方块集合与真实世界方块集合，整容器和逐块动画共用时间线采样入口，服务端世界写入也有分帧预算。`WholeContainerAnimationEditSession` 的连续编辑快照、逐块可见性缓存、可见性服务预算等设计值得保留。

但当前实现仍在编辑事务、状态生命周期、存档原子性、跨世界隔离和播放正确性方面存在明显缺口。本次共整理 **30 项问题**：

- **P1：9 项**。包括拖放无法撤销、拖放非原子、锁定语义失效、撤销状态分裂、时间线加载非原子、拆除片段前消失、旧视觉覆盖遗留、整容器渲染视图变换错误、移动/缩放丢失关键帧。
- **P2：17 项**。涉及普通属性编辑无撤销、active 残留、容器命令快照不完整、持久化字段丢失、轨道不变量、音频复制、淡化时间基准、跨维度污染、导出副作用、性能、异常隔离、可见性重试、命令回滚和数值边界。
- **P3：4 项**。主要是复制语义、可变 API、层级循环防护和边界查询语义。

建议优先建立“拖放/命令/存档”的原子性边界，再修复播放与渲染正确性，最后处理大规模方块性能和 API 封装债务。

> 重要验证限制：当前主工作区提交 `0c984faf` 的 `ActorLookAtEditSession.java:61` 已存在“缺少返回语句”编译错误。该问题不属于容器范围，本轮未修改。全量测试因此在容器相关代码相同的隔离基线 `ee2f39c8` 中执行。

## 2. 验证结果

### 2.1 全量测试

在 detached 的隔离工作树 `ee2f39c8` 中，通过临时 Gradle init script 将探针加入测试源集后执行全量 `test`：

- `BUILD SUCCESSFUL`
- 253 个测试类
- 1529 项测试
- 0 failures
- 0 errors
- 1 项 skipped

测试通过只说明现有回归基线可运行，**不代表本报告中的缺陷已被覆盖**。

### 2.2 临时特征化探针

共 11 项探针，全部通过。这里的“通过”表示成功复现了当前缺陷行为，而不是确认功能正确。

| 探针 | 实际复现结果 |
| --- | --- |
| `movingClipPrunesGlobalKeyframesAndUndoDoesNotRestoreThem` | 移动片段会删除范围外全局 `ClipKeyframe`，撤销只恢复时间，不恢复关键帧 |
| `clearLeavesActiveReferenceToRemovedContainer` | `ContainerManager.clear()` 后 `getActive()` 仍返回已移除容器 |
| `lockedContainerCanStillBeRenamedAndBatchDeleted` | 锁定容器仍可重命名，并可经批量删除路径移除 |
| `undoOfDeletedBoundClipCanRestoreAudioWithoutRestoringClip` | 同容器被新片段占用时，撤销旧片段删除后音频恢复，但旧片段未回到轨道 |
| `nbtRoundTripDropsMetadataAndAnimationPreset` | NBT 往返丢失 metadata 与默认动画预设 |
| `nonFiniteValuesAreAcceptedByClipAndVisibilityModels` | NaN 片段时间、Infinity 结束时间、NaN 可见性关键帧均可进入模型 |
| `shallowDuplicateDropsVisibilityKeyframes` | `duplicateShallow()` 丢失可见性关键帧 |
| `copyingBoundAudioClipClearsContainerBinding` | 复制/剪切绑定容器的音频片段会清除容器绑定 |
| `changingTrackTypeKeepsIncompatibleBoundClip` | 将 CONTAINER 轨道改成其他类型后，已绑定容器片段仍保留在轨道上 |
| `demolitionBeforeStartHasVisibleBlocksButZeroOpacity` | 逐块拆除片段开始前，可见集合为全量，但 `computeAlphaAtGlobalTime()` 返回 0 |
| `malformedTimelineDtoLeavesPartiallyReplacedModel` | 非法轨道导致加载抛错后，旧轨已删除，合法轨已部分恢复 |

探针源、init script、隔离工作树和测试产物均在交付前清理，未加入产品源码。

### 2.3 当前提交编译阻塞

主工作区 `0c984faf` 编译时报告：

```text
src/main/java/com/director/ui/actor/lookat/ActorLookAtEditSession.java:61: 错误: 缺少返回语句
```

对应 `resolveSpawn()` 在 `spawn == null` 时没有返回语句。该文件未在本轮修改。

### 2.4 未进行的验证

- 未启动 Minecraft 客户端，未进行 GPU 渲染、Mixin、ImGui 和多世界切换实机验证。
- 未进行超大方块容器的帧时间基准测试。
- 未进行区块长期不可用、服务端可见性任务饥饿和并发编辑压力测试。
- 未验证视频导出与实际播放同时运行时的全部副作用。

因此，客户端渲染、跨维度、性能和运行时异常问题分别标为“条件性”或“需实机确认”。

## 3. 架构与调用链

### 3.1 容器与片段不是两个独立领域对象

- 领域对象：`com.director.core.container.Container`
- 时间线片段：通用 `com.director.timeline.model.TimelineClip`
- 绑定关系：`boundContainerId` / `boundContainerName`
- 动画配置：片段内的 `ClipAnimationConfig`
- 轨道类型：普通容器片段使用 `TrackType.CONTAINER`
- 动画 scope：`WHOLE_CONTAINER` 或逐块序列

容器保存方块坐标、原始 `BlockStore` 快照、层级、状态、锁定、实时接收、metadata、维度、默认动画预设等；片段保存时间范围、绑定、可见性关键帧、动画配置和淡化参数。

### 3.2 拖放链

```text
ContainerTreeInteractionHandler
  -> DIRECTOR_CONTAINER Transferable（支持批量 UUID）
  -> TimelineDragAndDropHandler
  -> prepareContainerForContainerTrack()
  -> TimelineClip / 已有 TimelineClip
  -> TimelineTrack.addClip()
  -> ContainerTimelineService / TimelineAudioService
```

### 3.3 播放、渲染与世界写入链

```text
GameRendererMixin
  -> UIPlaybackBridge
  -> PlaybackOrchestrator
  -> TimelinePlaybackSampler / TimelineClipBlockController
  -> WholeContainerAnimation / PerBlockSchedule / BlockAnimationEngine
  -> BlockVisualStateManager
  -> DirectorRendererRegistry / BlockAnimationRenderer / BlockRenderManagerMixin

TimelineClipBlockController
  -> ContainerVisibilityService
  -> ServerWorld.setBlockState(AIR / 原 BlockState / BlockEntity NBT)
```

动画期间渲染集合和真实世界集合分离，结束后进行世界交接。容器主链不使用 `BlockDisplayEntity`；该类主要属于演员自定义模型预览。

### 3.4 持久化

- 容器：gzip NBT + `AtomicFileWriter`
- 时间线：Gson DTO，schema v2
- 容器删除会联动删除当前时间线中的容器片段
- 容器重命名会联动片段和音频显示名

## 4. 优先级与证据说明

- **P1**：应尽快修复，可能导致核心播放错误、状态分裂、存档/关键帧丢失或错误世界表现。
- **P2**：应在后续版本修复，涉及常见功能错误、特定条件风险、性能或维护性问题。
- **P3**：API 封装和语义债务，当前没有确认的必现用户路径。
- **已复现**：临时探针直接观察到当前结果。
- **强代码证据**：调用链和边界条件明确，但未实机复现。
- **条件性**：需要特定模型、维度、配置或运行时状态才会触发。
- **需实机确认**：代码证据明确，但 GPU、客户端 Mixin 或服务端长期行为尚未验证。

## 5. P1 问题

### C-01 容器拖放绕过命令历史、轨道锁和播放状态门控

**优先级**：P1  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:90-97`
- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:151-209`
- `src/main/java/com/director/ui/container/ContainerTreeInteractionHandler.java:119-160`
- `src/main/java/com/director/timeline/layout/TimelineClipLayoutResolver.java:92-116`

**问题**

`checkTrackValidity()` 只检查轨道类型是否接受容器载荷，不检查轨道锁定、模型播放中或 scrubbing。创建、移动和 ripple 都直接修改模型，没有进入 `TimelineCommandHistory`。

ripple 通过 `offsetTime()` 移动其他片段，但没有调用 `TimelineAudioService.onClipTimeRangeChanged()`，被挤开的音频事件可能与片段失去同步。容器树拖放源也没有统一拒绝锁定容器或组容器，组容器直到时间线 handler 中才被拒绝。

**影响**

- 拖入、移动和 ripple 无法撤销/重做。
- 锁定轨道仍可能被修改。
- 播放中拖放可能让同一帧采样看到前后不一致的状态。
- ripple 后音频时间轴可能错位。
- 批量拖放没有整批历史边界。

**修复方向**

新增拖放命令或复合命令，完整捕获片段、轨道、ripple、音频事件和容器副作用；在入口统一检查轨道锁、容器锁、播放和 scrubbing；ripple 后逐个同步音频；一次拖放生成一个可撤销单元。

**回归验证**

覆盖创建、同轨移动、跨轨移动、ripple、批量拖放的 undo/redo；锁定轨道、锁定容器、播放中和 scrubbing 时应拒绝或无副作用。

### C-02 容器拖放非原子，且忽略 `addClip()` 失败

**优先级**：P1  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:151-153`
- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:193-200`
- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:257-275`
- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:278-295`

**问题**

`prepareContainerForContainerTrack()` 会先关闭 intake、准备可见性快照、设置 HIDDEN，之后才执行片段添加。新建片段时忽略 `targetTrack.addClip(clip)` 的布尔结果；跨轨移动时先从旧轨移除，再忽略目标轨添加结果。

外层 `catch` 只记录日志，不回滚已经完成的容器副作用或批量拖放中先前成功的项目。

**影响**

当全局唯一性检查、音效收集或后续代码抛错时，可能留下“容器已被时间线接管但没有片段”的半成品状态。跨轨添加失败时，旧片段可能脱离轨道。批量拖放可能部分成功。

**修复方向**

先完整预检，再提交；检查所有 `addClip()` 返回值；在片段成功添加后才提交容器状态变化；命令层提供失败补偿和整批回滚。

**回归验证**

注入目标轨拒绝、音效收集异常和批量第二项失败，断言容器状态、轨道片段、音频和选择均恢复到拖放前。

### C-03 容器锁定只覆盖部分内容操作，属性、结构和批量删除可绕过

**优先级**：P1  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/core/container/Container.java:140-183`
- `src/main/java/com/director/ui/container/ContainerCommandService.java:53-62`
- `src/main/java/com/director/ui/container/ContainerCommandService.java:404-450`
- `src/main/java/com/director/ui/container/ContainerCommandService.java:473-499`
- `src/main/java/com/director/ui/panels/ContainerPropertiesPanel.java:192`
- `src/main/java/com/director/ui/panels/ContainerPropertiesPanel.java:260-281`
- `src/main/java/com/director/ui/container/ContainerTreeInteractionHandler.java:541-565`
- `src/main/resources/assets/director/lang/zh_cn.json:491`
- `src/main/resources/assets/director/lang/zh_cn.json:577`

**问题**

`addBlocks()`、`removeBlock()`、`clearBlocks()` 检查锁定，但重命名、类型、状态、metadata、默认预设、结构移动和批量删除没有统一的锁定门禁。`removeContainers()` 本身不检查锁定。属性面板和树菜单主要按“是否时间线控制”禁用，而不是按 `isLocked()` 禁用。

语言文案明确写着“锁定后禁止编辑”和“锁定容器后，将无法修改容器的内容和属性”，与实现不一致。

**影响**

用户认为锁定后不可修改的容器，仍可能被重命名、改属性、移动结构或批量删除，造成误操作和存档意外变化。

**修复方向**

在 manager/service 边界建立唯一的可编辑性校验，而不是依赖各 UI 自行判断；明确锁定容器是否允许解锁、加入时间线等例外；批量操作对锁定项整体拒绝或明确过滤；UI 禁用状态与服务端校验保持一致。

**回归验证**

锁定后逐项验证重命名、metadata、颜色、类型、状态、预设、结构移动、单删、批删、拖放；仅解锁操作应允许。

### C-04 删除片段撤销忽略恢复失败，却恢复音频并出栈

**优先级**：P1  
**证据**：已复现

**位置**

- `src/main/java/com/director/ui/timeline/commands/DeleteClipCommand.java:107-143`
- `src/main/java/com/director/timeline/model/TimelineTrack.java:99-111`

**问题**

撤销时 `ownerTrack.addClip(clip)` 的返回值被忽略，随后仍执行 `restoreAudio.run()`。`canUndo()` 只检查 `removedIndex >= 0`，不检查片段当前能否恢复。

如果删除后同一容器已被另一个片段占用，全局唯一性会拒绝旧片段，但音频事件仍被恢复。

**影响**

时间线片段与音频状态分裂；用户看到撤销成功，实际片段缺失；历史记录出栈后无法再次尝试恢复。

**修复方向**

将片段和音频视为一个原子恢复单元；`addClip()` 失败时不得恢复音频，也不得把命令标记为成功；根据需要让历史保留失败命令或返回明确失败状态。

**回归验证**

构造同容器占用冲突，验证撤销失败时轨道、音频、选择和脏状态均不变，且不会静默丢失历史。

### C-05 时间线加载非原子，损坏 DTO 会留下空模型或部分模型

**优先级**：P1  
**证据**：已复现

**位置**

- `src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:942-982`
- `src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:1011-1016`
- `src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:1152-1161`
- `src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:1297-1306`
- `src/main/java/com/director/timeline/model/TimelineModel.java:209-224`

**问题**

`fromDTO()` 先修改 viewport、duration、灯光并删除现有轨道和标记，再逐轨恢复。`trackDTO.clips` 没有 null 保护，`clipDTO.label` 直接进入要求非空的构造器。任一非法项抛错后，没有整体回滚。

最后还忽略 `track.addClip(clip)` 的结果，可能静默丢失被约束拒绝的片段。`TimelineModel` 构造器会在 `ContainerTimelineService` 绑定新模型前加载存档，进一步放大了全局服务唯一性耦合。

**影响**

损坏文件或版本不兼容数据可能导致当前模型被清空或部分恢复；自动保存随后可能覆盖仍有价值的旧状态。

**修复方向**

先完整校验并构建临时模型，全部成功后一次性替换当前模型；保留旧模型直到提交成功；对所有添加失败给出轨道/片段级诊断；加载期间使用模型局部唯一性，不依赖全局当前模型。

**回归验证**

覆盖 null clips、null label、非法时间、重复容器绑定、第二轨损坏和添加被拒绝，断言失败后旧模型完整保留。

### C-06 逐块拆除片段开始前可见集合为全量，但透明度为 0

**优先级**：P1  
**证据**：已复现

**位置**

- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1099-1125`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:606-620`
- `src/main/java/com/director/timeline/model/TimelineClip.java:561-579`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1255-1261`

**问题**

逐块拆除片段在 `currentTime < clipStart` 时返回全量 render/world blocks，表示片段前应保留完整结构；但透明度统一使用 `clip.computeAlphaAtGlobalTime()`，片段前会返回 0。

整容器路径在 `registerWholeContainerEntries()` 中专门把拆除片段前和轨道禁用时的 opacity 修正为 1，逐块路径没有相同修正。

**影响**

播放头位于拆除片段前或回拖到片段前时，方块可能已经完全消失，与“拆除前保留原结构”的语义相反。

**修复方向**

统一片段阶段和透明度的计算：先判定拆除片段前、禁用、动画中、完成后等阶段，再计算 opacity；避免可见集合与透明度各自采用不同规则。

**回归验证**

对整容器和逐块拆除配置分别验证片段前、起点、动画中、终点、片段后和轨道禁用状态。

### C-07 删除容器或缩减几何时，旧视觉覆盖无法完整释放

**优先级**：P1  
**证据**：强代码证据；服务端残留部分需实机确认

**位置**

- `src/main/java/com/director/core/container/Container.java:160-173`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:324-349`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:830-864`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1605-1655`
- `src/main/java/com/director/core/container/visibility/BlockVisualStateManager.java:376-395`

**问题**

容器删除监听只清理控制器缓存，没有清理 `BlockVisualStateManager` 的覆盖和 render entries。`cleanupContainersNoLongerControlled()` 在容器已从 registry 移除时直接 `continue`，也无法释放旧状态。

几何缩减时，控制器只对当前 `allBlocks` 和 `renderBlocks` 写状态；`clearOverridesFor()` 只遍历容器当前坐标。已经从容器删除的旧坐标无法通过当前几何释放。

`Container.removeBlock()` 还会删除持久 `BlockStore` 条目，但运行时可见性快照缺少对应的对称移除逻辑。

**影响**

旧坐标可能残留隐藏、ghost、动画覆盖或 render entry，表现为幽灵方块、错误遮挡和持续区块重渲染。运行时快照可能保留已删除方块数据。

**修复方向**

在几何变更前记录旧坐标集合，同步清理视觉覆盖、render entries、动画基态和运行时快照；容器删除时在丢弃对象前按缓存坐标完成清理；建立按容器/维度/坐标的所有权索引，支持反向释放。

**回归验证**

播放中删除容器、删除后撤销、移除部分方块、清空方块、隐藏/ghost/动画状态下缩减几何，检查旧坐标和快照均无残留。

### C-08 整容器渲染视图使用任意首块的局部变换

**优先级**：P1  
**证据**：强代码证据；GPU 表现需实机确认

**位置**

- `src/main/java/com/director/core/animation/WholeContainerAnimation.java:27-43`
- `src/main/java/com/director/core/animation/BlockAnimationRenderer.java:80-100`
- `src/main/java/com/director/core/animation/BlockAnimationRenderer.java:131-148`
- `src/main/java/com/director/core/animation/WholeContainerRenderView.java:34-61`

**问题**

每个方块拥有以自身为中心共轭 pivot 的局部矩阵，因此不同坐标的矩阵并不相同。`BlockAnimationRenderer` 却为整个 clip 只创建一个 `WholeContainerRenderView`，并使用 assemblies 中任意首个 entry 的 pos 和 transform。

该视图随后用同一个 transform 计算所有方块的邻居、AO、灯光、颜色和 BlockEntity 显示位置。对首块自身可能正确，对其它块及其邻居查询会使用错误的变换中心。

**影响**

整容器旋转、缩放或平移时，面剔除、邻接光、AO、生物群系颜色和 BlockEntity 查询可能错位，产生空洞、错误明暗或 Tile Entity 渲染异常。

**修复方向**

渲染视图应接收共享 pivot/世界变换，并对查询位置应用正确的全局变换；或者为每块使用其局部矩阵计算邻居显示位置。不能在 clip 级缓存一个任意首块矩阵供所有块复用。

**回归验证**

使用非对称多块结构和非零 pivot 验证邻居、AO、灯光、颜色、BlockEntity 的显示坐标；与逐块独立渲染路径做图像对比。

### C-09 移动、缩放和拖动预览会裁剪全局关键帧，撤销无法恢复

**优先级**：P1  
**证据**：已复现

**位置**

- `src/main/java/com/director/timeline/model/TimelineClip.java:118-140`
- `src/main/java/com/director/ui/timeline/commands/MoveClipCommand.java:95-128`
- `src/main/java/com/director/ui/timeline/commands/ResizeClipCommand.java:47-66`
- `src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:187-191`

**问题**

`setTimeRange()` 会调用 `pruneOrphanedKeyframes()`。纯平移也使用该方法，导致新范围外的全局 `ClipKeyframe` 被永久删除。撤销只恢复旧 start/end，无法恢复已经删除的关键帧。拖动预览和取消拖动同样无法恢复。

`offsetTime()` 本来会同时平移片段和全局关键帧，但移动命令没有使用它。

**影响**

音频自动化和依赖通用 `ClipKeyframe` 的功能会丢失数据；用户取消拖动或撤销后仍无法恢复。

**修复方向**

纯移动使用 `offsetTime()`；缩放保留明确的裁剪策略，但命令必须快照被裁剪关键帧并在 undo 时恢复；拖动预览也应使用可恢复的临时状态。

**回归验证**

验证不同方向移动、缩短后延长、跨轨移动、undo/redo 和拖动取消，关键帧数量与全局时间均完整恢复。

> 范围说明：当前纯容器方块动画主要由 `ClipAnimationConfig` 驱动，不直接消费 `TimelineClip.clipKeyframes`。该问题主要影响音频自动化、旧存档和通用片段 API，不应理解为所有容器动画都依赖这些关键帧。

## 6. P2 问题

### C-10 普通片段属性编辑没有撤销事务

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyUi.java:206-329`
- `src/main/java/com/director/ui/panels/PropertiesPanel.java:293-297`

**问题**

普通 basic/per-block/effect 属性直接调用 setter，`PropertiesPanel` 在 modified 后只 `markDirty()`，不执行 `StateChangeCommand`。整容器编辑已有不可变前后快照会话，但普通属性没有同等机制。

缩短长度还会立即触发关键帧裁剪。

**影响**普通属性编辑无法撤销，手势中修改与最终值没有清晰事务边界。

**修复方向**复用 `WholeContainerAnimationEditSession` 的快照思想，为普通属性提供 begin/edit/commit/cancel；提交时生成一个状态命令。

**回归验证**颜色、起止时间、长度、淡化、动画参数、可见性编辑的 undo/redo 与取消恢复。

### C-11 `clear()` 和批量删除可遗留已移除容器的 active 引用

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/core/container/ContainerManager.java:1309-1350`
- `src/main/java/com/director/core/container/ContainerManager.java:443-507`
- `src/main/java/com/director/core/container/ContainerIntakeSubscriber.java:215-239`
- `src/main/java/com/director/core/selection/tools/ContainerCullTool.java:280-299`
- `src/main/java/com/director/core/container/commands/ToggleIntakeCommand.java:34-58`

**问题**

`clear()` 清理 containers、roots 和选择，但没有清理 `activeContainer` 或 `lastSelectedContainer`。`removeContainers()` 也没有 active 清理。`ReleaseContainerCommand` 做了局部补救，说明问题已被部分路径意识到。

实时接收、剔除工具和 intake 命令会继续读取 active 引用。

**影响**UI 和工具可能继续指向已删除对象，导致状态错乱、错误日志或后续写入失败。

**修复方向**所有删除路径统一清除 active/lastSelected，并触发 active changed 事件；消费者同时校验容器是否仍注册。

**回归验证**单个删除、递归删除、批量删除、clear、清空后 undo，active 和选择引用均正确。

### C-12 释放与清空命令的状态快照不完整，且锁定拒绝发生在副作用之后

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/core/container/commands/ReleaseContainerCommand.java:41-73`
- `src/main/java/com/director/core/container/ContainerManager.java:304-309`
- `src/main/java/com/director/core/container/commands/ClearAllContainersCommand.java:28-49`
- `src/main/java/com/director/core/container/commands/ClearAllContainersCommand.java:57-89`

**问题**

`ReleaseContainerCommand` 先清 active、删除时间线片段，最后调用 `manager.removeContainer()`；如果容器锁定，manager 拒绝删除，但前面的副作用已经发生。

`ClearAllContainersCommand` 只快照层级和选择，不快照 active、时间线片段和可见性。undo 时若存在命令执行后新建的容器，会直接 `manager.clear()`，可能删除这些新对象。

**影响**释放锁定容器可能变成“删除片段但保留容器”；撤销清空无法恢复完整场景，甚至可能清除后续工作。

**修复方向**命令执行前完成可删除性预检；快照完整包含 active、片段、音频、可见性和层级；undo 只恢复本命令影响的对象，不清除无关新对象。

**回归验证**锁定释放、清空后新建容器再 undo、清空时存在播放片段和隐藏快照。

### C-13 容器 NBT 丢失 metadata 和默认动画预设，时间戳只写不读

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/core/container/persistence/ContainerPersistence.java:254-299`
- `src/main/java/com/director/core/container/persistence/ContainerPersistence.java:301-360`

**问题**

保存字段包含 id、name、type、parent、color、opacity、locked、state、intake、created、modified、level、blocks、snapshots，但没有 description、tagColor、titleColor、iconColor 等 metadata，也没有 `defaultAnimationPreset`。created/modified 只写入，解码时没有恢复。

**影响**重启或切换世界后容器描述、标签色和默认动画种子丢失；审计时间信息被重置。

**修复方向**增加向后兼容的可选 NBT 字段；为 metadata 值类型建立稳定编码；保存和恢复预设副本及时间戳；添加旧存档兼容测试。

**回归验证**完整字段 NBT 往返、旧版 NBT 加载、异常 metadata 值和预设深拷贝。

### C-14 重复 UUID 修复没有同步父引用和时间线绑定

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/core/container/persistence/ContainerPersistence.java:145-179`

**问题**发现重复 UUID 时，只把后出现的容器替换为新 UUID。父引用仍可能指向旧 UUID，最终解析到第一个同名 ID 对象；时间线片段的 `boundContainerId` 也没有对应 remap。

**影响**损坏存档的“修复”可能把子容器挂到错误父级，或让片段绑定到错误容器。

**修复方向**为重复 ID 建立显式恢复映射，所有父引用和绑定引用使用同一策略迁移；无法确定语义时将冲突容器隔离并报告，而不是静默绑定。

**回归验证**重复 UUID、子节点引用重复 ID、时间线绑定重复 ID、重复项位于文件不同顺序。

### C-15 轨道约束依赖全局当前模型，类型变更和加载添加缺少一致性校验

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineTrack.java:86-149`
- `src/main/java/com/director/timeline/model/TimelineTrack.java:245-247`
- `src/main/java/com/director/timeline/model/TimelineModel.java:209-224`
- `src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:1306`

**问题**

容器片段唯一性通过 `DirectorServices.get().containerTimeline().findClipsForContainer()` 检查，而不是当前 `TimelineTrack` 所属模型。多模型、离线模型或新模型加载期间可能检查错对象。

`setType()` 不重新校验已有片段；持久化忽略 `addClip()` 返回值。

**影响**合法片段可能被错误拒绝，非法轨道状态可能长期存在，加载可能静默丢片。

**修复方向**唯一性约束归属于模型或显式传入的 scope；类型变更提供迁移/拒绝策略；持久化对添加失败逐项报告并整体失败。

**回归验证**两个 TimelineModel 并存、构造期间加载、轨道改类型、重复绑定片段、加载时约束拒绝。

### C-16 复制或剪切绑定音频片段会清除容器绑定

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineClipCopies.java:22-33`
- `src/main/java/com/director/ui/timeline/state/ClipboardManager.java:40-59`
- `src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:947-956`

**问题**

`TimelineClipCopies.copy()` 对 `AudioTimelineClip` 主动清除 `boundContainerId/Name`。复制和剪切都走该逻辑。生产路径确实会创建绑定容器的音频片段，因此不是不可达 API 问题。

**影响**剪切再粘贴后音频失去容器关联，后续重命名、移动、删除和联动可能失效；复制出的独立音频也可能被错误绑定。

**修复方向**区分 copy 与 cut 的绑定策略：cut/undo 保留关系，普通 copy 明确生成独立副本或重新建立链接；使用显式 API，而不是在通用 copy 中清空字段。

**回归验证**绑定音频的 copy、cut/paste、cut/undo、容器重命名、容器片段移动和删除联动。

### C-17 post-animation fade 使用 clipEnd，控制器对长逐块动作使用 tail 修正

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineClip.java:413-439`
- `src/main/java/com/director/core/animation/PerBlockAnimationTiming.java:21-37`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:606-620`

**问题**

`getRestoreTime()` 和 `isInPostAnimationFade()` 以 `endTime` 为基准；控制器对超出 clipEnd 的长逐块动作计算 tail，并把透明度时间修正为 `currentTime - tail`。

淡化是否开始、恢复时间等 API 与控制器实际采样时间不一致。

**影响**动作长度超过片段长度时，淡化可能提前、延后或与动作结束不同步。

**修复方向**在模型层统一“有效动画完成时间”，由片段配置和 timing 计算一次，供透明度、UI、音频和控制器共同使用。

**回归验证**动作 duration 大于/小于片段长度、不同 delay、sequence span、loop、seek 和淡化边界。

### C-18 视觉覆盖只按 BlockPos 索引，跨维度可能污染

**优先级**：P2  
**证据**：条件性 + 强代码证据；需实机确认

**位置**

- `src/main/java/com/director/core/container/visibility/BlockVisualStateManager.java:298-299`
- `src/main/java/com/director/mixin/BlockRenderManagerMixin.java:64-75`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:565-632`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1830-1857`

**问题**

`visualOverrides` 和 `renderEntries` 只按 `BlockPos` 索引，Mixin 查询也不区分世界。控制器只在 levelKey 为 null 时绑定当前世界，但采样和视觉状态本身不按容器 levelKey 与客户端当前世界过滤。

**影响**容器绑定维度与当前客户端维度不一致时，同坐标方块可能被错误隐藏、ghost 或接管渲染。

**修复方向**视觉键至少包含 `RegistryKey<World> + BlockPos`；按当前世界过滤采样容器；维度切换时显式清理或迁移状态；服务端任务继续使用容器绑定世界解析。

**回归验证**主世界与下界同坐标容器、切换维度、多世界服务端环境、维度切换后 undo。

### C-19 视频导出未隔离播放态、粒子和特效门控

**优先级**：P2  
**证据**：强代码证据；需实机确认

**位置**

- `src/main/java/com/director/export/video/VideoExportManager.java:95-158`
- `src/main/java/com/director/timeline/playback/TimelinePlaybackSampler.java:23-42`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:508-521`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1262-1265`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1313-1348`

**问题**

EXPORT policy 只改变异常处理，没有传入方块控制器。控制器仍读取 `model.isPlaying()`；导出流程也没有暂停或隔离当前播放态。采样中的 dust、particle 和 effect 门控仍可能产生客户端副作用。

**影响**离线导出可能发射粒子、改变特效游标、触发真实世界交接，并与用户正在进行的播放互相干扰。

**修复方向**为 sampler/controller 提供显式 PREVIEW/EXPORT/HEADLESS 上下文；导出时暂停并恢复播放，禁用粒子与交互副作用，使用独立采样状态。

**回归验证**播放中启动导出、导出中 seek/loop、dust/effects 开启、取消导出和导出失败后的状态恢复。

### C-20 大方块量在静止播放和完成后 effects 场景仍存在 O(N) 每帧开销

**优先级**：P2  
**证据**：强代码证据；需性能实测

**位置**

- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:427-449`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:654-719`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1269-1300`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1492-1528`

**问题**

播放或 scrubbing 时 `needsClipBlockUpdate()` 始终为 true。每帧仍进行集合差、遮挡和 render entry 处理；整容器路径每帧全量 hidden→visible。逐块路径在动画完成后如果配置了 effects，仍对所有静态方块调用 `sample()`。

**影响**大容器在“视觉上静止”的播放区间仍可能产生显著 CPU、分配和区块重渲染开销，形成性能悬崖。

**修复方向**为静态区间增加 revision/时间桶缓存；effects 只在事件窗口或跨界时采样；整容器在全量重建前先判断 opacity、transform 和可见集合是否变化；用真实帧时间数据决定进一步分帧策略。

**回归验证**1k/10k/50k/200k 方块、整容器/逐块、有/无 effects、播放/暂停/seek，记录帧时间和分配量。

### C-21 单容器异常可能中断整帧，且缺少 `finishFrame()` 兜底

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:543-632`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:908-948`
- `src/main/java/com/director/ui/timeline/playback/PlaybackOrchestrator.java:205-238`

**问题**

控制器从 `beginTimelineFrame()` 后遍历轨道和容器，没有按容器隔离异常，也没有 `try/finally` 保证 `finishFrame()`。orchestrator 虽然捕获整个 controller 的 RuntimeException，但无法恢复控制器内部已经完成的一半状态。

**影响**单个非法方块、渲染状态或动画配置可能让后续容器在本帧停止更新，并遗留临时帧状态。

**修复方向**按容器捕获和记录错误；用 `try/finally` 完成帧尾清理；失败容器保留上一稳定状态或进入明确错误态；提供诊断计数。

**回归验证**在首个/中间/末尾容器注入异常，验证其它容器继续更新、帧尾状态清理、下一帧可恢复。

### C-22 服务端可见性任务对永久失败无限 requeue，快照补齐只加不删

**优先级**：P2  
**证据**：强代码证据；需服务端压力验证

**位置**

- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:134-177`
- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:482-499`
- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:530-559`

**问题**

world/store 缺失时任务重新放回 `timelineWork`；区块未加载或快照条目缺失时 `timelineHideAt()/timelineShowAt()` 返回 false，未完成工作继续 requeue。没有失败分类、退避或上限。

`backfillSnapshotFromBlockStore()` 只补充缺失项，不删除已经不属于容器的项。

**影响**长期不可用区块或损坏快照可能持续消耗每 tick 预算，饿死其它容器；旧快照可能不断增长。

**修复方向**区分瞬态等待与永久数据错误；对区块加载采用事件驱动/退避；对缺失快照隔离并报告；补齐逻辑改为与容器几何对称 diff；增加卡死任务指标。

**回归验证**区块未加载、store 缺失、entry.state 为 null、容器几何删除、多个容器竞争预算。

### C-23 时间线命令基类和插入命令缺少异常补偿

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/ui/timeline/commands/BaseTimelineCommand.java:44-67`
- `src/main/java/com/director/ui/timeline/commands/InsertClipCommand.java:29-45`

**问题**

`BaseTimelineCommand` 直接执行 `doExecute()` 和 `doUndo()`，没有失败结果或补偿。`InsertClipCommand` 先添加片段，再初始化音频；音频初始化抛错时不会回滚片段。

**影响**命令可能已经修改模型但未成功进入历史，或 undo 半途失败；用户无法通过历史恢复一致状态。

**修复方向**为命令提供明确成功/失败结果；在副作用发生后提供 rollback；只在完整成功后通知模型和入历史；对 undo 失败保留恢复上下文。

**回归验证**音频初始化异常、轨道添加后异常、undo 音频捕获异常、redo 失败。

### C-24 片段删除和重命名的模型失效处理不完整

**优先级**：P2  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/timeline/container/ContainerTimelineService.java:152-190`
- `src/main/java/com/director/timeline/container/ContainerTimelineService.java:193-226`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:417-449`

**问题**

`removeClipsForContainer()` 只 `markDirty()`，没有显式 `markDurationDirty()`、清理选中片段或主动失效方块控制器。模型监听最终可能递增 revision，但选择、时长和即时失效没有统一保证。

`updateClipNamesForContainer()` 只在 label 改变时更新 `boundContainerName`；label 相同但 bound name 为 null/旧值时不会修复。

**影响**删除后总时长缓存、选择状态和预览清理可能滞后；异常存档中的旧绑定名称无法自愈。

**修复方向**集中封装片段删除/重命名的完整失效流程；无条件同步绑定名称；清理相关选择和缓存；统一时长失效。

**回归验证**删除最后一个片段、删除当前选中片段、重命名同 label、null/旧 boundContainerName、撤销删除。

### C-25 容器名称索引按名称无条件删除，重名时可能误删其它对象

**优先级**：P2  
**证据**：条件性 + 强代码证据

**位置**

- `src/main/java/com/director/core/container/ContainerManager.java:351-365`
- `src/main/java/com/director/core/container/ContainerManager.java:483-495`
- `src/main/java/com/director/core/container/ContainerManager.java:903-932`

**问题**

删除和重命名时直接 `containersByName.remove(name)`，不检查该名称当前映射的对象是否就是目标容器。正常创建路径会生成唯一名，但损坏存档、恢复或直接 API 可能产生重名。

**影响**删除一个重名容器可能让另一个容器从名称索引消失，造成查询和后续命名异常。

**修复方向**按 ID 删除并校验名称映射身份；名称索引使用名称到集合/ID 的结构，或加载时强制修复重名。

**回归验证**同名容器删除、改名、加载和 undo，名称索引始终指向正确对象。

### C-26 非有限数值可进入片段、关键帧和透明度模型

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineClip.java:63-69`
- `src/main/java/com/director/timeline/model/VisibilityKeyframe.java:55-60`
- `src/main/java/com/director/timeline/model/clip/ClipKeyframe.java:98-105`
- `src/main/java/com/director/core/container/Container.java:458-461`

**问题**

构造器只比较 `startTime > endTime`，NaN 会使比较为 false；Infinity 也可通过。可见性关键帧只检查 `time < 0`，NaN 同样通过；`clamp01()` 不能过滤 NaN。`Container.setOpacity()` 的普通 clamp 也保留 NaN。

**影响**排序、二分、插值、时长、透明度和持久化可能产生不可预测行为，极端值可能导致 UI 或渲染异常。

**修复方向**在所有外部输入和 DTO 解码边界校验有限值；模型 setter 对 NaN 抛出异常或使用明确回退；保存前诊断非有限值。

**回归验证**NaN、±Infinity、极小长度、超大时间、非法 DTO 和属性面板输入。

## 7. P3 问题

### C-27 `duplicateShallow()` 复制语义不完整，并可能改变零长度片段

**优先级**：P3  
**证据**：已复现

**位置**

- `src/main/java/com/director/timeline/model/TimelineClip.java:78-99`
- `src/main/java/com/director/timeline/model/TimelineClip.java:118-126`
- `src/main/java/com/director/timeline/model/TimelineClip.java:300-316`

**问题**

拷贝构造函数会复制 `visibilityKeyframes`，`duplicateShallow()` 却遗漏该字段。它通过构造器创建副本，而 `setTimeRange()` 允许零长度，构造器会强制最小长度，因此复制可能改变源片段长度。

**影响**未来分割、复制等路径使用该方法时，可见性效果丢失，片段时长发生细微变化。

**修复方向**让 shallow copy 与 copy constructor 使用同一字段清单，并精确保留时间范围；增加两种复制 API 的一致性测试。

**回归验证**可见性关键帧、零长度、最小长度和所有 fade 字段的复制结果。

### C-28 多处模型 getter 暴露内部可变对象，可绕过约束和失效机制

**优先级**：P3  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineTrack.java:213-215`
- `src/main/java/com/director/timeline/model/TimelineClip.java:319-328`
- `src/main/java/com/director/core/container/Container.java:446-455`

**问题**

`getClips()` 返回内部列表，`getAnimationConfig()` 返回内部配置，`getDefaultAnimationPreset()` 返回内部预设。调用者可以直接修改，绕过 `addClip()` 约束、父引用维护、revision 和封装。

**影响**不变量依赖所有调用者自觉，测试和外部 API 很容易制造非法状态。

**修复方向**返回只读视图或副本，并提供明确的 mutate/copy API；现有连续编辑会话可继续基于快照工作；逐步迁移生产调用者。

**回归验证**外部修改返回值不能改变模型；显式修改方法正确触发失效和通知。

### C-29 `Container.addChild()` 只防自身循环，不防间接循环

**优先级**：P3  
**证据**：强代码证据

**位置**

- `src/main/java/com/director/core/container/Container.java:83-101`
- `src/main/java/com/director/core/container/Container.java:121-137`

**问题**

领域对象只检查 `child == this`。如果把祖先添加为子节点，会形成循环；`getDepth()` 和 `isDescendantOf()` 将无法终止。manager 移动路径和持久化加载有部分循环保护，但直接领域 API 仍不安全。

**影响**非法树结构可能导致遍历、序列化和 UI 栈溢出/卡死。

**修复方向**在领域层拒绝 `child.isDescendantOf(this)`，并添加最大深度或一致性诊断。

**回归验证**自身、直接父子反向、三级祖先关系和已有子树移动。

### C-30 `findClipAtTime()` 与重叠判断对片段边界采用不同语义

**优先级**：P3  
**证据**：语义待定 + 强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineTrack.java:158-165`
- `src/main/java/com/director/timeline/model/TimelineTrack.java:448-458`

**问题**

`findClipAtTime()` 使用闭区间，`hasClipOverlap()` 使用严格重叠。两个片段首尾相接时，边界点会被认为同时属于两个片段，但重叠判断认为不重叠。

**影响**边界播放、属性定位、音频联动或未来插入逻辑可能选中前一个片段而不是后一个片段。

**修复方向**明确统一语义：推荐查找使用左闭右开 `[start, end)`，并为最后一个片段或播放终点提供明确特例；同步调整测试和调用方。

**回归验证**相接片段边界、零长度、片段终点、播放 rangeEnd 和吸附到边缘。

## 8. 条件性风险与未实机验证项

以下问题不应当作所有用户必现：

1. **跨维度视觉污染（C-18）**：需要容器绑定世界与客户端当前世界不一致，但代码键设计已明确缺少世界维度。
2. **整容器邻接渲染错误（C-08）**：需要非零 transform；实际画面差异需客户端图像对比确认。
3. **性能悬崖（C-20）**：代码存在全量路径，但严重程度取决于方块数、effects、机器性能和渲染批次。
4. **服务端任务饥饿（C-22）**：需要快照缺失、区块不可用或永久写入失败；未进行长期压力测试。
5. **导出副作用（C-19）**：需要导出与播放/特效状态同时存在；未实机验证粒子和世界交接。
6. **重名索引（C-25）**：正常 UI 创建路径通常生成唯一名，风险主要来自损坏存档和直接 API。
7. **单容器异常（C-21）**：需要运行时产生异常；代码结构决定了异常会逃出整帧。

## 9. 明确不建议误报的事项

- `computeAlphaAtGlobalTime()` 在片段结束后并非无条件返回 1，已有可见性关键帧和 post-animation fade 逻辑；本次问题是拆除片段前和淡化基准，不是“结束后强制恢复不透明”。
- `duplicateShallow()` 当前生产调用者未确认，属于 API 潜在缺陷，不应描述为所有复制粘贴必现。
- 容器片段唯一性的全局服务耦合主要在多个 TimelineModel、离线模型或加载期间出现，不是普通单模型拖放必现。
- `WholeContainerAnimationEditSession` 的快照/提交模式是正确方向，普通属性编辑应复用而不是推翻。
- 纯容器方块动画不直接依赖 `TimelineClip.clipKeyframes`，C-09 的数据丢失范围需要按音频自动化和通用 API 理解。
- 没有证据表明容器主链依赖 `BlockDisplayEntity`，不应把演员预览实体的问题混入容器审查。

## 10. 推荐修复顺序

### 第一阶段：先恢复可编辑、可撤销和可加载

1. C-01、C-02：拖放命令化、预检、整批回滚、ripple 音频同步。
2. C-03：统一容器锁定门禁。
3. C-04、C-23：片段和音频原子恢复，命令失败补偿。
4. C-05：时间线临时模型构建与原子替换。
5. C-09：移动使用 offset，缩放命令快照关键帧。

### 第二阶段：修复播放和状态生命周期正确性

6. C-06：拆除片段前透明度语义。
7. C-07、C-11、C-12：删除、几何变化、active 和命令快照。
8. C-08、C-18：整容器 render view 与世界维度键。
9. C-17、C-24：统一 fade 完成时间和模型失效。
10. C-21、C-22：单容器异常隔离与可见性任务失败分类。

### 第三阶段：数据完整性和性能

11. C-13、C-14、C-15、C-16、C-25、C-26：持久化、轨道约束、音频复制、名称和数值边界。
12. C-19、C-20：导出上下文隔离与大方块量性能优化。
13. C-10、C-27 至 C-30：普通属性事务和 API 语义整理。

### 当前提交前置事项

在继续主分支全量验证前，需要先修复 `ActorLookAtEditSession.java:61` 的编译错误。该事项独立于容器问题，但会阻塞当前 HEAD 的构建和回归测试。

## 11. 总体评价

容器与容器片段已经具备完整的领域模型、拖放绑定、双模式动画、客户端覆盖渲染和服务端预算写入骨架。当前最值得投入的不是继续扩展功能，而是把“模型修改、命令历史、音频、可见性和存档”收敛到同一事务边界。

优先完成 P1 后，容器时间线才能达到可撤销、可恢复、加载失败不破坏现场的基本要求；随后再通过维度键、渲染视图修正和性能采样，提升大方块动画的稳定性与可维护性。
