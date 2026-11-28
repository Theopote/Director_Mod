# Director 方块动画代码审查报告

- 审查日期：2026-11-27
- 审查基线：`main` 分支，提交 `46a03298f621772622fe37973cea66cbef4d2b8c`
- 审查方式：源码调用链审查、跨文件一致性检查、Minecraft 1.21.10 映射实现核对、全量单元测试、临时缺陷特征化探针
- 产品代码修改：无
- 提交与推送：无
- 审查范围：动画配置、逐块与整容器采样、序列生成、播放控制器、客户端视觉状态、渲染、服务端世界写入、BlockEntity 快照、动态时长、缓存、特效与相关测试

## 1. 结论摘要

方块动画的主链路已经比较完整：控制器使用时间线时间预采样 snapshot，逐块动画将“序列启动时间”和“单块动作时间”分开，服务端世界写入有数量与时间预算，渲染端也区分了静态网格、独立变换和透明覆盖层。这些设计值得保留。

本轮确认 **5 项新的独立问题**：

- **P1：3 项**
  - BlockEntity NBT 恢复失败被吞掉，任务仍可能报告完成。
  - 隐藏方块时先移除 BlockEntity，且忽略 AIR 写入失败。
  - `ALL_AT_ONCE` 逐块进入动画完成后、片段结束前出现空窗。
- **P2：2 项**
  - 可见性缓存会跨过合法的亚毫秒动作边界。
  - 动态总时长没有包含 post-animation fade 的结束时间。

其中 3 项由临时探针直接复现；2 项世界写入问题具有明确调用链和 Minecraft 映射实现证据，但需要服务端集成测试进一步验证。

此前报告中的 C-06、C-08、C-17、C-18、C-26、C-27 / T-16 与本次范围有交叉。本报告将它们列为补充证据，不重复编号。

## 2. 验证结果

### 2.1 完整测试基线

审查期间主工作区首次完整 `test` 结果：

- Gradle 9.9.2 / Fabric Loom 1.14.10
- `BUILD SUCCESSFUL`
- 253 个测试类
- 1537 项测试
- 0 failures
- 0 errors
- 1 项 skipped

为排除并发工作区修改，又在提交 `46a03298` 的 detached 干净工作树中执行完整 `test`：

- Gradle 9.9.1 / Fabric Loom 1.14.10
- `BUILD SUCCESSFUL`
- 253 个测试类
- 1537 项测试
- 0 failures
- 0 errors
- 1 项 skipped

交付前清理探针后再次复跑主工作区，工作区已出现并发修改 `src/main/java/com/director/ui/timeline/render/TimelineRenderer.java`。本次结果为 1537 项测试、1 failed、1 skipped：

- 失败用例：`TimelineRenderMathRowsTest.tallActorBoundsUseTheWholeHeaderAndPlaceTheStateInItsBand()`
- 断言：expected `122.4`，actual `20.0`
- 失败位于演员轨道 band 布局，与方块动画代码无关；本轮未修改该文件或该测试。

测试通过只说明现有回归基线可运行，**不代表本报告中的缺陷已被覆盖**。

### 2.2 临时特征化探针

通过临时 Gradle init script 将 6 项探针加入测试源集。探针运行结果为 6 项均在目标断言处失败；这里的“失败”表示成功复现了当前缺陷行为，而不是探针本身无法运行。

| 探针 | 复现结果 | 对应问题 |
| --- | --- | --- |
| `simultaneousPerBlockAnimationMustNotVanishInsideItsClip` | `[0s, 5s]` 的 `ALL_AT_ONCE` 片段在 `0.5s` 可见，在 `2.0s` 已变为空集 | BA-03 |
| `visibleBlocksCacheMustNotReuseAcrossDemolitionCompletion` | 拆除动作 `0.00009s` 时可见，`0.00011s` 完成后仍错误复用可见集合 | BA-04 |
| `timelineDurationMustIncludePostAnimationFade` | 片段结束于 `10s`、淡化结束于 `12s`，模型总时长仍为 `10s` | BA-05 |
| `demolitionBlocksBeforeClipStartMustRemainOpaque` | 片段开始前可见集合非空，但 alpha 为 `0` | 已有 C-06 |
| `overlappingClipsMustNotSilentlyReplaceEachOthersRenderEntry` | 同坐标两个 RenderEntry 写入后只剩 1 个 | 已有 C-18 |
| `clearingOneContainerMustNotRemoveAnotherContainersVisualOverride` | 清理一个容器会删除另一个容器的同坐标覆盖状态 | 已有 C-18 |

探针源、init script 和临时字节码输出均已在交付前清理，未加入产品源码。

### 2.3 Minecraft 映射实现核对

核对了 Minecraft 1.21.10 / Yarn `build.3` 映射中的：

- `World.getBlockEntity()`
- `WorldChunk.setBlockState()`
- `World.setBlockState()`

确认：

- 服务端主线程且区块加载时，`getBlockEntity()` 可以创建缺失的 BlockEntity。
- `setBlockState()` 在方块状态变化时可以创建新的 BlockEntity。
- 新 BlockEntity 使用默认数据，**不会自动恢复快照 NBT**。
- 状态已经相等时，`World.setBlockState()` 返回 `false`，不会执行恢复。

这直接支持 BA-01 和 BA-02 的判断。

### 2.4 未进行的验证

- 未启动 Minecraft 客户端，未验证 GPU 渲染、透明排序、Mixin、ImGui 和多世界切换。
- 未在真实服务端世界中执行箱子、命令方块、漏斗等 BlockEntity 的隐藏/恢复集成测试。
- 未进行超大容器、长期区块不可用和持续高帧率采样的性能基准测试。
- 未验证视频导出与实际播放并发时的全部副作用。

因此，本报告将证据分为“已复现”“强代码证据”和“条件性/需实机确认”。

## 3. 架构与调用链

### 3.1 两条动画路径

- 逐块路径：`BlockSequenceGenerator` → `PerBlockSchedule` / `PerBlockAnimationTiming` → `BlockAnimationEngine` → `BlockAnimationSnapshot`
- 整容器路径：`WholeContainerAnimation` → `WholeContainerAnimation.Frame` → 各方块共享 pivot 的局部变换

逐块路径采用双时钟：

1. `start + delay + normalizedTime × sequenceSpan` 决定每个方块何时开始。
2. 单块 `duration` 决定动作持续多久。
3. `completionLimit()` 允许动作 tail 超出片段边界。

### 3.2 播放、渲染和世界写入

```text
TimelineClipBlockController
  -> calculateVisibleBlocks()
  -> BlockVisualStateManager
  -> BlockAnimationRenderer
  -> BlockRenderManager / 自定义 RenderLayer

TimelineClipBlockController
  -> ContainerVisibilityService.showContainerBlocksForTimeline()
  -> tick() 分帧追平
  -> TimelineVisibilityWork
  -> timelineShowAt() / timelineHideAt()
  -> ServerWorld.setBlockState()
  -> BlockEntityNbtIO.restore()
```

渲染集合和真实世界集合是分离的。客户端渲染消费控制器传入的 `overrideSnapshot`，不按世界时间重新采样，这一点避免了时间线播放时的二次采样跳变。

### 3.3 当前设计中值得保留的部分

- 时间线 snapshot 预采样，避免渲染器使用错误时钟。
- 可见性任务的预算、deadline、迭代器和重试队列。
- 静态 mesh 的容量上限、section 成员校验和世界身份失效机制。
- 原生世界 tessellation 路径保留 AO、biome tint、模型 parts 和内部面剔除。
- ONE_SHOT 特效门控，避免完成帧重复采样造成重复触发。
- 整容器变换使用共享 pivot，并通过 `T(pivot + offset) R S T(-pivot)` 保持相对位置。

## 4. 优先级说明

- **P1**：应尽快修复，可能导致核心播放错误、世界状态错误或方块数据丢失。
- **P2**：应在后续版本修复，涉及边界条件、特定配置和常见编辑流程。
- **已复现**：临时探针直接观察到当前结果。
- **强代码证据**：调用链和失败条件明确，但尚未完成运行时集成验证。

## 5. P1 问题

### BA-01 BlockEntity NBT 恢复失败被吞掉，任务仍报告完成

**优先级**：P1  
**证据**：强代码证据 + Minecraft 映射实现核对

**位置**

- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:550-559`
- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:637-665`
- `src/main/java/com/director/core/container/BlockEntityNbtIO.java:36-49`
- `src/main/java/com/director/core/container/visibility/TimelineVisibilityWork.java:38-58`
- `src/main/java/com/director/core/container/BlockStore.java:64-94`

**问题**

`timelineShowAt()` 的成功契约只检查 BlockState：

```java
if (world.getBlockState(pos).equals(entry.state)) return true;
restoreBlockEntry(world, pos, entry, affectedChunks);
return world.getBlockState(pos).equals(entry.state);
```

即使快照包含 `entry.beNbt`，只要 BlockState 已相等，就会直接返回成功，完全不检查 BlockEntity 是否存在、类型是否正确或 NBT 是否已恢复。

执行恢复时，`restoreBlockEntry()` 还存在三重吞错：

- 不检查 `world.setBlockState()` 的返回值。
- `world.getBlockEntity(pos)` 为 `null` 时直接返回。
- 忽略 `BlockEntityNbtIO.restore()` 的布尔返回值。

`BlockEntityNbtIO.restore()` 本身能够返回 `false`，但调用方没有消费该结果。最终 `TimelineVisibilityWork` 将位置加入 `shown`，任务被确认为完成。

**影响**

- 箱子内容、命令方块命令、漏斗过滤配置等 BlockEntity 数据可能丢失，而时间和 UI 仍认为恢复完成。
- 当区块加载后 BlockState 已存在但 BlockEntity 缺失或数据异常时，early-return 会永久跳过修复。
- 后续隐藏可能捕获到这个已经损坏的默认 BlockEntity，进一步覆盖原始快照。
- 该问题对普通方块不可见，但在容器包含功能性 BlockEntity 时属于数据丢失风险。

**修复方向**

1. 将恢复成功定义扩展为“BlockState 正确，且快照要求的 BlockEntity 已正确恢复”。
2. BlockState 已相等时，不要无条件 early-return；若 `beNbt != null`，仍检查 BlockEntity。
3. 检查 `setBlockState()` 的结果、BlockEntity 是否存在以及 `restore()` 的返回值。
4. 对可恢复失败返回 `false`，让 `TimelineVisibilityWork` 继续重试。
5. 对非法快照 NBT 等不可重试错误，应显式上报并保留快照，不能静默标记完成。
6. 校验 NBT 时应忽略 `id/x/y/z` 等位置标识差异，重点比较实际业务数据，避免将合法运行差异误判为失败。

**回归验证**

- 使用箱子和命令方块执行隐藏 → 恢复，验证物品、命令和自定义名称。
- 构造 BlockState 已相等但 BlockEntity 缺失/默认数据的场景，确认会重新恢复。
- 注入 `setBlockState()` 失败、BlockEntity 创建失败和 NBT 读取失败，确认任务不会误报完成。
- 验证区块重新加载后再次显示仍能恢复业务数据。

### BA-02 隐藏时先移除 BlockEntity，且忽略 AIR 写入失败

**优先级**：P1  
**证据**：强代码证据；异常路径需服务端集成验证

**位置**

- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:530-547`
- `src/main/java/com/director/core/container/visibility/ContainerVisibilityService.java:724-740`

**问题**

时间线和整体隐藏路径都采用以下顺序：

```java
var be = world.getBlockEntity(pos);
if (be != null) world.removeBlockEntity(pos);
try {
    world.setBlockState(pos, Blocks.AIR.getDefaultState(), ContainerBlockUpdateFlags.BATCH);
} catch (Exception e) {
    return false;
}
```

BlockEntity 在 AIR 写入成功前就被移除。`timelineHideAt()` 还没有检查 `setBlockState()` 的返回值，即使返回 `false` 也会最终返回 `true`；`VisibilityTask.hideOne()` 同样不检查返回值。

Minecraft 的状态变化本身会处理旧 BlockEntity 的移除，因此显式提前 `removeBlockEntity()` 不是必要步骤，反而扩大了失败窗口。

**影响**

- `setBlockState()` 抛异常时，原方块可能仍在世界中，但 BlockEntity 已经被删除。
- `setBlockState()` 返回 `false` 时，隐藏可能没有发生，但时间线任务仍可能推进。
- 整体隐藏任务只按处理位置数判断完成，不验证最终世界状态。
- 与 BA-01 叠加时，一次失败隐藏就可能造成后续无法恢复的 BlockEntity 数据损坏。

**修复方向**

- 先捕获完整快照，再执行 `setBlockState(AIR)`，由 Minecraft 状态变化流程处理旧 BlockEntity。
- 检查写入结果；非预期失败返回 `false` 并保留重试。
- 不要在可能失败的写入前主动删除 BlockEntity。
- 整体 `VisibilityTask` 的完成条件也应考虑失败位置，而不是只判断迭代器结束。

**回归验证**

- 注入 AIR 写入异常，确认原方块和 BlockEntity 都保持不变。
- 注入 `setBlockState()` 返回 `false`，确认隐藏任务继续重试。
- 对箱子、命令方块、漏斗分别验证隐藏和恢复。
- 验证区块边界、邻居更新和光照刷新不受顺序调整影响。

### BA-03 `ALL_AT_ONCE` 逐块进入动画完成后出现片段内空窗

**优先级**：P1  
**证据**：已复现

**位置**

- `src/main/java/com/director/core/animation/PerBlockAnimationTiming.java:31-36`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1136-1230`

**问题**

`ALL_AT_ONCE` 的所有方块在序列归一化时间 0 同时启动，因此 `completionLimit()` 使用片段起点而不是片段终点：

```java
return (simultaneous ? start : end) + timing.delay() + timing.duration();
```

控制器只在以下区间内执行序列采样：

```java
currentTime <= PerBlockAnimationTiming.completionLimit(config, clipStart, clipEnd)
```

而片段右侧分支要求：

```java
currentTime > clipEnd
```

当动作早于片段结束时会形成空档：

```text
completionLimit < currentTime <= clipEnd
```

此时既不在序列采样区间，也不在片段右侧分支，最终返回空集。

**复现**

- 片段：`[0s, 5s]`
- 模式：`FADE_IN`
- 并发：`ALL_AT_ONCE`
- delay：`0`
- duration：`1s`
- `completionLimit = 1s`
- `0.5s`：方块可见
- `2.0s`：方块错误消失
- `5s` 之后：才重新返回全量

**影响**

- 用户设置较长片段承载短动作时，方块会在片段中间消失。
- scrubbing、离线导出和逐帧检查都可能观察到空窗。
- 拆除方向在动作完成后保持空集是正确状态；修复时应避免把进入动画的“保持可见”语义错误套用到拆除动画。

**修复方向**

- 将序列采样上界扩展为 `max(clipEnd, completionLimit)`，让已完成的动作继续返回最终可见集；或
- 在动作已完成但尚未离开片段时，显式返回该片段的最终可见集。
- 保持片段边界与动作 tail 的语义分离：片段仍可表示触发区间，但播放不能把区间内未完成的状态误判为不存在。

**回归验证**

- 覆盖 `ALL_AT_ONCE` 的进入方向，并验证拆除方向仍正确保持最终空集。
- 检查动作开始、动作结束、`completionLimit` 前后、`clipEnd` 前后。
- 覆盖 delay 大于片段长度、duration 小于片段长度、duration 大于片段长度。
- 验证 scrubbing 跳转和连续播放结果一致。

## 6. P2 问题

### BA-04 可见性缓存会跨过合法的亚毫秒动作边界

**优先级**：P2  
**证据**：已复现

**位置**

- `src/main/java/com/director/core/animation/AnimationConstants.java:87-93`
- `src/main/java/com/director/core/animation/ClipAnimationConfig.java:186-189`
- `src/main/java/com/director/timeline/playback/block/TimelineClipBlockController.java:1043-1061`

**问题**

逐块模式允许最短动作时长为 `0.0001s`，但可见性缓存使用固定时间 epsilon：

```java
Math.abs(cache.lastCurrentTime - currentTime) < 0.001f
```

缓存键包含轨道状态、preview 状态、容器、几何 revision、序列参数和动画参数，但没有包含当前序列 cutoff，也没有保证 epsilon 小于当前动作窗口。

**复现**

- 拆除容器，`AnimationPhase.EXIT`
- `ALL_AT_ONCE`
- duration：`0.0001f`
- `0.00009s`：退出动作中，返回非空 renderBlocks
- `0.00011s`：退出动作已完成，预期空集，但缓存命中仍返回旧的非空集合

**影响**

- 极短动作、高精度时间输入和离线导出可能使用错误的可见集合。
- 普通 30/60 FPS 播放不一定触发，但模型 API 和未来高精度导出会触发。
- 缓存优化跨越了正确性边界，属于缓存键不完整，而不是序列算法错误。

**修复方向**

- 缓存签名增加 `started` / `finished` 等序列 cutoff，只有 cutoff 未变化时才复用集合。
- 播放和导出路径使用精确时间，不使用固定 1ms 等价类。
- 如果保留 epsilon，应保证其小于当前配置允许的最短有效动作窗口，而不能使用全局固定值。

**回归验证**

- 使用 `0.0001s` 拆除动作检查完成边界前后。
- 连续采样多个亚毫秒时间点，确认不会复用跨越 cutoff 的结果。
- 验证同一 cutoff 内仍能享受缓存收益。
- 覆盖 preview、播放、seek 和导出路径。

### BA-05 动态总时长遗漏 post-animation fade 的结束时间

**优先级**：P2  
**证据**：已复现 + 强代码证据

**位置**

- `src/main/java/com/director/timeline/model/TimelineModel.java:947-992`
- `src/main/java/com/director/timeline/model/TimelineClip.java:392-429`
- `src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyUi.java:284-313`
- `src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyAdapter.java:126-145`

**问题**

动态时长只取：

- 片段 `endTime`
- 逐块动作 `completionLimit()`
- 各类关键帧和音频事件时间

没有取已启用 post-animation fade 的：

```java
clip.getEndTime() + clip.getPostAnimationFadeEnd()
```

`TimelineClip` 已经通过 `getRestoreTime()` 和 `isInPostAnimationFade()` 定义了淡化结束时间，但动态时长没有使用它。

**复现**

- 用户设定总时长：`10s`
- 容器片段：`[0s, 10s]`
- 动画：`INSTANT`
- post-animation fade：启用，`start = 0`，`end = 2`
- 预期总时长：`12s`
- 实际总时长：`10s`

**影响**

- 最后一个时间线内容是带淡化的容器片段时，播放头会在淡化完成前停止。
- 用户必须手动延长总时长才能看到完整效果。
- 逐块动作 tail 较长时，动作完成和淡化完成可能进一步晚于当前动态时长。
- 淡化参数在属性面板修改后，现有适配器主要标记模型 dirty，没有统一标记 duration dirty；修复动态计算后也需要处理缓存失效。

**修复方向**

- 对启用 post-animation fade 的容器片段，将 `getRestoreTime()` 纳入动态时长。
- 若后续采纳 C-17 的 tail 时间基准，应基于有效动作结束时间计算淡化，而不是继续固定使用 `clipEnd`。
- 淡化启用状态、start、end 和动画 duration/delay 变化时，统一调用 `markDurationDirty()`。
- 保留“用户手动设定更长时长”的语义，只扩展、不随意缩短。

**回归验证**

- 最后一个片段启用/禁用 fade。
- 修改 fade start/end 后立即读取总时长。
- 覆盖逐块动作 tail 长于淡化起点的情况。
- 验证用户手动延长的总时长不会被自动计算缩短。

## 7. 与既有报告的交叉关系

以下问题此前已经记录，本轮只补充证据或关联影响，不作为新问题重复计数。

| 既有编号 | 问题 | 本轮补充 |
| --- | --- | --- |
| C-06 | 逐块拆除片段开始前可见集合为全量，但 alpha 为 0 | 探针再次复现；应在修复 BA-03 时一并检查进入/拆除方向的状态语义 |
| C-08 | 整容器 RenderView 使用任意首块的局部变换 | 本轮确认该问题影响邻居、AO、光照和 BlockEntity 显示坐标 |
| C-17 | post-animation fade 使用 clipEnd，控制器对长逐块动作使用 tail 修正 | BA-05 是动态时长侧的遗漏；两者需要统一有效结束时间 |
| C-18 | 视觉覆盖只按 `BlockPos` 索引，跨维度可能污染 | 两个结构探针分别复现 RenderEntry 覆盖和跨容器清理 |
| C-26 | 非有限数值可进入模型 | `ClipAnimationConfig.setDuration/setDelay` 和存档恢复路径同样缺少统一 finite 校验 |
| C-27 / T-16 | `duplicateShallow()` 丢失可见性关键帧 | 会影响复制后的方块动画可见性，不重复编号 |

## 8. 条件性风险与未确认事项

以下内容本轮没有足够证据升级为独立缺陷，但值得在实机验证中关注。

### 8.1 播放模型为空时的视觉状态清理

`TimelineClipBlockController` 在 `activeModel == null` 时主要重置特效并返回，没有显式清理 `BlockVisualStateManager`。如果面板或场景卸载后仍复用单例控制器，旧视觉覆盖可能遗留。需要确认实际卸载流程是否总会先清空 manager。

### 8.2 时间线目标集合的所有权契约

`showContainerBlocksForTimeline()` 文档要求调用方提交后不得修改集合，方法本身没有防御性复制。当前内部调用通常传入新建集合，因此未作为独立问题；未来若引入异步提交或复用集合，应改为不可变快照。

### 8.3 渲染与多世界集成

静态 mesh、ghost shell、RenderEntry 和 `handled` 集合存在不同的聚合粒度。当前没有发现除 C-18 之外的新高置信渲染错误，但透明排序、跨维度切换、区块重建和 BlockEntity 显示仍需实机验证。

## 9. 建议修复顺序

1. **先修复世界写入安全边界**：BA-02，避免失败隐藏破坏 BlockEntity。
2. **修复恢复成功契约**：BA-01，确保快照业务数据真正落地。
3. **修复可见性正确性**：BA-03、BA-04，保证任意合法时间点返回正确集合。
4. **统一动作、淡化和总时长**：BA-05 与 C-17 一起处理。
5. **补回归测试**：优先覆盖 BlockEntity 数据、`ALL_AT_ONCE`、亚毫秒 cutoff 和 fade 动态时长。
6. **最后做实机验证**：多世界、GPU 渲染、区块交接和性能。

## 10. 最终结论

本轮最需要优先处理的是“任务报告成功，但世界状态并未真正完成”的两个问题。它们不仅是动画表现问题，还可能破坏容器快照中的 BlockEntity 数据。播放侧最明确的新缺陷是 `ALL_AT_ONCE` 片段内空窗，已经被探针稳定复现。

建议先建立恢复/隐藏的统一成功判定，再修正时间区间和缓存签名，最后统一淡化与动态时长。这样可以用较小的改动范围消除本轮发现的核心正确性风险，同时保留现有分帧预算和预采样渲染架构。
