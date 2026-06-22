package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.i18n.PanelI18n;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.aware.IPanelRegistryAware;
import imgui.ImGui;

import com.director.ui.commands.UICommandFacade;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.theme.Icons;
import com.director.ui.PanelId;
import com.director.timeline.model.camera.path.CameraPathType;

import net.minecraft.client.MinecraftClient;

/**
 * Director MenuBar
 * Facade-based, production-safe implementation
 */
public class MenuBarPanel extends AbstractPanel implements IPanelRegistryAware, ICommandsAware {

    private UICommandFacade commands;
    private IPanelRegistry panelRegistry;

    // ==================== Panel Meta ====================

    @Override
    public String getPanelName() {
        return "MenuBar";
    }

    @Override
    public String getWindowName() {
        return "MainMenuBar###MenuBarPanel";
    }

    @Override
    protected boolean shouldUseStandardWindow() {
        return false;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    // ==================== Injection ====================

    @Override
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }

    @Override
    public String getRenderGroup() {
        return "menu_bar";
    }

    @Override
    public boolean isHideableInView() {
        return false;
    }

    @Override
    public void setPanelRegistry(IPanelRegistry registry) {
        this.panelRegistry = registry;
    }

    // ==================== Render ====================

    @Override
    protected void doRender() {
        renderMainMenuBar();
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String menuLabel(String icon, String key) {
        return icon + " " + tr(key);
    }

    private void renderMainMenuBar() {
        boolean startedInsideWindow = ImGui.beginMenuBar();
        boolean startedGlobal;
        if (!startedInsideWindow) {
            startedGlobal = ImGui.beginMainMenuBar();
            if (!startedGlobal) return;
        }

        try {
            renderFileMenu();
            renderEditMenu();
            renderTimelineMenu();
            renderPlaybackMenu();
            renderWindowMenu();
            renderHelpMenu();
            renderRightSideControls();
        } catch (Exception e) {
            logger.error("[MenuBar] Render error", e);
        } finally {
            if (startedInsideWindow) {
                ImGui.endMenuBar();
            } else {
                ImGui.endMainMenuBar();
            }
        }
    }

    // ==================== Right Side ====================

    private void renderRightSideControls() {
        float closeButtonWidth = ImGui.calcTextSize(Icons.CLOSE).x
                + ImGui.getStyle().getFramePadding().x * 2.0f;
        float rightX = ImGui.getWindowWidth()
                - closeButtonWidth
                - ImGui.getStyle().getWindowPadding().x;

        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), rightX));

        if (ImGui.button(Icons.CLOSE + "##closeDirector")) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    // ==================== File ====================

    private void renderFileMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.FOLDER, DirectorLang.Menu.FILE))) {
            if (ImGui.menuItem(tr(DirectorLang.Menu.FILE_NEW_PROJECT), "Ctrl+N")) {
                commands.project().newProject();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.FILE_RELOAD_PROJECT), "Ctrl+O")) {
                commands.project().openProject();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.FILE_SAVE), "Ctrl+S")) {
                commands.project().saveProject();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.FILE_SAVE_AS), "", false, false)) {
                commands.project().saveProjectAs();
            }

            ImGui.separator();

            if (ImGui.menuItem(menuLabel(Icons.VIDEO, DirectorLang.Menu.FILE_EXPORT_VIDEO))) {
                commands.project().exportVideo();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.FILE_EXIT), "Alt+F4")) {
                commands.project().exitApplication();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Edit ====================

    private void renderEditMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.EDIT, DirectorLang.Menu.EDIT))) {
            if (ImGui.menuItem(menuLabel(Icons.UNDO, DirectorLang.Menu.EDIT_UNDO), "Ctrl+Z")) {
                commands.timeline().undo();
            }
            if (ImGui.menuItem(menuLabel(Icons.REDO, DirectorLang.Menu.EDIT_REDO), "Ctrl+Y")) {
                commands.timeline().redo();
            }

            ImGui.separator();

            if (ImGui.menuItem(menuLabel(Icons.DELETE, DirectorLang.Menu.EDIT_DELETE_SELECTED), "Del")) {
                commands.timeline().deleteSelected();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Timeline ====================

    private void renderTimelineMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.TIMELINE, DirectorLang.Menu.TIMELINE))) {
            if (ImGui.menuItem(tr(DirectorLang.Menu.TIMELINE_SETTINGS))) {
                commands.timeline().openTimelineSettings();
            }

            ImGui.separator();

            if (ImGui.beginMenu(menuLabel(Icons.ADD, DirectorLang.Menu.TIMELINE_ADD_TRACK))) {
                if (ImGui.menuItem(menuLabel(Icons.CAMERA, DirectorLang.Menu.TIMELINE_TRACK_CAMERA))) {
                    commands.timeline().addCameraTrack(CameraPathType.CUSTOM);
                }
                if (ImGui.menuItem(menuLabel(Icons.TARGET, DirectorLang.Menu.TIMELINE_TRACK_ACTOR))) {
                    commands.timeline().addActorTrack();
                }
                if (ImGui.menuItem(menuLabel(Icons.LIGHT, DirectorLang.Menu.TIMELINE_TRACK_WEATHER))) {
                    commands.timeline().addWeatherTrack();
                }
                if (ImGui.menuItem(menuLabel(Icons.MUSIC, DirectorLang.Menu.TIMELINE_TRACK_AUDIO))) {
                    commands.timeline().addAudioTrack();
                }
                ImGui.endMenu();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.TIMELINE_SCROLL_TO_PLAYHEAD))) {
                commands.timeline().scrollToPlayhead();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.TIMELINE_ZOOM_TO_FIT))) {
                commands.timeline().zoomToFit();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Playback ====================

    private void renderPlaybackMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.PLAY, DirectorLang.Menu.PLAYBACK))) {
            boolean isPlaying = commands.playback().isPlaying();
            String playPauseLabel = isPlaying
                    ? menuLabel(Icons.PAUSE, DirectorLang.Menu.PLAYBACK_PAUSE)
                    : menuLabel(Icons.PLAY, DirectorLang.Menu.PLAYBACK_PLAY);
            if (ImGui.menuItem(playPauseLabel, "Space")) {
                commands.playback().togglePlayPause();
            }

            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_STOP))) {
                commands.playback().stop();
            }

            ImGui.separator();

            float currentSpeed = commands.playback().getPlaybackSpeed();
            if (ImGui.beginMenu(tr(DirectorLang.Menu.PLAYBACK_SPEED))) {
                if (ImGui.menuItem("0.25x", "", Math.abs(currentSpeed - 0.25f) < 0.01f)) {
                    commands.playback().setPlaybackSpeed(0.25f);
                }
                if (ImGui.menuItem("0.5x", "", Math.abs(currentSpeed - 0.5f) < 0.01f)) {
                    commands.playback().setPlaybackSpeed(0.5f);
                }
                if (ImGui.menuItem("1x", "", Math.abs(currentSpeed - 1.0f) < 0.01f)) {
                    commands.playback().setPlaybackSpeed(1.0f);
                }
                if (ImGui.menuItem("2x", "", Math.abs(currentSpeed - 2.0f) < 0.01f)) {
                    commands.playback().setPlaybackSpeed(2.0f);
                }
                ImGui.endMenu();
            }

            boolean isLooping = commands.playback().isLooping();
            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_TOGGLE_LOOP), "", isLooping)) {
                commands.playback().toggleLoop();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_SET_IN), "I")) {
                commands.playback().setInPoint();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_SET_OUT), "O")) {
                commands.playback().setOutPoint();
            }

            boolean hasIn = commands.playback().hasInPoint();
            boolean hasOut = commands.playback().hasOutPoint();
            float inPoint = commands.playback().getInPoint();
            float outPoint = commands.playback().getOutPoint();
            String rangeLabel = (hasIn || hasOut)
                    ? DirectorI18n.translate(DirectorLang.Menu.PLAYBACK_RANGE, inPoint, outPoint)
                    : tr(DirectorLang.Menu.PLAYBACK_RANGE_FULL);
            ImGui.menuItem(rangeLabel, "", false, false);

            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_CLEAR_IN_OUT), "", false, hasIn || hasOut)) {
                commands.playback().clearInOut();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_GO_TO_START), "Home")) {
                commands.playback().goToStart();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.PLAYBACK_GO_TO_END), "End")) {
                commands.playback().goToEnd();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Window ====================

    private void renderWindowMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.WINDOW, DirectorLang.Menu.WINDOW))) {
            if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_SHOW_ALL))) {
                commands.view().showAllPanels();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_HIDE_ALL))) {
                commands.view().hideAllPanels();
            }

            ImGui.separator();

            if (panelRegistry != null && ImGui.beginMenu(tr(DirectorLang.Menu.WINDOW_TOGGLE_PANELS))) {
                for (IPanel panel : panelRegistry.getAllPanels()) {
                    if (panel == this) continue;

                    String id = PanelId.fromPanel(panel);
                    if (id == null) continue;

                    if (ImGui.menuItem(PanelI18n.title(id), "", panel.isVisible())) {
                        commands.view().togglePanel(id);
                    }
                }
                ImGui.endMenu();
            }

            ImGui.separator();

            boolean detachedWindow = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof com.director.ui.DirectorScreen directorScreen) {
                detachedWindow = DirectorServices.get().imgui().isDetachedDirectorOpen(directorScreen);
            }
            if (ImGui.menuItem(menuLabel(Icons.WINDOW, DirectorLang.Menu.WINDOW_EXTERNAL), "", detachedWindow)) {
                if (client.currentScreen instanceof com.director.ui.DirectorScreen directorScreen) {
                    if (detachedWindow) {
                        DirectorServices.get().imgui().closeDetachedDirectorWindow(directorScreen);
                    } else {
                        DirectorServices.get().imgui().openDetachedDirectorWindow(directorScreen);
                    }
                }
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_SAVE_LAYOUT))) {
                commands.layout().showSaveLayoutDialog();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_LOAD_LAYOUT))) {
                commands.layout().showLoadLayoutDialog();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_RESTORE_LAYOUT))) {
                commands.layout().restoreDefaultLayout();
            }

            ImGui.separator();

            if (ImGui.beginMenu(tr(DirectorLang.Menu.WINDOW_THEME))) {
                if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_THEME_DARK), "", true)) {
                    commands.layout().setTheme("Dark");
                }
                if (ImGui.menuItem(tr(DirectorLang.Menu.WINDOW_THEME_LIGHT), "", false, false)) {
                    commands.layout().setTheme("Light");
                }
                ImGui.endMenu();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Help ====================

    private void renderHelpMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(menuLabel(Icons.HELP, DirectorLang.Menu.HELP))) {
            if (ImGui.menuItem(tr(DirectorLang.Menu.HELP_SHORTCUTS), "F1")) {
                commands.help().showShortcutHelp();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.HELP_MANUAL))) {
                commands.help().openUserManual();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.HELP_VIDEO))) {
                commands.help().openVideoTutorial();
            }

            ImGui.separator();

            if (ImGui.menuItem(tr(DirectorLang.Menu.HELP_REPORT_BUG), "", false, false)) {
                commands.help().reportBug();
            }
            if (ImGui.menuItem(tr(DirectorLang.Menu.HELP_ABOUT))) {
                commands.help().showAboutDialog();
            }

            ImGui.endMenu();
        }
    }
}
