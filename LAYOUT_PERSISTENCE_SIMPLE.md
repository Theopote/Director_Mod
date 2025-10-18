# ChronoBlocks 布局持久化 - 简化方案

## 🎯 实现方案

使用 **ImGui 原生的 INI 文件机制**，简单可靠。

## 📁 关键修改

### 1. 启用 ImGui INI 文件 ✅
**文件**: `src/main/java/com/chronoblocks/ui/imgui/ImGuiRenderer.java` (第86-88行)

```java
// 启用 ImGui 配置文件保存，用于持久化布局
String iniPath = "config/chronoblocks/imgui.ini";
io.setIniFilename(iniPath);
```

**效果**：
- ImGui 自动将布局保存到 `config/chronoblocks/imgui.ini`
- 自动加载保存的布局
- 完全不需要手动管理

### 2. 首次打开时应用默认布局 ✅
**文件**: `src/main/java/com/chronoblocks/ui/ChronoBlocksUIManager.java` (第560-575行)

```java
// 应用默认布局（仅在首次打开且无保存的布局时）
if (dockspaceId != -1 && isFirstLayout) {
    // 检查是否存在保存的布局文件
    Path iniPath = Paths.get("config/chronoblocks/imgui.ini");
    boolean hasCustomLayout = Files.exists(iniPath);
    
    if (!hasCustomLayout) {
        // 没有自定义布局，应用默认布局
        DockSpaceLayoutBuilder.buildDefaultLayout(dockspaceId);
    } else {
        LOGGER.info("[ChronoBlocks] 已加载保存的布局");
    }
    
    isFirstLayout = false;
}
```

### 3. 重置布局功能 ✅
**文件**: `src/main/java/com/chronoblocks/ui/ChronoBlocksUIManager.java` (第609-626行)

```java
public void resetToDefaultLayout() {
    // 删除 ImGui 的 INI 文件
    Path iniPath = Paths.get("config/chronoblocks/imgui.ini");
    if (Files.exists(iniPath)) {
        Files.delete(iniPath);
    }
    
    // 重置布局状态，下次打开时会重新应用默认布局
    DockSpaceLayoutBuilder.resetLayoutState();
    isFirstLayout = true;
}
```

## 🎮 使用方法

### 自动保存（默认）
1. **打开ChronoBlocks窗口**
2. **调整布局**（拖动面板、调整大小等）
3. **关闭窗口** - ImGui自动保存
4. **重新打开** - 自动恢复上次的布局 ✅

### 手动保存
- **菜单**: Window → Save Current Layout
- **效果**: 立即保存当前布局到 `imgui.ini`

### 重置布局
- **菜单**: Window → Restore Default Layout
- **效果**: 
  1. 删除 `imgui.ini` 文件
  2. 下次打开时应用默认布局

## 📂 布局文件

### 文件位置
```
config/chronoblocks/imgui.ini
```

### 文件内容示例
```ini
[Window][DockSpace]
Pos=0,19
Size=1920,1061
Collapsed=0

[Window][Container###ContainerPanel]
Pos=0,107
Size=288,754
Collapsed=0
DockId=0x00000005,0

[Window][Properties###PropertiesPanel]
Pos=1536,107
Size=384,754
Collapsed=0
DockId=0x00000006,0

...
```

## ✨ 优势

### 与之前方案对比
| 特性 | 之前的方案 | 当前方案 |
|------|-----------|----------|
| **保存方式** | 手动管理JSON | ImGui原生INI ✅ |
| **代码复杂度** | 高（需要序列化） | 低（ImGui自动） ✅ |
| **可靠性** | 容易出错 | 稳定可靠 ✅ |
| **与DockSpace兼容** | 可能冲突 ❌ | 完美兼容 ✅ |
| **维护成本** | 高 | 低 ✅ |

### 技术优势
- ✅ **零手动管理**：ImGui 自动保存/加载
- ✅ **完美兼容**：与 DockSpace 完全兼容
- ✅ **简单可靠**：使用成熟的 ImGui 机制
- ✅ **无状态冲突**：不会破坏 DockSpace 结构
- ✅ **性能优秀**：ImGui 优化的保存机制

## 🧪 测试步骤

### 基本功能测试
1. **删除现有配置**（如果存在）：
   ```
   删除 config/chronoblocks/imgui.ini
   ```

2. **首次打开**：
   - 启动游戏
   - 打开ChronoBlocks窗口
   - 应该看到默认布局（4个面板 + 中央透明区域）

3. **调整布局**：
   - 拖动 Container 面板到其他位置
   - 调整 Timeline 面板的高度
   - 拖动分隔线改变面板宽度

4. **关闭并重新打开**：
   - 关闭ChronoBlocks窗口
   - 重新打开
   - **验证**：布局应该与调整后的完全一致 ✅

5. **检查配置文件**：
   - 查看 `config/chronoblocks/imgui.ini`
   - 应该包含面板位置和大小信息

6. **测试重置**：
   - 点击 Window → Restore Default Layout
   - 关闭并重新打开窗口
   - **验证**：布局恢复为默认状态 ✅

### 边界情况测试
1. **删除INI文件**：
   - 手动删除 `imgui.ini`
   - 打开窗口
   - 应该自动应用默认布局

2. **损坏的INI文件**：
   - 修改 `imgui.ini` 为无效内容
   - 打开窗口
   - ImGui 应该自动修复或使用默认布局

3. **多次打开/关闭**：
   - 连续打开关闭多次
   - 布局应该始终保持一致

## 🔧 故障排除

### 布局没有保存？
**检查**：
1. `config/chronoblocks/` 目录是否存在
2. `imgui.ini` 文件是否生成
3. 日志中是否有错误

**解决**：
1. 手动创建 `config/chronoblocks/` 目录
2. 检查文件权限
3. 使用 Window → Save Current Layout 手动保存

### 布局保存但没有恢复？
**检查**：
1. `imgui.ini` 文件是否存在
2. 日志中是否显示 "已加载保存的布局"

**解决**：
1. 点击 Window → Restore Default Layout 重置
2. 删除 `imgui.ini` 重新开始

### 面板脱离DockSpace？
**原因**：不应该发生，因为我们使用ImGui原生机制

**解决**：
1. 重置布局：Window → Restore Default Layout
2. 删除 `imgui.ini` 文件

## 📝 注意事项

### ImGui INI 文件
- ✅ **自动管理**：ImGui会自动保存，无需手动调用
- ✅ **性能优秀**：ImGui只在必要时保存
- ✅ **兼容性好**：与DockSpace完美兼容
- ⚠️ **人类可读性差**：INI格式不如JSON直观，但更可靠

### 开发注意
- 修改默认布局后，建议删除用户的 `imgui.ini` 文件
- 面板的窗口名称（如 `"Container###ContainerPanel"`）必须保持一致
- DockSpace ID 必须保持不变

## 🚀 未来增强

可选的增强功能：
1. **布局预设**：内置多套布局方案（紧凑、宽屏等）
2. **一键备份**：导出/导入布局配置
3. **布局版本管理**：支持多个布局配置切换

---

**版本**: 1.0 (简化方案)  
**日期**: 2025-10-13  
**状态**: ✅ 完整实现，推荐使用

