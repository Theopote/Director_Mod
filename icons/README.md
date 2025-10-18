# ChronoBlocks SVG 图标集（纯黑色单色版）

> **专为 IcoMoon 字体生成优化**  
> **图标总数**: 100+ 个  
> **格式**: 纯黑色 (#000000) SVG

---

## 🎯 重要说明

**所有图标已转换为纯黑色单色版本**，完全符合 IcoMoon 字体生成要求！

### ✅ 图标规范
- ✅ **纯黑色** (#000000) - 无任何彩色
- ✅ **单色设计** - 无渐变、无多色
- ✅ **24x24 画布** - 统一尺寸
- ✅ **简洁路径** - 优化为单一路径
- ✅ **2px 描边** - 清晰可辨

### ❌ 已移除的问题
- ❌ 彩色填充 → 纯黑色
- ❌ CSS 样式 → 内联属性
- ❌ 复杂效果 → 简化设计
- ❌ hover 状态 → 纯静态SVG

---

## 📂 图标分类（100+ 个）

### 1️⃣ 基础编辑类 (11个)
```
add, edit, delete, copy, cut, paste, duplicate,
undo, redo, search, refresh
```

### 2️⃣ 播放控制类 (10个)
```
play, pause, stop, record, loop, time,
skip-back, skip-forward, fast-forward, rewind
```

### 3️⃣ 文件操作类 (9个)
```
folder, file, save, clipboard, package,
upload, download, import, export
```

### 4️⃣ 容器/对象类 (9个)
```
container, building, area, particle, animation,
block, box, sphere, cylinder
```

### 5️⃣ 工具类 (7个)
```
tools, toolbox, wrench, hammer, settings, ruler, magic
```

### 6️⃣ 绘图工具类 (9个)
```
brush, pen, eraser, color, eyedropper, palette,
cursor, hand, grab
```

### 7️⃣ 查看/显示类 (11个)
```
visible, invisible, preview, render, image,
stats, chart, pin, info, lightbulb, help
```

### 8️⃣ 状态/反馈类 (7个)
```
check, check-circle, warning, error, dot, lock, unlock
```

### 9️⃣ 变换/对齐类 (13个)
```
move, rotate, scale, transform,
align-left, align-center, align-right,
distribute, flip-horizontal, flip-vertical,
group, ungroup, parent
```

### 🔟 动画/时间线类 (13个)
```
timeline, track, keyframe, bezier, curve, path,
easing, interpolation, linear, ease-in, ease-out,
bounce, spring
```

### 1️⃣1️⃣ 缩放/视图类 (10个)
```
zoom-in, zoom-out, fit-screen, maximize, minimize,
maximize-2, minimize-2, fullscreen, grid, snap
```

### 1️⃣2️⃣ 箭头/导航类 (8个)
```
arrow-up, arrow-down, arrow-left, arrow-right,
chevron-up, chevron-down, chevron-left, chevron-right
```

### 1️⃣3️⃣ 选择/交互类 (5个)
```
select, lasso, target, crosshair, anchor
```

### 1️⃣4️⃣ 效果/渲染类 (5个)
```
effect, filter, light, shadow, opacity
```

### 1️⃣5️⃣ 界面元素类 (10个)
```
window, menu, more-vertical, more-horizontal, close,
plus, minus, star, shield, keyboard
```

### 1️⃣6️⃣ 其他功能类 (9个)
```
link, share, bookmark, tag, flag, bell,
merge, split, slice
```

### 1️⃣7️⃣ 音频/视频类 (7个)
```
volume, music, mic, waveform, video, film, camera
```

### 1️⃣8️⃣ 装饰/表情类 (6个)
```
smile, heart, fire, lightning, diamond, thumbs-up
```

### 1️⃣9️⃣ 时间/组织类 (3个)
```
clock, calendar, layers
```

---

## 🚀 IcoMoon 导入完整教程

### 步骤 1: 访问 IcoMoon
🌐 打开: https://icomoon.io/app/

### 步骤 2: 导入所有 SVG

**方法 A - 拖拽（推荐）**:
1. 打开文件资源管理器
2. 导航到 `E:\ChronoBlocks\chronoblocks\icons\`
3. 全选所有 `.svg` 文件（排除 `.md` 文件）
4. 拖拽到 IcoMoon 窗口

**方法 B - 点击导入**:
1. 点击 IcoMoon 右上角 "Import Icons" 按钮
2. 选择所有 SVG 文件
3. 点击 "Open"

### 步骤 3: 选择图标
```
✅ 点击 "Select All" 选择所有图标
   或者
✅ 单独点击选择需要的图标（选中会显示黄色边框）
```

### 步骤 4: 生成字体
```
1. 点击底部 "Generate Font" 按钮
2. 进入字体生成界面
3. 预览所有图标
```

### 步骤 5: 配置字体选项⚙️

点击右上角齿轮图标，设置：

```
Font Name: icomoon
Class Prefix: icon-
✅ Use class selector
✅ Encode & Embed Font in CSS
起始码位: E900 (Private Use Area)
```

### 步骤 6: 下载字体包
```
1. 点击右下角 "Download" 按钮
2. 下载 icomoon.zip
3. 解压获得：
   ├── fonts/
   │   ├── icomoon.eot
   │   ├── icomoon.svg
   │   ├── icomoon.ttf ← 重要！
   │   └── icomoon.woff
   ├── style.css ← 重要！Unicode 映射
   ├── demo.html
   └── selection.json ← 保存此文件可重新编辑
```

### 步骤 7: 更新项目文件

#### 7.1 替换字体文件
```bash
复制: icomoon.ttf
到: src/main/resources/assets/chronoblocks/fonts/icomoon.ttf
```

#### 7.2 查看 Unicode 映射
打开 `style.css`，找到类似这样的映射：
```css
.icon-add:before {
  content: "\e900";
}
.icon-play:before {
  content: "\e901";
}
...
```

#### 7.3 更新 Icons.java
根据 `style.css` 中的映射更新常量：
```java
public static final String ADD = "\uE900";
public static final String PLAY = "\uE901";
// ... 等等
```

---

## 📋 使用检查清单

### ✅ 导入前检查
- [ ] 所有 SVG 文件都是纯黑色 (#000000)
- [ ] 没有彩色、渐变或复杂效果
- [ ] 文件名清晰、无特殊字符
- [ ] SVG 文件可以在浏览器中正常打开

### ✅ 导入 IcoMoon 检查
- [ ] 所有图标在预览中正常显示
- [ ] 没有出现问号或空白方框
- [ ] 图标名称正确
- [ ] 字体设置配置正确

### ✅ 生成字体检查
- [ ] demo.html 中所有图标正常显示
- [ ] style.css 包含所有图标映射
- [ ] icomoon.ttf 文件大小合理（约 50-100KB）

### ✅ 项目集成检查
- [ ] icomoon.ttf 已复制到正确路径
- [ ] Icons.java 已更新所有常量
- [ ] 项目编译通过
- [ ] 游戏中图标正常显示

---

## 🎨 图标设计准则

所有图标遵循以下设计准则：

1. **简洁性** - 最少的线条表达最清晰的含义
2. **一致性** - 相同的线条粗细（2px）
3. **识别性** - 在小尺寸下仍然清晰
4. **可扩展性** - 矢量格式，任意缩放

---

## 💡 故障排除

### 问题1: IcoMoon 中图标显示为问号

**原因**: SVG 包含彩色或不支持的属性

**解决**: 本项目所有图标已修正为纯黑色，应该不会出现此问题

### 问题2: 生成的字体文件太大

**原因**: 包含了太多图标

**解决**: 只选择需要的图标，分批生成字体

### 问题3: 某些图标在字体中变形

**原因**: SVG 路径过于复杂

**解决**: 本项目图标已优化，如仍有问题，可在 IcoMoon 中单独调整

---

## 📊 图标完整列表

查看 **COMPLETE_ICON_LIST.md** 获取按类别分类的完整图标列表（100+个）

---

## 🎊 特色图标

### 动画专用图标 ⭐

为动画制作特别设计的图标：

```
🎬 动画/时间线类:
   timeline, track, keyframe, bezier, curve, path,
   easing, interpolation, linear, ease-in, ease-out,
   bounce, spring

🔄 变换工具类:
   move, rotate, scale, transform,
   flip-horizontal, flip-vertical

📐 对齐工具类:
   align-left, align-center, align-right, distribute

🎥 视频/音频类:
   video, film, camera, volume, music, mic, waveform
```

---

## 📝 完整工作流

```
1. 设计 SVG (纯黑色) ✅ 已完成
   ↓
2. 导入 IcoMoon ← 您的下一步
   ↓
3. 生成字体文件
   ↓
4. 下载 icomoon.ttf
   ↓
5. 替换项目文件
   ↓
6. 更新 Icons.java
   ↓
7. 编译测试
   ↓
8. 完成！🎉
```

---

## 🔗 相关文档

- **COMPLETE_ICON_LIST.md** - 完整图标清单（按类别）
- **INDEX.md** - 图标索引
- **FINAL_SUMMARY.md** - 项目总结

---

**准备就绪！可以开始在 IcoMoon 生成字体了！** 🚀
