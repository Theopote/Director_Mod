package com.director.ui.actor.path;

import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibraryManager;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 演员路径库面板：列出、重命名、锁定、可见性、删除已保存路径。
 */
public final class ActorPathLibraryPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathLibraryPanel.class);
    private static final int NAME_BUFFER = 128;
    private static final float CONTROL_BUTTON_SIZE = 22.0f;
    private static final String DELETE_POPUP_ID = "ConfirmActorPathDelete##ActorPathLibrary";

    private final ImString renameBuffer = new ImString("", NAME_BUFFER);
    private UUID renamingPathId = null;
    private UUID selectedPathId = null;
    private UUID pendingDeletePathId = null;
    private boolean deleteConfirmPopupOpen = false;
    private String lastError = "";
    private com.director.timeline.model.TimelineModel viewModel;
    private com.director.actor.path.ActorPathLibrary viewLibrary;

    public void render() {
        var model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        var library = ActorPathLibraryManager.getInstance();
        if (viewModel != model || viewLibrary != library) {
            viewModel = model; viewLibrary = library;
            renamingPathId = null; selectedPathId = null; pendingDeletePathId = null;
            deleteConfirmPopupOpen = false; lastError = "";
        }
        boolean headerOpen = ImGui.collapsingHeader(Icons.PATH + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.HEADER));

        if (headerOpen) {
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.HINT));
            ImGui.spacing();

            ImGui.beginDisabled(model == null || model.isPlaying());
            var paths = ActorPathLibraryManager.getAllPaths();
            if (paths.isEmpty()) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.EMPTY));
            } else {
                for (ActorPathDefinition path : paths) {
                    boolean selected = path.getId().equals(selectedPathId);
                    ImGui.pushID(path.getId().toString());

                    if (renamingPathId != null && renamingPathId.equals(path.getId())) {
                        renderRenameRow(path);
                    } else {
                        renderPathRow(path, selected);
                    }

                    ImGui.popID();
                }
            }
            ImGui.endDisabled();
            if (!lastError.isEmpty()) ImGui.textWrapped(lastError);
        }

        renderDeleteConfirmPopup();
    }

    public UUID getSelectedPathId() {
        return selectedPathId;
    }

    private void renderPathRow(ActorPathDefinition path, boolean selected) {
        String label = ActorPropertyAdapterI18n.tr(
                DirectorLang.Properties.AdapterActor.PathLibrary.WAYPOINT_COUNT_FMT,
                path.getName(),
                path.getWaypoints().size()
        );
        if (path.isLocked()) {
            label = Icons.LOCK + " " + label;
        }

        float controlsWidth = CONTROL_BUTTON_SIZE * 4.0f + ImGui.getStyle().getItemInnerSpacingX() * 3.0f;
        float iconRegionStart = ImGui.getContentRegionMaxX() - controlsWidth;
        float rowStartX = ImGui.getCursorPosX();
        float nameWidth = Math.max(0.0f, iconRegionStart - rowStartX - ImGui.getStyle().getItemInnerSpacingX());

        if (ImGui.selectable(label, selected, 0, nameWidth, 0)) {
            selectedPathId = path.getId();
            if (path.isLocked()) {
                LOGGER.info("[ActorPathLibraryPanel] Path '{}' is locked", path.getName());
            } else {
                ActorPathController.getInstance().loadPathForEditing(path.getId());
            }
        }
        if (path.isLocked() && ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.LOCKED_HINT));
        }

        ImGui.sameLine();
        ImGui.setCursorPosX(iconRegionStart);

        if (iconButton(path.isLocked() ? Icons.LOCK : Icons.UNLOCK, "##lock")) {
            boolean becomingLocked = !path.isLocked();
            if (!edit(model -> ActorPathEditing.setLocked(model, viewLibrary, path, becomingLocked, ActorPathController.librarySaver()))) return;
            if (becomingLocked && path.getId().equals(ActorPathController.getInstance().getEditingLibraryPathId())) {
                ActorPathController.getInstance().onPathRemovedFromLibrary(path.getId());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(
                    path.isLocked()
                            ? DirectorLang.Properties.AdapterActor.PathLibrary.UNLOCK
                            : DirectorLang.Properties.AdapterActor.PathLibrary.LOCK
            ));
        }

        ImGui.sameLine();
        if (iconButton(path.isVisible() ? Icons.VISIBLE : Icons.INVISIBLE, "##visible")) {
            edit(model -> ActorPathEditing.setVisible(model, viewLibrary, path, !path.isVisible(), ActorPathController.librarySaver()));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(
                    path.isVisible()
                            ? DirectorLang.Properties.AdapterActor.PathLibrary.HIDE
                            : DirectorLang.Properties.AdapterActor.PathLibrary.SHOW
            ));
        }

        ImGui.sameLine();
        if (iconButton(Icons.EDIT, "##rename")) {
            if (!path.isLocked()) {
                renamingPathId = path.getId();
                renameBuffer.set(path.getName());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.RENAME));
        }

        ImGui.sameLine();
        if (iconButton(Icons.DELETE, "##delete")) {
            openDeleteConfirmPopup(path.getId());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.DELETE));
        }
    }

    private boolean iconButton(String icon, String id) {
        return ImGui.button(icon + id, CONTROL_BUTTON_SIZE, CONTROL_BUTTON_SIZE);
    }

    private void renderRenameRow(ActorPathDefinition path) {
        ImGui.setNextItemWidth(-120);
        if (ImGui.inputText("##renameInput", renameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            commitRename(path.getId());
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.CHECK + "##ok")) {
            commitRename(path.getId());
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.CLOSE + "##cancel")) {
            renamingPathId = null;
        }
    }

    private void openDeleteConfirmPopup(UUID pathId) {
        pendingDeletePathId = pathId;
        deleteConfirmPopupOpen = true;
    }

    private void renderDeleteConfirmPopup() {
        if (!deleteConfirmPopupOpen || pendingDeletePathId == null) {
            return;
        }

        if (!ImGui.isPopupOpen(DELETE_POPUP_ID)) {
            ImGui.openPopup(DELETE_POPUP_ID);
        }

        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal(DELETE_POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
            ActorPathDefinition path = ActorPathLibraryManager.getPath(pendingDeletePathId);
            String pathName = path != null ? path.getName() : pendingDeletePathId.toString();

            ImGui.text(Icons.WARNING + " "
                    + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.DELETE_CONFIRM));
            ImGui.text(ActorPropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterActor.PathLibrary.DELETE_NAME_FMT, pathName));
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.PathLibrary.DELETE_WARNING));
            ImGui.separator();

            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DELETE), 100, 0)) {
                confirmDelete();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.CANCEL), 100, 0)) {
                cancelDelete();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void confirmDelete() {
        if (pendingDeletePathId == null) {
            return;
        }
        UUID pathId = pendingDeletePathId;
        if (edit(model -> ActorPathEditing.remove(model, viewLibrary, viewLibrary.get(pathId), ActorPathController.librarySaver()))) {
            if (pathId.equals(selectedPathId)) {
                selectedPathId = null;
            }
            if (pathId.equals(renamingPathId)) {
                renamingPathId = null;
            }
            ActorPathController.getInstance().onPathRemovedFromLibrary(pathId);
        }
        deleteConfirmPopupOpen = false;
        pendingDeletePathId = null;
    }

    private void cancelDelete() {
        deleteConfirmPopupOpen = false;
        pendingDeletePathId = null;
    }

    private void commitRename(UUID pathId) {
        String newName = renameBuffer.get().trim();
        if (newName.isEmpty()) {
            return;
        }
        if (!ActorPathLibraryManager.getInstance().isNameAvailable(newName, pathId)) {
            LOGGER.warn("[ActorPathLibraryPanel] 路径名称已存在: {}", newName);
            return;
        }
        if (edit(model -> ActorPathEditing.rename(model, viewLibrary, viewLibrary.get(pathId), newName, ActorPathController.librarySaver()))) {
            renamingPathId = null;
        }
    }
    private boolean edit(java.util.function.Consumer<com.director.timeline.model.TimelineModel> action) {
        try {
            if (viewModel == null || viewModel != com.director.core.DirectorServices.get().containerTimeline().getTimelineModel()
                    || viewLibrary != ActorPathLibraryManager.getInstance()) return false;
            action.accept(viewModel); lastError = ""; return true;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            lastError = failure.getMessage(); LOGGER.warn("[ActorPathLibraryPanel] {}", lastError); return false;
        }
    }
}
