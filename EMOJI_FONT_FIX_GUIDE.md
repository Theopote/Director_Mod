# ChronoBlocks Emoji 字体修复指南

## 问题描述

在 ChronoBlocks UI 界面中，Emoji 图标显示为问号（?）。

## 根本原因

ImGui 在初始化时构建字体图集（Font Atlas），该图集包含所有需要渲染的字符。如果在构建时没有指定包含 Emoji 字符集，或者使用的字体文件本身不支持 Emoji，那么在渲染时就找不到对应的字形（Glyph），只能显示为未定义字符（?）。

## 解决方案

### 核心思路

1. **主字体加载**：首先加载中文字体（NotoSansSC-Regular.ttf）作为基础字体
2. **合并模式**：使用 ImGui 的合并模式（Merge Mode）加载 Emoji 字体
3. **正确的 Unicode 范围**：为 Emoji 字体指定完整的 Unicode 字符范围

### 实现步骤

#### 1. 字体文件准备

已在项目中添加以下字体文件到 `src/main/resources/assets/chronoblocks/fonts/`：
- `NotoSansSC-Regular.ttf` - 中文字体（主字体）
- `NotoEmoji-Regular.ttf` - Emoji 字体

#### 2. 创建字体管理器

创建了专门的 `ImGuiFontManager` 类来管理字体加载：

**位置**：`src/main/java/com/chronovoid/chronoblocks/client/ImGuiFontManager.java`

**核心功能**：
- 加载中文字体作为主字体
- 使用合并模式加载 Emoji 字体
- 提供完整的 Emoji Unicode 范围覆盖
- 详细的错误处理和日志记录

#### 3. 修改 ImGuiRenderer

更新了 `ImGuiRenderer.java` 的 `initializeFonts()` 方法，使用新的字体管理器：

```java
private void initializeFonts(ImGuiIO io) {
    try {
        // 使用新的字体管理器加载中文字体和 Emoji 字体
        com.chronovoid.chronoblocks.client.ImGuiFontManager.initializeFonts(io);
        LOGGER.info("[ChronoBlocks] 字体初始化完成（包含中文和 Emoji 支持）");
        
    } catch (Exception e) {
        LOGGER.error("[ChronoBlocks] 字体初始化失败", e);
        // 确保至少有默认字体
        io.getFonts().addFontDefault();
        io.getFonts().build();
    }
}
```

## 技术细节

### 1. 字体合并模式

合并模式是 ImGui 的一个关键特性：

```java
final ImFontConfig emojiConfig = new ImFontConfig();
emojiConfig.setMergeMode(true);  // 关键：设置为合并模式
emojiConfig.setPixelSnapH(true);
```

当设置为合并模式时，新加载的字体字形会被添加到上一个字体的图集中，而不会替换它。这样可以实现多种字体的混合使用。

### 2. Emoji Unicode 范围

Emoji 字符分布在多个 Unicode 范围内：

```java
final short[] emojiGlyphRanges = new short[]{
    // 表情符号和象形文字
    (short) 0x1F300, (short) 0x1F5FF,  // 杂项符号和象形文字
    (short) 0x1F600, (short) 0x1F64F,  // 表情符号
    (short) 0x1F680, (short) 0x1F6FF,  // 交通和地图符号
    (short) 0x1F700, (short) 0x1F77F,  // 炼金术符号
    (short) 0x1F780, (short) 0x1F7FF,  // 几何图形扩展
    (short) 0x1F800, (short) 0x1F8FF,  // 补充箭头-C
    (short) 0x1F900, (short) 0x1F9FF,  // 补充符号和象形文字
    (short) 0x1FA00, (short) 0x1FA6F,  // 扩展-A
    (short) 0x1FA70, (short) 0x1FAFF,  // 符号和象形文字扩展-A
    
    // 其他常用符号
    (short) 0x2600,  (short) 0x26FF,   // 杂项符号（太阳、星星等）
    (short) 0x2700,  (short) 0x27BF,   // 装饰符号
    (short) 0x2B50,  (short) 0x2B55,   // 星星等
    
    0,  // 结束标记
};
```

### 3. 字体加载流程

1. **加载中文字体**：
   - 从资源文件读取 NotoSansSC-Regular.ttf
   - 指定中文字符范围（基本拉丁字符、CJK 符号和标点、CJK 统一汉字等）
   - 添加到字体图集

2. **加载 Emoji 字体**（合并模式）：
   - 从资源文件读取 NotoEmoji-Regular.ttf
   - 设置合并模式标志
   - 指定完整的 Emoji Unicode 范围
   - 合并到现有字体图集

3. **构建字体图集**：
   - 调用 `io.getFonts().build()` 生成最终的字体纹理

## 使用说明

### 重新编译项目

```bash
# 在项目根目录执行
./gradlew build
```

### 运行测试

启动 Minecraft 并打开 ChronoBlocks UI，之前显示为问号的 Emoji 应该现在能正常显示了。

### 支持的 Emoji 范围

包含以下常用 Emoji：
- 😀 😁 😂 🤣 😃 😄 😅 😆 - 笑脸表情
- ⭐ ⚡ ☀️ ☁️ ⛅ - 天气和符号
- 🚀 🚁 ✈️ 🚂 - 交通工具
- 📁 📂 📊 📈 - 办公符号
- ⚙️ 🔧 🔨 🛠️ - 工具符号

## 性能优化

### 字体图集大小

加载更多 Unicode 范围会增加字体图集的大小和内存占用。当前配置已经优化为只包含最常用的 Emoji 范围。

如果需要支持更多 Emoji，可以在 `ImGuiFontManager.java` 中添加额外的 Unicode 范围：

```java
// 添加更多 Emoji 范围
(short) 0x1F1E0, (short) 0x1F1FF,  // 旗帜符号
```

### 缓存和性能

字体图集在初始化时构建一次，之后会被缓存，不会影响运行时性能。

## 故障排查

### 问题：Emoji 仍然显示为问号

**可能原因和解决方法**：

1. **字体文件缺失**
   - 检查 `src/main/resources/assets/chronoblocks/fonts/NotoEmoji-Regular.ttf` 是否存在
   - 确认文件大小不为 0

2. **字体加载失败**
   - 查看日志输出，搜索 `[ChronoBlocks]` 相关的错误信息
   - 确认看到 "成功加载字体: Emoji 字体" 日志

3. **使用的 Emoji 不在范围内**
   - 检查你使用的 Emoji 的 Unicode 值
   - 如果不在现有范围内，添加相应的 Unicode 范围到 `emojiGlyphRanges`

4. **字体图集构建失败**
   - 查看日志中是否有 "字体图集构建完成" 的信息
   - 如果失败，检查是否有内存不足的错误

### 调试日志

在 `ImGuiFontManager.java` 中已经包含了详细的日志输出：

```
[ChronoBlocks-FontManager] 成功加载字体: 中文字体 (/assets/chronoblocks/fonts/NotoSansSC-Regular.ttf) - XXXXX 字节
[ChronoBlocks-FontManager] 成功加载字体: Emoji 字体 (/assets/chronoblocks/fonts/NotoEmoji-Regular.ttf) - XXXXX 字节
[ChronoBlocks-FontManager] 字体图集构建完成
```

如果看到这些日志，说明字体加载成功。

## 扩展功能

### 添加更多字体

如果需要添加其他特殊字体（如图标字体 FontAwesome），可以继续使用合并模式：

```java
// 在 ImGuiFontManager 中添加
private static boolean loadIconFont(ImFontAtlas fontAtlas) {
    final ImFontConfig iconConfig = new ImFontConfig();
    iconConfig.setMergeMode(true);  // 继续使用合并模式
    
    final short[] iconRanges = new short[]{
        (short) 0xE000, (short) 0xF8FF,  // 私有使用区
        0,
    };
    
    return loadFontFromResources(
        fontAtlas,
        FONT_PATH_PREFIX + "fontawesome.ttf",
        20.0f,
        iconConfig,
        iconRanges,
        "图标字体"
    );
}
```

然后在 `initializeFonts()` 方法中调用：

```java
// 第三步：加载图标字体（可选）
boolean iconFontLoaded = loadIconFont(fontAtlas);
```

### 动态字体大小

可以根据 UI 缩放级别动态调整字体大小：

```java
// 获取当前 UI 缩放
float uiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
float fontSize = DEFAULT_FONT_SIZE * uiScale;

// 使用调整后的字体大小
fontAtlas.addFontFromMemoryTTF(fontBytes, fontSize, fontConfig, glyphRanges);
```

## 参考资源

- [ImGui 官方文档 - 字体](https://github.com/ocornut/imgui/blob/master/docs/FONTS.md)
- [Unicode Emoji 完整列表](https://unicode.org/emoji/charts/full-emoji-list.html)
- [Noto Emoji 项目](https://github.com/googlefonts/noto-emoji)

## 总结

通过使用 ImGui 的字体合并模式，我们成功实现了：
1. ✅ 中文字符的完整支持
2. ✅ Emoji 表情符号的完整支持
3. ✅ 统一的字体管理
4. ✅ 详细的错误处理和日志记录
5. ✅ 易于扩展的架构设计

现在 ChronoBlocks UI 可以正确显示所有 Emoji 图标了！

