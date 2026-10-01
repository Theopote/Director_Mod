# Actor 动画完善记录

> 开发主规格：[演员系统开发规格与实施路线](actor_system_development_spec.md)（2026-10-01）。本文件记录实际完成证据和剩余问题，目标设计不视为已完成。

## 2026-09-28：地面路径与短跳

### 使用方式

- 路径工具创建的路径片段现在按地面移动播放。现有类型统一使用保守的地面规则，自定义演员也暂不自动认定为可飞行。
- 锚点控制行进方向，播放时根据方块碰撞形状投影到地面；路径线仍显示原始规划曲线。抬高锚点不会让演员飞行。新建/编辑路径时的默认时长按水平曲线长度计算。
- 路径片段属性新增“跳跃高度”“起跳时间（片段内）”“腾空时长”。高度为 0 时关闭；上限为 1.25 格、0.8 秒，每片段只跳一次，不因路径循环无限重复。
- 地形编辑或区块加载完成后，点击路径属性中的“重新检测地形”。

### 约束与边界

- 根据实体足部范围与身体空间检测支撑、台阶和低矮顶棚。每步最多上升 0.6 格、下降 1.25 格；不可达处截断路线并停止，不自动绕路。
- 起点最多向下查找 64 格。找不到有效起点时暂不显示演员，并在属性中提示；途中当前支撑消失或区块未加载时同样停止显示，等待重新检测。
- 短跳是有效地面路径上的有界动作，不能用于跨沟或翻越高墙；上方空间不足时保持地面位置。尚未包含起跳/落地肢体姿态、按物种配置的能力表、飞行或游泳模式。
- 约束只作用于生效的 Route。现有 Transform 片段仍按原规则覆盖 Route；演员关系与手工骨骼 Root 位移仍是后续层。
- 地形路线按几何修订缓存，随机跳转使用同一条已编译路线；正常采样仍检查当前位置的支撑与空间。最多缓存 8192 个采样点，超长路径提示拆分。
- 这是对旧路径行为的有意调整：已有悬空的三维路径也会按地面规则播放。跳跃新字段缺失时默认关闭，工程格式保持兼容。

### 编辑与验证

- 路径锚点拖动时只修改草稿，松手后再更新路径库/片段与存档，避免每次鼠标移动触发保存和地形重新编译。
- 跳跃参数已接入保存/加载与演员组路径复制。修复 waypoint 自赋值清空列表、非有限坐标与速度输入。
- 新增 8 项测试，覆盖贴地、台阶/断路、空间不足、循环与回退采样、跳跃边界、存档往返和旧工程默认值；演员组复制断言也已扩展。
- 全量 139 项测试：137 通过，仍是下述两项皮肤/存档环境相关测试失败。尚未进行游戏内地形与渲染验收；需检查平地、半砖、楼梯、窄通道、悬崖及修改地形后的重新检测。

## 2026-09-28：演员 Gizmo 拖动响应

- 演员 Transform Gizmo 改为在 Director 的 ImGui 帧中更新鼠标交互，避免只在约 20 Hz 的游戏 tick 中处理拖动。其他 Gizmo 暂保留原调度；演员交互不会同时走两个更新入口。
- 拖动过程中保留实时预览，移除逐次 `markDirty()` 及新建拖动片段时的立即保存；松手通过原有单次撤销命令通知和保存。
- 鼠标离开有效视口后松手仍结束拖动；切换或清除演员选择时提交进行中的编辑。锁定轨道拒绝启动编辑。
- 吸附改为对累计位移取整，避免高帧率、慢速拖动时每个小增量都被舍弃；无吸附时保留细微移动。
- 拖动期间停止 ActorRelation、LookAt 和 Root 动画位移争用演员整体位置与朝向。
- 全量测试 131 项，129 通过；仍为下述两个皮肤/存档测试失败。新增 3 项吸附回归测试通过，编译与 diff 检查通过。

以上为源码定位和自动测试结果，尚无游戏内帧时间测量。需实际检查：低速轴向/平面拖动、高帧率拖动、开关吸附、视口外松手、撤销/重做，以及带 Root 位移动画的演员。多物种模型绑定、头部约束和持物搬运仍按后续阶段推进。

## 2026-09-28：第一阶段，确定性采样与通道覆盖

本阶段先修复现有动画底座，不代表多物种渲染、玩家模型或持物功能已经完成。

### 已实现

- 过程化姿态使用时间轴相位采样，暂停、重复采样、回退后不继续累计时间。
- 每次采样清理上一次动作的运行时状态，避免搬运动作的锁臂状态残留。
- 显式动作以动作片段起点为相位起点；自动运动以当前 Transform/Route 片段起点为起点；无片段运动时使用工程时间。
- Transform 速度从当前插值段的导数求出，Route 速度根据弧长移动规则求出，不再依赖前一次采样。
- 显式 IDLE 抑制自动走跑；静音或离开该动作片段后恢复自动推断。
- PING_PONG 路径回程朝向与实际移动方向一致；ONCE 路径完成后速度为零。
- STEP 保持前值，到目标时间才切换。注意：这会改变依赖旧错误行为（提前跳到后值）的工程表现。
- RigPose 保留逐骨骼 TRS 通道掩码，复制和多层覆盖都保留掩码。仅缩放关键帧不会覆盖底层旋转、平移。
- 数据格式不变；通道掩码根据动画资产已有的稀疏通道生成。旧的完整姿态写入仍覆盖全部 TRS。

### 验证

- `gradlew.bat test`：128 项，126 通过，2 失败。
- 新增的 8 项回归测试全部通过，覆盖采样顺序、显式 IDLE、插值速度、STEP 边界、路径循环/回程、Transform 覆盖 Route、过程化状态重置和稀疏动画混合。
- 失败项为 `ActorRegistryPersistenceTest.skinFileNameIsPersistedAndRestored` 与 `ActorSkinStorageTest.importSkinWritesIntoSkinsDirectory`，测试未建立有效的活动世界存档上下文；相关存储实现本次未修改。未在原始 HEAD 上复跑验证基线。
- 尚未进行游戏内渲染与交互验收。

### 下一阶段

1. 将 LookAt 改为最终姿态上的头部约束，并正确解析点、实体和镜头目标。
2. 补齐预览模型绑定，统一可编辑能力与实际渲染支持；先牛羊、村民，再禽类。
3. 建立玩家模型渲染链路，并扩展按演员能力生成的时间线通道。
4. 实现骨骼挂点和道具渲染，使用可任意时间采样的附着关系表达拿起、搬运、放下。

当前限制：运动相位虽可重现，但尚未按累计路程锁定步态；演员关系引起的位移尚未纳入自动速度推断；道具事件与投掷仍不是确定性状态采样。现有走跑阈值暂时保留。


### 2026-09-29：身体选择与分通道打帧

- 身体子轨道与属性树统一通过 ActorSelectionHub 进入姿态工具，同步工具栏和部位选择；打开属性面板本身不再激活姿态工具。
- 数值编辑仅记录实际改过的位置、旋转或缩放通道，避免旋转编辑意外覆盖其他程序动画通道。增加所选部位的位置、旋转、缩放分别打帧按钮；完整姿态打帧仍保留。
- 属性面板和骨骼 Gizmo 开始操作时检查轨道锁定。选择新部位前结束旧 Gizmo 操作；新建姿态片段的撤销命令固定引用原轨道，避免切换演员后撤销作用于错误轨道。
- 增加稀疏通道打帧撤销/重做、不支持通道不产生空轨道、数值草稿通道重置测试。游戏内交互仍需手动验证。


### 2026-09-29：演员面板交互修复

- 演员列表增加文本搜索和类型筛选，列表选择同步场景与时间线；每行使用独立 ID，右键菜单绑定演员行，路径库移到独立页签。
- 名称输入使用持续草稿，提交后同步关联轨道，并支持撤销恢复各轨道原名称。
- 类型切换重建默认动作配置、清除旧预览和骨骼选择；撤销恢复原类型及原风格配置。
- 风格控件仅在实际修改时标记变更，独立演员面板与工具属性入口均发送保存所需的脏标记。
- 动作笔刷按演员配置筛选，旧的不兼容笔刷显示提示。创建弹窗说明类型动画能力，名称为空或放置失败时保留弹窗并显示原因。
- 本轮未新增生物类型；禽类、飞行和水生类型仍需要运行时与模型绑定配套实现。尚未做游戏内 UI 手动验证。


## 2026-10-01：P0 群体操作预检与事务

对应主规格：第 11.2 节、第 14 节 P0 和下一轮任务 2。

### 已实现

- 群体动作、路径复制、摄像机位置放置统一通过一个命令提交，整组一次撤销/重做；恢复原动作区间、原片段 ID、时间线包装器和父/反向引用。
- 提交前准备成员修改；执行中途失败时，按逆序恢复已经尝试的成员，包括部分写入后失败的当前成员，不将失败操作加入历史。
- 共享预检结果用于 UI 与执行服务：不存在演员、无当前轨道、锁定轨道、不支持的动作均跳过；显示可执行数量、跳过数量、成员原因和已有动作区间覆盖提示。文案接入中英文语言资源。
- 路径复制保留源演员，跳过目标区间已有路径或整体移动的成员；重复复制不会无提示叠加同一时间位置来源。源路径忽略静音片段。
- 数值无效或没有可执行成员时不写数据、不产生空撤销步骤。预备包装器时恢复旧反向引用，避免事务尚未提交就改动现有对象。
- 放置保留现有 Spawn 的可见性等其他属性；撤销同时恢复既有位置/朝向并移除本次新建 Spawn。

### 验证

- Gradle 编译与 20 项群体相关测试通过：原有命令 10 项、队形 4 项、新增事务 6 项。
- 新增覆盖异构群体预检、区间覆盖与整组撤销、路径冲突与源演员保留、既有/新建 Spawn 混合撤销、中途失败回滚、无效输入与全锁定无操作。
- JSON 解析与差异检查通过。游戏内群体 UI 与场景回放尚未人工验收；本轮未运行全仓库测试。

### 尚待推进

- P0 的骨骼 Gizmo 显式编辑目标、未提交草稿与上下文切换取消尚未实现，P0 未完成。
- 群体生成来源 ID、可持续编辑的 GroupBehaviorClip、成员例外与队形沿曲线变化仍按后续阶段推进。
- 动作兼容目前基于已有 RigProfile.supports；需要 P1 能力与绑定描述进一步区分模型真实渲染支持。


## 2026-10-01: P0 bone Gizmo draft transactions

- Bone Gizmo now has an explicit Animation Asset / Quick Pose target. Auto-key only controls commit on release; manual mode leaves a detached preview draft.
- Dragging creates no asset, clip, bone track or persistent key. Apply submits one undo command; cancel, Escape, actor/bone/time/model switches, playback and track locking discard the draft.
- Pending controls appear in the tool properties and timeline bone inspector. Shared asset editing is identified explicitly.
- Undo restores existing keys and interpolation, removes newly created bone tracks/assets/clips, and redo restores them. Looping clips map timeline time through asset playback normalization.
- Commit validates source clip, asset identity, source key, pose values and target context. No-motion gestures create no undo entry.
- Validation: 11 bone-related tests pass, including 4 new draft transaction tests. Interactive Minecraft viewport testing remains required for Escape during a drag, pending Root movement, context switching, and shared asset previews on other actors.
- Remaining P0 work: audit numeric/keyframe/menu authoring entry points for consistent locks, targets, transactions and persistence. The bone numeric panel still has its own draft flow; this round does not claim a unified authoring service.


## 2026-10-01: P0 numeric, keyframe and track-menu authoring consistency

- Added `ActorAnimationEditContext`: freezes model/actor/track/payload/clip/asset identity, timeline and local time, clip range/speed/loop, and asset duration/loop mode. Authoring rejects playback, locked or muted owners, missing/deleted targets, rebinding, asset replacement and incompatible rigs.
- Added `ActorAnimationAuthoringService`: numeric keying, channel/full-pose buttons, rest-pose keying, both timeline drag surfaces, delete, paste and mirror use the same asset target checks and undo transactions.
- Mixed translation/rotation/scale selections now move, delete and paste together. Collision, missing bone, unsupported channel, approximate duplicate target, invalid value or out-of-range time rejects the whole operation before any asset change. Paste never creates empty tracks during validation.
- Mirror and new-track paste undo remove tracks introduced by the operation. Existing bone track identity and unrelated channel data survive undo/redo. Repeated identical numeric keying does not add empty undo steps.
- The properties and timeline inspectors share one numeric draft. Collapsing the other inspector does not discard it. Starting Gizmo editing cancels numeric drafts; pending Gizmo previews block numeric writes. Context/source key changes invalidate stale numeric drafts even when their inspector is collapsed.
- Dope-sheet selection/drag context is scoped to model, actor, clip and asset; drag freezes source keys and cancels on Escape, source/context changes, playback, locks or competing drafts. Locked data remains selectable and copyable. Clipboard paste is restricted to the source rig, and an empty destination asset can receive keys.
- Actor track property-menu clip creation now validates model ownership, locks, playback, actor existence and supported action/legacy-pose type, and submits one undoable payload/wrapper transaction. Unsupported quick humanoid poses are not offered to quadrupeds through this service.
- Validation: 65 relevant tests passed, including 12 new authoring/context tests and 3 track-menu transaction tests. UI behavior in a running Minecraft client remains unverified.
- Remaining P0: asset-library attach/duplicate/import flows and other actor tools still need an entry-point audit; the legacy low-level keyframe helpers remain for compatibility, and Gizmo draft commits retain their own implementation. This is not completion of all P0 consistency work.

## 2026-10-01：P0 动画资产导入、复制与绑定

- 资产库加入“为当前片段制作独立副本”：深复制稀疏 TRS、插值、标记、时长与循环模式，仅重新绑定指定片段。片段 ID、包装器、时间区间、速度、循环设置与其他引用保持原有数据；复制和绑定作为一个撤销事务。
- 绑定服务与 UI 共用预检：验证播放状态、轨道归属、演员绑定、锁定/禁用、骨架兼容与时间范围。显示区间重叠数量，保留已有片段；同一播放头重复添加同一资产会被拒绝。一个演员对应多条轨道时，优先采用明确选中的轨道或片段所属轨道，不再任意选择第一条。
- 绑定的 payload 与时间线包装器分步提交，任一步失败都会逆序恢复，包括已经部分插入再抛错的步骤。独立副本的资产与片段绑定也使用相同事务机制。
- 资产创建不再将不支持骨骼编辑的已选演员默认为标准人形；UI 显示新建骨架与已有资产骨架，提供不冲突的副本 ID 建议。播放或姿态草稿未提交时资产修改入口只读，浏览与导出仍可用。
- 导入对话框返回后重新核对项目、演员与播放头，避免写入变化后的上下文。导入校验拒绝重复/近似重复的关键帧时间、非法缩放、无效四元数、超范围时间与未知骨骼；项目文件重复资产 ID 不再静默覆盖已有数据。
- 资产撤销和删除验证对象身份与所有持久化引用，包括禁用轨道和静音片段。独立副本产生新的外部引用时，撤销会回滚已尝试的解绑并保留原状态；同 ID 资产冲突不会覆盖其他对象。
- 命令历史在撤销/重做成功后才移动栈顶。失败保留原历史步骤，可在解决冲突后重试。
- 修正时间线初始化、重载和切换存档的依赖顺序：先加载自定义骨架与默认骨架，再解析动画资产，避免自定义动画因骨架尚未注册而无法恢复。
- 验证：Gradle 编译和 83 项相关测试通过；新增 10 项资产生命周期与 6 项导入/持久化校验测试。JSON 解析、差异检查通过。游戏内文件对话框、重叠提示、独立副本操作与实际切档尚未人工验收。
- 下一步：继续审计整体移动、朝向/注视、路径与其他演员写入入口的锁定/目标/撤销/持久化一致性；之后推进 P1 的统一能力描述与演员类型 UI。P0 尚未全部完成。

## 2026-10-01：P0 注视目标草稿与撤销

- 新增 `ActorLookAtEditSession`，冻结项目、演员、轨道、payload、播放头与原始注视片段。创建和修改都先准备独立草稿，拒绝播放中、锁定/禁用轨道、已删除演员、重新绑定、轨道/payload/包装器变化以及原片段变化后的提交。
- 世界点击和“看向摄像机”共用草稿提交入口，创建片段的 payload 和时间线包装器作为一次撤销操作；已有片段的修改支持完整恢复模式、世界目标、实体目标、强度与时间范围。取消、非法值和无改动编辑不会添加撤销步骤。
- 标记拖动只改草稿，预览覆盖目标标记、视线和演员朝向，不在移动时改写持久化片段或反复标记保存。松开一次提交；Esc、切换模式/演员/轨道/项目/时间、播放与锁定取消未完成草稿。
- 处理面板占用鼠标时的释放，避免拖动状态滞留。标记拾取优先于世界点击创建，拖动既有目标不会先触发一次目标修改；摄像机目标不进入世界坐标拖动。
- 注视工具与可视化采用明确选中的演员轨道或片段所属轨道，多轨道且无明确选择时不任意取第一条。可视化每帧清理过期标记，减少切换后的错误拾取。
- 验证：编译及 34 项相关测试通过，包含 8 项新注视草稿测试，以及动画资产、关键帧与骨骼草稿回归；差异检查通过。仍需游戏内验收标记拾取、面板上释放、Esc 和预览朝向；现有拾取仍基于准星方块命中点距离，尚未改成视口鼠标射线拾取。
- 后续继续处理整体 Transform Gizmo 与路径编辑的冻结目标、取消和单次撤销。此次完成注视入口，P0 尚未全部完成。

## 2026-10-01：P0 整体 Transform Gizmo 草稿与关键帧提交

- 新增 `ActorTransformEditSession`，冻结模型、演员、轨道、payload、播放头、源片段与源关键帧；拖动不再提前创建片段、扩展时间范围或覆盖源值。松开时一次提交，创建的 payload/包装器同步撤销，包装器插入失败回滚两侧。
- Esc、切换演员/轨道/项目/时间/模式、工具接管、播放、锁定/禁用、演员删除/重绑，以及源片段或关键帧变化取消草稿。取消直接恢复可用的源采样预览；没有实际移动、拖回初始值或只准备扩展的操作不创建片段或撤销记录。
- 旋转和缩放从当前播放头采样值开始，避免使用片段起点值导致起拖跳变。移动保留直接更新实体、pivot 与 box 的预览路径；源值检查直接比较冻结数据，不在每次指针采样中深复制整组关键帧，也不触发保存。
- 已有变换关键帧的片段，按播放头更新已有键或插入新键，解决只改端点被关键帧采样覆盖的问题。只写本次编辑的 TRS 通道，其他通道使用原键或当前采样；保留原键对象、插值和附加数据，撤销删除新增键或恢复原键。此路径沿用旧片段关键帧的全局时间语义；无关键帧的片段仍编辑端点，尚未统一为稀疏对象通道。
- 拖动尝试只在一个鼠标手势开始一次。拒绝或取消后，按住鼠标不会重新绑定到变化后的演员；演员专用 applier 不再退回修改方块选区。
- 属性面板的整体变换与注视写入复用草稿服务、明确轨道选择、时间检查和撤销；播放、锁定/禁用或竞争姿态草稿时禁用编辑。非输入状态重新采样，撤销或 Gizmo 改动后显示当前数据。数值输入与强度滑块仍按每次值变动提交，后续需合并为完整数值手势事务。
- 人形 `PreviewActorEntity` 加入独立的整体缩放预览值，在渲染时与骨骼 Root 缩放相乘；拖动和取消即时更新，提交后使用时间线采样。此次未扩展原生动物与自定义显示模型的整体缩放渲染或缩放后的拾取框。
- 验证：编译及 50 项相关测试通过，包含 16 项新增整体变换测试；覆盖真实关键帧采样、单次撤销、空手势、扩展、目标失效、非法值、部分插入失败与回退到端点值的关键帧编辑。仍需游戏内验证移动流畅度、旋转/缩放、Esc、切换工具与属性刷新。
- 下一项：路径锚点拖动、路径库编辑和演员路线应用的冻结目标、取消、锁定校验与撤销；P0 尚未全部完成。

## 2026-10-01：P0 路径锚点、路径库与路线应用事务

- 新增 `ActorPathEditing`：路径库资源与演员路线实例使用不同的明确目标，冻结模型、播放头、库/资源身份或演员/轨道/payload/包装器身份、几何和路线行为。路径要求 2–2048 个有限锚点；目标变化、播放、锁定/禁用、缺失演员、重绑或源修改拒绝提交。
- 锚点拖动只修改本地预览，松开一次提交并重新计算时长；没有位移或拖回原值不保存、不添加历史。Esc 在拖动分支之前处理，取消恢复草稿；切换演员/时间/模式/库/项目、工具接管、锁定和停用取消未完成手势。松开后的回调不会再次提交取消的手势。路线实例的演员运动仍在提交后重新采样，拖动期间不进行地形重编译。
- 修正新草稿仍持有旧 `editingClip` 的问题；新建、取消和停用清除路线绑定，避免后续草稿写入旧路线。保存资源、添加/删除锚点、修改曲线类型均使用事务，已保存目标不允许删除到不足两个锚点。未保存草稿的点编辑保持本地状态，不进入持久化历史。
- “应用到演员”先验证明确选中的演员轨道，拒绝路线/整体移动片段的时间重叠，再一次添加 payload 与包装器；任一步失败回滚两側。应用使用独立路线副本，保留已有资源，不再隐式先保存草稿到库；“保存到路径库”是独立的明确操作。路线沿用地面/飞行能力与受限跳跃采样，不改动生物运动规则。
- 路线几何修改同步包装器时间范围并保留速度、循环、朝向对齐与跳跃设置；撤销恢复原几何和时长。撤销/重做重新检查资源身份、几何、路线行为与时间冲突，失败保留历史，不覆盖变化后的数据。
- 路径库重命名、锁定、显示/隐藏、删除和新建接入单次撤销，拒绝锁定资源的重命名/删除及名称/ID 冲突。保存失败恢复内存状态；几何保存失败尝试恢复原持久化内容。持久化回调捕获库身份，切换存档后拒绝保存旧库到新库。
- 路径工具显示当前正在编辑草稿、库资源还是路线实例；分别提供应用、保存和删除锚点按钮，按可用目标和锚点数量禁用；未选演员仍可绘制和保存资源。按钮采用单列布局，失败原因在面板中显示；新增目标说明与保存按钮的中英文文案。库面板切换项目/库时取消旧编辑上下文，播放时只读。
- 路径 Gizmo 固定为锚点平移，屏蔽继承的方块选区缩放/旋转写入；拒绝拖动时不再移动 Gizmo 框。停用仅清理路径工具持有的 Gizmo。
- 验证：编译和 64 项相关测试通过，包括 13 项路径事务、3 项控制器取消回归，以及地面/跳跃、群体路线、整体变换和注视事务；中英文 JSON 与差异检查通过。游戏内的锚点拾取、Esc、工具切换、面板布局、地形重建耗时与跨存档操作仍需验收。
- 后续：继续审计路线属性面板中的速度、循环、跳跃与时长等参数写入，并将数值输入/滑块合并为完整手势事务；之后推进 P1 的统一能力描述与演员类型 UI。P0 尚未全部完成。
## 2026-10-01：P0 路线属性编辑事务

- 路线属性面板接入 `ActorRoutePropertyEditSession`。速度、插值、循环、沿路线朝向、跳跃参数、锚点坐标及增删先修改独立草稿；连续输入或滑块拖动结束后一次提交和撤销，未改动不添加历史，不逐帧保存。
- 新增可编辑的片段时长。沿用播放采样语义：速度决定单程运动快慢，片段时长决定有效播放窗口，循环/往返可持续多圈；属性编辑不隐式按速度重算窗口。路径工具修改几何仍沿用按长度计算单程时长的现有规则，后续可统一为显式“适配单程时长”操作。
- 冻结模型、轨道、演员绑定、播放头、源路线、包装器及完整原始属性。Esc、播放、锁定/禁用、目标改变、源属性变化、面板中断或编辑控件隐藏取消草稿；取消后等待手势释放，避免继续按住时绑定到新目标。
- 写入及撤销/重做前检查目标和原始状态，拒绝与其他移动片段重叠的时间窗口；几何、行为和包装器时间同步应用。锚点维持 2–2048 个，非法坐标及时间不写入源数据；仅行为修改不重建路径几何。
- 编译及 32 项路线相关测试通过（含 8 项新增事务测试），覆盖多次草稿修改单次撤销、无改动和取消、上下文变化、时长/几何校验、重叠失败无部分写入、源变化保留历史和重做冲突。仍需游戏内验收 ImGui 输入结束、折叠控件、切换目标和 Esc；本轮草稿仅在属性面板显示，不额外驱动世界路径预览。
- 下一项继续合并整体 Transform 数值输入与 LookAt 强度滑块的手势事务；P0 尚未全部完成。
