# 📋 ChronoBlocks 属性系统使用指南 v4.5

## 🎯 快速开始

### 1. 系统自动初始化

属性系统会在 ChronoBlocks 启动时自动初始化，无需手动设置：

```java
// UIManager 启动时自动调用
PropertyAdapterBootstrap.initialize();

// 输出：
[PropertyAdapterBootstrap] ✅ 已注册: KeyframePropertyAdapter → Keyframe
[PropertyAdapterBootstrap] ✅ 已注册: ContainerPropertyAdapter → Container
[PropertyAdapterBootstrap] ✅ 已注册: ClipPropertyAdapter → AnimationClip
[PropertyAdapterBootstrap] 属性系统初始化完成，共注册 3 个适配器
```

---

## 🎬 使用场景

### 场景 1: 编辑关键帧属性

```java
// 1. 在时间线中点击关键帧
Keyframe selectedKeyframe = ...;

// 2. 设置为当前选择
SelectionManager.get().setCurrentSelection(selectedKeyframe);

// 3. 属性面板自动显示关键帧属性编辑器
// 用户可以拖动滑块修改：
//   - 时间点
//   - 位置 XYZ
//   - 旋转 XYZ
//   - 缩放 XYZ
//   - 不透明度
//   - 插值类型
```

**效果**:
```
┌─────────────────────────────┐
│ 📋 属性                      │
├─────────────────────────────┤
│ Keyframe Properties         │
├─────────────────────────────┤
│ 🔑 关键帧属性                │
│                              │
│ 时间                         │
│  时间点 (s): [━━━●━━] 2.50  │
│                              │
│ 位置                         │
│  XYZ: [5.0] [2.0] [0.0]     │
│                              │
│ 旋转                         │
│  XYZ (度): [0] [90] [0]     │
│                              │
│ 缩放                         │
│  XYZ: [1.0] [1.0] [1.0]     │
│                              │
│ 不透明度                     │
│  不透明度: [━━━●━━] 0.8     │
│                              │
│ 插值                         │
│  插值类型: [缓入缓出 ▼]      │
└─────────────────────────────┘
```

---

### 场景 2: 查看容器信息

```java
// 1. 在场景中选择容器
Container selectedContainer = ...;

// 2. 设置为当前选择
SelectionManager.get().setCurrentSelection(selectedContainer);

// 3. 属性面板显示容器信息和操作按钮
```

**效果**:
```
┌─────────────────────────────┐
│ 📋 属性                      │
├─────────────────────────────┤
│ Container Properties        │
├─────────────────────────────┤
│ 📦 容器属性                  │
│                              │
│ 基本信息                     │
│  名称: MyBuilding            │
│  ID: a1b2c3d4-...           │
│                              │
│ 位置                         │
│  最小: (0, 64, 0)           │
│  最大: (10, 69, 10)         │
│  尺寸: 11 × 6 × 11          │
│                              │
│ 统计                         │
│  方块数量: 456              │
│  体积: 726 方块              │
│  填充率: 62.8%              │
│                              │
│ 操作                         │
│  [刷新容器] [同步到世界]     │
└─────────────────────────────┘
```

---

### 场景 3: 查看动画片段信息

```java
// 1. 选择动画片段
AnimationClip selectedClip = ...;

// 2. 设置为当前选择
SelectionManager.get().setCurrentSelection(selectedClip);

// 3. 属性面板显示片段信息
```

**效果**:
```
┌─────────────────────────────┐
│ 📋 属性                      │
├─────────────────────────────┤
│ AnimationClip Properties    │
├─────────────────────────────┤
│ 🎬 动画片段属性              │
│                              │
│ 基本信息                     │
│  名称: BuildingAnimation    │
│                              │
│ 时间                         │
│  持续时间: 15.50 秒         │
│                              │
│ 轨道                         │
│  轨道数量: 4                │
│  关键帧总数: 32             │
│                              │
│ 操作                         │
│  [播放片段] [导出片段]       │
└─────────────────────────────┘
```

---

### 场景 4: 未选择对象

```
┌─────────────────────────────┐
│ 📋 属性                      │
├─────────────────────────────┤
│                              │
│ 未选择对象                   │
│                              │
│ 在时间线或场景中选择一个     │
│ 对象以查看和编辑其属性       │
│                              │
│ 支持的对象类型：             │
│  • 关键帧 (Keyframe)         │
│  • 容器 (Container)          │
│  • 动画片段 (Clip)           │
└─────────────────────────────┘
```

---

## 🔧 高级用法

### 1. 自定义适配器

```java
// 创建自定义适配器
public class MyCustomAdapter implements IPropertyAdapter<MyCustomObject> {
    
    @Override
    public boolean renderProperties(MyCustomObject obj) {
        boolean modified = false;
        
        UITheme.title("✨ 自定义属性");
        
        UITheme.beginGroup("基本设置");
        // 渲染UI...
        UITheme.endGroup();
        
        return modified;
    }
    
    @Override
    public Class<MyCustomObject> getTargetType() {
        return MyCustomObject.class;
    }
}

// 在 Bootstrap 中注册
PropertyAdapterBootstrap.initialize(); // 先初始化核心适配器
PropertyAdapterRegistry.registerAdapter(MyCustomObject.class, new MyCustomAdapter());
```

---

### 2. 监听选择变化

```java
// 在你的组件中监听选择
SelectionManager.get().addSelectionChangedListener(selection -> {
    if (selection instanceof Keyframe) {
        Keyframe kf = (Keyframe) selection;
        System.out.println("选中了关键帧: " + kf.getTime());
    }
});
```

---

### 3. 多选支持

```java
// 设置多选
List<Keyframe> keyframes = ...;
SelectionManager.get().setMultiSelection(new ArrayList<>(keyframes));

// 监听多选变化
SelectionManager.get().addMultiSelectionChangedListener(selections -> {
    System.out.println("选中了 " + selections.size() + " 个对象");
});
```

---

### 4. 类型安全的选择获取

```java
// 获取当前选择（带类型检查）
Keyframe kf = SelectionManager.get().getCurrentSelectionAs(Keyframe.class);
if (kf != null) {
    // 是关键帧
} else {
    // 不是关键帧或未选择
}

// 检查选择类型
if (SelectionManager.get().isSelectionOfType(Container.class)) {
    // 当前选择是容器
}
```

---

## 📚 API 参考

### SelectionManager

```java
// 获取实例
SelectionManager selMgr = SelectionManager.get();

// 设置选择
selMgr.setCurrentSelection(object);

// 获取选择
Object selection = selMgr.getCurrentSelection();
Keyframe kf = selMgr.getCurrentSelectionAs(Keyframe.class);

// 检查状态
boolean hasSelection = selMgr.hasSelection();
boolean isKeyframe = selMgr.isSelectionOfType(Keyframe.class);

// 清除选择
selMgr.clearSelection();

// 监听器
selMgr.addSelectionChangedListener(listener);
selMgr.removeSelectionChangedListener(listener);
```

---

### PropertyAdapterRegistry

```java
// 注册适配器
PropertyAdapterRegistry.registerAdapter(MyType.class, new MyAdapter());

// 获取适配器
IPropertyAdapter<MyType> adapter = PropertyAdapterRegistry.getAdapterFor(object);
IPropertyAdapter<MyType> adapter = PropertyAdapterRegistry.getAdapterForType(MyType.class);

// 检查是否注册
boolean hasAdapter = PropertyAdapterRegistry.hasAdapter(MyType.class);

// 移除适配器
PropertyAdapterRegistry.removeAdapter(MyType.class);

// 获取统计信息
int count = PropertyAdapterRegistry.getAdapterCount();
String debug = PropertyAdapterRegistry.getDebugInfo();
```

---

### PropertyAdapterBootstrap

```java
// 初始化（幂等）
PropertyAdapterBootstrap.initialize();

// 检查状态
boolean initialized = PropertyAdapterBootstrap.isInitialized();

// 重置（仅测试）
PropertyAdapterBootstrap.resetForTesting();
```

---

## 🎯 最佳实践

### 1. 在 Timeline 中集成

```java
// TimelinePanel.java

private void handleKeyframeClick(Keyframe kf) {
    // 设置选择
    SelectionManager.get().setCurrentSelection(kf);
    
    // 属性面板自动更新
    LOGGER.debug("Selected keyframe at time: {}", kf.getTime());
}
```

---

### 2. 在 ContainerPanel 中集成

```java
// ContainerPanel.java

private void handleContainerClick(Container container) {
    // 设置选择
    SelectionManager.get().setCurrentSelection(container);
    
    // 属性面板自动更新
    LOGGER.debug("Selected container: {}", container.getName());
}
```

---

### 3. 属性修改回调

```java
// 在 PropertyAdapter 中
@Override
public boolean renderProperties(Keyframe kf) {
    boolean modified = false;
    
    float[] time = {kf.getTime()};
    if (ImGui.dragFloat("Time", time)) {
        kf.setTime(time[0]);
        modified = true;
        
        // 触发事件或命令
        // TODO: 可以在这里集成撤销/重做系统
        // commandHistory.execute(new ModifyKeyframeCommand(kf, oldTime, newTime));
    }
    
    return modified;
}
```

---

## 🐛 故障排除

### 问题: 属性面板不更新

**原因**: SelectionManager 的选择未设置  
**解决**: 确保在选择对象时调用 `SelectionManager.get().setCurrentSelection(object)`

---

### 问题: 显示"无可用的属性编辑器"

**原因**: 对象类型没有注册适配器  
**解决**: 在 `PropertyAdapterBootstrap` 中注册对应的适配器

---

### 问题: 属性修改后没有反映到对象

**原因**: 适配器的 `renderProperties()` 方法没有调用对象的 setter  
**解决**: 确保在 UI 控件值变化时调用对象的 setter 方法

---

## 🎉 总结

ChronoBlocks 智能属性系统提供了：

- ✨ **自动化** - 选择什么就编辑什么
- ✨ **智能化** - 自动匹配适配器
- ✨ **专业化** - 企业级代码质量
- ✨ **可扩展** - 轻松添加新类型

**准备好使用智能属性面板了吗？** 🚀✨

---

**版本**: v4.5.0  
**作者**: ChronoBlocks Team  
**日期**: 2025-10-15

