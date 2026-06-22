package com.director.ui.imgui;

import com.director.ui.DirectorScreen;
import com.director.ui.window.DetachedDirectorWindow;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.internal.ImGuiContext;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Director ImGui渲染器
 * 基于MasterPlanner的优秀设计，统一管理ImGui的生命周期
 */
public class ImGuiRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderer.class);
    private static volatile ImGuiRenderer INSTANCE;
    private static final Object LOCK = new Object();

    private ImGuiImplGlfw imGuiGlfw;
    private ImGuiImplGl3 imGuiGl3;
    private final DetachedDirectorWindow detachedDirectorWindow = new DetachedDirectorWindow();
    private long windowHandle;
    private long backendWindowHandle;
    private boolean initialized;
    private boolean frameInProgress;
    private boolean drawDataReady;
    private boolean externalViewportsEnabled = false;

    // perf (throttled)
    private long lastPerfLogMs = 0L;
    private long lastRenderNs = 0L;

    private ImGuiRenderer() {}

    /**
     * 创建单例实例（在 {@link com.director.core.DirectorServices#initializeClient()} 中调用，幂等）。
     * OpenGL 后端须在 {@link #init()} 中于 {@code CLIENT_STARTED} 之后初始化。
     */
    public static void initialize() {
        if (INSTANCE != null) {
            return;
        }
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new ImGuiRenderer();
            }
        }
    }

    /**
     * @deprecated 请使用 {@link com.director.core.DirectorServices#get()}{@code .imgui()}
     */
    @Deprecated
    public static ImGuiRenderer getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "[ImGuiRenderer] Not initialized — call DirectorServices.initializeClient()");
        }
        return INSTANCE;
    }

    /** 供框架内部使用的非废弃入口。 */
    public static ImGuiRenderer requireInitialized() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "[ImGuiRenderer] Not initialized — call DirectorServices.initializeClient()");
        }
        return INSTANCE;
    }

    public void init() {
        if (initialized) {
            LOGGER.debug("ImGui already initialized, skipping initialization");
            return;
        }

        try {
            LOGGER.info("[Director] Starting ImGui initialization...");

            // 获取Minecraft窗口
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                LOGGER.error("[Director] Minecraft client or window is null");
                throw new RuntimeException("Minecraft client not initialized");
            }

            // 获取窗口句柄
            windowHandle = client.getWindow().getHandle();
            backendWindowHandle = windowHandle;
            if (windowHandle == 0) {
                LOGGER.error("[Director] Invalid window handle");
                throw new RuntimeException("Invalid window handle");
            }

            // 确保在主线程中执行
            if (!client.isOnThread()) {
                LOGGER.error("[Director] ImGui initialization must be done on the main thread");
                throw new RuntimeException("Not on main thread");
            }

            // 初始化 ImGui - 始终创建并设置上下文（无 Treefactory 时 getCurrentContext 为 null，必须创建）
            ImGuiContext ctx = ImGui.createContext();
            if (ctx == null) {
                throw new RuntimeException("[Director] ImGui.createContext() 返回 null");
            }
            ImGui.setCurrentContext(ctx);

            // 配置 ImGui IO
            ImGuiIO io = ImGui.getIO();
            
            // 启用 ImGui 配置文件保存，用于持久化布局（按世界区分）
            updateIniPath(io);
            
            // 启用键盘导航和停靠功能
            // ⚠️ 多视口支持（ViewportsEnable）暂时禁用
            // 原因：在Minecraft环境中，多视口功能不稳定，会导致：
            // 1. UI闪烁（OpenGL上下文切换冲突）
            // 2. 鼠标输入失效（输入事件被Minecraft拦截）
            // 3. 渲染错误（OpenGL状态不一致）
            // 
            // 如果需要启用多视口，取消下面的注释，但请注意可能存在的问题
            int configFlags = ImGuiConfigFlags.NavEnableKeyboard
                | ImGuiConfigFlags.DockingEnable;
            io.setConfigFlags(configFlags);
            // configFlags |= ImGuiConfigFlags.ViewportsEnable;  // 多视口支持（暂时禁用）
            io.setConfigFlags(configFlags);

            // 初始化字体
            initializeFonts(io);
            applyConfigFlags(io);

            // 不在此处设置样式；DirectorStyleScope 在每帧渲染时临时 push 样式，渲染后 pop，避免影响 Treefactory 等模组

            // 初始化 GLFW 实现
            LOGGER.debug("[Director] Initializing GLFW implementation...");
            imGuiGlfw = new ImGuiImplGlfw();
            // installCallbacks = true: 让 ImGui 接收键盘和鼠标输入
            if (!imGuiGlfw.init(windowHandle, true)) {
                LOGGER.error("[Director] Failed to initialize GLFW implementation");
                throw new RuntimeException("ImGui GLFW implementation init failed");
            }
            LOGGER.info("[Director] ImGui GLFW callbacks installed (keyboard input enabled)");

            // 初始化 OpenGL3 实现
            LOGGER.debug("[Director] Initializing OpenGL3 implementation...");
            imGuiGl3 = new ImGuiImplGl3();
            // 关键：Minecraft 1.21.x 使用 Core Profile，但实际 GLSL 版本取决于显卡/驱动。
            // 如果这里写死过高的版本（例如 410），在只支持 150/330 的环境会直接导致 ImGui shader 编译失败 → 画面“什么都没有”。
            String glsl = pickGlslVersionString();
            LOGGER.info("[Director] Initializing ImGui GL3 backend with {}", glsl);
            // 关键修复（1.21.x 常见坑）：
            // Minecraft 渲染/截图/纹理上传流程可能遗留 GL_UNPACK_* 状态（尤其 UNPACK_ROW_LENGTH），
            // 会直接把 ImGui 的 font atlas 上传“写花”，表现为文字/图标像二维码碎片。
            // 在创建 ImGui device objects（包含 fonts texture）前强制重置像素存储参数。
            resetPixelStoreStateForImGuiTextureUpload();
            imGuiGl3.init(glsl);

            // 标记初始化完成
            initialized = true;
            frameInProgress = false;
            LOGGER.info("[Director] ImGui初始化完成");
        } catch (Exception e) {
            LOGGER.error("[Director] ImGui initialization failed", e);
            dispose();  // 清理资源
            throw e;
        }
    }

    private void initializeFonts(ImGuiIO io) {
        try {
            // 使用新的字体管理器加载中文字体和 Emoji 字体
            com.director.ui.imgui.ImGuiFontManager.initializeFonts(io);
            LOGGER.info("[Director] 字体初始化完成（包含中文和 Emoji 支持）");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 字体初始化失败", e);
            // 确保至少有默认字体
            io.getFonts().addFontDefault();
            io.getFonts().build();
        }
    }
    /**
     * 开始新的ImGui帧
     */
    private void applyConfigFlags(ImGuiIO io) {
        if (io == null) {
            return;
        }

        int flags = io.getConfigFlags();
        flags |= ImGuiConfigFlags.NavEnableKeyboard;
        flags |= ImGuiConfigFlags.DockingEnable;

        if (externalViewportsEnabled) {
            flags |= ImGuiConfigFlags.ViewportsEnable;
        } else {
            flags &= ~ImGuiConfigFlags.ViewportsEnable;
        }

        io.setConfigFlags(flags);
    }

    public void beginFrame() {
        synchronized (LOCK) {
            if (!initialized || frameInProgress) {
                LOGGER.debug("[Director] Skipping beginFrame - initialized: {}, frameInProgress: {}", initialized, frameInProgress);
                return;
            }

            try {
                LOGGER.debug("[Director] Beginning ImGui frame...");

                // 检查并更新INI文件路径（如果世界已切换）
                checkAndUpdateIniPath();

                ImGuiIO io = ImGui.getIO();
                if (detachedDirectorWindow.isOpen()) {
                    detachedDirectorWindow.prepareFrame(io);
                }

                imGuiGlfw.newFrame();
                ImGui.newFrame();
                
                // 合并模式：不需要推送字体，ImGui自动使用默认字体（已包含中文）
                // 之前的pushFont逻辑已移除，因为使用合并模式

                frameInProgress = true;
                LOGGER.debug("[Director] ImGui frame started successfully");
            } catch (Exception e) {
                LOGGER.error("[Director] Failed to begin frame", e);
                frameInProgress = false;
                reinitialize();
            }
        }
    }

    // updateInput 已废弃：输入由 ImGuiImplGlfw 维护，手动覆盖易导致 1.21.x 坐标/缩放不一致问题。

    /**
     * 结束ImGui帧并渲染
     */
    public void endFrame() {
        synchronized (LOCK) {
            if (!initialized || !frameInProgress) {
                LOGGER.debug("[Director] Skipping endFrame - initialized: {}, frameInProgress: {}", initialized, frameInProgress);
                return;
            }

            try {
                LOGGER.debug("[Director] Ending ImGui frame...");
                
                // 合并模式：不需要弹出字体
                
                ImGui.render();
                drawDataReady = true;
                
                LOGGER.debug("[Director] ImGui frame ended successfully");
            } catch (Exception e) {
                LOGGER.error("[Director] Failed to end frame", e);
            } finally {
                frameInProgress = false;
            }
        }
    }

    /**
     * 在“帧末 swap 前”绘制 ImGui（1.21.x 新渲染管线下更稳定）。
     * 典型调用点：注入到 RenderSystem.flipFrame(HEAD)。
     */
    public void renderPendingDrawData() {
        synchronized (LOCK) {
            if (!initialized || !drawDataReady || imGuiGl3 == null) {
                return;
            }
            try {
                RenderSystem.assertOnRenderThread();
                final long t0 = System.nanoTime();
                
                // 保存当前OpenGL状态（多视口会切换上下文）
                int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                
                // 避免被 Minecraft 的 RenderType scissor 影响
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                imgui.ImDrawData drawData = ImGui.getDrawData();
                if (detachedDirectorWindow.isOpen()) {
                    detachedDirectorWindow.renderDrawData(drawData, imGuiGl3);
                } else if (drawData != null && drawData.getCmdListsCount() > 0) {
                    imGuiGl3.renderDrawData(drawData);
                }
                
                // 多视口支持：渲染额外的平台窗口（拖出主窗口的面板）
                // ⚠️ 警告：在Minecraft环境中，多视口功能可能不稳定
                // 原因：Minecraft完全控制OpenGL上下文，多视口需要切换上下文，可能导致：
                // 1. UI闪烁（上下文切换冲突）
                // 2. 鼠标输入失效（输入事件被Minecraft拦截）
                // 3. 渲染错误（OpenGL状态不一致）
                // 
                // 如果遇到这些问题，建议暂时禁用多视口功能（移除ViewportsEnable标志）
                if (isExternalViewportsEnabled()) {
                    try {
                        // 恢复主窗口的OpenGL状态（为上下文切换做准备）
                        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                        if (scissorEnabled) {
                            GL11.glEnable(GL11.GL_SCISSOR_TEST);
                        }
                        
                        final long previousGlfwContext = GLFW.glfwGetCurrentContext();
                        ImGui.updatePlatformWindows();

                        // 渲染所有额外的视口窗口
                        // 注意：这会切换OpenGL上下文，可能与Minecraft的渲染系统冲突
                        ImGui.renderPlatformWindowsDefault();
                        
                        // 恢复主窗口的OpenGL上下文和状态
                        // renderPlatformWindowsDefault应该会自动恢复，但为了安全起见手动恢复
                        if (previousGlfwContext != 0L) {
                            GLFW.glfwMakeContextCurrent(previousGlfwContext);
                        }
                        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                        if (scissorEnabled) {
                            GL11.glEnable(GL11.GL_SCISSOR_TEST);
                        } else {
                            GL11.glDisable(GL11.GL_SCISSOR_TEST);
                        }
                    } catch (Exception viewportException) {
                        LOGGER.error("[Director] 多视口渲染失败，可能由于OpenGL上下文冲突。建议禁用多视口功能。", viewportException);
                        // 恢复基本状态
                        try {
                            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                            if (scissorEnabled) {
                                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                            } else {
                                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                lastRenderNs = System.nanoTime() - t0;
            } catch (Exception e) {
                LOGGER.error("[Director] Failed to render pending ImGui draw data", e);
            } finally {
                drawDataReady = false;
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastPerfLogMs > 5000L) {
                    lastPerfLogMs = nowMs;
                    LOGGER.warn("[Perf][ImGui] renderDrawData last={}ms", String.format("%.2f", lastRenderNs / 1_000_000.0));
                }
            }
        }
    }

    public boolean hasPendingDrawData() {
        return drawDataReady;
    }

    public boolean isExternalViewportsEnabled() {
        return externalViewportsEnabled
            && initialized
            && (ImGui.getIO().getConfigFlags() & ImGuiConfigFlags.ViewportsEnable) != 0;
    }

    public void openDetachedDirectorWindow(final DirectorScreen screen) {
        if (!initialized) {
            return;
        }
        detachedDirectorWindow.open(screen);
    }

    public void closeDetachedDirectorWindow(final DirectorScreen screen) {
        detachedDirectorWindow.close(screen);
    }

    public boolean isDetachedDirectorOpen(final DirectorScreen screen) {
        return detachedDirectorWindow.isDetached(screen);
    }

    public boolean consumeDetachedWindowCloseRequest(final DirectorScreen screen) {
        return detachedDirectorWindow.consumeCloseRequested(screen);
    }

    public void bindPlatformBackendToWindow(final long newWindowHandle) {
        if (!initialized || newWindowHandle == 0L || backendWindowHandle == newWindowHandle) {
            return;
        }

        try {
            if (imGuiGlfw != null) {
                imGuiGlfw.dispose();
            }
        } catch (Exception e) {
            LOGGER.debug("[Director] Rebinding ImGui GLFW backend dispose failed: {}", e.getMessage());
        }

        imGuiGlfw = new ImGuiImplGlfw();
        if (!imGuiGlfw.init(newWindowHandle, true)) {
            throw new RuntimeException("ImGui GLFW backend rebind failed");
        }
        backendWindowHandle = newWindowHandle;
    }

    public void restorePrimaryPlatformBackend() {
        if (!initialized || windowHandle == 0L) {
            return;
        }
        bindPlatformBackendToWindow(windowHandle);
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

                initialized = false;
                frameInProgress = false;
                windowHandle = 0;
                backendWindowHandle = 0;
                detachedDirectorWindow.cleanup();
                LOGGER.info("[Director] ImGui resources cleaned up");
            } catch (Exception e) {
                LOGGER.error("[Director] Error cleaning up ImGui resources", e);
            }
        }
    }

    private void reinitialize() {
        synchronized (LOCK) {
            LOGGER.info("[Director] Attempting to reinitialize ImGui renderer...");
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
                LOGGER.info("[Director] Window handle changed, reinitializing GLFW");
                windowHandle = currentWindowHandle;
                if (!detachedDirectorWindow.isOpen()) {
                    try {
                        bindPlatformBackendToWindow(windowHandle);
                    } catch (Exception e) {
                        LOGGER.error("[Director] Failed to reinitialize GLFW", e);
                        return;
                    }
                }
                LOGGER.info("[Director] ImGui GLFW callbacks reinstalled");
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

            LOGGER.debug("[Director] Updated display size win={}x{} fb={}x{}", winW, winH, fbW, fbH);
        } catch (Exception e) {
            LOGGER.error("[Director] Failed to update display size", e);
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
            com.director.ui.layout.LayoutPersistenceManager layoutManager = 
                com.director.ui.layout.LayoutPersistenceManager.getInstance();
            java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
            String iniPathStr = iniPath.toString().replace('\\', '/');
            io.setIniFilename(iniPathStr);
            LOGGER.info("[Director] ImGui INI文件路径: {}", iniPathStr);
        } catch (Exception e) {
            LOGGER.warn("[Director] 无法获取世界特定的INI路径，使用默认路径: {}", e.getMessage());
            io.setIniFilename("config/director/imgui.ini");
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
            if (com.director.util.WorldIdentifier.hasWorldChanged()) {
                LOGGER.info("[Director] 检测到世界切换，更新ImGui INI文件路径");
                ImGuiIO io = ImGui.getIO();
                updateIniPath(io);
                com.director.util.WorldIdentifier.clearCache();
            }
        } catch (Exception e) {
            LOGGER.warn("[Director] 检查世界切换失败: {}", e.getMessage());
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
            LOGGER.info("[Director] OpenGL={}, GLSL={}", glVersion, slVersion);

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
            // OpenGL 能力探测：驱动/上下文异常时不应阻断 UI 初始化
        }
        // 最保守 fallback
        return "#version 150";
    }

    /**
     * 重置像素存储（pack/unpack）参数，避免 font atlas 上传受 Minecraft 残留状态影响。
     *
     * <p>症状：ImGui 面板正常，但所有文字/图标呈现“二维码/乱码碎片”。</p>
     */
    private static void resetPixelStoreStateForImGuiTextureUpload() {
        try {
            RenderSystem.assertOnRenderThread();
            // unpack
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
            // pack（顺手统一一下，避免后续 readPixels/截图互相污染）
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
            LOGGER.debug("[Director] Reset GL pixel store state for ImGui texture upload");
        } catch (Throwable t) {
            // GL 状态重置在渲染线程边界执行；失败时降级继续，避免整帧 UI 崩溃
            LOGGER.warn("[Director] Failed to reset GL pixel store state (continuing anyway): {}", t.toString());
        }
    }
}
