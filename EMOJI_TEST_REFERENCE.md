# ChronoBlocks Emoji 测试参考

## 常用 Emoji 测试列表

以下是 ChronoBlocks UI 中可能使用的 Emoji，用于测试字体加载是否成功。

### 基础表情符号 (U+1F600 - U+1F64F)
- 😀 😁 😂 🤣 😃 😄 😅 😆 😉 😊
- 😋 😎 😍 😘 😗 😙 😚 🙂 🤗 🤩
- 🤔 🤨 😐 😑 😶 🙄 😏 😣 😥 😮
- 🤐 😯 😪 😫 😴 😌 😛 😜 😝 🤤

### 常用符号 (U+2600 - U+26FF)
- ⭐ ⚡ ☀️ ☁️ ⛅ ⛈️ 🌤️ 🌥️ 🌦️ 🌧️
- ⚙️ ⚠️ ⛔ ⚫ ⚪ ⬛ ⬜ ◼️ ◻️ ◾
- ✅ ❌ ❎ ➕ ➖ ➗ ✖️ ♻️ 🔄 🔃

### 杂项符号和象形文字 (U+1F300 - U+1F5FF)
- 🌀 🌁 🌂 🌃 🌄 🌅 🌆 🌇 🌈 🌉
- 🔥 💧 🌊 ⛰️ 🏔️ 🗻 🏕️ 🏖️ 🏜️ 🏝️
- 📁 📂 🗂️ 📅 📆 🗓️ 📇 📈 📉 📊
- 📋 📌 📍 📎 🔗 📐 📏 📑 🔖 🏷️

### 交通和地图符号 (U+1F680 - U+1F6FF)
- 🚀 🛸 🚁 🛩️ ✈️ 🛫 🛬 🪂 💺 🚂
- 🚃 🚄 🚅 🚆 🚇 🚈 🚉 🚊 🚝 🚞
- 🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐

### 补充符号和象形文字 (U+1F900 - U+1F9FF)
- 🤝 🤞 🤟 🤠 🤡 🤢 🤣 🤤 🤥 🤦
- 🤧 🤨 🤩 🤪 🤫 🤬 🤭 🤮 🤯 🧀
- 🧁 🧂 🧃 🧄 🧅 🧆 🧇 🧈 🧉 🧊

### 工具和开发相关
- 🔧 🔨 🛠️ 🗡️ ⚔️ 🔫 🏹 🛡️ 🔩 ⚙️
- 💻 🖥️ 🖨️ ⌨️ 🖱️ 🖲️ 💾 💿 📀 📱
- 📲 📟 📠 📡 🔋 🔌 💡 🔦 🕯️ 🗑️

### UI 常用图标
- ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁
- 🔂 🔄 🔃 🔼 🔽 ◀️ 🔽 ⏫ ⏬ ➡️
- ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↕️ ↔️ ↪️

## 测试方法

### 1. 在 ImGui 窗口中测试

```java
if (ImGui.begin("Emoji Test")) {
    ImGui.text("基础表情: 😀 😁 😂 🤣");
    ImGui.text("符号: ⭐ ⚡ ☀️ ⚙️");
    ImGui.text("文件: 📁 📂 📊 📈");
    ImGui.text("工具: 🔧 🔨 🛠️ ⚙️");
    ImGui.text("UI图标: ▶️ ⏸️ ⏹️ 🔄");
}
ImGui.end();
```

### 2. 测试按钮标签

```java
if (ImGui.button("⚙️ 设置")) {
    // 设置按钮
}

if (ImGui.button("📁 打开文件")) {
    // 打开文件按钮
}

if (ImGui.button("💾 保存")) {
    // 保存按钮
}
```

### 3. 测试菜单项

```java
if (ImGui.beginMenu("文件 📁")) {
    if (ImGui.menuItem("新建 ✨")) { }
    if (ImGui.menuItem("打开 📂")) { }
    if (ImGui.menuItem("保存 💾")) { }
    if (ImGui.menuItem("退出 ❌")) { }
    ImGui.endMenu();
}
```

## 预期结果

### ✅ 成功的表现
- 所有 Emoji 都显示为彩色或单色图标
- Emoji 与中文、英文字符混排时对齐正确
- 没有出现方框、问号或其他占位符

### ❌ 失败的表现
- Emoji 显示为问号（?）
- Emoji 显示为方框（□）
- Emoji 位置错位或重叠
- 日志中有字体加载错误

## 日志检查

启动游戏后，检查日志文件中是否有以下输出：

### 成功日志示例
```
[ChronoBlocks-FontManager] 成功加载字体: 中文字体 (/assets/chronoblocks/fonts/NotoSansSC-Regular.ttf) - 123456 字节
[ChronoBlocks-FontManager] 成功加载字体: Emoji 字体 (/assets/chronoblocks/fonts/NotoEmoji-Regular.ttf) - 789012 字节
[ChronoBlocks-FontManager] 字体图集构建完成
[ChronoBlocks] 字体初始化完成（包含中文和 Emoji 支持）
```

### 错误日志示例
```
[ChronoBlocks-FontManager] 找不到字体资源: /assets/chronoblocks/fonts/NotoEmoji-Regular.ttf (Emoji 字体)
[ChronoBlocks-FontManager] Emoji 字体加载失败，emoji 可能无法正常显示
```

## Unicode 范围对照表

| Unicode 范围 | 描述 | 示例 |
|-------------|------|------|
| U+1F300 - U+1F5FF | 杂项符号和象形文字 | 🌀 🔥 💧 📁 |
| U+1F600 - U+1F64F | 表情符号 | 😀 😁 😂 🤣 |
| U+1F680 - U+1F6FF | 交通和地图符号 | 🚀 🚁 ✈️ 🚂 |
| U+1F700 - U+1F77F | 炼金术符号 | 🜀 🜁 🜂 🜃 |
| U+1F900 - U+1F9FF | 补充符号和象形文字 | 🤝 🤞 🤟 🧀 |
| U+2600 - U+26FF | 杂项符号 | ⭐ ⚡ ☀️ ⚙️ |
| U+2700 - U+27BF | 装饰符号 | ✂️ ✏️ ✒️ ✔️ |
| U+2B50 - U+2B55 | 星星等 | ⭐ ⭕ |

## 常见问题

### Q: 为什么有些 Emoji 是黑白的？
A: NotoEmoji-Regular.ttf 提供的是单色 Emoji。如果需要彩色 Emoji，需要使用 NotoColorEmoji.ttf，但这需要 ImGui 支持彩色字体（需要额外配置）。

### Q: 为什么 Emoji 大小和文字不一致？
A: 可以在 `ImGuiFontManager.java` 中调整 `EMOJI_FONT_SIZE` 常量来匹配文字大小。

### Q: 可以添加自定义 Emoji 吗？
A: 可以，只需要在 `emojiGlyphRanges` 中添加相应的 Unicode 范围，并确保字体文件支持这些字符。

## 性能监控

### 字体图集大小
- 建议字体图集总大小不超过 16MB
- 可以通过减少 Unicode 范围来优化大小
- 使用 `io.getFonts().getTexDataAsRGBA32()` 可以获取图集尺寸

### 内存使用
- 每个字形大约占用 20-100 字节
- 当前配置大约包含 3000-5000 个字形
- 总内存占用约 2-5 MB

## 进阶配置

### 自定义 Emoji 范围
如果只需要特定的 Emoji，可以精简范围：

```java
// 只加载基础表情符号
final short[] emojiGlyphRanges = new short[]{
    (short) 0x1F600, (short) 0x1F64F,  // 仅表情符号
    0,
};
```

### 多个 Emoji 字体
可以合并多个 Emoji 字体来获得更好的覆盖：

```java
// 第一个 Emoji 字体
loadFontFromResources(fontAtlas, "NotoEmoji-Regular.ttf", 20.0f, emojiConfig1, ranges1, "Emoji 1");

// 第二个 Emoji 字体（继续合并模式）
loadFontFromResources(fontAtlas, "TwemojiMozilla.ttf", 20.0f, emojiConfig2, ranges2, "Emoji 2");
```

---

**最后更新**: 2024年
**测试平台**: Windows 10/11, Minecraft 1.21.4
**ImGui 版本**: 1.86+

