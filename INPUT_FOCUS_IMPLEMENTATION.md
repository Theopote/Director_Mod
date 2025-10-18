# ChronoBlocks 输入焦点管理实现总结

## 📋 功能概述

实现了智能输入焦点切换功能，当ChronoBlocks UI窗口打开时：
- **鼠标在UI面板上**：鼠标和键盘操作UI
- **鼠标在空白区域**：鼠标和键盘操作游戏
- **按住鼠标中键拖动**：移动玩家视角（仅在空白区域有效）
- **WASD键**：移动角色（仅在空白区域有效）
- **窗口关闭时**：恢复正常游戏操作

## 🔧 核心修复

### 1. DockSpace PassthruCentralNode 标志
**问题**：DockSpace窗口覆盖整个屏幕，导致ImGui认为中央空白区域也是UI  
**修复**：在 `ChronoBlocksUIManager.java` (第559行) 添加 `PassthruCentralNode` 标志

```java
imgui.ImGui.dockSpace(dockspaceId, 0, 0, imgui.internal.flag.ImGuiDockNodeFlags.PassthruCentralNode);
```

**效果**：中央空白区域不再捕获鼠标事件，完全透传给游戏

### 2. 移除中央保护区域窗口
**问题**：`renderCentralProtectionArea()` 创建的透明窗口被ImGui识别为UI元素  
**修复**：禁用该方法的调用 (ChronoBlocksUIManager.java 第478行)

```java
// ❌ 暂时禁用中央保护区域渲染 - 这会导致ImGui认为鼠标在UI上
// renderCentralProtectionArea();
```

### 3. 直接使用 ChronoBlocksScreen.isMouseOverChronoBlocksGui()
**问题**：`InputFocusManager` 在render循环中更新，与鼠标事件不同步  
**修复**：所有Mixin现在直接调用 `screen.isMouseOverChronoBlocksGui(x, y)`

```java
// MouseMixin.java, KeyboardInputMixin.java
boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(x, y);
```

### 4. 中键事件优先处理
**问题**：中键事件在 `ChronoBlocksScreen` 检查之后，可能被跳过  
**修复**：中键事件处理移到所有检查之前 (MouseMixin.java 第68-76行)

```java
// 中键事件处理必须在最前面
if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
    if (action == GLFW.GLFW_PRESS) {
        chronoblocks$isDragging = true;
    } else if (action == GLFW.GLFW_RELEASE) {
        chronoblocks$isDragging = false;
    }
    return;
}
```

### 5. 提高鼠标灵敏度
**配置**：将 `LOOK_SENSITIVITY` 从 `2.5` 提高到 `5.0`

```java
// MouseMixin.java 第36行
private static final double LOOK_SENSITIVITY = 5.0;  // 🎯 提高灵敏度，让视角移动更快
```

## 📁 涉及的文件

### 核心文件
1. **`src/main/java/com/chronoblocks/mixin/MouseMixin.java`** (107行)
   - 处理鼠标移动、按钮、滚轮、光标锁定事件
   - 实现中键拖动视角移动
   - 根据鼠标位置拦截/传递事件

2. **`src/main/java/com/chronoblocks/mixin/KeyboardInputMixin.java`** (69行)
   - 处理WASD移动键输入
   - 根据鼠标位置允许/禁止移动

3. **`src/main/java/com/chronoblocks/ui/ChronoBlocksScreen.java`**
   - 提供 `isMouseOverChronoBlocksGui()` 方法
   - 管理窗口生命周期

4. **`src/main/java/com/chronoblocks/ui/ChronoBlocksUIManager.java`**
   - DockSpace配置和渲染
   - 禁用中央保护区域窗口

5. **`src/main/java/com/chronoblocks/ui/input/InputFocusManager.java`**
   - 管理ChronoBlocks窗口激活状态
   - 处理鼠标锁定/解锁

### 配置文件
6. **`src/main/resources/chronoblocks.client.mixins.json`**
   - 注册 `MouseMixin`
   - 注册 `KeyboardInputMixin`
   - 注册 `KeyboardMixin`

## 🎮 使用说明

### 基本操作
1. **打开ChronoBlocks窗口**（快捷键或命令）
2. **UI交互**：
   - 鼠标移动到任何面板上
   - 点击、拖动、滚轮等操作UI元素
3. **游戏操作**：
   - 鼠标移动到中央空白区域（不在任何面板上）
   - **按住鼠标中键** + 拖动：移动视角
   - **WASD键**：移动角色
   - **空格键**：跳跃
   - **Shift键**：潜行
   - **Ctrl键**：疾跑
4. **关闭窗口**：恢复正常游戏模式

### 灵敏度调整
如果视角移动速度不满意，可以调整 `MouseMixin.java` 中的 `LOOK_SENSITIVITY`：
- **更快**：增大数值（如 `7.0`、`10.0`）
- **更慢**：减小数值（如 `3.0`、`4.0`）

## 🏗️ 架构设计

### 输入事件流程
```
用户输入
    ↓
Minecraft事件系统
    ↓
Mixin拦截 (MouseMixin/KeyboardInputMixin)
    ↓
检查：screen.isMouseOverChronoBlocksGui(x, y)
    ↓
    ├─ true (鼠标在UI上)
    │   └→ 拦截事件 (ci.cancel())，让ImGui处理
    │
    └─ false (鼠标在空白区域)
        └→ 传递给游戏
            ├─ 中键拖动 → 视角移动
            └─ WASD键 → 角色移动
```

### ImGui鼠标捕获检测
```
ImGui.getIO().getWantCaptureMouse()
    ↓
检查：鼠标是否在任何ImGui窗口或控件上
    ↓
    ├─ DockSpace窗口 (带PassthruCentralNode)
    │   ├─ 中央节点：false ✅
    │   └─ 面板区域：true
    │
    └─ 其他ImGui窗口：true
```

## ✅ 测试验证

### 功能测试
- [x] 鼠标在面板上时，可以操作UI
- [x] 鼠标在空白区域时，UI不响应鼠标
- [x] 按住中键拖动可以移动视角
- [x] WASD键可以移动角色
- [x] 鼠标移动到边缘时，正确切换焦点
- [x] 关闭窗口后，游戏操作恢复正常

### 性能测试
- [x] 无明显性能影响
- [x] 调试日志已全部移除
- [x] 编译成功，无警告

## 📝 技术要点

### PassthruCentralNode 标志
这是ImGui的一个特殊标志，用于DockSpace：
- **作用**：让中央节点不捕获鼠标事件
- **效果**：只有停靠的面板才捕获鼠标，中央区域完全透明
- **重要性**：这是解决输入焦点问题的**关键**

### 直接检查 vs InputFocusManager
- **问题**：`InputFocusManager.update()` 在 `render()` 中调用，但鼠标事件在不同线程
- **解决**：Mixin直接调用 `screen.isMouseOverChronoBlocksGui()`，实时检测
- **优势**：无延迟，准确反映当前鼠标位置

### 中键事件优先级
- **原因**：中键用于游戏操作（视角移动），不是UI操作
- **实现**：在任何 `ChronoBlocksScreen` 检查之前就处理中键
- **效果**：确保中键事件总是被正确捕获

## 🚀 未来优化

### 可选增强
1. **可配置的灵敏度**：通过UI设置调整鼠标灵敏度
2. **自定义快捷键**：允许用户自定义中键为其他按键
3. **吸附边缘**：鼠标靠近边缘时自动切换焦点
4. **多显示器支持**：正确处理多显示器环境

### 性能优化
1. **减少频繁的坐标检查**：缓存鼠标位置状态
2. **异步事件处理**：使用事件队列减少主线程负担

## 📚 参考资料

- **TreeFactory Mod**：输入处理架构参考
- **ImGui Documentation**：PassthruCentralNode标志说明
- **Fabric Mixin Tutorial**：Mixin注入技术

## 👥 贡献者

- **用户需求**：智能输入焦点切换
- **实现**：基于TreeFactory架构的优化版本
- **测试**：完整的功能和性能验证

---

**版本**：1.0  
**日期**：2025-10-13  
**状态**：✅ 完成并通过测试

