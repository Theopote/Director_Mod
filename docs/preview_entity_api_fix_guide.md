# PreviewActorEntity API 修复指南

## ⚠️ 当前状态

`PreviewActorEntity` 类需要实现 Minecraft 1.21 的 Entity 抽象方法：
- `readCustomData(ReadView)`
- `writeCustomData(WriteView)`

## 🔍 如何找到正确的类型

### 方法1：查看 Minecraft 源码

1. 在 IDE 中打开 `net.minecraft.entity.Entity` 类
2. 查找 `readCustomData` 和 `writeCustomData` 方法的签名
3. 查看 `ReadView` 和 `WriteView` 的完整类型路径

### 方法2：使用 IDE 的自动补全

1. 在 `PreviewActorEntity` 类中输入 `@Override`
2. 让 IDE 自动生成需要实现的方法
3. IDE 会自动填充正确的类型

### 方法3：查看编译错误

编译错误会显示需要实现的抽象方法签名，例如：
```
The type PreviewActorEntity must implement the inherited abstract method Entity.readCustomData(ReadView)
```

## 📝 可能的实现方式

根据 Minecraft 1.21 的 API，可能的实现如下：

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

或者：

```java
@Override
protected void readCustomData(net.minecraft.entity.Entity.ReadView readView) {
    // 不读取（预览实体不保存）
}

@Override
protected void writeCustomData(net.minecraft.entity.Entity.WriteView writeView) {
    // 不写入（不进存档）
}
```

## ✅ 修复步骤

1. 在 IDE 中打开 `PreviewActorEntity.java`
2. 使用 IDE 的"实现抽象方法"功能（通常是 Alt+Enter 或右键菜单）
3. IDE 会自动生成正确的方法签名
4. 在方法体中添加注释：`// 不读取（预览实体不保存）` 和 `// 不写入（不进存档）`

## 🎯 临时解决方案

如果暂时无法确定正确的类型，可以：

1. 使用 IDE 的自动补全功能
2. 或者查看项目中其他继承 Entity 的类作为参考
3. 或者暂时注释掉 `PreviewActorEntity` 类，使用现有的 `PreviewEntity` 包装类

## 📋 检查清单

- [ ] 找到 `ReadView` 和 `WriteView` 的正确类型
- [ ] 实现 `readCustomData` 方法
- [ ] 实现 `writeCustomData` 方法
- [ ] 确保代码能够编译通过
- [ ] 测试预览实体的创建和销毁

