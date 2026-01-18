# Action 区间「涂抹式」编辑实现状态

## 一、已实现的核心组件 ✅

### 1. **ActionBrushState** ✅
- 位置：`com.chronoblocks.ui.timeline.interaction.ActionBrushState`
- 功能：全局轻量状态（当前绘制动作）
- API：
  ```java
  ActionBrushState.set(ActorActionType.RUN);  // 设置当前绘制动作
  ActorActionType current = ActionBrushState.get();  // 获取当前绘制动作
  ```

### 2. **ActionClipIntervalResolver** ✅
- 位置：`com.chronoblocks.ui.timeline.interaction.ActionClipIntervalResolver`
- 功能：区间切割算法（涂抹式编辑的核心）
- 核心方法：
  ```java
  List<ActorActionClip> resolve(
      List<ActorActionClip> existingClips,
      float startTime,
      float endTime,
      ActorActionType actionType
  )
  ```
- 算法逻辑：
  - 完全不相交 → 保留原有区间
  - 有重叠 → 切割：
    - `old.start < a` → 保留 `[old.start, a)`
    - `old.end > b` → 保留 `(b, old.end]`
  - 添加新 Action 区间 `[a, b]`
  - 按时间排序结果

### 3. **TimelineInteractionManager** ✅
- 修改：在 `InteractionMode` 枚举中添加了 `PAINT_ACTION` 模式
- 用途：标识当前处于"涂抹 Action"交互状态

### 4. **EntityTrack** ✅
- 新增方法：
  - `getActionClips()` - 获取所有 ActorActionClip
  - `replaceActionClips(List<ActorActionClip> newClips)` - 替换 ActionClips

## 二、待实现的功能 🚧

### 1. **TimelineInteractionHandler 涂抹逻辑** 🚧
- 位置：`com.chronoblocks.ui.timeline.interaction.TimelineInteractionHandler`
- 需要实现：
  - 检测 `PAINT_ACTION` 模式
  - 处理鼠标按下 → 拖拽 → 松开
  - 在鼠标松开时调用 `ActionClipIntervalResolver.resolve()`
  - 应用结果到 `EntityTrack`

### 2. **UI Action Brush 选择器** 🚧
- 位置：`ActorActionClipPropertyAdapter` 或 `ActorProjectPropertyAdapter`
- 需要实现：
  - 下拉菜单选择当前绘制动作
  - 显示当前选择的动作类型
  - 快捷键支持（可选）

### 3. **拖拽预览渲染** 🚧
- 位置：`TimelineClipRenderer` 或 `TimelineRenderer`
- 需要实现：
  - 拖拽过程中显示半透明色块
  - 显示 Action 名称（如 `[ RUN (preview) ]`）
  - 使用 `ActionTypeColors` 获取颜色

### 4. **交互护栏** 🚧
- 位置：`TimelineInteractionHandler` 或 `TimelinePanel`
- 需要实现：
  - 没有选 ActorTrack → 禁止涂抹
  - 当前 Brush Action 不被 RigProfile 支持 → 禁止涂抹
  - 显示 Tooltip 提示原因

## 三、实现设计

### 交互流程

```
1. 导演选择动作（Brush）
   ↓
2. 在 Timeline 中拖拽一段
   ↓
3. 系统自动处理重叠（区间切割）
   ↓
4. 创建/更新 ActionClips
```

### 数据流

```
鼠标拖拽
  ↓
记录 startTime / endTime
  ↓
ActionBrushState.get() → actionType
  ↓
EntityTrack.getActionClips() → existingClips
  ↓
ActionClipIntervalResolver.resolve(existingClips, start, end, type)
  ↓
List<ActorActionClip> newClips
  ↓
EntityTrack.replaceActionClips(newClips)
```

## 四、核心算法示例

### 区间切割示例

**输入**：
```
已有：  [  WALK  ][   RUN   ][ IDLE ]
涂抹：       [ DANCE ]
```

**输出**：
```
结果： [ WALK ][ DANCE ][ RUN ][ IDLE ]
```

**算法步骤**：
1. WALK: `old.end <= startTime` → 保留
2. RUN: `old.start < startTime && old.end > endTime` → 切割
   - 左：`[RUN.start, DANCE.start)` → 被 DANCE 覆盖，不保留
   - 右：`(DANCE.end, RUN.end]` → 保留
3. IDLE: `old.start >= endTime` → 保留
4. 添加新 Action: `[DANCE.start, DANCE.end]`

## 五、下一步工作

1. **优先级 1**：实现 TimelineInteractionHandler 涂抹逻辑
   - 这是核心交互逻辑
   - 完成后导演即可"拖一段"创建 Action

2. **优先级 2**：实现 UI Action Brush 选择器
   - 提供动作选择入口
   - 可以在 ActorActionClipPropertyAdapter 或 ActorProjectPropertyAdapter 中添加

3. **优先级 3**：实现拖拽预览渲染
   - 提升导演体验
   - 可以在 TimelineClipRenderer 中添加预览绘制

4. **优先级 4**：实现交互护栏
   - 防止误操作
   - 提升专业感

## 六、注意事项

1. **涂抹只影响 ActionClip，不碰 Transform**
   - 原则：Action 区间编辑只改变"这段时间的动作语义"
   - TransformClip 不受影响

2. **Timeline 永远是最终真相**
   - 所有涂抹结果 → 变成明确的 ActionClip 数据
   - 不存在"隐式状态"

3. **区间切割算法必须稳定**
   - 同一个输入必须产生相同的输出
   - 边界情况必须正确处理
