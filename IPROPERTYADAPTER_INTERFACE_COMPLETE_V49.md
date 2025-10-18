# 🧩 IPropertyAdapter&lt;T&gt; 接口完整文档 v4.9

**ChronoBlocks 属性系统核心接口** - 2025-10-16

---

## 📋 接口概述

`IPropertyAdapter<T>` 是 ChronoBlocks 属性系统的**基础抽象层**，定义了一个对象在属性面板中如何被编辑与展示。

**位置：** `com.chronoblocks.ui.properties.IPropertyAdapter`

---

## ✨ 核心职责

| 职责 | 描述 |
|------|------|
| 🎯 **类型匹配** | 通过 `getTargetType()` 提供目标类型 |
| 🎨 **UI 渲染** | 通过 `renderProperties()` 渲染属性编辑 UI |
| 🔄 **变更通知** | 通过 `onPropertyChanged()` 响应属性变化 |
| ⚙️ **扩展功能** | 支持 update / reset / validate 等扩展方法 |

---

## 📊 方法详解

### ✅ 核心必需方法（2个）

#### 1️⃣ `boolean renderProperties(T target)`

**功能：** 渲染属性编辑 UI

**参数：**
- `target` - 当前被选中的对象实例

**返回值：**
- `true` - 有任何属性被修改
- `false` - 无修改

**典型实现：**
```java
@Override
public boolean renderProperties(Vector3f vec) {
    float[] values = {vec.x, vec.y, vec.z};
    boolean changed = ImGui.dragFloat3("XYZ", values, 0.05f);
    if (changed) {
        vec.set(values[0], values[1], values[2]);
        onPropertyChanged(vec);
    }
    return changed;
}
```

**常用 ImGui 控件：**
- `ImGui.text()` - 显示标签
- `ImGui.dragFloat()` - 数值拖动条
- `ImGui.inputFloat3()` - 三维向量输入
- `ImGui.checkbox()` - 布尔值勾选框
- `ImGui.colorEdit4()` - RGBA 颜色拾色器
- `ImGui.combo()` - 下拉选择框
- `ImGui.sliderFloat()` - 范围滑动条

---

#### 2️⃣ `Class<T> getTargetType()`

**功能：** 获取此适配器适用的目标类型

**返回值：**
- 目标类型的 `Class` 对象

**典型实现：**
```java
@Override
public Class<Vector3f> getTargetType() {
    return Vector3f.class;
}
```

---

### ⚙️ 可选扩展方法（6个）

#### 3️⃣ `String getAdapterName()`

**功能：** 获取适配器的显示名称

**返回值：**
- 显示名称，例如 `"Keyframe Properties"`

**默认实现：**
```java
default String getAdapterName() {
    Class<T> type = getTargetType();
    if (type == null || type == Object.class) {
        return "属性编辑器";
    }
    return type.getSimpleName() + " Properties";
}
```

**自定义示例：**
```java
@Override
public String getAdapterName() {
    return "🎥 相机属性编辑器";
}
```

---

#### 4️⃣ `boolean supports(Object target)`

**功能：** 检查是否支持给定对象（用于复杂类型匹配）

**参数：**
- `target` - 要检查的对象

**返回值：**
- `true` - 支持此对象
- `false` - 不支持

**默认实现：**
```java
default boolean supports(Object target) {
    if (target == null) {
        return false;
    }
    Class<T> type = getTargetType();
    if (type == null) {
        return false;
    }
    return type.isAssignableFrom(target.getClass());
}
```

**自定义示例（反射式适配器）：**
```java
@Override
public boolean supports(Object target) {
    if (target == null) return false;
    String className = target.getClass().getSimpleName().toLowerCase();
    return className.contains("camera");
}
```

---

#### 5️⃣ `void onPropertyChanged(T target)`

**功能：** 当属性修改后触发

**参数：**
- `target` - 当前对象

**典型用例：**
- 标记场景为 dirty 状态，触发重绘
- 触发撤销/重做命令记录
- 发布事件通知其他组件
- 验证属性合法性

**默认实现：**
```java
default void onPropertyChanged(T target) {
    LOGGER.debug("[PropertyAdapter] 属性已更新: {}", 
        target != null ? target.getClass().getSimpleName() : "null");
}
```

**自定义示例：**
```java
@Override
public void onPropertyChanged(Vector3f vec) {
    // 标记场景为 dirty 状态
    SceneManager.markDirty();
    
    // 发布事件
    EventBus.publish(new PropertyChangedEvent(vec));
    
    // 记录撤销命令
    CommandManager.record(new ModifyVectorCommand(vec));
}
```

---

#### 6️⃣ `void update(T target, float deltaTime)`

**功能：** 每帧更新

**参数：**
- `target` - 当前对象
- `deltaTime` - 每帧时间（秒）

**典型用例：**
- 更新实时预览
- 刷新动画状态
- 计算派生属性
- 更新统计信息

**默认实现：**
```java
default void update(T target, float deltaTime) {
    // 默认空实现
}
```

**自定义示例：**
```java
@Override
public void update(AnimationClip clip, float deltaTime) {
    // 更新当前帧预览
    float currentTime = clip.getCurrentTime() + deltaTime;
    clip.setCurrentTime(currentTime);
    
    // 刷新关键帧插值
    clip.updateInterpolation();
}
```

---

#### 7️⃣ `void resetToDefault(T target)`

**功能：** 重置对象属性到默认值

**参数：**
- `target` - 当前对象

**典型用例：**
- 重置关键帧到起始状态
- 清空容器内容
- 恢复默认材质

**默认实现：**
```java
default void resetToDefault(T target) {
    LOGGER.debug("[PropertyAdapter] 属性重置: {}", 
        target != null ? target.getClass().getSimpleName() : "null");
}
```

**自定义示例：**
```java
@Override
public void resetToDefault(Keyframe kf) {
    kf.setTime(0.0f);
    kf.setPosition(new Vector3f(0, 0, 0));
    kf.setRotation(new Vector3f(0, 0, 0));
    kf.setScale(new Vector3f(1, 1, 1));
    kf.setOpacity(1.0f);
    onPropertyChanged(kf);
}
```

---

#### 8️⃣ `boolean validate(T target)`

**功能：** 属性合法性验证

**参数：**
- `target` - 当前对象

**返回值：**
- `true` - 合法
- `false` - 不合法

**典型用例：**
- 检查时间戳是否为负数
- 检查向量是否为 NaN
- 检查引用是否为 null
- 检查范围是否合法

**默认实现：**
```java
default boolean validate(T target) {
    return true; // 默认总是合法
}
```

**自定义示例：**
```java
@Override
public boolean validate(Keyframe kf) {
    // 检查时间戳
    if (kf.getTime() < 0) {
        LOGGER.error("关键帧时间不能为负数: {}", kf.getTime());
        return false;
    }
    
    // 检查向量是否为 NaN
    Vector3f pos = kf.getPosition();
    if (Float.isNaN(pos.x) || Float.isNaN(pos.y) || Float.isNaN(pos.z)) {
        LOGGER.error("关键帧位置包含 NaN 值");
        return false;
    }
    
    // 检查透明度范围
    if (kf.getOpacity() < 0 || kf.getOpacity() > 1) {
        LOGGER.error("透明度必须在 0-1 范围内: {}", kf.getOpacity());
        return false;
    }
    
    return true;
}
```

---

## 🧠 完整实现示例

### 示例 1：简单类型适配器（Vector3f）

```java
package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import org.joml.Vector3f;
import imgui.ImGui;

public class Vector3fPropertyAdapter implements IPropertyAdapter<Vector3f> {
    
    @Override
    public Class<Vector3f> getTargetType() {
        return Vector3f.class;
    }
    
    @Override
    public boolean renderProperties(Vector3f vec) {
        float[] values = {vec.x, vec.y, vec.z};
        boolean changed = ImGui.dragFloat3("XYZ", values, 0.05f);
        if (changed) {
            vec.set(values[0], values[1], values[2]);
            onPropertyChanged(vec);
        }
        return changed;
    }
    
    @Override
    public String getAdapterName() {
        return "Vector3f 属性编辑器";
    }
    
    @Override
    public void onPropertyChanged(Vector3f vec) {
        // 标记场景为 dirty 状态
        // SceneManager.markDirty();
        LOGGER.debug("Vector3f 已更新: ({}, {}, {})", vec.x, vec.y, vec.z);
    }
    
    @Override
    public boolean validate(Vector3f vec) {
        return !Float.isNaN(vec.x) && !Float.isNaN(vec.y) && !Float.isNaN(vec.z);
    }
}
```

---

### 示例 2：复杂对象适配器（Keyframe）

```java
package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

public class KeyframePropertyAdapter implements IPropertyAdapter<Keyframe> {
    
    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
    
    @Override
    public boolean renderProperties(Keyframe kf) {
        boolean modified = false;
        
        UITheme.title("🔑 关键帧属性");
        
        // 时间戳
        if (ImGui.collapsingHeader("⏱️ 时间", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] time = {kf.getTime()};
            if (ImGui.dragFloat("时间戳", time, 0.01f, 0.0f, 100.0f)) {
                kf.setTime(time[0]);
                modified = true;
            }
        }
        
        // 位置
        if (ImGui.collapsingHeader("📍 位置", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f pos = kf.getPosition();
            float[] posArr = {pos.x, pos.y, pos.z};
            if (ImGui.dragFloat3("XYZ", posArr, 0.1f)) {
                kf.setPosition(new Vector3f(posArr[0], posArr[1], posArr[2]));
                modified = true;
            }
        }
        
        // 旋转
        if (ImGui.collapsingHeader("🔄 旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f rot = kf.getRotation();
            float[] rotArr = {rot.x, rot.y, rot.z};
            if (ImGui.dragFloat3("XYZ", rotArr, 0.5f, -360.0f, 360.0f)) {
                kf.setRotation(new Vector3f(rotArr[0], rotArr[1], rotArr[2]));
                modified = true;
            }
        }
        
        if (modified) {
            onPropertyChanged(kf);
        }
        
        return modified;
    }
    
    @Override
    public String getAdapterName() {
        return "🔑 关键帧属性编辑器";
    }
    
    @Override
    public void onPropertyChanged(Keyframe kf) {
        // 发布事件
        EventBus.publish(new KeyframeModifiedEvent(kf));
        
        // 标记为 dirty
        SceneManager.markDirty();
    }
    
    @Override
    public void resetToDefault(Keyframe kf) {
        kf.setTime(0.0f);
        kf.setPosition(new Vector3f(0, 0, 0));
        kf.setRotation(new Vector3f(0, 0, 0));
        kf.setScale(new Vector3f(1, 1, 1));
        kf.setOpacity(1.0f);
        onPropertyChanged(kf);
    }
    
    @Override
    public boolean validate(Keyframe kf) {
        if (kf.getTime() < 0) {
            LOGGER.error("关键帧时间不能为负数");
            return false;
        }
        
        Vector3f pos = kf.getPosition();
        if (Float.isNaN(pos.x) || Float.isNaN(pos.y) || Float.isNaN(pos.z)) {
            LOGGER.error("关键帧位置包含 NaN 值");
            return false;
        }
        
        return true;
    }
}
```

---

### 示例 3：反射式适配器（Camera）

```java
package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImFloat;

public class CameraPropertyAdapter implements IPropertyAdapter<Object> {
    
    @Override
    public Class<Object> getTargetType() {
        return Object.class; // 反射式适配器使用 Object
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("camera");
    }
    
    @Override
    public boolean renderProperties(Object camera) {
        boolean modified = false;
        
        UITheme.title("🎥 相机属性");
        
        // 使用反射获取/设置属性
        float currentFov = getFloatProperty(camera, "fov", "getFov", 70.0f);
        ImFloat fov = new ImFloat(currentFov);
        if (ImGui.dragFloat("视野 (FOV)", fov, 0.1f, 10.0f, 150.0f)) {
            if (setFloatProperty(camera, "fov", "setFov", fov.get())) {
                modified = true;
            }
        }
        
        if (modified) {
            onPropertyChanged(camera);
        }
        
        return modified;
    }
    
    @Override
    public String getAdapterName() {
        return "🎥 相机属性编辑器";
    }
    
    // 反射辅助方法
    private float getFloatProperty(Object obj, String field, String getter, float def) {
        try {
            Method method = obj.getClass().getMethod(getter);
            return ((Number) method.invoke(obj)).floatValue();
        } catch (Exception e) {
            return def;
        }
    }
    
    private boolean setFloatProperty(Object obj, String field, String setter, float value) {
        try {
            Method method = obj.getClass().getMethod(setter, float.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## 🔄 工作流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1️⃣ 用户选择对象                                              │
│    SelectionManager.select(keyframe)                        │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 2️⃣ PropertiesPanel 获取适配器                               │
│    adapter = PropertyAdapterRegistry.getAdapterFor(keyframe)│
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 3️⃣ 渲染 UI                                                   │
│    modified = adapter.renderProperties(keyframe)            │
│    ↓                                                        │
│    - ImGui.dragFloat3("Position", ...)                     │
│    - ImGui.dragFloat3("Rotation", ...)                     │
│    - ImGui.sliderFloat("Opacity", ...)                     │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 4️⃣ 用户修改属性                                              │
│    modified = true                                          │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 5️⃣ 触发回调                                                  │
│    adapter.onPropertyChanged(keyframe)                      │
│    ↓                                                        │
│    - SceneManager.markDirty()                               │
│    - EventBus.publish(PropertyChangedEvent)                 │
│    - CommandManager.record(ModifyCommand)                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 方法速查表

| 方法 | 必需 | 默认实现 | 典型用途 |
|------|------|----------|----------|
| `renderProperties()` | ✅ | 无 | 渲染 UI |
| `getTargetType()` | ✅ | 无 | 返回类型 |
| `getAdapterName()` | ⚙️ | `类型名 + " Properties"` | 显示标题 |
| `supports()` | ⚙️ | `isAssignableFrom` 检查 | 类型匹配 |
| `onPropertyChanged()` | ⚙️ | 日志输出 | 变更通知 |
| `update()` | ⚙️ | 空实现 | 每帧刷新 |
| `resetToDefault()` | ⚙️ | 日志输出 | 重置属性 |
| `validate()` | ⚙️ | 总是返回 `true` | 合法性验证 |
| `getDebugInfo()` | ⚙️ | 返回类型信息 | 调试输出 |

---

## 🎯 最佳实践

### ✅ 推荐做法

1. **返回值正确性**
   - `renderProperties()` 只在有修改时返回 `true`
   - 避免每帧都返回 `true` 导致不必要的刷新

2. **调用 onPropertyChanged()**
   - 修改属性后立即调用 `onPropertyChanged()`
   - 在回调中发布事件、标记 dirty、记录命令

3. **使用 UITheme**
   - 使用 `UITheme.title()` 统一标题样式
   - 使用 `UITheme.separator()` 分隔区域
   - 使用 `UITheme.label()` 显示提示信息

4. **折叠式分组**
   - 使用 `ImGui.collapsingHeader()` 分组属性
   - 为常用属性添加 `ImGuiTreeNodeFlags.DefaultOpen`

5. **Tooltip 提示**
   - 使用 `ImGui.setTooltip()` 提供详细说明
   - 在 `ImGui.isItemHovered()` 后调用

### ❌ 避免做法

1. **不要在 renderProperties() 中修改全局状态**
   - 只修改传入的 `target` 对象
   - 全局状态修改应在 `onPropertyChanged()` 中

2. **不要忘记调用父类方法**
   - 如果覆写 `onPropertyChanged()`，记得调用 `super.onPropertyChanged()`

3. **不要阻塞渲染线程**
   - 避免在 `renderProperties()` 中执行耗时操作
   - 使用异步任务处理重量级计算

---

## 🎉 总结

✅ **IPropertyAdapter<T> 接口提供：**
- 🎯 类型安全的属性编辑框架
- 🎨 灵活的 UI 渲染机制
- 🔄 完善的变更通知系统
- ⚙️ 丰富的扩展功能

✅ **8个方法覆盖：**
- 2个核心必需方法
- 6个可选扩展方法

✅ **支持场景：**
- 简单类型（Vector3f、Color）
- 复杂对象（Keyframe、Container）
- 反射式适配器（Camera、Physics）
- 列表与容器（List、Array）

---

**🎊 IPropertyAdapter&lt;T&gt; v4.9 - 企业级接口定义完成！**

