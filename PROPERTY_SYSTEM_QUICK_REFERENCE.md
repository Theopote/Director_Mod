# 🚀 ChronoBlocks 属性系统快速参考

> **5分钟上手新属性系统**

---

## 📦 核心组件速查

| 组件 | 位置 | 用途 |
|------|------|------|
| `PropertyAdapter` | `ui.adapters` | 适配器接口 |
| `PropertyAdapterRegistry` | `ui.adapters` | 注册表 |
| `PropertyContext` | `ui.adapters.context` | 上下文 |
| `BasePropertyAdapter` | `ui.adapters` | 基础适配器 |
| `PropertyAdapterModule` | `ui.adapters` | 注册模块 |
| `ModernPropertiesPanel` | `ui.panels` | 属性面板 |

---

## ⚡ 快速开始

### 1. 初始化

```java
// 应用启动时
PropertyAdapterModule.initialize();
```

### 2. 创建适配器

```java
public class MyAdapter extends BasePropertyAdapter<MyType> {
    @Override
    protected boolean doRenderProperties(MyType target) {
        boolean changed = false;
        
        renderSectionHeader("属性");
        changed |= renderFloatProperty("值", 
            target::getValue, 
            target::setValue,
            0.1f, 0.0f, 100.0f);
        
        return changed;
    }
    
    @Override
    public Class<MyType> getTargetType() {
        return MyType.class;
    }
}
```

### 3. 注册适配器

```java
PropertyAdapterModule.registerCustomAdapter(new MyAdapter());
```

### 4. 使用

```java
// 选择对象
SelectionManager.get().setCurrentSelection(myObject);

// 自动渲染属性编辑器
```

---

## 🎨 便利方法速查

```java
// 浮点数
renderFloatProperty("名称", getter, setter, speed, min, max);

// 整数
renderIntProperty("名称", getter, setter, speed, min, max);

// 布尔值
renderBoolProperty("名称", getter, setter);

// 文本
renderTextProperty("名称", getter, setter);

// 下拉框
renderComboProperty("名称", getter, setter, "选项1", "选项2");

// RGB颜色
renderColorProperty("名称", getter, setter);

// RGBA颜色
renderColorAlphaProperty("名称", getter, setter);

// 节标题
renderSectionHeader("节标题");

// 只读文本
renderReadOnlyText("标签", "值");

// 帮助提示
renderHelpMarker("提示文本");
```

---

## 🔍 查找适配器

```java
// 方式1: 通过类型
PropertyAdapter<Keyframe> adapter = 
    PropertyAdapterRegistry.get().findAdapter(Keyframe.class);

// 方式2: 通过对象
PropertyAdapter<Keyframe> adapter = 
    PropertyAdapterRegistry.get().findAdapter(keyframe);

// 方式3: 通过上下文
PropertyContext context = new PropertyContext();
context.setSelectedObject(keyframe);
// 自动匹配适配器
```

---

## 📊 统计信息

```java
// 获取统计
var stats = PropertyAdapterRegistry.get().getStatistics();

// 活跃适配器
int active = stats.getActiveAdapters();

// 缓存命中率
double hitRate = stats.getCacheHitRate();

// 支持类型
Set<Class<?>> types = PropertyAdapterRegistry.get().getSupportedTypes();
```

---

## 🎯 完整示例

```java
// ==================== 创建适配器 ====================
public class KeyframeAdapter extends BasePropertyAdapter<Keyframe> {
    
    @Override
    protected boolean doRenderProperties(Keyframe kf) {
        boolean changed = false;
        
        renderSectionHeader("⏱ 时间");
        changed |= renderFloatProperty("时间点",
            kf::getTime,
            kf::setTime,
            0.01f, 0.0f, Float.MAX_VALUE);
        
        renderSectionHeader("📍 位置");
        float[] pos = kf.getPosition();
        changed |= renderFloatProperty("X",
            () -> pos[0],
            v -> kf.setPosition(v, pos[1], pos[2]),
            0.1f, -100.0f, 100.0f);
        
        return changed;
    }
    
    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
    
    @Override
    public int getPriority() {
        return 100; // 高优先级
    }
}

// ==================== 注册适配器 ====================
public class AppInitializer {
    public static void init() {
        PropertyAdapterModule.initialize();
        PropertyAdapterModule.registerCustomAdapter(new KeyframeAdapter());
    }
}

// ==================== 使用属性面板 ====================
public class UIManager {
    private final ModernPropertiesPanel propertiesPanel;
    
    public UIManager() {
        this.propertiesPanel = new ModernPropertiesPanel();
    }
    
    public void render() {
        propertiesPanel.render();
    }
}

// ==================== 选择对象 ====================
Keyframe keyframe = ...;
SelectionManager.get().setCurrentSelection(keyframe);
// → 自动显示关键帧属性编辑器
```

---

## ⚙️ 配置选项

```java
// 标签宽度
adapter.setLabelWidth(150.0f);

// 紧凑模式
adapter.setCompactMode(true);

// 调试模式
adapter.setDebugMode(true);
```

---

## 🔧 调试技巧

```java
// 打印系统状态
PropertyAdapterModule.printStatus();

// 查看缓存状态
PropertyAdapterRegistry.get().getStatistics();

// 清除缓存
PropertyAdapterRegistry.get().clearCache();

// 刷新适配器
PropertyContext context = ...;
context.refreshAdapter();
```

---

## 📋 接口方法速查

### PropertyAdapter（必需实现）

```java
boolean renderProperties(T target);        // 渲染UI
void onPropertyChanged(T target);         // 变更回调
Class<T> getTargetType();                // 目标类型
```

### PropertyAdapter（可选实现）

```java
void initialize();                        // 初始化
void update(T target, float deltaTime);  // 每帧更新
void dispose();                          // 清理
boolean validate(T target);              // 验证
int getPriority();                       // 优先级
boolean supports(Object obj);            // 类型支持
String getDisplayName();                 // 显示名称
String getDescription();                 // 描述
boolean isEnabled();                     // 是否启用
```

---

## 🎨 BasePropertyAdapter（便利方法）

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `doRenderProperties(T)` | `boolean` | 子类实现（必需） |
| `renderSectionHeader(String)` | `void` | 渲染节标题 |
| `renderFloatProperty(...)` | `boolean` | 浮点数属性 |
| `renderIntProperty(...)` | `boolean` | 整数属性 |
| `renderBoolProperty(...)` | `boolean` | 布尔属性 |
| `renderTextProperty(...)` | `boolean` | 文本属性 |
| `renderComboProperty(...)` | `boolean` | 下拉框属性 |
| `renderColorProperty(...)` | `boolean` | RGB颜色 |
| `renderColorAlphaProperty(...)` | `boolean` | RGBA颜色 |
| `renderReadOnlyText(...)` | `void` | 只读文本 |
| `renderHelpMarker(String)` | `void` | 帮助提示 |

---

## 💡 最佳实践

### ✅ 推荐

```java
// 1. 使用 BasePropertyAdapter
public class MyAdapter extends BasePropertyAdapter<MyType> { }

// 2. 使用方法引用
renderFloatProperty("值", target::getValue, target::setValue, ...);

// 3. 添加验证
@Override
public boolean validate(MyType target) {
    return target.getValue() >= 0;
}

// 4. 使用节标题
renderSectionHeader("基本属性");
```

### ❌ 避免

```java
// 1. 不要在 renderProperties 中修改对象
target.setValue(100); // 错误！

// 2. 不要忘记返回 changed
return false; // 总是返回 false 是错误的

// 3. 不要在构造函数中做耗时操作
public MyAdapter() {
    loadLargeResource(); // 错误！应该在 initialize() 中
}
```

---

## 🐛 常见问题

### Q: 找不到适配器？
A: 检查是否调用 `PropertyAdapterModule.initialize()`

### Q: 属性不更新？
A: 确保 `renderProperties()` 正确返回 `changed` 状态

### Q: 性能问题？
A: 查看缓存命中率：`getStatistics().getCacheHitRate()`

### Q: 如何调试？
A: 使用 `PropertyAdapterModule.printStatus()`

---

## 📚 更多资源

- **完整指南**: `PROPERTY_SYSTEM_GUIDE_V4_5.md`
- **集成报告**: `PROPERTY_SYSTEM_INTEGRATION_V4_5.md`
- **示例代码**: `SimpleKeyframeAdapter.java`

---

**版本**: v4.5  
**更新日期**: 2025-10-17  
**ChronoBlocks Team**

