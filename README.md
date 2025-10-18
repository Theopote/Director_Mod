# 🎬 ChronoBlocks

> **Minecraft 动画制作模组** - 专业的方块动画编辑器

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.11+-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ 功能特色

### 🎯 核心功能

- **📅 时间轴系统** - 专业的多轨道动画编辑器
- **📦 容器管理** - 6种容器类型（分组、建筑、区域、粒子、动画、自定义）
- **🔑 关键帧编辑** - 完整的关键帧动画支持
- **📈 曲线插值** - 12种插值类型（线性、缓动、贝塞尔、弹跳等）
- **🎨 属性系统** - 智能属性面板，自动适配不同对象
- **🖼️ 自定义图标** - 161个专业动画工具图标

### 🎮 界面系统

- **ImGui 界面** - 现代化的停靠式布局
- **中文支持** - 完整的中文界面和字体
- **主题系统** - 可定制的UI主题
- **快捷键** - 完整的键盘快捷键支持
- **智能输入** - 自动焦点管理，游戏和UI无缝切换

### 🔧 技术特性

- **依赖注入** - 企业级的模块化架构
- **命令模式** - 完整的撤销/重做系统
- **事件系统** - 解耦的事件总线
- **插件API** - 支持第三方扩展
- **性能优化** - 高效的渲染和数据管理

---

## 📦 安装

### 前置要求

- Minecraft 1.21.4
- Fabric Loader 0.16.11+
- Java 21

### 安装步骤

1. 下载最新的 `.jar` 文件
2. 将文件放入 `.minecraft/mods` 文件夹
3. 启动游戏
4. 按 `[指定按键]` 打开 ChronoBlocks 界面

---

## 🚀 快速开始

### 1. 创建容器

1. 打开 ChronoBlocks 界面
2. 进入 **Container** 面板
3. 点击 **➕ 新建容器**
4. 选择容器类型（建筑、区域等）

### 2. 添加关键帧

1. 在时间轴上选择时间点
2. 调整容器属性（位置、旋转、缩放等）
3. 点击 **添加关键帧**
4. 重复以上步骤创建动画

### 3. 播放动画

1. 点击时间轴上的 **▶ 播放** 按钮
2. 查看动画效果
3. 调整关键帧和插值类型优化动画

---

## 📚 文档

- [完整文档索引](CHRONOBLOCKS_PROJECT_INDEX_V1.md)
- [属性系统指南](属性系统README.md)
- [时间轴使用指南](TIMELINE_README.md)
- [容器系统文档](CONTAINER_SYSTEM_COMPLETE_V4.md)
- [图标系统说明](icons/README.md)

---

## 🎨 图标系统

ChronoBlocks 使用自定义的图标字体系统，包含 **161 个专业动画工具图标**：

- ✅ 播放控制图标（播放、暂停、停止、循环等）
- ✅ 文件操作图标（保存、打开、导入、导出等）
- ✅ 编辑工具图标（添加、删除、复制、粘贴等）
- ✅ 动画专业图标（关键帧、轨道、插值、曲线等）
- ✅ 变换工具图标（移动、旋转、缩放、翻转等）

详见 [图标系统完整文档](图标系统完成总结.md)

---

## 🛠️ 开发

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/Theopote/ChronoBlocks.git
cd ChronoBlocks

# 编译
./gradlew build

# 运行客户端
./gradlew runClient
```

### 项目结构

```
ChronoBlocks/
├── src/main/java/com/chronoblocks/
│   ├── core/              # 核心系统（动画、容器、时间轴）
│   ├── ui/                # UI系统（面板、主题、图标）
│   ├── mixin/             # Mixin注入
│   └── ...
├── src/main/resources/
│   └── assets/chronoblocks/
│       ├── fonts/         # 字体文件
│       └── lang/          # 语言文件
├── icons/                 # SVG图标源文件（162个）
└── docs/                  # 文档（100+份）
```

---

## 🤝 贡献

欢迎贡献！请：

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📝 更新日志

### v1.0 (2025-10-18)

#### ✨ 新功能
- 🎬 完整的时间轴动画系统
- 📦 容器管理系统（6种类型）
- 🔑 关键帧编辑器
- 📈 12种曲线插值
- 🎨 属性面板系统（15+适配器）
- 🖼️ 自定义图标字体（161个图标）

#### 🔧 技术改进
- ✅ ImGui + GLFW + OpenGL3 渲染
- ✅ 完整的中文支持
- ✅ 智能输入焦点管理
- ✅ 企业级依赖注入架构
- ✅ 命令模式撤销/重做

#### 📚 文档
- ✅ 100+ 份技术文档
- ✅ 完整的API参考
- ✅ 详细的使用指南

---

## 🔗 相关链接

- **作者**: [@Theopote](https://github.com/Theopote)
- **问题反馈**: [Issues](https://github.com/Theopote/ChronoBlocks/issues)
- **讨论**: [Discussions](https://github.com/Theopote/ChronoBlocks/discussions)

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🙏 致谢

- [Dear ImGui](https://github.com/ocornut/imgui) - 优秀的GUI框架
- [imgui-java](https://github.com/SpaiR/imgui-java) - ImGui的Java绑定
- [Fabric](https://fabricmc.net/) - 模组开发框架
- [IcoMoon](https://icomoon.io/) - 图标字体生成工具
- [Inkscape](https://inkscape.org/) - SVG编辑工具

---

**用 ChronoBlocks 创建精彩的 Minecraft 动画！** 🚀✨

