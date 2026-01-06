# EntityClip UI 集成总结

## ✅ 已完成的工作

### 一、属性面板适配器

#### 1. EntitySpawnClipPropertyAdapter ✅
- **位置**：`src/main/java/com/chronoblocks/ui/properties/adapters/EntitySpawnClipPropertyAdapter.java`
- **功能**：编辑出现/绑定片段的属性
- **支持字段**：
  - 基础信息（时间范围）
  - 位置和旋转（位置 X/Y/Z，偏航/俯仰）
  - 可见性（出现时可见）

#### 2. EntityTransformClipPropertyAdapter ✅
- **位置**：`src/main/java/com/chronoblocks/ui/properties/adapters/EntityTransformClipPropertyAdapter.java`
- **功能**：编辑位移/旋转/缩放片段的属性
- **支持字段**：
  - 基础信息（时间范围）
  - 插值设置（插值类型、跟随路径、路径ID）
  - 起始位置（位置、旋转、缩放）
  - 结束位置（位置、旋转、缩放）

### 二、创建菜单

#### TimelineContextMenuFactory 集成 ✅
- **位置**：`src/main/java/com/chronoblocks/ui/timeline/menu/TimelineContextMenuFactory.java`
- **功能**：在实体轨道的右键菜单中添加"创建实体片段"子菜单
- **支持的 Clip 类型**：
  - 出现/绑定 (Spawn)
  - 位移/旋转 (Transform)
  - 动画 (Animation)
  - 行为 (Action)
  - 状态 (State)
  - 看向 (LookAt)
  - 离场 (Despawn)

#### 使用方式
1. 右键点击实体轨道
2. 选择"创建实体片段"
3. 选择要创建的 Clip 类型
4. Clip 会在当前播放头位置创建，默认时长为 1-2 秒

### 三、TimelineTrack 集成

#### EntityTrack Payload 支持 ✅
- **位置**：`src/main/java/com/chronoblocks/ui/timeline/model/TimelineTrack.java`
- **新增字段**：
  - `entityTrackPayload` - EntityTrack 实例
- **新增方法**：
  - `getEntityTrackPayload()` - 获取 EntityTrack
  - `setEntityTrackPayload()` - 设置 EntityTrack
  - `getOrCreateEntityTrackPayload()` - 获取或创建 EntityTrack（如果不存在则创建）
  - `hasEntityTrackPayload()` - 检查是否有 EntityTrack

#### 设计特点
- EntityTrack 作为 TimelineTrack 的 payload，独立存储
- 自动同步绑定信息（显示名称、实体类型等）
- 延迟创建：只有在需要时才创建 EntityTrack

### 四、属性适配器注册

#### PropertyAdapterBootstrap 更新 ✅
- **位置**：`src/main/java/com/chronoblocks/ui/properties/PropertyAdapterBootstrap.java`
- **已注册**：
  - `EntitySpawnClipPropertyAdapter`
  - `EntityTransformClipPropertyAdapter`
- **后续可添加**：
  - `EntityAnimationClipPropertyAdapter`
  - `EntityActionClipPropertyAdapter`
  - `EntityStateClipPropertyAdapter`
  - `EntityLookAtClipPropertyAdapter`
  - `EntityDespawnClipPropertyAdapter`

## 📋 使用示例

### 创建 EntityClip

```java
// 1. 获取实体轨道
TimelineTrack entityTrack = model.getTrackAt(index);
if (entityTrack.getType() == TrackType.ENTITY) {
    // 2. 获取或创建 EntityTrack payload
    EntityTrack actorTrack = entityTrack.getOrCreateEntityTrackPayload();
    
    // 3. 创建 Spawn Clip
    EntitySpawnClip spawnClip = new EntitySpawnClip("出现", 0.0f, 1.0f);
    spawnClip.setPosition(0, 64, 0);
    actorTrack.addClip(spawnClip);
    
    // 4. 创建 Transform Clip
    EntityTransformClip transformClip = new EntityTransformClip("移动", 1.0f, 5.0f);
    transformClip.setFromPos(0, 64, 0);
    transformClip.setToPos(10, 64, 10);
    actorTrack.addClip(transformClip);
}
```

### 编辑 EntityClip 属性

1. 选中 EntityClip（在时间线上）
2. 属性面板会自动显示对应的适配器
3. 编辑属性后自动保存

## 🎯 后续工作

### 优先级1：UI 渲染
1. **时间线渲染**
   - 在时间线上渲染 EntityClip
   - 显示 Clip 类型图标和颜色
   - 支持拖拽和调整时长

2. **选择系统**
   - 支持选中 EntityClip
   - 多选支持
   - 框选支持

### 优先级2：更多属性适配器
1. **EntityAnimationClipPropertyAdapter**
   - 动画ID选择器
   - 动画源类型选择
   - 循环和速度控制

2. **EntityActionClipPropertyAdapter**
   - 行为类型选择
   - 参数表编辑

3. **EntityStateClipPropertyAdapter**
   - 状态开关（潜行、冲刺、发光等）
   - 可见性控制

4. **EntityLookAtClipPropertyAdapter**
   - 目标模式选择
   - 世界坐标/实体UUID/摄像机选择
   - 强度控制

5. **EntityDespawnClipPropertyAdapter**
   - 离场模式选择（隐藏/删除）

### 优先级3：命令系统
1. **AddEntityClipCommand**
   - 支持撤销/重做
   - 支持命令历史

2. **DeleteEntityClipCommand**
   - 删除 EntityClip
   - 支持撤销/重做

3. **DuplicateEntityClipCommand**
   - 复制 EntityClip
   - 支持撤销/重做

### 优先级4：序列化
1. **EntityTrack 序列化**
   - 保存 EntityTrack payload 到项目文件
   - 加载时恢复 EntityTrack

2. **EntityClip 序列化**
   - 保存所有 Clip 类型
   - 恢复 Clip 状态

## ✨ 总结

UI 支持和 TimelineTrack 集成已完成：

1. ✅ **属性面板适配器** - EntitySpawnClip 和 EntityTransformClip 的适配器已实现
2. ✅ **创建菜单** - 在实体轨道右键菜单中可以创建所有类型的 EntityClip
3. ✅ **TimelineTrack 集成** - EntityTrack 作为 payload 存储在 TimelineTrack 中
4. ✅ **属性适配器注册** - 已注册到 PropertyAdapterBootstrap

下一步可以继续实现：
- 时间线渲染 EntityClip
- 更多属性适配器
- 命令系统
- 序列化支持

