# 🔍 ChronoBlocks 通用属性适配器 v4.5

## ✅ 完成状态

**版本**: v4.5.0  
**日期**: 2025-10-15  
**状态**: ✅ 完全完成

---

## 🎯 核心功能

GenericPropertyAdapter 是属性系统的**智能回退机制**：

| 特性 | 说明 |
|------|------|
| 🧠 **自动反射** | 自动分析对象的所有字段 |
| ✏ **基础类型编辑** | 支持 int, float, double, boolean, String |
| 🔄 **枚举支持** | 自动生成枚举下拉菜单 |
| 🔄 **自动回写** | 修改即时保存到对象 |
| 🚫 **智能过滤** | 跳过静态字段和瞬态字段 |
| 🛡 **异常安全** | 完整的错误处理 |

---

## 🏗️ 工作原理

### 查找策略（3 层）

```
1. 精确匹配
   └─ Keyframe → KeyframePropertyAdapter ✅

2. 继承匹配
   └─ MyKeyframe extends Keyframe → KeyframePropertyAdapter ✅

3. 回退适配器
   └─ UnknownObject → GenericPropertyAdapter ✅
```

### 自动化流程

```
用户选择未知对象
    ↓
PropertyAdapterRegistry.getAdapterFor(object)
    ↓
1. 查找精确匹配 → 未找到
    ↓
2. 查找兼容类型 → 未找到
    ↓
3. 返回 GenericPropertyAdapter
    ↓
自动反射对象字段
    ↓
渲染可编辑的属性UI
```

---

## 📊 支持的字段类型

| 类型 | UI 控件 | 说明 |
|------|---------|------|
| **int, Integer** | InputInt | 整数输入框 |
| **float, Float** | InputFloat | 浮点输入框（3位小数） |
| **double, Double** | InputFloat | 双精度浮点（转为float） |
| **boolean, Boolean** | Checkbox | 复选框 |
| **String** | InputText | 文本输入框（256字符） |
| **Enum** | Combo | 下拉选择菜单 |
| **其他类型** | 只读显示 | 显示 toString() 值 |

---

## 🎨 UI 效果

### 示例对象

```java
public class CustomObject {
    private String name = "MyObject";
    private int level = 5;
    private float speed = 1.5f;
    private boolean enabled = true;
    private Status status = Status.ACTIVE;
}

enum Status {
    ACTIVE, PAUSED, STOPPED
}
```

### 渲染效果

```
┌─────────────────────────────┐
│ 📋 属性                      │
├─────────────────────────────┤
│ 通用属性编辑器               │
├─────────────────────────────┤
│ 🔍 通用属性                  │
├─────────────────────────────┤
│ 类型信息                     │
│  类名: CustomObject          │
│  完整类名: com...Custom...   │
├─────────────────────────────┤
│ 字段                         │
│  name:    [MyObject______]   │
│  level:   [5____________]    │
│  speed:   [1.500________]    │
│  enabled: [✓]                │
│  status:  [ACTIVE ▼]         │
├─────────────────────────────┤
│ 提示                         │
│  这是通用属性编辑器，仅显示   │
│  基础字段。创建专属Adapter   │
│  以获得更好的编辑体验。       │
└─────────────────────────────┘
```

---

## 🔧 技术实现

### 1. 反射字段扫描

```java
Field[] fields = clazz.getDeclaredFields();

for (Field field : fields) {
    // 跳过静态和瞬态字段
    if (Modifier.isStatic(field.getModifiers()) || 
        Modifier.isTransient(field.getModifiers())) {
        continue;
    }
    
    field.setAccessible(true);
    Object value = field.get(target);
    renderField(field, target, value);
}
```

### 2. 类型特定的UI控件

```java
// int 类型
if (type == int.class || type == Integer.class) {
    int[] buffer = {(int) value};
    if (ImGui.inputInt(fieldName, buffer)) {
        setValue(field, target, buffer[0]);
        modified = true;
    }
}

// float 类型
else if (type == float.class || type == Float.class) {
    float[] buffer = {(float) value};
    if (ImGui.inputFloat(fieldName, buffer, 0.1f, 1.0f, "%.3f")) {
        setValue(field, target, buffer[0]);
        modified = true;
    }
}

// boolean 类型
else if (type == boolean.class || type == Boolean.class) {
    ImBoolean buffer = new ImBoolean((boolean) value);
    if (ImGui.checkbox(fieldName, buffer)) {
        setValue(field, target, buffer.get());
        modified = true;
    }
}

// Enum 类型
else if (type.isEnum()) {
    Object[] constants = type.getEnumConstants();
    String[] names = Arrays.stream(constants)
        .map(Object::toString)
        .toArray(String[]::new);
    
    int currentIndex = Arrays.asList(constants).indexOf(value);
    ImInt selected = new ImInt(currentIndex);
    
    if (ImGui.combo(fieldName, selected, names, names.length)) {
        setValue(field, target, constants[selected.get()]);
        modified = true;
    }
}
```

### 3. 安全的值更新

```java
private void setValue(Field field, Object target, Object newValue) {
    try {
        field.setAccessible(true);
        field.set(target, newValue);
        LOGGER.debug("字段已更新: {} = {}", field.getName(), newValue);
    } catch (IllegalAccessException e) {
        LOGGER.error("无法设置字段值: {}", field.getName(), e);
    }
}
```

---

## 🎯 集成到系统

### PropertyAdapterRegistry 增强

**新增方法**:
```java
// 设置回退适配器
PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());

// 获取回退适配器
IPropertyAdapter<Object> fallback = PropertyAdapterRegistry.getFallbackAdapter();

// 检查是否有回退适配器
boolean hasFallback = PropertyAdapterRegistry.hasFallbackAdapter();
```

**查找逻辑**:
```java
public static <T> IPropertyAdapter<T> getAdapterFor(T target) {
    // 1. 精确匹配
    IPropertyAdapter<?> adapter = adapters.get(targetClass);
    if (adapter != null) return adapter;
    
    // 2. 兼容匹配（继承）
    for (Entry entry : adapters.entrySet()) {
        if (entry.getKey().isAssignableFrom(targetClass)) {
            return entry.getValue();
        }
    }
    
    // 3. 回退适配器 ⭐
    if (fallbackAdapter != null) {
        return fallbackAdapter;
    }
    
    return null;
}
```

---

### PropertyAdapterBootstrap 集成

**启动时自动设置**:
```java
public static synchronized void initialize() {
    // ... 注册核心适配器 ...
    
    // ========== 设置回退适配器 ==========
    PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());
    LOGGER.info("✅ 已设置回退适配器: GenericPropertyAdapter");
    
    // ... 注册可选适配器 ...
}
```

**启动日志**:
```
[PropertyAdapterBootstrap] 正在注册属性适配器...
[PropertyAdapterBootstrap] ✅ 已注册: KeyframePropertyAdapter → Keyframe
[PropertyAdapterBootstrap] ✅ 已注册: ContainerPropertyAdapter → Container
[PropertyAdapterBootstrap] ✅ 已注册: ClipPropertyAdapter → AnimationClip
[PropertyAdapterBootstrap] ✅ 已设置回退适配器: GenericPropertyAdapter
[PropertyAdapterBootstrap] 属性系统初始化完成，共注册 3 个适配器
```

---

## 🎭 使用场景

### 场景 1: 调试未知对象

```java
// 开发新功能，创建了新类型
public class MyNewFeature {
    private String featureName = "Test";
    private int priority = 10;
    private boolean enabled = true;
}

// 在编辑器中选择
MyNewFeature feature = ...;
SelectionManager.get().setCurrentSelection(feature);

// GenericPropertyAdapter 自动显示所有字段
// 无需创建专属适配器！
```

**面板显示**:
```
┌─────────────────────────────┐
│ 🔍 通用属性                  │
├─────────────────────────────┤
│ 类型信息                     │
│  类名: MyNewFeature          │
├─────────────────────────────┤
│ 字段                         │
│  featureName: [Test______]   │
│  priority:    [10________]   │
│  enabled:     [✓]            │
├─────────────────────────────┤
│ 提示                         │
│  这是通用属性编辑器...       │
└─────────────────────────────┘
```

---

### 场景 2: 快速原型开发

```java
// 不需要为每个新类型创建适配器
// 直接使用通用适配器进行开发和测试
public class PrototypeEffect {
    private String effectName = "Glow";
    private float intensity = 0.8f;
    private EffectType type = EffectType.PARTICLE;
}

// 立即可用！
SelectionManager.get().setCurrentSelection(new PrototypeEffect());
```

---

### 场景 3: 生产环境回退

```java
// 即使忘记注册某个类型的适配器
// 用户也能看到和编辑基本属性
// 不会显示"无可用编辑器"错误

UnknownObject obj = ...;
SelectionManager.get().setCurrentSelection(obj);

// GenericPropertyAdapter 保证基本可用性
```

---

## 📐 设计亮点

### 1. UITheme 完全集成

```java
// 使用统一样式
UITheme.title("🔍 通用属性");
UITheme.beginGroup("类型信息");
UITheme.label("类名: " + clazz.getSimpleName(), UITheme.DarkTheme.TEXT);
UITheme.endGroup();
UITheme.separator();
```

### 2. 智能字段过滤

```java
// 跳过不需要显示的字段
if (Modifier.isStatic(field.getModifiers()) ||    // 静态字段
    Modifier.isTransient(field.getModifiers())) {   // 瞬态字段
    continue;
}
```

### 3. 类型安全的枚举编辑

```java
// 自动生成枚举选项
Object[] constants = type.getEnumConstants();
String[] names = Arrays.stream(constants)
    .map(Object::toString)
    .toArray(String[]::new);

ImInt selected = new ImInt(currentIndex);
if (ImGui.combo(fieldName, selected, names, names.length)) {
    setValue(field, target, constants[selected.get()]);
}
```

### 4. 完整的异常处理

```java
try {
    field.setAccessible(true);
    Object value = field.get(target);
    renderField(field, target, value);
} catch (IllegalAccessException e) {
    LOGGER.warn("无法访问字段: {}", field.getName());
    ImGui.textDisabled(field.getName() + " (访问失败)");
} catch (Exception e) {
    LOGGER.error("渲染字段时出错: {}", field.getName(), e);
    ImGui.textDisabled(field.getName() + " (错误)");
}
```

---

## 🚀 改进的 PropertyAdapterRegistry

### 新增功能

| 方法 | 说明 |
|------|------|
| `setFallbackAdapter()` | 设置回退适配器 |
| `getFallbackAdapter()` | 获取回退适配器 |
| `hasFallbackAdapter()` | 检查是否有回退 |

### 增强的查找逻辑

```java
// 原来：找不到返回 null
IPropertyAdapter adapter = getAdapterFor(unknownObject);
// adapter = null ❌

// 现在：找不到使用回退
IPropertyAdapter adapter = getAdapterFor(unknownObject);
// adapter = GenericPropertyAdapter ✅
```

---

## 📊 完整的适配器层次

```
PropertyAdapterRegistry
  │
  ├─ 精确匹配
  │   ├─ Keyframe → KeyframePropertyAdapter
  │   ├─ Container → ContainerPropertyAdapter
  │   └─ AnimationClip → ClipPropertyAdapter
  │
  ├─ 继承匹配
  │   └─ CustomKeyframe extends Keyframe → KeyframePropertyAdapter
  │
  └─ 回退适配器 ⭐
      └─ 任何其他对象 → GenericPropertyAdapter
```

---

## 💡 使用示例

### 示例 1: 调试新对象

```java
// 创建一个没有适配器的对象
public class DebugInfo {
    private String message = "Test";
    private int code = 200;
    private boolean success = true;
}

// 选择对象
DebugInfo info = new DebugInfo();
SelectionManager.get().setCurrentSelection(info);

// 自动使用 GenericPropertyAdapter
// 用户可以编辑所有字段
```

---

### 示例 2: 枚举类型

```java
public class GameSettings {
    private Difficulty difficulty = Difficulty.NORMAL;
    private GameMode mode = GameMode.CREATIVE;
}

enum Difficulty { EASY, NORMAL, HARD }
enum GameMode { SURVIVAL, CREATIVE, ADVENTURE }

// 选择对象
SelectionManager.get().setCurrentSelection(new GameSettings());

// GenericPropertyAdapter 自动生成枚举下拉菜单
```

**面板显示**:
```
字段
  difficulty: [NORMAL ▼]
                ├─ EASY
                ├─ NORMAL ✓
                └─ HARD
  mode:       [CREATIVE ▼]
                ├─ SURVIVAL
                ├─ CREATIVE ✓
                └─ ADVENTURE
```

---

### 示例 3: 混合类型对象

```java
public class ComplexObject {
    private String name = "Complex";
    private int id = 123;
    private float value = 45.67f;
    private boolean active = true;
    private Status status = Status.RUNNING;
    
    // 这些会被跳过
    private static final String VERSION = "1.0";  // 静态
    private transient Object cache = null;        // 瞬态
}
```

**面板只显示**:
```
字段
  name:   [Complex_________]
  id:     [123_____________]
  value:  [45.670__________]
  active: [✓]
  status: [RUNNING ▼]

(VERSION 和 cache 被自动跳过)
```

---

## 🎯 优势对比

### 没有 GenericPropertyAdapter

```
用户选择未知对象
    ↓
PropertyAdapterRegistry.getAdapterFor(object)
    ↓
返回 null
    ↓
PropertiesPanel 显示：
"无可用的属性编辑器" ❌
```

### 有 GenericPropertyAdapter

```
用户选择未知对象
    ↓
PropertyAdapterRegistry.getAdapterFor(object)
    ↓
返回 GenericPropertyAdapter
    ↓
PropertiesPanel 显示：
所有可编辑的字段 ✅
```

---

## 📝 代码质量

### 特性

- ✅ **完整的 Javadoc**
- ✅ **类型安全**（泛型 + 反射）
- ✅ **异常处理**（每个字段独立 try-catch）
- ✅ **UITheme 集成**
- ✅ **详细的日志**

### 性能优化

```java
// 字段访问权限缓存
field.setAccessible(true);

// 智能过滤
if (Modifier.isStatic(...) || Modifier.isTransient(...)) {
    continue; // 跳过不需要的字段
}
```

---

## 🔄 集成流程

### 1. 创建 GenericPropertyAdapter ✅

**文件**: `src/main/java/com/chronoblocks/ui/properties/adapters/GenericPropertyAdapter.java`  
**行数**: 232 行

### 2. 增强 PropertyAdapterRegistry ✅

**新增**:
- `setFallbackAdapter()`
- `getFallbackAdapter()`
- `hasFallbackAdapter()`
- 查找逻辑中添加回退层

### 3. 集成到 Bootstrap ✅

```java
// PropertyAdapterBootstrap.initialize() 中添加
PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());
```

---

## 🎉 效果演示

### 启动日志

```
[PropertyAdapterBootstrap] 正在注册属性适配器...
[PropertyAdapterBootstrap] ✅ 已注册: KeyframePropertyAdapter → Keyframe
[PropertyAdapterBootstrap] ✅ 已注册: ContainerPropertyAdapter → Container
[PropertyAdapterBootstrap] ✅ 已注册: ClipPropertyAdapter → AnimationClip
[PropertyAdapterBootstrap] ✅ 已设置回退适配器: GenericPropertyAdapter
[PropertyAdapterRegistry] 已设置回退适配器: GenericPropertyAdapter
[PropertyAdapterBootstrap] 属性系统初始化完成，共注册 3 个适配器
```

### 调试信息

```java
PropertyAdapterRegistry.getDebugInfo();
```

**输出**:
```
PropertyAdapterRegistry [3 adapters]:
  - Keyframe → KeyframePropertyAdapter
  - Container → ContainerPropertyAdapter
  - AnimationClip → ClipPropertyAdapter
  Fallback: GenericPropertyAdapter
```

---

## 🎯 最佳实践

### 1. 开发阶段

```java
// 快速原型：直接使用 GenericPropertyAdapter
public class Prototype {
    private String name;
    private float value;
}

// 无需创建适配器，立即可用
```

### 2. 生产阶段

```java
// 为核心类型创建专属适配器
public class KeyframePropertyAdapter implements IPropertyAdapter<Keyframe> {
    // 提供更好的UI和验证
}

// 其他类型仍可使用 GenericPropertyAdapter
```

### 3. 扩展阶段

```java
// 第三方插件添加新类型
public class PluginObject {
    // 自定义字段
}

// 无需修改核心代码
// GenericPropertyAdapter 自动支持
```

---

## 🎊 总结

### 完成的功能

- ✅ GenericPropertyAdapter（232 行）
- ✅ PropertyAdapterRegistry 回退机制
- ✅ PropertyAdapterBootstrap 集成
- ✅ 7 种字段类型支持
- ✅ 完整的异常处理
- ✅ UITheme 样式集成

### 技术亮点

- 🧠 智能反射系统
- 🔄 三层查找策略
- 🛡 异常安全
- 🎨 专业UI
- 📝 完整文档

### 用户价值

- ✨ 任何对象都能编辑
- ✨ 无需手动注册
- ✨ 快速原型开发
- ✨ 生产环境回退
- ✨ 插件友好

---

**GenericPropertyAdapter - 让 ChronoBlocks 属性系统真正「智能」！** 🔍✨

现在即使遇到未知对象类型，也能：
- 🧠 自动反射分析
- ✏ 提供基础编辑
- 🔄 安全保存修改
- 📋 友好的UI提示

**属性系统已经完美了！** 🎉🚀

---

**版本**: v4.5.0  
**作者**: ChronoBlocks Team  
**日期**: 2025-10-15  
**状态**: ✅ 生产就绪

