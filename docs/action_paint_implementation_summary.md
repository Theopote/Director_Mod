# Action 涂抹式编辑实现总结

## ✅ 已完成的核心功能

### 1. **ActionBrushState** ✅
- 位置：`com.director.ui.timeline.interaction.ActionBrushState`
- 功能：全局轻量状态（当前绘制动作）
- 状态：已完成

### 2. **ActionClipIntervalResolver** ✅
- 位置：`com.director.ui.timeline.interaction.ActionClipIntervalResolver`
- 功能：区间切割算法（涂抹式编辑的核心）
- 算法：已完成

### 3. **TimelineInteractionManager** ✅
- 修改：在 `InteractionMode` 枚举中添加了 `PAINT_ACTION` 模式
- 状态：已完成

### 4. **EntityTrack** ✅
- 新增方法：
  - `getActionClips()` - 获取所有 ActorActionClip
  - `replaceActionClips(List<ActorActionClip> newClips)` - 替换 ActionClips
- 状态：已完成

### 5. **TimelineInteractionHandler 涂抹逻辑** ✅（部分）
- **已完成**：
  - `ActionPaintHandle` 内部类
  - `canStartActionPaint()` - 检查是否可以涂抹
  - `startActionPaint()` - 开始涂抹
  - `paintAction()` - 拖拽过程中更新结束时间
  - `applyActionPaint()` - 应用涂抹结果
  - `handleDrag()` 中添加了 `PAINT_ACTION` case
  - `endInteractionIfNeeded()` 中添加了 `PAINT_ACTION` case

- **待完成**：
  - `handleMouseButtons()` 中的 `case EMPTY` 需要修改为检查涂抹模式

## 🚧 待完成的工作

### 1. **修复 case EMPTY 处理**
需要在 `handleMouseButtons()` 中修改 `case EMPTY`，使其：
- 检查是否在 EntityTrack 上
- 检查是否可以涂抹（`canStartActionPaint`）
- 如果可以，调用 `startActionPaint()`
- 否则，调用 `startMarqueeCandidate()`（原有逻辑）

**当前代码**（476-485行）：
```java
// 2) 根据 hit 类型分发
switch (hit.type()) {
    case CLIP -> onClipClicked(hit, mouseX, contentX, isDoubleClick);
    case KEYFRAME -> onKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
    case AUTOMATION_KEYFRAME -> onAutomationKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
    case EMPTY -> startMarqueeCandidate(mouseX, mouseY, trackAreaTop, scrollY);
    default -> { /* NONE / TRACK_HEADER 在这里忽略 */ }
}
```

**需要修改为**：
```java
// 2) 根据 hit 类型分发
// ⭐ Action 涂抹：如果是在 EntityTrack 上点击 EMPTY 区域，尝试开始涂抹
if (hit.type() == TimelineHitResult.HitType.EMPTY && hit.track() != null) {
    TimelineTrack track = hit.track();
    if (track.getType() == TrackType.ENTITY && canStartActionPaint(track)) {
        // 开始 Action 涂抹
        startActionPaint(hit, mouseX, contentX);
    } else {
        // 普通框选
        startMarqueeCandidate(mouseX, mouseY, trackAreaTop, scrollY);
    }
} else {
    switch (hit.type()) {
        case CLIP -> onClipClicked(hit, mouseX, contentX, isDoubleClick);
        case KEYFRAME -> onKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
        case AUTOMATION_KEYFRAME -> onAutomationKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
        case EMPTY -> startMarqueeCandidate(mouseX, mouseY, trackAreaTop, scrollY);
        default -> { /* NONE / TRACK_HEADER 在这里忽略 */ }
    }
}
```

### 2. **UI Action Brush 选择器** 🚧
- 位置：`ActorProjectPropertyAdapter` 或 `ActorActionClipPropertyAdapter`
- 需要：添加 Action Brush 下拉菜单，用于选择当前绘制动作

### 3. **拖拽预览渲染** 🚧
- 位置：`TimelineClipRenderer` 或 `TimelineRenderer`
- 需要：拖拽过程中显示半透明色块预览

### 4. **交互护栏** ✅（部分）
- `canStartActionPaint()` 已实现 RigProfile 支持检查
- 待完善：UI 提示（Tooltip）

## 📋 当前状态

**核心逻辑已完成**，但 `case EMPTY` 的处理还没有修改，导致涂抹功能无法触发。

**修复步骤**：
1. 修改 `handleMouseButtons()` 中的 `case EMPTY` 处理
2. 测试涂抹功能

完成后，Action 涂抹式编辑的核心功能即可使用。
