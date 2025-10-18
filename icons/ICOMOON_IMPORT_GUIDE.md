# 🎯 IcoMoon 导入操作指南

> **5分钟快速生成字体文件**

---

## 🚀 快速开始（3步完成）

### 第1步: 打开 IcoMoon
🌐 访问: https://icomoon.io/app/

### 第2步: 导入所有 SVG
**拖拽导入**（最简单）:
```
1. 打开文件夹: E:\ChronoBlocks\chronoblocks\icons\
2. 全选所有 .svg 文件（排除 .md 文件）
3. 拖拽到 IcoMoon 窗口
4. 等待导入完成
```

### 第3步: 生成并下载
```
1. 点击 "Select All" 选择所有图标
2. 点击底部 "Generate Font" 按钮
3. 点击右下角 "Download" 下载
4. 解压 icomoon.zip
```

---

## ⚙️ 字体配置（推荐设置）

点击 "Generate Font" 页面右上角的齿轮图标⚙️，设置：

```
✅ Font Name: icomoon
✅ Class Prefix: icon-
✅ Use class selector: ✓
✅ Encode & Embed Font in CSS: ✓
✅ Use i tag: (可选)
✅ CSS Selector: .icon-
```

**起始码位**: 
```
第一个图标从 E900 开始（Private Use Area）
IcoMoon 会自动递增分配后续图标
```

---

## 📦 下载文件说明

解压 `icomoon.zip` 后获得：

### 📁 fonts/ 文件夹
```
fonts/
├── icomoon.ttf    ← 🎯 需要此文件！
├── icomoon.eot
├── icomoon.svg
└── icomoon.woff
```

### 📄 重要文件
```
style.css          ← 🎯 Unicode 映射表（查看码位）
selection.json     ← 🎯 项目文件（保存可重新编辑）
demo.html          ← 预览所有图标
demo-files/        ← 演示资源
```

---

## 🔄 更新项目流程

### 1. 复制字体文件
```
源文件: icomoon.ttf
目标: src/main/resources/assets/chronoblocks/fonts/icomoon.ttf
```

### 2. 查看 Unicode 映射

打开 `style.css`，查看映射关系：

```css
.icon-play:before {
  content: "\e900";  ← Unicode 码位
}
.icon-pause:before {
  content: "\e901";
}
.icon-stop:before {
  content: "\e902";
}
...
```

### 3. 更新 Icons.java

根据 `style.css` 更新常量：

```java
// 播放控制
public static final String PLAY = "\uE900";
public static final String PAUSE = "\uE901";
public static final String STOP = "\uE902";
// ... 依此类推
```

**注意**: 
- CSS 中是 `\e900`（小写）
- Java 中是 `\uE900`（大写 u + 大写十六进制）

### 4. 编译测试
```bash
cd E:\ChronoBlocks\chronoblocks
.\gradlew compileJava
```

### 5. 运行游戏测试
```bash
.\gradlew runClient
```

---

## 🎯 图标名称建议

在 IcoMoon 中建议使用以下命名规则：

### 规则1: 使用连字符分隔
```
✅ arrow-up
✅ skip-back
✅ zoom-in
❌ arrowUp
❌ skipBack
```

### 规则2: 语义化命名
```
✅ play, pause, stop （清晰直观）
✅ camera, video, film （功能明确）
❌ icon1, icon2, icon3 （无意义）
```

### 规则3: 保持一致性
```
✅ align-left, align-center, align-right
✅ arrow-up, arrow-down, arrow-left, arrow-right
✅ chevron-up, chevron-down, chevron-left, chevron-right
```

---

## ✅ 验证清单

### IcoMoon 导入验证
- [ ] 所有100+个图标都已导入
- [ ] 没有图标显示为问号或空白
- [ ] 图标预览清晰可见
- [ ] 图标名称正确无误

### 字体生成验证
- [ ] demo.html 中所有图标正常显示
- [ ] style.css 包含所有图标的 Unicode 映射
- [ ] icomoon.ttf 文件大小在 50-200KB 之间

### 项目集成验证
- [ ] 字体文件已复制到项目
- [ ] Icons.java 已完整更新
- [ ] 编译无错误
- [ ] 游戏中图标正常显示（不是方框）

---

## 💡 专业提示

### 提示1: 保存 IcoMoon 项目
```
下载后保留 selection.json 文件
可以重新导入 IcoMoon 继续编辑
```

### 提示2: 批量生成
```
如果图标太多，可以分组生成：
- 核心图标集 (50个)
- 扩展图标集 (50个)
分别生成两个字体文件
```

### 提示3: 测试预览
```
使用 demo.html 测试所有图标
确保在字体中显示正常再集成到项目
```

### 提示4: 版本管理
```
每次生成新字体时，保存：
- icomoon.ttf (字体文件)
- style.css (映射表)
- selection.json (项目文件)
便于后续更新和维护
```

---

## 🆘 常见问题

### Q1: 导入后某些图标是问号？
**A**: 检查该 SVG 是否包含彩色或复杂效果，本项目所有图标已优化为纯黑色。

### Q2: 字体文件太大？
**A**: 只选择需要的图标，不必全部导入。

### Q3: 如何修改图标顺序？
**A**: 在 IcoMoon 的 "Generate Font" 界面拖拽图标重新排列。

### Q4: 如何删除某个图标？
**A**: 在图标选择界面，点击图标右上角的 "×" 删除。

### Q5: 可以重新编辑吗？
**A**: 可以！导入 `selection.json` 文件到 IcoMoon 恢复项目。

---

## 📞 获取帮助

如果遇到问题：

1. 📖 查看 IcoMoon 官方文档: https://icomoon.io/#docs
2. 🔍 检查 `COMPLETE_ICON_LIST.md` 确认图标清单
3. 📝 查看 `style.css` 中的 Unicode 映射
4. 🧪 使用 `demo.html` 测试字体

---

**准备好开始了吗？** 🚀

**打开 IcoMoon，拖拽 SVG 文件，3步生成字体！**

