# Blockbench .bbmodel 导入器 开发文档

状态：设计中（尚未实现） · 关联代码：`com.director.actor.rig.skeleton.*`、`com.director.actor.model.*`
前置阅读：`docs/actor_animation_mine_imator_roadmap.md`（如存在）、`/memories/repo/actor_rig_system.md`

## 1. 目标

让用户可以直接导入一个用 [Blockbench](https://www.blockbench.net/) 制作的 `.bbmodel` 工程文件，
自动生成：

1. 一个 `RigDefinition`（骨骼层级：骨骼 id、父子关系、静止姿态）；
2. 一个 `CustomModelDefinition`（每根骨骼下挂的方块状外观，即 `BoneCuboid` 列表）；
3. （可选，见姊妹文档《自定义模型贴图管线设计》）每个 cuboid 的真实贴图，而不是当前 MVP
   的"整块统一原版方块材质"占位方案。

导入产物注册进 `CustomRigLibrary` + `CustomModelLibrary`，之后即可像内置示例
`BuiltInCustomRigs.SIMPLE_ROBOT` 一样，绑定给 `ActorType.CUSTOM` 演员、在 Timeline 里做关键帧动画。

本导入器**只解析导入**，不负责渲染贴图（渲染仍由 `CustomModelPreviewRenderer` 负责，
初期沿用"整块材质"占位方案；接入真实贴图见姊妹文档）。

## 2. 为什么选 `.bbmodel` 而不是 `.geo.json` / `.obj` / `glTF`

- `.bbmodel` 是 Blockbench 的原生工程格式（JSON），信息最完整：既有 `outliner`（骨骼树），
  也有 `elements`（cuboid 几何+每面 UV），还内嵌纹理（base64 PNG），一个文件就是"骨架+外观"的完整数据源，
  不需要额外配对文件。
- `.geo.json`（Bedrock Entity 格式）只有导出后的结果，缺少 Blockbench 工程态的编辑元数据，且骨骼动画
  部分格式差异更大，不如 `.bbmodel` 直接。
- `.obj`/glTF 是通用三角网格格式，没有"cuboid + 骨骼"这种和 Minecraft 方块模型天然契合的结构，
  解析出来还要做网格→cuboid 的近似重建，得不偿失。
- Director 现有的 `RigDefinition`/`BoneCuboid` 数据结构本来就是"层级 + 轴对齐盒子"的简化模型，
  与 Blockbench 的 `outliner`/`elements` 结构几乎一一对应，转换成本最低。

## 3. `.bbmodel` 格式（与本导入器相关的子集）

`.bbmodel` 是一个 JSON 文件，顶层结构（仅列出用得到的字段）：

```jsonc
{
  "meta": { "format_version": "4.5", "model_format": "free" },
  "resolution": { "width": 64, "height": 64 },
  "elements": [
    {
      "uuid": "e1",
      "name": "torso",
      "from": [-3, 0, -2],      // 局部坐标（单位：像素，1 像素 = 1/16 方块）
      "to": [3, 7, 2],
      "origin": [0, 0, 0],      // 旋转支点
      "rotation": [0, 0, 0],    // 可选：整体旋转（度）
      "faces": {
        "north": { "uv": [0, 8, 6, 15], "texture": 0 },
        "south": { "uv": [6, 8, 12, 15], "texture": 0 },
        "east":  { "uv": [0, 8, 4, 15], "texture": 0 },
        "west":  { "uv": [0, 8, 4, 15], "texture": 0 },
        "up":    { "uv": [0, 0, 6, 4], "texture": 0 },
        "down":  { "uv": [6, 0, 12, 4], "texture": 0 }
      }
    }
  ],
  "outliner": [
    {
      "name": "root",
      "uuid": "b0",
      "origin": [0, 0, 0],
      "rotation": [0, 0, 0],
      "children": [
        {
          "name": "torso",
          "uuid": "b1",
          "origin": [0, 12, 0],
          "rotation": [0, 0, 0],
          "children": ["e1", { "name": "head", "uuid": "b2", "origin": [0, 19, 0], "children": ["e2"] }]
        }
      ]
    }
  ],
  "textures": [
    { "id": "0", "name": "skin.png", "source": "data:image/png;base64,....", "uuid": "t0" }
  ]
}
```

要点：

- **`outliner`** 是骨骼树：数组套数组的递归结构，每个"组"（group）节点是一根骨骼；
  节点的 `children` 里既可能是子骨骼（对象），也可能是挂在当前骨骼上的 `elements` 的 `uuid`（字符串）。
- **`elements`** 是所有 cuboid 的扁平列表，用 `uuid` 被 `outliner` 引用，不出现在 `outliner` 里的
  element 视为挂在隐式根节点上。
- 坐标单位是**像素**（16 像素 = 1 格），且是 Blockbench 自己的坐标系（Y 向上，原点通常在模型底部中心），
  需要换算成 Direcror 的"格"单位（1.0 = 1 格）并做坐标系修正。
- `rotation` 既可能出现在骨骼（`outliner` 节点）上，也可能出现在单个 `elements` 上（cuboid 自身旋转，
  Minecraft/Blockbench 限制单个 cuboid 只能绕一个轴且角度是 -45/-22.5/0/22.5/45 中的一个）；
  **MVP 阶段只处理骨骼级别的旋转，忽略单个 cuboid 自身旋转**（转换时对这类模型给出警告，不中断导入）。
- `faces` 定义每个面的 UV 矩形 + 使用哪个纹理（`texture` 是 `textures` 数组的下标或 id）。
  本导入器第一阶段**不解析 UV**（贴图问题由姊妹文档单独处理），只记录 cuboid 几何。

## 4. 数据映射规则

### 4.1 骨骼命名与去重

- 骨骼 id 直接取 Blockbench 节点的 `name` 字段，做以下清洗：
  - 转小写；空格/中横线转下划线；
  - 去除 Minecraft 资源 id 不允许的字符（只保留 `[a-z0-9_]`）；
  - 与 `RigDefinition` 现有校验一致：id 不能为空、不能重复。若清洗后重复，追加 `_2`、`_3` 等后缀，
    并在导入报告里列出重命名映射，方便用户核对。
- **必须存在且必须是唯一根节点**：若 `outliner` 顶层有多个节点，自动插入一个合成的 `root` 骨骼作为它们的父节点
  （因为 `RigDefinition` 要求"恰好一个根骨骼"，见 `RigDefinition` 构造函数校验）。

### 4.2 骨骼变换（`BoneDefinition.restLocal`）

- Blockbench 的 `origin` 是**世界坐标**（相对于模型原点，不是相对父骨骼），需要转换成"相对父骨骼"的局部
  平移：`localTranslation = (origin - parentOrigin) / 16`（除以 16 把像素换算成格）。
- Blockbench 的骨骼 `rotation` 是欧拉角（度，XYZ 顺序，Blockbench 里通常叫 `rotation: [x, y, z]`），
  换算成 `Quaternionf`：`new Quaternionf().rotateXYZ(toRadians(x), toRadians(y), toRadians(z))`，
  与 `HumanoidRig`/`ActorAnimationDopeSheet` 里现有的旋转约定保持一致（复用
  `LegacyHumanoidPoseAdapter.rotate` 里同款欧拉角约定，避免出现两套旋转语义）。
- `scale` 固定为 `(1,1,1)`：Blockbench 的骨骼节点本身没有独立缩放概念（缩放通常通过动画关键帧表达，
  静止姿态不需要）。
- `BoneCapabilities`：默认给所有非根骨骼 `rotationAndScale()`，根骨骼给 `full()`
  （与 `HumanoidRig`/`BuiltInCustomRigs` 现有约定一致）。

### 4.3 Cuboid 几何（`BoneCuboid`）

对每个 element，找到它在 `outliner` 里被哪个骨骼节点引用（若未被任何骨骼引用，挂在合成根骨骼下）：

```
offset = (from - boneOrigin) / 16          // Vector3f，单位：格
size   = (to - from) / 16                  // Vector3f，单位：格
```

- `to - from` 任一分量 `<= 0` 视为无效 cuboid（Blockbench 理论上不会出现，但导入前需要校验，
  复用 `BoneCuboid` 构造函数里已有的 `size.x/y/z > 0` 校验，解析失败的 element 跳过并记入警告列表，
  不让整个导入失败）。
- Blockbench 的 Y 轴与 Director/Minecraft 一致（向上为正），X/Z 轴的正负号在实测中需要与一个已知模型
  （比如导出内置 `SIMPLE_ROBOT` 到 Blockbench 里核对一次）做一次校准，若发现左右镜像，在读取阶段统一取反
  X 分量即可（实现时预留 `MIRROR_X` 常量方便调整，不要散落魔法数字）。
- 单个骨骼下有多个 element 时，`CustomModelDefinition.cuboids()` 里对应生成多条 `BoneCuboid`
  （已支持：`CustomModelDefinition` 本来就是 `List<BoneCuboid>`，同一个 `boneId` 可以出现多次）。

### 4.4 贴图（本阶段占位）

- 第一阶段忽略 `faces`/`textures`，所有 cuboid 统一赋值一个用户可选的占位方块（默认
  `minecraft:white_concrete`，暴露一个导入参数 `defaultBlockStateId`）。
- 解析时仍然把 `textures[].source`（base64 PNG）解码并保存到
  `<world>/director/custom_model_textures/<modelId>/<textureId>.png`，为姊妹文档的贴图管线预留数据，
  即使本阶段渲染用不上，也不要丢弃这份信息（避免用户以后要重新导入一次）。

## 5. 新增文件与接口设计

```
com.director.actor.model.bbmodel/
├── BbModelFile.java            // 反序列化后的原始结构（POJO，贴近 JSON 字段，不做校验）
├── BbModelParser.java          // JSON -> BbModelFile（用现有 Gson，风格对齐 RigDefinitionPersistence）
├── BbModelImporter.java        // BbModelFile -> ImportResult（核心转换逻辑，见下）
├── BbModelImportResult.java    // record：RigDefinition rig, CustomModelDefinition model,
│                                //         List<String> warnings, Map<String, Path> extractedTextures
└── BbModelImportException.java // 导入失败时抛出，带用户可读的中文原因
```

### 5.1 `BbModelImporter` 核心签名

```java
public final class BbModelImporter {
    private BbModelImporter() {}

    /**
     * @param file              .bbmodel 文件路径
     * @param rigId             生成的 RigDefinition 使用的 id（需保证在 CustomRigLibrary 中唯一）
     * @param modelId           生成的 CustomModelDefinition 使用的 id
     * @param defaultBlockStateId 贴图管线接入前，所有 cuboid 使用的占位方块，如 "minecraft:white_concrete"
     */
    public static BbModelImportResult importFile(
            Path file, String rigId, String modelId, String defaultBlockStateId) throws IOException;
}
```

- 不直接写入 `CustomRigLibrary`/`CustomModelLibrary`，只返回结果对象；由调用方（UI 层，
  类似 `ActorAnimationAssetEditing` 的做法）决定何时注册、是否包一层撤销命令
  （`TimelineCommandBridge`/`StateChangeCommand`，与项目里其它"导入"操作保持一致的撤销体验）。
- `warnings` 用于展示"忽略了 N 个 cuboid 自身旋转""发现 2 个重名骨骼已重命名为 xxx_2"这类非致命提示，
  在导入完成对话框里展示，而不是每次都弹错误。

### 5.2 UI 接入点

- 复用现有面板结构：在 `ActorProjectPropertyAdapter` 的"自定义骨架"/"自定义模型外观"下拉框旁新增
  一个"导入 .bbmodel##importBbModel" 按钮（与现有 `ActorAnimationAssetPanel` 里"导入动画文件"按钮
  的 `chooseFile` 弹窗模式保持一致）。
- 导入成功后：
  1. 调用 `CustomRigLibrary.getInstance().register(result.rig())`；
  2. 调用 `CustomModelLibrary.getInstance().register(result.model())`；
  3. 若当前正在编辑的 Actor 类型是 `CUSTOM`，自动把 `customRigId`/`customModelId` 指向新导入的结果；
  4. 展示 `warnings`。

## 6. 边界情况与校验清单

- 空模型（没有任何 `elements`）：允许导入骨架，`CustomModelDefinition.cuboids()` 为空列表
  （用户后续可以用姊妹文档的贴图管线或手工编辑补充外观）。
- 骨骼数超过合理上限（建议 64）：给出警告但不阻止导入，性能由
  `CustomModelPreviewRenderer`（每个 cuboid 一个 `BlockDisplayEntity`）自然限制，超大模型会明显卡顿，
  在警告文案里提示用户"骨骼较多，可能影响预览性能"。
- 非"free"/"bedrock"格式的 `model_format`（比如 Java 通用模型）：只影响个别字段的默认值，不阻止导入，
  当作普通 free 模型处理。
- 文件不是合法 JSON、缺少 `outliner`/`elements` 关键字段：直接抛 `BbModelImportException`，
  文案参考 `CustomRigPersistence`/`AnimationAssetPersistence` 的"文件校验失败：xxx"风格。

## 7. 测试计划

- 单元测试（`src/test/java`，纯 JVM，不依赖 Minecraft 运行时，符合仓库现有 `AnimationAssetPersistence`
  等类的测试方式）：
  - 用手写的最小 `.bbmodel` 字符串（2~3 根骨骼 + 2 个 cuboid）验证 `RigDefinition`/`CustomModelDefinition`
    转换结果的骨骼数量、父子关系、offset/size 数值。
  - 多根节点自动合成 root 的场景。
  - 重名骨骼自动重命名的场景。
  - 非法 cuboid（`to <= from`）被跳过且产生 warning 的场景。
- 手动验证：导入一个真实的、简单的人形 Blockbench 模型，对比预览效果与 Blockbench 编辑器里的比例是否一致
  （重点检查 X 轴镜像、Y 轴原点是否对齐）。

## 8. 分阶段建议

1. **P0**：`BbModelParser` + `BbModelImporter`（骨架+cuboid几何，占位材质）+ 单元测试，不接 UI。
2. **P1**：接入 `ActorProjectPropertyAdapter` 的导入按钮 + 撤销命令。
3. **P2**：与姊妹文档《自定义模型贴图管线设计》打通，让导入时提取的贴图真正显示到 cuboid 上。

## 9. 明确不做的事情（避免范围蔓延）

- 不支持 Blockbench 的"网格"（mesh）元素类型，只支持 `cube` 类型的 `elements`（Director 的
  `BoneCuboid` 本来就只能表达轴对齐盒子）。
- 不支持导入 Blockbench 里的动画（`animations` 字段）；动画仍然通过 Director 自己的关键帧编辑器制作。
- 不做"编辑并导出回 .bbmodel"的反向流程。
