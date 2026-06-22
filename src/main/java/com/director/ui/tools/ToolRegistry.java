package com.director.ui.tools;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
import com.director.ui.tools.impl.ActorLookAtTool;
import com.director.ui.tools.impl.ActorMoveTool;
import com.director.ui.tools.impl.ActorPathTool;
import com.director.ui.tools.impl.ActorPoseTool;
import com.director.ui.tools.impl.ActorRotateTool;
import com.director.ui.tools.impl.ActorScaleTool;
import com.director.ui.tools.impl.ActorSelectTool;
import com.director.ui.tools.impl.CameraPathEditTool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolRegistry（UI Facade）
 *
 * <p>目标：让 UI 面板（尤其 ToolbarPanel / KeyBindings）不再直接依赖具体业务 Manager，
 * 而是统一通过 ToolRegistry 进行“查询当前工具 / 切换工具 / 判断可用性”。</p>
 *
 * <p>注意：当前阶段的工具仍以 core 的 SelectionTool 为主；本类通过适配器把它们提升为 EditorTool。</p>
 */
public final class ToolRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ToolRegistry INSTANCE = new ToolRegistry();

    private static final String WORLD_PLACEHOLDER_ID = "__world_placeholder__";
    private static final String TIMELINE_PLACEHOLDER_ID = "__timeline_placeholder__";

    public static final String ACTOR_SELECT_TOOL_ID = "actor_select";
    public static final String ACTOR_MOVE_TOOL_ID = ActorMoveTool.ID;
    public static final String ACTOR_ROTATE_TOOL_ID = ActorRotateTool.ID;
    public static final String ACTOR_SCALE_TOOL_ID = ActorScaleTool.ID;
    public static final String ACTOR_LOOK_AT_TOOL_ID = ActorLookAtTool.ID;
    public static final String ACTOR_POSE_TOOL_ID = ActorPoseTool.ID;
    public static final String ACTOR_PATH_TOOL_ID = "actor_path";

    private final SelectionManager selectionManager = DirectorServices.get().selection();

    private final List<EditorTool> selectionTools = new ArrayList<>();
    private final List<EditorTool> extraTools = new ArrayList<>();
    private volatile boolean initialized = false;

    /**
     * 当前激活的“非 Selection”工具（World/Timeline 等）。
     * <p>当该值非空时：Toolbar 高亮该工具，Selection 工具高亮会被抑制，避免“双高亮”。</p>
     */
    private volatile String activeNonSelectionToolId = null;

    /**
     * 工具 UI 元数据表（集中管理：分组/排序/教学提示）。
     * key: toolId
     */
    private static final Map<String, ToolMetadata> META = new ConcurrentHashMap<>();
    static {
        // Selection 工具（稳定排序）
        META.put("click", ToolMetadata.of(
                ToolGroup.SELECTION, 10,
                "单击方块以选择",
                "Shift+单击：加选/减选"
        ));
        META.put("box", ToolMetadata.of(
                ToolGroup.SELECTION, 20,
                "拖拽以框选一个区域",
                "Shift：叠加到当前选区（取决于当前操作模式）"
        ));
        META.put("wand", ToolMetadata.of(
                ToolGroup.SELECTION, 30,
                "选择相邻/相似的方块区域",
                "可在工具属性中调整比较规则与性能限制"
        ));
        META.put("brush", ToolMetadata.of(
                ToolGroup.SELECTION, 40,
                "生成几何体/刷选区域加入选区",
                "可在工具属性中调整形状/半径/高度"
        ));
        META.put("face", ToolMetadata.of(
                ToolGroup.SELECTION, 50,
                "按面扩散选择",
                "可在工具属性中调整匹配规则/步数"
        ));
        META.put("lasso", ToolMetadata.of(
                ToolGroup.SELECTION, 60,
                "绘制套索路径进行选择",
                "可在工具属性中查看采样点与状态"
        ));

        // World 工具（如果未来注册进 SelectionRegistry 也能自动归类）
        META.put("container_cull", ToolMetadata.of(
                ToolGroup.WORLD, 10,
                "从当前激活容器中剔除方块",
                "右键确认剔除，ESC 取消"
        ));
        META.put(ACTOR_SELECT_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 20,
                "Select actor and show Gizmo",
                "G/R/S: Move/Rotate/Scale"
        ));
        META.put(ACTOR_MOVE_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 30,
                "Move the selected actor",
                "Click an actor first, then drag the Gizmo arrows"
        ));
        META.put(ACTOR_ROTATE_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 40,
                "Rotate the selected actor",
                "Click an actor first, then drag the rotation Gizmo"
        ));
        META.put(ACTOR_SCALE_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 50,
                "Scale the selected actor",
                "Click an actor first, then drag the scale Gizmo"
        ));
        META.put(ACTOR_LOOK_AT_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 60,
                "Make the selected actor look at a target",
                "Click a world point to create or update a LookAt clip"
        ));
        META.put(ACTOR_POSE_TOOL_ID, ToolMetadata.of(
                ToolGroup.WORLD, 70,
                "Author actor pose/action clips",
                "Use Properties to create semantic ActorAction clips"
        ));
        META.put("actor_path", ToolMetadata.of(
                ToolGroup.WORLD, 80,
                "Draw an actor route from world waypoints",
                "Left click adds waypoints, right click creates a RouteClip"
        ));

        // 占位工具（让 World/Timeline 架子先完整，后续直接替换/追加真实工具）
        META.put(WORLD_PLACEHOLDER_ID, ToolMetadata.of(
                ToolGroup.WORLD, 9999,
                "World 工具系统：开发中",
                "这里将承载容器/场景/渲染等工具"
        ));
        META.put(TIMELINE_PLACEHOLDER_ID, ToolMetadata.of(
                ToolGroup.TIMELINE, 9999,
                "时间线工具系统：开发中",
                "这里将承载时间线编辑/剪辑/标记等工具"
        ));
        META.put(CameraPathEditTool.ID, ToolMetadata.of(
                ToolGroup.TIMELINE, 10,
                "Edit camera path anchors",
                "Click a camera keyframe anchor in the world, then drag the Gizmo arrows"
        ));
    }

    private ToolRegistry() {}

    public static ToolRegistry get() {
        return INSTANCE;
    }

    public static boolean isActorToolId(String toolId) {
        return Objects.equals(toolId, ACTOR_SELECT_TOOL_ID)
                || Objects.equals(toolId, ACTOR_MOVE_TOOL_ID)
                || Objects.equals(toolId, ACTOR_ROTATE_TOOL_ID)
                || Objects.equals(toolId, ACTOR_SCALE_TOOL_ID)
                || Objects.equals(toolId, ACTOR_LOOK_AT_TOOL_ID)
                || Objects.equals(toolId, ACTOR_POSE_TOOL_ID)
                || Objects.equals(toolId, ACTOR_PATH_TOOL_ID);
    }

    public synchronized void initializeIfNeeded() {
        if (initialized) return;

        selectionTools.clear();

        // 1) Selection 工具：来自 SelectionRegistry（保持现状，不改现有工具）
        for (SelectionTool tool : selectionManager.getRegistry().getAllTools()) {
            if (tool == null) continue;
            selectionTools.add(new SelectionToolAdapter(tool, selectionManager));
        }

        // 2) Extra 工具：World/Timeline（Actor 工具 + 占位）
        registerBuiltInExtraTools();
        ensurePlaceholders();

        initialized = true;
        LOGGER.info("[ToolRegistry] 初始化完成，Selection={}，Extra={}",
                selectionTools.size(), extraTools.size());
    }

    public List<EditorTool> getAllTools() {
        initializeIfNeeded();
        List<EditorTool> out = new ArrayList<>(selectionTools.size() + extraTools.size());
        out.addAll(selectionTools);
        out.addAll(extraTools);
        return Collections.unmodifiableList(out);
    }

    public List<EditorTool> getToolsByGroup(ToolGroup group) {
        initializeIfNeeded();
        if (group == null) return getAllTools();
        List<EditorTool> out = new ArrayList<>();
        for (EditorTool tool : getAllTools()) {
            if (tool != null && group == tool.getGroup()) {
                out.add(tool);
            }
        }
        // 稳定排序：先按 metadata.order，再按 displayName
        out.sort(Comparator
                .comparingInt((EditorTool t) -> getMetadataOrDefault(t.getId()).getOrder())
                .thenComparing(EditorTool::getDisplayName, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    public String getActiveToolId() {
        return activeNonSelectionToolId != null ? activeNonSelectionToolId : selectionManager.getCurrentToolId();
    }

    public EditorTool getActiveTool() {
        String activeId = getActiveToolId();
        if (activeId == null) return null;
        for (EditorTool tool : getAllTools()) {
            if (tool != null && Objects.equals(activeId, tool.getId())) {
                return tool;
            }
        }
        return null;
    }

    public boolean hasNonSelectionActiveTool() {
        return activeNonSelectionToolId != null;
    }

    public String getActiveNonSelectionToolId() {
        return activeNonSelectionToolId;
    }

    public void clearNonSelectionActiveTool() {
        EditorTool prev = getToolById(activeNonSelectionToolId);
        activeNonSelectionToolId = null;
        if (prev instanceof ToolLifecycle lifecycle) {
            try {
                lifecycle.onDeactivate();
            } catch (Exception e) {
                LOGGER.debug("[ToolRegistry] onDeactivate failed: {}", e.getMessage());
            }
        }
    }

    public EditorTool getToolById(String toolId) {
        if (toolId == null) return null;
        for (EditorTool t : getAllTools()) {
            if (t != null && Objects.equals(toolId, t.getId())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 确保存在默认工具（仅在当前没有工具时才设置）。
     */
    public void ensureDefaultTool(String toolId) {
        // 如果用户当前处于非 Selection 工具模式，不要强行切换 Selection 默认工具
        if (activeNonSelectionToolId != null) return;
        if (selectionManager.hasCurrentTool()) return;
        activate(toolId);
    }

    /**
     * 激活指定工具（统一入口）。
     *
     * @return true 表示已尝试切换（world!=null 且 toolId 合法），false 表示因环境不足跳过
     */
    public boolean activate(String toolId) {
        if (toolId == null || toolId.isBlank()) return false;

        // 优先：如果是 extra 工具，走 editor-level 激活
        EditorTool extra = findExtraTool(toolId);
        if (extra != null) {
            return activateNonSelection(extra);
        }

        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }

        // 切回 Selection 工具：清除非 Selection 高亮（并触发 onDeactivate 钩子）
        if (activeNonSelectionToolId != null) {
            clearNonSelectionActiveTool();
        }

        selectionManager.setCurrentTool(toolId, world);
        return true;
    }

    public boolean activate(EditorTool tool) {
        if (tool == null) return false;
        if (!tool.isEnabled()) return false;

        // Selection vs Non-Selection 分发
        if (tool.getGroup() == ToolGroup.SELECTION) {
            return activate(tool.getId());
        }
        return activateNonSelection(tool);
    }

    private EditorTool findExtraTool(String toolId) {
        if (toolId == null) return null;
        for (EditorTool t : extraTools) {
            if (t != null && Objects.equals(toolId, t.getId())) {
                return t;
            }
        }
        return null;
    }

    private boolean activateNonSelection(EditorTool tool) {
        if (tool == null) return false;
        if (!tool.isEnabled()) return false;

        // 切换前停用旧的 non-selection
        EditorTool prev = getToolById(activeNonSelectionToolId);
        if (prev instanceof ToolLifecycle prevLifecycle) {
            try {
                prevLifecycle.onDeactivate();
            } catch (Exception e) {
                LOGGER.debug("[ToolRegistry] prev.onDeactivate failed: {}", e.getMessage());
            }
        }

        activeNonSelectionToolId = tool.getId();

        if (tool instanceof ToolLifecycle lifecycle) {
            try {
                lifecycle.onActivate();
            } catch (Exception e) {
                LOGGER.debug("[ToolRegistry] tool.onActivate failed: {}", e.getMessage());
            }
        }

        LOGGER.info("[ToolRegistry] Activated non-selection tool: {} ({})", tool.getDisplayName(), tool.getId());
        return true;
    }

    public ToolMetadata getMetadata(String toolId) {
        if (toolId == null) return null;
        return META.get(toolId);
    }

    public ToolMetadata getMetadataOrDefault(String toolId) {
        ToolMetadata meta = getMetadata(toolId);
        return meta != null ? meta : ToolMetadata.of(ToolGroup.SELECTION, 1000);
    }

    /**
     * 注册非 Selection 工具（World/Timeline 等）。
     * <p>当前 Toolbar 已能渲染，但激活逻辑仍以 Selection 为主；后续引入真正的 World/Timeline 工具时，
     * 可在这里扩展 activate(EditorTool) 的分发策略。</p>
     */
    public synchronized void registerExtraTool(EditorTool tool) {
        if (tool == null) return;
        initializeIfNeeded();
        addExtraToolIfMissing(tool);
    }

    private void registerBuiltInExtraTools() {
        addExtraToolIfMissing(new ActorSelectTool());
        addExtraToolIfMissing(new ActorMoveTool());
        addExtraToolIfMissing(new ActorRotateTool());
        addExtraToolIfMissing(new ActorScaleTool());
        addExtraToolIfMissing(new ActorLookAtTool());
        addExtraToolIfMissing(new ActorPoseTool());
        addExtraToolIfMissing(new ActorPathTool());
        addExtraToolIfMissing(new CameraPathEditTool());
    }

    private void addExtraToolIfMissing(EditorTool tool) {
        if (tool == null) return;
        for (EditorTool existing : extraTools) {
            if (existing != null && Objects.equals(existing.getId(), tool.getId())) {
                return;
            }
        }
        extraTools.add(tool);
        removePlaceholderForGroup(tool.getGroup());
    }

    private void removePlaceholderForGroup(ToolGroup group) {
        String placeholderId = group == ToolGroup.WORLD ? WORLD_PLACEHOLDER_ID
                : group == ToolGroup.TIMELINE ? TIMELINE_PLACEHOLDER_ID
                : null;
        if (placeholderId == null) {
            return;
        }
        extraTools.removeIf(t -> t != null && Objects.equals(t.getId(), placeholderId));
    }

    private synchronized void ensurePlaceholders() {
        if (!hasExtraToolInGroup(ToolGroup.WORLD)
                && extraTools.stream().noneMatch(t -> t != null && Objects.equals(t.getId(), WORLD_PLACEHOLDER_ID))) {
            extraTools.add(new PlaceholderTool(
                    WORLD_PLACEHOLDER_ID,
                    Icons.WRENCH,
                    "世界工具(开发中)",
                    "World 工具系统尚未接入，敬请期待。"
            ));
        }

        if (!hasExtraToolInGroup(ToolGroup.TIMELINE)
                && extraTools.stream().noneMatch(t -> t != null && Objects.equals(t.getId(), TIMELINE_PLACEHOLDER_ID))) {
            extraTools.add(new PlaceholderTool(
                    TIMELINE_PLACEHOLDER_ID,
                    Icons.TIME,
                    "时间线工具(开发中)",
                    "时间线工具系统尚未接入，敬请期待。"
            ));
        }
    }

    private boolean hasExtraToolInGroup(ToolGroup group) {
        for (EditorTool tool : extraTools) {
            if (tool == null) {
                continue;
            }
            if (tool.getGroup() == group
                    && !Objects.equals(tool.getId(), WORLD_PLACEHOLDER_ID)
                    && !Objects.equals(tool.getId(), TIMELINE_PLACEHOLDER_ID)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================
    // Adapters
    // =========================================================

    private static final class PlaceholderTool implements EditorTool {
        private final String id;
        private final String icon;
        private final String displayName;
        private final String description;

        private PlaceholderTool(String id, String icon, String displayName, String description) {
            this.id = id;
            this.icon = icon;
            this.displayName = displayName;
            this.description = description;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getIcon() {
            return icon;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ToolGroup getGroup() {
            ToolMetadata meta = META.get(id);
            return meta != null ? meta.getGroup() : ToolGroup.SELECTION;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isActive() {
            // 占位工具默认不可用，因此不会被激活；保持 false
            return false;
        }
    }

    private final class SelectionToolAdapter implements EditorTool {
        private final SelectionTool tool;
        private final SelectionManager selectionManager;

        private SelectionToolAdapter(SelectionTool tool, SelectionManager selectionManager) {
            this.tool = Objects.requireNonNull(tool, "tool");
            this.selectionManager = Objects.requireNonNull(selectionManager, "selectionManager");
        }

        @Override
        public String getId() {
            return tool.getId();
        }

        @Override
        public String getDisplayName() {
            return tool.getDisplayName();
        }

        @Override
        public String getIcon() {
            return tool.getIcon();
        }

        @Override
        public String getDescription() {
            String desc = tool.getDescription();
            return desc != null ? desc : "";
        }

        @Override
        public ToolGroup getGroup() {
            ToolMetadata meta = META.get(getId());
            if (meta != null) return meta.getGroup();
            // 默认：Selection
            return ToolGroup.SELECTION;
        }

        @Override
        public boolean isEnabled() {
            // world 为空时禁用；部分工具可在此追加更严格的启用条件
            if (MinecraftClient.getInstance().world == null) return false;
            return selectionManager.getRegistry().getTool(getId()) != null;
        }

        @Override
        public boolean isActive() {
            // 非 Selection 工具激活时，抑制 Selection 高亮（避免双高亮）
            if (activeNonSelectionToolId != null) {
                return false;
            }
            String activeId = selectionManager.getCurrentToolId();
            return Objects.equals(activeId, getId());
        }
    }
}


