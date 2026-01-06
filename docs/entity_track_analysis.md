# 实体轨道设计分析与改进方案

## 一、当前实现 vs 建议设计

### 当前实现（基于关键帧）
- ✅ `TrackType.ENTITY` - 已实现
- ✅ `EntityKeyframe` - 已实现（包含位置、旋转、动作等所有属性）
- ✅ `EntityTrackSampler` - 已实现（关键帧插值）
- ✅ `TimelineEntityController` - 已实现（控制器框架）
- ✅ `EntityKeyframePropertyAdapter` - 已实现（属性面板）

**特点**：类似天气轨道，使用关键帧系统，所有属性集中在一个关键帧中。

### 建议设计（基于 Clip）
- ❌ `EntityTimelineClip` - 未实现
- ❌ `EntityClipType` 枚举 - 未实现
- ❌ 不同类型的 Clip（Spawn, Transform, Animation等）- 未实现
- ❌ 轨道级实体绑定信息（actorId, entityType等）- 未实现

**特点**：类似容器轨道，使用 Clip 系统，不同类型的 Clip 处理不同功能。

## 二、核心差异分析

### 1. 设计理念差异

**当前实现**：
- 一个关键帧 = 一个时间点的完整实体状态
- 所有属性（位置、旋转、动作）都在同一个关键帧中
- 适合简单的实体动画

**建议设计**：
- 一个 Clip = 一段表演（Spawn、Transform、Animation等）
- 不同类型的 Clip 处理不同的功能
- 更适合复杂的"导演"场景

### 2. 架构差异

**当前实现**：
```
EntityTrack
  └─ EntityKeyframe[] (关键帧数组)
      └─ 包含所有属性（位置、旋转、动作等）
```

**建议设计**：
```
EntityTrack (包含 actorId, entityType)
  └─ EntityClip[] (片段数组)
      ├─ EntitySpawnClip (出现/绑定)
      ├─ EntityTransformClip (位移/旋转)
      ├─ EntityAnimationClip (动画)
      ├─ EntityActionClip (行为)
      └─ EntityStateClip (状态)
```

## 三、改进方案（渐进式）

### 阶段1：保持兼容 + 添加 Clip 支持（推荐）

**策略**：同时支持两种方式
- 保留现有的 `EntityKeyframe` 系统（向后兼容）
- 新增 `EntityClip` 系统（新功能）

**实现步骤**：

1. **创建 EntityClip 基类和类型枚举**
   ```java
   public enum EntityClipType {
       SPAWN,      // 出现/绑定
       TRANSFORM,  // 位移/旋转/缩放
       ANIMATION,  // 动画播放
       ACTION,     // 行为（攻击、对话等）
       STATE,      // 状态（潜行、举盾等）
       LOOK_AT,    // 看向目标
       DESPAWN     // 离场
   }
   
   public abstract class EntityTimelineClip extends TimelineClip {
       protected EntityClipType clipType;
       protected UUID boundActorId; // 绑定的演员ID
   }
   ```

2. **实现核心 Clip 类型**
   - `EntitySpawnClip` - 实体出现/绑定
   - `EntityTransformClip` - 位移/旋转（复用 Camera Path 系统）
   - `EntityAnimationClip` - 动画播放

3. **在 TimelineTrack 中添加实体绑定信息**
   ```java
   // 对于 ENTITY 轨道
   private UUID boundActorId;      // 演员ID
   private EntityType entityType;   // 实体类型
   private String actorDisplayName; // 显示名称
   private boolean persistent;      // 是否跨剪辑存在
   ```

4. **更新轨道特性**
   - `TrackType.ENTITY` 应该支持 `CLIPS`（当前已支持）
   - 添加 `ENTITY_CLIPS` 特性以区分 Clip 和 Keyframe 模式

### 阶段2：UI 和交互支持

1. **轨道头部显示**
   - 显示实体图标、名称
   - 显示绑定状态

2. **Clip 创建菜单**
   - 右键菜单：创建不同类型的 Clip
   - 拖拽创建默认 Clip

3. **属性面板**
   - 根据 Clip 类型显示不同的属性编辑器
   - Transform Clip → 曲线编辑器（复用 Camera Path）
   - Animation Clip → 动画列表选择器

### 阶段3：播放系统集成

1. **采样器更新**
   - `EntityTrackSampler` 需要同时支持 Keyframe 和 Clip
   - 优先使用 Clip，如果没有 Clip 则回退到 Keyframe

2. **控制器更新**
   - `TimelineEntityController` 需要处理 Clip 播放
   - 不同类型的 Clip 有不同的播放逻辑

## 四、建议的下一步实现

### 优先级1：核心架构
1. ✅ 创建 `EntityClipType` 枚举
2. ✅ 创建 `EntityTimelineClip` 基类
3. ✅ 实现 `EntitySpawnClip`（最基础）
4. ✅ 实现 `EntityTransformClip`（最重要，复用 Camera Path）

### 优先级2：轨道绑定
1. ✅ 在 `TimelineTrack` 中添加实体绑定字段
2. ✅ 更新 `TimelineTrackPropertyAdapter` 显示实体信息

### 优先级3：UI 支持
1. ✅ Clip 创建菜单
2. ✅ Clip 属性面板
3. ✅ 轨道头部显示实体信息

## 五、兼容性考虑

### 向后兼容策略
- 保留 `EntityKeyframe` 系统
- 如果轨道上有 Clip，优先使用 Clip
- 如果轨道上只有 Keyframe，使用 Keyframe 系统
- 提供迁移工具（可选）

### 数据迁移
- 现有的 `EntityKeyframe` 可以转换为 `EntityTransformClip`
- 自动迁移脚本（可选）

## 六、与现有系统的融合点

1. **Camera Path 系统**
   - `EntityTransformClip` 直接复用 `TimelineCameraPathManager`
   - 使用相同的插值算法（Linear, Bezier, CatmullRom）

2. **Selection 系统**
   - 选中实体 → 高亮对应 Entity Track
   - 选中 Clip → 显示 Clip 属性

3. **Gizmo 系统**
   - 移动 Gizmo = 修改 Transform Clip 关键帧
   - 旋转 Gizmo = 修改旋转关键帧

4. **Audio Track**
   - Action Clip 可自动生成音频事件
   - 与容器轨道的音频同步机制类似

5. **Camera Track**
   - LOOK_AT Clip 可直接指向摄像机
   - 实现"看向镜头"效果

