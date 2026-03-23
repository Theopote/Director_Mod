# 全局快捷键迁移 TODO：Panel → UIShortcutCoordinator

**原则**：凡是「全局含义」的快捷键，只能存在于 UIShortcutCoordinator（经 ShortcutDispatcher）。  
Panel 内仅保留**局部**快捷键（如 Timeline 内 Scrub、InlineRenamer 输入等）。

---

## 已迁移到 ShortcutDispatcher ✅

| 快捷键 | 动作 | 说明 |
|--------|------|------|
| Space / Ctrl+Space | 播放/暂停 | 已迁移 |
| Ctrl+Z | 撤销 | 已迁移 |
| Ctrl+Y | 重做 | 已迁移 |
| Delete | 删除选中 | 已迁移 |
| 工具切换 (W/E/R 等) | 已迁移 |

---

## 待迁移（当前仍在 Panel 内）

### 1. ContainerPanel — `handleKeyboardShortcuts()`

| 按键 | 当前行为 | 目标 |
|------|----------|------|
| **P** | 折叠/展开属性面板 | 可选：保留为**局部**（仅当容器树焦点时），或迁到 ShortcutCoordinator 作为「View」类快捷键 |
| **Delete** | 删除选中容器（弹确认） | 迁到 ShortcutCoordinator，通过 Facade 或专用命令；需上下文：仅当容器树焦点时 |
| **Ctrl+Z** | 容器命令历史 Undo | 与 Timeline Undo 不同域；可保留**局部**（容器焦点时），或统一为「上下文 Undo」由 ShortcutCoordinator 根据焦点路由 |
| **Ctrl+Y** | 容器命令历史 Redo | 同上 |

**建议**：Delete 迁到 ShortcutCoordinator，按「当前焦点」分支到 Timeline 删除或 Container 删除。Ctrl+Z / Ctrl+Y 可先保留局部，后续若做「全局 Undo 栈」再统一。

### 2. SelectionHistoryPanel — `handleKeyboardShortcuts()`

| 按键 | 当前行为 | 目标 |
|------|----------|------|
| **Ctrl+Z** | 选择历史 Undo | 与 Timeline、Container 不同域；建议保留**局部**（仅当本面板焦点时） |
| **Ctrl+Y** | 选择历史 Redo | 同上 |

**建议**：保留局部；若焦点在本面板，优先由 Panel 消费，避免与 Timeline Undo 冲突。若未来做「编辑/播放模式」键切换，可在此统一。

---

## 明确保留在 Panel 内（局部）✅

| 场景 | 说明 |
|------|------|
| Timeline 内 Scrub、缩放、拖放 | 纯时间线交互，局部 |
| InlineRenamer 文本输入 | 局部 |
| 各种 Combo/InputText 聚焦时 | 不拦截全局快捷键 |

---

## 实施顺序建议

1. **ContainerPanel Delete**：迁到 ShortcutBindings + ShortcutActions，增加上下文判断（是否容器树焦点）。
2. **ContainerPanel Ctrl+Z / Ctrl+Y**：先保留局部；若与 ShortcutDispatcher 冲突（同一帧两处都触发），再考虑「焦点优先」或统一 Undo 入口。
3. **SelectionHistoryPanel Ctrl+Z / Y**：保留局部，确保 ImGui 焦点在本面板时 ShortcutDispatcher 不消费（已有 `ShortcutContext.shouldHandleShortcut` 等可扩展）。

---

## 引用

- `UIShortcutCoordinator` / `ShortcutDispatcher`：`com.director.ui.input`
- `ShortcutBindings`、`ShortcutActions`、`ShortcutContext`
- 权责边界：`docs` 或 `.cursor` 中的 UI Shell 架构说明
