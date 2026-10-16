package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.action.ActorActionType;
import com.director.ui.timeline.interaction.ActionBrushState;
import com.director.actor.skin.ActorSkinStorage;
import com.director.actor.skin.ActorSkinTextureManager;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.query.ActorUsageQuery;
import com.director.actor.rig.ActorRigProfile;
import com.director.actor.rig.RigProfileStyle;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.actor.group.ActorGroupTimelineCommandService;
import com.director.ui.actor.model.BbModelAssetEditing;
import com.director.ui.actor.model.CustomModelCuboidPropertyUI;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.RigProfileStylePropertyUI;
import com.director.ui.selection.SelectionManager;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ActorProjectPropertyAdapter v1.0
 *
 * <p>Project-level 属性面板：管理项目中的所有 Actor（演员）
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 显示 Actor 列表</li>
 *   <li>✅ 新建 Actor</li>
 *   <li>✅ 删除 Actor</li>
 *   <li>✅ 编辑 Actor 的基础属性（name / type）</li>
 *   <li>✅ 将 Actor 设为 Selection</li>
 *   <li>❌ 不编辑 Transform / LookAt / Animation（这些在 Clip 面板）</li>
 *   <li>❌ 不操作 Timeline 播放</li>
 *   <li>❌ 不直接创建 Clip（SpawnClip 已在创建 Actor 时由上层处理）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>Actor 是 Project 级资产，生命周期 > Timeline</li>
 *   <li>不污染 Track / Clip 编辑逻辑</li>
 *   <li>不与 Gizmo 强耦合</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorProjectPropertyAdapter implements IPropertyAdapter<Object> {
    private java.util.UUID incompatibleTypeActor;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorProjectPropertyAdapter.class);
    private static final float DEFAULT_CLIP_DURATION = 5.0f;
    
    // UI state
    private UUID selectedActorId;
    private UUID nameDraftActorId;
    private String nameDraftSource;
    private ActorType actorTypeFilter;
    private final ImString actorSearch = new ImString("", 256);
    
    // Create popup state
    private boolean createPopupOpen = false;
    private String createError = "";
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;
    private final ImString newGroupName = new ImString("Group", 256);
    
    // Edit state
    private final ImString editingActorName = new ImString("", 256);
    private final ImString bbModelRigId = new ImString("", 256);
    private final ImString bbModelModelId = new ImString("", 256);
    private String bbModelImportMessage = "";
    private final ImFloat groupActionDuration = new ImFloat(2.0f);
    private final ImFloat groupActionIntensity = new ImFloat(1.0f);
    private final ImBoolean groupActionLoop = new ImBoolean(true);
    
    // Delete confirm state
    private UUID deletingActorId = null;
    private boolean deleteConfirmOpen = false;
    
    // =========================================================
    // IPropertyAdapter implementation
    // =========================================================
    
    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }

    @Override
    public int getPriority() { return 40; }
    
    @Override
    public boolean supports(Object obj) {
        // 支持两种情况：
        // 1. 特定标记对象 "ACTOR_PROJECT"（用于无选择时显示）
        // 2. Actor 对象（用于选中 Actor 时显示）
        if (obj == null) {
            return false; // null 由 PropertiesPanel 的 renderNoSelectionMessage 处理
        }
        if ("ACTOR_PROJECT".equals(obj)) {
            return true;
        }
        return obj instanceof Actor;
    }
    
    @Override
    public boolean renderProperties(Object obj) {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();

        boolean modified = false;
        boolean projectView = "ACTOR_PROJECT".equals(obj);
        
        // 如果传入的是 Actor 对象，直接选中它
        if (obj instanceof Actor actor) {
            selectedActorId = actor.getActorId();
        }
        
        if (projectView) {
            selectedActorId = ActorSelectionHub.getSelectedActorId();
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.browser.hint"));
            // Actor 列表
            modified |= renderActorList(actorRegistry);

            ImGui.spacing();
            modified |= renderGroupList(actorRegistry);
            
            // 创建 Actor 按钮
            ImGui.spacing();
            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ADD_ACTOR))) {
                openCreateActorPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ADD_GROUP))) {
                createGroup(actorRegistry);
                modified = true;
            }
            
            // 创建 Actor 弹窗
            renderCreateActorPopup(actorRegistry);
        }
        
        // 选中的 Actor 编辑器
        if (!projectView && selectedActorId != null) {
            modified |= renderSelectedActorEditor(actorRegistry);
        }
        
        return modified;
    }
    
    @Override
    public String getAdapterName() {
        return ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ADAPTER_NAME);
    }
    
    // =========================================================
    // Actor list
    // =========================================================
    
    private boolean renderActorList(ActorRegistry actorRegistry) {
        // 使用 UITheme 的方式显示分隔符文本
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ACTOR_LIST), UITheme.DarkTheme.TEXT);
        UITheme.separator();

        List<Actor> actors = actorRegistry.getAllActors();
        if (actors.isEmpty()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.NO_ACTORS));
            return false;
        }
        
        ImGui.inputText("搜索演员##actorSearch", actorSearch);
        if (ImGui.beginCombo("类型筛选##actorTypeFilter", actorTypeFilter == null ? "全部类型" : ActorPropertyAdapterI18n.actorTypeLabel(actorTypeFilter))) {
            if (ImGui.selectable("全部类型", actorTypeFilter == null)) actorTypeFilter = null;
            for (var type : ActorType.values()) {
                if (ImGui.selectable(ActorPropertyAdapterI18n.actorTypeLabel(type), type == actorTypeFilter)) actorTypeFilter = type;
            }
            ImGui.endCombo();
        }
        String query = actorSearch.get().strip().toLowerCase(java.util.Locale.ROOT);
        int visible = 0;
        for (Actor actor : actors) {
            if (actorTypeFilter != null && actor.getType() != actorTypeFilter) continue;
            String label = actor.getName() + " (" + ActorPropertyAdapterI18n.actorTypeLabel(actor.getType()) + ")";
            if (!label.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            visible++;
            ImGui.pushID(actor.getActorId().toString());
            String placeLabel = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.PLACE);
            float buttonWidth = ImGui.calcTextSize(placeLabel).x + ImGui.getStyle().getFramePaddingX() * 2;
            float rowWidth = Math.max(30, ImGui.getContentRegionAvailX() - buttonWidth - ImGui.getStyle().getItemSpacingX());
            if (ImGui.selectable(label + "###actorRow", actor.getActorId().equals(selectedActorId), 0, rowWidth, 0)) selectActor(actor);
            if (ImGui.beginPopupContextItem("ActorContextMenu")) {
                if (ImGui.menuItem(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.EDIT))) selectActor(actor);
                if (ImGui.menuItem(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.PLACE))) ActorSelectionHub.beginPlace(actor);
                if (ImGui.menuItem(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DELETE))) {
                    deletingActorId = actor.getActorId();
                    deleteConfirmOpen = true;
                }
                ImGui.endPopup();
            }
            ImGui.sameLine();
            if (ImGui.smallButton(placeLabel)) ActorSelectionHub.beginPlace(actor);
            ImGui.popID();
        }
        if (visible == 0) ImGui.textDisabled("没有匹配的演员");
        return false;
    }

    private boolean renderGroupList(ActorRegistry actorRegistry) {
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ACTOR_GROUPS), UITheme.DarkTheme.TEXT);
        UITheme.separator();

        List<ActorGroup> groups = actorRegistry.getAllGroups();
        if (groups.isEmpty()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.NO_GROUPS));
            return false;
        }

        for(ActorGroup group:groups) {
            ImGui.pushID(group.getGroupId().toString());
            String label=ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_LABEL_FMT,group.getName(),group.size());
            boolean selected=com.director.ui.properties.PropertyContextService.get().getActorInspection()==group;
            if(ImGui.selectable(label+"##groupRow",selected)) {
                ActorSelectionHub.clearSelection();
                DirectorServices.get().uiSelection().setCurrentSelection(group);
                com.director.ui.properties.PropertyContextService.get().inspectActorObject(group);
            }
            ImGui.popID();
        }
        return false;
    }

    public void renderGroupCommands(ActorGroup group) {
        renderGroupActionAuthoring(group);
        renderGroupRouteAuthoring(group);
        renderGroupPlacement(group);
    }

    private void renderGroupActionAuthoring(ActorGroup group) {
        ImGui.spacing();
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_ACTION), UITheme.DarkTheme.TEXT);

        ActorActionType currentBrush = ActionBrushState.get();
        String currentLabel = currentBrush != null
                ? ActorPropertyAdapterI18n.actionSemanticLabel(currentBrush)
                : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NONE);
        if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ACTION) + "##groupAction" + group.getGroupId(), currentLabel)) {
            for (ActorActionType type : ActorActionType.values()) {
                boolean selected = type == currentBrush;
                if (ImGui.selectable(ActorPropertyAdapterI18n.actionSemanticLabel(type), selected)) {
                    ActionBrushState.set(type);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION) + "##groupActionDuration" + group.getGroupId(),
                groupActionDuration.getData(), 0.05f, 20.0f, "%.2fs");
        ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY) + "##groupActionIntensity" + group.getGroupId(),
                groupActionIntensity.getData(), 0.0f, 1.0f, "%.2f");
        ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.LOOP) + "##groupActionLoop" + group.getGroupId(), groupActionLoop);

        var preview = ActorGroupTimelineCommandService.getInstance().previewActions(
                DirectorServices.get().containerTimeline().getTimelineModel(), group, currentBrush, groupActionDuration.get());
        ImGui.pushID("actionPreview:" + group.getGroupId());
        renderGroupPreview(preview);
        ImGui.popID();
        boolean canCreate = preview.canExecute();
        if (!canCreate) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.CREATE_ACTION_CLIPS) + "##groupCreateAction" + group.getGroupId())) {
            int created = ActorGroupTimelineCommandService.getInstance().createActionClips(
                    group,
                    currentBrush,
                    groupActionDuration.get(),
                    groupActionIntensity.get(),
                    groupActionLoop.get()
            );
            LOGGER.info("[ActorProjectPropertyAdapter] Created {} group action clips for {}", created, group.getName());
        }
        if (!canCreate) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_ACTION_TOOLTIP));
        }
    }

    private void renderGroupRouteAuthoring(ActorGroup group) {
        ImGui.spacing();
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_ROUTE), UITheme.DarkTheme.TEXT);

        UUID sourceActorId = ActorSelectionHub.getSelectedActorId();
        String sourceLabel = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.SOURCE_FMT,
                sourceActorId != null ? sourceActorId.toString()
                        : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.SOURCE_NONE));
        ImGui.textDisabled(sourceLabel);

        var preview = ActorGroupTimelineCommandService.getInstance().previewRoutes(
                DirectorServices.get().containerTimeline().getTimelineModel(), group, sourceActorId);
        ImGui.pushID("routePreview:" + group.getGroupId());
        renderGroupPreview(preview);
        ImGui.popID();
        boolean canCreate = preview.canExecute();
        if (!canCreate) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.COPY_SELECTED_ROUTE) + "##groupCopyRoute" + group.getGroupId())) {
            int created = ActorGroupTimelineCommandService.getInstance()
                    .copyLatestRouteFromActor(group, sourceActorId);
            LOGGER.info("[ActorProjectPropertyAdapter] Copied selected actor route to {} group members", created);
        }
        if (!canCreate) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_ROUTE_TOOLTIP));
        }
    }

    private static void renderGroupPreview(ActorGroupTimelineCommandService.Preview preview) {
        String prefix = "gui.director.properties.adapter.actor.group_preview.";
        if (preview.problem() != null) {
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(prefix + preview.problem()));
            return;
        }
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr(prefix + "summary", preview.eligible().size(), preview.skipped().size()));
        if (preview.overwrittenMembers() > 0)
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(prefix + "overwrite", preview.overwrittenMembers()));
        if (!preview.skipped().isEmpty()) {
            ImGui.pushID(preview.overwrittenMembers() > 0 ? "groupActionPreview" : "groupPreview");
            if (ImGui.treeNode(ActorPropertyAdapterI18n.tr(prefix + "details"))) {
                var registry = ActorRegistryManager.getInstance();
                for (var skipped : preview.skipped()) {
                    String name = registry.getActor(skipped.actorId()).map(Actor::getName).orElse(skipped.actorId().toString());
                    ImGui.textWrapped(name + ": " + ActorPropertyAdapterI18n.tr(prefix + skipped.reason().name().toLowerCase(java.util.Locale.ROOT)));
                }
                ImGui.treePop();
            }
            ImGui.popID();
        }
    }

    private void renderGroupPlacement(ActorGroup group) {
        ImGui.spacing();
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_PLACEMENT), UITheme.DarkTheme.TEXT);

        var preview = ActorGroupTimelineCommandService.getInstance().previewPlacement(
                DirectorServices.get().containerTimeline().getTimelineModel(), group);
        ImGui.pushID("placementPreview:" + group.getGroupId());
        renderGroupPreview(preview);
        ImGui.popID();
        boolean canPlace = preview.canExecute();
        if (!canPlace) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.PLACE_GROUP_AT_CAMERA) + "##groupPlaceCamera" + group.getGroupId())) {
            int placed = ActorGroupTimelineCommandService.getInstance().placeGroupAtCamera(group);
            LOGGER.info("[ActorProjectPropertyAdapter] Placed {} group actors at camera", placed);
        }
        if (!canPlace) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUP_PLACEMENT_TOOLTIP));
        }
    }
    
    // =========================================================
    // Selected actor editor
    // =========================================================
    
    private boolean renderSelectedActorEditor(ActorRegistry actorRegistry) {
        Optional<Actor> actorOpt = actorRegistry.getActor(selectedActorId);
        if (actorOpt.isEmpty()) {
            selectedActorId = null;
            return false;
        }
        
        Actor actor = actorOpt.get();
        boolean modified = false;
        
        UITheme.separator();
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ACTOR_PROPERTIES), UITheme.DarkTheme.TEXT);

        if (!actor.getActorId().equals(nameDraftActorId) || !actor.getName().equals(nameDraftSource)) {
            nameDraftActorId = actor.getActorId();
            nameDraftSource = actor.getName();
            editingActorName.set(nameDraftSource);
        }
        if (ImGui.inputText(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NAME), editingActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String newName = editingActorName.get();
            if (newName != null && !newName.isBlank() && !newName.equals(actor.getName())) {
                var timeline = DirectorServices.get().containerTimeline().getTimelineModel();
                com.director.ui.actor.ActorPropertyEditing.rename(timeline, actor, newName);
                nameDraftSource = actor.getName();
                modified = true;
                LOGGER.debug("[ActorProjectPropertyAdapter] Actor name changed: {}", newName);
            }
        }
        
        // ---- Type ----
        String currentTypeName = ActorPropertyAdapterI18n.actorTypeLabel(actor.getType());
        if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TYPE), currentTypeName)) {
            ActorType[] types = ActorType.values();
            String[] typeLabels = ActorPropertyAdapterI18n.actorTypeLabels();
            for (int i = 0; i < types.length; i++) {
                ActorType type = types[i];
                boolean isSelected = type == actor.getType();
                if (ImGui.selectable(typeLabels[i], isSelected)) {
                    var timeline = DirectorServices.get().containerTimeline().getTimelineModel();
                    if (timeline != null && com.director.actor.rig.ActorRigCompatibility.canChangeType(timeline, actor.getActorId(), type)) {
                        if (actor.getType() != type) {
                            com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().deactivate();
                            com.director.ui.actor.ActorPropertyEditing.changeType(timeline, actor, type, () -> {
                                ActorPreviewManager.getInstance().removePreview(actor.getActorId());
                                if (actor.getActorId().equals(ActorSelectionHub.getSelectedActorId())) {
                                    com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().deactivate();
                                    com.director.ui.actor.pose.ActorBoneSelectionController.getInstance().deactivate();
                                    ActorSelectionHub.selectActor(actor.getActorId());
                                    var tools = com.director.ui.tools.ToolRegistry.get();
                                    if (com.director.ui.tools.ToolRegistry.ACTOR_POSE_TOOL_ID.equals(tools.getActiveNonSelectionToolId())) {
                                        tools.activate(actor.resolveRigDefinition().isPresent()
                                                ? com.director.ui.tools.ToolRegistry.ACTOR_POSE_TOOL_ID
                                                : com.director.ui.tools.ToolRegistry.ACTOR_SELECT_TOOL_ID);
                                    }
                                }
                            });
                        }
                        incompatibleTypeActor = null;
                        modified = true;
                    } else {
                        incompatibleTypeActor = actor.getActorId();
                    }
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (actor.getActorId().equals(incompatibleTypeActor)) {
            ImGui.textWrapped("现有动画与目标类型不兼容，请先移除动画引用，再更换演员类型。");
        }
        ImGui.textWrapped(com.director.actor.rig.ActorRigCompatibility.explanation(actor));
        var capabilities=com.director.actor.ActorCapabilities.of(actor);
        ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.capabilities.summary",
                capabilities.actions().size(),capabilities.editableBones()
                    ? net.minecraft.client.resource.language.I18n.translate("gui.director.actor.capabilities.available")
                    : net.minecraft.client.resource.language.I18n.translate("gui.director.actor.capabilities.unavailable"),
                capabilities.propSlots().size()));

        // ---- Custom rig binding (only relevant for ActorType.CUSTOM) ----
        if (actor.getType() == ActorType.CUSTOM) {
            modified |= renderCustomActorAssets(actor);
        }
        
        ImGui.spacing();
        
        // ---- Actions ----
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.FOCUS_IN_WORLD))) {
            ActorSelectionHub.selectActor(actor.getActorId());
            LOGGER.debug("[ActorProjectPropertyAdapter] Focus actor in world: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.PLACE_AT_CAMERA))) {
            placeActorAtCamera(actor);
            LOGGER.debug("[ActorProjectPropertyAdapter] Place actor at camera: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_ACTOR))) {
            openDeleteConfirm(actor.getActorId());
        }
        
        // 提示信息
        ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.PLACE_TIP));
        
        ImGui.spacing();
        
        // ---- Action Brush (动作涂抹笔刷) ----
        renderActionBrushSelector(actor);
        
        ImGui.spacing();

        modified |= renderSelectedActorGroups(actorRegistry, actor);

        ImGui.spacing();
        
        // ---- Performance Style (风格滑条) ----
        ActorRigProfile rigProfile = actor.getRigProfile();
        if (rigProfile != null) {
            RigProfileStyle style = rigProfile.getStyle();
            if (style != null) {
                modified |= RigProfileStylePropertyUI.render(style);
            }
        }

        ImGui.spacing();

        // ---- Skin / Appearance ----
        modified |= renderActorSkinSection(actor);

        ImGui.spacing();

        // ---- Usage Information (只读) ----
        com.director.ui.properties.actor.ActorPathMovementPropertyUI.render(actor.getActorId());
        renderActorUsage(actor);
        com.director.ui.properties.actor.ActorRuntimeDiagnosticsUI.render(actor.getActorId());
        
        return modified;
    }

    private boolean renderCustomActorAssets(Actor actor) {
        boolean modified = false;
        var customRigs = com.director.actor.rig.skeleton.CustomRigLibrary.getInstance().all();
        String currentRigLabel = actor.getCustomRigId() == null ? "（未绑定）" : actor.getCustomRigId();
        if (ImGui.beginCombo("自定义骨架##customRig", currentRigLabel)) {
            if (ImGui.selectable("（未绑定）", actor.getCustomRigId() == null)) {
                actor.setCustomRigId(null);
                modified = true;
            }
            for (var rig : customRigs) {
                boolean isSelected = rig.id().equals(actor.getCustomRigId());
                if (ImGui.selectable(rig.displayName() + "##rig:" + rig.id(), isSelected)) {
                    actor.setCustomRigId(rig.id());
                    modified = true;
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.button("导入 .bbmodel##importBbModel")) {
            modified |= importBbModel(actor);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("从 Blockbench 工程文件导入自定义骨架与方块外观模型");
        }

        ImGui.inputText("骨架 ID##bbModelRigId", bbModelRigId);
        ImGui.inputText("模型 ID##bbModelModelId", bbModelModelId);

        if (customRigs.isEmpty()) {
            ImGui.textWrapped("项目中暂无自定义骨架，可通过 .bbmodel 导入，或使用内置示例骨架。");
        }

        if (actor.getCustomRigId() != null) {
            var models = com.director.actor.model.CustomModelLibrary.getInstance().forRig(actor.getCustomRigId());
            String currentModelLabel = actor.getCustomModelId() == null ? "（无外观，仅骨架）" : actor.getCustomModelId();
            if (ImGui.beginCombo("自定义模型外观##customModel", currentModelLabel)) {
                if (ImGui.selectable("（无外观，仅骨架）", actor.getCustomModelId() == null)) {
                    actor.setCustomModelId(null);
                    modified = true;
                }
                for (var model : models) {
                    boolean isSelected = model.id().equals(actor.getCustomModelId());
                    if (ImGui.selectable(model.displayName() + "##model:" + model.id(), isSelected)) {
                        actor.setCustomModelId(model.id());
                        modified = true;
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
            if (models.isEmpty()) {
                ImGui.textWrapped("该骨架暂无匹配的方块外观模型；非人形拓扑骨架需要绑定模型才能显示外观。");
            }
        }

        if (!bbModelImportMessage.isEmpty()) {
            ImGui.textWrapped(bbModelImportMessage);
        }

        if (actor.getCustomModelId() != null) {
            TimelineModel timeline = DirectorServices.get().containerTimeline().getTimelineModel();
            if (timeline != null) {
                CustomModelCuboidPropertyUI.render(actor, timeline);
            }
        }
        return modified;
    }

    private boolean importBbModel(Actor actor) {
        TimelineModel timeline = DirectorServices.get().containerTimeline().getTimelineModel();
        if (timeline == null) {
            bbModelImportMessage = "时间线未初始化，无法导入。";
            return false;
        }

        String file = chooseBbModelFile();
        if (file == null || file.isBlank()) {
            return false;
        }

        Path path = Paths.get(file.trim());
        if (bbModelRigId.get().isBlank()) {
            bbModelRigId.set(BbModelAssetEditing.suggestRigId(path));
        }
        if (bbModelModelId.get().isBlank()) {
            bbModelModelId.set(BbModelAssetEditing.suggestModelId(path));
        }

        try {
            var result = BbModelAssetEditing.importFile(
                    timeline,
                    path,
                    bbModelRigId.get().trim(),
                    bbModelModelId.get().trim(),
                    actor);
            bbModelRigId.set(result.rig().id());
            bbModelModelId.set(result.model().id());
            StringBuilder message = new StringBuilder("导入完成，可撤销。");
            if (!result.warnings().isEmpty()) {
                message.append('\n').append(String.join("\n", result.warnings()));
            }
            bbModelImportMessage = message.toString();
            return true;
        } catch (IllegalArgumentException e) {
            bbModelImportMessage = e.getMessage();
        } catch (Exception e) {
            bbModelImportMessage = "导入失败：" + e.getMessage();
            LOGGER.warn("[ActorProjectPropertyAdapter] bbmodel import failed", e);
        }
        return false;
    }

    private static String chooseBbModelFile() {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.bbmodel")).flip();
            return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    "导入 Blockbench 模型", "", filters, "Blockbench .bbmodel", false);
        } catch (Throwable t) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 打开 .bbmodel 选择对话框失败: {}", t.getMessage());
            return null;
        }
    }

    private boolean renderActorSkinSection(Actor actor) {
        if (actor == null) {
            return false;
        }
        boolean modified = false;
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.SECTION), UITheme.DarkTheme.TEXT);

        if (actor.getType() == ActorType.PLAYER) {
            ImInt model = new ImInt(actor.getPlayerModel().ordinal());
            String[] labels = {net.minecraft.client.resource.language.I18n.translate("gui.director.actor.player_model.default"),
                    net.minecraft.client.resource.language.I18n.translate("gui.director.actor.player_model.wide"),
                    net.minecraft.client.resource.language.I18n.translate("gui.director.actor.player_model.slim")};
            if (ImGui.combo(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.player_model"), model, labels)) {
                actor.setPlayerModel(Actor.PlayerModel.values()[Math.max(0, Math.min(model.get(), labels.length - 1))]);
                ActorRegistryManager.save();
                modified = true;
            }
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.player_model.hint"));
            ImInt bendMode=new ImInt(actor.getLimbBendMode().ordinal());
            if(ImGui.combo("肘膝弯曲模式##actorLimbBend",bendMode,new String[]{"平滑弯曲","直接折弯"})) {
                actor.setLimbBendMode(com.director.actor.rig.skeleton.LimbBendMode.values()[bendMode.get()]);
                ActorRegistryManager.save();
                modified=true;
            }
            ImGui.textWrapped("两种模式共用关节关键帧。直接折弯保留清晰折角，显示角度限制为 110°，避免方形肢体内侧穿插。");
        }

        String current = actor.getSkinFileName();
        ImGui.textDisabled(current != null ? current : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.DEFAULT));

        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.CHOOSE_PNG))) {
            String chosen = openPngFileDialog();
            if (chosen != null && !chosen.isBlank()) {
                try {
                    Path source = Paths.get(chosen.trim());
                    String relative = ActorSkinStorage.importSkin(source, actor.getActorId());
                    if (relative != null) {
                        actor.setSkinFileName(relative);
                        ActorSkinTextureManager.invalidate(actor.getActorId());
                        ActorRegistryManager.save();
                        modified = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("[ActorProjectPropertyAdapter] 导入皮肤失败: {}", chosen, e);
                }
            }
        }

        ImGui.sameLine();

        boolean hasSkin = actor.getSkinFileName() != null;
        if (!hasSkin) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.CLEAR))) {
            UUID id = actor.getActorId();
            ActorSkinStorage.deleteSkin(id);
            actor.setSkinFileName(null);
            ActorSkinTextureManager.invalidate(id);
            ActorRegistryManager.save();
            modified = true;
        }
        if (!hasSkin) {
            ImGui.endDisabled();
        }
        return modified;
    }

    private String openPngFileDialog() {
        // 复用 VideoExportDialog 使用的 tinyfd 依赖；此处简单包装，仅选择 PNG。
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            String title = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.DIALOG_TITLE);
            String defaultPath = "";
            String[] patterns = new String[]{"*.png"};
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) {
                filters.put(stack.UTF8(p));
            }
            filters.flip();
            String filterDesc = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Skin.DIALOG_FILTER);
            return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, filters, filterDesc, false);
        } catch (Throwable t) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 打开皮肤选择对话框失败: {}", t.getMessage());
            return null;
        }
    }

    private boolean renderSelectedActorGroups(ActorRegistry actorRegistry, Actor actor) {
        if (actor == null) {
            return false;
        }
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.GROUPS_SECTION), UITheme.DarkTheme.TEXT);
        List<ActorGroup> groups = actorRegistry.getAllGroups();
        if (groups.isEmpty()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.NO_GROUPS_HINT));
            return false;
        }

        boolean modified = false;
        UUID actorId = actor.getActorId();
        for (ActorGroup group : groups) {
            boolean member = group.containsActor(actorId);
            if (ImGui.checkbox(group.getName() + "##actorGroupMember" + group.getGroupId(), member)) {
                if (member) {
                    group.removeActor(actorId);
                } else {
                    group.addActor(actorId);
                }
                modified = true;
            }
        }
        if (modified) {
            ActorRegistryManager.save();
        }
        return modified;
    }
    
    // =========================================================
    // Selection handling
    // =========================================================
    
    /**
     * 在演员面板中选中演员，并同步场景和时间线选择
     * <p>通过 ActorSelectionHub.selectActor() 共享选择状态
     *
     * @param actor Actor 实例
     */
    private void selectActor(Actor actor) {
        if (actor == null) {
            selectedActorId = null;
            return;
        }
        
        // 所有编辑入口指向同一个演员。
        ActorSelectionHub.selectActor(actor.getActorId());
        this.selectedActorId = actor.getActorId();
        com.director.ui.properties.PropertyContextService.get().inspectActorObject(actor);

        LOGGER.debug("[ActorProjectPropertyAdapter] Selected actor in panel: {}", actor.getActorId());
    }
    
    // =========================================================
    // Create actor
    // =========================================================
    
    private void openCreateActorPopup() {
        createError = "";
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.POPUP_CREATE));
    }

    private void createGroup(ActorRegistry actorRegistry) {
        String baseName = newGroupName.get();
        if (baseName == null || baseName.isBlank()) {
            baseName = "Group";
        }
        String name = baseName.trim();
        int suffix = 1;
        String candidate = name;
        while (actorRegistry.hasGroupWithName(candidate)) {
            candidate = name + " " + suffix++;
        }
        actorRegistry.createGroup(candidate);
        ActorRegistryManager.save();
        LOGGER.info("[ActorProjectPropertyAdapter] Created ActorGroup: {}", candidate);
    }
    
    private void renderCreateActorPopup(ActorRegistry actorRegistry) {
        if (!createPopupOpen) {
            return;
        }
        
        if (ImGui.beginPopupModal(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.POPUP_CREATE))) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.CREATE_NEW_ACTOR));
            ImGui.separator();

            if (ImGui.inputText(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NAME), newActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                // Enter key pressed, but we'll handle it in the Create button
            }
            
            // Type combo
            String currentTypeName = ActorPropertyAdapterI18n.actorTypeLabel(newActorType);
            if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TYPE), currentTypeName)) {
                ActorType[] types = ActorType.values();
                String[] typeLabels = ActorPropertyAdapterI18n.actorTypeLabels();
                for (int i = 0; i < types.length; i++) {
                    ActorType type = types[i];
                    boolean selected = type == newActorType;
                    if (ImGui.selectable(typeLabels[i], selected)) {
                        newActorType = type;
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
            
            ImGui.textWrapped(com.director.actor.rig.ActorRigCompatibility.explanation(newActorType));
            ImGui.separator();
            
            // Buttons
            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CREATE))) {
                String name = newActorName.get().strip();
                if (name.isBlank()) {
                    createError = "请输入演员名称。";
                } else if (com.director.ui.actor.placement.ActorPlacementController.getInstance().startPlacement(name, newActorType)) {
                    ImGui.closeCurrentPopup();
                    createPopupOpen = false;
                    createError = "";
                } else {
                    createError = "无法开始放置，请确认已进入世界并打开时间线。";
                }
            }

            ImGui.sameLine();
            
            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL))) {
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }
            
            if (!createError.isEmpty()) ImGui.textWrapped(createError);
            ImGui.endPopup();
        }
    }
    
    // =========================================================
    // Create Actor and Track（原子性操作）
    // =========================================================
    
    /**
     * 生成唯一的演员名称（检查演员和轨道名称）
     *
     * @param baseName 基础名称
     * @param actorRegistry ActorRegistry
     * @param timelineModel TimelineModel
     * @return 唯一的名称
     */
    private String generateUniqueActorName(String baseName, ActorRegistry actorRegistry, TimelineModel timelineModel) {
        if (baseName == null || baseName.isBlank()) {
            baseName = "Actor";
        }
        String trimmed = baseName.trim();
        String candidate = trimmed;
        int index = 1;

        // 检查名称是否已存在（检查演员和轨道）
        while (isNameTaken(candidate, actorRegistry, timelineModel)) {
            candidate = trimmed + index;
            index++;
        }

        return candidate;
    }

    /**
     * 检查名称是否已被使用（检查演员和轨道）
     *
     * @param name 名称
     * @param actorRegistry ActorRegistry
     * @param timelineModel TimelineModel
     * @return 如果名称已被使用则返回 true
     */
    private boolean isNameTaken(String name, ActorRegistry actorRegistry, TimelineModel timelineModel) {
        // 检查演员名称
        if (actorRegistry.hasActorWithName(name)) {
            return true;
        }

        // 检查轨道名称（特别是演员轨道）
        if (timelineModel != null) {
            for (TimelineTrack track : timelineModel.getTracks()) {
                if (track != null && name.equals(track.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 创建 Actor 和 Track（原子性操作）
     * <p>与 ActorSelectorPanel.createActorAndTrack() 保持一致
     *
     * @param actorName Actor 名称
     * @param actorType Actor 类型
     */
    private void createActorAndTrack(String actorName, ActorType actorType) {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        TimelineModel timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();

        try {
            // 0️⃣ 生成唯一的名称
            String uniqueName = generateUniqueActorName(actorName, actorRegistry, timelineModel);

            // 1️⃣ Create Actor
            Actor actor = actorRegistry.createActor(uniqueName, actorType);
            ActorRef ref = new ActorRef(actor.getActorId());

            // 2️⃣ Create ActorTrack
            ActorTrack actorTrack = new ActorTrack();
            actorTrack.setActorRef(ref);
            actorTrack.setDisplayName(actor.getName());

            // 3️⃣ Create TimelineTrack with ActorTrack payload
            if (timelineModel == null) {
                LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化，无法创建 Track");
                // 即使没有 TimelineModel，也选中 Actor
                selectActor(actor);
                return;
            }

            // 使用唯一的名称创建轨道
            TimelineTrack timelineTrack = new TimelineTrack(uniqueName, TrackType.ACTOR);
            timelineTrack.setActorTrackPayload(actorTrack);

            // 4️⃣ Add to TimelineModel
            timelineModel.addTrack(timelineTrack);

            // 5️⃣ Insert SpawnClip at current time
            float currentTime = timelineModel.getCurrentTime();
            EntitySpawnClip spawnClip = new EntitySpawnClip(
                    "Spawn",
                    currentTime,
                    currentTime + DEFAULT_CLIP_DURATION
            );
            spawnClip.setVisibleOnSpawn(true);
            actorTrack.addClip(spawnClip);
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, actorTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);

            // 6️⃣ Mark model as dirty (trigger save)
            timelineModel.markDirty();

            // 7️⃣ Select Actor（这会触发 PropertiesPanel 切换到 Actor 面板）
            selectActor(actor);

            LOGGER.info("[ActorProjectPropertyAdapter] 成功创建 Actor: {} ({}), ActorTrack 已添加到时间线",
                    actor.getName(), actorType);
        } catch (Exception e) {
            LOGGER.error("[ActorProjectPropertyAdapter] 创建 Actor 失败", e);
            // TODO: 如果实现了撤销/重做系统，这里应该回滚所有操作
        }
    }
    
    // =========================================================
    // Actor Usage (只读查询)
    // =========================================================
    
    /**
     * 渲染 Actor 使用情况（只读）
     * <p>显示该 Actor 在哪些 Timeline/Track 中被使用
     *
     * @param actor Actor 实例
     */
    private void renderActorUsage(Actor actor) {
        if (actor == null) {
            return;
        }
        
        UITheme.separator();
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.USED_IN), UITheme.DarkTheme.TEXT);

        TimelineModel timeline = DirectorServices.get().containerTimeline().getTimelineModel();
        if (timeline == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.NO_TIMELINE));
            return;
        }
        
        // 查询使用该 Actor 的所有 ActorTrack
        List<ActorTrack> actorTracks = ActorUsageQuery.findTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        if (actorTracks.isEmpty()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.NOT_USED_IN_TIMELINE));
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.binding.hint"));
            ImGui.beginDisabled(timeline.isPlaying() || timeline.isScrubbing());
            if(ImGui.smallButton(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.binding.create"))) {
                try {
                    com.director.ui.actor.ActorTrackBindingEditing.create(timeline,actor);
                    ActorSelectionHub.selectActor(actor.getActorId());
                } catch(IllegalStateException | IllegalArgumentException failure) {
                    LOGGER.warn("Cannot bind actor track: {}",failure.getMessage());
                }
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.smallButton(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_ACTOR) + "##unused")) {
                openDeleteConfirm(actor.getActorId());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_UNUSED_TOOLTIP));
            }
            return;
        }
        
        // 查询对应的 TimelineTrack（用于 Focus Track 功能）
        List<TimelineTrack> timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        // 显示 Timeline 名称（目前只有一个 Timeline，未来可扩展）
        String timelineName = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.CURRENT_TIMELINE);

        ImGui.bulletText(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.TIMELINE_FMT, timelineName));
        
        // 显示每个 Track
        ImGui.indent();
        for (int i = 0; i < actorTracks.size(); i++) {
            ActorTrack actorTrack = actorTracks.get(i);
            TimelineTrack timelineTrack = i < timelineTracks.size() ? timelineTracks.get(i) : null;
            
            String trackDisplayName = actorTrack.getDisplayName();
            if (trackDisplayName == null || trackDisplayName.isBlank()) {
                trackDisplayName = timelineTrack != null && timelineTrack.getName() != null
                        ? timelineTrack.getName()
                        : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DEFAULT_TRACK_NAME);
            }

            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.TRACK_FMT, trackDisplayName));

            if (timelineTrack != null) {
                String buttonId = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.FOCUS_TRACK) + "##" + timelineTrack.hashCode();
                if (ImGui.smallButton(buttonId)) {
                    focusTrack(timelineTrack);
                }
            }
        }
        ImGui.unindent();
    }
    
    /**
     * 聚焦到指定的 Track
     * <p>选中该 Track，并切换到 Track 面板
     *
     * @param track TimelineTrack 实例
     */
    private void focusTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        // 使用 SelectionManager 选中 Track
        SelectionManager selectionManager = DirectorServices.get().uiSelection();
        selectionManager.selectTrack(track);
        
        LOGGER.debug("[ActorProjectPropertyAdapter] Focused track: {}", track.getName());
    }
    
    // =========================================================
    // Place Actor at Camera
    // =========================================================
    
    /**
     * 将 Actor 放置到当前摄像机位置
     * <p>创建或更新 EntitySpawnClip，将位置设置为当前摄像机位置
     *
     * @param actor Actor 实例
     */
    private void placeActorAtCamera(Actor actor) {
        if (actor == null) {
            return;
        }
        
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] MinecraftClient 未初始化");
            return;
        }
        
        // 获取当前摄像机位置
        var cameraSample = com.director.timeline.playback.camera.CameraAccessor.sampleFromGameCamera(client);
        if (cameraSample == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 无法获取摄像机位置");
            return;
        }
        
        var timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
        if (timelineModel == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化");
            return;
        }
        
        // 查找该 Actor 的 ActorTrack
        var timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(timelineModel, actor.getActorId());
        if (timelineTracks.isEmpty()) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 未找到 Actor 对应的 Track");
            return;
        }
        
        // 使用第一个 Track（通常只有一个）
        TimelineTrack timelineTrack = timelineTracks.getFirst();
        ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
        if (timelineTrack.isLocked()) return;
        if (actorTrack == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] ActorTrack payload 为空");
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        org.joml.Vector3f cameraPos = new org.joml.Vector3f(
                (float) cameraSample.position().x,
                (float) cameraSample.position().y,
                (float) cameraSample.position().z
        );
        
        // 查找当前时间是否有 EntitySpawnClip
        var clipsAtTime = actorTrack.findClipsAt(currentTime);
        EntitySpawnClip spawnClip = null;
        for (var clip : clipsAtTime) {
            if (clip instanceof EntitySpawnClip) {
                spawnClip = (EntitySpawnClip) clip;
                break;
            }
        }
        
        if (spawnClip != null) {
            // 更新现有的 SpawnClip
            spawnClip.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
            spawnClip.setYawDeg(cameraSample.yaw());
            spawnClip.setPitchDeg(cameraSample.pitch());
            LOGGER.info("[ActorProjectPropertyAdapter] 更新 SpawnClip 位置到摄像机: ({}, {}, {})",
                    cameraPos.x, cameraPos.y, cameraPos.z);
        } else {
            // 创建新的 SpawnClip
            spawnClip = new EntitySpawnClip("Spawn", currentTime, currentTime + DEFAULT_CLIP_DURATION);
            spawnClip.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
            spawnClip.setYawDeg(cameraSample.yaw());
            spawnClip.setPitchDeg(cameraSample.pitch());
            spawnClip.setVisibleOnSpawn(true);
            actorTrack.addClip(spawnClip);
            LOGGER.info("[ActorProjectPropertyAdapter] 创建 SpawnClip 在摄像机位置: ({}, {}, {})",
                    cameraPos.x, cameraPos.y, cameraPos.z);
        }
        
        if (spawnClip.getTimelineClip() == null) {
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, actorTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);
        }
        timelineModel.markDirty();
        
        // 选中 Actor，激活 Gizmo
        ActorSelectionHub.selectActor(actor.getActorId());
    }
    
    // =========================================================
    // Delete actor
    // =========================================================
    
    private void openDeleteConfirm(UUID actorId) {
        deletingActorId = actorId;
        deleteConfirmOpen = true;
    }
    
    /** Render once from the global dialog layer, outside all panel and item ID scopes. */
    public void renderDeleteConfirmPopup() {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        if (!deleteConfirmOpen || deletingActorId == null) {
            return;
        }
        
        String deletePopup = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.POPUP_DELETE)
                + "###DirectorDeleteActorConfirm";
        if (!ImGui.isPopupOpen(deletePopup)) ImGui.openPopup(deletePopup);
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
        imgui.type.ImBoolean open = new imgui.type.ImBoolean(true);
        if (ImGui.beginPopupModal(deletePopup, open, imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                if (!open.get() || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                    ImGui.closeCurrentPopup();
                    deleteConfirmOpen = false;
                    deletingActorId = null;
                    return;
                }
                Optional<Actor> actorOpt = actorRegistry.getActor(deletingActorId);
                if (actorOpt.isEmpty()) {
                    ImGui.closeCurrentPopup();
                    deleteConfirmOpen = false;
                    deletingActorId = null;
                    return;
                }
            
                Actor actor = actorOpt.get();
            
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_CONFIRM));
                ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f,
                        ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_ACTOR_FMT,
                                actor.getName(), ActorPropertyAdapterI18n.actorTypeLabel(actor.getType())));
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_UNDO_WARNING));

                ImGui.separator();

                if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DELETE))) {
                    deleteActor(actorRegistry, deletingActorId);
                
                    ImGui.closeCurrentPopup();
                    deleteConfirmOpen = false;
                    deletingActorId = null;
                }
            
                ImGui.sameLine();
            
                if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL))) {
                    ImGui.closeCurrentPopup();
                    deleteConfirmOpen = false;
                    deletingActorId = null;
                }
            
            } finally {
                ImGui.endPopup();
            }
        }
        if (!open.get()) {
            deleteConfirmOpen = false;
            deletingActorId = null;
        }
    }
    
    /**
     * Explicit instance deletion uses one history command, including unused actors.
     * Skin assets are retained so undo can restore the original appearance.
     */
    private void deleteActor(ActorRegistry actorRegistry, UUID actorId) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        com.director.ui.actor.ActorDeletionService.delete(model, actorRegistry, actorId);
        if (actorId != null && !actorRegistry.contains(actorId) && actorId.equals(selectedActorId)) selectedActorId = null;
    }

    // =========================================================
    // Action Brush Selector
    // =========================================================
    
    /**
     * 渲染 Action Brush 选择器（用于涂抹式编辑）
     * <p>导演在这里选择当前绘制动作（RUN / DANCE / CARRY 等）
     */
    private void renderActionBrushSelector(Actor actor) {
        UITheme.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.ACTION_BRUSH), UITheme.DarkTheme.TEXT);

        ActorActionType currentBrush = ActionBrushState.get();
        if (currentBrush != null && !com.director.actor.ActorCapabilities.of(actor).supports(currentBrush))
            ImGui.textWrapped("当前笔刷不适用于此演员，请选择下方支持的动作。");
        String currentLabel = currentBrush != null
                ? ActorPropertyAdapterI18n.actionSemanticLabel(currentBrush)
                : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NONE);

        if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.CURRENT_BRUSH), currentLabel)) {
            for (ActorActionType type : ActorActionType.values()) {
                if (!com.director.actor.ActorCapabilities.of(actor).supports(type)) continue;
                boolean selected = type == currentBrush;
                if (ImGui.selectable(ActorPropertyAdapterI18n.actionSemanticLabel(type), selected)) {
                    ActionBrushState.set(type);
                    LOGGER.debug("[ActorProjectPropertyAdapter] Action Brush 设置为: {}", type);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.BRUSH_TOOLTIP));
        }

        ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.BRUSH_HINT_FMT, currentLabel));
    }
}
