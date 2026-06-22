package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.aware.IPanelRegistryAware;
import imgui.ImGui;

import com.director.ui.commands.UICommandFacade;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.imgui.ImGuiRenderer;
import com.director.ui.theme.Icons;
import com.director.ui.PanelId;
import com.director.ui.timeline.model.camera.path.CameraPathType;

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
        // 将光标移动到右侧
        float closeButtonWidth = ImGui.calcTextSize(Icons.CLOSE).x 
                + ImGui.getStyle().getFramePadding().x * 2.0f;
        float rightX = ImGui.getWindowWidth() 
                - closeButtonWidth 
                - ImGui.getStyle().getWindowPadding().x;
        
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), rightX));

        // 关闭按钮
        if (ImGui.button(Icons.CLOSE + "##closeDirector")) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    // ==================== File ====================

    private void renderFileMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.FOLDER + " File")) {
            if (ImGui.menuItem("New Project", "Ctrl+N")) {
                commands.project().newProject();
            }
            if (ImGui.menuItem("Reload Project", "Ctrl+O")) {
                commands.project().openProject();
            }

            ImGui.separator();

            if (ImGui.menuItem("Save", "Ctrl+S")) {
                commands.project().saveProject();
            }
            if (ImGui.menuItem("Save As...", "", false, false)) {
                commands.project().saveProjectAs();
            }

            ImGui.separator();

            if (ImGui.menuItem(Icons.VIDEO + " Export Video")) {
                commands.project().exportVideo();
            }

            ImGui.separator();

            if (ImGui.menuItem("Exit", "Alt+F4")) {
                commands.project().exitApplication();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Edit ====================

    private void renderEditMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.EDIT + " Edit")) {
            if (ImGui.menuItem(Icons.UNDO + " Undo", "Ctrl+Z")) {
                commands.timeline().undo();
            }
            if (ImGui.menuItem(Icons.REDO + " Redo", "Ctrl+Y")) {
                commands.timeline().redo();
            }

            ImGui.separator();

            if (ImGui.menuItem(Icons.DELETE + " Delete Selected", "Del")) {
                commands.timeline().deleteSelected();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Timeline ====================

    private void renderTimelineMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.TIMELINE + " Timeline")) {
            if (ImGui.menuItem("Timeline Settings")) {
                commands.timeline().openTimelineSettings();
            }

            ImGui.separator();

            if (ImGui.beginMenu(Icons.ADD + " Add Track")) {
                if (ImGui.menuItem(Icons.CAMERA + " Camera Track")) {
                    commands.timeline().addCameraTrack(CameraPathType.CUSTOM);
                }
                if (ImGui.menuItem(Icons.TARGET + " Actor Track")) {
                    commands.timeline().addActorTrack();
                }
                if (ImGui.menuItem(Icons.LIGHT + " Weather Track")) {
                    commands.timeline().addWeatherTrack();
                }
                if (ImGui.menuItem(Icons.MUSIC + " Audio Track")) {
                    commands.timeline().addAudioTrack();
                }
                ImGui.endMenu();
            }

            ImGui.separator();

            if (ImGui.menuItem("Scroll to Playhead")) {
                commands.timeline().scrollToPlayhead();
            }
            if (ImGui.menuItem("Zoom to Fit")) {
                commands.timeline().zoomToFit();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Playback ====================

    private void renderPlaybackMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.PLAY + " Playback")) {
            // 播放/暂停
            boolean isPlaying = commands.playback().isPlaying();
            String playPauseLabel = isPlaying ? Icons.PAUSE + " Pause" : Icons.PLAY + " Play";
            if (ImGui.menuItem(playPauseLabel, "Space")) {
                commands.playback().togglePlayPause();
            }
            
            // 停止
            if (ImGui.menuItem("Stop")) {
                commands.playback().stop();
            }

            ImGui.separator();

            // 播放速度子菜单（显示当前选中的速度）
            float currentSpeed = commands.playback().getPlaybackSpeed();
            if (ImGui.beginMenu("Playback Speed")) {
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

            // 循环播放（带复选框）
            boolean isLooping = commands.playback().isLooping();
            if (ImGui.menuItem("Toggle Loop", "", isLooping)) {
                commands.playback().toggleLoop();
            }

            ImGui.separator();

            // In/Out 区间
            if (ImGui.menuItem("Set In Point", "I")) {
                commands.playback().setInPoint();
            }
            if (ImGui.menuItem("Set Out Point", "O")) {
                commands.playback().setOutPoint();
            }

            boolean hasIn = commands.playback().hasInPoint();
            boolean hasOut = commands.playback().hasOutPoint();
            float inPoint = commands.playback().getInPoint();
            float outPoint = commands.playback().getOutPoint();
            String rangeLabel = (hasIn || hasOut)
                    ? String.format("Range: %.2fs - %.2fs", inPoint, outPoint)
                    : "Range: Full Timeline";
            ImGui.menuItem(rangeLabel, "", false, false);

            if (ImGui.menuItem("Clear In/Out", "", false, hasIn || hasOut)) {
                commands.playback().clearInOut();
            }

            ImGui.separator();

            // 跳转到开始
            if (ImGui.menuItem("Go to Start", "Home")) {
                commands.playback().goToStart();
            }
            // 跳转到结束
            if (ImGui.menuItem("Go to End", "End")) {
                commands.playback().goToEnd();
            }

            ImGui.endMenu();
        }
    }

    // ==================== Window ====================

    private void renderWindowMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.WINDOW + " Window")) {
            if (ImGui.menuItem("Show All Panels")) {
                commands.view().showAllPanels();
            }
            if (ImGui.menuItem("Hide All Panels")) {
                commands.view().hideAllPanels();
            }

            ImGui.separator();

            if (panelRegistry != null && ImGui.beginMenu("Toggle Panels")) {
                for (IPanel panel : panelRegistry.getAllPanels()) {
                    if (panel == this) continue;

                    String id = PanelId.fromPanel(panel);
                    if (id == null) continue;

                    if (ImGui.menuItem(panel.getPanelName(), "", panel.isVisible())) {
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
            if (ImGui.menuItem(Icons.WINDOW + " External Window", "", detachedWindow)) {
                if (client.currentScreen instanceof com.director.ui.DirectorScreen directorScreen) {
                    if (detachedWindow) {
                        DirectorServices.get().imgui().closeDetachedDirectorWindow(directorScreen);
                    } else {
                        DirectorServices.get().imgui().openDetachedDirectorWindow(directorScreen);
                    }
                }
            }

            ImGui.separator();

            // 布局管理
            if (ImGui.menuItem("Save Layout")) {
                commands.layout().showSaveLayoutDialog();
            }
            if (ImGui.menuItem("Load Layout")) {
                commands.layout().showLoadLayoutDialog();
            }
            if (ImGui.menuItem("Restore Default Layout")) {
                commands.layout().restoreDefaultLayout();
            }

            ImGui.separator();

            // 主题切换
            if (ImGui.beginMenu("Theme")) {
                if (ImGui.menuItem("Dark Theme", "", true)) { // 当前默认主题
                    commands.layout().setTheme("Dark");
                }
                if (ImGui.menuItem("Light Theme", "", false, false)) {
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

        if (ImGui.beginMenu(Icons.HELP + " Help")) {
            if (ImGui.menuItem("Shortcuts", "F1")) {
                commands.help().showShortcutHelp();
            }
            if (ImGui.menuItem("User Manual")) {
                commands.help().openUserManual();
            }
            if (ImGui.menuItem("Video Tutorial")) {
                commands.help().openVideoTutorial();
            }

            ImGui.separator();

            if (ImGui.menuItem("Report Bug", "", false, false)) {
                commands.help().reportBug();
            }
            if (ImGui.menuItem("About Director")) {
                commands.help().showAboutDialog();
            }

            ImGui.endMenu();
        }
    }
}
