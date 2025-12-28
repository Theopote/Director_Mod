package com.chronoblocks.ui.imgui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
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
            
            // 启用 ImGui 配置文件保存，用于持久化布局（按世界区分）
            updateIniPath(io);
            
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
            // 关键：Minecraft 1.21.x 使用 Core Profile，但实际 GLSL 版本取决于显卡/驱动。
            // 如果这里写死过高的版本（例如 410），在只支持 150/330 的环境会直接导致 ImGui shader 编译失败 → 画面“什么都没有”。
            String glsl = pickGlslVersionString();
            LOGGER.info("[ChronoBlocks] Initializing ImGui GL3 backend with {}", glsl);
            imGuiGl3.init(glsl);

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

                // 检查并更新INI文件路径（如果世界已切换）
                checkAndUpdateIniPath();

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
                // 注意：在 1.21.x 中尽量不要“手动覆盖” ImGuiImplGlfw 已维护的输入状态，
                // 以免出现坐标/缩放不一致导致 UI 被裁剪或绘制到屏幕外。
                // 这个方法保留但不再主动调用（见 beginFrame）。
                io.setMousePos((float) mouseX[0], (float) mouseY[0]);
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
                RenderSystem.assertOnRenderThread();
                // 1.21.x：Minecraft 的 RenderType 绘制会使用内部 scissor；若遗留 scissor 状态可能导致 ImGui 全部被裁掉
                RenderSystem.disableScissorForRenderTypeDraws();
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

            // 1.21.x：不要使用 getScaledWidth/Height（GUI 缩放），那会导致 ImGui DisplaySize 与 framebuffer 不一致，
            // 进而出现“绘制了但全在屏幕外/被裁剪”的典型现象。
            int winW = Math.max(1, window.getWidth());
            int winH = Math.max(1, window.getHeight());
            int fbW = Math.max(1, window.getFramebufferWidth());
            int fbH = Math.max(1, window.getFramebufferHeight());
            ImGuiIO io = ImGui.getIO();
            io.setDisplaySize(winW, winH);
            io.setDisplayFramebufferScale((float) fbW / winW, (float) fbH / winH);

            LOGGER.debug("[ChronoBlocks] Updated display size win={}x{} fb={}x{}", winW, winH, fbW, fbH);
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Failed to update display size", e);
        }

    }

    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 更新ImGui INI文件路径（按世界区分）
     */
    private void updateIniPath(ImGuiIO io) {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
            String iniPathStr = iniPath.toString().replace('\\', '/');
            io.setIniFilename(iniPathStr);
            LOGGER.info("[ChronoBlocks] ImGui INI文件路径: {}", iniPathStr);
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 无法获取世界特定的INI路径，使用默认路径: {}", e.getMessage());
            io.setIniFilename("config/chronoblocks/imgui.ini");
        }
    }
    
    /**
     * 检查并更新INI文件路径（如果世界已切换）
     */
    public void checkAndUpdateIniPath() {
        if (!initialized) {
            return;
        }
        
        try {
            if (com.chronoblocks.util.WorldIdentifier.hasWorldChanged()) {
                LOGGER.info("[ChronoBlocks] 检测到世界切换，更新ImGui INI文件路径");
                ImGuiIO io = ImGui.getIO();
                updateIniPath(io);
                com.chronoblocks.util.WorldIdentifier.clearCache();
            }
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 检查世界切换失败: {}", e.getMessage());
        }
    }

    /**
     * 根据当前 OpenGL/GLSL 能力选择 ImGui GL3 backend 的 shader version 字符串。
     * 这是解决“升级后 UI 不显示”的高频原因：shader 版本写死过高/过低导致编译失败。
     */
    private static String pickGlslVersionString() {
        try {
            String glVersion = GL11.glGetString(GL11.GL_VERSION);
            String slVersion = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
            LOGGER.info("[ChronoBlocks] OpenGL={}, GLSL={}", glVersion, slVersion);

            // 粗略提取 GLSL 主版本（如 "4.60" / "3.30" / "1.50"）
            int major = 0;
            int minor = 0;
            if (slVersion != null) {
                String[] parts = slVersion.trim().split("[^0-9.]+")[0].split("\\.");
                if (parts.length >= 1) major = Integer.parseInt(parts[0]);
                if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
            }

            // GLSL 1.50 ~ OpenGL 3.2 core（Minecraft 常见下限）
            if (major <= 1) {
                return "#version 150";
            }
            // 3.30+ 更常见（现代驱动）
            if (major == 3) {
                return "#version 330 core";
            }
            // 4.x
            if (major >= 4) {
                // 4.10+ 用 410，否则用 330 core
                if (major > 4 || minor >= 10) return "#version 410 core";
                return "#version 330 core";
            }
        } catch (Throwable t) {
            // ignore
        }
        // 最保守 fallback
        return "#version 150";
    }
}
