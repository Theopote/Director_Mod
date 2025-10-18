# ChronoBlocks 图标字体迁移指南

> **版本**: v1.0  
> **日期**: 2025-10-18  
> **目标**: 将 Emoji 图标迁移到自定义 IcoMoon 图标字体

---

## 📋 迁移概述

本指南帮助您将 ChronoBlocks UI 中的 Emoji 图标（如📦、▶、🔍）替换为自定义的 IcoMoon 图标字体。

### ✅ 已完成的工作

1. ✅ **字体文件部署** - `icomoon.ttf` 已放置在 `src/main/resources/assets/chronoblocks/fonts/`
2. ✅ **Icons 常量类** - 创建了 `com.chronoblocks.ui.theme.Icons` 类
3. ✅ **字体管理器更新** - `ImGuiFontManager` 已添加图标字体加载逻辑
4. ✅ **字体范围配置** - Private Use Area (U+E000 - U+F8FF) 已配置

### 🎯 需要迁移的面板

- MenuBarPanel
- ToolbarPanel  
- TimelinePanel
- PropertiesPanel
- KeyframePropertiesPanel
- ContainerPanel
- ContainerPropertiesPanel
- FontTestPanel

---

## 🔄 迁移步骤

### 步骤 1: 导入 Icons 类

在每个面板的开头添加导入：

```java
import com.chronoblocks.ui.theme.Icons;
```

### 步骤 2: 替换 Emoji 为图标常量

使用查找替换功能，将 Emoji 字符串替换为对应的图标常量。

---

## 📚 图标映射表

### 编辑操作类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| ➕ | `"➕"` | `Icons.ADD` | 添加/新建 |
| ✏ | `"✏"` | `Icons.EDIT` | 编辑 |
| 🗑 | `"🗑"` | `Icons.DELETE` | 删除 |
| 📋 | `"📋"` | `Icons.COPY` | 复制 |
| ↶ | `"↶"` | `Icons.UNDO` | 撤销 |
| ↷ | `"↷"` | `Icons.REDO` | 重做 |
| 🔍 | `"🔍"` | `Icons.SEARCH` | 搜索 |
| 🔄 | `"🔄"` | `Icons.REFRESH` | 刷新 |

### 播放控制类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| ▶ | `"▶"` | `Icons.PLAY` | 播放 |
| ⏸ | `"⏸"` | `Icons.PAUSE` | 暂停 |
| ⏹ | `"⏹"` | `Icons.STOP` | 停止 |
| 🔁 | `"🔁"` | `Icons.LOOP` | 循环 |
| ⏱ | `"⏱"` | `Icons.TIME` | 时间 |
| \|◀ | `"\|◀"` | `Icons.SKIP_BACK` | 跳到开始 |
| ▶\| | `"▶\|"` | `Icons.SKIP_FORWARD` | 跳到结束 |

### 文件操作类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| 📁 | `"📁"` | `Icons.FOLDER` | 文件夹 |
| 📄 | `"📄"` | `Icons.FILE` | 文件 |
| 💾 | `"💾"` | `Icons.SAVE` | 保存 |
| 📋 | `"📋"` | `Icons.CLIPBOARD` | 剪贴板 |
| 📦 | `"📦"` | `Icons.PACKAGE` | 包裹 |

### 容器类型类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| 📦 | `"📦"` | `Icons.CONTAINER` | 容器 |
| 🏢 | `"🏢"` | `Icons.BUILDING` | 建筑 |
| 🗺️ | `"🗺️"` | `Icons.AREA` | 区域 |
| ✨ | `"✨"` | `Icons.PARTICLE` | 粒子 |
| 🎬 | `"🎬"` | `Icons.ANIMATION` | 动画 |
| 🧱 | `"🧱"` | `Icons.BLOCK` | 方块 |

### 工具类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| 🛠 | `"🛠"` | `Icons.TOOLS` | 工具 |
| 🧰 | `"🧰"` | `Icons.TOOLBOX` | 工具箱 |
| 🔧 | `"🔧"` | `Icons.WRENCH` | 扳手 |
| 🔨 | `"🔨"` | `Icons.HAMMER` | 锤子 |
| ⚙ | `"⚙"` | `Icons.SETTINGS` | 设置 |
| 📐 | `"📐"` | `Icons.RULER` | 尺子 |

### 查看信息类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| 👁 | `"👁"` | `Icons.VISIBLE` | 可见 |
| 👁‍🗨 | `"👁‍🗨"` | `Icons.INVISIBLE` | 隐藏 |
| 📊 | `"📊"` | `Icons.STATS` | 统计 |
| 📈 | `"📈"` | `Icons.CHART` | 图表 |
| 📍 | `"📍"` | `Icons.PIN` | 位置 |
| ℹ | `"ℹ"` | `Icons.INFO` | 信息 |
| 💡 | `"💡"` | `Icons.LIGHTBULB` | 提示 |
| ❓ | `"❓"` | `Icons.HELP` | 帮助 |

### 状态指示器类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| ✓ | `"✓"` | `Icons.CHECK` | 对勾 |
| ✅ | `"✅"` | `Icons.CHECK_CIRCLE` | 勾选框 |
| ⚠️ | `"⚠️"` | `Icons.WARNING` | 警告 |
| ❌ | `"❌"` | `Icons.ERROR` | 错误 |
| ● | `"●"` | `Icons.DOT` | 圆点 |
| 🔒 | `"🔒"` | `Icons.LOCK` | 锁定 |
| 🔓 | `"🔓"` | `Icons.UNLOCK` | 解锁 |

### 界面元素类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| 🪟 | `"🪟"` | `Icons.WINDOW` | 窗口 |
| 🔑 | `"🔑"` | `Icons.KEYFRAME` | 关键帧 |
| 🎨 | `"🎨"` | `Icons.PALETTE` | 调色板 |
| 🎯 | `"🎯"` | `Icons.TARGET` | 靶心 |
| 🌟 | `"🌟"` | `Icons.STAR` | 星星 |
| 🛡️ | `"🛡️"` | `Icons.SHIELD` | 盾牌 |
| ⌨ | `"⌨"` | `Icons.KEYBOARD` | 键盘 |

### 箭头类

| Emoji | 旧代码 | 新代码 | 说明 |
|-------|--------|--------|------|
| ⬆️ | `"⬆️"` | `Icons.ARROW_UP` | 上箭头 |
| ⬇️ | `"⬇️"` | `Icons.ARROW_DOWN` | 下箭头 |
| ⬅️ | `"⬅️"` | `Icons.ARROW_LEFT` | 左箭头 |
| ➡️ | `"➡️"` | `Icons.ARROW_RIGHT` | 右箭头 |

---

## 💡 迁移示例

### 示例 1: ToolbarPanel - 播放控制

**迁移前：**
```java
private void renderPlaybackControls() {
    PlaybackController pc = PlaybackController.get();
    UITheme.label("▶", UITheme.DarkTheme.TEXT_SELECTED);
    ImGui.sameLine();

    if (pc.isPlaying()) {
        if (ImGui.button("⏸ 暂停##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
            pc.pause();
    } else {
        if (ImGui.button("▶ 播放##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
            pc.play();
    }
    ImGui.sameLine();

    if (ImGui.button("⏹ 停止##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
        pc.stop();
    ImGui.sameLine();

    if (ImGui.checkbox("🔁 循环##loop", isLoopEnabled)) {
        isLoopEnabled = !isLoopEnabled;
        pc.toggleLoop();
    }
}
```

**迁移后：**
```java
import com.chronoblocks.ui.theme.Icons;  // 添加导入

private void renderPlaybackControls() {
    PlaybackController pc = PlaybackController.get();
    UITheme.label(Icons.PLAY, UITheme.DarkTheme.TEXT_SELECTED);  // ✨ 使用图标常量
    ImGui.sameLine();

    if (pc.isPlaying()) {
        if (ImGui.button(Icons.PAUSE + " 暂停##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
            pc.pause();
    } else {
        if (ImGui.button(Icons.PLAY + " 播放##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
            pc.play();
    }
    ImGui.sameLine();

    if (ImGui.button(Icons.STOP + " 停止##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) 
        pc.stop();
    ImGui.sameLine();

    if (ImGui.checkbox(Icons.LOOP + " 循环##loop", isLoopEnabled)) {
        isLoopEnabled = !isLoopEnabled;
        pc.toggleLoop();
    }
}
```

### 示例 2: MenuBarPanel - 文件菜单

**迁移前：**
```java
private void renderFileMenu() {
    if (ImGui.beginMenu("📁 File")) {
        if (ImGui.menuItem("New Animation", "Ctrl+N")) {
            handleNewAnimation();
        }
        // ...
        ImGui.endMenu();
    }
}
```

**迁移后：**
```java
import com.chronoblocks.ui.theme.Icons;  // 添加导入

private void renderFileMenu() {
    if (ImGui.beginMenu(Icons.FOLDER + " File")) {  // ✨ 使用图标常量
        if (ImGui.menuItem("New Animation", "Ctrl+N")) {
            handleNewAnimation();
        }
        // ...
        ImGui.endMenu();
    }
}
```

### 示例 3: ContainerPanel - 工具栏

**迁移前：**
```java
private void renderToolbar() {
    if (ImGui.button("➕ 新建容器")) {
        createNewContainer();
    }
    
    ImGui.sameLine();
    if (ImGui.button("🧱 从世界采集")) {
        collectBlocksFromWorld();
    }
    
    ImGui.sameLine();
    if (ImGui.button("🗑 删除选中")) {
        deleteSelectedContainer();
    }
}
```

**迁移后：**
```java
import com.chronoblocks.ui.theme.Icons;  // 添加导入

private void renderToolbar() {
    if (ImGui.button(Icons.ADD + " 新建容器")) {  // ✨ 使用图标常量
        createNewContainer();
    }
    
    ImGui.sameLine();
    if (ImGui.button(Icons.BLOCK + " 从世界采集")) {  // ✨
        collectBlocksFromWorld();
    }
    
    ImGui.sameLine();
    if (ImGui.button(Icons.DELETE + " 删除选中")) {  // ✨
        deleteSelectedContainer();
    }
}
```

### 示例 4: 仅图标按钮（无文字）

**迁移前：**
```java
if (ImGui.button("💾")) {
    handleSave();
}
if (ImGui.isItemHovered()) {
    ImGui.setTooltip("保存项目");
}
```

**迁移后：**
```java
if (ImGui.button(Icons.SAVE)) {  // ✨ 使用图标常量
    handleSave();
}
if (ImGui.isItemHovered()) {
    ImGui.setTooltip("保存项目");
}
```

---

## 🔧 批量替换命令

使用 IDE 的查找替换功能（支持正则表达式）：

### IntelliJ IDEA / VS Code

1. 打开查找替换 (Ctrl+Shift+R)
2. 启用正则表达式模式
3. 使用以下模式逐个替换：

```
查找: "➕"
替换为: Icons.ADD

查找: "🗑"
替换为: Icons.DELETE

查找: "📋"
替换为: Icons.COPY

// ... 依此类推
```

---

## ✅ 迁移检查清单

完成每个面板的迁移后，请检查：

- [ ] 已添加 `import com.chronoblocks.ui.theme.Icons;`
- [ ] 所有 Emoji 字符串已替换为对应的图标常量
- [ ] 代码编译通过，无错误
- [ ] 运行游戏，UI 中图标显示正常
- [ ] 悬停提示正常工作
- [ ] 按钮点击交互正常

---

## 🐛 故障排除

### 问题 1: 图标显示为方框 □

**原因**: 图标字体未正确加载

**解决方案**:
1. 检查 `icomoon.ttf` 是否在正确的路径：`src/main/resources/assets/chronoblocks/fonts/`
2. 检查日志，确认字体加载成功：`[ChronoBlocks] 自定义图标字体加载成功`
3. 重新编译项目

### 问题 2: 图标太小或太大

**原因**: 字体大小不匹配

**解决方案**:
调整 `ImGuiFontManager.java` 中的 `DEFAULT_FONT_SIZE` 常量

### 问题 3: 某些图标不显示

**原因**: 图标 Unicode 码位不在加载范围内

**解决方案**:
检查 `style.css` 中的 Unicode 码位，确保在 `U+E000 - U+F8FF` 范围内

---

## 📊 迁移进度跟踪

| 面板 | 图标数量 | 迁移状态 | 测试状态 |
|-----|---------|---------|---------|
| MenuBarPanel | 12+ | ⏳ 待迁移 | ⏳ 待测试 |
| ToolbarPanel | 5+ | ⏳ 待迁移 | ⏳ 待测试 |
| TimelinePanel | 6+ | ⏳ 待迁移 | ⏳ 待测试 |
| PropertiesPanel | 18+ | ⏳ 待迁移 | ⏳ 待测试 |
| KeyframePropertiesPanel | 10+ | ⏳ 待迁移 | ⏳ 待测试 |
| ContainerPanel | 12+ | ⏳ 待迁移 | ⏳ 待测试 |
| ContainerPropertiesPanel | - | ⏳ 待迁移 | ⏳ 待测试 |
| FontTestPanel | 40+ | ⏳ 待迁移 | ⏳ 待测试 |

**图例**: ⏳ 待处理 | ✅ 已完成 | ❌ 有问题

---

## 🎯 最佳实践

1. **使用常量而非硬编码**: 始终使用 `Icons.XXX` 而不是 `"\uE900"`
2. **添加注释**: 在复杂的图标组合中添加注释说明
3. **保持一致性**: 在整个项目中使用相同的图标表示相同的功能
4. **测试兼容性**: 迁移后测试所有交互功能
5. **更新文档**: 更新相关文档和注释

---

## 📝 更新日志

| 日期 | 版本 | 变更内容 |
|-----|------|---------|
| 2025-10-18 | v1.0 | 初始版本：创建迁移指南，完成字体加载和 Icons 类 |

---

**完成迁移后，请更新本文档并标记为"已完成"状态！** ✅

