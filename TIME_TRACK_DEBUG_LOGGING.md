# 时间轨道系统 - 调试日志说明

## 📋 已添加的调试日志

为了追踪"设置时间后下一tick就被覆盖"的问题，我们在以下关键位置添加了详细的调试日志：

### 1. TimelineWorldController 关键方法

#### `update()` - 每tick更新
```
[TimelineWorld] 🔄 update() 调用 - 播放中={}, scrubbing={}, 当前时间={}s, 冻结状态={}, frozenGameTime={}
```

#### `applyTimeSample()` - 应用时间采样
```
[TimelineWorld] 📍 applyTimeSample() 调用 - 时间={}s
[TimelineWorld] ✅ applyTimeSample() - 采样成功: worldTime={}, useWorldTime={}, freezeWorldTime={}
```

#### `previewAt()` - 预览时间
```
[TimelineWorld] 👁️ previewAt() 调用 - 时间={}s
[TimelineWorld] ✅ previewAt() - 采样成功: worldTime={}, useWorldTime={}, freezeWorldTime={}
```

#### `previewAtKeyframeTime()` - 预览关键帧位置
```
[TimelineWorld] 🎯 previewAtKeyframeTime() 调用 - 关键帧时间={}s, 当前冻结状态={}, frozenGameTime={}
[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览前更新冻结状态
[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览后再次更新冻结状态
[TimelineWorld] ✅ previewAtKeyframeTime() - 完成，当前冻结状态={}, frozenGameTime={}
```

#### `applySampleInternal()` - 应用采样内部
```
[TimelineWorld] 🔧 applySampleInternal() 调用 - worldTime={}, useWorldTime={}, freezeWorldTime={}, quiet={}
[TimelineWorld] 📊 applySampleInternal() - 更新缓存: 旧时间={}, 新时间={}, 冻结状态={}
[TimelineWorld] ⏰ applySampleInternal() - 准备应用世界时间: normalizedTicks={}, freezeTimeActive={}
[TimelineWorld] ✅ applySampleInternal() - 世界时间已应用，当前 frozenGameTime={}
```

#### `applyWorldTime()` - 应用世界时间
```
[TimelineWorld] 🌍 applyWorldTime() 调用 - 新时间={}, 旧时间={}, freezeTimeActive={}, 当前frozenGameTime={}
[TimelineWorld] 🔨 applyWorldTime() - 调用 world.setTime({}, {}, false)
[TimelineWorld] ❄️ applyWorldTime() - 更新冻结时间快照: {} -> {}
[TimelineWorld] ✅ applyWorldTime() - 设置成功，验证通过
```

#### `maintainFrozenTime()` - 维持冻结时间（Mixin调用）
```
[TimelineWorld] ❄️ maintainFrozenTime() - 检测到时间不一致! 期望={}, 当前世界时间={}, 强制恢复
[TimelineWorld] ❄️ maintainFrozenTime() - 维持冻结时间: {} ticks (当前世界时间一致)
[TimelineWorld] ❌ maintainFrozenTime() - 恢复失败! 期望={}, 实际={}
```

#### `freezeMinecraftTime()` - 冻结Minecraft时间
```
[TimelineWorld] ❄️ freezeMinecraftTime() 调用开始
[TimelineWorld] 🌍 freezeMinecraftTime() - 当前世界时间={}, hasOverride={}, currentSample={}
[TimelineWorld] ✅ freezeMinecraftTime() - 使用采样时间作为冻结时间: {}
[TimelineWorld] ⚠️ freezeMinecraftTime() - 无活跃采样，使用当前世界时间: {}
[TimelineWorld] ✅ freezeMinecraftTime() - 冻结完成: freezeTimeActive=true, frozenGameTime={}
```

#### `updateTimeFreezeState()` - 更新冻结状态
```
[TimelineWorld] 🔄 updateTimeFreezeState() - shouldFreeze={}, 当前freezeTimeActive={}
[TimelineWorld] ❄️ updateTimeFreezeState() - 触发冻结
[TimelineWorld] 🌊 updateTimeFreezeState() - 触发恢复
[TimelineWorld] ✅ updateTimeFreezeState() - 完成: {} -> {}, frozenTime: {} -> {}
```

#### `rebuildFromActiveTrack()` - 重建采样器
```
[TimelineWorld] 🔨 rebuildFromActiveTrack() 调用 - 当前冻结状态={}, frozenGameTime={}
[TimelineWorld] 📋 rebuildFromActiveTrack() - 轨道'{}'，关键帧数量={}
[TimelineWorld]   - 关键帧: 时间={}s, worldTime={}, useNaturalTime={}
[TimelineWorld] ✅ rebuildFromActiveTrack() - 完成，当前冻结状态={}, frozenGameTime={}
```

### 2. TimeKeyframePropertyAdapter 用户操作

#### 属性面板修改
```
[TimeKeyframePropertyAdapter] 🎯 用户修改世界时间: {} -> {}
[TimeKeyframePropertyAdapter] 🎯 用户点击'正午'按钮，设置时间=6000
[TimeKeyframePropertyAdapter] ✅ 检测到参数变化，准备应用 - 关键帧时间={}s, 世界时间={}, useNaturalTime={}
[TimeKeyframePropertyAdapter] 🔨 调用 rebuildFromActiveTrack()
[TimeKeyframePropertyAdapter] 🎯 调用 previewAtKeyframeTime({})
[TimeKeyframePropertyAdapter] ✅ 参数变化处理完成
```

### 3. ClientWorldMixin - Mixin拦截

#### tickTime拦截
```
[ClientWorldMixin] ❄️ tickTime() 被拦截 - 冻结时间系统
```

## 🔍 调试步骤

### 步骤1：设置时间并观察日志

1. **打开游戏日志**（控制台或日志文件）
2. **打开 ChronoBlocks 界面**
3. **选中一个时间关键帧**
4. **在属性面板点击"正午"按钮**

观察日志输出，应该看到：

```
[TimeKeyframePropertyAdapter] 🎯 用户点击'正午'按钮，设置时间=6000
[TimeKeyframePropertyAdapter] ✅ 检测到参数变化，准备应用
[TimelineWorld] 🔨 rebuildFromActiveTrack() 调用
[TimelineWorld] 🎯 previewAtKeyframeTime() 调用 - 关键帧时间=X.Xs
[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览前更新冻结状态
[TimelineWorld] ❄️ freezeMinecraftTime() 调用开始
[TimelineWorld] 🌍 freezeMinecraftTime() - 当前世界时间=XXXX
[TimelineWorld] 👁️ previewAt() 调用 - 时间=X.Xs
[TimelineWorld] 🔧 applySampleInternal() 调用 - worldTime=6000
[TimelineWorld] 🌍 applyWorldTime() 调用 - 新时间=6000
[TimelineWorld] ✅ applyWorldTime() - 设置成功
```

### 步骤2：观察下一tick的行为

设置时间后，等待1-2秒，观察日志：

1. **是否调用了 `update()`？** 如果调用了，它会在什么时候调用？
2. **`maintainFrozenTime()` 被调用了多少次？** 每次调用时使用的 `frozenGameTime` 值是什么？
3. **`applyWorldTime()` 被调用了多少次？** 每次设置的时间值是什么？
4. **时间是在什么时候被覆盖的？** 是在 `update()` 中，还是在 `maintainFrozenTime()` 中？

### 步骤3：检查关键时序问题

重点关注以下时序：

1. **属性面板设置时间时的顺序：**
   - `rebuildFromActiveTrack()` 是否在 `previewAtKeyframeTime()` 之前？
   - `freezeMinecraftTime()` 是否在 `applyWorldTime()` 之前？
   - 如果 `freezeMinecraftTime()` 在 `applyWorldTime()` 之前，它使用的 `currentSample` 是什么？

2. **下一tick时的顺序：**
   - `update()` 是否被调用？如果是，它是否在播放/scrubbing状态？
   - `maintainFrozenTime()` 使用的 `frozenGameTime` 是什么值？
   - 时间是在哪个步骤被覆盖的？

## 📝 日志分析要点

### 关键问题检查清单

- [ ] **问题1：`freezeMinecraftTime()` 是否使用了错误的时间值？**
  - 检查：`freezeMinecraftTime()` 调用时，`hasOverride()` 和 `currentSample` 是什么？
  - 如果 `currentSample` 为 null，它会使用当前世界时间，这可能是问题所在

- [ ] **问题2：`update()` 是否在不应该的时候被调用？**
  - 检查：`update()` 是否在属性面板设置时间后被调用？
  - 如果调用了，它是否在播放/scrubbing状态？

- [ ] **问题3：`frozenGameTime` 是否在设置后立即被覆盖？**
  - 检查：`applyWorldTime()` 设置的时间是什么？
  - 然后检查：`maintainFrozenTime()` 使用的 `frozenGameTime` 是什么？
  - 如果两者不一致，说明 `frozenGameTime` 在设置后被覆盖了

- [ ] **问题4：时序问题 - 冻结状态更新是否在应用采样之前？**
  - 检查：`previewAtKeyframeTime()` 中，`updateTimeFreezeState()` 是否在 `previewAt()` 之前？
  - 如果是，`freezeMinecraftTime()` 可能使用了旧的 `currentSample`

## 🎯 预期问题场景

### 场景1：冻结时使用了错误的时间

```
1. 属性面板设置时间=6000
2. previewAtKeyframeTime() 调用
3. updateTimeFreezeState() 调用（预览前）
4. freezeMinecraftTime() 调用
   - 此时 currentSample 可能还是旧值或null
   - 使用当前世界时间（比如12000）作为 frozenGameTime
5. previewAt() 调用
6. applyWorldTime() 设置时间=6000
   - 但 freezeTimeActive 已经为 true
   - frozenGameTime 更新为 6000
7. 下一tick，maintainFrozenTime() 调用
   - 使用 frozenGameTime=6000（正确）
   - 但如果有其他代码在中间覆盖了时间，就会有问题
```

### 场景2：update() 在设置后立即调用

```
1. 属性面板设置时间=6000
2. previewAtKeyframeTime() 调用，设置时间=6000
3. 下一帧，update() 被调用（如果正在播放）
4. update() 采样当前播放头位置的时间
5. 如果播放头不在关键帧位置，采样得到的时间不是6000
6. applyTimeSample() 覆盖了设置的时间
```

## 📋 日志输出示例

### 正常情况（应该看到的）

```
[TimeKeyframePropertyAdapter] 🎯 用户点击'正午'按钮，设置时间=6000
[TimelineWorld] 🔨 rebuildFromActiveTrack() 调用
[TimelineWorld] 🎯 previewAtKeyframeTime() 调用 - 关键帧时间=0.0s
[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览前更新冻结状态
[TimelineWorld] ❄️ freezeMinecraftTime() 调用开始
[TimelineWorld] 👁️ previewAt() 调用 - 时间=0.0s
[TimelineWorld] 🔧 applySampleInternal() 调用 - worldTime=6000
[TimelineWorld] 🌍 applyWorldTime() 调用 - 新时间=6000
[TimelineWorld] ✅ applyWorldTime() - 设置成功
[TimelineWorld] ✅ previewAtKeyframeTime() - 完成

# 下一tick
[ClientWorldMixin] ❄️ tickTime() 被拦截
[TimelineWorld] ❄️ maintainFrozenTime() - 维持冻结时间: 6000 ticks
```

### 问题情况（可能看到的）

```
[TimelineWorld] 🔧 applySampleInternal() 调用 - worldTime=6000
[TimelineWorld] 🌍 applyWorldTime() 调用 - 新时间=6000
[TimelineWorld] ✅ applyWorldTime() - 设置成功

# 下一tick - 问题出现
[TimelineWorld] 🔄 update() 调用 - 播放中=false, scrubbing=false
[TimelineWorld] 📍 applyTimeSample() 调用 - 时间=5.0s  <-- 播放头不在关键帧位置
[TimelineWorld] 🔧 applySampleInternal() 调用 - worldTime=9000  <-- 采样得到的时间不是6000
[TimelineWorld] 🌍 applyWorldTime() 调用 - 新时间=9000  <-- 覆盖了6000
```

## 🔧 下一步

根据日志输出，我们可以确定：

1. **时间是在哪个步骤被覆盖的？**
2. **是 `update()` 的问题，还是 `freezeMinecraftTime()` 的问题？**
3. **时序是否正确？冻结状态是否在应用采样之前更新？**

请运行游戏，设置时间，然后将日志输出发给我，我可以帮您分析问题。

