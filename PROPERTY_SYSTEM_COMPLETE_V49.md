# 🎉 ChronoBlocks 属性系统完整实现 v4.9

**最终完成版** - 2025-10-16

---

## 📦 系统架构概览

ChronoBlocks 属性系统是一个**企业级动态属性面板框架**，基于**适配器模式**设计，支持：

- ✅ **16种专用属性编辑器**
- ✅ **通用反射回退编辑器**
- ✅ **类型安全的注册与查找机制**
- ✅ **线程安全的并发访问**
- ✅ **模块化扩展与动态加载**

---

## 🧩 核心组件

### 1️⃣ PropertyAdapterRegistry（注册中心）

**文件：** `src/main/java/com/chronoblocks/ui/properties/PropertyAdapterRegistry.java`

**职责：** 统一管理所有 PropertyAdapter 实例的注册、查找与类型匹配。

**关键特性：**
- 🔒 **线程安全** - 使用 `ConcurrentHashMap` 防止并发修改
- 🔍 **多级匹配** - 精确匹配 → 继承链查找 → `supports()` 方法 → Fallback
- 🛡️ **泛型安全** - 完整的类型参数支持
- 📊 **调试工具** - `getDebugInfo()` 输出所有注册信息

**核心 API：**
```java
// 注册适配器
PropertyAdapterRegistry.registerAdapter(Keyframe.class, new KeyframePropertyAdapter());

// 获取适配器（通过对象）
Keyframe kf = ...;
IPropertyAdapter<Keyframe> adapter = PropertyAdapterRegistry.getAdapterFor(kf);

// 设置回退适配器
PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());
```

---

### 2️⃣ PropertyAdapterBootstrap（启动器）

**文件：** `src/main/java/com/chronoblocks/ui/properties/PropertyAdapterBootstrap.java`

**职责：** 在 ChronoBlocks 启动时自动注册所有可用的属性适配器。

**已注册的适配器（16个）：**

| 类别 | 适配器 | 目标类型 | 功能说明 |
|------|--------|----------|----------|
| **时间线系统（3个）** | | | |
| | `TimelineProjectPropertyAdapter` | `Object` (反射) | 项目名称、FPS、自动保存、序列管理 |
| | `TimelineSequencePropertyAdapter` | `Object` (反射) | 序列名称、时长、循环、轨道管理 |
| | `TimelineTrackPropertyAdapter` | `Object` (反射) | 轨道名称、启用、混合模式、时间范围 |
| **核心对象（3个）** | | | |
| | `KeyframePropertyAdapter` | `Keyframe` | 位置、旋转、缩放、透明度、插值类型 |
| | `ContainerPropertyAdapter` | `Container` | 基本信息、视觉属性、动画模式、层级 |
| | `ClipPropertyAdapter` | `AnimationClip` | 片段名称、时长、关键帧列表 |
| **扩展系统（6个）** | | | |
| | `EffectPropertyAdapter` | `Object` (反射) | 粒子、光照、物理、声音等特效参数 |
| | `ProjectEnvironmentPropertyAdapter` | `Object` (反射) | 环境光、天空、雾、光照、天气 |
| | `MaterialLibraryPropertyAdapter` | `Object` (反射) | PBR材质、纹理路径、材质列表 |
| | `CameraPropertyAdapter` | `Object` (反射) | 位置、旋转、FOV、景深、镜头特效 |
| | `PostProcessingPropertyAdapter` | `Object` (反射) | Bloom、景深、色调映射、颜色分级 |
| | `PhysicsEnvironmentPropertyAdapter` | `Object` (反射) | 重力、风场、碰撞、空气阻力 |
| **扩展类型（3个）** | | | |
| | `Vector3fPropertyAdapter` | `org.joml.Vector3f` | XYZ 向量拖动条 |
| | `ColorPropertyAdapter` | `org.joml.Vector4f` | RGBA 颜色拾色器 |
| | `ListPropertyAdapter` | `java.util.List` | 列表展开与递归渲染 |
| **通用回退（1个）** | | | |
| | `GenericPropertyAdapter` | `Object` | 反射通用编辑器（任意对象） |

**调用示例：**
```java
// 在 UIManager 或 EditorCore 初始化时调用
public void init() {
    PropertyAdapterBootstrap.initialize();
    // ... 其他初始化 ...
}
```

---

## 🎨 16种专用适配器详解

### 🎬 时间线系统适配器

#### 1️⃣ TimelineProjectPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/TimelineProjectPropertyAdapter.java`

**编辑功能：**
- 📝 项目名称、作者、描述
- ⚙️ 全局设置（FPS、自动保存间隔、全局循环）
- 🎞️ 序列管理（添加/删除/重命名）
- 📤 导出设置（分辨率、帧率、格式）

**UI 特色：**
- 折叠式分组
- 批量操作按钮
- 实时统计信息

---

#### 2️⃣ TimelineSequencePropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/TimelineSequencePropertyAdapter.java`

**编辑功能：**
- 📝 序列名称与描述
- ⏱️ 持续时间、时间缩放
- 🔁 循环播放
- 🎛️ 轨道管理（添加/删除/重排序）
- ▶️ 播放控制（播放/暂停/停止）

**UI 特色：**
- 播放控制按钮组
- 轨道列表可视化
- 时间尺标预览

---

#### 3️⃣ TimelineTrackPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/TimelineTrackPropertyAdapter.java`

**编辑功能：**
- 📝 轨道名称与启用状态
- 🎯 容器绑定
- 🎨 混合模式（Normal、Additive、Multiply）
- ⚖️ 权重与对齐
- ⏱️ 时间范围（开始/结束时间）
- 🎭 视觉参数（颜色、高度）

**UI 特色：**
- 混合模式预设
- 容器选择器
- 实时权重调整

---

### 🎨 核心对象适配器

#### 4️⃣ KeyframePropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/KeyframePropertyAdapter.java`

**编辑功能：**
- ⏱️ 时间戳
- 📍 位置（XYZ）
- 🔄 旋转（XYZ）
- 📏 缩放（XYZ）
- 👁️ 透明度
- 🎯 插值类型（Linear、Bezier、Ease In/Out...）

**UI 特色：**
- 向量编辑器集成
- 插值曲线预览
- 一键复制/粘贴

---

#### 5️⃣ ContainerPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/ContainerPropertyAdapter.java`

**编辑功能：**
- 📝 基本信息（名称、ID、类型）
- 🎨 视觉属性（颜色、材质、透明度）
- 🎬 动画模式（Transform、Color、Scale）
- 📦 边界与统计
- 🌳 层级关系
- 🏷️ 元数据管理

**UI 特色：**
- 折叠式多分组
- 层级树可视化
- 元数据键值对编辑器

---

#### 6️⃣ ClipPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/ClipPropertyAdapter.java`

**编辑功能：**
- 📝 片段名称与描述
- ⏱️ 持续时间、起始时间
- 🔑 关键帧列表查看
- 📊 统计信息

**UI 特色：**
- 关键帧列表渲染
- 时间线预览

---

### 🌟 扩展系统适配器

#### 7️⃣ EffectPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/EffectPropertyAdapter.java`

**编辑功能：**
- 🎆 粒子特效（位置、数量、速度、颜色、重力）
- 💡 光照特效（光源位置、颜色、强度、范围、阴影）
- 📷 相机抖动（强度、频率、衰减）
- 🎨 材质淡入淡出（持续时间、颜色、不透明度）
- 🧮 物理效果（力的大小、方向、影响半径）
- 🔊 音效（音量、音高、循环、3D定位）

**UI 特色：**
- 动态特效类型识别
- 反射式参数渲染
- 类型特定的控件

---

#### 8️⃣ ProjectEnvironmentPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/ProjectEnvironmentPropertyAdapter.java`

**编辑功能：**
- 🌅 环境光（颜色、强度）
- 🌈 天空颜色（地平线、天顶、过渡）
- 🌫️ 雾效果（启用、密度、颜色、范围）
- ☀️ 光照（方向、颜色、强度、阴影）
- 🎚️ 曝光与后处理
- 🌦️ 天气与昼夜循环
- 👁️ 方块可见性层级

**UI 特色：**
- 颜色拾色器集成
- 实时环境预览提示
- 预设天气快捷按钮

---

#### 9️⃣ MaterialLibraryPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/MaterialLibraryPropertyAdapter.java`

**编辑功能：**
- 📋 材质列表管理（添加/删除/重命名）
- 🎨 基础颜色（RGBA）
- ⚙️ PBR 参数（金属度、粗糙度、发光强度）
- 🖼️ 纹理路径（基础、法线、粗糙度、金属、发光）

**UI 特色：**
- 材质卡片式布局
- PBR 参数拖动条
- 纹理路径输入框

---

#### 🔟 CameraPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/CameraPropertyAdapter.java`

**编辑功能：**
- 📍 位置与旋转（XYZ 向量）
- 🎥 投影参数（FOV、近/远裁剪面、预设）
- 📷 镜头特效（景深、焦距、光圈、散景形状）
- 🎬 动画控制（持续时间、循环、平滑度）

**UI 特色：**
- FOV 预设按钮（广角/标准/长焦/超广角）
- 散景形状选择器（圆形/六边形/八边形/心形）
- 归零与预览快捷操作

---

#### 1️⃣1️⃣ PostProcessingPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/PostProcessingPropertyAdapter.java`

**编辑功能：**
- 🌟 Bloom 泛光（启用、强度、阈值）
- 📷 景深 (Depth of Field)（启用、焦距、光圈、散景形状）
- 🎨 色调映射（线性/ACES/Reinhard/Filmic）
- 🎛️ 颜色分级（曝光、对比度、饱和度）
- ✨ 屏幕空间效果（SSAO、SSR）

**UI 特色：**
- 效果开关与参数联动
- 预设配置按钮
- 实时效果预览提示

---

#### 1️⃣2️⃣ PhysicsEnvironmentPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/PhysicsEnvironmentPropertyAdapter.java`

**编辑功能：**
- 🌍 重力系统（重力向量、预设：地球/月球/火星/无重力）
- 🌪️ 风场系统（风力向量、风速、湍流）
- 💥 碰撞系统（启用、弹性、摩擦力）
- 🌫️ 空气阻力（阻力系数、空气密度）
- ⚙️ 物理开关（启用/禁用物理、调试显示）

**UI 特色：**
- 重力预设快捷按钮
- 物理参数实时调整
- 调试可视化开关

---

### 🔧 扩展类型适配器

#### 1️⃣3️⃣ Vector3fPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/Vector3fPropertyAdapter.java`

**编辑功能：**
- 📊 XYZ 向量拖动条
- 🎯 单独分量编辑
- 🔢 数值输入框

**UI 特色：**
- 紧凑的三行布局
- Tooltip 提示

---

#### 1️⃣4️⃣ ColorPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/ColorPropertyAdapter.java`

**编辑功能：**
- 🎨 RGBA 颜色拾色器
- 🔴 独立通道调整
- 📋 十六进制复制/粘贴

**UI 特色：**
- ImGui 原生颜色选择器
- Alpha 通道支持

---

#### 1️⃣5️⃣ ListPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/ListPropertyAdapter.java`

**编辑功能：**
- 📋 列表展开/折叠
- 🔁 递归元素渲染
- ➕ 添加/删除元素

**UI 特色：**
- 树状展开视图
- 元素序号显示
- 动态适配器查找

---

### 🛡️ 通用回退适配器

#### 1️⃣6️⃣ GenericPropertyAdapter
**文件：** `src/main/java/com/chronoblocks/ui/properties/adapters/GenericPropertyAdapter.java`

**编辑功能：**
- 🔍 反射扫描所有字段
- ✏️ 自动类型推断编辑器
- 🔒 只读字段识别

**UI 特色：**
- 兼容任意对象
- 自动分组
- 安全的默认编辑器

---

## 🔄 查找机制

PropertyAdapterRegistry 使用**4级回退机制**：

```
1️⃣ 精确匹配 (Exact Match)
   ↓ 未找到
2️⃣ 继承链查找 (isAssignableFrom)
   ↓ 未找到
3️⃣ supports() 方法检查
   ↓ 未找到
4️⃣ Fallback 适配器 (GenericPropertyAdapter)
```

**示例流程：**
```java
Object camera = new Camera();

// 1️⃣ 精确匹配: Camera.class → 未注册
// 2️⃣ 继承链: Camera extends Object → Object.class → CameraPropertyAdapter ✅
// 3️⃣ supports(): CameraPropertyAdapter.supports(camera) → true ✅
// 返回: CameraPropertyAdapter
```

---

## 📊 性能与线程安全

### 并发控制
- ✅ 使用 `ConcurrentHashMap` 存储适配器
- ✅ `synchronized` 保护初始化过程
- ✅ 只读操作无需加锁

### 内存优化
- ✅ 单例适配器实例（共享复用）
- ✅ 懒加载可选模块
- ✅ 弱引用缓存（未来扩展）

---

## 🧪 使用示例

### 完整工作流

```java
// ========== 1️⃣ 初始化阶段 ==========
public class ChronoBlocksMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // 注册所有适配器
        PropertyAdapterBootstrap.initialize();
    }
}

// ========== 2️⃣ UI 渲染阶段 ==========
public class PropertiesPanel {
    public void render() {
        Object selected = SelectionManager.getCurrentSelected();
        
        if (selected != null) {
            // 自动查找适配器
            IPropertyAdapter adapter = PropertyAdapterRegistry.getAdapterFor(selected);
            
            if (adapter != null) {
                // 渲染属性编辑器
                boolean modified = adapter.renderProperties(selected);
                
                if (modified) {
                    // 通知系统对象已修改
                    EventBus.publish(new ObjectModifiedEvent(selected));
                }
            }
        } else {
            UITheme.label("未选择对象", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
}

// ========== 3️⃣ 选择管理阶段 ==========
public class SelectionManager {
    private static Object currentSelected = null;
    
    public static void select(Object obj) {
        currentSelected = obj;
        EventBus.publish(new SelectionChangedEvent(obj));
    }
    
    public static Object getCurrentSelected() {
        return currentSelected;
    }
}
```

---

## 🎯 扩展指南

### 如何添加新适配器？

**步骤：**

1️⃣ **创建适配器类**
```java
package com.chronoblocks.ui.properties.adapters;

public class MyCustomAdapter implements IPropertyAdapter<MyCustomObject> {
    @Override
    public boolean renderProperties(MyCustomObject obj) {
        // 渲染UI
        return false; // 是否修改
    }
    
    @Override
    public Class<MyCustomObject> getTargetType() {
        return MyCustomObject.class;
    }
    
    @Override
    public String getAdapterName() {
        return "我的自定义适配器";
    }
}
```

2️⃣ **注册到 Bootstrap**
```java
// 在 PropertyAdapterBootstrap.initialize() 中添加：
registerAdapter(MyCustomObject.class, new MyCustomAdapter());
registeredCount++;
```

3️⃣ **完成！** 🎉
系统会自动识别并使用新适配器。

---

## 📝 日志输出示例

启动时的完整日志：

```
[PropertyAdapterBootstrap] 正在注册属性适配器...
  ========== 时间线系统适配器 ==========
[PropertyAdapterBootstrap] ✅ 已注册: TimelineProjectPropertyAdapter → TimelineProject
[PropertyAdapterBootstrap] ✅ 已注册: TimelineSequencePropertyAdapter → TimelineSequence
[PropertyAdapterBootstrap] ✅ 已注册: TimelineTrackPropertyAdapter → TimelineTrack
  ========== 核心对象适配器 ==========
[PropertyAdapterBootstrap] ✅ 已注册: KeyframePropertyAdapter → Keyframe
[PropertyAdapterBootstrap] ✅ 已注册: ContainerPropertyAdapter → Container
[PropertyAdapterBootstrap] ✅ 已注册: ClipPropertyAdapter → AnimationClip
  ========== 扩展系统适配器 ==========
[PropertyAdapterBootstrap] ✅ 已注册: EffectPropertyAdapter → Effect
[PropertyAdapterBootstrap] ✅ 已注册: ProjectEnvironmentPropertyAdapter → ProjectEnvironment
[PropertyAdapterBootstrap] ✅ 已注册: MaterialLibraryPropertyAdapter → MaterialLibrary
  ========== 高级功能适配器 ==========
[PropertyAdapterBootstrap] ✅ 已注册: CameraPropertyAdapter → Camera
[PropertyAdapterBootstrap] ✅ 已注册: PostProcessingPropertyAdapter → PostProcessing
[PropertyAdapterBootstrap] ✅ 已注册: PhysicsEnvironmentPropertyAdapter → PhysicsEnvironment
  ========== 扩展类型适配器 ==========
[PropertyAdapterBootstrap] ✅ 已注册: Vector3fPropertyAdapter → Vector3f
[PropertyAdapterBootstrap] ✅ 已注册: ColorPropertyAdapter → Vector4f
[PropertyAdapterBootstrap] ✅ 已注册: ListPropertyAdapter → List
  ========== 通用回退适配器 ==========
[PropertyAdapterBootstrap] ✅ 已设置回退适配器: GenericPropertyAdapter
[PropertyAdapterBootstrap] 属性系统初始化完成，共注册 16 个适配器
```

---

## 🎉 完成清单

| 功能模块 | 状态 | 文件 |
|---------|------|------|
| ✅ 注册中心 | 完成 | `PropertyAdapterRegistry.java` |
| ✅ 启动器 | 完成 | `PropertyAdapterBootstrap.java` |
| ✅ 时间线项目适配器 | 完成 | `TimelineProjectPropertyAdapter.java` |
| ✅ 时间线序列适配器 | 完成 | `TimelineSequencePropertyAdapter.java` |
| ✅ 时间线轨道适配器 | 完成 | `TimelineTrackPropertyAdapter.java` |
| ✅ 关键帧适配器 | 完成 | `KeyframePropertyAdapter.java` |
| ✅ 容器适配器 | 完成 | `ContainerPropertyAdapter.java` |
| ✅ 片段适配器 | 完成 | `ClipPropertyAdapter.java` |
| ✅ 特效适配器 | 完成 | `EffectPropertyAdapter.java` |
| ✅ 环境适配器 | 完成 | `ProjectEnvironmentPropertyAdapter.java` |
| ✅ 材质库适配器 | 完成 | `MaterialLibraryPropertyAdapter.java` |
| ✅ 相机适配器 | 完成 | `CameraPropertyAdapter.java` |
| ✅ 后处理适配器 | 完成 | `PostProcessingPropertyAdapter.java` |
| ✅ 物理适配器 | 完成 | `PhysicsEnvironmentPropertyAdapter.java` |
| ✅ Vector3f适配器 | 完成 | `Vector3fPropertyAdapter.java` |
| ✅ Color适配器 | 完成 | `ColorPropertyAdapter.java` |
| ✅ List适配器 | 完成 | `ListPropertyAdapter.java` |
| ✅ 通用适配器 | 完成 | `GenericPropertyAdapter.java` |

---

## 🚀 下一步计划

1. **UI 集成** - 将属性面板集成到主窗口右侧
2. **快捷键支持** - 添加键盘快捷键快速切换属性
3. **预设管理** - 保存/加载属性预设
4. **撤销/重做** - 集成到命令系统
5. **实时预览** - 参数修改时实时更新场景

---

**🎊 ChronoBlocks 属性系统 v4.9 - 企业级完整实现！**

