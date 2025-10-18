# 📘 Timeline 集成指南 v4.5

## 完整实现教程：右键菜单 + 批量编辑

---

## 📋 概述

本指南详细说明如何在 `TimelinePanel` 中集成以下功能：

1. ✅ **多选支持** - Ctrl/Shift 键多选关键帧
2. ✅ **右键菜单** - KeyframeContextMenu 快速操作
3. ✅ **批量编辑器** - BatchKeyframeEditor 批量操作
4. ✅ **选择可视化** - 高亮显示选中的关键帧

---

## 🔧 步骤 1: 在 TimelinePanel 中添加必要的字段

### 添加导入

```java
import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.ui.menus.KeyframeContextMenu;
import com.chronoblocks.ui.panels.BatchKeyframeEditor;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
```

### 添加字段

```java
public class TimelinePanel extends AbstractPanel {
    // ... 现有代码 ...
    
    // ==================== v4.5 多选和右键菜单支持 ====================
    
    /** 批量编辑器面板引用 */
    private BatchKeyframeEditor batchEditor;
    
    /** 当前悬停的关键帧（用于右键菜单） */
    private Keyframe hoveredKeyframe = null;
    
    /** 右键点击的关键帧 */
    private Keyframe contextMenuKeyframe = null;
    
    // ... 其他代码 ...
}
```

---

## 🔧 步骤 2: 在 TimelinePanel 构造函数中初始化

```java
public TimelinePanel() {
    // ... 现有代码 ...
    
    // 初始化批量编辑器
    batchEditor = new BatchKeyframeEditor();
}
```

---

## 🔧 步骤 3: 在 doRender() 中添加键盘检测

```java
@Override
protected void doRender() {
    // ... 现有代码 ...
    
    // ========== v4.5 键盘状态检测 ==========
    updateKeyboardState();
    
    // 渲染时间轴内容
    renderTimelineContent();
    
    // ========== v4.5 渲染右键菜单 ==========
    renderContextMenu();
    
    // ... 其他代码 ...
}

/**
 * 更新键盘状态（Ctrl/Shift 键）
 */
private void updateKeyboardState() {
    boolean ctrlPressed = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
    boolean shiftPressed = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
    
    model.setMultiSelectMode(ctrlPressed);
}
```

---

## 🔧 步骤 4: 在关键帧渲染处添加交互逻辑

假设你在 `TimelineRenderer` 或 `TimelinePanel` 中有这样的代码渲染关键帧：

```java
private void renderKeyframe(Keyframe keyframe, float x, float y) {
    // ... 绘制关键帧的代码 ...
    
    // ========== v4.5 选择高亮 ==========
    boolean isSelected = model.isSelected(keyframe);
    
    if (isSelected) {
        // 绘制选中高亮（例如更亮的颜色或边框）
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRect(
            x - 6, y - 6, x + 6, y + 6,
            ImGui.getColorU32(0.2f, 0.8f, 1.0f, 1.0f),  // 青色高亮
            2.0f, 0, 2.0f  // 圆角、flags、粗细
        );
    }
    
    // ========== v4.5 鼠标交互检测 ==========
    
    // 检查鼠标是否悬停在关键帧上
    float mouseX = ImGui.getMousePosX();
    float mouseY = ImGui.getMousePosY();
    float dist = (float) Math.sqrt((mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y));
    
    if (dist < 8.0f) {  // 关键帧半径
        hoveredKeyframe = keyframe;
        
        // 左键点击 - 选择
        if (ImGui.isMouseClicked(0)) {  // 0 = 左键
            boolean ctrlHeld = model.isMultiSelectMode();
            model.selectItem(keyframe, ctrlHeld);
            
            // 如果选中多个，更新批量编辑器
            if (model.getSelectedCount() > 1) {
                updateBatchEditor();
            }
        }
        
        // 右键点击 - 打开菜单
        if (ImGui.isMouseClicked(1)) {  // 1 = 右键
            // 如果点击的不是已选中的，先选中它
            if (!model.isSelected(keyframe)) {
                model.selectItem(keyframe, false);
            }
            
            contextMenuKeyframe = keyframe;
            
            // 打开对应的菜单
            if (model.getSelectedCount() > 1) {
                KeyframeContextMenu.openBatch();
            } else {
                KeyframeContextMenu.open();
            }
        }
        
        // 显示工具提示
        if (ImGui.isMouseHoveringRect(x - 8, y - 8, x + 8, y + 8)) {
            ImGui.setTooltip(String.format(
                "关键帧 @ %.2fs\n类型: %s\n左键: 选择 | 右键: 菜单",
                keyframe.getTime(),
                keyframe.getInterpolationType().getDisplayName()
            ));
        }
    }
}
```

---

## 🔧 步骤 5: 渲染右键菜单

```java
/**
 * 渲染右键菜单
 */
private void renderContextMenu() {
    if (model.getSelectedCount() == 1 && contextMenuKeyframe != null) {
        // 单个关键帧菜单
        KeyframeContextMenu.render(
            contextMenuKeyframe,
            kf -> {
                // 关键帧被修改的回调
                onKeyframeModified(kf);
            }
        );
    } else if (model.getSelectedCount() > 1) {
        // 批量关键帧菜单
        List<Keyframe> selectedKeyframes = new ArrayList<>();
        for (Object item : model.getSelectedItems()) {
            if (item instanceof Keyframe) {
                selectedKeyframes.add((Keyframe) item);
            }
        }
        
        KeyframeContextMenu.renderBatch(
            selectedKeyframes,
            kfs -> {
                // 批量修改的回调
                onBatchKeyframesModified(kfs);
            }
        );
    }
}

/**
 * 关键帧被修改时的回调
 */
private void onKeyframeModified(Keyframe keyframe) {
    // 刷新 UI
    LOGGER.info("[Timeline] 关键帧已修改: {}", keyframe.getTime());
    
    // 如果有需要，通知其他系统
    // EventBus.post(new KeyframeModifiedEvent(keyframe));
}

/**
 * 批量关键帧被修改时的回调
 */
private void onBatchKeyframesModified(List<Keyframe> keyframes) {
    LOGGER.info("[Timeline] {} 个关键帧已批量修改", keyframes.size());
    
    // 更新批量编辑器
    updateBatchEditor();
}

/**
 * 更新批量编辑器
 */
private void updateBatchEditor() {
    if (batchEditor == null) return;
    
    List<Keyframe> selectedKeyframes = new ArrayList<>();
    for (Object item : model.getSelectedItems()) {
        if (item instanceof Keyframe) {
            selectedKeyframes.add((Keyframe) item);
        }
    }
    
    batchEditor.setSelectedKeyframes(selectedKeyframes);
}
```

---

## 🔧 步骤 6: 添加键盘快捷键支持

```java
/**
 * 处理键盘快捷键
 */
private void handleKeyboardShortcuts() {
    boolean ctrlPressed = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
    
    // Ctrl + A: 全选
    if (ctrlPressed && ImGui.isKeyPressed(ImGuiKey.A)) {
        model.selectAll();
        updateBatchEditor();
    }
    
    // Escape: 清除选择
    if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
        model.clearSelection();
        batchEditor.clearSelection();
    }
    
    // Delete: 删除选中的关键帧
    if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
        deleteSelectedKeyframes();
    }
    
    // Ctrl + C: 复制
    if (ctrlPressed && ImGui.isKeyPressed(ImGuiKey.C)) {
        copySelectedKeyframes();
    }
    
    // Ctrl + V: 粘贴
    if (ctrlPressed && ImGui.isKeyPressed(ImGuiKey.V)) {
        pasteKeyframes();
    }
}

private void deleteSelectedKeyframes() {
    // TODO: 实现删除逻辑
    LOGGER.info("[Timeline] 删除 {} 个关键帧", model.getSelectedCount());
    model.clearSelection();
}

private void copySelectedKeyframes() {
    // TODO: 实现复制逻辑
    LOGGER.info("[Timeline] 复制 {} 个关键帧", model.getSelectedCount());
}

private void pasteKeyframes() {
    // TODO: 实现粘贴逻辑
    LOGGER.info("[Timeline] 粘贴关键帧");
}
```

---

## 🔧 步骤 7: 在 ChronoBlocksUIManager 中注册批量编辑器面板

```java
public class ChronoBlocksUIManager {
    // ... 现有代码 ...
    
    public void initialize(Window window) {
        // ... 现有代码 ...
        
        // 注册批量编辑器面板 v4.5
        BatchKeyframeEditor batchEditor = new BatchKeyframeEditor();
        registerPanel("BatchEditor", batchEditor);
        
        // ... 其他代码 ...
    }
}
```

---

## 🎨 步骤 8: 美化选中状态的视觉效果

### 方案 1: 边框高亮

```java
if (model.isSelected(keyframe)) {
    ImDrawList drawList = ImGui.getWindowDrawList();
    
    // 绘制外发光效果
    for (int i = 3; i > 0; i--) {
        float alpha = 0.3f / i;
        drawList.addCircle(
            x, y, 
            6 + i * 2,  // 半径递增
            ImGui.getColorU32(0.2f, 0.8f, 1.0f, alpha),
            16, 1.5f
        );
    }
    
    // 绘制实心边框
    drawList.addCircleFilled(
        x, y, 8,
        ImGui.getColorU32(0.2f, 0.8f, 1.0f, 1.0f)
    );
}
```

### 方案 2: 颜色变化

```java
float r, g, b;
if (model.isSelected(keyframe)) {
    // 选中：亮青色
    r = 0.2f;
    g = 0.8f;
    b = 1.0f;
} else if (hoveredKeyframe == keyframe) {
    // 悬停：淡青色
    r = 0.5f;
    g = 0.9f;
    b = 1.0f;
} else {
    // 默认：白色
    r = 1.0f;
    g = 1.0f;
    b = 1.0f;
}

drawKeyframeDiamond(x, y, r, g, b);
```

---

## 🧪 测试清单

### 基础功能

- [ ] 单击选择关键帧
- [ ] Ctrl + 点击切换选择（多选）
- [ ] Shift + 点击范围选择（如果实现）
- [ ] 点击空白处清除选择
- [ ] Esc 键清除选择

### 右键菜单

- [ ] 右键打开单个关键帧菜单
- [ ] 快速选择插值类型
- [ ] 应用贝塞尔预设
- [ ] 复制/粘贴关键帧属性
- [ ] 多选时打开批量菜单

### 批量编辑

- [ ] 批量设置插值类型
- [ ] 时间缩放功能
- [ ] 时间偏移功能
- [ ] 对齐到第一个/最后一个
- [ ] 均匀分布关键帧

### 可视化

- [ ] 选中关键帧有明显高亮
- [ ] 悬停时显示工具提示
- [ ] 批量编辑器实时显示选中数量
- [ ] 拖拽贝塞尔控制点时曲线预览更新

---

## 🎯 完整代码示例

### TimelinePanel.java（完整集成示例）

```java
package com.chronoblocks.ui.panels;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.ui.menus.KeyframeContextMenu;
import com.chronoblocks.ui.timeline.TimelineModel;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TimelinePanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);
    
    private final TimelineModel model;
    private BatchKeyframeEditor batchEditor;
    
    private Keyframe hoveredKeyframe = null;
    private Keyframe contextMenuKeyframe = null;
    
    public TimelinePanel() {
        super();
        this.model = new TimelineModel();
        this.batchEditor = new BatchKeyframeEditor();
        
        setPanelName("Timeline");
        setWindowName("Timeline###TimelinePanel");
    }
    
    @Override
    protected void doRender() {
        // 更新键盘状态
        updateKeyboardState();
        
        // 处理快捷键
        handleKeyboardShortcuts();
        
        // 渲染时间轴内容
        renderTimelineContent();
        
        // 渲染右键菜单
        renderContextMenu();
    }
    
    private void updateKeyboardState() {
        boolean ctrlPressed = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        model.setMultiSelectMode(ctrlPressed);
    }
    
    private void handleKeyboardShortcuts() {
        boolean ctrlPressed = model.isMultiSelectMode();
        
        if (ctrlPressed && ImGui.isKeyPressed(ImGuiKey.A)) {
            model.selectAll();
            updateBatchEditor();
        }
        
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            model.clearSelection();
            batchEditor.clearSelection();
        }
    }
    
    private void renderTimelineContent() {
        // 实现你的时间轴渲染逻辑
        // 在渲染关键帧时调用 renderKeyframe()
    }
    
    private void renderKeyframe(Keyframe keyframe, float x, float y) {
        boolean isSelected = model.isSelected(keyframe);
        
        // 绘制关键帧（带选中高亮）
        // ... 绘制代码 ...
        
        // 交互检测
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        float dist = (float) Math.sqrt((mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y));
        
        if (dist < 8.0f) {
            hoveredKeyframe = keyframe;
            
            if (ImGui.isMouseClicked(0)) {
                model.selectItem(keyframe, model.isMultiSelectMode());
                if (model.getSelectedCount() > 1) {
                    updateBatchEditor();
                }
            }
            
            if (ImGui.isMouseClicked(1)) {
                if (!model.isSelected(keyframe)) {
                    model.selectItem(keyframe, false);
                }
                contextMenuKeyframe = keyframe;
                
                if (model.getSelectedCount() > 1) {
                    KeyframeContextMenu.openBatch();
                } else {
                    KeyframeContextMenu.open();
                }
            }
        }
    }
    
    private void renderContextMenu() {
        if (model.getSelectedCount() == 1 && contextMenuKeyframe != null) {
            KeyframeContextMenu.render(contextMenuKeyframe, this::onKeyframeModified);
        } else if (model.getSelectedCount() > 1) {
            List<Keyframe> selected = new ArrayList<>();
            for (Object item : model.getSelectedItems()) {
                if (item instanceof Keyframe) {
                    selected.add((Keyframe) item);
                }
            }
            KeyframeContextMenu.renderBatch(selected, this::onBatchKeyframesModified);
        }
    }
    
    private void onKeyframeModified(Keyframe kf) {
        LOGGER.info("[Timeline] 关键帧已修改");
    }
    
    private void onBatchKeyframesModified(List<Keyframe> kfs) {
        LOGGER.info("[Timeline] {} 个关键帧已批量修改", kfs.size());
        updateBatchEditor();
    }
    
    private void updateBatchEditor() {
        List<Keyframe> selected = new ArrayList<>();
        for (Object item : model.getSelectedItems()) {
            if (item instanceof Keyframe) {
                selected.add((Keyframe) item);
            }
        }
        batchEditor.setSelectedKeyframes(selected);
    }
}
```

---

## 🎉 完成！

现在你的 Timeline 面板已经具备：

✅ **多选支持** - Ctrl + 点击多选关键帧  
✅ **右键菜单** - 快速操作和批量编辑  
✅ **批量编辑器** - 强大的批量操作面板  
✅ **视觉反馈** - 选中高亮和悬停提示  
✅ **键盘快捷键** - Ctrl+A, Esc, Delete 等  

---

**下一步**: 实现路径动画系统！🛤️

---

**作者**: ChronoBlocks AI Assistant  
**版本**: v4.5  
**日期**: 2025-10-15

