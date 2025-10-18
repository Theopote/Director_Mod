# 🚀 曲线插值系统 - 快速开始指南

## 📦 什么是曲线插值？

曲线插值让你的动画从"机械运动"变成"自然流畅"的效果。就像现实世界中物体不会突然启动或停止，而是有加速和减速的过程。

---

## ⚡ 30 秒快速上手

### 1️⃣ 创建关键帧动画

```java
// 创建轨道
Track track = new Track("MyAnimation", "container-uuid", true);

// 添加起始关键帧
Keyframe k1 = new Keyframe(0.0f);
k1.setPosition(0f, 0f, 0f);
k1.setInterpolationType(InterpolationType.EASE_IN_OUT); // ← 设置曲线
track.addKeyframe(k1);

// 添加结束关键帧
Keyframe k2 = new Keyframe(2.0f);
k2.setPosition(100f, 0f, 0f);
track.addKeyframe(k2);

// 播放！动画会平滑地从慢到快再到慢
```

### 2️⃣ 效果对比

```
LINEAR（线性）:
  0s ━━━━━━━━━━> 2s
     匀速运动（机械）

EASE_IN_OUT（缓入缓出）:
  0s ━╱━━━━━╲━> 2s
     慢→快→慢（自然）
```

---

## 🎨 8 种插值类型速查

### 基础类型（推荐新手）

| 类型 | 效果 | 适用场景 | 代码 |
|-----|------|---------|------|
| **LINEAR** | 匀速 | 机械运动 | `InterpolationType.LINEAR` |
| **EASE_OUT** | 快→慢 | 停止效果 | `InterpolationType.EASE_OUT` |
| **EASE_IN** | 慢→快 | 启动效果 | `InterpolationType.EASE_IN` |
| **EASE_IN_OUT** | 慢→快→慢 | 大多数动画 | `InterpolationType.EASE_IN_OUT` |

### 高级类型

| 类型 | 效果 | 适用场景 | 代码 |
|-----|------|---------|------|
| **BEZIER** | 自定义曲线 | 精确控制 | `InterpolationType.BEZIER` |
| **STEP** | 立即跳变 | 开关效果 | `InterpolationType.STEP` |
| **SPRING** | 弹性回弹 | UI 交互 | `InterpolationType.SPRING` |
| **BOUNCE** | 多次弹跳 | 球落地 | `InterpolationType.BOUNCE` |

---

## 📝 常用场景示例

### 场景 1：物体移动（推荐 EASE_IN_OUT）

```java
keyframe.setInterpolationType(InterpolationType.EASE_IN_OUT);
```

**效果**: 平滑启动，平滑停止，看起来很自然

---

### 场景 2：UI 弹出（推荐 EASE_OUT）

```java
keyframe.setInterpolationType(InterpolationType.EASE_OUT);
```

**效果**: 快速弹出，然后缓慢停下，有冲击感

---

### 场景 3：按钮按下（推荐 SPRING）

```java
keyframe.setInterpolationType(InterpolationType.SPRING);
keyframe.setCustomParams(new float[]{6.0f, 6.0f}); // 阻尼, 频率
```

**效果**: 按下后有弹性回弹，像真实的弹簧

---

### 场景 4：球落地（推荐 BOUNCE）

```java
keyframe.setInterpolationType(InterpolationType.BOUNCE);
```

**效果**: 多次弹跳，逐渐停止

---

### 场景 5：自定义曲线（推荐 BEZIER）

```java
keyframe.setInterpolationType(InterpolationType.BEZIER);
keyframe.setBezierControlPoints(0.42f, 0.0f, 0.58f, 1.0f); // CSS ease-in-out
```

**效果**: 精确控制加速和减速的曲线形状

---

## 🎬 完整工作流程

### 步骤 1：在 Timeline 中创建关键帧

1. 选择容器
2. 在 Timeline 中点击 "Add Keyframe"
3. 设置位置、旋转、缩放

### 步骤 2：选择插值类型

**方法 A：代码设置**
```java
Keyframe kf = getSelectedKeyframe();
kf.setInterpolationType(InterpolationType.EASE_OUT);
```

**方法 B：使用 UI（推荐）**
1. 在 Timeline 中选中关键帧
2. 在 Keyframe Properties 面板选择插值类型
3. 如果选择 BEZIER，调整控制点滑动条

### 步骤 3：播放预览

1. 点击 Toolbar 的 Play 按钮
2. 观察动画效果
3. 如果不满意，返回步骤 2 调整

---

## 🎨 贝塞尔曲线快速参考

### 预设曲线（推荐）

```java
// 使用预设
keyframe.setInterpolationType(InterpolationType.BEZIER);

// CSS 标准预设
keyframe.setBezierControlPoints(CurveInterpolator.getBezierPreset("ease"));
keyframe.setBezierControlPoints(CurveInterpolator.getBezierPreset("ease-in-out"));
keyframe.setBezierControlPoints(CurveInterpolator.getBezierPreset("ease-in-cubic"));
```

### 自定义控制点

```java
// 控制点格式: [x1, y1, x2, y2]
// x 范围: 0.0 ~ 1.0（时间）
// y 范围: -0.5 ~ 1.5（值，可以超出）

keyframe.setBezierControlPoints(
    0.42f, 0.0f,  // P1 (x1, y1)
    0.58f, 1.0f   // P2 (x2, y2)
);
```

---

## 💡 实用技巧

### 技巧 1：快速试用不同曲线

```java
// 循环测试所有曲线类型
for (InterpolationType type : InterpolationType.values()) {
    keyframe.setInterpolationType(type);
    playAndWait(2000); // 播放 2 秒
}
```

### 技巧 2：弹簧效果调参

```java
// 阻尼越大，回弹越少
// 频率越大，振荡越快

// 轻微回弹
keyframe.setCustomParams(new float[]{8.0f, 4.0f});

// 强烈回弹
keyframe.setCustomParams(new float[]{4.0f, 8.0f});
```

### 技巧 3：分段动画

```java
// 第一段：快速启动
Keyframe k1 = new Keyframe(0.0f);
k1.setInterpolationType(InterpolationType.EASE_IN);

// 第二段：缓慢停止
Keyframe k2 = new Keyframe(1.0f);
k2.setInterpolationType(InterpolationType.EASE_OUT);

Keyframe k3 = new Keyframe(2.0f);
```

---

## 🎯 推荐配置

### 通用动画（80% 场景适用）

```java
keyframe.setInterpolationType(InterpolationType.EASE_IN_OUT);
```

### UI 交互

```java
// 弹出菜单
keyframe.setInterpolationType(InterpolationType.EASE_OUT);

// 关闭菜单
keyframe.setInterpolationType(InterpolationType.EASE_IN);

// 按钮反馈
keyframe.setInterpolationType(InterpolationType.SPRING);
keyframe.setCustomParams(new float[]{7.0f, 6.0f});
```

### 物理效果

```java
// 球落地
keyframe.setInterpolationType(InterpolationType.BOUNCE);

// 弹簧拉伸
keyframe.setInterpolationType(InterpolationType.SPRING);
keyframe.setCustomParams(new float[]{5.0f, 8.0f});
```

---

## 🐛 常见问题

### Q: 如何知道使用哪种曲线？

**A**: 使用这个决策树：

```
需要平滑过渡？
├─ 是 → 使用 EASE_IN_OUT（万能选择）
└─ 否 → 使用 LINEAR 或 STEP

需要弹性效果？
├─ 回弹 → SPRING
└─ 弹跳 → BOUNCE

需要精确控制？
└─ 使用 BEZIER 自定义
```

### Q: 为什么动画看起来不平滑？

**A**: 检查以下项目：

1. ✅ 关键帧是否设置了插值类型（默认是 LINEAR）
2. ✅ 关键帧间隔是否太短（建议 > 0.5 秒）
3. ✅ FPS 是否足够高（需要 > 30fps）

### Q: BEZIER 曲线参数怎么调？

**A**: 使用预设开始：

```java
// 先试试预设
String[] presets = {"ease", "ease-in", "ease-out", "ease-in-out"};
for (String preset : presets) {
    float[] controls = CurveInterpolator.getBezierPreset(preset);
    keyframe.setBezierControlPoints(controls);
    // 观察效果
}

// 找到接近的预设后，微调控制点
```

---

## 📈 性能建议

### ✅ 性能友好

```java
// 所有插值类型都是 O(1)，随便用
keyframe.setInterpolationType(任何类型); // 都很快！
```

### ⚠️ 注意事项

```java
// 避免在每帧创建新的关键帧对象
// ❌ 不好
for (int i = 0; i < 1000; i++) {
    Keyframe kf = new Keyframe(i * 0.01f); // 每帧创建
}

// ✅ 好
Keyframe kf = new Keyframe();
for (int i = 0; i < 1000; i++) {
    kf.setTime(i * 0.01f); // 重用对象
}
```

---

## 🎓 进阶学习

### 学习路径

1. ✅ **基础** - 试用 LINEAR, EASE_IN_OUT
2. ✅ **中级** - 尝试所有 8 种类型
3. ✅ **高级** - 自定义 BEZIER 曲线
4. ✅ **专家** - 组合多种曲线，创建复杂动画

### 推荐资源

- **CSS Easing Functions** - https://easings.net/
- **Bezier Curve Editor** - https://cubic-bezier.com/
- **Animation Principles** - Disney's 12 Principles

---

## 🚀 下一步

### 你现在可以：

1. 🎬 **创建第一个平滑动画** - 使用 EASE_IN_OUT
2. 🎨 **试用贝塞尔预设** - 体验不同风格
3. 🏀 **添加物理效果** - 弹簧和弹跳
4. 🎯 **优化现有动画** - 为所有关键帧设置曲线

### 推荐尝试：

```java
// 创建一个"弹性UI"效果
Keyframe k1 = new Keyframe(0.0f);
k1.setScale(1f, 1f, 1f);
k1.setInterpolationType(InterpolationType.SPRING);
k1.setCustomParams(new float[]{6.0f, 8.0f});

Keyframe k2 = new Keyframe(0.5f);
k2.setScale(1.2f, 1.2f, 1.2f);

// 播放，看按钮"弹"起来！
```

---

**🎉 现在你已经掌握了曲线插值系统的基础！**

开始为你的动画添加曲线，让它们更加自然流畅吧！🚀

---

**版本**: v4.5  
**最后更新**: 2025-10-15  
**下一步**: [CURVE_INTERPOLATION_SYSTEM_V4_5.md](CURVE_INTERPOLATION_SYSTEM_V4_5.md) - 完整技术文档

