# 自定义模型贴图管线 开发文档

状态：设计中（尚未实现） · 关联代码：`com.director.actor.model.*`、`com.director.mixin.accessor.*`
前置阅读：`docs/bbmodel_import_design.md`、`/memories/repo/actor_rig_system.md`

## 1. 背景与问题

阶段 2 MVP（`CustomModelPreviewRenderer`）里，每个 `BoneCuboid` 只能选一个**原版方块状态**
（如 `minecraft:white_concrete`）作为整体外观，因为渲染用的是 `DisplayEntity.BlockDisplayEntity`，
它显示的是"一个方块的默认外观"，无法让六个面分别贴不同的自定义纹理区域。

Mine-imator/Blockbench 用户期望的是：像贴皮肤一样，给模型的每个面指定纹理图集里的一块 UV 区域
（这正是 `.bbmodel` 里 `faces.uv` 字段描述的东西，见姊妹文档）。要在 Minecraft 里做到这一点，
不能只换 `BlockState`，需要让 cuboid 使用一个**自定义方块/物品模型**（Minecraft 模型 JSON 里的
`elements` 天然支持"任意 cuboid + 每面独立 UV"，与 Blockbench 的 `elements` 几乎是同一套概念）。

## 2. 技术方案选型

### 2.1 候选方案对比

| 方案 | 说明 | 结论 |
|---|---|---|
| A. 继续用 `BlockDisplayEntity` + 原版方块 | 现状 | 无法自定义贴图，仅作为过渡方案 |
| B. `ItemDisplayEntity` + 运行时生成的物品模型（自定义 `elements`+UV，贴自定义纹理） | 物品模型 JSON 原生支持任意 cuboid 每面 UV，且不需要真的把"方块"放进世界 | **推荐** |
| C. 真正的自定义 `EntityRenderer` + 手写网格渲染（类似 GeckoLib） | 完全自由，但要接管渲染管线、光照、动画混合等一整套东西 | 工程量过大，超出当前项目定位（Director 是编辑器/预览工具，不是通用动画引擎） |
| D. 依赖 GeckoLib | 现成方案 | 引入外部 mod 依赖，绑定其模型/动画格式与生命周期，与 Director 已有的 `RigDefinition`/关键帧系统重复建设，放弃 |

选 **B**：复用 Minecraft 自己的模型 JSON 能力（`elements` + `faces.uv` + `textures`），只是把模型
"喂给"一个运行时生成、仅客户端可见的物品，再用 `ItemDisplayEntity` 展示，动画驱动方式与现有
`CustomModelPreviewRenderer`（每帧设置 `AffineTransformation`）完全一致，只是把 `BlockDisplayEntity`
换成 `ItemDisplayEntity`，渲染内容换成自定义模型而不是原版方块。

### 2.2 为什么不用"一个 cuboid 一个 Item"，而要合并

如果每个 `BoneCuboid` 各自生成一个只含一个 cuboid 的模型，粒度最简单，但每个骨骼多个 cuboid 时会有多个
`ItemDisplayEntity`，和现在 MVP 的做法一致，先按这个粒度做（**每个 `BoneCuboid` = 一个物品模型 = 一个
`ItemDisplayEntity`**），不做"整根骨骼合并成一个模型"的优化。原因：

- 合并模型需要处理"一个物品模型里的多个 cuboid 分别绑定到不同骨骼动画"的问题，
  而 Minecraft 物品模型没有"骨骼"概念，一个物品实体只有一个整体 transform，没法做"模型内再拆分动画"。
  Director 已经用"多个 Display 实体 + 各自的 world transform"解决了这个问题，没必要在模型层再造一遍。
- 每 cuboid 一个 entity 的性能特征和现有 MVP 完全一致，不引入新的性能回归面。

## 3. 运行时资源生成与注册

### 3.1 需要生成什么

对每个唯一的 `(cuboid 几何, 贴图内容)` 组合，生成一份 Minecraft **物品模型 JSON**（Java 版模型格式）：

```jsonc
{
  "parent": "minecraft:item/generated",
  "textures": { "particle": "director:custom_model/<modelId>/<textureId>", "0": "director:custom_model/<modelId>/<textureId>" },
  "elements": [
    {
      "from": [ /* cuboid.offset * 16，限制在 [-16,32] 范围内，见 3.4 */ ],
      "to":   [ /* (offset+size) * 16 */ ],
      "faces": {
        "north": { "uv": [...], "texture": "#0" },
        "south": { "uv": [...], "texture": "#0" },
        "east":  { "uv": [...], "texture": "#0" },
        "west":  { "uv": [...], "texture": "#0" },
        "up":    { "uv": [...], "texture": "#0" },
        "down":  { "uv": [...], "texture": "#0" }
      }
    }
  ]
}
```

这与 Blockbench "Java Block/Item Model" 导出的格式基本一致，`bbmodel` 里 `faces.uv` 可以直接照抄
（前提是 `docs/bbmodel_import_design.md` 里补上 UV 解析，当前设计文档假定这份数据已经可用）。

### 3.2 怎么把它"喂给"游戏（客户端运行时资源包注入）

Minecraft/Fabric 支持通过 `ResourceManagerHelper`（Fabric API `fabric-resource-loader-v0`，
项目 `build.gradle` 里已经有这个依赖，见下）在客户端追加一个**内存资源包**
（实现 `net.minecraft.resource.ResourcePack`，各方法直接从内存 `Map<String, byte[]>` 读取，
不落地到磁盘），流程：

1. 新建 `DirectorRuntimeResourcePack implements ResourcePack`：内部是一个
   `Map<Identifier, byte[]>`（模型 json、贴图 png、`items/*.json` 的物品覆盖定义等）。
2. 通过 `ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
   .registerReloadListener(...)` 或直接在 Fabric 的
   `ResourceLoader`/`ResourcePackActivationType` API 里把这个内存包注册为"始终启用"的内置资源包
   （类似 mod 自带资源包的机制，Fabric API 里有 `ResourceManagerHelper.registerBuiltinResourcePack`
   可以用同样的思路，但那是针对 `.zip`/目录的，需要确认 API 是否支持纯内存实现；如果不支持，
   退化为方案 3.2b）。
3. **3.2b（更稳妥的退路，若内存包 API 限制较多）**：把生成的 model/texture 文件写入
   `<游戏实例>/.director_runtime_resources/` 临时目录（真实文件，而不是内存字节数组），
   注册为一个磁盘资源包。实现更简单（不用实现完整 `ResourcePack` 接口的所有方法），
   代价是要自己管理临时文件的清理（进程退出/世界切换时删除），比内存包多一点点 I/O，
   但对这个"编辑器工具"的定位完全可以接受。**建议先按 3.2b 实现，验证可行后再考虑优化成内存包**。
4. 无论 3.2a 还是 3.2b，注册/更新资源后都要触发一次
   `MinecraftClient.getInstance().reloadResources()`（客户端资源重载），新模型才会生效；
   这是一个异步且不算轻量的操作，**不能每帧调用**，必须做成"只在新增/修改自定义模型贴图时触发一次"，
   见 3.3 的缓存策略。

### 3.3 物品的选择：用什么"物品"承载自定义模型

`ItemDisplayEntity` 展示的是一个 `ItemStack`，其外观由该物品当前的模型决定。两种做法：

- **每个 cuboid 模型分配一个独立的自定义物品 id**（如 `director:custom_model_piece`，
  用 NBT/组件里的自定义数据区分具体模型，再配合 `minecraft:item_model` 组件覆盖具体路径）——
  1.21.x 起 Minecraft 用**数据组件**（data components）而不是 `CustomModelData` 数值来做模型覆盖，
  需要确认当前 1.21.10 的物品模型覆盖机制（`minecraft:item_model` 组件 / `ItemStack.set(DataComponentTypes.ITEM_MODEL, Identifier)`），
  一个物品 + 动态设置 `ITEM_MODEL` 组件指向我们生成的模型 id，是目前（1.21.x）最直接的路子，
  不需要遍历旧版 `CustomModelData` 谓词模型的写法。
- 选用一个视觉上无干扰的基底物品（如 `minecraft:paper` 或自定义 mod 物品，参考仓库里
  `com.director.item.DirectorToolItem` 的注册方式新增一个 `director:custom_model_carrier` 物品，
  仅供内部渲染使用，不出现在合成/创造模式物品栏——可以用 `FabricItemSettings` 隐藏、或不注册进
  `ItemGroup`），避免和玩家背包物品混淆。

### 3.4 坐标范围限制

Minecraft 方块/物品模型的 `elements.from/to` 官方要求落在 `[-16, 32]` 像素范围内（超出会被裁剪或
报警告）。`BoneCuboid` 的 offset/size 单位是"格"，需要在生成模型 json 前做一次换算+范围检查：
`fromPixels = offset * 16`，若超出范围，钳制并在导入/生成日志里给出警告（不阻断，尽量渲染出裁剪后的结果）。

## 4. 缓存与生命周期

### 4.1 生成时机

- 不在"每帧"生成，只在以下时机触发（幂等，按内容哈希去重）：
  - 导入 `.bbmodel` 完成时（结合姊妹文档）；
  - 用户在 UI 里手动编辑某个 cuboid 的 UV/贴图时；
  - 游戏启动时把 `CustomModelLibrary` 里所有已持久化的模型批量生成一次（世界加载后的一次性预热，
    避免运行时首次播放动画才发现模型没注册）。

### 4.2 缓存 key

`modelJsonKey = sha256(cuboid.offset, cuboid.size, faceUvMap, textureContentHash)`，
与 `AnimationAssetPersistence`/`CustomRigPersistence` 一样落盘一份索引文件
（`<world>/director/custom_model_cache/index.json`），重复内容直接复用已生成的模型 id，
避免每次重启都重新生成、重复触发资源重载。

### 4.3 清理

- 世界切换/项目关闭时，`CustomModelPreviewRenderer` 已有的 `clear()` 负责销毁
  `ItemDisplayEntity`；生成的资源文件（3.2b 方案下的临时目录）本身可以跨世界复用（按内容哈希去重），
  不需要每次清空，只在 mod 卸载/客户端退出时做一次性清理。

## 5. 新增文件设计

```
com.director.actor.model.texture/
├── GeneratedItemModel.java        // record：id, elementsJson（已按 3.1 拼好的模型 JSON 文本）
├── CustomModelResourceBuilder.java // BoneCuboid + 贴图数据 -> GeneratedItemModel（按 3.1/3.4 规则拼 JSON）
├── DirectorRuntimeResourcePack.java // 方案 3.2b：管理临时目录里的 model/texture 文件 + 触发 reloadResources()
├── CustomModelResourceCache.java  // 按内容哈希去重、索引持久化（3.2/4.2）
└── CustomModelCarrierItem.java    // 承载自定义模型的内部物品（3.3），注册在 mod 初始化里
```

`CustomModelPreviewRenderer` 需要相应调整：

- 新增一个渲染模式开关（每个 `BoneCuboid` 可选：`PLACEHOLDER_BLOCK`（现状）或
  `CUSTOM_TEXTURED_ITEM`（新）），根据 `CustomModelDefinition` 里是否有解析出的贴图数据决定。
- `spawnAll()` 分支：`CUSTOM_TEXTURED_ITEM` 模式下改为 `new DisplayEntity.ItemDisplayEntity(...)`，
  用已有的 `DisplayEntityAccessor` 设置 transform（复用，无需新增 mixin），
  但需要新增一个 `ItemDisplayEntityAccessor`（`@Invoker("setItemStack")`，
  因为 `ItemDisplayEntity.setItemStack` 目前也是 `private`，与 `BlockDisplayEntityAccessor` 同理）。

## 6. 风险与已知限制

- **客户端资源重载有感知延迟/卡顿**：`reloadResources()` 是相对重的操作，批量导入多个模型时要合并成
  一次重载（防抖：收集 200ms 内的所有变更，统一触发一次），而不是每个模型触发一次。
- **多人/服务器场景不适用**：这套方案完全是客户端本地资源注入，纯个人预览/创作工具场景没问题；
  如果以后要"多人协作预览同一个自定义演员"，这些资源需要跟着分发给其他客户端，是明确超出当前范围的问题，
  本设计不处理。
- **物品模型的 UV/光照与真正的方块渲染存在细微差异**（比如物品渲染默认自带一点点环境光效果、
  没有真实方块的方向性阴影），预期效果"足够像"但不会和把同一个 cuboid 摆成世界方块完全一致，
  在文档里应向用户说明这是"预览近似"，不是像素级还原。
- **数据组件 API 会随 Minecraft 版本变化**（`minecraft:item_model` 组件是 1.21.2+ 引入的新机制），
  实现前需要用前一份阶段2文档同款方法（`javap` 反编译已缓存的 mapped jar）核实
  `net.minecraft.component.DataComponentTypes` 里与模型覆盖相关的确切字段名，不要凭记忆假设。

## 7. 分阶段建议

1. **P0**：`CustomModelResourceBuilder` 单元测试（纯字符串/JSON 拼装逻辑，不依赖 Minecraft 运行时）。
2. **P1**：`DirectorRuntimeResourcePack`（3.2b 磁盘临时目录方案）+ 手动触发一次 `reloadResources()`，
   先跑通"一个手写的自定义模型 JSON 出现在游戏里"这个最小闭环。
3. **P2**：`CustomModelCarrierItem` + `ItemDisplayEntityAccessor` + `CustomModelPreviewRenderer` 的
   `CUSTOM_TEXTURED_ITEM` 分支，替换掉 MVP 的占位方块渲染。
4. **P3**：缓存去重（4.2）+ 批量预热（4.1 第三条）+ 防抖重载。
5. **P4**：与 `docs/bbmodel_import_design.md` 的 UV 解析部分打通，形成"导入 .bbmodel 就能看到真实贴图"
   的完整体验。

## 8. 明确不做的事情

- 不追求"服务器同步"，只做客户端本地预览。
- 不引入 GeckoLib 或其它第三方动画/建模运行时依赖。
- 不做真正意义上的"任意三角网格"渲染，仍然局限于"cuboid + 每面矩形 UV"（与 Minecraft 模型格式、
  Blockbench Java 模型导出格式的能力边界一致）。
