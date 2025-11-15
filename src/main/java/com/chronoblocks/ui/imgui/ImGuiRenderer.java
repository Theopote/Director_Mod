package com.chronoblocks.ui.imgui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChronoBlocks ImGui渲染器
 * 基于MasterPlanner的优秀设计，统一管理ImGui的生命周期
 */
public class ImGuiRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderer.class);
    private static volatile ImGuiRenderer INSTANCE;
    private static final Object LOCK = new Object();

    private ImGuiImplGlfw imGuiGlfw;
    private ImGuiImplGl3 imGuiGl3;
    private long windowHandle;
    private boolean initialized;
    private boolean frameInProgress;

    private ImGuiRenderer() {}

    public static ImGuiRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ImGuiRenderer();
                }
            }
        }
        return INSTANCE;
    }

    public void init() {
        if (initialized) {
            LOGGER.debug("ImGui already initialized, skipping initialization");
            return;
        }

        try {
            LOGGER.info("[ChronoBlocks] Starting ImGui initialization...");

            // 获取Minecraft窗口
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                LOGGER.error("[ChronoBlocks] Minecraft client or window is null");
                throw new RuntimeException("Minecraft client not initialized");
            }

            // 获取窗口句柄
            windowHandle = client.getWindow().getHandle();
            if (windowHandle == 0) {
                LOGGER.error("[ChronoBlocks] Invalid window handle");
                throw new RuntimeException("Invalid window handle");
            }

            // 确保在主线程中执行
            if (!client.isOnThread()) {
                LOGGER.error("[ChronoBlocks] ImGui initialization must be done on the main thread");
                throw new RuntimeException("Not on main thread");
            }

            // 初始化 ImGui
            LOGGER.debug("[ChronoBlocks] Creating ImGui context...");
            ImGui.createContext();

            // 配置 ImGui IO
            ImGuiIO io = ImGui.getIO();
            
            // 启用 ImGui 配置文件保存，用于持久化布局
            String iniPath = "config/chronoblocks/imgui.ini";
            io.setIniFilename(iniPath);
            LOGGER.info("[ChronoBlocks] ImGui INI文件路径: {}", iniPath);
            
            io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard | ImGuiConfigFlags.DockingEnable);  // 启用键盘导航和停靠功能

            // 初始化字体
            initializeFonts(io);

            // 设置 ImGui 样式
            LOGGER.debug("[ChronoBlocks] Setting ImGui style...");
            ImGui.styleColorsDark();  // 使用深色主题

            // 初始化 GLFW 实现
            LOGGER.debug("[ChronoBlocks] Initializing GLFW implementation...");
            imGuiGlfw = new ImGuiImplGlfw();
            // installCallbacks = true: 让 ImGui 接收键盘和鼠标输入
            if (!imGuiGlfw.init(windowHandle, true)) {
                LOGGER.error("[ChronoBlocks] Failed to initialize GLFW implementation");
                throw new RuntimeException("ImGui GLFW implementation init failed");
            }
            LOGGER.info("[ChronoBlocks] ImGui GLFW callbacks installed (keyboard input enabled)");

            // 初始化 OpenGL3 实现
            LOGGER.debug("[ChronoBlocks] Initializing OpenGL3 implementation...");
            imGuiGl3 = new ImGuiImplGl3();
            imGuiGl3.init("#version 410 core");  // 使用OpenGL 4.1核心配置，兼容新版本Minecraft

            // 标记初始化完成
            initialized = true;
            frameInProgress = false;
            LOGGER.info("[ChronoBlocks] ImGui初始化完成");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] ImGui initialization failed", e);
            dispose();  // 清理资源
            throw e;
        }
    }

    private void initializeFonts(ImGuiIO io) {
        try {
            // 使用新的字体管理器加载中文字体和 Emoji 字体
            com.chronoblocks.ui.imgui.ImGuiFontManager.initializeFonts(io);
            LOGGER.info("[ChronoBlocks] 字体初始化完成（包含中文和 Emoji 支持）");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 字体初始化失败", e);
            // 确保至少有默认字体
            io.getFonts().addFontDefault();
            io.getFonts().build();
        }
    }
    /**
     * 开始新的ImGui帧
     */
    public void beginFrame() {
        synchronized (LOCK) {
            if (!initialized || frameInProgress) {
                LOGGER.debug("[ChronoBlocks] Skipping beginFrame - initialized: {}, frameInProgress: {}", initialized, frameInProgress);
                return;
            }

            try {
                LOGGER.debug("[ChronoBlocks] Beginning ImGui frame...");

                // 更新输入状态
                updateInput();

                imGuiGlfw.newFrame();
                ImGui.newFrame();
                
                // 合并模式：不需要推送字体，ImGui自动使用默认字体（已包含中文）
                // 之前的pushFont逻辑已移除，因为使用合并模式

                frameInProgress = true;
                LOGGER.debug("[ChronoBlocks] ImGui frame started successfully");
            } catch (Exception e) {
                LOGGER.error("[ChronoBlocks] Failed to begin frame", e);
                frameInProgress = false;
                reinitialize();
            }
        }
    }

    private void updateInput() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                Window window = client.getWindow();
                double[] mouseX = new double[1];
                double[] mouseY = new double[1];
                GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);

                ImGuiIO io = ImGui.getIO();

                // 获取窗口的缩放因子
                float scaleX = (float)window.getScaleFactor();
                float scaleY = (float)window.getScaleFactor();

                // 计算正确的鼠标位置
                float scaledMouseX = (float)mouseX[0] / scaleX;
                float scaledMouseY = (float)mouseY[0] / scaleY;

                // 更新鼠标位置
                io.setMousePos(scaledMouseX, scaledMouseY);

                // 更新鼠标按钮状态
                io.setMouseDown(0, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
                io.setMouseDown(1, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
                io.setMouseDown(2, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS);
            }
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Failed to update input", e);
        }
    }

    /**
     * 结束ImGui帧并渲染
     */
    public void endFrame() {
        synchronized (LOCK) {
            if (!initialized || !frameInProgress) {
                LOGGER.debug("[ChronoBlocks] Skipping endFrame - initialized: {}, frameInProgress: {}", initialized, frameInProgress);
                return;
            }

            try {
                LOGGER.debug("[ChronoBlocks] Ending ImGui frame...");
                
                // 合并模式：不需要弹出字体
                
                ImGui.render();
                imGuiGl3.renderDrawData(ImGui.getDrawData());
                
                LOGGER.debug("[ChronoBlocks] ImGui frame ended successfully");
            } catch (Exception e) {
                LOGGER.error("[ChronoBlocks] Failed to end frame", e);
            } finally {
                frameInProgress = false;
            }
        }
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        synchronized (LOCK) {
            if (!initialized) {
                return;
            }

            try {
                if (frameInProgress) {
                    endFrame();
                }

                if (imGuiGl3 != null) {
                    imGuiGl3.dispose();
                    imGuiGl3 = null;
                }

                if (imGuiGlfw != null) {
                    imGuiGlfw.dispose();
                    imGuiGlfw = null;
                }

                ImGui.destroyContext();

                initialized = false;
                frameInProgress = false;
                windowHandle = 0;
                LOGGER.info("[ChronoBlocks] ImGui resources cleaned up");
            } catch (Exception e) {
                LOGGER.error("[ChronoBlocks] Error cleaning up ImGui resources", e);
            }
        }
    }

    private void reinitialize() {
        synchronized (LOCK) {
            LOGGER.info("[ChronoBlocks] Attempting to reinitialize ImGui renderer...");
            dispose();
            init();
        }
    }

    /**
     * 更新显示尺寸
     */
    public void updateDisplaySize() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return;
            }

            Window window = client.getWindow();

            // 检查窗口句柄是否发生变化
            long currentWindowHandle = window.getHandle();
            if (currentWindowHandle != windowHandle) {
                LOGGER.info("[ChronoBlocks] Window handle changed, reinitializing GLFW");
                windowHandle = currentWindowHandle;

                if (imGuiGlfw != null) {
                    imGuiGlfw.dispose();
                }
                imGuiGlfw = new ImGuiImplGlfw();
                // installCallbacks = true: 重新安装输入回调
                if (!imGuiGlfw.init(windowHandle, true)) {
                    LOGGER.error("[ChronoBlocks] Failed to reinitialize GLFW");
                    return;
                }
                LOGGER.info("[ChronoBlocks] ImGui GLFW callbacks reinstalled");
            }

            // 更新显示尺寸
            int scaledWidth = window.getScaledWidth();
            int scaledHeight = window.getScaledHeight();
            ImGui.getIO().setDisplaySize(scaledWidth, scaledHeight);

            LOGGER.debug("[ChronoBlocks] Updated display size: {}x{}", scaledWidth, scaledHeight);
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Failed to update display size", e);
        }

    }

    public boolean isInitialized() {
        return initialized;
    }
}
