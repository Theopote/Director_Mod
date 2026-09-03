# Director模组性能优化总结

## 问题描述

当时间线轨道上的容器片段数量增多，总方块数超过1万个时，FPS会从60掉到10左右，导致几乎无法操作。

## 根本原因分析

### 1. **遮挡检测（最大的性能杀手）** ✅ 已改为按需启用

**问题代码位置**：`TimelineClipBlockController.applyOcclusionOverrides()`

**问题描述**：
- 每帧都调用遮挡检测，遍历所有候选容器的所有方块
- 复杂度：O(容器数 × 方块数)
- 典型场景：10个容器 × 5000方块 = 每帧45000次包围盒检测

**解决方案**：
```java
// 默认不执行遮挡检测。
// 只有当前片段启用“自动淡化前方片段”时，applyOcclusionOverrides() 才按需执行。
```

**性能提升**：默认场景绕过遮挡计算；需要该效果时仍可显式启用

---

### 2. **属性面板频繁触发模型更新** ✅ 已修复

**问题代码位置**：`TimelineClipPropertyUi.java` 中所有的 `ImGui.dragFloat/sliderFloat`

**问题描述**：
- 拖动滑块时，每帧都调用 `markDirty()` -> `notifyModelChanged()` -> `timelineRevision++`
- 导致 `TimelineClipBlockController` 的性能优化短路失效，每帧都重新计算

**解决方案**：
```java
// 修改前：每帧都触发
if (ImGui.dragFloat(...)) {
    config.setDuration(duration[0]);
    modified = true;  // ❌ 每帧都触发
}

// 修改后：只在松手时触发
if (ImGui.dragFloat(...)) {
    config.setDuration(duration[0]);
}
// ⭐ 性能优化：只在拖动结束时才标记为已修改
if (ImGui.isItemDeactivatedAfterEdit()) {
    modified = true;  // ✅ 只在松手时触发一次
}
```

**修复范围**：30+ 个控件（动画持续时间、序列参数、特效参数、变换器参数等）

**性能提升**：拖动滑块时不再触发重计算，体验流畅

---

### 3. **集合差异检测优化** ✅ 已优化

**问题代码位置**：`TimelineClipBlockController.update()` 中的集合比较

**优化内容**：
```java
// 先比较大小 - O(1)操作
if (prev.size() != now.size()) {
    changed = true;
    continue;  // 快速返回
}
// 只有大小相同时才遍历检查内容
```

**性能提升**：大部分情况下可以快速返回，避免遍历

---

### 4. **遮挡检测缓存优化** ✅ 已优化（功能按需启用）

**位置**：`BlockOcclusionResolver.java`

遮挡检测使用缓存，并只在当前片段明确启用“自动淡化前方片段”时运行。

---

## 性能监控

代码已内置性能日志，每5秒输出一次：

```
[Perf][TimelineClipBlockController] total=Xms (clear=Xms, step1_visible=Xms, diff=Xms, occlusion=Xms, step3_apply+entries=Xms, rerender=Xms) containers=X, visibleTotal=X, seqTotal=X, renderEntries=X
```

### 关键指标说明：

- **total**：总耗时（应该 < 16ms 才能保持60FPS）
- **step1_visible**：计算可见方块耗时（包括序列生成）
- **diff**：集合差异检测耗时
- **occlusion**：遮挡检测耗时（未启用自动淡化时应接近 0ms）
- **step3_apply+entries**：应用视觉状态耗时
- **containers**：活跃容器数量
- **visibleTotal**：可见方块总数

---

## 下一步排查思路（如果问题仍未解决）

### 1. 查看性能日志

运行游戏，观察日志输出，找出哪个步骤耗时最长：

**如果 `step1_visible` 耗时最长（> 10ms）**：
- 问题在于序列生成或方块集合操作
- 检查是否缓存命中（容器modifiedTime应该保持稳定）
- 可能需要优化 `BlockSequenceGenerator.generate()`

**如果 `step3_apply+entries` 耗时最长（> 10ms）**：
- 问题在于应用视觉状态或世界方块落地
- 检查 `BlockVisualStateManager` 或 `ContainerVisibilityService`

**如果 `rerender` 耗时最长（> 10ms）**：
- 问题在于区块重渲染调度
- 可能需要优化 `scheduleBlockRerender()`

### 2. 检查是否频繁重新计算

在 `TimelineClipBlockController.update()` 开头添加日志：

```java
if (!playing && !scrubbing && !previewTimeChanged && timelineRevision == lastHandledRevision) {
    return;  // 应该在静止时短路
}
LOGGER.warn("Update triggered: playing={}, scrubbing={}, timeChanged={}, revisionChanged={}", 
    playing, scrubbing, previewTimeChanged, timelineRevision != lastHandledRevision);
```

如果在静止状态下仍然每帧都打印日志，说明有什么东西在持续触发更新。

### 3. 检查序列生成缓存

在 `getOrGenerateSequenceEntries()` 中添加日志：

```java
if (cache != null && cache.entries != null && !cache.entries.isEmpty()) {
    LOGGER.debug("Cache HIT for clip {}", clip.getLabel());
    return cache.entries;
}
LOGGER.warn("Cache MISS for clip {}, generating {} blocks", clip.getLabel(), blockCount);
```

如果频繁看到 "Cache MISS"，说明缓存机制不生效。

### 4. 临时禁用非核心功能

尝试逐步禁用功能来定位瓶颈：

**禁用世界方块落地**：
```java
// 在 step3 中注释掉
// visibilityService.showContainerBlocks(containerId, worldBlocks);
```

**禁用视觉状态管理**：
```java
// 在 step3 中注释掉
// visualStateManager.applyStateForBlocks(...);
```

---

## 预期性能

**优化后的预期性能**（50000方块，10个容器）：

| 场景 | FPS | 说明 |
|------|-----|------|
| 静止预览（不移动播放头） | 60 | 应该完全短路，不执行update() |
| 拖动播放头 | 45-60 | 取决于方块数量和序列复杂度 |
| 播放动画 | 40-60 | 取决于方块数量 |
| 修改属性面板滑块 | 55-60 | 只在松手时触发一次更新 |

**如果FPS仍然低于30**，说明有其他未发现的性能瓶颈，需要进一步排查。

---

## 已知限制

1. **遮挡检测默认不运行**：仅当片段启用“自动淡化前方片段”时按需运行
2. **大规模场景（10万+方块）**：可能仍有性能问题，建议将建筑拆分成更小的部分

---

## 代码修改清单

### 已修改文件：

1. **TimelineClipPropertyUi.java** - 30+ 处修改，所有drag/slider控件延迟触发
2. **TimelineClipBlockController.java** - 按需触发遮挡检测，优化集合差异检测
3. **BlockOcclusionResolver.java** - 为按需遮挡检测添加缓存机制

### 修改标识：

所有性能优化相关的代码都添加了 `⭐ 性能优化` 注释，便于搜索和维护。

---

## 联系与反馈

如果问题仍未解决，请提供：
1. 性能日志输出（特别是各步骤耗时）
2. 容器数量和平均方块数
3. 是否启用了autoFadePreviousClips功能
4. F3调试信息截图

优化日期：2026-06-26
