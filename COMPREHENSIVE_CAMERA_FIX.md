# 全面相机系统修复方案

## 发现的根本问题

### 问题 1：玩家实体位置/旋转未同步
当 owner=TIMELINE 时：
- ✅ 我们在 HEAD/TAIL 设置了 Camera 的位置/旋转
- ❌ 但玩家实体的位置/旋转（player.getPos(), player.getYaw(), player.getPitch()）还是旧的
- ❌ Camera.update() 的原版逻辑会基于玩家实体计算，导致不一致

### 问题 2：拖动播放头结束时的状态丢失
- 拖动结束时调用 `bakeCurrentSampleIntoGameAndExit()` 只是释放控制权
- 但当前 CameraRuntime 的状态没有同步到玩家实体
- 导致松开后跳回玩家实体的旧位置

### 问题 3：Camera.update() 内部逻辑仍然执行
- 即使我们在 HEAD 设置了相机状态，Camera.update() 内部的原版逻辑仍然会执行
- 原版逻辑会基于 player 实体的位置/旋转重新计算相机
- 这会导致冲突

## 解决方案

### 方案 1：同步玩家实体位置/旋转（关键修复）

当 owner=TIMELINE 时，需要：
1. 在 HEAD 处同步玩家实体位置/旋转到 CameraRuntime 的状态
2. 确保 Camera.update() 的原版逻辑看到的是正确的位置

### 方案 2：在 TAIL 处同步状态到玩家实体

当拖动结束时，需要：
1. 将 CameraRuntime.currentSample 的状态同步到玩家实体
2. 然后释放控制权，这样玩家继续从当前位置移动

### 方案 3：阻止 MouseMixin 在时间线控制时修改视角

检查 CameraRuntime.owner，如果是 TIMELINE，阻止 `changeLookDirection`。

