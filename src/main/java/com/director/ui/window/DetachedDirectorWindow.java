package com.director.ui.window;

import com.director.ui.DirectorScreen;
import com.director.ui.imgui.ImGuiRenderer;
import imgui.ImDrawData;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DetachedDirectorWindow {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetachedDirectorWindow.class);
    private static final String WINDOW_TITLE = "Director";

    private long windowHandle;
    private DirectorScreen attachedScreen;
    private boolean previousLeftMouseDown;
    private boolean previousRightMouseDown;
    private boolean currentLeftMouseDown;
    private boolean currentRightMouseDown;
    private boolean currentMiddleMouseDown;
    private DirectorScreen pendingCloseScreen;

    public boolean isDetached(final DirectorScreen screen) {
        return windowHandle != 0L && attachedScreen == screen;
    }

    public boolean isOpen() {
        return windowHandle != 0L && attachedScreen != null;
    }

    public long getWindowHandle() {
        return windowHandle;
    }

    public void open(final DirectorScreen screen) {
        if (screen == null) {
            return;
        }

        if (isDetached(screen)) {
            GLFW.glfwShowWindow(windowHandle);
            GLFW.glfwFocusWindow(windowHandle);
            return;
        }

        cleanup();

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            LOGGER.warn("Minecraft window is unavailable, cannot detach Director");
            return;
        }

        final long primaryWindowHandle = client.getWindow().getHandle();
        if (primaryWindowHandle == 0L) {
            LOGGER.warn("Primary GLFW window handle is invalid, cannot detach Director");
            return;
        }

        final int[] posX = new int[1];
        final int[] posY = new int[1];
        final int[] width = new int[1];
        final int[] height = new int[1];
        final int[] workareaX = new int[1];
        final int[] workareaY = new int[1];
        final int[] workareaWidth = new int[1];
        final int[] workareaHeight = new int[1];
        pickWindowPlacement(posX, posY, width, height, workareaX, workareaY, workareaWidth, workareaHeight);

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_TRUE);

        windowHandle = GLFW.glfwCreateWindow(width[0], height[0], WINDOW_TITLE, 0, primaryWindowHandle);
        if (windowHandle == 0L) {
            LOGGER.error("Failed to create detached Director window");
            return;
        }

        final long previousGlContext = GLFW.glfwGetCurrentContext();
        try {
            GLFW.glfwMakeContextCurrent(windowHandle);
            GLFW.glfwSetWindowSize(windowHandle, width[0], height[0]);
            GLFW.glfwSetWindowPos(windowHandle, posX[0], posY[0]);
            GLFW.glfwSwapInterval(0);

            attachedScreen = screen;
            pendingCloseScreen = null;
            previousLeftMouseDown = false;
            previousRightMouseDown = false;
            currentLeftMouseDown = false;
            currentRightMouseDown = false;
            currentMiddleMouseDown = false;
            ImGuiRenderer.getInstance().bindPlatformBackendToWindow(windowHandle);
            GLFW.glfwShowWindow(windowHandle);
            fitWindowFrameIntoWorkarea(posX, posY, width, height, workareaX, workareaY, workareaWidth, workareaHeight);
            GLFW.glfwSetWindowSize(windowHandle, width[0], height[0]);
            GLFW.glfwSetWindowPos(windowHandle, posX[0], posY[0]);
            GLFW.glfwFocusWindow(windowHandle);
            LOGGER.info("Detached Director window opened: {}", windowHandle);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize detached Director window", e);
            cleanup();
        } finally {
            if (previousGlContext != 0L) {
                GLFW.glfwMakeContextCurrent(previousGlContext);
            }
        }
    }

    public void close(final DirectorScreen screen) {
        if (screen == null || attachedScreen != screen) {
            return;
        }
        if (pendingCloseScreen == screen) {
            pendingCloseScreen = null;
        }
        cleanup();
    }

    public boolean consumeCloseRequested(final DirectorScreen screen) {
        if (screen == null || pendingCloseScreen != screen) {
            return false;
        }
        pendingCloseScreen = null;
        return true;
    }

    public void prepareFrame(final ImGuiIO io) {
        if (!isOpen() || io == null) {
            return;
        }

        final int[] width = new int[1];
        final int[] height = new int[1];
        final int[] framebufferWidth = new int[1];
        final int[] framebufferHeight = new int[1];
        final double[] mouseX = new double[1];
        final double[] mouseY = new double[1];

        GLFW.glfwGetWindowSize(windowHandle, width, height);
        GLFW.glfwGetFramebufferSize(windowHandle, framebufferWidth, framebufferHeight);
        GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);

        io.setDisplaySize(Math.max(1, width[0]), Math.max(1, height[0]));
        io.setDisplayFramebufferScale(
            Math.max(1.0f, (float) framebufferWidth[0] / Math.max(1.0f, width[0])),
            Math.max(1.0f, (float) framebufferHeight[0] / Math.max(1.0f, height[0]))
        );
        io.setMousePos((float) mouseX[0], (float) mouseY[0]);
        pollDetachedMouseButtons(io);
    }

    public void renderDrawData(final ImDrawData drawData, final ImGuiImplGl3 rendererBackend) {
        if (!isOpen() || rendererBackend == null) {
            return;
        }

        if (GLFW.glfwWindowShouldClose(windowHandle)) {
            pendingCloseScreen = attachedScreen;
            cleanup();
            return;
        }

        final long previousGlContext = GLFW.glfwGetCurrentContext();
        try {
            GLFW.glfwMakeContextCurrent(windowHandle);

            final int[] framebufferWidth = new int[1];
            final int[] framebufferHeight = new int[1];
            GLFW.glfwGetFramebufferSize(windowHandle, framebufferWidth, framebufferHeight);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, Math.max(1, framebufferWidth[0]), Math.max(1, framebufferHeight[0]));
            GL11.glClearColor(0.06f, 0.07f, 0.09f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            if (drawData != null && drawData.getCmdListsCount() > 0) {
                rendererBackend.renderDrawData(drawData);
            }

            GLFW.glfwSwapBuffers(windowHandle);
        } catch (Exception e) {
            LOGGER.error("Failed to render detached Director window", e);
            cleanup();
        } finally {
            if (previousGlContext != 0L) {
                GLFW.glfwMakeContextCurrent(previousGlContext);
            }
        }
    }

    public void cleanup() {
        final long handleToDestroy = windowHandle;
        windowHandle = 0L;
        attachedScreen = null;
        previousLeftMouseDown = false;
        previousRightMouseDown = false;
        currentLeftMouseDown = false;
        currentRightMouseDown = false;
        currentMiddleMouseDown = false;

        if (handleToDestroy != 0L) {
            ImGuiRenderer.getInstance().restorePrimaryPlatformBackend();
            try {
                Callbacks.glfwFreeCallbacks(handleToDestroy);
            } catch (Exception e) {
                LOGGER.debug("Detached Director callback free failed: {}", e.getMessage());
            }
            try {
                GLFW.glfwDestroyWindow(handleToDestroy);
            } catch (Exception e) {
                LOGGER.debug("Detached Director window destroy failed: {}", e.getMessage());
            }
        }
    }

    private void pollDetachedMouseButtons(final ImGuiIO io) {
        final boolean leftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        final boolean rightDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        final boolean middleDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        currentLeftMouseDown = leftDown;
        currentRightMouseDown = rightDown;
        currentMiddleMouseDown = middleDown;
        io.setMouseDown(0, leftDown);
        io.setMouseDown(1, rightDown);
        io.setMouseDown(2, middleDown);

        if (leftDown != previousLeftMouseDown || rightDown != previousRightMouseDown) {
            LOGGER.debug(
                "Detached Director input changed: focused={}, hovered={}, leftDown={}, rightDown={}, mouse=({}, {}), wantCaptureMouse={}",
                GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE,
                GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE,
                leftDown,
                rightDown,
                io.getMousePosX(),
                io.getMousePosY(),
                io.getWantCaptureMouse()
            );
            previousLeftMouseDown = leftDown;
            previousRightMouseDown = rightDown;
        }
    }

    private void pickWindowPlacement(
        final int[] posX,
        final int[] posY,
        final int[] width,
        final int[] height,
        final int[] workareaX,
        final int[] workareaY,
        final int[] workareaWidth,
        final int[] workareaHeight
    ) {
        final PointerBuffer monitors = GLFW.glfwGetMonitors();
        long targetMonitor = 0L;

        if (monitors != null && monitors.limit() > 1) {
            targetMonitor = monitors.get(1);
        } else if (monitors != null && monitors.limit() > 0) {
            targetMonitor = monitors.get(0);
        }

        if (targetMonitor != 0L) {
            GLFW.glfwGetMonitorWorkarea(targetMonitor, workareaX, workareaY, workareaWidth, workareaHeight);

            final int monitorWorkareaX = workareaX[0];
            final int monitorWorkareaY = workareaY[0];
            final int monitorWorkareaWidth = Math.max(1, workareaWidth[0]);
            final int monitorWorkareaHeight = Math.max(1, workareaHeight[0]);

            width[0] = Math.max(960, Math.min(1440, monitorWorkareaWidth));
            height[0] = Math.max(640, Math.min(900, monitorWorkareaHeight));
            posX[0] = monitorWorkareaX + Math.max(0, (monitorWorkareaWidth - width[0]) / 2);
            posY[0] = monitorWorkareaY + Math.max(0, (monitorWorkareaHeight - height[0]) / 2);
        } else {
            posX[0] = 80;
            posY[0] = 80;
            width[0] = 1440;
            height[0] = 900;
            workareaX[0] = posX[0];
            workareaY[0] = posY[0];
            workareaWidth[0] = width[0];
            workareaHeight[0] = height[0];
        }
    }

    private void fitWindowFrameIntoWorkarea(
        final int[] posX,
        final int[] posY,
        final int[] width,
        final int[] height,
        final int[] workareaX,
        final int[] workareaY,
        final int[] workareaWidth,
        final int[] workareaHeight
    ) {
        final int[] frameLeft = new int[1];
        final int[] frameTop = new int[1];
        final int[] frameRight = new int[1];
        final int[] frameBottom = new int[1];
        GLFW.glfwGetWindowFrameSize(windowHandle, frameLeft, frameTop, frameRight, frameBottom);

        final int maxClientWidth = Math.max(1, workareaWidth[0] - frameLeft[0] - frameRight[0]);
        final int maxClientHeight = Math.max(1, workareaHeight[0] - frameTop[0] - frameBottom[0]);

        width[0] = Math.min(width[0], maxClientWidth);
        height[0] = Math.min(height[0], maxClientHeight);

        final int outerWidth = width[0] + frameLeft[0] + frameRight[0];
        final int outerHeight = height[0] + frameTop[0] + frameBottom[0];

        posX[0] = workareaX[0] + frameLeft[0] + Math.max(0, (workareaWidth[0] - outerWidth) / 2);
        posY[0] = workareaY[0] + frameTop[0] + Math.max(0, (workareaHeight[0] - outerHeight) / 2);
    }
}
