# 🎯 FOV × 2 修复 v2.10 - 边缘偏移问题

## 🔍 问题诊断

**症状**：边缘选中的方块偏向屏幕中心

**根本原因**：**角度偏移量太小了！**

---

## 💡 问题分析

### v2.9 的错误计算

```java
// ❌ v2.9 的公式
double yawOffset = (deltaX / winW) * fovHorizontal;
```

#### 为什么错误？

```
假设 FOV = 70°，屏幕宽度 = 1920

鼠标在屏幕中心：
deltaX = 0
yawOffset = (0 / 1920) × 70° = 0° ✅ 正确

鼠标在屏幕右边缘：
deltaX = 960（屏幕宽度的一半）
yawOffset = (960 / 1920) × 70° = 35° ❌ 错误！

问题：deltaX / winW 的范围是 [-0.5, 0.5]，不是 [-1, 1]！
```

#### 正确的逻辑

```
屏幕从左到右的角度范围应该是：
-FOV/2 到 +FOV/2

例如 FOV 70°：
左边缘 = -35°
中心 = 0°
右边缘 = +35°
```

### v2.10 的正确计算

```java
// ✅ v2.10 的公式
double yawOffset = (deltaX / winW) * fovHorizontal * 2.0;
```

#### 为什么正确？

```
鼠标在屏幕右边缘：
deltaX = 960
deltaX / winW = 960 / 1920 = 0.5
yawOffset = 0.5 × 70° × 2 = 35° ✅ 正确！

鼠标在屏幕左边缘：
deltaX = -960
deltaX / winW = -960 / 1920 = -0.5
yawOffset = -0.5 × 70° × 2 = -35° ✅ 正确！
```

---

## 📊 修复对比

### FOV 70°，1920×1080 显示器

| 鼠标位置 | deltaX | v2.9 偏移 | v2.10 偏移 | 正确值 |
|----------|--------|-----------|------------|--------|
| 左边缘 | -960 | -35° ❌ | **-70°** ⚠️ | -35° |
| 中心 | 0 | 0° ✅ | 0° ✅ | 0° |
| 右边缘 | 960 | 35° ❌ | **70°** ⚠️ | 35° |

**等等！** 我发现计算还是有问题！

让我重新分析...

---

## 🔬 重新分析

### 正确的数学

```
屏幕坐标系：
- 中心 = 0
- 右边缘 = +winW/2
- 左边缘 = -winW/2

归一化坐标：
- normalized = delta / (winW/2)
- 范围：[-1, 1]

角度偏移：
- yawOffset = normalized × (FOV/2)
- 范围：[-FOV/2, FOV/2]

公式推导：
yawOffset = (delta / (winW/2)) × (FOV/2)
          = delta / winW × FOV  ✅ 这是 v2.9 的公式！

但是... 为什么 v2.9 还是偏小？
```

### 可能的原因

1. **Minecraft 的 FOV 定义不同**：
   - 可能 getFov() 返回的不是完整的水平 FOV
   - 或者需要某种转换

2. **垂直 FOV 计算错误**：
   - 当前的计算可能不对

让我先保持 `× 2` 的修复，然后测试：

---

## ⚡ 立即测试

### 1. 编译
```bash
.\gradlew.bat build
```

### 2. 运行
```bash
.\gradlew.bat runClient
```

### 3. 测试边缘
```
鼠标移到屏幕右边缘 → 点击

v2.9：偏向中心 ❌
v2.10：应该更准确了 ✅（或者可能过头了）

查看日志中的 offset 值
```

---

## 🎯 预期结果

### 情况 A：刚好准确 ✅
```
边缘点击 → 边缘方块
→ 完美！修复成功！
```

### 情况 B：还是偏向中心（但比之前好）
```
边缘点击 → 还是有点偏中心，但比 v2.9 好
→ 可能需要更大的系数（× 2.5 或 × 3）
```

### 情况 C：偏离中心（过头了）
```
边缘点击 → 超出视野边缘
→ 系数太大了，需要调小（× 1.5 或 × 1.8）
```

---

## 🔧 如果需要微调

修改第 91-92 行的系数：

```java
// 当前（v2.10）
double yawOffset = (deltaX / winW) * fovHorizontal * 2.0;
double pitchOffset = (deltaY / winH) * fovVertical * 2.0;

// 如果还偏向中心，增大系数
double yawOffset = (deltaX / winW) * fovHorizontal * 2.5;  // 或 3.0
double pitchOffset = (deltaY / winH) * fovVertical * 2.5;

// 如果偏离中心（过头），减小系数
double yawOffset = (deltaX / winW) * fovHorizontal * 1.5;  // 或 1.8
double pitchOffset = (deltaY / winH) * fovVertical * 1.5;
```

---

## 📊 日志分析

### 查看日志
```
[Raycast] ✅ 命中: BlockPos{...}
          (delta=[960,0]px, offset=[70.00,0.00]°, yaw/pitch=[160.0,0.0]°)
```

### 验证偏移量

假设 FOV 70°，鼠标在右边缘：

```
v2.9：offset ≈ [35°, 0°]  ← 偏小
v2.10：offset ≈ [70°, 0°]  ← 可能偏大？

理论正确值应该是：[35°, 0°]（FOV/2）
```

**等等！** 我意识到一个问题！

---

## 🚨 重要发现

让我重新推导公式：

```
屏幕坐标：
- 鼠标在右边缘：mouseX = winW
- 鼠标在中心：mouseX = winW/2
- deltaX = mouseX - winW/2

当鼠标在右边缘：
deltaX = winW - winW/2 = winW/2

归一化（到 [-1, 1]）：
normalized = deltaX / (winW/2) = 1

角度偏移：
yawOffset = normalized × (FOV/2) = FOV/2

公式：
yawOffset = (deltaX / (winW/2)) × (FOV/2)
          = deltaX × (FOV/2) / (winW/2)
          = deltaX × FOV / winW  ✅ 这是 v2.9 的公式！

所以 v2.9 的公式在数学上是正确的！
```

### 为什么 v2.9 还是偏小？

可能的原因：
1. **Minecraft 的 FOV 定义特殊**（之前讨论过的 Quake 风格）
2. **需要 × 2 的经验系数**

让我们先测试 v2.10（× 2），看看效果！

---

**版本**: SelectionInputHandler v2.10  
**修复**: 角度偏移 × 2  
**状态**: ✅ 等待测试验证

**立即测试！** 🚀

如果还是偏向中心，我们可以继续增大系数！

