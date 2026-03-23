# PreviewActorEntity IDE 修复指南

## ⚠️ 当前状态

`PreviewActorEntity` 类需要实现 Minecraft 1.21 的 Entity 抽象方法：
- `readCustomData(ReadView)`
- `writeCustomData(WriteView)`

## 🔧 使用 IDE 自动生成（推荐）

### IntelliJ IDEA / Android Studio

1. **打开文件**：`src/main/java/com/director/actor/preview/PreviewActorEntity.java`

2. **定位到类声明**：
   ```java
   public class PreviewActorEntity extends Entity {
   ```

3. **使用快捷键**：
   - 将光标放在类名 `PreviewActorEntity` 上
   - 按 `Alt + Enter`（Windows/Linux）或 `Option + Enter`（Mac）
   - 选择 **"Implement methods"** 或 **"实现抽象方法"**

4. **选择要实现的方法**：
   - 在弹出窗口中，勾选 `readCustomData` 和 `writeCustomData`
   - 点击 **"OK"**

5. **IDE 会自动生成正确的方法签名**，例如：
   ```java
   @Override
   protected void readCustomData(Entity.ReadView readView) {
       // TODO: 实现方法
   }

   @Override
   protected void writeCustomData(Entity.WriteView writeView) {
       // TODO: 实现方法
   }
   ```

6. **添加实现**：
   ```java
   @Override
   protected void readCustomData(Entity.ReadView readView) {
       // 不读取（预览实体不保存）
   }

   @Override
   protected void writeCustomData(Entity.WriteView writeView) {
       // 不写入（不进存档）
   }
   ```

### Eclipse

1. **打开文件**：`src/main/java/com/director/actor/preview/PreviewActorEntity.java`

2. **定位到类声明**：
   ```java
   public class PreviewActorEntity extends Entity {
   ```

3. **使用快捷键**：
   - 将光标放在类名 `PreviewActorEntity` 上
   - 按 `Ctrl + 1`（Windows/Linux）或 `Cmd + 1`（Mac）
   - 选择 **"Add unimplemented methods"** 或 **"添加未实现的方法"**

4. **IDE 会自动生成正确的方法签名**

5. **添加实现**（同上）

### Visual Studio Code

1. **打开文件**：`src/main/java/com/director/actor/preview/PreviewActorEntity.java`

2. **定位到类声明**：
   ```java
   public class PreviewActorEntity extends Entity {
   ```

3. **使用命令面板**：
   - 按 `Ctrl + Shift + P`（Windows/Linux）或 `Cmd + Shift + P`（Mac）
   - 输入 "Java: Implement Abstract Methods"
   - 选择要实现的方法

4. **IDE 会自动生成正确的方法签名**

5. **添加实现**（同上）

## 🔍 手动查找类型（如果 IDE 无法自动生成）

如果 IDE 无法自动生成，可以手动查找正确的类型：

1. **查看 Minecraft 源码**：
   - 在 IDE 中打开 `net.minecraft.entity.Entity` 类
   - 查找 `readCustomData` 和 `writeCustomData` 方法的签名
   - 查看 `ReadView` 和 `WriteView` 的完整类型路径

2. **查看编译错误**：
   - 编译项目
   - 查看错误信息，通常会显示需要实现的抽象方法签名

3. **可能的类型**：
   - `Entity.ReadView` / `Entity.WriteView`
   - `net.minecraft.entity.data.DataTracker.ReadView` / `WriteView`
   - 或其他类型（取决于 Minecraft 1.21 的实际 API）

## ✅ 验证修复

修复后，应该：
- ✅ 没有编译错误
- ✅ 方法签名正确
- ✅ 方法体为空或包含注释

## 📋 检查清单

- [ ] 使用 IDE 自动生成方法签名
- [ ] 添加实现（空方法体 + 注释）
- [ ] 确保代码能够编译通过
- [ ] 测试预览实体的创建和销毁

## 🎯 最终代码示例

```java
@Override
protected void readCustomData(Entity.ReadView readView) {
    // 不读取（预览实体不保存）
}

@Override
protected void writeCustomData(Entity.WriteView writeView) {
    // 不写入（不进存档）
}
```

**注意**：实际的方法签名可能因 Minecraft 版本而异，请使用 IDE 自动生成以确保正确。

