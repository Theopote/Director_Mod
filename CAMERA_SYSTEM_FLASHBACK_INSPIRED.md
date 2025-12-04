# 相机系统 - 参考 Flashback 的实现理念

## Flashback 的核心设计原则

根据 [Flashback](https://github.com/Moulberry/Flashback) 项目（Minecraft 录制/回放模组），相机系统的核心原则是：

1. **单一真相源**：所有相机状态都存储在一个统一的状态管理中
2. **完全控制**：录制/回放时完全控制相机，不允许原版逻辑干扰
3. **状态一致性**：录制时的状态和回放时的状态完全一致

## 我们的实现（基于 Flashback 理念）

### 核心架构

```
┌─────────────────────────────────────────────────┐
│        CameraRuntime.currentSample              │
│           (唯一的真相源)                         │
└─────────────────────────────────────────────────┘
         ↑                    ↓
   写入状态              读取状态并应用
         │                    │
    ┌────┴────┐         ┌────┴────┐
    │         │         │         │
Timeline   玩家输入  CameraMixin  GameRendererMixin
采样写入   同步写入   应用位置   应用 FOV
           (TAIL)     旋转      Roll
```

### 关键实现点

1. **CameraRuntime 是唯一的真相源**
   - 所有相机状态都存储在 `currentSample` 中
   - 无论是玩家控制还是时间线控制，都使用同一份数据

2. **双重应用机制（确保完全控制）**
   - HEAD 处：如果 owner=TIMELINE，先应用 CameraRuntime 状态
   - TAIL 处：如果 owner=TIMELINE，再次应用 CameraRuntime 状态（确保原版逻辑没有修改）

3. **输入锁定机制**
   - 当 owner=TIMELINE 时，自动锁定所有玩家输入
   - 当 owner=PLAYER 时，自动解锁输入

4. **状态同步**
   - owner=PLAYER 时，原生 Camera 由原版逻辑控制，然后同步到 CameraRuntime
   - owner=TIMELINE 时，原生 Camera 完全由 CameraRuntime 控制

## 与 Flashback 的相似之处

1. ✅ **统一的状态管理**：所有相机状态都在一个地方管理
2. ✅ **完全控制机制**：时间线控制时，完全接管相机
3. ✅ **状态一致性**：录制（关键帧）和回放使用同一套数据

## 当前实现的优势

1. **单一数据源**：CameraRuntime.currentSample 是唯一的真相源
2. **双重保险**：HEAD 和 TAIL 都应用状态，确保原版逻辑无法干扰
3. **无缝切换**：玩家控制和时间线控制之间可以无缝切换
4. **输入锁定**：自动管理输入锁定，确保时间线控制时玩家无法移动

---

**参考来源**：[Flashback by Moulberry](https://github.com/Moulberry/Flashback)

