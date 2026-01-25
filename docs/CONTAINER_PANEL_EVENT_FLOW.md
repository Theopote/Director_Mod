# Container 面板事件流时序图

**用途**：本文档是 Container 面板相关 bug 的「判官」——所有行为应对齐此事件流与三条铁律。

**架构对齐**：Panel / Interaction / Selection / Command / Model

---

## 1. 选中（mouse → selection，无拖拽语义）

```
┌──────────┐
│  Mouse   │
└────┬─────┘
     │ mouseDown(x,y)
     ▼
┌────────────────────────────┐
│ ContainerTreeInteraction   │
│ Handler                    │
└────┬───────────────────────┘
     │ hitTest(x,y)
     ▼
┌────────────────────────────┐
│ VisibleContainerNodeList   │   ← Renderer & Interaction 共用
│ (flattenVisibleTree)       │
└────┬───────────────────────┘
     │ nodeId
     ▼
┌────────────────────────────┐
│ SelectionModel             │
└────┬───────────────────────┘
     │
     │ ① 普通点击（无 modifier）
     │    → selectSingle(nodeId)
     │
     │ ② Ctrl / Cmd + click
     │    → toggle(nodeId)
     │
     │ ③ Shift + click
     │    → selectRange(anchor, nodeId)
     │
     ▼
┌────────────────────────────┐
│ SelectionState Updated     │
│ selected / primary / anchor│
└────┬───────────────────────┘
     │
     │ repaint()
     ▼
┌────────────────────────────┐
│ ContainerTreeRenderer      │
│ (draw highlight)           │
└────────────────────────────┘
```

⬆️ **到这里为止，只是「选中」，没有任何拖拽语义。**

---

## 2. 拖拽开始（关键分水岭）

```
┌──────────┐
│  Mouse   │
└────┬─────┘
     │ mouseDrag(start)
     ▼
┌────────────────────────────┐
│ ContainerTreeInteraction   │
│ Handler                    │
└────┬───────────────────────┘
     │ beginDrag(nodeId)
     ▼
┌────────────────────────────┐
│ SelectionModel             │
│ beginDrag(nodeId)          │
└────┬───────────────────────┘
     │
     │ if nodeId ∉ selected:
     │    selectSingle(nodeId)
     │
     │ dragSet = selected
     ▼
┌────────────────────────────┐
│ DragContext                │
│ (immutable dragSet)        │
└────┬───────────────────────┘
     │
     │ show drag preview
     ▼
┌────────────────────────────┐
│ ContainerTreeRenderer      │
│ (ghost rows / count badge) │
└────────────────────────────┘
```

📌 **这是之前所有问题的「核心缺失点」**  
👉 **dragSet 必须来自 SelectionModel**

---

## 3. 拖拽悬停（hover）

```
┌──────────┐
│  Mouse   │
└────┬─────┘
     │ mouseMove(x,y)
     ▼
┌────────────────────────────┐
│ ContainerTreeInteraction   │
│ Handler                    │
└────┬───────────────────────┘
     │ hitTest drop target
     ▼
┌────────────────────────────┐
│ DropTargetResolver         │
│ (group / index)            │
└────┬───────────────────────┘
     │
     │ validateDrop(dragSet, target)
     ▼
┌────────────────────────────┐
│ DragFeedbackState          │
│ valid / invalid / no-op    │
└────┬───────────────────────┘
     │ repaint()
     ▼
┌────────────────────────────┐
│ ContainerTreeRenderer      │
│ (insert line / red glow)   │
└────────────────────────────┘
```

---

## 4. 拖拽释放 → Command（最重要的一段）

```
┌──────────┐
│  Mouse   │
└────┬─────┘
     │ mouseUp
     ▼
┌────────────────────────────┐
│ ContainerTreeInteraction   │
│ Handler                    │
└────┬───────────────────────┘
     │ if dropValid:
     ▼
┌────────────────────────────┐
│ ContainerCommandService    │
│ moveContainers(...)        │
└────┬───────────────────────┘
     │
     │ build MoveContainersCommand
     │   ├─ compute original indices
     │   ├─ apply index correction
     │   └─ composite command
     ▼
┌────────────────────────────┐
│ CommandStack               │
│ execute + push undo        │
└────┬───────────────────────┘
     │
     ▼
┌────────────────────────────┐
│ ContainerModel / Manager   │
│ (tree mutation)            │
└────┬───────────────────────┘
     │ modelChanged
     ▼
┌────────────────────────────┐
│ ContainerPanelController   │
│ refresh tree               │
└────┬───────────────────────┘
     │
     ▼
┌────────────────────────────┐
│ ContainerTreeRenderer      │
│ redraw                     │
└────────────────────────────┘
```

---

## 5. Undo / Redo（已天然支持）

```
User presses Ctrl+Z
        │
        ▼
┌────────────────────────────┐
│ CommandStack.undo()        │
└────┬───────────────────────┘
     │
     ▼
┌────────────────────────────┐
│ MoveContainersCommand.undo │
│ (reverse order)            │
└────┬───────────────────────┘
     │
     ▼
┌────────────────────────────┐
│ ContainerModel             │
└────┬───────────────────────┘
     │
     ▼
┌────────────────────────────┐
│ Tree refresh + repaint     │
└────────────────────────────┘
```

---

## 6. 这张图怎么用？（判官用法）

遇到任一问题时，先问一句：

> **这件事发生在时序图的哪一层？**  
> **它有没有越权？有没有绕过 Selection / Command？**

| 问题 | 对照 |
|------|------|
| 「为什么点了没反应？」 | hitTest → VisibleNodeList → SelectionModel，是否有一层被跳过或错用？ |
| 「为什么选中了却拖不动？」 | beginDrag 是否从 Selection 取 dragSet？ImGui 的 source 与 dragSet 是否一致？ |
| 「为什么只移动了一个？」 | drop 时是否用 dragSet（来自 Selection），而非「当前 hit 的 node」？ |
| 「为什么 undo 顺序乱了？」 | 是否只通过 MoveContainersCommand / CommandStack 改模型？ |

---

## 7. 三条铁律（直接拿来对照）

| 角色 | 职责 | 不做什么 |
|------|------|----------|
| **Interaction** | 只负责「解释输入」 | 不直接改 Model，不代替 Selection 做「意图」决策 |
| **Selection** | 只负责「表达意图」 | 不执行移动/删除；dragSet、anchor、primary 由其维护 |
| **Command** | **唯一**能改模型的入口 | 不 bypass CommandStack；不直接操作 ContainerManager 的树结构 |

> 只要你守住这三条，这个面板以后怎么扩展都不会塌。

---

## 8. 与当前实现的映射（便于对照）

| 时序图概念 | 当前代码位置 |
|------------|--------------|
| hitTest(x,y) | ImGui 的 `isItemHovered` / `isItemClicked`，每行一个 `treeNodeEx` |
| VisibleContainerNodeList | 未单独抽象；Renderer 逐行 `renderContainerNode`，Interaction 用「当前 item」等效 |
| SelectionModel | `ContainerManager` 的 container selection + `ContainerPanelController.selectContainer` |
| beginDrag(nodeId) / dragSet=selected | `setupDragAndDrop`：`hasMultipleSelection ? selectedContainers : [container]`，payload 与 `lastDragPayloadData` |
| DropTargetResolver | `handleDropPayload`：GROUP → 子节点追加；否则 → 目标前插入 |
| validateDrop / no-op | `ContainerCommandService.moveContainers` + `MoveIndexCorrection.isNoOpWhenDroppingIntoSelectedBlock` |
| moveContainers → MoveContainersCommand | `ContainerCommandService.moveContainers` → `execute(MoveContainersCommand)` |
