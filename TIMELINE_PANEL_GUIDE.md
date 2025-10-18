# ChronoBlocks 时间线面板 - 完整功能指南

> 🎬 专业级时间轴面板，类似 After Effects / Blender / Flashback

## ✨ 核心功能列表

### 1️⃣ 视图控制

| 功能 | 操作方式 | 说明 |
|------|----------|------|
| **水平缩放** | 鼠标滚轮 | 在时间线内容区域滚动，以鼠标位置为中心缩放 |
| **缩放倍率** | 拖动滑块 | 20-800 px/s 可调，实时显示当前缩放值 |
| **水平滚动** | 鼠标中键拖拽 | 按住中键拖动可平移时间线视图 |
| **水平滚轮** | 横向滚轮 | 如果鼠标支持，可使用横向滚轮快速滚动 |

### 2️⃣ 播放控制

| 按钮 | 功能 | 快捷键建议 |
|------|------|-----------|
| `\|<` | 跳转到开始 (0s) | Home |
| `播放/暂停` | 切换播放状态 | Space |
| `>\|` | 跳转到结束 | End |
| **时间显示** | 当前时间 / 总时长 | - |

**播放特性**：
- ✅ 自动使用 `ImGui.getIO().getDeltaTime()` 推进时间
- ✅ 播放到结尾自动暂停
- ✅ 播放时播放头（红线）实时移动

### 3️⃣ 播放头 (Playhead) 交互

| 操作 | 效果 |
|------|------|
| **点击标尺区域** | 立即跳转到点击位置的时间 |
| **拖动播放头三角** | 拖动三角形手柄可精确调整时间 |
| **Ctrl + 拖动** | 启用吸附时，拖动会自动对齐到刻度 |

**视觉效果**：
- 🔴 红色竖线穿过所有轨道
- 🔺 顶部红色三角形指示器

### 4️⃣ 片段 (Clip) 管理

#### 创建片段
1. **从 ContainerPanel 拖放**
   - 在 ContainerPanel 中开始拖拽物品
   - 拖到时间线面板上
   - 在鼠标位置创建新片段（默认 1 秒长度）

2. **集成代码示例**（ContainerPanel）
```java
if (ImGui.beginDragDropSource()) {
    byte[] data = itemName.getBytes(StandardCharsets.UTF_8);
    ImGui.setDragDropPayload(TimelinePanel.DND_PAYLOAD_ITEM, data);
    ImGui.text("拖放: " + itemName);
    ImGui.endDragDropSource();
}
```

#### 拖动片段
| 操作 | 效果 |
|------|------|
| **点击片段** | 选中片段（黄色边框高亮） |
| **拖动片段** | 移动整个片段到新位置 |
| **Ctrl + 拖动** | 启用吸附，自动对齐到刻度 |

#### 右键菜单
- ✅ **删除** - 移除片段
- ✅ **设置时长为 1s** - 快速设置时长
- ✅ **设置时长为 2s** - 快速设置时长
- ✅ **清除所有关键帧** - 移除片段的所有关键帧

### 5️⃣ 关键帧系统

| 操作 | 效果 |
|------|------|
| **双击片段** | 在鼠标位置添加关键帧 |
| **关键帧显示** | 黄色竖线 + 黄色圆点 |
| **右键菜单** | "清除所有关键帧" |

**关键帧用途**：
- 🎯 标记动画关键时刻
- 🎨 可视化重要时间点
- 📊 后续可扩展为属性关键帧

### 6️⃣ 吸附系统

| 设置 | 说明 |
|------|------|
| **吸附开关** | 复选框：`吸附 (Ctrl 切换)` |
| **吸附间隔** | 默认 0.25 秒（可配置） |
| **激活方式** | 按住 `Ctrl` 键时拖动自动吸附 |

**吸附效果**：
- ✅ 拖动片段时吸附到时间刻度
- ✅ 拖动播放头时吸附到刻度
- ✅ 创建片段时吸附到整秒

### 7️⃣ 时间刻度标尺

**智能刻度间隔**：
| 缩放级别 | 刻度间隔 |
|----------|----------|
| ≥ 200 px/s | 0.25 秒 |
| ≥ 100 px/s | 0.5 秒 |
| ≥ 50 px/s | 1.0 秒 |
| ≥ 20 px/s | 5.0 秒 |
| < 20 px/s | 10.0 秒 |

**特性**：
- ✅ 自动根据缩放调整刻度密度
- ✅ 显示时间标签（如 "0.00s", "1.00s"）
- ✅ 仅渲染可见区域的刻度（性能优化）

---

## 🎨 视觉设计

### 颜色方案
- **背景**: 深灰色 (30, 30, 30)
- **轨道**: 中灰色 (45, 45, 45)
- **标尺**: 稍亮灰色 (40, 40, 40)
- **播放头**: 红色 (255, 80, 80)
- **关键帧**: 金黄色 (255, 220, 64)
- **选中边框**: 亮黄色 (255, 255, 80)

### 布局参数
```java
canvasHeight = 220f;      // 画布总高度
trackHeight = 28f;        // 单个轨道高度
trackGap = 6f;            // 轨道间距
rulerHeight = 22f;        // 标尺高度
leftLabelWidth = 140f;    // 左侧标签宽度
```

---

## 🔧 技术实现

### 核心架构

```
TimelinePanel
├── 播放控制栏 (renderControlBar)
│   ├── 播放/暂停按钮
│   ├── 时间显示
│   ├── 吸附开关
│   └── 缩放控制
│
└── 时间线画布 (renderTimelineCanvas)
    ├── 左侧轨道标签区
    ├── 时间刻度标尺 (renderRuler)
    ├── 轨道列表
    │   └── 片段渲染 (renderClip)
    │       ├── 片段矩形
    │       ├── 关键帧点
    │       └── 交互处理 (handleClipInteraction)
    ├── 播放头 (renderPlayhead)
    └── 鼠标输入处理 (handleMouseInput)
        ├── 滚轮缩放
        ├── 中键平移
        └── 拖放接收
```

### 数据结构

```java
TimelineTrack {
    String name;               // 轨道名称
    List<TimelineClip> clips;  // 片段列表
}

TimelineClip {
    String label;              // 片段标签
    float startTime;           // 开始时间（秒）
    float endTime;             // 结束时间（秒）
    int color;                 // RGBA 颜色
    List<Float> keyframes;     // 关键帧时间点列表
}
```

### 状态管理

```java
// 时间轴参数
duration = 20.0f;              // 总时长
pixelsPerSecond = 80.0f;       // 缩放倍率
scrollX = 0f;                  // 水平滚动偏移

// 播放状态
playing = false;
currentTime = 0f;

// 吸附设置
snapEnabled = true;
snapInterval = 0.25f;

// 交互状态
draggingClip = null;           // 正在拖动的片段
draggingPlayhead = false;      // 是否拖动播放头
selectedClip = null;           // 当前选中的片段
```

---

## 🚀 高级功能建议（后续扩展）

### 优先级：高 🔥

1. **调整片段长度**
   - 拖动片段左右边缘
   - 实时预览新长度
   - 最小长度限制

2. **多选支持**
   - Shift + 点击多选片段
   - 框选多个片段
   - 批量移动/删除

3. **复制粘贴**
   - Ctrl+C 复制选中片段
   - Ctrl+V 粘贴到播放头位置
   - 重复粘贴支持

### 优先级：中 ⭐

4. **吸附增强**
   - 吸附到其他片段边缘
   - 吸附到关键帧
   - 磁吸效果提示

5. **轨道管理**
   - 添加/删除轨道
   - 重命名轨道
   - 轨道折叠/展开
   - 轨道锁定（禁止编辑）

6. **时间线标记**
   - 添加时间标记点
   - 标记描述文本
   - 快速跳转到标记

### 优先级：低 💡

7. **撤销/重做**
   - 操作历史栈
   - Ctrl+Z / Ctrl+Y
   - 历史记录面板

8. **性能优化**
   - 虚拟化渲染（仅渲染可见轨道）
   - 大量片段时的裁剪优化
   - 异步加载关键帧数据

9. **导出功能**
   - 导出时间线数据为 JSON
   - 导入已保存的时间线
   - 批量导出片段信息

---

## 📝 使用示例

### 基本工作流程

1. **启动游戏** → 打开 Timeline 面板
2. **查看演示数据**：
   - 轨道 1："方块动画" - 包含 "移动" 和 "旋转" 片段
   - 轨道 2："粒子特效" - 包含 "烟雾" 片段
3. **播放测试**：点击 `播放` 按钮，观察播放头移动
4. **拖动片段**：点击并拖动 "移动" 片段到新位置
5. **添加关键帧**：双击 "旋转" 片段添加关键帧
6. **调整缩放**：滚动鼠标滚轮放大时间线
7. **启用吸附**：勾选 "吸附" 复选框，按住 Ctrl 拖动片段

### ContainerPanel 集成示例

```java
// 在 ContainerPanel.java 中添加拖放支持

public void renderItem(String itemName, String itemId) {
    ImGui.text(itemName);
    
    // 开始拖放源
    if (ImGui.beginDragDropSource()) {
        // 将物品 ID 或名称作为 payload
        byte[] data = itemId.getBytes(StandardCharsets.UTF_8);
        ImGui.setDragDropPayload(TimelinePanel.DND_PAYLOAD_ITEM, data);
        
        // 拖动时显示的预览
        ImGui.text("拖放: " + itemName);
        
        ImGui.endDragDropSource();
    }
}
```

---

## 🐛 已知问题和注意事项

### ImGui API 兼容性

不同版本的 ImGui Java 绑定可能有细微差异，主要注意：

1. **Checkbox API**
   - ✅ 当前使用：`ImGui.checkbox(String, boolean)` 返回新状态
   - ⚠️ 某些版本可能需要：`boolean[] ref = {value}`

2. **DragDrop Payload**
   - ✅ 当前实现：支持 `byte[]` 和 `String` 两种类型
   - ⚠️ 根据你的 ImGui 版本调整 `payload` 解析逻辑

3. **鼠标按钮常量**
   - ✅ 使用：`ImGuiMouseButton.Left/Middle/Right`
   - ⚠️ 某些版本可能使用：`0, 1, 2`

### 性能考虑

- **当前实现**：适合中小规模项目（< 50 轨道，< 500 片段）
- **优化建议**：
  - 大量轨道时实现虚拟滚动
  - 片段过多时添加可见性裁剪
  - 关键帧数据考虑延迟加载

---

## 📦 完整功能清单

| 功能 | 状态 | 版本 |
|------|------|------|
| 水平缩放（滚轮） | ✅ 完成 | v3.0 |
| 水平滚动（中键） | ✅ 完成 | v3.0 |
| 从 ContainerPanel 拖放 | ✅ 完成 | v3.0 |
| 拖动片段移动 | ✅ 完成 | v3.0 |
| 秒格吸附 | ✅ 完成 | v3.0 |
| 关键帧显示 | ✅ 完成 | v3.0 |
| 双击添加关键帧 | ✅ 完成 | v3.0 |
| 播放/暂停控制 | ✅ 完成 | v3.0 |
| 可拖拽播放头 | ✅ 完成 | v3.0 |
| 片段选中高亮 | ✅ 完成 | v3.0 |
| 右键上下文菜单 | ✅ 完成 | v3.0 |
| 智能时间刻度 | ✅ 完成 | v3.0 |
| 调整片段长度 | ⏳ 计划中 | v3.1 |
| 多选支持 | ⏳ 计划中 | v3.2 |
| 复制粘贴 | ⏳ 计划中 | v3.2 |

---

## 🎓 学习资源

### 代码位置
- **主文件**: `src/main/java/com/chronoblocks/ui/panels/TimelinePanel.java`
- **集成示例**: 本文档 "ContainerPanel 集成示例" 部分

### 关键方法
- `renderTimelineCanvas()` - 主渲染逻辑
- `handleClipInteraction()` - 片段交互处理
- `handleMouseInput()` - 缩放和滚动
- `renderPlayhead()` - 播放头渲染和拖动

### 扩展开发指南
1. **添加新的片段属性**：修改 `TimelineClip` 类
2. **自定义渲染样式**：调整 `renderClip()` 方法
3. **增加交互方式**：扩展 `handleClipInteraction()`
4. **集成更多面板**：使用 `DND_PAYLOAD_ITEM` 常量

---

**版本**: 3.0 | **作者**: ChronoBlocks Team | **更新日期**: 2025-10-14

🎬 享受专业级时间线编辑体验！

