package com.director.ui.actor;

import com.director.actor.*;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.actor.placement.ActorPlacementController;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ActorSelectorPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectorPanel.class);

    private final ActorRegistry actorRegistry;
    private final TimelineModel timelineModel;

    private boolean createPopupOpen = false;
    private boolean editNamePopupOpen = false;
    private boolean deleteConfirmPopupOpen = false;

    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;

    private Actor editingActor = null;
    private final ImString editingActorName = new ImString("", 256);

    private Actor deletingActor = null;

    public ActorSelectorPanel(ActorRegistry actorRegistry, TimelineModel timelineModel) {
        this.actorRegistry = actorRegistry;
        this.timelineModel = timelineModel;
    }

    public void render() {
        renderActorList();

        ImGui.spacing();
        if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.ADD_ACTOR))) {
            openCreatePopup();
        }

        renderCreatePopup();
        renderEditNamePopup();
        renderDeleteConfirmPopup();
    }

    private void renderActorList() {
        List<Actor> actors = actorRegistry.getAllActors();

        if (actors.isEmpty()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.NO_ACTORS));
            return;
        }

        for (Actor actor : actors) {
            ImGui.pushID(actor.getActorId().toString());

            String displayText = actor.getName() + "  (" + ActorPropertyAdapterI18n.actorTypeLabel(actor.getType()) + ")";
            if (ImGui.selectable(displayText, actor.getActorId().equals(com.director.ui.actor.selection.ActorSelectionHub.getSelectedActorId()))) {
                com.director.ui.actor.selection.ActorSelectionHub.selectActor(actor.getActorId());
            }

            if (ImGui.beginPopupContextItem("ActorContextMenu")) {
                if (ImGui.menuItem(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.EDIT_NAME))) {
                    openEditActorNamePopup(actor);
                }
                if (ImGui.menuItem(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.DELETE))) {
                    openDeleteActorConfirmPopup(actor);
                }
                ImGui.endPopup();
            }

            ImGui.popID();
        }
    }

    private void openCreatePopup() {
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_CREATE));
    }

    private void renderCreatePopup() {
        if (!createPopupOpen) {
            return;
        }

        String popupCreate = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_CREATE);
        if (ImGui.beginPopupModal(popupCreate, ImGuiCond.Always)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.NAME_LABEL));
            ImGui.sameLine();
            ImGui.inputText("##ActorName", newActorName);

            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.TYPE_LABEL));
            ImGui.sameLine();
            renderActorTypeCombo();

            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.PLACE_HINT));

            ImGui.separator();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CREATE))) {
                createActorAndTrack();
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }

            ImGui.sameLine();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL))) {
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }

            ImGui.endPopup();
        }
    }

    private void renderActorTypeCombo() {
        String currentTypeName = ActorPropertyAdapterI18n.actorTypeLabel(newActorType);
        String[] typeLabels = ActorPropertyAdapterI18n.actorTypeLabels();
        if (ImGui.beginCombo("##ActorType", currentTypeName)) {
            ActorType[] types = ActorType.values();
            for (int i = 0; i < types.length; i++) {
                ActorType type = types[i];
                boolean selected = (type == newActorType);
                if (ImGui.selectable(typeLabels[i], selected)) {
                    newActorType = type;
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }

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

    private void openEditActorNamePopup(Actor actor) {
        editingActor = actor;
        editingActorName.set(actor.getName());
        editNamePopupOpen = true;
        ImGui.openPopup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_EDIT_NAME));
    }

    private void renderEditNamePopup() {
        if (!editNamePopupOpen || editingActor == null) {
            return;
        }

        if (ImGui.beginPopupModal(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_EDIT_NAME), ImGuiCond.Always)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.ACTOR_NAME_LABEL));
            ImGui.sameLine();
            ImGui.inputText("##EditActorName", editingActorName);

            ImGui.separator();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SAVE))) {
                String newName = editingActorName.get();
                if (newName != null && !newName.trim().isEmpty()) {
                    editingActor.setName(newName.trim());
                    updateActorTrackDisplayName(editingActor);
                    timelineModel.markDirty();
                    LOGGER.info("[ActorSelector] 已更新 Actor 名称: {}", newName);
                }
                ImGui.closeCurrentPopup();
                editNamePopupOpen = false;
                editingActor = null;
            }

            ImGui.sameLine();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL))) {
                ImGui.closeCurrentPopup();
                editNamePopupOpen = false;
                editingActor = null;
            }

            ImGui.endPopup();
        }
    }

    private void openDeleteActorConfirmPopup(Actor actor) {
        deletingActor = actor;
        deleteConfirmPopupOpen = true;
        ImGui.openPopup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_DELETE));
    }

    private void renderDeleteConfirmPopup() {
        if (!deleteConfirmPopupOpen || deletingActor == null) {
            return;
        }

        if (ImGui.beginPopupModal(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.POPUP_DELETE), ImGuiCond.Always)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.DELETE_CONFIRM));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.DELETE_NAME_FMT, deletingActor.getName()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.DELETE_TYPE_FMT,
                    ActorPropertyAdapterI18n.actorTypeLabel(deletingActor.getType())));
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Selector.DELETE_WARNING));

            ImGui.separator();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DELETE), 100, 0)) {
                deleteActor(deletingActor);
                ImGui.closeCurrentPopup();
                deleteConfirmPopupOpen = false;
                deletingActor = null;
            }

            ImGui.sameLine();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL), 100, 0)) {
                ImGui.closeCurrentPopup();
                deleteConfirmPopupOpen = false;
                deletingActor = null;
            }

            ImGui.endPopup();
        }
    }

    private void deleteActor(Actor actor) {
        if (actor == null) {
            return;
        }

        try {
            removeActorTracksForActor(actor.getActorId());
            actorRegistry.removeActor(actor.getActorId());
            timelineModel.markDirty();
            LOGGER.info("[ActorSelector] 已删除 Actor: {} ({})", actor.getName(), actor.getType());
        } catch (Exception e) {
            LOGGER.error("[ActorSelector] 删除 Actor 失败", e);
        }
    }

    private void removeActorTracksForActor(java.util.UUID actorId) {
        if (actorId == null || timelineModel == null) {
            return;
        }

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

    private void updateActorTrackDisplayName(Actor actor) {
        if (actor == null || timelineModel == null) {
            return;
        }

        for (com.director.timeline.model.TimelineTrack track : timelineModel.getAllTracks()) {
            if (track.getType() == com.director.timeline.model.TrackType.ACTOR) {
                com.director.timeline.model.entity.ActorTrack actorTrack = track.getActorTrackPayload();
                if (actorTrack != null) {
                    com.director.actor.ActorRef ref = actorTrack.getActorRef();
                    if (ref != null && actor.getActorId().equals(ref.getActorId())) {
                        actorTrack.setDisplayName(actor.getName());
                        track.setName(actor.getName());
                    }
                }
            }
        }
    }
}
