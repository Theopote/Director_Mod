# 🎨 PropertiesPanel UI 集成完整文档 v4.9

**ChronoBlocks 智能动态属性面板** - 2025-10-16

---

## 📋 概述

`PropertiesPanel` 是 ChronoBlocks 的**智能动态属性面板**，根据用户选择的对象类型自动切换到对应的属性编辑器。支持 **17种对象类型**（16种专用适配器 + 1种通用回退适配器）。

**文件位置：** `src/main/java/com/chronoblocks/ui/panels/PropertiesPanel.java`

---

## ✨ 核心功能

| 功能 | 描述 |
|------|------|
| 🎯 **上下文驱动** | 根据选中对象类型动态渲染属性编辑器 |
| 🔄 **事件驱动更新** | 监听 `SelectionManager` 的选择变化事件 |
| 🧩 **智能类型适配** | 4级查找机制（精确→继承→supports→fallback） |
| ↔️ **双向绑定** | 属性修改即时反映到对象 |
| 📊 **实时统计** | 显示适配器信息和性能指标 |
| 🛠️ **调试模式** | 详细的日志和诊断信息 |

---

## 📊 支持的对象类型（17种）

### 🎬 时间线系统（3个）
- `TimelineProject` → `TimelineProjectPropertyAdapter`
- `TimelineSequence` → `TimelineSequencePropertyAdapter`
- `TimelineTrack` → `TimelineTrackPropertyAdapter`

### 🎯 核心对象（3个）
- `Keyframe` → `KeyframePropertyAdapter`
- `Container` → `ContainerPropertyAdapter`
- `AnimationClip` → `ClipPropertyAdapter`

### 🌟 扩展系统（6个）
- `Effect` → `EffectPropertyAdapter`
- `ProjectEnvironment` → `ProjectEnvironmentPropertyAdapter`
- `MaterialLibrary` → `MaterialLibraryPropertyAdapter`
- `Camera` → `CameraPropertyAdapter`
- `PostProcessing` → `PostProcessingPropertyAdapter`
- `PhysicsEnvironment` → `PhysicsEnvironmentPropertyAdapter`

### 🔧 扩展类型（3个）
- `Vector3f` → `Vector3fPropertyAdapter`
- `Vector4f (Color)` → `ColorPropertyAdapter`
- `List` → `ListPropertyAdapter`

### 🛡️ 通用回退（1个）
- `Object (任意)` → `GenericPropertyAdapter`

---

## 🎨 UI 状态

PropertiesPanel 有 **4种主要 UI 状态**：

### 1️⃣ 无选择状态

**触发条件：** `currentSelection == null`

**显示内容：**
- 📋 属性面板标题
- 未选择对象提示
- 支持的对象类型列表（折叠式分组）
  - 🎬 时间线系统（3个）
  - 🎯 核心对象（3个）
  - 🌟 扩展系统（6个）
  - 🔧 扩展类型（3个）
  - 🛡️ 通用回退（1个）
- 统计信息
  - 📊 已注册适配器数量
  - 🛡️ 回退适配器状态

**截图预览：**
```
┌────────────────────────────────────┐
│ 📋 属性面板                        │
│                                    │
│ 未选择对象                          │
│ 在时间线或场景中选择一个对象...     │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ ✨ 支持的对象类型（17种）           │
│                                    │
│ ▼ 🎬 时间线系统（3个）              │
│   • 时间线项目 (TimelineProject)   │
│   • 时间线序列 (TimelineSequence)  │
│   • 时间线轨道 (TimelineTrack)     │
│                                    │
│ ▶ 🎯 核心对象（3个）                │
│ ▶ 🌟 扩展系统（6个）                │
│ ▶ 🔧 扩展类型（3个）                │
│ ▶ 🛡️ 通用回退（1个）               │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ 📊 已注册适配器: 16 个              │
│ 🛡️ 回退适配器: 已启用              │
└────────────────────────────────────┘
```

---

### 2️⃣ 正常编辑状态

**触发条件：** `currentSelection != null && currentAdapter != null`

**显示内容：**
- 适配器名称（标题）
- 对象类型信息
- 🔍 调试按钮
- 分隔线
- **属性编辑器内容**（由适配器渲染）

**截图预览：**
```
┌────────────────────────────────────┐
│ 🔑 关键帧属性编辑器                │
│ 类型: Keyframe        [🔍 调试]   │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ ▼ ⏱️ 时间                          │
│   时间戳: [0.50] ────────────      │
│                                    │
│ ▼ 📍 位置                          │
│   XYZ: [0.0] [0.0] [0.0]          │
│                                    │
│ ▼ 🔄 旋转                          │
│   XYZ: [0.0] [0.0] [0.0]          │
│                                    │
│ ▼ 📏 缩放                          │
│   XYZ: [1.0] [1.0] [1.0]          │
│                                    │
│ ▼ 👁️ 不透明度                      │
│   不透明度: [1.0] ────────────     │
│                                    │
│ ▼ 🎯 插值类型                      │
│   插值: [Linear ▼]                 │
└────────────────────────────────────┘
```

---

### 3️⃣ 无适配器状态

**触发条件：** `currentSelection != null && currentAdapter == null`

**显示内容：**
- ⚠️ 无可用的属性编辑器（标题）
- 警告消息（黄色）
- 对象信息
  - 类名
  - 完整路径
  - 包名
- 解决方案
  - 1️⃣ 创建专用适配器
  - 2️⃣ 使用通用适配器
- 🔍 查看注册表详情按钮

**截图预览：**
```
┌────────────────────────────────────┐
│ ⚠️ 无可用的属性编辑器              │
│                                    │
│ 对象类型 "MyCustomObject"          │
│ 没有注册属性适配器                 │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ 📦 对象信息:                       │
│   • 类名: MyCustomObject           │
│   • 完整路径: com.example.MyCustom │
│   • 包名: com.example              │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ 💡 解决方案:                       │
│ 1️⃣ 创建专用适配器:                 │
│   • 实现 IPropertyAdapter<Type>    │
│   • 在 PropertyAdapterBootstrap    │
│     中注册                         │
│                                    │
│ 2️⃣ 使用通用适配器:                 │
│   • 确保 GenericPropertyAdapter   │
│     已注册为回退适配器             │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ [ 🔍 查看注册表详情 ]              │
└────────────────────────────────────┘
```

---

### 4️⃣ 错误状态

**触发条件：** 渲染属性时抛出异常

**显示内容：**
- ❌ 渲染错误（标题）
- 错误消息（红色）
- 错误详情
  - 异常类型
  - 错误消息
- 堆栈跟踪（折叠）
- 建议操作
- 🔄 清除选择并重试按钮

**截图预览：**
```
┌────────────────────────────────────┐
│ ❌ 渲染错误                        │
│                                    │
│ 渲染属性时发生错误                 │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ 📋 错误详情:                       │
│   • 异常类型: NullPointerException │
│   • 错误消息: position is null     │
│                                    │
│ ▶ 🔍 堆栈跟踪                      │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│ 💡 建议操作:                       │
│   • 检查对象是否包含所有必需属性   │
│   • 查看日志获取完整错误堆栈       │
│   • 确认适配器实现是否正确         │
│   • 尝试重新选择对象               │
│                                    │
│ [ 🔄 清除选择并重试 ]              │
└────────────────────────────────────┘
```

---

## 🔄 工作流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1️⃣ 用户在时间线/场景中选择对象                              │
│    SelectionManager.get().setCurrentSelection(object)       │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 2️⃣ SelectionManager 触发事件                                │
│    SelectionChangedEvent → PropertiesPanel.onSelectionChanged│
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 3️⃣ PropertiesPanel 更新选择                                 │
│    updateSelection(newSelection)                            │
│    ↓                                                        │
│    currentSelection = newSelection                          │
│    currentAdapter = PropertyAdapterRegistry.getAdapterFor() │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 4️⃣ PropertyAdapterRegistry 查找适配器                       │
│    ├─ 精确匹配 (Exact Match)                                │
│    ├─ 继承链查找 (isAssignableFrom)                         │
│    ├─ supports() 方法检查                                   │
│    └─ Fallback 适配器 (GenericPropertyAdapter)             │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 5️⃣ PropertiesPanel 渲染 UI                                  │
│    doRender()                                               │
│    ↓                                                        │
│    if (currentSelection == null)                            │
│        renderNoSelectionMessage()                           │
│    else if (currentAdapter == null)                         │
│        renderNoAdapterMessage()                             │
│    else                                                     │
│        renderObjectTypeHeader()                             │
│        currentAdapter.renderProperties(currentSelection)    │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 6️⃣ 用户修改属性                                             │
│    ImGui.dragFloat3("Position", ...)                        │
│    ↓                                                        │
│    modified = true                                          │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 7️⃣ 适配器触发回调                                           │
│    adapter.onPropertyChanged(object)                        │
│    ↓                                                        │
│    - SceneManager.markDirty()                               │
│    - EventBus.publish(PropertyChangedEvent)                 │
│    - CommandManager.record(ModifyCommand)                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧠 使用示例

### 示例 1：选择关键帧

```java
// 用户在时间线中点击关键帧
Keyframe kf = timeline.getKeyframe(0);

// 选择管理器设置当前选择
SelectionManager.get().setCurrentSelection(kf);

// PropertiesPanel 自动切换
// → 触发事件: SelectionChangedEvent
// → 查找适配器: KeyframePropertyAdapter
// → 渲染 UI: 关键帧属性编辑器
```

### 示例 2：选择相机

```java
// 用户在场景中选择相机
Camera camera = scene.getCamera();

// 设置选择
SelectionManager.get().setCurrentSelection(camera);

// PropertiesPanel 自动切换
// → 查找适配器: CameraPropertyAdapter (通过 supports())
// → 渲染 UI: 相机属性编辑器（FOV、景深、镜头特效等）
```

### 示例 3：选择未知对象

```java
// 用户选择了一个自定义对象
MyCustomObject obj = new MyCustomObject();

// 设置选择
SelectionManager.get().setCurrentSelection(obj);

// PropertiesPanel 自动切换
// → 查找适配器: 
//   1. 精确匹配 → 未找到
//   2. 继承链查找 → 未找到
//   3. supports() 检查 → 未找到
//   4. Fallback → GenericPropertyAdapter
// → 渲染 UI: 通用反射属性编辑器
```

---

## 🛠️ 调试功能

### 调试按钮

在正常编辑状态下，标题栏右侧有一个 **🔍 调试** 按钮：

```java
if (ImGui.smallButton("🔍 调试")) {
    LOGGER.info("[PropertiesPanel] 调试信息:\n{}", 
        currentAdapter.getDebugInfo(currentSelection));
    LOGGER.info("[PropertiesPanel] 注册表状态:\n{}", 
        PropertyAdapterRegistry.getDebugInfo());
}
```

**输出示例：**
```
[PropertiesPanel] 调试信息:
[PropertyAdapter] KeyframePropertyAdapter - com.chronoblocks.core.animation.Keyframe

[PropertiesPanel] 注册表状态:
PropertyAdapterRegistry [16 adapters]:
  - Keyframe → KeyframePropertyAdapter
  - Container → ContainerPropertyAdapter
  - AnimationClip → ClipPropertyAdapter
  - Vector3f → Vector3fPropertyAdapter
  - Vector4f → ColorPropertyAdapter
  - List → ListPropertyAdapter
  - Object → TimelineProjectPropertyAdapter
  - Object → TimelineSequencePropertyAdapter
  ...
  Fallback: GenericPropertyAdapter
```

---

## 📊 性能监控

### 定期检查机制

PropertiesPanel 使用**双重检测机制**确保选择同步：

1. **事件驱动**（主要）：监听 `SelectionChangedEvent`
2. **定期轮询**（备用）：每 100ms 检查一次选择变化

```java
// 定期检查选择变化（备用机制）
long currentTime = System.currentTimeMillis();
if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
    checkSelectionChange();
    lastUpdateTime = currentTime;
}
```

**优点：**
- 事件丢失时也能及时更新
- 双重保险确保 UI 一致性

---

## 🎯 扩展指南

### 如何添加新的对象类型支持？

**步骤：**

1️⃣ **创建专用适配器**
```java
public class MyObjectPropertyAdapter implements IPropertyAdapter<MyObject> {
    @Override
    public Class<MyObject> getTargetType() {
        return MyObject.class;
    }
    
    @Override
    public boolean renderProperties(MyObject obj) {
        // 渲染 UI
        return false;
    }
}
```

2️⃣ **注册到 Bootstrap**
```java
// 在 PropertyAdapterBootstrap.initialize() 中添加
registerAdapter(MyObject.class, new MyObjectPropertyAdapter());
registeredCount++;
```

3️⃣ **使用选择管理器**
```java
// 在用户交互代码中
MyObject obj = ...;
SelectionManager.get().setCurrentSelection(obj);
```

4️⃣ **完成！** 🎉
PropertiesPanel 会自动识别并显示新的属性编辑器。

---

## 🔧 API 参考

### 关键方法

| 方法 | 描述 |
|------|------|
| `doRender()` | 主渲染方法，根据状态显示不同 UI |
| `onSelectionChanged(Object)` | 选择变化回调（事件驱动） |
| `checkSelectionChange()` | 检查选择变化（轮询备用） |
| `updateSelection(Object)` | 更新当前选择和适配器 |
| `renderObjectTypeHeader()` | 渲染对象类型标题 |
| `renderNoSelectionMessage()` | 渲染无选择消息 |
| `renderNoAdapterMessage()` | 渲染无适配器消息 |
| `renderErrorMessage(Exception)` | 渲染错误消息 |

### 关键字段

| 字段 | 类型 | 描述 |
|------|------|------|
| `currentSelection` | `Object` | 当前选择的对象 |
| `currentAdapter` | `IPropertyAdapter<Object>` | 当前适配器 |
| `lastUpdateTime` | `long` | 上次更新时间（用于轮询） |
| `UPDATE_INTERVAL` | `long` | 更新间隔（100ms） |

---

## 🎉 总结

✅ **PropertiesPanel v4.9 提供：**
- 🎯 智能的对象类型识别
- 🎨 动态的 UI 切换
- 📊 详细的调试信息
- 🛡️ 完善的错误处理
- ↔️ 双向数据绑定
- 🔄 事件驱动更新

✅ **17种对象类型全覆盖：**
- 3个时间线系统
- 3个核心对象
- 6个扩展系统
- 3个扩展类型
- 1个通用回退

✅ **4种 UI 状态：**
- 无选择状态
- 正常编辑状态
- 无适配器状态
- 错误状态

---

**🎊 PropertiesPanel UI 集成 v4.9 - 企业级实现完成！**

