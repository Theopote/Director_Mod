# 动画代码清理计划

## 一、当前结构分析

### 1. 新旧包结构对比

#### 旧系统：`com.chronoblocks.animation`
```
animation/
├── core/                    # 通用动画核心（关键帧、轨道系统）
│   ├── AnimationClip.java  # 通用动画片段
│   ├── Keyframe.java       # 关键帧（通用）
│   ├── Track.java          # 轨道（通用）
│   ├── TransformTrack.java
│   ├── RotationTrack.java
│   ├── VisibilityTrack.java
│   └── PlayableGraph.java
├── timeline/                # 时间线相关
│   ├── ClipAnimationConfig.java  # ⚠️ 需要迁移
│   ├── TimelineController.java   # ⚠️ 需要检查
│   ├── AnimationTimelineService.java
│   ├── ContainerVisibilityTrack.java
│   └── VisibilityKeyframe.java
├── integration/playback/    # 播放集成
│   └── ClipPlaybackRuntimeManager.java  # ⚠️ 需要检查
└── ui/                      # UI组件
    └── AnimationInspectorPanel.java      # ⚠️ 需要检查
```

#### 新系统：`com.chronoblocks.core.animation`
```
core/animation/
├── BlockAnimationEngine.java      # 方块动画引擎接口（新）
├── AnimationEngineFactory.java    # 引擎工厂（新）
└── impl/
    ├── InstantAnimationEngine.java
    ├── GrowUpAnimationEngine.java
    └── ExpandAnimationEngine.java
```

### 2. 职责划分

**旧系统职责**：
- 通用关键帧动画系统
- 属性动画（Transform, Rotation, Visibility等）
- 动画片段管理（AnimationClip）

**新系统职责**：
- 方块显示/隐藏动画引擎
- 基于容器和方块位置的动画计算
- 片段时长内的方块出现动画

## 二、需要清理的内容

### ✅ 需要迁移的文件

1. **`ClipAnimationConfig.java`** 
   - 当前位置：`com.chronoblocks.animation.timeline.ClipAnimationConfig`
   - 目标位置：`com.chronoblocks.core.animation.ClipAnimationConfig`
   - 理由：这是片段动画配置，与新的方块动画引擎直接相关
   - 影响范围：需要更新所有导入此类的文件

### ⚠️ 需要检查的文件

2. **`TimelineController.java`**
   - 当前状态：被 `PlaybackController` 使用
   - 需要确认：是否与新系统冲突或重复

3. **`AnimationTimelineService.java`**
   - 当前状态：被 `PlaybackController` 使用
   - 需要确认：是否与新系统冲突或重复

4. **`ClipPlaybackRuntimeManager.java`**
   - 当前状态：被 `PlaybackController` 使用
   - 需要确认：是否与新系统冲突或重复

5. **`AnimationInspectorPanel.java`**
   - 当前状态：UI面板
   - 需要确认：是否还在使用，是否与新系统兼容

### ✅ 应该保留的文件

以下文件属于通用动画系统，**不应迁移**：
- `animation.core.AnimationClip` - 通用动画片段
- `animation.core.Keyframe` - 通用关键帧
- `animation.core.Track` 及其子类 - 通用轨道系统
- `animation.core.PlayableGraph` - 播放图
- `animation.timeline.ContainerVisibilityTrack` - 容器可见性轨道

## 三、迁移计划

### 阶段1：迁移 ClipAnimationConfig

1. 将 `ClipAnimationConfig.java` 从 `animation.timeline` 移动到 `core.animation`
2. 更新所有导入：
   - `TimelineClip.java`
   - `TimelineClipPropertyAdapter.java`
   - `TimelineClipBlockController.java`
   - `TimelineModelPersistence.java`

### 阶段2：检查和评估

1. 检查 `TimelineController` 与新系统的关系
2. 检查 `AnimationTimelineService` 的用途
3. 检查 `ClipPlaybackRuntimeManager` 是否还在使用
4. 检查 `AnimationInspectorPanel` 是否还在使用

### 阶段3：清理和重构

1. 标记废弃的类（如果需要保留向后兼容）
2. 更新文档
3. 统一命名规范

## 四、注意事项

1. **向后兼容性**：如果有序列化的配置或保存的文件，需要保持兼容
2. **导入更新**：需要批量更新所有导入路径
3. **测试**：迁移后需要测试相关功能
4. **文档**：更新相关文档说明新旧系统的区别

