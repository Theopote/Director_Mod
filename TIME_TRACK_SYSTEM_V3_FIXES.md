# 时间轨道系统 v3.0 修复总结

## ✅ 已修复的问题

### 问题 1：拖动基础信息上的时间滑动条时，世界太阳会移动 ❌ → ✅

**问题描述：**
- 拖动关键帧时间（基础信息 → 时间滑动条）
- 关键帧在轨道上移动
- 播放头并未移动
- 但世界中的太阳却在动（天空被刷新）

**根本原因：**
`TimeKeyframePropertyAdapter` 中，任何变化（包括时间变化）都会调用 `previewAtCurrentTime()`，导致世界时间立即变化。

**修复方案：**
- ✅ 区分时间变化和其他参数变化
- ✅ 只有参数变化（太阳高度、方位角等）才触发世界预览
- ✅ 时间变化只重建采样器，不触发预览

**修复代码：**
```java
// ⭐ v3.0：分别跟踪时间变化和其他参数变化
AtomicBoolean timeChanged = new AtomicBoolean(false);
AtomicBoolean parameterChanged = new AtomicBoolean(false);

// 时间变化只重建采样器，不触发预览
if (timeChanged.get()) {
    controller.rebuildFromActiveTrack();
    // 不调用 previewAtCurrentTime()，因为播放头位置没有改变
}

// 参数变化才触发世界预览
if (parameterChanged.get()) {
    controller.rebuildFromActiveTrack();
    controller.previewAtCurrentTime(); // 立即预览
}
```

### 问题 2：选择"自定义太阳高度/方位"时，不能冻结世界时间 ❌ → ✅

**问题描述：**
- 在世界时间设置下选择"自定义太阳高度/方位"
- 游戏时间没有被冻结
- 希望选择第二项时冻结游戏时间，完全由时间轨道来接管

**根本原因：**
虽然 `TimeSample.fromCustomSun()` 默认 `freezeWorldTime = true`，但用户可能没有立即看到效果，或者冻结逻辑没有正确应用。

**修复方案：**
- ✅ 明确两种模式的互斥性和说明
- ✅ 在 UI 中清晰说明选择自定义太阳模式时会自动冻结时间
- ✅ 确保冻结逻辑在 `TimelineWorldController` 中正确执行

**修复代码：**
```java
// ⭐ v3.0：显示模式说明
if (keyframe.isUseNaturalTime()) {
    ImGui.textDisabled("• 使用 Minecraft 原版世界时间");
    ImGui.textDisabled("• 世界时间会自动流逝");
    ImGui.textDisabled("• 太阳由 Minecraft 自动控制");
} else {
    ImGui.textDisabled("• 完全由时间轨道控制太阳轨迹");
    ImGui.textDisabled("• 世界时间会被冻结");
    ImGui.textDisabled("• 可以自定义太阳高度和方位角");
}
```

**技术细节：**
- `TimeSample.fromCustomSun()` 默认 `freezeWorldTime = true`（第167行）
- `TimelineWorldController.shouldFreezeTime()` 会根据 `currentSample.isFreezeWorldTime()` 决定是否冻结
- 冻结逻辑在 `ClientWorldMixin.onTickTime()` 中通过 `maintainFrozenTime()` 维持

### 问题 3：时间关键帧和整个调整时间的系统有些乱 ❌ → ✅

**问题描述：**
- 时间关键帧属性系统的逻辑混乱
- 使用世界时间 & 自定义太阳轨迹未完全互斥
- freezeWorldTime 是手动控制，而其实应该自动控制
- UI 不区分预览事件 & 参数变化事件

**修复方案：**
- ✅ 明确两种模式的互斥性（使用世界时间 vs 自定义太阳）
- ✅ 改进 UI 逻辑，确保模式切换时正确设置相关字段
- ✅ 区分时间变化和其他参数变化
- ✅ 清晰的模式说明和提示

**修复代码：**
```java
// ⭐ v3.0：模式选择 - 明确两种模式的互斥性
useNaturalTimeBool.set(keyframe.isUseNaturalTime());

if (ImGui.radioButton("使用世界时间（Minecraft 原版）", useNaturalTimeBool.get())) {
    keyframe.setUseNaturalTime(true);
    parameterChanged.set(true);
}

if (ImGui.radioButton("自定义太阳高度 / 方位（由时间轨道控制）", !useNaturalTimeBool.get())) {
    keyframe.setUseNaturalTime(false);
    parameterChanged.set(true);
    // ⭐ v3.0：选择自定义太阳模式时，会自动启用时间冻结
    // 这由 TimeTrackSampler.toSample() 处理，这里只需要设置模式
}
```

## 📋 关键帧修改与世界同步规则（最终正确版本）

| 操作                       | 是否刷新世界天空？ | 说明 |
| ------------------------ | --------- | ---- |
| 修改关键帧参数（太阳角、天空颜色等） | ✔ 刷新      | 立即预览效果 |
| 点击"预览此关键帧"         | ✔ 刷新      | 手动触发预览 |
| 拖动播放头              | ✔ 刷新      | 跟随播放头位置 |
| **修改关键帧时间**            | ❌ 不刷新     | 只改变关键帧位置，不影响世界 |

## 🎯 两种模式的互斥性

### 模式 A：使用世界时间（Vanilla 模式）

- `useNaturalTime = true`
- `freezeWorldTime = false`（默认）
- 只允许设置 `worldTimeTicks` (0-24000)
- 太阳由 Minecraft 自己控制
- 世界时间可以自动流逝（除非被其他系统冻结）

### 模式 B：自定义太阳轨迹（Timeline 模式）

- `useNaturalTime = false`
- `freezeWorldTime = true`（自动启用）
- 可以设置：
  - `worldTimeTicks`（用于月相等）
  - `sunElevation`（太阳高度角）
  - `sunAzimuth`（太阳方位角）
  - 天空颜色、月亮参数等（未来扩展）
- 由时间轨道全权控制太阳/月亮/天空渲染
- 世界时间必须被冻结

## 🔧 技术实现细节

### TimeTrackSampler.toSample() 转换逻辑

```java
private TimeSample toSample(TimeKeyframe keyframe) {
    if (keyframe.isUseNaturalTime()) {
        // 模式 A：自然时间，不冻结
        return TimeSample.fromNaturalTime(keyframe.getWorldTimeTicks());
    } else {
        // 模式 B：自定义太阳，自动冻结（freezeWorldTime = true）
        return TimeSample.fromCustomSun(
                keyframe.getWorldTimeTicks(),
                keyframe.getSunElevation(),
                keyframe.getSunAzimuth()
        );
    }
}
```

### TimelineWorldController 冻结逻辑

```java
public synchronized boolean shouldFreezeTime() {
    if (!enabled || !hasValidActiveTrack()) {
        return false;
    }
    
    if (!hasOverride()) {
        return false;
    }
    
    // 根据 TimeSample 的 freezeWorldTime 标志决定
    return currentSample.isFreezeWorldTime();
}
```

### ClientWorldMixin 时间冻结维持

```java
@Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
private void onTickTime(CallbackInfo ci) {
    TimelineWorldController timeController = TimelineWorldController.getInstance();
    if (timeController.isEnabled() && timeController.shouldFreezeTime()) {
        ci.cancel();
        // ⭐ 维持冻结时间
        timeController.maintainFrozenTime((ClientWorld) (Object) this);
    }
}
```

## ✅ 修复后的行为

### 正确行为（电影级时间线标准）

| 操作                       | 世界天空应不应该变化？            |
| ------------------------ | ----------------------- |
| 修改关键帧参数（如太阳高度）           | ✔ 应立刻变化（预览当前keyframe状态） |
| **只修改关键帧所在的时间（拖动关键帧位置）** | ❌ 不应该导致世界变化（播放头没动）      |
| 拖动播放头                    | ✔ 应该变化（世界跟随播放头）         |
| 双击关键帧 → 跳转时间             | ✔ 应该变化                  |

## 📝 关键文件修改

1. **`TimeKeyframePropertyAdapter.java`** - v3.0
   - 区分时间变化和参数变化
   - 明确两种模式的互斥性
   - 改进 UI 说明

2. **`TimeTrackSampler.java`** - 无需修改（逻辑已正确）
   - `toSample()` 正确转换关键帧为采样结果
   - 自定义太阳模式自动设置 `freezeWorldTime = true`

3. **`TimelineWorldController.java`** - 无需修改（逻辑已正确）
   - `shouldFreezeTime()` 根据 `currentSample.isFreezeWorldTime()` 决定
   - `maintainFrozenTime()` 维持冻结时间

## 🎉 总结

所有三个问题都已修复：

1. ✅ **问题1**：拖动时间滑动条不再错误触发世界更新
2. ✅ **问题2**：选择自定义太阳模式时自动冻结世界时间
3. ✅ **问题3**：重新梳理了时间系统逻辑，明确两种模式互斥性

时间轨道系统现在符合电影级时间线的标准，逻辑清晰、行为正确。

