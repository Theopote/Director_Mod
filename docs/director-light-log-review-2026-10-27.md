# director-light-latest.log 检查与修复

检查日期：2026-10-27。原始日志：`C:\Users\navib\OneDrive\xwechat_files\wxid_0613686136812_db8f\msg\file\2026-10\director-light-latest.log`，5180 行，覆盖 2026-10-26 18:20:57～20:52:24。

## 本轮新发现并修复：面选择预览使用已结束的缓冲

日志在 18:23:18～18:23:48 记录 7 条 ToolPreviewManager ERROR，起始行分别为 998、1333、1664、2033、2392、2717、2964。异常均为 `java.lang.IllegalStateException: Not building!`，调用链为：

```text
BufferBuilder → ToolPreviewManager.renderQuad
  → renderBlockPreview → renderFaceHoverPreview → render
```

这不是日志中 GPU 内存等待告警的同一问题。预览代码先获取填充层 consumer，再获取线框层 consumer，之后才写入填充顶点。使用共享即时缓冲的 provider 在切换 RenderLayer 时会结束旧 builder，旧 consumer 随即失效。错误记录有五秒节流，实际触发次数可能多于日志中的七条。

相同错误顺序也存在于 renderFacePreview。修复两条路径：

- 填充开启时才获取填充 consumer，并立即完成全部填充顶点写入。
- 填充结束后才获取线框 consumer，不再跨越图层切换持有并使用旧 consumer。
- 关闭轮廓时不请求无用的线框缓冲；悬停法线箭头仍按原有行为显示。

未通过吞掉异常或调整渲染阶段掩盖问题。修复位置：`src/main/java/com/director/client/renderer/ToolPreviewManager.java`。

新增 ToolPreviewBufferLifetimeTest，以切换图层就结束旧 consumer 的 provider 验证真实预览方法，覆盖面选区/悬停两条路径中填充与轮廓开关的全部组合，检查填充顶点数、轮廓及法线箭头保留。

## 日志中的其他 Director 报错与警告

| 记录 | 判断与处理 |
| --- | --- |
| :186～196 Fabric API 版本比较失败/不兼容 ERROR | 原版本比较器不能处理 +1.21.10；当前源码已在此前改用 Fabric Loader 语义版本比较，已有回归。本次包包含该修复。日志中另一个版本检查器也记录 API 兼容，不能把 ERROR 当成依赖实际不匹配。 |
| :3317 Iris Missing program director:pipeline/whole_container_fade | 与之前日志相同的自定义管线映射缺失；当前源码已有主画面/阴影映射桥接与光影下的遮挡绘制适配。本次包包含该修复；仍需原 Iris+BSL 环境画面验收。 |
| :4677、4684、4694、4733 灯光轨道无可编辑片段 | :4670 显示手动创建了“灯光轨道 1”，之后添加关键帧找不到目标片段。此警告不说明灯光渲染失败。当前设计应先在灯光面板放置光源，CreateLightCommand 会同时创建对应轨道、片段和光源资源，再在片段范围内添加关键帧。空轨道没有绑定光源，不应盲目创建无主关键帧。本轮验证现有灯光关键帧回归通过。 |
| 摄像机关键帧添加被拒 16 条 | 日志提示应先创建自定义路径片段，属于编辑目标保护，不能据此推断摄像机损坏。 |
| 关闭 UI 时 DockSpace 跟踪记录残留 8 组 | 与前次相同，可能由关闭发生在渲染调用中途引起，日志本身不足以证明实际 ImGui 泄漏；本轮未改动关闭顺序。 |
| StatusBar 负优先级、重复属性注册、重复容器片段 | 前者与源码刻意设置 -10 的设计有关，后两者为防重复保护；面板八次初始化均为成功 11、失败 0。 |

Voxy GPU 内存等待、可选模组类缺失、网络版本检查超时属于其他模块的记录，本轮没有修改这些模组。日志结束有玩家退出、服务器停止和资源清理，没有发现 Director 致命崩溃的证据。

## 验证与交付

- 先运行预览缓冲生命周期测试及 LightKeyframeAuthoringTest，全部通过。
- `gradlew.bat test remapJar --offline --continue` 成功。
- 全量 **1178 项测试通过**，无失败、错误或跳过；`git diff --check` 通过。
- 已重新生成可替换模组：`build/libs/director-1.0.0.jar`。
- 尚未进行原存档游戏内视觉复测；优先复测面选择悬停、确认选区，以及填充/轮廓开关组合，观察是否仍出现 Not building!。
