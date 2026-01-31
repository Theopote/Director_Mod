# 时间恢复修复 - v4.0
## 问题分析

用户报告：关闭 ChronoBlocks 后，世界时间没有恢复到 Minecraft 默认值，即使使用 `/time set noon` 命令也无法改变时间。

### 根本原因

#### 1. ❌ **Mixin 注入位置错误**
```java
// 旧代码（错误）
@Inject(method = "getTimeOfDay()J", at = @At("RETURN"), cancellable = true)
private void overrideTimeOfDay(CallbackInfoReturnable<Long> cir) {
    ...
}
```

**问题：**
- `@At("RETURN")` 位置在方法执行后，此时原始返回值已经计算完成
- 当 `hasOverride()` 为 false 时，仅 `return` 而不修改 `cir`，但此时的值可能已经被其他 Mixin 或缓存影响
- 无法保证完全恢复原始行为

#### 2. ❌ **`freezeMinecraftTime()` 中永久修改世界时间**
```java
// 旧代码（错误）
private synchronized void freezeMinecraftTime() {
    ...
    if (hasOverride()) {
        frozenGameTime = currentSample.getWorldTimeTicks() % 24000L;
        try {
            internalTimeUpdate = true;
            world.setTime(frozenGameTime, frozenGameTime, false);  // ⚠️ 永久改变世界内部时间
        } finally {
            internalTimeUpdate = false;
        }
    }
    ...
}
```

**问题：**
- `world.setTime()` 直接修改了 Minecraft 世界对象的内部时间值
- 即使后来关闭 ChronoBlocks 且 `hasOverride()` 返回 false，世界的内部时间**仍然是那个冻结的值**
- `blockExternalTimeUpdate()` Mixin 只在 `hasOverride()` 为 true 时才阻止 `setTime()` 调用
- 这导致时间被永久锁定，无法通过命令或正常机制恢复

#### 3. ❌ **`blockExternalTimeUpdate()` Mixin 阻止所有外部时间更新**
```java
@Inject(method = "setTime(JJZ)V", at = @At("HEAD"), cancellable = true)
private void blockExternalTimeUpdate(long time, long timeOfDay, boolean doDaylightCycle, CallbackInfo ci) {
    TimelineWorldController controller = TimelineWorldController.getInstance();
    if (!controller.hasOverride()) {
        return;  // ✅ 正确：当没有override时允许外部更新
    }
    if (controller.isInternalTimeUpdate()) {
        return;  // ✅ 正确：允许ChronoBlocks内部更新
    }
    ci.cancel();  // ✅ 正确：阻止外部更新（如/time set命令）
}
```

当 `hasOverride() = false` 时，此 Mixin 会返回，允许 `setTime()` 继续。但问题是在第 2 点创造的时间值已经是永久的。

## 解决方案

### ✅ 修复 1：改变 Mixin 注入位置到 HEAD
```java
@Inject(method = "getTimeOfDay()J", at = @At("HEAD"), cancellable = true)
private void overrideTimeOfDay(CallbackInfoReturnable<Long> cir) {
    TimelineWorldController controller = TimelineWorldController.getInstance();
    if (!controller.hasOverride()) {
        return;  // 让原始方法完全执行
    }
    TimeSample sample = controller.current();
    if (sample == null) {
        return;
    }

    // 直接返回 TimeSample 中的时间
    long tod = sample.getWorldTimeTicks() % 24000L;
    if (tod < 0)
        tod += 24000L;
    cir.setReturnValue(tod);
}
```

**优势：**
- `@At("HEAD")` 在方法最开始执行，完全控制返回值
- 当 `hasOverride()` 为 false 时，完全不干涉，让原始方法执行
- 确保时间同步逻辑集中在一处

### ✅ 修复 2：移除 `freezeMinecraftTime()` 中的 `world.setTime()` 调用
```java
private synchronized void freezeMinecraftTime() {
    ...
    if (hasOverride()) {
        frozenGameTime = currentSample.getWorldTimeTicks() % 24000L;
        LOGGER.info("[TimelineWorld] ✅ freezeMinecraftTime() - 使用采样时间作为冻结时间: {}", frozenGameTime);
        
        // ⭐ v4.0 关键修复：移除 world.setTime() 调用
        // 原因：setTime() 会永久改变世界的内部时间值
        // 即使后来 hasOverride() 变为 false，世界内部时间仍然是那个冻结的值
        // 应该由 Mixin 的 overrideTimeOfDay() 负责返回冻结的时间值
        // 而不是修改世界的实际时间
        
        /*
        try {
            internalTimeUpdate = true;
            world.setTime(frozenGameTime, frozenGameTime, false);
        } finally {
            internalTimeUpdate = false;
        }
        */
    }
    ...
}
```

**优势：**
- 世界的内部时间值保持不变
- 时间冻结完全由 Mixin 的 `overrideTimeOfDay()` 管理
- 关闭 ChronoBlocks 时，`hasOverride()` 返回 false，Mixin 停止干涉
- 世界恢复到真正的 Minecraft 时间流

### ✅ 修复 3：在 `clear()` 中调用 `syncClientTimeWithServer()`
```java
public synchronized void clear() {
    hasCustomSample = false;
    currentSample = null;

    // 恢复原版时间系统
    resumeMinecraftTime();
    
    // ⭐ 关键修复：显式同步客户端时间与服务器
    syncClientTimeWithServer();

    freezeTimeActive = false;
    savedDoDaylightCycle = null;

    LOGGER.info("[TimelineWorld] Cleared time override state");
}
```

## 时间恢复流程（修复后）

```
1. 用户关闭 ChronoBlocks 窗口
   ↓
2. ChronoBlocksScreen.close()
   ↓
3. forceDeactivateChronoBlocksMode() → chronoBlocksModeActive = false
   ↓
4. TimelineWorldController.clear()
   ├─ hasCustomSample = false
   ├─ currentSample = null
   ├─ resumeMinecraftTime() → 恢复 doDaylightCycle GameRule
   ├─ syncClientTimeWithServer() → 同步客户端时间
   └─ freezeTimeActive = false
   ↓
5. 客户端 Mixin 检查状态
   ├─ WorldMixin.overrideTimeOfDay()
   │  └─ hasOverride() == false → 完全不干涉，使用原始时间
   ├─ ClientWorldMixin.onTickTime()
   │  └─ isChronoBlocksModeActive() == false → tickTime() 正常执行
   └─ ClientWorldMixin.blockExternalTimeUpdate()
      └─ hasOverride() == false → 允许 /time set 命令
   ↓
6. ✅ Minecraft 时间恢复正常，所有命令生效
```

## 测试场景

### ✅ 场景 1：关闭 ChronoBlocks 后时间恢复
1. 打开 ChronoBlocks
2. 在时间轨道上设置关键帧（例如：改变时间为 noon）
3. 关闭 ChronoBlocks 窗口
4. **预期结果**：世界时间恢复到关闭前的状态

### ✅ 场景 2：`/time set` 命令生效
1. 打开 ChronoBlocks
2. 在时间轨道上设置关键帧（冻结时间）
3. 关闭 ChronoBlocks 窗口
4. 执行 `/time set noon`
5. **预期结果**：世界时间立即变为 noon，命令生效

### ✅ 场景 3：天空效果同步恢复
1. 打开 ChronoBlocks，设置自定义天空颜色
2. 关闭 ChronoBlocks
3. **预期结果**：天空恢复到 Minecraft 原生颜色和亮度

## 文件修改总结

| 文件 | 改动 | 说明 |
|------|------|------|
| `WorldMixin.java` | `@At("RETURN")` → `@At("HEAD")` | 在方法开始处而不是结束处拦截 |
| `WorldMixin.java` | 简化返回值逻辑 | 直接返回 TimeSample 时间，避免复杂计算 |
| `TimelineWorldController.java` | 注释 `world.setTime()` | 移除永久修改世界时间的调用 |
| `TimelineWorldController.java` | 添加 `syncClientTimeWithServer()` | 在关闭时显式同步时间 |

## 版本历史

- **v1.0**：初始实现，使用 `@At("RETURN")` + `world.setTime()`
- **v2.0**：添加 `maintainFrozenTime()` 维持冻结，但 `setTime()` 仍然是问题
- **v3.0**：优化冻结逻辑，但基本问题未解决
- **v3.5**：优化减少 `setTime()` 调用频率，但根本问题仍然存在
- **v4.0** ✅：**根本修复** - 移除 `setTime()` 调用，改变 Mixin 位置，完全恢复时间
