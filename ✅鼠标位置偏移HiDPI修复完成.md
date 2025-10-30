# ✅ 鼠标位置偏移 HiDPI 修复完成

## 📋 问题描述
**症状**：点击选择方块时，鼠标位置和实际选中位置不一致，距离越远偏移越严重

## ✅ 修复内容
1. **坐标系统统一**：glfwGetCursorPos() ⇔ Window.getWidth/Height()
2. **右手系修正**：
ight = worldUp  forward, up = right  forward
3. **静态 FOV**：使用 client.options.getFov()（动态FOV因权限问题暂时不可用）

## 🎯 关键修改
- 从 getFramebufferWidth/Height() 改为 getWidth/Height()
- 修正叉积顺序
- 统一坐标空间

## 📁 修改文件
- SelectionInputHandler.java (raycastFromMouse方法)

## ✅ 编译状态
- 编译成功

## ⏳ 待测试
- 近距离选择（5格）
- 中距离选择（50格）
- 远距离选择（100+格）
- HiDPI显示器测试（如果适用）

**预期效果**：任意距离都无偏移
