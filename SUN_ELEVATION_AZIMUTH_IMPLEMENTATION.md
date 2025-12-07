# 太阳高度角和方位角实现指南

## 🔴 当前状态

**问题确认：** 太阳高度角和方位角目前**没有工作**。

**原因：** 
- `TimeSample` 中已包含 `sunElevation` 和 `sunAzimuth` 数据
- `TimeTrackSampler` 已实现插值逻辑
- 属性面板可以设置这些值
- **但是**：没有 Mixin 将数据应用到实际的太阳渲染中

**结果：** 设置太阳高度角和方位角后，游戏中的太阳位置**不会改变**，仍然按照原版的 `worldTime` 计算。

## ✅ 解决方案

### 1. 已创建 Mixin 框架

已创建 `SkyRendererMixin.java`，但需要根据 Minecraft 1.21.4 的实际映射调整。

### 2. 如何找到正确的注入点

**步骤 1：查找 WorldRenderer 类**

在 IDE 中使用 "Go to Class" (Ctrl+N) 查找：
```
net.minecraft.client.render.WorldRenderer
```

**步骤 2：查找天空渲染方法**

在 `WorldRenderer` 中搜索以下关键词：
- `renderSky`
- `renderSun`
- `sun`
- `sky`

**步骤 3：查找太阳渲染的具体位置**

找到类似这样的代码：
```java
// 伪代码示例
public void renderSky(MatrixStack matrices, ...) {
    float skyAngle = world.getSkyAngle(tickDelta);
    // ... 天空渲染逻辑
    // 太阳渲染通常在这里，涉及 MatrixStack 旋转
    matrices.multiply(RotationAxis.POSITIVE_X.rotation(...));
    // 渲染太阳纹理
}
```

**步骤 4：确定注入点**

找到太阳渲染的**具体位置**，通常是：
- 在 `matrices.multiply(...)` 调用之前（旋转之前）
- 或在 `world.getSkyAngle(...)` 调用之后（获取角度之后）

### 3. 实现完整的 Mixin

找到正确的方法签名后，更新 `SkyRendererMixin.java`：

```java
@Inject(
    method = "renderSky(...)", // 填入实际的方法签名
    at = @At(value = "INVOKE", target = "Lnet/minecraft/...;...") // 找到太阳旋转的具体位置
)
private void chronoblocks$injectCustomSunPosition(
        MatrixStack matrices, 
        float tickDelta, 
        CallbackInfo ci) {
    
    TimelineWorldController controller = TimelineWorldController.getInstance();
    if (controller == null || !controller.hasOverride()) {
        return;
    }

    TimeSample sample = controller.current();
    if (sample == null || sample.isUseWorldTime()) {
        return; // 使用原版逻辑
    }

    // ⭐ 应用自定义太阳位置
    // 方位角：转换为 Minecraft 坐标系（0° = 正北 → Minecraft 南=0°）
    float azimuthRad = (float) Math.toRadians(sample.getSunAzimuth() - 180.0f);
    
    // 高度角：转换为 pitch 角度（90° - elevation）
    float elevationRad = (float) Math.toRadians(90.0f - sample.getSunElevation());
    
    // 先绕 Y 轴旋转（方位角）
    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(azimuthRad));
    
    // 再绕 X 轴旋转（高度角）
    matrices.multiply(RotationAxis.POSITIVE_X.rotation(elevationRad));
}
```

### 4. 备选方案：修改 ClientWorld.getSkyAngle()

如果无法找到太阳渲染的具体位置，可以尝试通过 `ClientWorldMixin` 覆盖 `getSkyAngle()` 方法：

```java
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    
    @ModifyReturnValue(
        method = "getSkyAngle(F)F", // 需要确认方法名
        at = @At("RETURN")
    )
    private float chronoblocks$modifySkyAngle(float original, float tickDelta) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (controller == null || !controller.hasOverride()) {
            return original;
        }

        TimeSample sample = controller.current();
        if (sample == null || sample.isUseWorldTime()) {
            return original;
        }

        // ⚠️ 限制：这种方式只能控制高度，无法控制方位角
        // 因为原版只使用 skyAngle 来计算高度
        return sample.getSkyAngle();
    }
}
```

**限制：** 这种方式只能影响太阳的高度，无法控制方位角。

### 5. 测试验证

实现后，请测试：

1. **创建时间关键帧**
   - 设置 "自定义太阳" 模式
   - 设置 `sunElevation = 60°`（正午高度）
   - 设置 `sunAzimuth = 180°`（正南）

2. **观察效果**
   - 太阳应该在正南方向，高度角为 60°
   - 如果太阳位置没有变化，说明 Mixin 没有生效

3. **调试**
   - 在 Mixin 方法中添加日志输出
   - 检查是否被调用
   - 检查 `sample` 的值是否正确

## 📚 参考资料

### Minecraft 1.21.4 相关类

- `net.minecraft.client.render.WorldRenderer` - 天空渲染主类
- `net.minecraft.client.world.ClientWorld` - 世界类，包含 `getSkyAngle()` 方法
- `net.minecraft.util.math.RotationAxis` - 旋转轴常量
- `net.minecraft.client.util.math.MatrixStack` - 矩阵栈

### 相关方法（需要确认）

- `WorldRenderer.renderSky(...)` - 渲染天空
- `ClientWorld.getSkyAngle(float)` - 获取天空角度
- `MatrixStack.multiply(Quaternionf)` - 应用旋转矩阵

## 🎯 下一步

1. ✅ 已创建 `SkyRendererMixin` 框架
2. ⏳ **需要**：在 IDE 中查找正确的注入点
3. ⏳ **需要**：更新 Mixin 的方法签名
4. ⏳ **需要**：测试验证太阳位置是否正确

## ⚠️ 注意事项

1. **方法签名可能不同**：Minecraft 的映射可能因版本而异，需要根据实际映射调整
2. **注入点选择**：必须在太阳旋转**之前**注入，否则无效
3. **坐标系统**：Minecraft 的坐标系可能与标准的球面坐标不同，需要调整转换
4. **性能考虑**：Mixin 会在每帧渲染时调用，确保逻辑高效

