package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathType;
import imgui.ImGui;

import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.PanelId;

import net.minecraft.client.MinecraftClient;

/**
 * ChronoBlocks MenuBar
 * Facade-based, production-safe implementation
 */
public class MenuBarPanel extends AbstractPanel implements IPanelRegistryAware {

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

    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
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
        if (!ImGui.beginMainMenuBar()) return;

        try {
            renderFileMenu();
            renderTimelineMenu();
            renderCameraMenu();
            renderPlaybackMenu();
            renderWindowMenu();
            renderHelpMenu();
            renderRightSideControls();
        } catch (Exception e) {
            logger.error("[MenuBar] Render error", e);
        } finally {
            ImGui.endMainMenuBar();
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
        if (ImGui.button(Icons.CLOSE + "##closeChronoBlocks")) {
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
            if (ImGui.menuItem("Open Project", "Ctrl+O")) {
                commands.project().openProject();
            }

            ImGui.separator();

            if (ImGui.menuItem("Save", "Ctrl+S")) {
                commands.project().saveProject();
            }
            if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
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

    // ==================== Timeline ====================

    private void renderTimelineMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.TIMELINE + " Timeline")) {
            if (ImGui.menuItem("Timeline Settings")) {
                commands.timeline().openTimelineSettings();
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

    // ==================== Camera ====================

    private void renderCameraMenu() {
        if (commands == null) return;

        if (ImGui.beginMenu(Icons.CAMERA + " Camera")) {
            if (ImGui.beginMenu("Camera Path Preset")) {
                if (ImGui.menuItem("Orbit")) {
                    commands.camera().createCameraPath(CameraPathType.ORBIT);
                }
                if (ImGui.menuItem("Dolly")) {
                    commands.camera().createCameraPath(CameraPathType.DOLLY);
                }
                if (ImGui.menuItem("Crane")) {
                    commands.camera().createCameraPath(CameraPathType.CRANE);
                }
                ImGui.endMenu();
            }

            ImGui.separator();

            if (ImGui.menuItem("Align Camera to View")) {
                commands.camera().alignCameraToCurrentView();
            }
            if (ImGui.menuItem("Create Shot from View")) {
                commands.camera().createShotFromCurrentView();
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
                if (ImGui.menuItem("Light Theme", "", false)) {
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

            if (ImGui.menuItem("Report Bug")) {
                commands.help().reportBug();
            }
            if (ImGui.menuItem("About ChronoBlocks")) {
                commands.help().showAboutDialog();
            }

            ImGui.endMenu();
        }
    }
}
