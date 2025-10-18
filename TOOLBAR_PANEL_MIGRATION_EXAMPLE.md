# ToolbarPanel 迁移示例

> **示例面板**: ToolbarPanel.java  
> **迁移日期**: 2025-10-18

---

## 📝 迁移前代码

```java
package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.theme.UITheme;

public class ToolbarPanel extends AbstractPanel {
    // ... 省略字段声明 ...

    @Override
    protected void doRender() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.NORMAL, UITheme.Spacing.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        
        renderToolButtons();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderPlaybackControls();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderStatusIndicators();
        ImGui.sameLine();
        renderCloseButton();
        
        ImGui.popStyleVar(2);
    }

    /* ---------- 工具选择 ---------- */
    private void renderToolButtons() {
        // 工具图标
        UITheme.label("🛠", UITheme.DarkTheme.TEXT_SELECTED);  // ❌ 使用 Emoji
        ImGui.sameLine();

        for (ToolMode mode : ToolMode.values()) {
            boolean active = (currentTool == mode);
            if (active) {
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }
            if (ImGui.button(mode.getDisplayName() + "##" + mode.name(), 70, UITheme.Spacing.BUTTON_HEIGHT)) {
                setCurrentTool(mode);
            }
            if (active) {
                ImGui.popStyleColor();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(mode.getDisplayName() + " (" + mode.getShortcut() + ")");
            }
            ImGui.sameLine();
        }
    }

    /* ---------- 播放控制 ---------- */
    private void renderPlaybackControls() {
        PlaybackController pc = PlaybackController.get();
        UITheme.label("▶", UITheme.DarkTheme.TEXT_SELECTED);  // ❌ 使用 Emoji
        ImGui.sameLine();

        // Play / Pause - 使用统一按钮高度
        if (pc.isPlaying()) {
            if (ImGui.button("⏸ 暂停##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) pc.pause();  // ❌
        } else {
            if (ImGui.button("▶ 播放##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) pc.play();  // ❌
        }
        ImGui.sameLine();

        // Stop
        if (ImGui.button("⏹ 停止##play", 70, UITheme.Spacing.BUTTON_HEIGHT)) pc.stop();  // ❌
        ImGui.sameLine();

        // Loop toggle
        if (ImGui.checkbox("🔁 循环##loop", isLoopEnabled)) {  // ❌
            isLoopEnabled = !isLoopEnabled;
            pc.toggleLoop();
        }
    }

    /* ---------- 状态显示 ---------- */
    private void renderStatusIndicators() {
        AnimationState state = AnimationState.get();
        float time = state.getCurrentTime();
        float duration = state.getClip() != null ? state.getClip().computeDuration() : 10.0f;
        float fps = ImGui.getIO().getFramerate();

        // 使用主题文本颜色
        UITheme.label(String.format("⏱ %.2f / %.2fs", time, duration), UITheme.DarkTheme.TEXT);  // ❌
        ImGui.sameLine();
        UITheme.label("🧰 " + currentTool.getDisplayName(), UITheme.DarkTheme.TEXT);  // ❌
        ImGui.sameLine();
        UITheme.label(String.format("FPS: %.0f", fps), UITheme.DarkTheme.TEXT);
        
        // 渲染开关 v4.5
        ImGui.sameLine();
        renderRenderingToggle();
    }
    
    /* ---------- 渲染开关 v4.5 ---------- */
    private void renderRenderingToggle() {
        var renderer = com.chronoblocks.client.renderer.ContainerRenderer.get();
        boolean enabled = renderer.isEnabled();
        
        // 使用主题颜色的可视化开关
        if (enabled) {
            float[] activeColor = UITheme.toFloatArrayPublic(rgba(51, 204, 51, 255));
            float[] hoverColor = UITheme.toFloatArrayPublic(rgba(77, 255, 77, 255));
            ImGui.pushStyleColor(ImGuiCol.Button, activeColor[0], activeColor[1], activeColor[2], activeColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        } else {
            float[] inactiveColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON);
            float[] hoverColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON_HOVERED);
            ImGui.pushStyleColor(ImGuiCol.Button, inactiveColor[0], inactiveColor[1], inactiveColor[2], inactiveColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        }
        
        String label = enabled ? "👁 可见" : "👁 隐藏";  // ❌ 使用 Emoji
        if (ImGui.button(label + "##renderToggle", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            renderer.setEnabled(!enabled);
            logger.info("[ChronoBlocks] 容器渲染已{}", enabled ? "禁用" : "启用");
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(enabled ? "点击隐藏容器边界框" : "点击显示容器边界框");
        }
        
        ImGui.popStyleColor(2);
        
        // 渲染选项弹出菜单（右键点击按钮打开）
        if (enabled && ImGui.beginPopupContextItem("renderOptions")) {
            UITheme.title("📐 渲染选项");  // ❌ 使用 Emoji
            
            // ...省略渲染选项代码...
            
            ImGui.endPopup();
        }
    }
}
```

---

## ✅ 迁移后代码

```java
package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;  // ✅ 添加导入

public class ToolbarPanel extends AbstractPanel {
    // ... 省略字段声明 ...

    @Override
    protected void doRender() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.NORMAL, UITheme.Spacing.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        
        renderToolButtons();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderPlaybackControls();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderStatusIndicators();
        ImGui.sameLine();
        renderCloseButton();
        
        ImGui.popStyleVar(2);
    }

    /* ---------- 工具选择 ---------- */
    private void renderToolButtons() {
        // 工具图标
        UITheme.label(Icons.TOOLS, UITheme.DarkTheme.TEXT_SELECTED);  // ✅ 使用图标常量
        ImGui.sameLine();

        for (ToolMode mode : ToolMode.values()) {
            boolean active = (currentTool == mode);
            if (active) {
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }
            if (ImGui.button(mode.getDisplayName() + "##" + mode.name(), 70, UITheme.Spacing.BUTTON_HEIGHT)) {
                setCurrentTool(mode);
            }
            if (active) {
                ImGui.popStyleColor();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(mode.getDisplayName() + " (" + mode.getShortcut() + ")");
            }
            ImGui.sameLine();
        }
    }

    /* ---------- 播放控制 ---------- */
    private void renderPlaybackControls() {
        PlaybackController pc = PlaybackController.get();
        UITheme.label(Icons.PLAY, UITheme.DarkTheme.TEXT_SELECTED);  // ✅ 使用图标常量
        ImGui.sameLine();

        // Play / Pause - 使用统一按钮高度
        if (pc.isPlaying()) {
            if (ImGui.button(Icons.PAUSE + " 暂停##play", 70, UITheme.Spacing.BUTTON_HEIGHT))  // ✅
                pc.pause();
        } else {
            if (ImGui.button(Icons.PLAY + " 播放##play", 70, UITheme.Spacing.BUTTON_HEIGHT))  // ✅
                pc.play();
        }
        ImGui.sameLine();

        // Stop
        if (ImGui.button(Icons.STOP + " 停止##play", 70, UITheme.Spacing.BUTTON_HEIGHT))  // ✅
            pc.stop();
        ImGui.sameLine();

        // Loop toggle
        if (ImGui.checkbox(Icons.LOOP + " 循环##loop", isLoopEnabled)) {  // ✅
            isLoopEnabled = !isLoopEnabled;
            pc.toggleLoop();
        }
    }

    /* ---------- 状态显示 ---------- */
    private void renderStatusIndicators() {
        AnimationState state = AnimationState.get();
        float time = state.getCurrentTime();
        float duration = state.getClip() != null ? state.getClip().computeDuration() : 10.0f;
        float fps = ImGui.getIO().getFramerate();

        // 使用主题文本颜色
        UITheme.label(String.format(Icons.TIME + " %.2f / %.2fs", time, duration), UITheme.DarkTheme.TEXT);  // ✅
        ImGui.sameLine();
        UITheme.label(Icons.TOOLBOX + " " + currentTool.getDisplayName(), UITheme.DarkTheme.TEXT);  // ✅
        ImGui.sameLine();
        UITheme.label(String.format("FPS: %.0f", fps), UITheme.DarkTheme.TEXT);
        
        // 渲染开关 v4.5
        ImGui.sameLine();
        renderRenderingToggle();
    }
    
    /* ---------- 渲染开关 v4.5 ---------- */
    private void renderRenderingToggle() {
        var renderer = com.chronoblocks.client.renderer.ContainerRenderer.get();
        boolean enabled = renderer.isEnabled();
        
        // 使用主题颜色的可视化开关
        if (enabled) {
            float[] activeColor = UITheme.toFloatArrayPublic(rgba(51, 204, 51, 255));
            float[] hoverColor = UITheme.toFloatArrayPublic(rgba(77, 255, 77, 255));
            ImGui.pushStyleColor(ImGuiCol.Button, activeColor[0], activeColor[1], activeColor[2], activeColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        } else {
            float[] inactiveColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON);
            float[] hoverColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON_HOVERED);
            ImGui.pushStyleColor(ImGuiCol.Button, inactiveColor[0], inactiveColor[1], inactiveColor[2], inactiveColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        }
        
        String label = enabled ? (Icons.VISIBLE + " 可见") : (Icons.INVISIBLE + " 隐藏");  // ✅ 使用图标常量
        if (ImGui.button(label + "##renderToggle", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            renderer.setEnabled(!enabled);
            logger.info("[ChronoBlocks] 容器渲染已{}", enabled ? "禁用" : "启用");
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(enabled ? "点击隐藏容器边界框" : "点击显示容器边界框");
        }
        
        ImGui.popStyleColor(2);
        
        // 渲染选项弹出菜单（右键点击按钮打开）
        if (enabled && ImGui.beginPopupContextItem("renderOptions")) {
            UITheme.title(Icons.RULER + " 渲染选项");  // ✅ 使用图标常量
            
            // ...省略渲染选项代码...
            
            ImGui.endPopup();
        }
    }
}
```

---

## 📊 改动统计

| 类型 | 数量 |
|-----|-----|
| 添加导入 | 1 |
| 替换图标 | 9 |
| 总改动行数 | 10 |

---

## ✅ 迁移检查清单

- [x] 添加 `import com.chronoblocks.ui.theme.Icons;`
- [x] `🛠` → `Icons.TOOLS`
- [x] `▶` → `Icons.PLAY`
- [x] `⏸` → `Icons.PAUSE`
- [x] `⏹` → `Icons.STOP`
- [x] `🔁` → `Icons.LOOP`
- [x] `⏱` → `Icons.TIME`
- [x] `🧰` → `Icons.TOOLBOX`
- [x] `👁` → `Icons.VISIBLE` / `Icons.INVISIBLE`
- [x] `📐` → `Icons.RULER`

---

## 🧪 测试结果

| 测试项 | 结果 |
|-------|-----|
| 编译通过 | ✅ |
| 工具按钮显示正常 | ✅ |
| 播放控制按钮显示正常 | ✅ |
| 状态指示器显示正常 | ✅ |
| 渲染开关显示正常 | ✅ |
| 按钮点击交互正常 | ✅ |
| 悬停提示正常 | ✅ |

---

## 📝 注意事项

1. **图标大小**: 图标字体使用与主字体相同的大小（20.0f），确保视觉一致性
2. **字符串拼接**: 使用 `Icons.XXX + " 文字"` 的方式组合图标和文字
3. **条件表达式**: 在三元运算符中使用括号确保正确的字符串拼接顺序
4. **保持ID**: 保持 ImGui 的 ID 标记（如 `##play`、`##loop`）不变

---

**迁移完成！** ✅

