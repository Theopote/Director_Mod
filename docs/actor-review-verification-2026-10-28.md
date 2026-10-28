# 演员系统复审报告核验

核验日期：2026-10-28。当前 HEAD 为 `65c4ca7f351996d1810a59feb0a51004e6c5eed4`，与报告基线一致。核对了下载的 Markdown 报告及粘贴版本；两者主要判断一致。报告中的“建议推进顺序”只作为待核对的建议，没有据此修改业务代码。

结论：报告发现了一批真实的性能改进点、国际化与交互问题，也混入了功能规划项和数项明确误判。不能按它的 P0/P1 列表直接实施，尤其不能直接暴露可变姿态、删除生命周期早退、删除已有测试所用重载，或改变整层原子拒绝规则。

## 一、24 项新问题逐项判断

| 编号 | 判断 | 代码核验与修正 |
| --- | --- | --- |
| N1 防御性拷贝 | **现象成立，数量与严重性夸大，首个修复建议不安全** | Layer 构造器与访问器均拷贝，ActorPoseSources.layer 又调用访问器，确有可省的内部副本。但无预设/手工层的帧不会必然产生所称 4～6 次 Layer 拷贝；RigPose.copy 是变换引用数组、一个 BitSet 和 int[] 的复制，不是“两个 BitSet”，也不逐个深拷贝不可变 BoneTransform。直接让公开 pose() 返回内部可变 RigPose 会破坏快照契约和现有 layerSnapshotsAreFrozenAndZeroEnvelopeDoesNotClaimOwnership 测试。可参考 PoseLayer 的内部只读快照入口，保留公开防御拷贝。没有实测依据将其定为 P0。 |
| N2 目录每帧构建 | **成立，性能影响需要测量** | ActorAnimationCatalog.entries/filter 每次重新遍历、分配条目与结果，打开相关 UI 时逐帧调用；AnimationAssetLibrary.all 自身也复制列表。不过 sameLayout 对同一 rig 对象先返回 true，所以不是每个资产必然逐骨比较。缓存有价值，但必须覆盖重命名、用途修改、导入、撤销及骨架布局变化；仅增删时更新 libraryRevision 不够，资产的 name/purpose 可原地修改。 |
| N3 临时采样器 | **成立一部分，建议不充分** | UI 确有 new EntityClipSampler 后立即 sampleSnapshot；源码目前可搜到 13 个 new 点，不是报告列的 11 个，其中 Runtime 主采样器在 TrackRuntime 构造时创建一次，并非全部逐帧创建。分类列表是在 sampleAt 内部创建，缓存 sampler 实例本身不会消除每次采样的这些分配。snapshot 不消耗事件缓存，Runtime 已有常驻 sampler；没有证据表明这里重现 Action 重复触发。WeakHashMap<ActorTrack,EntityClipSampler> 的值又强持有 track，会使弱键失去预期回收作用，不能照搬。可优先让 UI 复用有效只读帧结果，再测分类/分配成本。此次 git 历史检索也没有查到该文件的 forEditing 定义，不应把“恢复旧接口”当作已验证历史事实。 |
| N4 单预设槽 | **限制确实存在，属于新功能需求** | 每个 Spawn 一个 ActorPresetBinding，采样选择一个预设。无法独立混合两个用户预设资产。但已有“程序化基础步态＋上半身预设＋手工层”，不能笼统说上下身动作无法共存。是否增加多槽位需要产品与持久化设计，不构成已证明的 P0 故障。 |
| N5 领域层中文 | **成立，属于分层/国际化问题** | ActorPoseStage、ActorPoseSources、ActorPoseFeedback 等含展示串。宜逐步换成结构化状态与 UI 翻译；不是无法运行的 P0。ActorPoseStage 已有 State 枚举，并非完全没有结构化状态。 |
| N6 中英混杂 | **成立** | Motion.capture 把 action 转字符串，loop 用 name，面板直接拼接。保持枚举并在 UI 翻译更合理。 |
| N7 时间标签 | **显示事实成立，误读属于交互风险** | 片段名旁边显示的是播放头绝对时间，不是片段时长。可明确标为播放头并补起止区间；不能称时间计算错误。 |
| N8 面板重复判断活动 Spawn | **重复逻辑成立，但错误后果未证实** | 面板和 sampler 都遍历当前覆盖片段，取最后一个未静音 Spawn，两者在此规则上相同。面板之后调用 Runtime.poseFeedback；该入口会过滤无效运行状态、失效时间和修订，禁用/REMOVE 并不会因此必然显示有效旧快照。统一查询入口是维护性改进，不足以证明三套规则已经产生错误显示。 |
| N9 查看设置不定位片段 | **可用性建议，不是已证实的错误** | QUICK/MANUAL 没有直接重新选片段，但会 selectActor；该方法保留同演员已选片段，必要时选择轨道。面板入口本来要求当前 source 在播放头区间且正在生效，并非每次导航都需要移动播放头。可统一导航，但“只切工具”描述不完整。 |
| N10 focusTarget 生命周期 | **成立** | 静态强引用仅在恰好渲染该 source 时消费；不存在统一选中变化清理。一次未消费请求可跨选择保留，后续再渲染时自动展开。需界定请求有效期/上下文，不能仅把一项全局强引用叫作无限内存泄漏。 |
| N11 切演员保留资产选择 | **前半成立，绑定错误的后果不成立** | 状态只按 ownerModel 重置，跨演员保留 selected 的确存在。不过面板 :116 调 previewPresetAttachment，里面检查 rig ID 与布局；:124 在 preview==null 时禁用应用按钮。applyPreset 内再次调用同一验证。不会按报告所说直接绑入不兼容资产；可改进兼容提示和跨演员筛选策略。 |
| N12 两条腿部提示 | **可同时出现成立，修复后的描述错误** | STATIC_POSE＋路径时会显示第一条；有腿旋转且本地时钟时也会显示第二条。统一表达有价值。但“改为仅上半身”不会消掉第一条：第一条判断根本不检查骨骼范围，仍由用途与路径决定。 |
| N13 非人形无程序化步态 | **对进入 RigPoseEvaluator 的非人形自定义 rig 成立，泛化到所有四足不成立** | evaluate 对非人形 rig 从 rest 开始，缺少任意自定义骨架的步态映射。但内置 COW/SHEEP 的 resolveRig 返回空，一般走 ActorRuntimeController :895～927 的旧 Pose 分支，保留 rigPose.pose；不能据此认定内置四足沿路径全部不摆腿。自定义 rig 的具体视觉结果仍需实测或专门测试。 |
| N14 部分通道失败却整层拒绝 | **不成立** | RigPoseComposer :28～32 先验证整层，:33～46 先构建 edits，成功后才统一提交。非法缩放、骨架不匹配、非法变换均不会先写 90 个通道再拒 1 个。已有 invalidAdditiveScaleRejectsTheWholeLayerBeforeWritingOtherBones 和 overflowingAdditiveValuesCannotPartiallyApplyALayer 回归。整层 REJECTED 与当前原子契约一致。改为部分接受是另一个设计，不是修正现状。 |
| N15 新 UI 中文硬编码 | **成立** | 新预设、协调和资产界面存在大量未进入语言表的文案，与既有翻译入口混用。 |
| N16 骨骼环导致死循环 | **不成立** | RigDefinition :42 拒绝自引用，:45～59 对所有骨骼沿父链检查环；骨架定义不可变。已有 rejectsCyclesAndDuplicateIds 测试。正常构造/导入的骨架无法把环送进 PoseScope.descendant。 |
| N17 死重载与顺序表 | **部分成立** | 四参数 resolve 生产调用少，但测试源码有多处调用，并非“全库无调用”；直接删除会使测试编译失败。每次构建固定 List.of 顺序表是可整理的小项，不是高影响故障。 |
| N18 手工换行脆弱 | **尚未证实的 UI 建议** | 代码每帧读取 available、FramePadding、ItemSpacing 后计算宽度，并不是忽略样式变化。报告没有实际窄面板或 imgui-java 版本复现。“单轴方法语义不一致”没有证据，需先视觉复测；固定等宽并不自动更正确。 |
| N19 缩进 | **成立，仅格式问题** | EditorToolPropertyAdapter 的 treeNode 内容缩进不一致，括号逻辑正常。 |
| N20 优先级与执行顺序 | **所称矛盾不成立，可改进说明** | 合成顺序为 PRESET→QUICK→MANUAL→CONTACT，显示反序是正常的后处理/覆盖优先级展示。最后执行并不意味着优先级最低。CONTACT 本身受作者权重约束，面板也说明不等于独占；可更明确说明“显示顺序与执行顺序相反”。 |
| N21 缺 tier | **事实成立，属于设计选择** | Entry 没有 tier，只有用途；L0/L1/L2 不是代码必须实现的正确性条件。项目采用文档也明确不按全局 tier 隐藏用户步态资产。 |
| N22 没有性能阈值断言 | **不成立** | DenseBlockPositionPerformanceTest 有两个测试，其中 :26 已有 assertTimeout(Duration.ofSeconds(5))，覆盖 393216 个密集位置的构建与全量查询；另有 SequenceVisibility 的密集坐标回归。不能说只有输出时间、无阈值守护。CI 是否调用测试属于流水线问题，报告没有提供证据。 |
| N23 空结果 child | **成立，低影响 UI 问题** | 空结果提示之后仍创建最小 36px 的边框 child。可以收紧空间，不是渲染/数据故障。 |
| N24 必然读取过期帧 | **不成立** | 面板不是直接取字段，而是调用 ActorRuntimeController.poseFeedback；该入口 :139 已要求 feedback.matches(currentTime, actorEditContextRevision)，不匹配返回 null。面板 null 分支已有等待刷新提示。gesture.begin 在后不会绕过此校验；Navigation deferred 注释说的是导航提交时机，不能作为旧快照可接受的证据。 |

## 二、上轮复核表需要纠正的重点

### 1. “存在性双真值源仍未闭合”的核心论证不成立

ActorTrack.isPresentAt :302 明确要求当前时间不超过 Spawn.endTime。它的语义是“处于 Spawn 生命周期窗口，且未被 REMOVE 结束”，不是“曾经 Spawn 过，之后永久存在直到 REMOVE”。

若 isPresentAt(time) 为真，则当前至少有一个未静音 Spawn 覆盖该时间，因此 findClipsAt(time) 不可能为空。无覆盖片段时 sampler 早退，与当前存在区间语义并不矛盾。已有 outsideLifecycleIsInactiveEvenWithLongerActionClip 明确断言 Spawn 窗口之后不再存在，即使较长的姿态片段仍覆盖该时间。

如果产品要改成入场事件持续存在，那应明确变更生命周期契约及相关测试，不能仅删除早退当作修 bug。仅删早退也不会改变 isPresentAt 对 Spawn.endTime 的限制。

### 2. 放置入口并不只有 addDefaultSpawnClip

ActorPlacementController :343 直接创建默认五秒 Spawn；ActorGroupTimelineCommandService :212 也直接创建五秒 Spawn。addDefaultSpawnClip 不是唯一放置入口。报告把该项标未验证是谨慎的，但不应把此便利方法的改动等同于所有放置流程的变化。

### 3. 旧 0.01 秒存档缺少专用迁移：观察基本正确，是否错误取决于兼容承诺

TimelineModelPersistence :1745 按 DTO 原始 startTime/endTime 构建 Spawn，未找到对历史 0.01 秒默认片段的特例扩展，因此旧片段仍保留原时长。不能不分来源地把所有短片段强制扩成五秒，那也可能覆盖用户刻意制作的短生命周期。需要文件版本或默认片段来源信息支撑迁移策略。

### 4. “DefaultAnimationPlayer 的第二渲染路径”命名不准确

确实保留 RigPose 路径和旧 Pose 兼容路径，后者目前也服务于不具备骨架资产绑定的内置物种。ActorRuntimeController.getAnimationPose 实际调用的是 AnimationDataSampler.sample；DefaultAnimationPlayer 全库只搜到类自身，没有调用点。不能据此断言在执行第二个有状态 DefaultAnimationPlayer，更不能在未替代四足等行为前删除兼容路径。

### 5. 正确的已修复项与未实现项

Purpose 非法值容错、PoseScope 下沉、范围匹配不创建替换集合、ADDITIVE 标签改进、腿部风险的一键上半身修正，这些判断基本正确。“必然滑步”的旧表述本身不应恢复。

搜索/用途/来源筛选和协调阶段文本已经实现；ActorStudio、悬停试播、专门层栈 HUD、模板库、时间线层带尚未完整实现。这些属于项目规划缺口。PanelId 实际有十多个面板标识，不是报告所称只有四个通用容器，但的确没有 ActorStudio。

## 三、功能缺口与优先级

- 单预设槽、空白资产新建、缺少显式 FIT_SPEED 模式以及专用 HUD/层带/试播/模板库，作为“尚未实现的能力”大体正确；不能直接评成已承诺功能的故障。
- Director 的 fabric.mod.json 本就将产品描述为 single-player cinematic toolkit。没有演员多人同步支持是当前产品边界，不能单凭搜索不到 ServerPlayNetworking 就提升成应优先修复的错误；是否扩展多人需要另立目标。
- ONCE 路径按片段长度反算速度属当前明确语义，显式 FIT_SPEED 是可选的新模式。距离时钟禁用的是预设速度，不应与所有路径循环模式的移动速度控制混淆。
- 不建议采纳整份 P0 排序：报告没有性能采样或游戏复现来证明 N1～N3 的严重程度，N4 是新能力，N5 是架构/国际化整理。应先确认用户可见故障，再基于测量处理逐帧采样/目录重建，随后整理交互、文案与功能规划。

## 四、验证记录

运行了现有 58 项相关测试，全部通过，无失败、错误或跳过，覆盖生命周期、事件采样、骨架校验、整层原子拒绝、姿态快照、来源反馈、目录筛选、资产生命周期、预设范围/路径提示及密集坐标性能回归。

这不等于所有 UI 交互已经游戏内验收，也不证明没有其他缺陷。此次没有新写测试、没有修改业务代码，也没有执行报告中的修复建议；只新增本核验文档。
