# Timeline 视觉区分实现总结

## 一、实现目标

让导演在时间线上一眼看出：
- 哪些是「状态 / 意图区间」（Action）
- 哪些是「连续变化」（Transform / Path）
- 哪些是「瞬时事件」（ActionEvent / AnimationEvent）

## 二、已实现的功能

### 1. **TimelineClipVisualType 枚举** ✅
- 位置：`com.chronoblocks.ui.timeline.model.entity.TimelineClipVisualType`
- 定义了三种视觉类型：
  - `CURVE`：曲线类型（连续变化）
  - `BLOCK`：区块类型（状态区间）
  - `EVENT`：事件类型（瞬时）

### 2. **EntityClip.getVisualType() 方法** ✅
- 默认返回 `CURVE`
- 子类可以覆盖以返回不同的视觉类型

### 3. **各 Clip 类型的视觉类型映射** ✅
- `ActorActionClip` → `BLOCK`（状态区间）
- `EntityActionClip` → `EVENT`（瞬时事件）
- `EntityTransformClip` → `CURVE`（连续变化，默认）
- `EntityAnimationClip` → `CURVE`（连续变化，默认）
- `EntitySpawnClip` → `CURVE`（连续变化，默认）
- `EntityStateClip` → `CURVE`（连续变化，默认）
- `EntityLookAtClip` → `CURVE`（连续变化，默认）
- `EntityDespawnClip` → `CURVE`（连续变化，默认）

### 4. **EntityTimelineClip.getVisualType() 方法** ✅
- 委托给 `EntityClip.getVisualType()`
- 使 TimelineClipRenderer 可以通过 `EntityTimelineClip` 获取视觉类型

### 5. **TimelineClipRenderer 视觉类型分支** ✅
- `renderClip()` 方法根据视觉类型分支调用：
  - `renderCurveClip()`：曲线类型（TransformClip 等）
  - `renderBlockClip()`：区块类型（ActorActionClip）
  - `renderEventClip()`：事件类型（ActionEvent 等）

### 6. **Block Clip 绘制** ✅
- 实心区块，高度略大于 Curve
- 使用 ActionType 颜色（通过 `ActionTypeColors`）
- 中央显示 Action 名称
- **不显示关键帧**（这是底线）
- 不显示调整手柄（状态区间，通常不需要频繁调整）

### 7. **Event Clip 绘制** ✅
- 菱形标记（◆）
- 在开始时间位置显示
- 选中时显示外圈
- 不显示调整手柄和关键帧

### 8. **ActionTypeColors 颜色映射** ✅
- 位置：`com.chronoblocks.ui.timeline.render.ActionTypeColors`
- 为每个 ActionType 定义了稳定的颜色：
  - IDLE → 灰色
  - WALK → 蓝色
  - RUN → 橙色
  - DANCE → 紫色
  - CARRY → 绿色
  - THROW_PREPARE → 红色
  - ATTACK → 深红
  - CELEBRATE → 黄色
  - SIT → 棕色
  - MEDITATE → 青色
  - GRAZE → 草绿
  - ROAR → 深橙
  - CUSTOM → 粉色

## 三、视觉语法

| Clip 类型 | 视觉形态 | 含义 | 颜色 |
|----------|---------|------|------|
| TransformClip | 曲线 / 路径感 | 连续变化 | 技术感（青 / 绿） |
| ActorActionClip | 实心区块 | 状态区间 | ActionType 颜色（稳定） |
| ActionEvent | 菱形标记（◆） | 瞬时 | Clip 颜色 |

## 四、设计原则

### ✅ 已对齐的原则

1. **语义和视觉一一对应**
   - 曲线 = 数值变化
   - 方块 = 状态
   - 点 = 事件

2. **颜色语义稳定**
   - 同一个 ActionType 在任何 Track 都是同色
   - 颜色 = 动作类别，不是数值

3. **Block Clip 不显示关键帧**
   - 这是底线
   - ActorActionClip = 区间，不是曲线

4. **不重写 TimelineRenderer**
   - 只做"视觉分层增强"
   - 通过 `getVisualType()` 方法分支绘制

## 五、导演体验

完成这一步后，导演会自然形成：
- "曲线 = 数值变化"
- "方块 = 状态"
- "点 = 事件"

这正是专业时间线工具的共同语言。

## 六、未来扩展

即使未来添加新的 Clip 类型：
- `CameraActionClip` → 指定 `BLOCK`
- `WeatherActionClip` → 指定 `BLOCK`
- 其他事件类型 → 指定 `EVENT`

只需要在对应的 `EntityClip` 子类中覆盖 `getVisualType()` 方法即可。

## 七、技术细节

### 文件清单

1. **TimelineClipVisualType.java**（新建）
   - 定义视觉类型枚举

2. **EntityClip.java**（修改）
   - 添加 `getVisualType()` 方法（默认返回 `CURVE`）

3. **ActorActionClip.java**（修改）
   - 覆盖 `getVisualType()` 返回 `BLOCK`

4. **EntityActionClip.java**（修改）
   - 覆盖 `getVisualType()` 返回 `EVENT`

5. **EntityTimelineClip.java**（修改）
   - 添加 `getVisualType()` 方法，委托给 `EntityClip`

6. **TimelineClipRenderer.java**（修改）
   - `renderClip()` 方法根据视觉类型分支
   - 添加 `renderCurveClip()`、`renderBlockClip()`、`renderEventClip()` 方法

7. **ActionTypeColors.java**（新建）
   - ActionType 到颜色的映射

## 八、注意事项

1. **圆角矩形**
   - 当前使用普通矩形（`addRectFilled`）
   - 未来可以改进为使用 Path API 绘制圆角

2. **Block Clip 调整手柄**
   - 当前不显示调整手柄
   - 如果需要，可以保留，但建议禁用

3. **Event Clip 位置**
   - 事件在开始时间位置显示
   - 如果事件有持续时间，可以考虑显示为短线段
