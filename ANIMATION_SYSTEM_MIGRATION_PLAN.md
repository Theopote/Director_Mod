# 动画系统统一迁移计划

## 📊 系统分析

### 旧动画系统 (`com.chronoblocks.animation`)
**位置**: `src/main/java/com/chronoblocks/animation/`

**职责**: 容器级别的动画系统
- **核心类**:
  - `AnimationClip` - 动画片段（容器级别）
  - `Track<T>` - 动画轨道基类
  - `TransformTrack` - 位置/缩放轨道
  - `RotationTrack` - 旋转轨道
  - `VisibilityTrack` - 可见性轨道
  - `Keyframe<T>` - 关键帧
  - `EvaluatedPose` - 求值结果
  - `PlayableGraph` - 动画求值图
- **时间线管理**:
  - `TimelineController` - 时间线控制器
  - `AnimationTimelineService` - 时间线服务
  - `ContainerVisibilityTrack` - 容器可见性轨道
- **播放集成**:
  - `ClipPlaybackRuntimeManager` - 播放运行时管理器

**使用位置** (21个文件):
- `PlaybackController` - 播放控制器
- UI适配器: `TrackAdapter`, `ClipPropertyAdapter`, `KeyframePropertyAdapter`
- UI面板: `BatchKeyframeEditor`, `KeyframePropertiesPanel`
- 属性系统: `PropertyAdapterBootstrap`
- UI组件: `CurvePreviewWidget`

### 新动画系统 (`com.chronoblocks.core.animation`)
**位置**: `src/main/java/com/chronoblocks/core/animation/`

**职责**: 方块级别的动画系统
- **核心类**:
  - `BlockAnimationEngine` - 方块动画引擎
  - `BlockAnimationRenderer` - 方块动画渲染器
  - `BlockAnimationContext` - 动画上下文
  - `BlockAnimationSnapshot` - 动画快照
- **序列系统**:
  - `BlockSequenceEntry` - 方块序列条目
  - `BlockSequenceGenerator` - 序列生成器
  - `SequenceEvaluator` - 序列求值器
- **变换系统**:
  - `IAnimationTransformer` - 动画变换器接口
  - 各种变换器实现（Expand, FadeIn, FallDown等）
- **特效系统**:
  - `BlockEffectRegistry` - 特效注册表
  - `IBlockEffect` - 特效接口
  - 各种特效实现

## 🔍 系统关系

两个系统是**互补关系**，不是重复：
- **旧系统**: 处理**容器级别**的动画（整个容器的位置、旋转、可见性）
- **新系统**: 处理**方块级别**的动画（单个方块的动画效果、变换、特效）

## 📋 迁移计划

### 目标
将旧动画系统迁移到 `com.chronoblocks.core.animation` 下，统一管理所有动画相关代码。

### 迁移结构

```
com.chronoblocks.core.animation/
├── block/              # 方块级别动画（现有）
│   ├── BlockAnimationEngine.java
│   ├── BlockAnimationRenderer.java
│   └── ...
├── container/          # 容器级别动画（迁移）
│   ├── AnimationClip.java
│   ├── Track.java
│   ├── TransformTrack.java
│   ├── RotationTrack.java
│   ├── VisibilityTrack.java
│   ├── Keyframe.java
│   ├── EvaluatedPose.java
│   └── PlayableGraph.java
├── timeline/           # 时间线管理（迁移）
│   ├── TimelineController.java
│   ├── AnimationTimelineService.java
│   ├── ContainerVisibilityTrack.java
│   └── VisibilityKeyframe.java
├── playback/           # 播放集成（迁移）
│   └── ClipPlaybackRuntimeManager.java
├── sequence/           # 序列系统（现有）
├── transform/          # 变换系统（现有）
└── effect/             # 特效系统（现有）
```

### 迁移步骤

1. **创建新的包结构**
   - 在 `core.animation` 下创建 `container`, `timeline`, `playback` 子包

2. **迁移文件**
   - `animation.core.*` → `core.animation.container.*`
   - `animation.timeline.*` → `core.animation.timeline.*`
   - `animation.integration.playback.*` → `core.animation.playback.*`

3. **更新包声明**
   - 更新所有迁移文件的 `package` 声明

4. **更新导入引用**
   - 更新所有使用旧动画系统的文件（21个文件）
   - 使用全局查找替换功能

5. **验证**
   - 编译检查
   - 功能测试

## ✅ 可保留的文件

所有旧动画系统的文件都应该**保留并迁移**，因为它们都在被使用：
- ✅ `AnimationClip` - 被 PlaybackController, UI适配器使用
- ✅ `Track` 及其子类 - 被 UI适配器, 属性面板使用
- ✅ `Keyframe` - 被 UI面板, 属性适配器使用
- ✅ `EvaluatedPose` - 被 PlayableGraph, ClipPlaybackRuntimeManager 使用
- ✅ `PlayableGraph` - 被 TimelineController 使用
- ✅ `TimelineController` - 被 AnimationTimelineService, PlaybackController 使用
- ✅ `AnimationTimelineService` - 被 PlaybackController, ClipPlaybackRuntimeManager 使用
- ✅ `ClipPlaybackRuntimeManager` - 被 PlaybackController 使用
- ✅ `ContainerVisibilityTrack` - 被 PlayableGraph 使用
- ✅ `VisibilityKeyframe` - 被 ContainerVisibilityTrack 使用

## ❌ 可删除的文件

**无** - 所有文件都在被使用，不应删除。

## 🎯 执行建议

1. 先创建新的包结构
2. 逐个迁移文件并更新包声明
3. 使用 IDE 的全局查找替换功能更新导入
4. 编译验证
5. 删除旧的 `com.chronoblocks.animation` 文件夹

