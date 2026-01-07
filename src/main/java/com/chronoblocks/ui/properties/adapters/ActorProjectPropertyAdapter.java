package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.actor.ActorType;
import com.chronoblocks.ui.actor.selection.ActorSelectionHub;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
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
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ActorProjectPropertyAdapter implements IPropertyAdapter<Object> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorProjectPropertyAdapter.class);
    
    // UI state
    private UUID selectedActorId;
    
    // Create popup state
    private boolean createPopupOpen = false;
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;
    
    // Edit state
    private final ImString editingActorName = new ImString("", 256);
    private ActorType editingActorType = ActorType.VILLAGER;
    
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
    public boolean supports(Object obj) {
        // 支持两种情况：
        // 1. 特定标记对象 "ACTOR_PROJECT"（用于无选择时显示）
        // 2. Actor 对象（用于选中 Actor 时显示）
        if (obj == null) {
            return false; // null 由 PropertiesPanel 的 renderNoSelectionMessage 处理
        }
        if (obj instanceof String && "ACTOR_PROJECT".equals(obj)) {
            return true;
        }
        if (obj instanceof Actor) {
            return true;
        }
        return false;
    }
    
    @Override
    public boolean renderProperties(Object obj) {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        if (actorRegistry == null) {
            UITheme.label("ActorRegistry not available.", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 如果传入的是 Actor 对象，直接选中它
        if (obj instanceof Actor actor) {
            selectedActorId = actor.getActorId();
        }
        
        // 标题
        UITheme.title("🎭 Actors");
        
        // Actor 列表
        modified |= renderActorList(actorRegistry);
        
        // 创建 Actor 按钮
        ImGui.spacing();
        if (ImGui.button("+ Add Actor")) {
            openCreateActorPopup();
        }
        
        // 创建 Actor 弹窗
        renderCreateActorPopup(actorRegistry);
        
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
        UITheme.separatorText("Actor List");
        
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
            
            // 右键菜单：编辑/删除
            if (ImGui.beginPopupContextItem("ActorContextMenu_" + actor.getActorId())) {
                if (ImGui.selectable("Edit")) {
                    selectActor(actor);
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
        
        if (ImGui.button("Delete Actor")) {
            openDeleteConfirm(actor.getActorId());
        }
        
        return modified;
    }
    
    // =========================================================
    // Selection handling
    // =========================================================
    
    private void selectActor(Actor actor) {
        if (actor == null) {
            selectedActorId = null;
            return;
        }
        
        this.selectedActorId = actor.getActorId();
        
        // 通知全局 Selection（Project-level selection）
        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager != null) {
            // 设置 Actor 对象作为 Selection
            selectionManager.setCurrentSelection(actor);
        }
        
        LOGGER.debug("[ActorProjectPropertyAdapter] Selected actor: {}", actor.getActorId());
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
                    Actor actor = actorRegistry.createActor(name, newActorType);
                    selectActor(actor);
                    
                    // 标记需要保存（通过 TimelineModel）
                    var timelineModel = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                    
                    LOGGER.info("[ActorProjectPropertyAdapter] Created actor: {} ({})", name, newActorType);
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
    
    private void deleteActor(ActorRegistry actorRegistry, UUID actorId) {
        boolean removed = actorRegistry.removeActor(actorId);
        
        if (removed) {
            // 如果删除的是当前选中的 Actor，清空选择
            if (actorId.equals(selectedActorId)) {
                selectedActorId = null;
            }
            
            // 清空全局 Selection
            SelectionManager selectionManager = SelectionManager.get();
            if (selectionManager != null) {
                selectionManager.clearSelection();
            }
            
            // 标记需要保存（通过 TimelineModel）
            var timelineModel = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel != null) {
                timelineModel.markDirty();
            }
            
            LOGGER.info("[ActorProjectPropertyAdapter] Deleted actor: {}", actorId);
        }
    }
}

