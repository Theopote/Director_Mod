## ChronoBlocks 视频导出（MP4）— FFmpeg 安装与放置指南

你已选择 **不内置/不分发 FFmpeg 二进制** 的方案。ChronoBlocks 只会在本机查找 ffmpeg 并调用。

### ChronoBlocks 查找 ffmpeg 的顺序（当前实现）
- **1) JVM 参数（最高优先级）**：`-Dchronoblocks.ffmpeg=/path/to/ffmpeg`
- **2) 游戏根目录（.minecraft）**：
  - Windows：`<gameDir>/ffmpeg.exe`
  - Linux/macOS：`<gameDir>/ffmpeg`
- **3) 系统 PATH**：直接执行 `ffmpeg` / `ffmpeg.exe`

其中 `<gameDir>` 为 Fabric 的 gameDir（通常就是 `.minecraft`）。

### 推荐做法（最简单）
- **Windows**：把 `ffmpeg.exe` 放到 `.minecraft` 根目录（与 `mods/` 同级）。
- **Linux/macOS**：把 `ffmpeg` 放到 `.minecraft` 根目录，并确保可执行权限（`chmod +x ffmpeg`）。

### FFmpeg 下载来源（建议使用可信构建源）
你需要下载带 H.264 编码支持的 FFmpeg（即支持 `libx264`）。

- **Windows**
  - 常见做法是下载官方/社区构建的压缩包，解压后取 `bin/ffmpeg.exe`。
  - 关键词：`FFmpeg Windows build gyan` 或 `BtbN FFmpeg Builds`

- **Linux**
  - 建议使用发行版包管理器（如 `apt/yum/pacman`），或使用静态构建。
  - 关键词：`johnvansickle ffmpeg static`

- **macOS**
  - 可用 Homebrew 安装（`brew install ffmpeg`），或下载社区构建。
  - 关键词：`evermeet ffmpeg`

### 如何验证 ffmpeg 可用
在命令行执行：

```bash
ffmpeg -version
```

ChronoBlocks 的“导出视频”对话框里也会显示 **FFmpeg 状态：已就绪/不可用**，并展示检测到的路径与错误原因。


