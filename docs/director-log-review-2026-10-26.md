# Director 两份用户日志检查

检查日期：2026-10-26。本次只分析日志并对照当前工作区源码，没有修改业务代码或运行游戏复测。

原始日志目录：`C:\Users\navib\OneDrive\xwechat_files\wxid_0613686136812_db8f\msg\file\2026-10`。

| 文件 | 行数 | 覆盖时间 |
| --- | ---: | --- |
| director-latest.log | 8324 | 17:16:50～17:53:32 |
| director-dakaiduka.log | 948 | 18:10:01～18:19:32 |

两次均加载 Minecraft 1.21.10、Director 1.0.0、Fabric API 0.138.4+1.21.10；日志结束包含主动停止与资源清理记录。没有发现 Director 导致的致命崩溃记录，但存在严重的运行性能问题。

## 1. 最高优先级：时间线控制器在渲染线程长时间阻塞

这些关键证据是 INFO 级性能日志，只筛选 ERROR/WARN 会漏掉最严重的问题。

| 文件与行号 | 时间 | 单次 total | 主要耗时 |
| --- | --- | ---: | --- |
| dakaiduka:759 | 18:13:05 | 22403.11ms | step1_visible=8294.27ms，step3_apply+entries=14085.16ms |
| dakaiduka:792 | 18:17:28 | 25108.57ms | step3_apply+entries=25068.69ms |
| dakaiduka:796 | 18:17:43 | 15437.90ms | step3_apply+entries=15374.46ms |
| latest:7260 | 17:34:12 | 15700.49ms | step1_visible=15659.11ms |
| latest:7490 | 17:42:24 | 11012.42ms | step1_visible=10874.57ms |
| latest:8222 | 17:52:47 | 18133.98ms | step1_visible=18073.83ms |

对照 `TimelineClipBlockController.java:502` 和 `:1011`，计时从本次调用起点计算，并非五秒内多帧耗时之和；日志由 Render thread 输出。这说明单次操作可阻塞十几至二十多秒。计时是墙钟时间，仍可能包含 GC、锁等待等，无法仅靠这些分段日志定位到某个方法内部。

latest:7876，17:48:18，单次处理仍需 2405.70ms，其中 step3 为 2381.23ms。latest:7879、7882 记录 0.7 FPS。latest:8030 附近至 8100 的后段样本持续约 1.3～1.4 秒；8100 即使 renderEntries=0，total 仍为 1335.49ms、step3=1313.21ms。这证明剩余卡顿不能只归因于实际绘制的补画数量。

建议优先细分 step1 的可见集合/序列生成，以及 step3 的快照准备、可见性服务提交、视觉状态更新、动画条目生成；用 CPU/分配/锁等待采样区分具体原因。大集合工作应避免在渲染线程同步全量执行。

## 2. 方块恢复积压，并伴随大量覆盖层绘制

- latest:7490，visibleTotal=373363，renderEntries=373362；7491 的 section pending=407。
- latest:7496、7499，BlockAnimations 最近一次耗时分别为 175.55ms、179.24ms，累计记录峰值 max=391.01ms。
- dakaiduka:798，BlockAnimations last=222.86ms，max=250.44ms；792 的 renderEntries=183844。
- latest:7875，17:48:16，working=1，oldestTargetMs=351754，约 5 分 52 秒；该五秒统计窗口 writes=1499。dakaiduka:795 的 oldestTargetMs=40201，约 40 秒。

oldestTargetMs 表示持续待处理任务年龄，不能理解为每一个最新目标都延迟这么久。两份日志也有队列清空记录，不能据此断言永久死锁。但长时间未收敛、数十万补画条目和绘制尖峰都已得到日志支持。恢复调度、补画交接以及主线程集合处理需要一起检查；不能仅凭这些日志认定显卡性能不足。

## 3. Iris 与 Director 淡入淡出管线兼容报错

latest:7508，17:42:42：

```text
Missing program director:pipeline/whole_container_fade in override list.
This is not a critical problem, but it could lead to weird rendering.
```

7509～7537 的诊断堆栈经过 Iris `redirectIrisProgram`、Director `BlockAnimationRenderer.renderBlockWithAlphaAndDepth`。当前 `WholeContainerRenderLayers.java` 确实定义该自定义管线。

这是明确的渲染兼容问题线索，可能影响透明度/淡入淡出外观；日志之后仍继续运行，不能把该 Throwable 堆栈当成游戏已崩溃。应复测相同动画在启用/停用光影时的差异，并核对自定义管线的 Iris 适配。

## 4. Fabric API 不兼容告警属于版本比较缺陷

两份日志:186～196 均出现：

```text
版本比较失败: 0.138.4+1.21.10 vs 0.138.4+
必需模组 fabric 版本不匹配
发现不兼容的必需模组，模组可能无法正常工作
```

当前 `ModCompatibilityChecker.java:260` 的 compareVersions 直接按点分割后 Integer.parseInt，遇到当前版本中的 `4+1` 会失败。比较最低要求 0.138.4 时没有先去掉当前版本的构建元数据；之后比较 0.140.0 又因次版本较低而返回不满足。因此这里的 ERROR 不能证明实际 Fabric API 不兼容，应修正版本解析并验证，而不是据此要求用户升级依赖。

## 5. UI 调用栈警告尚不能认定为实际 ImGui 泄漏

dakaiduka:890～892 一次，latest 中四次；关闭时均残留 `[DockSpace:DockSpace]`，随后清理一个记录。

当前源码在 DockSpace 内渲染菜单栏，菜单栏可以触发关闭；close 中立即检查/清空自建跟踪栈，随后 renderDockSpaceWithPanels 的 finally 才执行 recordEnd。这能解释关闭发生于当前渲染调用中途时的警告。应核实关闭顺序/记录时机；现有日志没有证明底层 ImGui 真正存在 Begin/End 泄漏或内存泄漏。

## 6. 操作被拒与低优先级日志

- latest 中 120 条“容器 环路 已在时间线上，拒绝修改动画预设”，集中于 17:29:46～17:29:53。这是业务保护规则，但说明用户修改没有生效；界面应明确禁用或提示，避免同一交互持续提交和刷日志。
- latest:8166～8168 拒绝重复添加环路、台阶、花坛的片段；保护逻辑生效，但应复核重开 UI 时是否有重复装载路径，不能凭此断言片段丢失。
- latest 中一次拖入不接受容器的轨道被拒绝，属于目标类型校验。
- 面板 StatusBar 优先级 -10 的警告，当前源码明确返回 -10；面板初始化均为成功 11、失败 0，属于验证规则与设计意图不一致。
- 两份:440/对应资源加载阶段出现 Director 工具纹理为 30×30，导致方块图集 mip level 从 4 降为 1。应把该纹理调整为合适的二次幂尺寸；日志未证明它造成此次秒级卡顿。
- Voxy 有 GPU 内存等待失败/大量复制告警；网络更新与用户资料请求也有超时。这些应与 Director 问题区分，不能用它们解释已经定位到 Director 调用中的全部耗时。

处理顺序：渲染线程秒级阻塞 → 恢复积压与补画成本 → Iris 自定义管线兼容 → 版本检查误报 → UI 关闭记录与操作反馈 → 纹理/低价值警告。

## 第一轮优化与验证

本节记录用户授权优化后实施的修改；上文保留原始日志分析。

### 已复现并修复的集合瓶颈

时间线控制器的全量位置缓存使用 `Set.copyOf(cache.blocks)`。JDK 的不可变 Set 使用线性探测；密集三维 BlockPos 的哈希容易形成连续拥挤区域，使构建和 contains 扫描异常昂贵。同一个全量集也被序列视图和服务端 BlockPositionTarget 用于成员查询，因此影响不止首次构建。

使用真实 Minecraft BlockPos，坐标为 x=-500～-373、y=-43～-12、z=1785～1800，共 65536 个位置，本机复现结果：

| 实现 | 构建 | 遍历查询所有位置 |
| --- | ---: | ---: |
| 修改前 Set.copyOf | 2819.27ms | 2214.50ms |
| 修改后只读 HashSet 快照（全量测试时） | 2.20ms | 2.77ms |

这是分次运行的合成用例，受 JVM 预热和机器负载影响，不应转换为游戏 FPS 提升倍数；但已直接复现原始实现的秒级耗时。更薄的平面分布未出现同样严重的耗时，因此之前仅测试沿单轴分布的位置没有发现此问题。

新增 `BlockPositionSets.immutableCopy`，内部使用 HashSet 的哈希分散与冲突处理，再用只读包装保持跨帧/跨线程快照不可变；控制器已改用该入口。缓存仍按原有逻辑复用，不在每帧复制集合。本轮没有改变动画采样、方块恢复预算或播放语义。

新增回归覆盖：393216 个密集坐标的构建与全量查询、输入集合修改不影响历史快照、禁止通过集合与迭代器修改、密集坐标下普通/拆除序列与前后跳转的成员关系。39.3 万位置的完整验证用例在全量测试时约为 0.117 秒；这同样不是游戏帧耗时实测。

### 兼容检查误报修复

版本比较改用 Fabric Loader 的语义版本解析器，构建元数据不参与版本优先级比较，预发布版本仍低于正式版；补充 Fabric API 构建后缀、最低版本、版本范围、预发布与无效版本回归。

### 验证与产物

- 相关时间线、动画、可见性及兼容检查测试：296 项通过。
- `gradlew.bat test remapJar --offline --continue`：成功；全量 1171 项测试全部通过，无失败、错误或跳过。
- 可替换的模组包：`build/libs/director-1.0.0.jar`。
- 尚未运行用户原存档的游戏内复测；不能承诺此次已消除全部卡顿。Iris 淡入淡出管线报错、UI 关闭跟踪警告与资源纹理问题未在本轮更改。下一次复测应重点比较相同容器/播放头操作下 step1_visible、step3_apply+entries、oldestTargetMs 和实际画面交接。
