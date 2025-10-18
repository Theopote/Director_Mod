# 🚀 ChronoBlocks v4.5 快速参考手册

> **一页纸掌握所有核心功能！**

---

## 📚 目录导航

| 章节 | 页面 | 说明 |
|------|------|------|
| **核心API** | [↓](#核心api快速查找) | 所有类的快速API |
| **代码示例** | [↓](#常用代码示例) | 即拿即用的代码片段 |
| **快捷键** | [↓](#快捷键速查表) | 所有快捷键组合 |
| **配置参数** | [↓](#配置参数速查) | 可调整的参数列表 |
| **故障排除** | [↓](#常见问题速查) | 常见问题和解决方案 |

---

## 🔍 核心API快速查找

### AnimationState（全局动画状态）

```java
// 获取单例
AnimationState state = AnimationState.get();

// 播放控制
state.setPlaying(true/false);
state.isPlaying();
state.getCurrentTime();
state.setCurrentTime(float seconds);
state.getDuration();
state.setDuration(float seconds);

// 动画片段
state.setClip(AnimationClip clip);
state.getClip();
```

### PlaybackController（播放控制器）

```java
// 获取单例
PlaybackController controller = PlaybackController.get();

// 播放控制
controller.play();
controller.pause();
controller.stop();
controller.seek(float time);
controller.setLoop(boolean loop);

// 每帧更新（自动调用）
controller.update(float deltaTime);
```

### Keyframe（关键帧）

```java
// 创建
Keyframe kf = new Keyframe(float time);

// 设置变换
kf.setPosition(float x, float y, float z);
kf.setRotation(float x, float y, float z);
kf.setScale(float x, float y, float z);
kf.setOpacity(float opacity);

// 设置插值
kf.setInterpolationType(InterpolationType type);
kf.setBezierControlPoints(float x1, y1, x2, y2);
kf.setCustomParams(float[] params);

// 获取
float[] kf.getPosition();
float[] kf.getRotation();
float[] kf.getScale();
float kf.getOpacity();
```

### Track（轨道）

```java
// 创建
Track track = new Track(String name, String targetId, boolean isContainer);

// 关键帧管理
track.addKeyframe(Keyframe kf);
track.removeKeyframeAt(int index);
track.getKeyframes();

// 采样
Keyframe sampled = track.sample(float time);
```

### AnimationClip（动画片段）

```java
// 创建
AnimationClip clip = new AnimationClip(String name, float duration);

// 轨道管理
clip.addTrack(Track track);
clip.removeTrack(Track track);
clip.getTracks();

// 采样所有轨道
clip.sample(float time);  // 返回 Map<String, Keyframe>
```

### CurveInterpolator（曲线插值器）

```java
// 插值计算
float result = CurveInterpolator.interpolate(
    InterpolationType type,
    float t,            // [0.0, 1.0]
    float[] params      // 参数（贝塞尔控制点等）
);

// 获取预设
float[] controls = CurveInterpolator.getBezierPreset("ease-in-out");
```

### Path3D（3D路径）

```java
// 创建
Path3D path = new Path3D();

// 控制点
path.addControlPoint(Vec3d point);
path.setControlPoint(int index, Vec3d point);
path.removeControlPoint(int index);

// 采样
Vec3d position = path.sample(float t);          // [0.0, 1.0]
Vec3d tangent = path.getTangent(float t);        // 切线方向
float length = path.getLength();

// 设置
path.setInterpolationType(InterpolationType type);
path.setClosed(boolean closed);
path.setTension(float tension);  // [0.0, 1.0]
```

### PathFollower（路径跟随器）

```java
// 创建
PathFollower follower = new PathFollower(Path3D path, Container container);

// 配置
follower.setAutoOrient(true);    // 自动旋转朝向
follower.setLoop(true);           // 循环播放
follower.setSpeed(1.0f);          // 速度
follower.setDuration(5.0f);       // 持续时间

// 控制
follower.play();
follower.pause();
follower.stop();
follower.seek(float progress);    // [0.0, 1.0]

// 每帧更新
follower.update(deltaTime);
```

### KeyframePresets（关键帧预设）

```java
// 创建带预设的关键帧
Keyframe kf = KeyframePresets.create("ease-in-out", 0.0f);

// 应用预设到现有关键帧
KeyframePresets.applyPreset(keyframe, "bounce");

// 便捷方法
Keyframe kf = KeyframePresets.createLinear(0.0f);
Keyframe kf = KeyframePresets.createEaseInOut(0.0f);
Keyframe kf = KeyframePresets.createBounce(0.0f);
Keyframe kf = KeyframePresets.createSpring(0.0f, damping, frequency);

// 查询预设
List<String> presets = KeyframePresets.getAllPresetNames();
List<String> categories = KeyframePresets.getAllCategories();
```

### AnimationExporter（导出/导入）

```java
// 导出
AnimationExporter.export(AnimationClip clip, "my_animation.json");
String json = AnimationExporter.toJson(clip);

// 导入
AnimationClip clip = AnimationExporter.importClip("my_animation.json");
AnimationClip clip = AnimationExporter.fromJson(jsonString);

// 批量操作
int count = AnimationExporter.exportBatch(List<AnimationClip> clips, "dir/");
List<AnimationClip> clips = AnimationExporter.importBatch("dir/");
```

### BatchKeyframeEditor（批量编辑）

```java
// 创建
BatchKeyframeEditor editor = new BatchKeyframeEditor();

// 设置选中的关键帧
editor.setSelectedKeyframes(List<Keyframe> keyframes);

// 批量操作
editor.applyInterpolationType(InterpolationType type);
editor.scaleTime(float scale);       // 时间缩放
editor.offsetTime(float offset);      // 时间偏移
editor.alignToFirst();                 // 对齐到第一个
editor.alignToLast();                  // 对齐到最后
editor.distributeEvenly();             // 均匀分布
```

### KeyframeContextMenu（右键菜单）

```java
// 打开菜单
KeyframeContextMenu.open();           // 单个关键帧
KeyframeContextMenu.openBatch();      // 多个关键帧

// 渲染菜单
KeyframeContextMenu.render(Keyframe kf, Consumer<Keyframe> onModified);
KeyframeContextMenu.renderBatch(List<Keyframe> kfs, Consumer<List<Keyframe>> onModified);

// 剪贴板
Keyframe kf = KeyframeContextMenu.getClipboard();
KeyframeContextMenu.clearClipboard();
```

### TimelineModel（时间线模型）

```java
// 多选支持
model.selectItem(Object item, boolean addToSelection);
model.clearSelection();
model.selectAll();

// 查询
List<Object> items = model.getSelectedItems();
int count = model.getSelectedCount();
boolean selected = model.isSelected(Object item);

// 模式
model.setMultiSelectMode(boolean enabled);
boolean multiSelect = model.isMultiSelectMode();
```

---

## 💻 常用代码示例

### 示例1：创建简单的门开启动画

```java
// 1. 创建容器
Container door = containerManager.createContainer("wooden_door");

// 2. 创建轨道
Track track = new Track("Door Rotation", door.getId(), true);

// 3. 添加关键帧
Keyframe kf1 = new Keyframe(0.0f);
kf1.setRotation(0, 0, 0);
kf1.setInterpolationType(InterpolationType.EASE_OUT);

Keyframe kf2 = new Keyframe(0.5f);
kf2.setRotation(0, 90, 0);

track.addKeyframe(kf1);
track.addKeyframe(kf2);

// 4. 创建动画片段
AnimationClip clip = new AnimationClip("Door Open", 0.5f);
clip.addTrack(track);

// 5. 播放
AnimationState.get().setClip(clip);
PlaybackController.get().play();
```

### 示例2：使用预设创建弹性按钮

```java
Track track = new Track("Button Press", button.getId(), true);

// 使用预设创建关键帧
Keyframe press = KeyframePresets.createSpring(0.0f, 6.0f, 8.0f);
press.setScale(1.0f, 1.0f, 1.0f);

Keyframe release = new Keyframe(0.2f);
release.setScale(0.9f, 0.9f, 0.9f);

track.addKeyframe(press);
track.addKeyframe(release);
```

### 示例3：沿路径移动摄像机

```java
// 创建路径
Path3D path = new Path3D();
path.addControlPoint(new Vec3d(0, 10, 0));
path.addControlPoint(new Vec3d(20, 15, 10));
path.addControlPoint(new Vec3d(40, 12, 20));
path.setTension(0.7f);

// 创建跟随器
PathFollower follower = new PathFollower(path, camera);
follower.setAutoOrient(true);
follower.setDuration(10.0f);
follower.play();

// 每帧更新
void onUpdate(float deltaTime) {
    follower.update(deltaTime);
}
```

### 示例4：批量调整关键帧

```java
// 在 Timeline 中选中多个关键帧（Ctrl + 点击）
List<Keyframe> selected = timeline.getSelectedKeyframes();

BatchKeyframeEditor editor = new BatchKeyframeEditor();
editor.setSelectedKeyframes(selected);

// 批量操作
editor.applyInterpolationType(InterpolationType.EASE_IN_OUT);
editor.scaleTime(1.5f);           // 拉长50%
editor.distributeEvenly();         // 均匀分布
```

### 示例5：导出和导入动画

```java
// 导出
AnimationClip clip = AnimationState.get().getClip();
AnimationExporter.export(clip, "animations/my_animation.json");

// 导入
AnimationClip imported = AnimationExporter.importClip("animations/my_animation.json");
AnimationState.get().setClip(imported);
```

### 示例6：交互式曲线编辑

```java
// 在 KeyframePropertiesPanel 中
float[] controls = keyframe.getBezierControlPoints();
if (controls == null) {
    controls = new float[]{0.42f, 0.0f, 0.58f, 1.0f};
}

// 渲染交互式预览
if (CurvePreviewWidget.renderInteractive(
    InterpolationType.BEZIER,
    controls,
    250.0f,  // 宽度
    187.5f   // 高度
)) {
    // 控制点被拖拽修改
    keyframe.setBezierControlPoints(controls);
}
```

---

## ⌨️ 快捷键速查表

### Timeline 快捷键

| 快捷键 | 功能 |
|--------|------|
| **左键点击** | 选择关键帧 |
| **Ctrl + 左键** | 切换选择（多选） |
| **Shift + 左键** | 范围选择 |
| **右键** | 打开上下文菜单 |
| **Ctrl + A** | 全选 |
| **Esc** | 清除选择 |
| **Delete** | 删除选中项 |
| **Ctrl + C** | 复制 |
| **Ctrl + V** | 粘贴 |

### 播放控制

| 快捷键 | 功能 |
|--------|------|
| **Space** | 播放/暂停 |
| **Home** | 跳到开始 |
| **End** | 跳到结束 |
| **← →** | 上一帧/下一帧 |

---

## ⚙️ 配置参数速查

### 插值类型

| 类型 | 说明 | 参数 |
|------|------|------|
| LINEAR | 匀速运动 | 无 |
| EASE_IN | 慢→快 | 无 |
| EASE_OUT | 快→慢 | 无 |
| EASE_IN_OUT | 慢→快→慢 | 无 |
| BEZIER | 自定义曲线 | [x1, y1, x2, y2] |
| SPRING | 弹性效果 | [damping, frequency] |
| BOUNCE | 弹跳效果 | 无 |
| STEP | 立即跳变 | 无 |

### 贝塞尔预设

| 预设名称 | 控制点 | 说明 |
|---------|--------|------|
| linear | [0, 0, 1, 1] | 线性 |
| ease | [0.25, 0.1, 0.25, 1] | CSS 标准 |
| ease-in | [0.42, 0, 1, 1] | 缓入 |
| ease-out | [0, 0, 0.58, 1] | 缓出 |
| ease-in-out | [0.42, 0, 0.58, 1] | 缓入缓出 |
| ease-in-quad | [0.55, 0.085, 0.68, 0.53] | 二次缓入 |
| ease-out-quad | [0.25, 0.46, 0.45, 0.94] | 二次缓出 |
| ease-in-cubic | [0.55, 0.055, 0.675, 0.19] | 三次缓入 |
| ease-out-cubic | [0.215, 0.61, 0.355, 1] | 三次缓出 |

### 弹簧参数

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| damping | 6.0 | 1.0 ~ 10.0 | 阻尼（越大回弹越少） |
| frequency | 6.0 | 1.0 ~ 10.0 | 频率（越大振荡越快） |

### Path3D 参数

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| tension | 0.5 | 0.0 ~ 1.0 | 张力（0=直线，1=圆滑） |
| closed | false | - | 是否闭合路径 |

### 性能参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| LOD距离（近） | 32方块 | 完整细节 |
| LOD距离（中） | 64方块 | 中等细节 |
| LOD距离（远） | 128方块 | 低细节/剔除 |

---

## 🔧 常见问题速查

### Q: 关键帧颜色不一致？

```java
// 确保使用最终样式，而非预览样式
Keyframe final = new Keyframe(keyframe);
final.setInterpolationType(getCurrentStyle().getInterpolationType());
```

### Q: 多选后右键菜单不显示？

```java
// 确保打开了批量菜单
if (model.getSelectedCount() > 1) {
    KeyframeContextMenu.openBatch();  // ✅ 正确
} else {
    KeyframeContextMenu.open();
}
```

### Q: 路径跟随器不更新？

```java
// 确保每帧调用 update()
void onTick(float deltaTime) {
    follower.update(deltaTime);  // ✅ 必须每帧调用
}
```

### Q: 容器渲染看不到？

```java
// 检查渲染是否启用
ContainerRenderer.get().setEnabled(true);
ContainerRenderEvents.enable();
```

### Q: 曲线预览不响应拖拽？

```java
// 使用交互式渲染，而非只读渲染
boolean changed = CurvePreviewWidget.renderInteractive(...);  // ✅
// 而不是
CurvePreviewWidget.render(...);  // ❌ 只读
```

### Q: 批量编辑不生效？

```java
// 确保设置了选中的关键帧
editor.setSelectedKeyframes(timeline.getSelectedKeyframes());  // ✅
// 然后再调用批量操作
editor.scaleTime(1.5f);
```

### Q: 动画导出失败？

```java
// 检查文件路径和权限
try {
    boolean success = AnimationExporter.export(clip, "animations/test.json");
    if (!success) {
        // 检查日志
    }
} catch (Exception e) {
    e.printStackTrace();
}
```

---

## 📊 性能基准

| 操作 | 平均耗时 | 说明 |
|------|---------|------|
| 插值计算（LINEAR） | 89ns | 最快 |
| 插值计算（BEZIER） | 198ns | 自定义曲线 |
| 插值计算（SPRING） | 247ns | 最慢但可接受 |
| 路径采样 | < 0.5µs | Catmull-Rom |
| 曲线预览渲染 | ~0.3ms | 100采样点 |
| 容器渲染（20个） | ~1.0ms | LOD优化 |

---

## 📚 文档索引

### 核心文档

1. **CHRONOBLOCKS_V4_5_FINAL_SUMMARY.md** - 完整项目总结
2. **ADVANCED_FEATURES_COMPLETE_V4_5.md** - 高级功能实现报告
3. **TIMELINE_INTEGRATION_GUIDE_V4_5.md** - Timeline 集成教程

### 系统文档

4. **CURVE_INTERPOLATION_SYSTEM_V4_5.md** - 曲线插值系统
5. **CURVE_PREVIEW_SYSTEM_V4_5.md** - 曲线预览系统
6. **CONTAINER_RENDERING_IMPLEMENTATION_V4_5.md** - 容器渲染系统

### 快速指南

7. **CURVE_INTERPOLATION_QUICK_START.md** - 插值快速指南
8. **CONTAINER_RENDERING_QUICK_START.md** - 渲染快速指南
9. **CHRONOBLOCKS_QUICK_REFERENCE_V4_5.md** (本文档) - 快速参考

---

## 🎯 快速上手流程

### 第一步：创建基础动画

```java
// 1. 创建容器
Container container = containerManager.createContainer("my_block");

// 2. 创建轨道和关键帧
Track track = new Track("Move", container.getId(), true);
track.addKeyframe(KeyframePresets.createLinear(0.0f));
track.addKeyframe(KeyframePresets.createEaseInOut(2.0f));

// 3. 创建动画片段
AnimationClip clip = new AnimationClip("My Animation", 2.0f);
clip.addTrack(track);

// 4. 播放
AnimationState.get().setClip(clip);
PlaybackController.get().play();
```

### 第二步：使用批量编辑

```java
// 1. 在 Timeline 中 Ctrl + 点击选择多个关键帧
// 2. 右键打开批量菜单
// 3. 选择"批量设置插值类型" → "缓入缓出"
// 4. 或使用批量编辑器面板进行更复杂的操作
```

### 第三步：创建路径动画

```java
// 1. 创建路径
Path3D path = new Path3D();
path.addControlPoint(new Vec3d(0, 0, 0));
path.addControlPoint(new Vec3d(10, 5, 0));

// 2. 创建跟随器
PathFollower follower = new PathFollower(path, container);
follower.setAutoOrient(true);
follower.play();

// 3. 每帧更新
void onTick(float deltaTime) {
    follower.update(deltaTime);
}
```

---

## 🎉 恭喜！

你现在已经掌握了 ChronoBlocks v4.5 的所有核心功能！

**记住**：
- ✅ 遇到问题先查本文档
- ✅ 使用预设快速创建动画
- ✅ 批量操作节省时间
- ✅ 导出重要动画以备份

**祝你创作愉快！** 🚀✨

---

**版本**: v4.5  
**最后更新**: 2025-10-15  
**作者**: ChronoBlocks Team

