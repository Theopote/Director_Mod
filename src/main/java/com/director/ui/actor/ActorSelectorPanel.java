package com.director.ui.actor;

import com.director.actor.*;
import com.director.ui.actor.placement.ActorPlacementController;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ActorSelectorPanel v1.0
 *
 * <p>属性面板中的"演员管理器"
 * <p>职责：让导演"创建演员"这件事，直觉、快速、不可犯错
 *
 * <p>核心功能：
 * <ul>
 *   <li>显示 Actor 列表</li>
 *   <li>创建新 Actor（原子性：Actor placement preview）</li>
 *   <li>选择 ActorType</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 只做方案 C（Actor 系统）</li>
 *   <li>✅ 不绑定已有世界实体</li>
 *   <li>✅ 不编辑 Transform / Animation（那是 Step 4B）</li>
 *   <li>✅ 不涉及 Runtime / Preview 逻辑</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorSelectorPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectorPanel.class);

    private final ActorRegistry actorRegistry;
    private final TimelineModel timelineModel;

    private boolean createPopupOpen = false;
    private boolean editNamePopupOpen = false;
    private boolean deleteConfirmPopupOpen = false;

    // Create popup state
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;

    // Edit popup state
    private Actor editingActor = null;
    private final ImString editingActorName = new ImString("", 256);

    // Delete confirm popup state
    private Actor deletingActor = null;

    /**
     * 构造函数
     *
     * @param actorRegistry ActorRegistry 实例
     * @param timelineModel TimelineModel 实例
     */
    public ActorSelectorPanel(ActorRegistry actorRegistry, TimelineModel timelineModel) {
        this.actorRegistry = actorRegistry;
        this.timelineModel = timelineModel;
    }

    // =========================================================
    // Render
    // =========================================================

    /**
     * 渲染 Actor Selector 面板
     */
    public void render() {
        ImGui.textColored(0.9f, 0.7f, 1.0f, 1.0f, "🎭 Actors");

        ImGui.separator();

        renderActorList();

        ImGui.spacing();
        if (ImGui.button("+ Add Actor")) {
            openCreatePopup();
        }

        renderCreatePopup();
        renderEditNamePopup();
        renderDeleteConfirmPopup();
    }

    // =========================================================
    // Actor List
    // =========================================================

    private void renderActorList() {
        List<Actor> actors = actorRegistry.getAllActors();

        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors yet.");
            return;
        }

        for (Actor actor : actors) {
            ImGui.pushID(actor.getActorId().toString());
            
            // 显示 Actor 信息（可点击选择）
            String displayText = actor.getName() + "  (" + actor.getType().name() + ")";
            if (ImGui.selectable(displayText, false)) {
                // TODO: 选择 Actor 后可以显示其详细信息或关联的轨道
            }
            
            // 右键菜单：编辑/删除
            if (ImGui.beginPopupContextItem("ActorContextMenu")) {
                if (ImGui.menuItem("编辑名称")) {
                    openEditActorNamePopup(actor);
                }
                if (ImGui.menuItem("删除")) {
                    openDeleteActorConfirmPopup(actor);
                }
                ImGui.endPopup();
            }
            
            ImGui.popID();
        }
    }

    // =========================================================
    // Create Actor Popup
    // =========================================================

    private void openCreatePopup() {
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup("Create Actor");
    }

    private void renderCreatePopup() {
        if (!createPopupOpen) return;

        if (ImGui.beginPopupModal("Create Actor", ImGuiCond.Always)) {

            ImGui.text("Name:");
            ImGui.sameLine();
            ImGui.inputText("##ActorName", newActorName);

            ImGui.text("Type:");
            ImGui.sameLine();
            renderActorTypeCombo();

            ImGui.textDisabled("Right-click in world to place actor.");

            ImGui.separator();

            if (ImGui.button("Create")) {
                createActorAndTrack();
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

    private void renderActorTypeCombo() {
        String currentTypeName = newActorType.name();
        if (ImGui.beginCombo("##ActorType", currentTypeName)) {
            for (ActorType type : ActorType.values()) {
                boolean selected = (type == newActorType);
                if (ImGui.selectable(type.name(), selected)) {
                    newActorType = type;
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }

    // =========================================================
    // Creation Logic（事务）
    // =========================================================

    /**
     * Start actor placement (preview + right-click to place).
     */
    private void createActorAndTrack() {
        String actorName = newActorName.get();
        if (actorName == null || actorName.trim().isEmpty()) {
            actorName = "Actor";
        }

        ActorPlacementController placementController = ActorPlacementController.getInstance();
        if (placementController.startPlacement(actorName.trim(), newActorType)) {
            LOGGER.info("[ActorSelector] Start placement: {} ({})", actorName, newActorType);
        } else {
            LOGGER.warn("[ActorSelector] Failed to start placement for actor: {}", actorName);
        }
    }

    // =========================================================
    // Edit Actor Name Popup
    // =========================================================

    private void openEditActorNamePopup(Actor actor) {
        editingActor = actor;
        editingActorName.set(actor.getName());
        editNamePopupOpen = true;
        ImGui.openPopup("Edit Actor Name");
    }

    private void renderEditNamePopup() {
        if (!editNamePopupOpen || editingActor == null) return;

        if (ImGui.beginPopupModal("Edit Actor Name", ImGuiCond.Always)) {
            ImGui.text("Actor Name:");
            ImGui.sameLine();
            ImGui.inputText("##EditActorName", editingActorName);

            ImGui.separator();

            if (ImGui.button("Save")) {
                String newName = editingActorName.get();
                if (newName != null && !newName.trim().isEmpty()) {
                    editingActor.setName(newName.trim());
                    // 同步更新关联的 ActorTrack 显示名称
                    updateActorTrackDisplayName(editingActor);
                    timelineModel.markDirty();
                    LOGGER.info("[ActorSelector] 已更新 Actor 名称: {}", newName);
                }
                ImGui.closeCurrentPopup();
                editNamePopupOpen = false;
                editingActor = null;
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
                editNamePopupOpen = false;
                editingActor = null;
            }

            ImGui.endPopup();
        }
    }

    // =========================================================
    // Delete Actor Confirm Popup
    // =========================================================

    private void openDeleteActorConfirmPopup(Actor actor) {
        deletingActor = actor;
        deleteConfirmPopupOpen = true;
        ImGui.openPopup("Delete Actor");
    }

    private void renderDeleteConfirmPopup() {
        if (!deleteConfirmPopupOpen || deletingActor == null) return;

        if (ImGui.beginPopupModal("Delete Actor", ImGuiCond.Always)) {
            ImGui.text("确定要删除 Actor 吗？");
            ImGui.text("名称: " + deletingActor.getName());
            ImGui.text("类型: " + deletingActor.getType().name());
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "警告：此操作无法撤销！");

            ImGui.separator();

            if (ImGui.button("删除", 100, 0)) {
                deleteActor(deletingActor);
                ImGui.closeCurrentPopup();
                deleteConfirmPopupOpen = false;
                deletingActor = null;
            }

            ImGui.sameLine();

            if (ImGui.button("取消", 100, 0)) {
                ImGui.closeCurrentPopup();
                deleteConfirmPopupOpen = false;
                deletingActor = null;
            }

            ImGui.endPopup();
        }
    }

    // =========================================================
    // Actor Management
    // =========================================================

    /**
     * 删除 Actor（同时清理关联的 ActorTrack）
     */
    private void deleteActor(Actor actor) {
        if (actor == null) return;

        try {
            // 1️⃣ 删除关联的 ActorTrack
            removeActorTracksForActor(actor.getActorId());

            // 2️⃣ 从 ActorRegistry 删除 Actor
            actorRegistry.removeActor(actor.getActorId());

            // 3️⃣ 标记模型为 dirty
            timelineModel.markDirty();

            LOGGER.info("[ActorSelector] 已删除 Actor: {} ({})", actor.getName(), actor.getType());

        } catch (Exception e) {
            LOGGER.error("[ActorSelector] 删除 Actor 失败", e);
        }
    }

    /**
     * 移除与指定 Actor 关联的所有 ActorTrack
     */
    private void removeActorTracksForActor(java.util.UUID actorId) {
        if (actorId == null || timelineModel == null) return;

        java.util.List<com.director.timeline.model.TimelineTrack> tracksToRemove = new java.util.ArrayList<>();

        for (com.director.timeline.model.TimelineTrack track : timelineModel.getAllTracks()) {
            if (track.getType() == com.director.timeline.model.TrackType.ACTOR) {
                com.director.timeline.model.entity.ActorTrack actorTrack = track.getActorTrackPayload();
                if (actorTrack != null) {
                    com.director.actor.ActorRef ref = actorTrack.getActorRef();
                    if (ref != null && actorId.equals(ref.getActorId())) {
                        tracksToRemove.add(track);
                    }
                }
            }
        }

        for (com.director.timeline.model.TimelineTrack track : tracksToRemove) {
            timelineModel.removeTrack(track);
            LOGGER.debug("[ActorSelector] 已移除关联的 ActorTrack: {}", track.getName());
        }
    }

    /**
     * 更新 ActorTrack 的显示名称以匹配 Actor 名称
     */
    private void updateActorTrackDisplayName(Actor actor) {
        if (actor == null || timelineModel == null) return;

        for (com.director.timeline.model.TimelineTrack track : timelineModel.getAllTracks()) {
            if (track.getType() == com.director.timeline.model.TrackType.ACTOR) {
                com.director.timeline.model.entity.ActorTrack actorTrack = track.getActorTrackPayload();
                if (actorTrack != null) {
                    com.director.actor.ActorRef ref = actorTrack.getActorRef();
                    if (ref != null && actor.getActorId().equals(ref.getActorId())) {
                        actorTrack.setDisplayName(actor.getName());
                        track.setName(actor.getName()); // 同时更新 TimelineTrack 名称
                    }
                }
            }
        }
    }
}

