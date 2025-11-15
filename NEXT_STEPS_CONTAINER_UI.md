# 容器系统UI完善 - 下一步指导

## 📋 当前状态

**已完成**:
- ✅ 核心数据结构和逻辑层(步骤1-5)
- ✅ ContainerPanel基础框架已存在

**待完成**:
- 🚧 ContainerPanel UI扩展(步骤6)
- 🚧 命令系统(步骤7)
- 🚧 剔除工具(步骤8)

---

## 🎯 步骤6: ContainerPanel UI完善

### 需要修改的文件
`src/main/java/com/chronoblocks/ui/panels/ContainerPanel.java`

### 具体修改内容

#### 1. 在`renderToolbar()`方法中添加新按钮组

在`renderCreationButtons()`调用后添加`renderContainerControlButtons()`:

```java
private void renderToolbar() {
    renderCreationButtons();
    renderContainerControlButtons();  // 新增：容器控制按钮
    renderSelectionButtons();
    renderViewAndCollectionControls();
    renderHistoryControls();
    renderSelectionInfo();
}
```

#### 2. 添加`renderContainerControlButtons()`方法

```java
/**
 * 渲染容器控制按钮（v5.0新增）
 */
private void renderContainerControlButtons() {
    Container activeContainer = containerManager.getActive();
    boolean hasActive = activeContainer != null;
    
    // 收纳选区按钮
    ImGui.sameLine();
    if (!hasActive) ImGui.beginDisabled();
    if (ImGui.button(Icons.DOWNLOAD + " 收纳选区", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
        intakeCurrentSelection();
    }
    if (!hasActive) ImGui.endDisabled();
    if (ImGui.isItemHovered() && hasActive) {
        ImGui.setTooltip("将当前选区添加到激活容器 (需要先激活容器)");
    }
    
    // 显示/隐藏按钮
    ImGui.sameLine();
    if (!hasActive) ImGui.beginDisabled();
    String visibilityText = hasActive && activeContainer.isHidden() 
        ? Icons.EYE + " 显示" 
        : Icons.EYE_SLASH + " 隐藏";
    if (ImGui.button(visibilityText, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
        toggleContainerVisibility();
    }
    if (!hasActive) ImGui.endDisabled();
    if (ImGui.isItemHovered() && hasActive) {
        String tooltip = activeContainer.isHidden() 
            ? "显示容器中的方块"
            : "隐藏容器中的方块";
        ImGui.setTooltip(tooltip);
    }
    
    // 实时接收按钮
    ImGui.sameLine();
    if (!hasActive) ImGui.beginDisabled();
    String intakeText = hasActive && activeContainer.isIntakeEnabled()
        ? Icons.PAUSE + " 停止接收"
        : Icons.PLAY + " 实时接收";
    if (ImGui.button(intakeText, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
        toggleIntakeMode();
    }
    if (!hasActive) ImGui.endDisabled();
    if (ImGui.isItemHovered() && hasActive) {
        String tooltip = activeContainer.isIntakeEnabled()
            ? "停止实时接收选中的方块"
            : "开启实时接收模式：每次选中方块时自动收纳";
        ImGui.setTooltip(tooltip);
    }
    
    // 释放按钮
    ImGui.sameLine();
    if (!hasActive) ImGui.beginDisabled();
    if (ImGui.button(Icons.TRASH + " 释放", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
        releaseActiveContainer();
    }
    if (!hasActive) ImGui.endDisabled();
    if (ImGui.isItemHovered() && hasActive) {
        ImGui.setTooltip("删除容器并恢复方块显示");
    }
}
```

#### 3. 添加操作方法实现

```java
/**
 * 收纳当前选区到激活容器
 */
private void intakeCurrentSelection() {
    ContainerIntakeSubscriber.getInstance().intakeCurrentSelection();
}

/**
 * 切换容器可见性
 */
private void toggleContainerVisibility() {
    Container active = containerManager.getActive();
    if (active == null) {
        return;
    }
    
    ContainerState newState = active.getState().toggle();
    active.setState(newState);
    
    // 同步HiddenBlockRegistry
    HiddenBlockRegistry.get().setContainerHidden(
        active.getId(),
        newState.isHidden(),
        active::getBlocks
    );
    
    LOGGER.info("[ContainerPanel] 容器 {} 状态切换为: {}", 
        active.getName(), newState.getDisplayName());
}

/**
 * 切换实时接收模式
 */
private void toggleIntakeMode() {
    Container active = containerManager.getActive();
    if (active == null) {
        return;
    }
    
    boolean newState = !active.isIntakeEnabled();
    active.setIntakeEnabled(newState);
    
    LOGGER.info("[ContainerPanel] 容器 {} 实时接收: {}", 
        active.getName(), newState ? "开启" : "关闭");
}

/**
 * 释放激活容器
 */
private void releaseActiveContainer() {
    Container active = containerManager.getActive();
    if (active == null) {
        return;
    }
    
    // 打开确认对话框
    contextMenuContainer = active;
    ImGui.openPopup("ConfirmRelease");
}
```

#### 4. 添加释放确认对话框

在`renderConfirmationDialogs()`方法中添加:

```java
private void renderConfirmationDialogs() {
    renderDeletionConfirmDialog();
    renderItemDeletionConfirmDialog();
    renderClearAllConfirmDialog();
    renderMergeContainersDialog();
    renderReleaseConfirmDialog();  // 新增
    renderInformationalDialogs();
}

/**
 * 渲染释放容器确认对话框
 */
private void renderReleaseConfirmDialog() {
    ImVec2 center = ImGui.getMainViewport().getCenter();
    ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

    if (ImGui.beginPopupModal("ConfirmRelease", ImGuiWindowFlags.AlwaysAutoResize)) {
        if (contextMenuContainer != null) {
            ImGui.text("释放容器 \"" + contextMenuContainer.getName() + "\" ?");
            ImGui.separator();
            ImGui.textWrapped("释放后容器将被删除，方块将重新显示在世界中。");
            ImGui.spacing();

            if (ImGui.button("确定释放", 120, 0)) {
                containerManager.release(contextMenuContainer.getId());
                containerManager.setActive((UUID)null); // 取消激活
                contextMenuContainer = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
        }
        ImGui.endPopup();
    }
}
```

#### 5. 修改容器列表项显示状态图标

在`renderContainerNode()`方法中的label构建部分:

```java
String label = String.format("%s %s %s###%s", 
    container.getType().getIcon(),
    getContainerStateIcon(container),  // 新增：状态图标
    container.getName(),
    container.getId());

// ... 添加辅助方法 ...

/**
 * 获取容器状态图标
 */
private String getContainerStateIcon(Container container) {
    StringBuilder icons = new StringBuilder();
    
    // 隐藏/显示图标
    if (container.isHidden()) {
        icons.append("🚫");
    } else {
        icons.append("👁️");
    }
    
    // 实时接收图标
    if (container.isIntakeEnabled()) {
        icons.append("📥");
    }
    
    // 激活图标
    if (containerManager.isActive(container)) {
        icons.append("⭐");
    }
    
    return icons.toString();
}
```

#### 6. 在右键菜单中添加容器操作选项

在`renderItemContextMenu()`方法中添加(在"锁定/解锁"之后):

```java
ImGui.separator();

// 激活/取消激活
boolean isActive = containerManager.isActive(contextMenuContainer);
if (ImGui.menuItem(isActive ? "取消激活" : "激活", "A")) {
    if (isActive) {
        containerManager.setActive((UUID)null);
    } else {
        containerManager.setActive(contextMenuContainer);
    }
}

// 实时接收开关
boolean intakeEnabled = contextMenuContainer.isIntakeEnabled();
if (ImGui.menuItem(intakeEnabled ? "停止实时接收" : "开启实时接收", "I")) {
    contextMenuContainer.setIntakeEnabled(!intakeEnabled);
}

// 显示/隐藏
boolean hidden = contextMenuContainer.isHidden();
if (ImGui.menuItem(hidden ? "显示方块" : "隐藏方块", "V")) {
    toggleContainerVisibilityFor(contextMenuContainer);
}

// 释放容器
if (ImGui.menuItem("释放容器", "R")) {
    ImGui.openPopup("ConfirmRelease");
}
```

#### 7. 添加必要的import

在文件顶部添加:

```java
import com.chronoblocks.core.container.ContainerIntakeSubscriber;
import com.chronoblocks.core.container.HiddenBlockRegistry;
import com.chronoblocks.core.container.ContainerState;
```

---

## 🔧 测试要点

完成上述修改后，测试以下功能：

1. **激活容器**：
   - 右键容器 → 激活
   - 容器名称前应显示⭐图标
   - 工具栏按钮应变为可用状态

2. **收纳选区**：
   - 激活容器后，使用选择工具选中方块
   - 点击"收纳选区"按钮
   - 方块应添加到容器中

3. **显示/隐藏**：
   - 点击"隐藏"按钮
   - 方块应在世界中消失
   - 容器图标变为🚫
   - 点击"显示"恢复

4. **实时接收模式**：
   - 激活容器并点击"实时接收"
   - 使用点选工具点击方块
   - 每点击一个，方块应立即消失（被收纳）

5. **释放容器**：
   - 点击"释放"按钮
   - 确认对话框出现
   - 确认后容器删除，方块恢复显示

6. **容器边界呼吸边框**：
   - 激活容器且状态为"显示"时
   - 在3D世界中应看到彩虹呼吸边框

---

## 📦 步骤7 & 8 预览

### 步骤7: 命令系统

创建以下命令类以支持Undo/Redo：

```
src/main/java/com/chronoblocks/core/container/commands/
├── ToggleContainerStateCommand.java
├── ToggleIntakeCommand.java
├── AddBlocksToContainerCommand.java
└── RemoveBlocksFromContainerCommand.java
```

### 步骤8: 剔除工具

创建文件：
- `src/main/java/com/chronoblocks/core/selection/tools/ContainerCullTool.java`

功能要点：
- 注册为选择工具
- 只选择当前激活容器内的方块
- 红色半透明预览
- 右键确认，ESC取消

---

## 🎉 完成后效果

用户将能够：
1. 使用"生成选区"快速创建容器并隐藏方块
2. 使用"实时接收"模式逐个点击方块并隐藏（剥洋葱式操作）
3. 随时切换容器的显示/隐藏状态
4. 合并多个容器
5. 释放容器恢复方块
6. 使用剔除工具移除容器中不需要的方块
7. 拖动容器到时间线制作动画

---

*下一步: 按照上述指导完善ContainerPanel UI*

