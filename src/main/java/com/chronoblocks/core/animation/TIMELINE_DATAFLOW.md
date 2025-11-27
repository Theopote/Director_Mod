# ChronoBlocks 时间线数据流总结

## 整体架构

```
TimelineClip (startTime, length)
    │
    ├─ SequenceParameters  → BlockSequenceGenerator
    │                           ↓
    │                   BlockSequenceEntry (per block: normalizedTime, groupId)
    │                           ↓
    └─ ClipAnimationConfig (mode, easing, duration, params, effects[])
                                ↓
                     BlockAnimationRenderer.renderBlock()
                     ├─ 动画变换：Transformer.transform(matrices, easedT, params)
                     └─ 特效：for each EffectInstance →
                               effectT = (blockLocalNorm - startOffset) / duration
                               IBlockEffect.render(world, pos, effectT, inst, random)
```

## 三层时间映射

### 1. 序列行为（Sequence Behavior）
**决定：谁先出现**

```java
float normalizedSeqTime = seqEntry.getNormalizedTime(); // [0..1]
float seqStartTime = clipStartTime + clipLength * normalizedSeqTime;
```

**含义**：
- `normalizedSeqTime = 0.0` → 第一个方块，在 `clipStartTime` 开始
- `normalizedSeqTime = 1.0` → 最后一个方块，在 `clipEndTime` 开始
- `normalizedSeqTime = 0.5` → 中间方块，在片段中点开始

**示例**：
- Clip 从 10s 开始，长度 2s
- 方块 A 的 `normalizedSeqTime = 0.3`
- → `seqStartTime = 10 + 2 * 0.3 = 10.6s`

### 2. 动画行为（Animation Behavior）
**决定：怎么出现（位移/旋转/缩放）**

```java
float localAnimTime = elapsedTime - seqStartTime;        // 方块局部时间（绝对秒数）
float animProgress = localAnimTime / config.getDuration(); // [0..1]
float easedT = easing.apply(animProgress);               // 应用缓动曲线
```

**含义**：
- `animProgress = 0.0` → 动画刚开始（初始状态）
- `animProgress = 1.0` → 动画结束（最终状态）
- `easedT` → 经过缓动函数重新映射后的进度值，传给 Transformer

**示例**：
- `duration = 0.5s`（动画时长）
- `localAnimTime = 0.25s`（已经播放了 0.25 秒）
- → `animProgress = 0.25 / 0.5 = 0.5`（动画进行到一半）
- → `easedT = EasingFunction.EASE_OUT.apply(0.5) ≈ 0.75`（缓动后更快）

### 3. 特效行为（Effect Behavior）
**决定：视觉装饰（粒子/光效/扫描线等）**

```java
float blockLocalNorm = localAnimTime / clipLength;       // 方块在片段中的归一化进度 [0..1]
float effectT = (blockLocalNorm - startOffset) / duration; // 特效局部进度 [0..1]
```

**含义**：
- `startOffset` 和 `duration` 都是归一化的 `[0..1]`，相对于片段长度
- `blockLocalNorm = 0.0` → 方块刚开始在片段中出现
- `blockLocalNorm = 1.0` → 方块完全出现（片段结束）
- 每个 EffectInstance 有独立的 `startOffset` 和 `duration`

**示例**：
- `clipLength = 2.0s`
- `localAnimTime = 0.5s`
- → `blockLocalNorm = 0.5 / 2.0 = 0.25`（片段进行到 25%）
- 特效 A：`startOffset = 0.0`, `duration = 0.3` → `effectT = 0.25 / 0.3 = 0.83`（特效进行到 83%）
- 特效 B：`startOffset = 0.2`, `duration = 0.2` → `effectT = (0.25 - 0.2) / 0.2 = 0.25`（特效刚开始）

## 完整渲染流程

### BlockAnimationRenderer.renderBlock() 执行顺序

1. **序列判断**：`if (elapsedTime < seqStartTime) return;` → 方块还没轮到

2. **计算动画进度**：
   - `localAnimTime = elapsedTime - seqStartTime`
   - `animProgress = localAnimTime / duration`
   - `easedT = easing.apply(animProgress)`

3. **应用动画变换**：
   - `matrices.push()`
   - `matrices.translate(pos.getX(), pos.getY(), pos.getZ())` → 移动到世界坐标
   - `matrices.translate(0.5, 0.5, 0.5)` → 移动到方块中心
   - `transformer.transform(easedT, params, random)` → 应用位移/旋转/缩放
   - `matrices.translate(-0.5, -0.5, -0.5)` → 移回原点

4. **执行特效**（不修改 MatrixStack）：
   - 对每个启用的 `EffectInstance`：
     - 计算 `blockLocalNorm = localAnimTime / clipLength`
     - 计算 `effectT = (blockLocalNorm - startOffset) / duration`
     - 如果 `effectT ∈ [0,1]`，调用 `effect.render(world, pos, effectT, inst, random)`

5. **渲染方块本体**：
   - `blockRenderManager.renderBlockAsEntity(blockState, matrices, consumers, ...)`
   - `matrices.pop()`

## 关键设计决策

### ✅ 为什么特效时间基于 clipLength 而不是 animDuration？

**原因**：
- 动画模式可能是 `INSTANT`（duration ≈ 0），如果基于 animDuration，特效会无法触发
- 特效应该是"方块出现在片段中的视觉装饰"，而不是"动画过程的装饰"
- 这样可以实现"落地后爆尘"等效果（startOffset 设为接近 1.0）

### ✅ 为什么 startOffset 和 duration 归一化到 [0..1]？

**原因**：
- 片段长度可以变化，归一化后特效时间会自动适应
- UI 中可以用百分比展示，更直观
- 时间轴预览可以直接用归一化值绘制

### ✅ 特效与动画的顺序？

**顺序**：Transform → Effect → Render Block

**原因**：
- Transform 修改 MatrixStack（位移/旋转/缩放）
- Effect 不修改 MatrixStack，只产生粒子/光效
- 特效应该在方块渲染前执行（某些特效可能需要读取方块状态）

## 示例时间线

```
时间轴: 0s -------- 1s -------- 2s (clipLength = 2s)

方块 A (normalizedSeqTime = 0.2):
  seqStartTime = 0.4s
  localAnimTime:
    0.4s: 0.0s (开始)
    0.6s: 0.2s (进行中)
    0.9s: 0.5s (动画结束，duration = 0.5s)
    2.0s: 1.6s (片段结束)

特效 A (FlashWhite, startOffset=0.0, duration=0.3):
  在 blockLocalNorm ∈ [0.0, 0.3] 时激活
  即 localAnimTime ∈ [0.0s, 0.6s] 时触发

特效 B (DustBurst, startOffset=0.8, duration=0.2):
  在 blockLocalNorm ∈ [0.8, 1.0] 时激活
  即 localAnimTime ∈ [1.6s, 2.0s] 时触发（落地后）
```

## 总结

- **序列行为**：决定方块出现的顺序（`normalizedSeqTime` → `seqStartTime`）
- **动画行为**：决定方块如何运动（`localAnimTime` → `animProgress` → `easedT` → `Transformer`）
- **特效行为**：决定视觉装饰（`blockLocalNorm` → `effectT` → `IBlockEffect.render()`）

三者完全解耦，可以独立配置和叠加。

