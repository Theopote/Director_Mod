# EntityClipSampler 改进总结

## ✅ 吸纳的建议和改进

### 一、ActionTriggerCache（新增）

**位置**：`src/main/java/com/chronoblocks/ui/timeline/playback/entity/ActionTriggerCache.java`

#### 设计特点
- 防止重复触发 Action Clip
- 只在"刚进入 Clip 的那一帧"触发
- 支持 rewind / loop（当时间离开 Clip 时清除记录）

#### 使用方式
```java
ActionTriggerCache cache = new ActionTriggerCache();
if (cache.shouldTrigger(actionClip, time)) {
    // 触发 Action
}
```

### 二、EntityActionEvent（新增）

**位置**：`src/main/java/com/chronoblocks/ui/timeline/playback/entity/EntityActionEvent.java`

#### 设计特点
- 记录本帧触发的 Action 事件
- 包含行为类型和参数
- 由 TimelineEntityController 处理这些事件

### 三、EntitySample 扩展

#### 新增字段
- `lookAtEnabled` - LookAt 是否启用（布尔值）
- `actionEvents` - Action 事件列表（本帧触发的事件）

#### 改进
- `lookAtStrength` 默认值改为 0.0f（更符合语义）

### 四、叠加优先级规则（明确化）

#### Transform：覆盖（最后一个生效）
- **规则**：按时间排序，取最后一个
- **实现**：`applyTransform()` 方法按开始时间排序，选择最新的

#### LookAt：叠加（强度最大）
- **规则**：取强度最大的 LookAt Clip
- **实现**：`applyLookAt()` 方法比较强度，选择最大的

#### State：last-wins（最后一个生效）
- **规则**：按时间排序，最后一个覆盖前面的（而不是 OR 逻辑）
- **实现**：`applyState()` 方法按顺序应用，最后一个覆盖

#### Animation：last-wins（最后一个生效）
- **规则**：按时间排序，取最后一个
- **实现**：`applyAnimation()` 方法按开始时间排序，选择最新的

#### Action：一次性触发
- **规则**：只在刚进入 Clip 时触发，使用 ActionTriggerCache 防止重复
- **实现**：`applyActions()` 方法使用 ActionTriggerCache 管理触发状态

### 五、代码结构改进

#### 方法重构
- `applyTransform()` - 处理多个 Transform Clip，选择最新的
- `applyAnimation()` - 处理多个 Animation Clip，选择最新的
- `applyState()` - 处理多个 State Clip，最后一个覆盖
- `applyLookAt()` - 处理多个 LookAt Clip，选择强度最大的
- `applyActions()` - 处理 Action Clip，使用 ActionTriggerCache

#### 工具方法
- `clearActionCache()` - 清除 Action 触发缓存（用于重置/跳转时间）

## 📊 叠加规则总结表

| 类型        | 规则说明                        | 实现方法           |
| --------- | --------------------------- | -------------- |
| Transform | **覆盖**（最后一个生效，按时间排序）      | `applyTransform()` |
| LookAt    | **叠加**（强度最大）                | `applyLookAt()`    |
| State     | **last-wins**（最后一个覆盖）       | `applyState()`     |
| Animation | **last-wins**（最后一个生效）       | `applyAnimation()` |
| Action    | **事件触发**（一次性，使用缓存防止重复）    | `applyActions()`   |

## 🎯 导演语义

### 采样流程
```
time t
 ├─ Transform Clip → 决定位移（覆盖，最后一个生效）
 ├─ LookAt Clip    → 叠加头部朝向（强度最大）
 ├─ State Clip     → 叠加布尔状态（last-wins）
 ├─ Animation Clip → 选择最终动画（last-wins）
 └─ Action Clip    → 触发事件（一次性）
```

### 最终输出
- `EntitySample` - 包含所有叠加后的最终状态
- `actionEvents` - 本帧触发的事件列表

## ✨ 改进优势

1. **明确的叠加规则** - 每种 Clip 类型都有清晰的优先级规则
2. **Action 触发管理** - ActionTriggerCache 防止重复触发，支持 rewind/loop
3. **代码结构清晰** - 每种 Clip 类型都有独立的处理方法
4. **易于扩展** - 未来可以添加权重混合、Mask、Layer 等功能

## 📋 后续扩展建议

### v2 功能（未来）
1. **Animation 权重混合**
   - 按 weight 混合多个 Animation Clip
   - 支持 upper-body / lower-body mask

2. **State 高级叠加**
   - 布尔：OR / AND / OVERRIDE
   - 数值：max / min / lerp

3. **Transform 路径系统**
   - 支持 Camera Path 复用
   - 支持贝塞尔曲线

4. **LookAt 平滑过渡**
   - 支持多个 LookAt 之间的平滑过渡
   - 支持强度插值

## 🔧 使用示例

```java
// 创建采样器
EntityClipSampler sampler = new EntityClipSampler(entityTrack);

// 在时间 3.0 秒采样
EntitySample sample = sampler.sampleAt(3.0f);

// 检查 Action 事件
for (EntityActionEvent event : sample.actionEvents) {
    // 处理事件
    System.out.println("触发行为: " + event.type());
}

// 重置 Action 缓存（跳转时间时）
sampler.clearActionCache();
```

## ✨ 总结

已吸纳建议中的核心改进：

1. ✅ **ActionTriggerCache** - 防止重复触发 Action
2. ✅ **EntityActionEvent** - 记录本帧触发的事件
3. ✅ **明确的叠加规则** - 每种 Clip 类型都有清晰的优先级
4. ✅ **代码结构优化** - 方法更清晰，易于扩展
5. ✅ **EntitySample 扩展** - 添加 lookAtEnabled 和 actionEvents

EntityClipSampler 现在是一个完整的"可播放系统"，ChronoBlocks 的"演员系统"已真正成立！

