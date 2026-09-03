# Director 角色动画系统推进方案

## 1. 目标与结论

### 当前实施状态（2026-09-03）

- Phase 0 数据底座已开始实现。
- 已增加通用 `BoneTransform`、`BoneDefinition`、`RigDefinition` 和 `RigPose`。
- 已增加 `director:humanoid` 标准人形骨架及旧 `Pose` 适配器。
- 已贯通 `Pose -> RigPose -> ArmorStand` 的首条渲染链路。
- 骨骼 Gizmo、关键帧资产和 Dope Sheet 尚未开始，仍按下述阶段推进。

Director 有可能实现接近 Mine-imator 的角色摆姿和动画制作体验，但不应把目标理解为“一次性复制 Mine-imator 的全部能力”。更合理的目标是：

> 在 Minecraft 客户端内提供一套面向导演的、可视化的骨骼姿态编辑与关键帧动画系统，并与 Director 已有的 Actor、Timeline、撤销、镜头和道具系统形成统一工作流。

建议按以下顺序推进：

1. 人形骨架的可视化 FK 摆姿。
2. 每根骨骼的关键帧、补间与曲线编辑。
3. 动作资产的保存、复用和混合。
4. 手脚 IK、注视和道具约束。
5. 四足、非标准模型、面部和更高级的动画工具。

其中前 3 步完成后，Director 就已经具备“可实际制作角色动画”的核心闭环；IK、面部和复杂骨架属于增强能力，不应成为首版的阻塞项。

---

## 2. Mine-imator 式体验应包含什么

用户感知上的关键能力不是某个特定文件格式，而是以下工作流：

1. 在场景中选中角色。
2. 点击头、身体、上臂、前臂、手、腿等部位。
3. 使用旋转或位移 Gizmo 直接摆姿。
4. 在当前时间自动或手动插入关键帧。
5. 移动播放头，再摆一个姿势，由系统生成中间动作。
6. 调整缓入缓出、贝塞尔曲线或关键帧切线。
7. 将一段动作保存为资产，并应用到其他兼容角色。
8. 使用脚部固定、手持道具、LookAt 等约束减少手工调整。

Director 当前已经拥有其中的时间线、Actor 选择、整体变换 Gizmo、姿态采样、运行时混合和工程持久化基础；主要缺口是通用骨架模型、骨骼选择、骨骼 Gizmo、逐通道关键帧及动画资产编辑器。

---

## 3. 当前基础与关键限制

### 3.1 可以复用的现有能力

- `ActorTrack` 和实体片段体系可以承载角色动画轨道。
- `ActorPoseClip` 已支持宽泛身体区域覆盖和相邻姿态补间。
- `AnimationData`、`AnimationKeyframe`、`DefaultAnimationPlayer` 和 `AnimationLibrary` 已形成最小可运行管线。
- `PoseBlender` 已定义 Animation > RigProfile > Base Pose 的覆盖优先级。
- `ActorGizmoController`、`GizmoManager` 可以提供交互和撤销事务方面的参考。
- `ActorRuntimeController` 已经是最终姿态进入预览实体的统一出口。
- Timeline 持久化和统一撤销历史可以继续扩展。

### 3.2 不能继续沿用的限制

当前 `Pose` 是固定字段结构，例如 `headPitch`、`leftArmYaw`。它适合原型和原版人形模型，但不适合作为长期骨骼动画格式：

- 无法表达上臂/前臂/手掌等层级。
- 无法表达四足动物的前后四腿、尾巴、耳朵等骨骼。
- 每增加一种模型都需要修改 Pose、Sampler、Persistence、UI 和 Renderer。
- 固定的身体区域 Mask 无法支持单骨骼或骨骼子树混合。

因此，现有 `Pose` 应保留为兼容层和简单动作接口，不应继续成为完整动画系统的最终数据模型。

---

## 4. 推荐目标架构

### 4.1 RigDefinition：骨架定义

新增与模型无关的骨架描述：

```java
public final class RigDefinition {
    String id;
    String displayName;
    List<BoneDefinition> bones;
    Map<String, Integer> boneIndexById;
}

public final class BoneDefinition {
    String id;             // humanoid.left_arm
    String displayName;
    int parentIndex;       // -1 表示根骨骼
    Transform restLocal;   // 默认局部位置、旋转、缩放
    BoneCapabilities capabilities;
}
```

设计要求：

- 骨骼使用稳定 ID，不使用 UI 显示名称作为存档键。
- 父子关系必须显式保存。
- `restLocal` 表达绑定姿态。
- 每个骨骼声明是否允许旋转、平移、缩放以及各轴限制。
- 首批只提供 `director:humanoid`；四足骨架后续单独定义。

### 4.2 RigPose：通用姿态

```java
public final class RigPose {
    RigDefinition rig;
    Transform[] localTransforms;
    BitSet authoredBones;
}
```

`RigPose` 使用骨骼索引数组，避免每帧创建字符串 Map。编辑器可以使用骨骼 ID，运行时编译为索引访问。

`Transform` 至少包含：

- `Vector3f translation`
- `Quaternionf rotation`
- `Vector3f scale`

动画存储和插值应使用四元数，不应直接对三个欧拉角做长期插值。UI 可以显示欧拉角，但进入运行时后转换为 Quaternion，并使用 `slerp` 或归一化 `nlerp`。

### 4.3 AnimationAsset：可复用动画资产

```java
public final class AnimationAsset {
    String id;
    String name;
    String rigId;
    float duration;
    LoopMode loopMode;
    Map<String, BoneTrack> boneTracks;
    List<AnimationMarker> markers;
}

public final class BoneTrack {
    KeyframeTrack<Vector3f> translation;
    KeyframeTrack<Quaternionf> rotation;
    KeyframeTrack<Vector3f> scale;
}
```

关键帧必须按通道保存。只改变左手时，不应为全身每根骨骼生成重复关键帧。

每个关键帧至少支持：

- 时间。
- 值。
- 插值模式：STEP、LINEAR、SMOOTH、BEZIER。
- 入/出切线或曲线参数。

### 4.4 Timeline 中的职责划分

建议保留两类概念：

- `ActorPoseClip`：快速摆拍和简单姿态过渡，适合少量导演式调整。
- `ActorAnimationClip`：引用一个 `AnimationAsset`，控制起始偏移、速度、循环、淡入淡出和骨骼 Mask。

不要把一个复杂动作的全部骨骼关键帧直接塞进主 Timeline 的片段属性面板。应提供独立的“动画片段编辑模式”：主时间线负责场景编排，动画编辑器负责资产内部的骨骼通道。

### 4.5 运行时姿态栈

建议最终采样顺序为：

1. Rest Pose。
2. `ActorRigProfile` 的过程化动作。
3. `AnimationAsset` 关键帧动画。
4. `ActorPoseClip` 的导演覆盖。
5. LookAt、手脚 IK、道具挂接等约束。
6. 最终骨骼矩阵计算并提交渲染。

每层都应返回 `RigPose + BoneMask + weight`，而不是直接修改实体。这样才能实现局部动画，例如下半身行走、上半身挥手。

---

## 5. 分阶段实施计划

## Phase 0：规格冻结与技术验证

目标：证明 Minecraft 当前渲染对象可以稳定接受逐骨骼姿态。

任务：

- 确认首版只支持 Director 的人形预览 Actor。
- 定义标准人形骨骼 ID 和层级。
- 实现 `RigDefinition`、`BoneDefinition`、`RigPose`、`Transform`。
- 写一个硬编码姿态，将头、身体、双臂和双腿映射到当前 ArmorStand 渲染模型。
- 确认旋转空间、轴方向、角度单位和父子矩阵顺序。
- 为 RigDefinition 验证、姿态复制、局部到模型空间计算编写单测。

验收标准：同一组 RigPose 数据在暂停、播放和时间轴跳转后渲染结果完全一致。

预计工作量：3～5 个开发日。

## Phase 1：人形 FK 可视化摆姿 MVP

目标：用户能够在 3D 视口中选骨骼并直接旋转摆姿。

任务：

- 新增 `ActorBoneSelectionController`，通过骨骼碰撞体或屏幕空间距离实现拾取。
- 新增 `ActorBoneGizmoController`，不要复用整体 Actor TransformClip 的写入逻辑。
- 支持本地/世界旋转空间切换。
- 首版支持头、身体、左右臂、左右腿；上臂 Yaw/Roll 必须可编辑。
- 拖动开始时记录前态，结束时向统一 `TimelineCommandHistory` 提交一次命令。
- 自动定位或创建当前时间的 Pose 关键帧。
- 增加“自动关键帧”开关，默认建议关闭，避免无意写入。
- 在视口中高亮选中骨骼，并显示父子层级。

验收标准：用户不打开属性面板，仅靠视口即可完成站立、挥手、叉腰、坐姿等基本人形姿态，并可一次 Ctrl+Z 撤销整次拖动。

预计工作量：8～12 个开发日。

## Phase 2：真正的逐骨骼关键帧动画

目标：完成“移动播放头—摆姿—自动补间—回放”的制作闭环。

任务：

- 引入 `AnimationAsset`、`BoneTrack` 和通用 `KeyframeTrack<T>`。
- 将关键帧按骨骼与 TRS 通道存储。
- 旋转使用 Quaternion 插值。
- 支持 STEP、LINEAR、SMOOTH 三种插值；BEZIER 可在曲线编辑器完成后加入。
- 新增 Dope Sheet：按骨骼显示关键帧，支持框选、移动、复制、删除和时间缩放。
- 支持整姿态打帧和仅修改通道打帧。
- 实现关键帧吸附、复制/粘贴、镜像姿态和左右骨骼交换。
- 动画采样必须完全由 Timeline 时间驱动，禁止依赖帧累计。
- 所有批量编辑作为单个可撤销事务提交。

验收标准：可以用两个或多个关键姿态制作平滑挥手、转头、坐下和起身，并在任意时间跳转后得到确定结果。

预计工作量：12～18 个开发日。

## Phase 3：曲线编辑与动作资产闭环

目标：让动作可调整节奏、保存和复用。

任务：

- 新增 Graph Editor，显示选中骨骼通道曲线。
- 支持缓入、缓出、自动切线、断开切线和复制曲线。
- 工程内保存 AnimationAsset，并采用独立版本号。
- 支持动作重命名、复制、删除、导入和导出。
- `ActorAnimationClip` 引用资产 ID，不复制整份关键帧数据。
- 支持片段速度、反向、循环、裁剪区间、淡入和淡出。
- 支持骨骼 Mask 与混合权重。
- 资产删除时检查引用，禁止产生静默悬空引用。

验收标准：制作一次挥手动作后，可以将它用于多个兼容演员，并独立调整每个片段的速度和循环方式。

预计工作量：10～15 个开发日。

## Phase 4：IK、LookAt 和道具约束

目标：减少需要逐帧修正的机械劳动。

推荐顺序：

1. 双骨骼 IK：上臂—前臂—手、上腿—小腿—脚。
2. 脚部固定和地面投射。
3. 头部/眼睛 LookAt 约束和角度限制。
4. 手部挂点与道具父子约束。
5. 双手持物和约束权重动画。

约束必须是非破坏层：原始 FK 关键帧保持不变，运行时在其上求解。需要“烘焙约束到关键帧”功能，便于导出或手工修正。

验收标准：角色走动时脚不会明显滑动；手可以稳定跟随剑、工具或目标点；关闭约束后原始动画数据不丢失。

预计工作量：15～25 个开发日。

## Phase 5：四足、非标准模型与面部

目标：从固定人形升级为可扩展角色系统。

任务：

- 定义 `director:quadruped` 标准 Rig，而不是把四足字段塞进人形 Pose。
- 为每个实体适配 RigBinding，将通用骨骼映射到实际模型部件。
- 增加前左、前右、后左、后右、颈部、头、尾巴等通道。
- 提供 Rig 兼容性检查和动作重定向规则。
- 面部首版优先支持头部附件、眼睛朝向和嘴部开合；复杂表情系统单独设计。
- 对无法绑定的骨骼给出明确警告，不允许静默忽略整段动画。

预计工作量：20 个开发日起，取决于支持的实体和模型范围。

---

## 6. 数据迁移与兼容策略

现有工程不能因为引入通用骨架而失效。

建议：

- 保留当前 `Pose` 和 `ActorPoseClip` 的读取能力。
- 加载旧片段时，通过 `LegacyHumanoidPoseAdapter` 映射到标准人形 RigPose。
- 新字段使用可选值，旧存档缺失时采用绑定姿态。
- AnimationAsset 使用独立 schemaVersion，并提供逐版本迁移器。
- Rig 和动画资产均使用 namespaced ID，例如 `director:humanoid`、`project:wave_01`。
- 存档继续采用临时文件、原子替换和备份策略。

在通用系统稳定前，不建议立即删除旧 Pose API；应先标记为兼容接口，并让新 UI 只写新格式。

---

## 7. UI 建议

推荐布局：

- 左侧：骨骼层级树，可搜索、锁定和隐藏。
- 中央：3D 视口与骨骼 Gizmo。
- 下方：Dope Sheet / Graph Editor 切换。
- 右侧：选中骨骼的局部 Transform、插值和约束属性。
- 顶部工具条：选择、移动、旋转、局部/世界空间、自动关键帧、镜像姿态。

交互原则：

- 拖动过程中实时预览，松开鼠标只产生一个撤销命令。
- 切换时间不会自动改写关键帧。
- 没有关键帧时编辑，应明确提示将创建关键帧。
- 多骨骼选择应支持统一旋转和时间移动。
- 数值输入与 Gizmo 必须共享同一命令层，避免撤销行为不一致。

---

## 8. 测试与性能门槛

### 单元测试

- Rig 层级合法性、循环父子关系检测。
- Quaternion 插值和角度跨越 ±180° 的最短路径。
- STEP、LINEAR、SMOOTH、BEZIER 采样。
- BoneMask 和多层权重混合。
- 旧 Pose 到 RigPose 的迁移。
- 关键帧移动、复制、删除的撤销/重做。
- AnimationAsset 序列化往返一致性。

### 集成测试

- 暂停、逐帧、拖动和跳转后的姿态一致。
- 动画资产删除、重命名和重复 ID。
- 多演员同时播放不同动作。
- Actor 删除或换 Rig 后的引用处理。
- 工程异常退出后的动画资产恢复。

### 性能目标

- 编辑状态下 20 个演员同时采样，不产生明显帧时间尖峰。
- 每帧采样路径避免字符串查找和临时对象分配。
- 只在关键帧或 Rig 结构变化时重建缓存。
- IK 和约束只对启用的骨骼链求解。

---

## 9. 风险与应对

### Minecraft 模型并非统一骨架

原版实体模型层级和命名不同，模组实体差异更大。应通过 `RigBinding` 隔离，而不是让动画编辑器依赖具体 ModelPart。

### 欧拉角万向锁和插值翻转

UI 可以展示欧拉角，内部必须使用 Quaternion。导入欧拉数据时需要角度连续化和最短路径处理。

### Gizmo 与 Timeline 状态竞争

拖动期间应锁定编辑上下文；若播放头、选中演员或工程发生变化，应安全取消事务，不能把结果写到错误片段。

### 功能范围失控

首版不做完整 IK、面部捕捉、布料、物理骨骼和任意模组模型导入。先保证人形 FK 动画闭环可用。

### 两套动画格式长期并存

必须尽早指定 `AnimationAsset/RigPose` 为新主格式，并提供旧格式适配器；不要让两个系统各自继续扩展。

---

## 10. 推荐首个可交付版本

建议把第一个公开里程碑定义为“Actor Animation MVP”，范围严格限制为：

- 标准人形 Rig。
- 头、身体、双臂、双腿的骨骼选择。
- FK 旋转 Gizmo。
- 自动/手动关键帧。
- STEP、LINEAR、SMOOTH 插值。
- Dope Sheet 的选择、移动、复制、删除。
- 姿态镜像。
- 完整撤销/重做。
- 工程保存、加载和旧 ActorPoseClip 兼容。

不包含：

- IK。
- 曲线编辑器。
- 四足动物。
- GeckoLib。
- 面部动画。
- 任意第三方模型绑定。

这个范围已经足以验证用户是否真正愿意使用 Director 制作角色动作，同时避免在尚未验证工作流前投入高成本的通用模型和 IK 系统。

---

## 11. 建议的实施顺序

实际开发时建议按以下 PR/变更集拆分：

1. RigDefinition、RigPose、Transform 与测试。
2. 标准人形 RigBinding 和渲染验证。
3. 骨骼拾取、高亮与层级面板。
4. Bone Gizmo 和统一撤销事务。
5. AnimationAsset、BoneTrack 与确定性采样。
6. 自动关键帧和 Dope Sheet。
7. 插值模式、镜像、复制粘贴。
8. 资产保存、复用和旧格式迁移。
9. 性能测试、异常恢复和 MVP 发布。

每个变更集都应保持工程可编译、旧项目可加载，并附带对应采样或持久化测试。不要先建设一个庞大的“最终骨架框架”再一次性接 UI；渲染、编辑、存档和撤销应从第一个骨骼开始纵向贯通。

---

## 12. 最终判断

Director 具备实现 Mine-imator 式角色动画的良好基础，最大的技术障碍不是插值公式，而是从“固定九项姿态字段”迁移到“通用骨架和逐通道关键帧”，并提供可靠的可视化编辑体验。

最值得立即启动的是 Phase 0 和 Phase 1：先让用户能够在视口中选择并旋转人形骨骼，而且所有操作可撤销、可保存。完成这一步后，再建设动画资产和曲线系统，风险和返工都会显著降低。
