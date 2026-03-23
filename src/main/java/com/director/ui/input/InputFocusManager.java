package com.director.ui.input;

import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 输入焦点管理器：只有鼠标悬停在 Director UI 上时才拦截键盘/鼠标，否则让 Minecraft 继续接收输入。
 */
public class InputFocusManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputFocusManager.class);
    private static volatile InputFocusManager instance;

    private volatile boolean directorScreenActive = false;
    private volatile boolean shouldCaptureMouse = false;
    private volatile boolean shouldCaptureKeyboard = false;
    private volatile boolean allowGameInput = true;
    private volatile boolean panelHoveredThisFrame = false;

    private static final boolean DEBUG = Boolean.getBoolean("director.input.debug");

    private InputFocusManager() {
        LOGGER.info("[InputFocus] 输入焦点管理器初始化");
    }

    public static InputFocusManager getInstance() {
        if (instance == null) {
            synchronized (InputFocusManager.class) {
                if (instance == null) {
                    instance = new InputFocusManager();
                }
            }
        }
        return instance;
    }

    public void beginFrame() {
        panelHoveredThisFrame = false;
    }

    public void recordPanelHover() {
        panelHoveredThisFrame = true;
    }

    public void update() {
        if (!directorScreenActive) {
            shouldCaptureMouse = false;
            shouldCaptureKeyboard = false;
            allowGameInput = true;
            return;
        }

        panelHoveredThisFrame = false;

        try {
            ImGui.getIO(); // ensure ImGui context exists
            boolean mouseOverGui = panelHoveredThisFrame;

            shouldCaptureMouse = mouseOverGui;
            shouldCaptureKeyboard = mouseOverGui;
            allowGameInput = !mouseOverGui;
        } catch (Exception e) {
            LOGGER.error("[InputFocus] 更新焦点状态失败", e);
        }
    }

    public void setDirectorScreenActive(boolean active) {
        if (this.directorScreenActive == active) {
            return;
        }
        this.directorScreenActive = active;

        if (!active) {
            shouldCaptureMouse = false;
            shouldCaptureKeyboard = false;
            allowGameInput = true;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.mouse != null) {
                client.mouse.lockCursor();
            }
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.mouse != null) {
                client.mouse.unlockCursor();
            }
        }
    }

    public boolean shouldCaptureKeyboard() {
        boolean result = directorScreenActive && shouldCaptureKeyboard;
        if (DEBUG) {
            LOGGER.trace("[InputFocus] shouldCaptureKeyboard: active={}, capture={}, result={}",
                    directorScreenActive, shouldCaptureKeyboard, result);
        }
        return result;
    }
}
