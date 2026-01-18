# Action 涂抹式编辑：case EMPTY 修复指南

## 问题描述

`TimelineInteractionHandler.java` 中的 `case EMPTY` 处理还没有修改，导致涂抹功能无法触发。

**当前代码**（478-485行）：
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

## 修复方案

需要将 `case EMPTY` 的处理改为**先检查是否可以涂抹，如果可以则开始涂抹，否则使用原有逻辑**。

**修复后的代码**：
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

## 手动修复步骤

1. 打开 `TimelineInteractionHandler.java`
2. 定位到第 478 行（`// 2) 根据 hit 类型分发`）
3. 将整个 `switch (hit.type())` 语句块替换为上面的修复后代码

## 修复后的行为

- **在 EntityTrack 上点击 EMPTY 区域**：
  - 如果 `canStartActionPaint(track)` 返回 `true` → 开始涂抹（调用 `startActionPaint()`）
  - 否则 → 普通框选（调用 `startMarqueeCandidate()`）

- **在其他地方点击 EMPTY 区域**：
  - 使用原有逻辑（框选）

## 测试

修复后，应该能够：
1. 在 ActorProjectPropertyAdapter 中选择 Action Brush（如 RUN）
2. 在 Timeline 的 EntityTrack 上拖拽一段
3. 系统自动创建 ActorActionClip（RUN 动作区间）
4. 如果拖拽的区域与已有 ActionClip 重叠，自动切割区间
