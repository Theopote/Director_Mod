package com.director.ui.properties.adapters;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.action.ActorActionType;
import com.director.ui.timeline.interaction.ActionBrushState;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.query.ActorUsageQuery;
import com.director.actor.rig.ActorRigProfile;
import com.director.actor.rig.RigProfileStyle;
import com.director.ui.actor.group.ActorGroupTimelineCommandService;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.RigProfileStylePropertyUI;
import com.director.ui.selection.SelectionManager;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.EntityTrack;
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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorProjectPropertyAdapter.class);
    private static final float DEFAULT_CLIP_DURATION = 5.0f;
    
    // UI state
    private UUID selectedActorId;
    
    // Create popup state
    private boolean createPopupOpen = false;
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;
    private final ImString newGroupName = new ImString("Group", 256);
    
    // Edit state
    private final ImString editingActorName = new ImString("", 256);
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
        
        // 标题
        UITheme.title("🎭 Actors");
        
        if (projectView) {
            // Actor 列表
            modified |= renderActorList(actorRegistry);

            ImGui.spacing();
            modified |= renderGroupList(actorRegistry);
            
            // 创建 Actor 按钮
            ImGui.spacing();
            if (ImGui.button("+ Add Actor")) {
                openCreateActorPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("+ Add Group")) {
                createGroup(actorRegistry);
                modified = true;
            }
            
            // 创建 Actor 弹窗
            renderCreateActorPopup(actorRegistry);
        }
        
        // 选中的 Actor 编辑器
        if (selectedActorId != null) {
            modified |= renderSelectedActorEditor(actorRegistry);
        }
        
        // 删除确认弹窗
        renderDeleteConfirmPopup(actorRegistry);
        
        return modified;
    }
    
    @Override
    public String getAdapterName() {
        return "Actor Project Adapter";
    }
    
    // =========================================================
    // Actor list
    // =========================================================
    
    private boolean renderActorList(ActorRegistry actorRegistry) {
        // 使用 UITheme 的方式显示分隔符文本
        UITheme.label("Actor List", UITheme.DarkTheme.TEXT);
        UITheme.separator();
        
        List<Actor> actors = actorRegistry.getAllActors();
        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors in project.");
            return false;
        }
        
        boolean modified = false;
        
        for (Actor actor : actors) {
            boolean selected = actor.getActorId().equals(selectedActorId);
            
            int flags = ImGuiTreeNodeFlags.Leaf
                      | ImGuiTreeNodeFlags.NoTreePushOnOpen
                      | (selected ? ImGuiTreeNodeFlags.Selected : 0);
            
            String displayText = String.format("%s  (%s)", actor.getName(), actor.getType().name());
            
            ImGui.treeNodeEx(actor.getActorId().toString(), flags, displayText);
            
            if (ImGui.isItemClicked()) {
                selectActor(actor);
                modified = true;
            }
            
            // Place 按钮（在同一行）
            ImGui.sameLine();
            float itemWidth = ImGui.getItemRectSizeX();
            float buttonWidth = 60.0f;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - buttonWidth);
            
            if (ImGui.smallButton("Place")) {
                // 开始放置模式
                com.director.ui.actor.selection.ActorSelectionHub.beginPlace(actor);
                LOGGER.debug("[ActorProjectPropertyAdapter] 开始放置 Actor: {}", actor.getName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("在世界中放置此 Actor\n右键点击世界中的位置来放置");
            }
            
            // 右键菜单：编辑/删除
            if (ImGui.beginPopupContextItem("ActorContextMenu_" + actor.getActorId())) {
                if (ImGui.selectable("Edit")) {
                    selectActor(actor);
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.selectable("Place")) {
                    com.director.ui.actor.selection.ActorSelectionHub.beginPlace(actor);
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.selectable("Delete")) {
                    openDeleteConfirm(actor.getActorId());
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
        }
        
        return modified;
    }

    private boolean renderGroupList(ActorRegistry actorRegistry) {
        UITheme.label("Actor Groups", UITheme.DarkTheme.TEXT);
        UITheme.separator();

        List<ActorGroup> groups = actorRegistry.getAllGroups();
        if (groups.isEmpty()) {
            ImGui.textDisabled("No actor groups in project.");
            return false;
        }

        boolean modified = false;
        for (ActorGroup group : groups) {
            String groupLabel = String.format("%s  (%d actors)", group.getName(), group.size());
            if (ImGui.treeNodeEx(group.getGroupId().toString(),
                    ImGuiTreeNodeFlags.DefaultOpen,
                    groupLabel)) {
                ImString name = new ImString(group.getName(), 128);
                if (ImGui.inputText("Name##groupName" + group.getGroupId(), name, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    group.setName(name.get());
                    modified = true;
                }

                ActorGroup.FormationType[] formations = ActorGroup.FormationType.values();
                ImInt formation = new ImInt(group.getFormationType().ordinal());
                String[] formationLabels = new String[formations.length];
                for (int i = 0; i < formations.length; i++) {
                    formationLabels[i] = formations[i].name();
                }
                if (ImGui.combo("Formation##groupFormation" + group.getGroupId(), formation, formationLabels)) {
                    group.setFormationType(formations[Math.max(0, Math.min(formation.get(), formations.length - 1))]);
                    modified = true;
                }

                float[] spacing = { group.getFormationSpacing() };
                if (ImGui.sliderFloat("Formation Spacing##groupFormationSpacing" + group.getGroupId(),
                        spacing, 0.1f, 12.0f, "%.1f blocks")) {
                    group.setFormationSpacing(spacing[0]);
                    modified = true;
                }

                ActorGroup.DelayPolicy[] delays = ActorGroup.DelayPolicy.values();
                ImInt delay = new ImInt(group.getDelayPolicy().ordinal());
                String[] delayLabels = new String[delays.length];
                for (int i = 0; i < delays.length; i++) {
                    delayLabels[i] = delays[i].name();
                }
                if (ImGui.combo("Delay##groupDelay" + group.getGroupId(), delay, delayLabels)) {
                    group.setDelayPolicy(delays[Math.max(0, Math.min(delay.get(), delays.length - 1))]);
                    modified = true;
                }

                float[] step = { group.getDelayStepSeconds() };
                if (ImGui.sliderFloat("Delay Step##groupDelayStep" + group.getGroupId(), step, 0.0f, 5.0f, "%.2fs")) {
                    group.setDelayStepSeconds(step[0]);
                    modified = true;
                }

                renderGroupActionAuthoring(group);
                renderGroupRouteAuthoring(group);
                renderGroupPlacement(group);

                if (ImGui.button("Delete Group##deleteGroup" + group.getGroupId())) {
                    actorRegistry.removeGroup(group.getGroupId());
                    modified = true;
                    ImGui.treePop();
                    break;
                }

                ImGui.treePop();
            }
        }

        if (modified) {
            ActorRegistryManager.save();
        }
        return modified;
    }

    private void renderGroupActionAuthoring(ActorGroup group) {
        ImGui.spacing();
        UITheme.label("Group Action", UITheme.DarkTheme.TEXT);

        ActorActionType currentBrush = ActionBrushState.get();
        String currentLabel = currentBrush != null ? currentBrush.name() : "NONE";
        if (ImGui.beginCombo("Action##groupAction" + group.getGroupId(), currentLabel)) {
            for (ActorActionType type : ActorActionType.values()) {
                boolean selected = type == currentBrush;
                if (ImGui.selectable(type.name(), selected)) {
                    ActionBrushState.set(type);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        ImGui.sliderFloat("Duration##groupActionDuration" + group.getGroupId(),
                groupActionDuration.getData(), 0.05f, 20.0f, "%.2fs");
        ImGui.sliderFloat("Intensity##groupActionIntensity" + group.getGroupId(),
                groupActionIntensity.getData(), 0.0f, 1.0f, "%.2f");
        ImGui.checkbox("Loop##groupActionLoop" + group.getGroupId(), groupActionLoop);

        boolean canCreate = group.size() > 0 && currentBrush != null;
        if (!canCreate) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Create Action Clips##groupCreateAction" + group.getGroupId())) {
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
            ImGui.setTooltip("从当前播放头时间开始，为组内每个 Actor 创建 ActorActionClip，并应用 Delay 策略。");
        }
    }

    private void renderGroupRouteAuthoring(ActorGroup group) {
        ImGui.spacing();
        UITheme.label("Group Route", UITheme.DarkTheme.TEXT);

        UUID sourceActorId = ActorSelectionHub.getSelectedActorId();
        String sourceLabel = "Source: " + (sourceActorId != null ? sourceActorId : "no selected actor");
        ImGui.textDisabled(sourceLabel);

        boolean canCreate = group.size() > 0 && sourceActorId != null;
        if (!canCreate) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Copy Selected Actor Route##groupCopyRoute" + group.getGroupId())) {
            int created = ActorGroupTimelineCommandService.getInstance()
                    .copyLatestRouteFromActor(group, sourceActorId);
            LOGGER.info("[ActorProjectPropertyAdapter] Copied selected actor route to {} group members", created);
        }
        if (!canCreate) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("复制当前选中 Actor 最近的 ActorRouteClip 到此组成员，并应用 Delay 策略。");
        }
    }

    private void renderGroupPlacement(ActorGroup group) {
        ImGui.spacing();
        UITheme.label("Group Placement", UITheme.DarkTheme.TEXT);

        boolean canPlace = group.size() > 0;
        if (!canPlace) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Place Group at Camera##groupPlaceCamera" + group.getGroupId())) {
            int placed = ActorGroupTimelineCommandService.getInstance().placeGroupAtCamera(group);
            LOGGER.info("[ActorProjectPropertyAdapter] Placed {} group actors at camera", placed);
        }
        if (!canPlace) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在当前播放头时间创建或更新组成员的 SpawnClip，并按 Formation/Spacing 排布。");
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
        UITheme.label("Actor Properties", UITheme.DarkTheme.TEXT);
        
        // ---- Name ----
        editingActorName.set(actor.getName());
        if (ImGui.inputText("Name", editingActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String newName = editingActorName.get();
            if (newName != null && !newName.isBlank() && !newName.equals(actor.getName())) {
                actor.setName(newName);
                modified = true;
                LOGGER.debug("[ActorProjectPropertyAdapter] Actor name changed: {}", newName);
            }
        }
        
        // ---- Type ----
        String currentTypeName = actor.getType().name();
        if (ImGui.beginCombo("Type", currentTypeName)) {
            for (ActorType type : ActorType.values()) {
                boolean isSelected = type == actor.getType();
                if (ImGui.selectable(type.name(), isSelected)) {
                    actor.setType(type);
                    modified = true;
                    LOGGER.debug("[ActorProjectPropertyAdapter] Actor type changed: {}", type);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        ImGui.spacing();
        
        // ---- Actions ----
        if (ImGui.button("Focus in World")) {
            ActorSelectionHub.selectActor(actor.getActorId());
            LOGGER.debug("[ActorProjectPropertyAdapter] Focus actor in world: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("Place at Camera")) {
            placeActorAtCamera(actor);
            LOGGER.debug("[ActorProjectPropertyAdapter] Place actor at camera: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("Delete Actor")) {
            openDeleteConfirm(actor.getActorId());
        }
        
        // 提示信息
        ImGui.textDisabled("💡 Tip: Right-click in world to place actor");
        
        ImGui.spacing();
        
        // ---- Action Brush (动作涂抹笔刷) ----
        renderActionBrushSelector();
        
        ImGui.spacing();

        modified |= renderSelectedActorGroups(actorRegistry, actor);

        ImGui.spacing();
        
        // ---- Performance Style (风格滑条) ----
        ActorRigProfile rigProfile = actor.getRigProfile();
        if (rigProfile != null) {
            RigProfileStyle style = rigProfile.getStyle();
            if (style != null) {
                RigProfileStylePropertyUI.render(style);
                // Style 的修改会立即生效，因为 RigProfile 在 Runtime 中会读取 style 的值
                modified = true; // 标记为已修改（即使只是 UI 交互）
            }
        }
        
        ImGui.spacing();
        
        // ---- Usage Information (只读) ----
        renderActorUsage(actor);
        
        return modified;
    }

    private boolean renderSelectedActorGroups(ActorRegistry actorRegistry, Actor actor) {
        if (actor == null) {
            return false;
        }
        UITheme.label("🎭 Groups", UITheme.DarkTheme.TEXT);
        List<ActorGroup> groups = actorRegistry.getAllGroups();
        if (groups.isEmpty()) {
            ImGui.textDisabled("No groups yet. Use + Add Group in the Actors project view.");
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
     * 在演员面板中选中演员（仅更新内部状态，不设置全局选择）
     * <p>只有在点击"Focus in World"等按钮时，才通过 ActorSelectionHub.selectActor() 设置全局选择
     *
     * @param actor Actor 实例
     */
    private void selectActor(Actor actor) {
        if (actor == null) {
            selectedActorId = null;
            return;
        }
        
        // 只更新内部的选中状态，不设置全局 SelectionManager
        // 这样属性面板不会因为演员面板中的选择而显示重复内容
        this.selectedActorId = actor.getActorId();

        LOGGER.debug("[ActorProjectPropertyAdapter] Selected actor in panel: {}", actor.getActorId());
    }
    
    // =========================================================
    // Create actor
    // =========================================================
    
    private void openCreateActorPopup() {
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup("Create Actor");
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
        
        if (ImGui.beginPopupModal("Create Actor")) {
            ImGui.text("Create New Actor");
            ImGui.separator();
            
            // Name input
            if (ImGui.inputText("Name", newActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                // Enter key pressed, but we'll handle it in the Create button
            }
            
            // Type combo
            String currentTypeName = newActorType.name();
            if (ImGui.beginCombo("Type", currentTypeName)) {
                for (ActorType type : ActorType.values()) {
                    boolean selected = type == newActorType;
                    if (ImGui.selectable(type.name(), selected)) {
                        newActorType = type;
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
            
            ImGui.separator();
            
            // Buttons
            if (ImGui.button("Create")) {
                String name = newActorName.get();
                if (name != null && !name.isBlank()) {
                    // 使用新的放置系统
                    com.director.ui.actor.placement.ActorPlacementController placementController =
                            com.director.ui.actor.placement.ActorPlacementController.getInstance();
                    
                    if (placementController.startPlacement(name, newActorType)) {
                        LOGGER.info("[ActorProjectPropertyAdapter] 开始放置 Actor: {} ({})", name, newActorType);
                    } else {
                        LOGGER.warn("[ActorProjectPropertyAdapter] 无法开始放置 Actor");
                    }
                }
                
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }
            
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

        // 检查轨道名称（特别是实体轨道）
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
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();

        try {
            // 0️⃣ 生成唯一的名称
            String uniqueName = generateUniqueActorName(actorName, actorRegistry, timelineModel);

            // 1️⃣ Create Actor
            Actor actor = actorRegistry.createActor(uniqueName, actorType);
            ActorRef ref = new ActorRef(actor.getActorId());

            // 2️⃣ Create EntityTrack
            EntityTrack entityTrack = new EntityTrack();
            entityTrack.setActorRef(ref);
            entityTrack.setDisplayName(actor.getName());

            // 3️⃣ Create TimelineTrack with EntityTrack payload
            if (timelineModel == null) {
                LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化，无法创建 Track");
                // 即使没有 TimelineModel，也选中 Actor
                selectActor(actor);
                return;
            }

            // 使用唯一的名称创建轨道
            TimelineTrack timelineTrack = new TimelineTrack(uniqueName, TrackType.ENTITY);
            timelineTrack.setEntityTrackPayload(entityTrack);

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
            entityTrack.addClip(spawnClip);
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, entityTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);

            // 6️⃣ Mark model as dirty (trigger save)
            timelineModel.markDirty();

            // 7️⃣ Select Actor（这会触发 PropertiesPanel 切换到 Actor 面板）
            selectActor(actor);

            LOGGER.info("[ActorProjectPropertyAdapter] 成功创建 Actor: {} ({}), EntityTrack 已添加到时间线",
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
        UITheme.label("Used In", UITheme.DarkTheme.TEXT);
        
        TimelineModel timeline = ContainerTimelineService.getInstance().getTimelineModel();
        if (timeline == null) {
            ImGui.textDisabled("No active timeline.");
            return;
        }
        
        // 查询使用该 Actor 的所有 EntityTrack
        List<EntityTrack> entityTracks = ActorUsageQuery.findTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        if (entityTracks.isEmpty()) {
            ImGui.textDisabled("This actor is not used in the current timeline.");
            ImGui.sameLine();
            if (ImGui.smallButton("Delete Actor##unused")) {
                openDeleteConfirm(actor.getActorId());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("This actor is not used in any track. Click to delete it.");
            }
            return;
        }
        
        // 查询对应的 TimelineTrack（用于 Focus Track 功能）
        List<TimelineTrack> timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        // 显示 Timeline 名称（目前只有一个 Timeline，未来可扩展）
        String timelineName = "Current Timeline";
        // 如果 TimelineModel 有 getName() 方法，可以使用它
        // timelineName = timeline.getName() != null ? timeline.getName() : "Unnamed Timeline";
        
        ImGui.bulletText(String.format("Timeline: %s", timelineName));
        
        // 显示每个 Track
        ImGui.indent();
        for (int i = 0; i < entityTracks.size(); i++) {
            EntityTrack entityTrack = entityTracks.get(i);
            TimelineTrack timelineTrack = i < timelineTracks.size() ? timelineTracks.get(i) : null;
            
            String trackDisplayName = entityTrack.getDisplayName();
            if (trackDisplayName == null || trackDisplayName.isBlank()) {
                trackDisplayName = timelineTrack != null && timelineTrack.getName() != null
                        ? timelineTrack.getName()
                        : "Actor Track";
            }
            
            ImGui.text(String.format("- Track: %s", trackDisplayName));
            
            // Focus Track 按钮
            if (timelineTrack != null) {
                // 使用 track 的 hashCode 作为唯一标识符
                String buttonId = "Focus Track##" + timelineTrack.hashCode();
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
        SelectionManager selectionManager = SelectionManager.get();
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
        var cameraSample = com.director.ui.timeline.playback.camera.CameraAccessor.sampleFromGameCamera(client);
        if (cameraSample == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 无法获取摄像机位置");
            return;
        }
        
        var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化");
            return;
        }
        
        // 查找该 Actor 的 EntityTrack
        var timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(timelineModel, actor.getActorId());
        if (timelineTracks.isEmpty()) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 未找到 Actor 对应的 Track");
            return;
        }
        
        // 使用第一个 Track（通常只有一个）
        TimelineTrack timelineTrack = timelineTracks.getFirst();
        EntityTrack entityTrack = timelineTrack.getEntityTrackPayload();
        if (entityTrack == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] EntityTrack payload 为空");
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        org.joml.Vector3f cameraPos = new org.joml.Vector3f(
                (float) cameraSample.position().x,
                (float) cameraSample.position().y,
                (float) cameraSample.position().z
        );
        
        // 查找当前时间是否有 EntitySpawnClip
        var clipsAtTime = entityTrack.findClipsAt(currentTime);
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
            entityTrack.addClip(spawnClip);
            LOGGER.info("[ActorProjectPropertyAdapter] 创建 SpawnClip 在摄像机位置: ({}, {}, {})",
                    cameraPos.x, cameraPos.y, cameraPos.z);
        }
        
        if (spawnClip.getTimelineClip() == null) {
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, entityTrack.getColorRgba());
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
        ImGui.openPopup("Delete Actor");
    }
    
    private void renderDeleteConfirmPopup(ActorRegistry actorRegistry) {
        if (!deleteConfirmOpen || deletingActorId == null) {
            return;
        }
        
        if (ImGui.beginPopupModal("Delete Actor")) {
            Optional<Actor> actorOpt = actorRegistry.getActor(deletingActorId);
            if (actorOpt.isEmpty()) {
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
                ImGui.endPopup();
                return;
            }
            
            Actor actor = actorOpt.get();
            
            ImGui.text("Are you sure you want to delete this actor?");
            ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, 
                String.format("Actor: %s (%s)", actor.getName(), actor.getType().name()));
            ImGui.textDisabled("This action cannot be undone.");
            
            ImGui.separator();
            
            if (ImGui.button("Delete")) {
                deleteActor(actorRegistry, deletingActorId);
                
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 删除演员（同时删除该演员在实体轨道上的所有片段）
     * <p>根据逻辑：在演员面板列表中的演员，一定在实体轨道上有对应的演员片段
     * <p>删除演员时，应该同时删除该演员对应的轨道和所有片段
     */
    private void deleteActor(ActorRegistry actorRegistry, UUID actorId) {
        if (actorId == null) {
            return;
        }
        
        var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化，无法删除轨道");
            // 即使没有 TimelineModel，也尝试删除演员
            actorRegistry.removeActor(actorId);
            return;
        }
        
        // 1️⃣ 查找使用该演员的所有轨道
        List<TimelineTrack> tracksToRemove = ActorUsageQuery.findTimelineTracksUsingActor(
                timelineModel, 
                actorId
        );
        
        // 2️⃣ 删除所有关联的轨道（这会自动删除轨道上的所有片段）
        for (TimelineTrack track : tracksToRemove) {
            timelineModel.removeTrack(track);
            LOGGER.debug("[ActorProjectPropertyAdapter] 已删除关联的轨道: {}", track.getName());
        }
        
        // 3️⃣ 清理 Minecraft 世界中的预览实体
        ActorPreviewManager.getInstance().removePreview(actorId);
        
        // 4️⃣ 从 ActorRegistry 删除演员
        boolean removed = actorRegistry.removeActor(actorId);
        
        if (removed) {
            // 如果删除的是当前选中的 Actor，清空选择
            if (actorId.equals(selectedActorId)) {
                selectedActorId = null;
            }
            
            // 清空全局 Selection（包括方块选区）
            ActorSelectionHub.clearSelection();

            // 标记需要保存（通过 TimelineModel）
            timelineModel.markDirty();
            
            LOGGER.info("[ActorProjectPropertyAdapter] 已删除演员 {} 及其所有轨道、片段、预览实体和选区", actorId);
        }
    }
    
    // =========================================================
    // Action Brush Selector
    // =========================================================
    
    /**
     * 渲染 Action Brush 选择器（用于涂抹式编辑）
     * <p>导演在这里选择当前绘制动作（RUN / DANCE / CARRY 等）
     */
    private void renderActionBrushSelector() {
        UITheme.label("🎨 Action Brush", UITheme.DarkTheme.TEXT);
        
        ActorActionType currentBrush = ActionBrushState.get();
        String currentLabel = currentBrush != null ? currentBrush.name() : "NONE";
        
        if (ImGui.beginCombo("Current Brush", currentLabel)) {
            for (ActorActionType type : ActorActionType.values()) {
                boolean selected = type == currentBrush;
                if (ImGui.selectable(type.name(), selected)) {
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
            ImGui.setTooltip("选择当前绘制动作\\n在 Timeline 的演员轨道上拖拽即可创建 ActionClip");
        }
        
        ImGui.textDisabled("💡 在 Timeline 的演员轨道上拖拽一段，即可创建 " + currentLabel + " 动作区间");
    }
}
