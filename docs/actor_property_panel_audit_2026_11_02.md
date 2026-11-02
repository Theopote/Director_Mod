# 演员属性面板状态审查

范围：演员整体、姿态工具、路径运动、演员片段、Root 与骨骼关键帧选择。结论来自当前代码调用链，未进行 Minecraft 客户端 UI 实测。本文只记录审查，不修改产品代码。

## 当前设计

属性面板先检查独立的 actorInspection，然后使用 PropertyContextService 在工具和时间线之间按最近交互仲裁。骨骼选择和关键帧选择另外存储，并未成为属性面板仲裁的目标类型。时间线自身又嵌入骨骼编辑面板和关键帧菜单。

## 已确认问题

1. **演员整体入口不一致，P1。** ActorProjectPropertyAdapter 的列表选择会调用 inspectActorObject；ActorPickController.handleClick 只调用 ActorSelectionHub.selectActor。后者把 UI 选择设为 ACTOR，但 PropertyContextService 不把 ACTOR 解析为演员对象。没有工具时进入 NONE；有工具时仍展示工具。PropertiesPanel.renderActorPanel 当前没有调用点，ACTOR 轨道还被显式转到空选择说明。结果是同一个演员从列表、世界和轨道点击可能得到三种不同内容。应把演员检查目标纳入统一仲裁，明确区分“检查演员”和“使用工具”。

2. **关键帧没有稳定的属性上下文，P1。** ActorRootTimelineRows 选择关键帧时仍将所属片段写入 uiSelection；骨骼行也选择可见所属片段，实际键存在 ActorKeyframeSelection。PropertiesPanel 未读取这些状态。键时间和插值藏在时间线右键菜单，部位数值则基于当前播放头、最后选中的骨骼，而非任意选中键集合。单键点击会移动播放头，部分掩盖差异；框选或 Ctrl 多选时差异明显。应增加单键/多键检查视图，明确资产时间、通道、插值、混合值和操作范围；不要把播放头采样值冒充选中键的原始值。

3. **只读状态失去查看能力，P1。** ActorClipInspectorGesture.begin 无法建立可写会话就返回 null，Spawn/Transform/Animation 等适配器随后提前退出。ActorRouteClipPropertyAdapter 也在会话不可用时退出。ActorClipPropertyEditSession 拒绝播放、轨道锁定、禁用等状态，因此这些情况下大量现有参数不可见。应将“读取 source”和“建立编辑 draft”分开；始终显示参数，只禁用编辑并说明具体原因。

4. **姿态工具仍有旧创建旁路，P1。** EditorToolPropertyAdapter.renderPoseAuthoring 的“创建姿态片段”按钮实际调用 createActorActionClip，创建 ActorActionClip；与手工 Pose 和骨骼资产三个概念混用。该创建函数直接向 payload 和轨道添加片段，markDirty，不通过 TimelineCommandBridge；resolveActorTrack 取首个根轨道，未沿用统一的歧义解析，也未在该入口校验播放和轨道锁定。应使用统一片段创建服务，并把文案改为明确的语义动作。

5. **路径设置分散，P2。** 演员详情提供路径库绑定，路径工具提供绘制/编辑，Route 属性另有速度、持续时间、循环、朝向与路径切换。每一块都具有作用，但用户需要理解工具状态才能找全设置。建议 Route 检查视图集中展示路径来源、运动时序、朝向、步态时钟和 Transform 冲突；世界路径工具只负责几何交互，并提供返回当前 Route 的入口。不要在“沿路径运动”状态把信息缩减为绘制参数。

6. **姿态工具信息层级过重，P2。** renderActorToolProperties 同时展开风格、预设画廊、关键帧设置、资产面板、骨骼树/数值、Dope Sheet、语义动作创建和局部覆盖。时间线还可显示另一份骨骼与 Dope Sheet。当前共享选择与草稿校验值得保留，但同时铺开不利于识别当前操作对象。建议主视图只显示当前对象和当前任务，资产、风格、创建动作及诊断放入次级折叠区。

## 推荐统一规则

显式关键帧选择优先于所属片段；显式片段选择优先于演员概览。工具为当前目标提供操作，不应仅因工具激活时间较晚而长期替换检查对象。工具自身需要参数时显示独立区块。

面板顶部固定显示演员名、轨道名、目标类型、所属片段、场景时间和资产时间；只读原因与共享资产范围就近显示。提供“返回片段”和“返回演员”导航，并明确退出关键帧焦点，避免只是更换标题而保留旧操作对象。

多选键显示数量、时间范围、通道范围和混合值。能整组修改的字段才允许编辑，冲突整组拒绝。部位多选与关键帧多选必须区别标注。

保留目前的保护：距离时钟不直接重定时、路径拥有位移时禁止冲突写入、修改共享资产前显示影响范围、上下文变化取消未应用草稿、关键帧焦点的拒绝命令不能落入片段删除。

## 建议顺序

先统一 Actor/Clip/KeySelection 的检查上下文及只读显示；再补齐单键和多键属性；随后移除旧创建旁路，最后整理路径与姿态布局。不要先做外观重排，再让各面板各自猜测编辑目标。

## 第二轮：选择切换与操作范围

7. **隐藏面板会全局取消草稿，P1。** ActorTimelineAnimationPanel.hideDetail 在折叠、剩余高度小于 100 或模型失效时调用 ActorAnimationEditingLifecycle.cancelTransientWork，清除骨骼数值草稿、Gizmo 手势及临时编辑。PropertiesPanel.onContentUnavailable/dispose 也调用该函数。两个面板共享草稿，但任意一个隐藏都会影响另一个。建议记录交互来源，隐藏只取消自身交互，编辑对象真正失效才全局取消。窗口折叠、分栏拖动和停靠切换仍需客户端实测。

8. **实体注视流程不闭合，P2。** ActorLiveTransformPropertyUI 提供 ENTITY_UUID 模式，却没有实体选择器或跳转按钮。模式切换立即提交；ActorNumericEditSession.updateLookTarget 在没有目标 UUID 时抛错，要求先去片段属性指定。切换为世界点或相机还会清除 UUID，再切回来也会报错。应在当前面板提供目标选择，或明确导航到对应注视片段。

9. **Root 删除范围与通道选择不同，P2。** ActorRootKeyframePropertyUI 的删除依据当前播放头；ActorRootKeyframeEditing.remove 删除整个键，包含全部 TRS 通道，不读取时间轴所选通道。它符合“删除当前时间关键帧”的现有文案，不能视为数据错误，但与子行选择并存容易误解。建议明确“删除此时刻全部整体通道”，另提供“删除所选通道”，并显示受影响范围。

10. **旧关键帧适配器不能直接复用，P1，入口受限。** PropertyAdapterBootstrap 仍注册 EntityClipKeyframePropertyAdapter。其 TRS 控件向 ImGui 传临时 float[]，随后从未更新的 ImFloat 读回，拖动值没有正确写入；时间和插值直接修改源对象，没有新服务的撤销、锁定和冲突保护。提示称相对时间，当前 Root 服务却按场景时间处理。Root 子行目前选择所属片段，不能据此声称普通单击必然触发该问题；但统一关键帧检查上下文时必须替换该适配器，不能只接入所选键。

11. **整体点击与部位选择意图未分开，P2。** ActorSelectionHub.selectActor 保留同一演员轨道上的片段引用；骨骼选择仍 active 时继续同步骨骼并清除整体 Gizmo。世界点击也用此入口，因此重新点击演员身体并不明确返回整体编辑。建议区分检查演员资产、选择整体和选择骨骼：整体选择清除部位与键焦点，骨骼选择保留动画引用，检查资产不擅自改变引用。

本轮仍是代码审查，未执行客户端交互复现。已排除一项误判：整体变换面板在无活动手势时每帧重新采样，不能声称撤销后必然显示旧值。
