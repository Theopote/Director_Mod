# Director 时间线逻辑审查

日期：2026-10-13。范围：时间线模型、播放推进、播放头与片段交互、布局与吸附、命令历史、剪贴板、持久化、音频播放与音频导出。

结论：发现 13 项可定位的问题，4 项 P1、9 项 P2。12 项通过当前编译产物的最小 Java 复现验证；Esc 取消问题通过完整调用链核对确认，尚未做游戏内 UI 复现。P1 表示可能造成数据丢失或工程污染；P2 表示可触发的操作、播放或同步异常。本次只审查，没有修改业务源码。

## 1. [P1] 新建工程、切换世界后仍保留旧工程撤销历史

位置：[TimelinePanel.java:523](D:/development/DC/director/src/main/java/com/director/ui/panels/TimelinePanel.java:523)、[TimelinePanel.java:534](D:/development/DC/director/src/main/java/com/director/ui/panels/TimelinePanel.java:534)。

`resetWorkspace()` 和 `reloadForCurrentWorld()` 都复用同一个模型与命令历史，没有清空 undo/redo。旧命令继续持有旧轨道、片段及注册表数据，新世界执行撤销可能把旧工程对象重新加入当前模型，随后自动保存。

复现：在工程 A 删除一条轨道 → 新建空工程或切到工程 B → 撤销。隔离复现调用实际内存重置方法后执行旧删除命令的 undo，得到 `tracks=[old-world]`，预期为空。

建议：工程切换前取消所有临时编辑，清空历史、剪贴板和工程相关选择；已提交命令不跨工程复用。

## 2. [P1] 删除音频片段后撤销，声音事件没有恢复

位置：[DeleteClipCommand.java:70](D:/development/DC/director/src/main/java/com/director/ui/timeline/commands/DeleteClipCommand.java:70)、[DeleteClipCommand.java:112](D:/development/DC/director/src/main/java/com/director/ui/timeline/commands/DeleteClipCommand.java:112)、[TimelineAudioService.java:1282](D:/development/DC/director/src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:1282)。

删除时 `onClipRemoved()` 移除关联事件和链接；undo 只恢复片段，没有保存或恢复事件快照。对于解绑后独立编辑的音频片段，重新 addClip 不会重建声音事件，因此撤销后画面恢复，声音数据仍丢失。容器轨道删除同样存在未完整恢复关联音频状态的风险。

复现结果：带一个关联事件的独立音频片段，execute 删除后 undo，`clips=1, events=0`；预期事件仍为 1。

建议：删除命令把片段、关联音频片段、事件、轨道归属和链接作为同一事务，undo 恢复原对象及用户编辑值，而非仅重新生成。

## 3. [P1] 演员片段复制/剪切退化为普通片段，实际表演数据丢失

位置：[DuplicateClipCommand.java:45](D:/development/DC/director/src/main/java/com/director/ui/timeline/commands/DuplicateClipCommand.java:45)、[ClipboardManager.java:46](D:/development/DC/director/src/main/java/com/director/ui/timeline/state/ClipboardManager.java:46)、[ClipboardManager.java:64](D:/development/DC/director/src/main/java/com/director/ui/timeline/state/ClipboardManager.java:64)。

这些入口统一使用 `new TimelineClip(source)`，没有复制 EntityTimelineClip 对应的 EntityClip，也没有把副本加入 ActorTrack payload。剪切还会先删除原来的真实 EntityClip，使粘贴只剩一个空壳。复制 AudioTimelineClip 也会丢失子类型，绑定的音频副本可能因被模型误认为容器片段而遭拒绝。

复现：复制演员 Transform 片段，得到 `class=TimelineClip, uiClipCount=2, payloadClipCount=1`。副本不参与实际演员采样；Actor payload 恢复时还会重建 UI wrappers，因此该空壳不能可靠恢复。

建议：按片段类型做语义复制，完整复制 payload、关键帧与音频事件，更新所属轨道，并纳入撤销命令；未支持的类型暂时禁用复制/剪切入口。

## 4. [P1] Esc 取消只清交互标志，没有回滚数据或结束 scrubbing

位置：[TimelinePanel.java:319](D:/development/DC/director/src/main/java/com/director/ui/panels/TimelinePanel.java:319)、[TimelineInteractionHandler.java:324](D:/development/DC/director/src/main/java/com/director/ui/timeline/interaction/TimelineInteractionHandler.java:324)。

快捷键调用 `TimelinePanel.cancelInteraction()`，该方法仅取消演员关键帧交互并调用 InteractionManager.cancelInteraction。普通片段拖拽/拉伸已经实时改写真实模型；清空 handle 后失去回滚信息，修改也没有进入命令历史。播放头拖动时 `model.scrubbing` 不会清除，相机仍可持续被时间线接管，音频预览和吸附辅助线也可能残留。

复现步骤：拖动普通片段但不松开鼠标，按 Esc；或者拖动播放头时按 Esc。静态调用链确认上述清理缺失；尚未在运行中的游戏内操作验证。

建议：Esc 使用统一的取消事务路径，回滚片段时间/轨道和关联事件，结束 scrubbing、清理预览与辅助线，再丢弃 handle。

## 5. [P2] 跨轨道拖动没有记录轨道变化，撤销不完整

位置：[TimelineClipDragHandler.java:235](D:/development/DC/director/src/main/java/com/director/ui/timeline/interaction/TimelineClipDragHandler.java:235)、[TimelinePanel.java:1354](D:/development/DC/director/src/main/java/com/director/ui/panels/TimelinePanel.java:1354)、[MoveClipCommand.java:107](D:/development/DC/director/src/main/java/com/director/ui/timeline/commands/MoveClipCommand.java:107)。

预览过程中实际 remove/add 到新轨道，提交回调却只传入旧/新时间。仅纵向换轨且时间相同，不会生成任何命令；同时改时间时，undo 只恢复时间，仍留在新轨道。音频事件在预览中也已换轨，取消路径没有把事件一同搬回。

复现：从 original 轨道移入 target 并移动时间，执行实际 MoveClipCommand 后 undo，`owner=target`，预期 original。

建议：MoveClip 命令记录原/目标轨道及关联事件归属；只换轨也算有效变更，取消与 undo 恢复整个事务。

## 6. [P2] 删除音频轨道绕过模型通知，残留采样器继续发声

位置：[DeleteTrackCommand.java:78](D:/development/DC/director/src/main/java/com/director/ui/timeline/commands/DeleteTrackCommand.java:78)、[TimelineAudioController.java:101](D:/development/DC/director/src/main/java/com/director/ui/timeline/playback/TimelineAudioController.java:101)。

DeleteTrackCommand 直接修改 `trackList`，没有调用 model.removeTrack/addTrack。BaseTimelineCommand 的 markDirty 只发通用变更通知，音频控制器依赖专用 TrackPlaybackListener 重建采样器，没有订阅该通用通知。因此轨道已经从 UI 删除，samplers 映射仍引用原轨道，update 继续采样其事件。

复现结果：删除后 `modelTrackCount=0, samplerCount=1`。

建议：结构变更统一走模型 API，同时允许按指定索引恢复轨道；恢复/重载也应统一刷新音频控制器。

## 7. [P2] 非原子时间更新使粘贴、容器拖入及关联音频范围错误

位置：[ClipboardManager.java:88](D:/development/DC/director/src/main/java/com/director/ui/timeline/state/ClipboardManager.java:88)、[TimelineDragAndDropHandler.java:187](D:/development/DC/director/src/main/java/com/director/ui/timeline/interaction/TimelineDragAndDropHandler.java:187)、[TimelineAudioService.java:1090](D:/development/DC/director/src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:1090)、[TimelineAudioService.java:1108](D:/development/DC/director/src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:1108)。

这些路径先 setStartTime 再 setEndTime；setStartTime 会用旧 end 限制起点。粘贴还在改起点后才读 getLength，已把原长度改变。

复现结果：原 `[0,4]` 粘贴到 1 秒得到 `[1,4]`、长度 3，预期 `[1,5]`；容器/关联音频后移到 `[5,9]` 得到起点 3.95，预期 5。大幅右移会改变时长或造成声音不同步。

建议：先保存原长度，再使用原子 setTimeRange；涉及内部关键帧时显式平移/重映射，并在命令中保存完整旧状态。

## 8. [P2] 就近放置算法可能在非法位置循环，最后仍返回重叠结果

位置：[TimelineClipLayoutResolver.java:80](D:/development/DC/director/src/main/java/com/director/timeline/layout/TimelineClipLayoutResolver.java:80)、[TimelineClipLayoutResolver.java:91](D:/development/DC/director/src/main/java/com/director/timeline/layout/TimelineClipLayoutResolver.java:91)。

算法把“阻挡片段之前”的候选起点 clamp 到 0，却没有验证该候选是否真的容得下当前片段。无效候选离 desired 更近时不断重复选择，64 次循环后直接返回仍重叠的位置。同一 resolver 用于普通片段及相机路径片段。

复现：占用 `[0,5]`，插入长度 2、候选起点 1，返回 `start=0, overlaps=true`，合法结果应为 5。

建议：枚举真实可用间隙或验证每个候选；迭代耗尽后绝不能把非法位置当成成功结果。

## 9. [P2] 手动总时长未序列化，播放出点恢复时被截断

位置：[TimelineModelPersistence.java:124](D:/development/DC/director/src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:124)、[TimelineModelPersistence.java:520](D:/development/DC/director/src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:520)、[TimelineModelPersistence.java:1630](D:/development/DC/director/src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java:1630)。

TimelineDTO 没有 duration 字段，toDTO/fromDTO 都没有保存恢复用户设定的总时长。重建模型退回默认 20 秒或内容计算出的尾部；恢复 in/out 时又按这个错误时长 clamp。

复现：空工程设 duration=60、outPoint=40，DTO 往返后 `duration=20, out=20`。

建议：保存用户设定时长，与动态内容时长区分；先恢复时长再恢复范围，并处理旧版本缺失字段。

## 10. [P2] 音频循环及暂停跳转会补触发不该播放的整段事件

位置：[AudioTrackSampler.java:112](D:/development/DC/director/src/main/java/com/director/ui/timeline/playback/audio/AudioTrackSampler.java:112)、[AudioTrackSampler.java:159](D:/development/DC/director/src/main/java/com/director/ui/timeline/playback/audio/AudioTrackSampler.java:159)、[TimelineAudioController.java:93](D:/development/DC/director/src/main/java/com/director/ui/timeline/playback/TimelineAudioController.java:93)、[PlaybackOrchestrator.java:147](D:/development/DC/director/src/main/java/com/director/ui/timeline/playback/PlaybackOrchestrator.java:147)。

循环回入点只降低 currentTime，没有通知采样器重置，采样器按倒放触发 `[新时间,旧时间)` 的全部事件。暂停时控制器直接 return，不更新 lastTime；暂停期间 seek 后恢复播放，会跨过并补触发被跳过的声音。

复现使用 AudioEvent 子类记录 isEnabled 调用并拒绝实际发声：0→4.9→0 后，1 秒和 3 秒事件各触发两次，第二次均发生在循环回零瞬间；1 秒暂停并跳到 8 秒后恢复，2 秒与 4 秒事件均被错误触发。

建议：区分正常前进、seek 与 loop；seek 更新基准不补播，loop 分别处理出点尾窗/入点头窗，控制器通知采样器发生了时钟跳变。

## 11. [P2] 多音频轨道下，移动/拉伸只更新时间线第一条音频轨道

位置：[TimelineAudioService.java:493](D:/development/DC/director/src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:493)、[TimelineAudioService.java:556](D:/development/DC/director/src/main/java/com/director/ui/timeline/audio/TimelineAudioService.java:556)。

事件可以通过 moveLinkedEventsToTrack 搬到其他音频轨道，但时间更新和预览仍调用 findAudioTrack，只在第一条轨道按 ID 查找。位于第二条轨道的关联声音没有随片段移动或拉伸。

复现：A、B 两条音频轨道，B 上 `[0,4]` 的音频片段关联 2 秒事件，使用实际 MoveClipCommand 后移到 `[5,9]`，事件仍为 2 秒，预期 7 秒。

建议：维护事件所属轨道索引，或搜索所有音频轨道；所有链接事件操作统一使用同一查询入口。

## 12. [P2] 关键帧/片段吸附遗漏大部分现代时间线数据

位置：[TimelineSnapping.java:133](D:/development/DC/director/src/main/java/com/director/timeline/snapping/TimelineSnapping.java:133)、[TimelineSnapping.java:235](D:/development/DC/director/src/main/java/com/director/timeline/snapping/TimelineSnapping.java:235)。

候选只来自 TimelineClip 边缘与 legacy Float keyframes，没有纳入轨道级相机/天气/时间/季节关键帧、相机路径边缘与关键帧，以及对象形式音量关键帧等。界面开启“关键帧吸附”时，这些可见关键帧实际无法作为吸附目标。

复现：相机轨道关键帧在 3 秒，关闭网格/边缘吸附，仅开关键帧吸附，在默认阈值内的 3.03 秒仍返回 3.03，预期 3。

建议：从各类型模型收集统一候选，并排除正在拖动的对象及自身关键帧；统一预览与正式数据的时间语义。

## 13. [P2] 音频导出没有遵守实时播放的 Solo 与音量包络

位置：[TimelineAudioExporter.java:188](D:/development/DC/director/src/main/java/com/director/export/video/TimelineAudioExporter.java:188)、[TimelineAudioExporter.java:208](D:/development/DC/director/src/main/java/com/director/export/video/TimelineAudioExporter.java:208)。

实时控制器按 Solo 筛选，采样器应用 TimelineAudioService.getVolumeMultiplierForEvent；导出事件收集只检查 muted，并直接使用 event.volume。导出视频的声音会与预览不同，其他轨道仍可发声，音量关键帧产生的静音/淡入淡出也被忽略。visible/locked 的预览筛选同样未共享。

复现调用真实导出器的事件收集方法：A 为 Solo、B 非 Solo，各一个事件，导出仍收集 2 个，预期 1；A 的音量包络为 0，实时 multiplier=0，但导出事件 volume=1。

建议：实时与离线导出共享轨道选择和事件增益计算规则；明确锁定仅禁止编辑，不应无意改变声音输出。

## 验证结果与边界

执行 `gradlew.bat test --offline --console=plain`：691 项，688 通过、3 失败。完整报告：[Gradle 测试报告](D:/development/DC/director/build/reports/tests/test/index.html)。

已有失败：

- ActorMotionSamplingTest.explicitIdleWinsUntilMutedOrOutsideItsInterval：预期 RUN，实际 WALK；测试速度为 1，当前实现 RUN_THRESHOLD=4，该断言与现规则不一致，不能直接判为播放故障。
- ActorMotionSamplingTest.loopingRouteHasNoTeleportSpeedAndPingPongFacesTravelDirection：最终 ONCE 断言预期速度 0，实际 0.2；当前 ONCE 会把 4 单位路径铺满 20 秒片段，因此第 3 秒仍在移动。需要确认产品规则并同步测试，不应直接按旧断言修实现。
- ActorSkinStorageTest.importSkinWritesIntoSkinsDirectory：皮肤导入返回 null；与本次时间线主体审查无直接关系，未在本次修复。

本次隔离复现：[TimelineAudit.java](D:/development/DC/director/build/tmp/timeline-audit/TimelineAudit.java)、[实际输出](D:/development/DC/director/build/tmp/timeline-audit/results.txt)。使用现有测试 classpath 与当前编译产物，探针位于 build 临时目录，未加入正式测试集。反射仅用于访问私有快照、链接设置和内部事件收集，不替换业务实现。

未进行运行中 Minecraft 客户端的鼠标操作、真实声音输出、视频编码或大容器压力测试，因此没有把这些验证称为游戏内端到端测试。复现过程中没有载入真实世界，也没有保存用户存档。唯一新增的受版本管理可见文件是本报告。

建议修复次序：先修工程历史隔离、删除撤销的数据快照、片段语义复制与 Esc 取消；随后统一移动事务和音频事件时钟；最后补齐时长持久化、布局合法性、吸附及导出一致性，并把上述最小复现转为正式回归测试。


## 修复进展（2026-10-13）

已修改本次审查定位的逻辑：

- 工程生命周期：重置时清理撤销/重做、剪贴板和临时交互；模型重置通知音频控制器清除旧采样器。
- 删除与撤销：保存并恢复关联音频片段、事件对象、链接、方块时序和生成签名；恢复旧版关键帧插值。删除不再按时间范围误删独立声音事件。
- 复制与粘贴：保留演员片段子类型、关键帧和类型专属参数，同步演员 payload；音频复制保留事件和音量自动化；粘贴支持撤销/重做并保留长度，拒绝锁定或不兼容轨道。
- 拖动：跨轨道及纯纵向移动进入撤销历史；取消会还原轨道、时间范围、事件归属和预览状态。保存前取消未提交交互。
- 音频同步：原子更新范围，按音频片段实际旧范围重映射音量关键帧；同步和加载重建覆盖所有音频轨道。
- 时间线布局：按合法空隙选择最近起点，消除夹在多个阻挡片段间循环或返回重叠位置的问题。
- 持久化：保存时长并在恢复 In/Out 前加载，兼容旧文件；时长修改标记保存，加载保留原有持久化抑制设置。
- 播放：区分编辑器跳转和正常推进，暂停跳转不补播跨过的事件；循环分段采样并保留超出终点的时间，停止前采样末尾声音。
- 吸附与导出：补全自动化、路径和对象关键帧候选，排除当前拖动物；实时播放与导出共享可见、静音、独奏规则和事件音量包络，锁定只限制编辑。

新增正式回归测试：`src/test/java/com/director/timeline/TimelineRegressionTest.java`，29 项全部通过。

最终执行 `gradlew.bat test --offline --console=plain`：720 项，717 通过，3 项失败。失败仍为上述审查前已有的两个 ActorMotionSamplingTest 断言和一个 ActorSkinStorageTest，未修改这些测试或相关产品规则。编译通过，`git diff --check` 通过。

验证范围仍为离线 Java 回归测试；未进行运行中 Minecraft 的真实鼠标操作、真实声音播放或视频编码验证。改动未提交。
