package com.chronoblocks.mixin;

import com.chronoblocks.core.selection.SelectionInputHandler;
import com.chronoblocks.ui.ChronoBlocksScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🖱️ MouseMixin v2.0 (ChronoBlocks 专用输入系统)
 * ======================================================
 * ✅ 改进点：
 * - 精确 ImGui 区域 Hover 判断（GLFW 像素坐标）
 * - 线程安全的 SelectionInputHandler 拖拽逻辑
 * - 视角拖拽灵敏度自动匹配鼠标灵敏度
 * - 在 UI 外自动恢复光标锁定
 * - 减少多余日志，优化逻辑层次
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("ChronoBlocks-MouseMixin");

    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Unique
    private boolean chronoblocks$isDragging = false;

    @Unique
    private SelectionInputHandler selectionInputHandler;

    @Unique
    private double lastX, lastY;

    /**
     * 🔍 判断鼠标是否在 ChronoBlocks ImGui 区域内
     */
    @Unique
    private boolean isHoveringChronoBlocksUI(ChronoBlocksScreen screen) {
        if (client == null || client.getWindow() == null) return false;
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), x, y);
        return screen.isMouseOverChronoBlocksGui(x[0], y[0]);
    }

    /**
     * 🎯 光标移动事件：控制中键拖动视角
     * 在 @At("TAIL") 阶段调用，确保 cursorDelta 已更新。
     */
    @Inject(method = "onCursorPos", at = @At("TAIL"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (!(client.currentScreen instanceof ChronoBlocksScreen screen)) return;

        boolean hover = isHoveringChronoBlocksUI(screen);

        // 更新位移记录
        double dx = x - lastX;
        double dy = y - lastY;
        lastX = x;
        lastY = y;

        // ⭐ 检测玩家鼠标输入：如果不在播放/scrubbing，立即切回玩家控制
        if (!hover && client.player != null) {
            com.chronoblocks.ui.timeline.playback.camera.CameraRuntime runtime = 
                com.chronoblocks.ui.timeline.playback.camera.CameraRuntime.getInstance();
            
            // 检测鼠标移动（表示玩家想要操作摄像机）
            boolean hasMouseMovement = Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001;
            
            if (hasMouseMovement && runtime.isTimelineOwner()) {
                // 检查时间线是否真的在活动（播放或 scrubbing）
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                
                if (!controller.isTimelineActive()) {
                    // ⭐ 播放头静止 + 玩家鼠标移动 = 立即切回玩家控制
                    controller.clearPreviewState();
                    runtime.setOwner(com.chronoblocks.ui.timeline.playback.camera.CameraRuntime.Owner.PLAYER);
                    com.chronoblocks.client.ChronoBlocksClientController.getInstance().setHideCrosshair(false);
                    LOGGER.debug("[MouseMixin] 检测到玩家鼠标输入，自动切换回玩家控制");
                }
            }
            
            // 中键拖拽时移动视角（仅在玩家控制时）
            if (chronoblocks$isDragging && runtime.isPlayerOwner()) {
                double baseSensitivity = client.options.getMouseSensitivity().getValue();
                double scale = baseSensitivity * 2.0; // ✅ 动态灵敏度系数
                client.player.changeLookDirection(dx * scale, dy * scale);
            }
        }
    }

    /**
     * 🖱️ 鼠标按键事件
     * - 中键控制视角拖拽模式
     * - 左键触发选择逻辑
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (selectionInputHandler == null) {
            selectionInputHandler = new SelectionInputHandler();
        }

        // 🎥 中键：切换视角拖拽模式
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            chronoblocks$isDragging = (action == GLFW.GLFW_PRESS);
            return;
        }

        // 仅在 ChronoBlocks 打开时拦截
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            boolean hover = isHoveringChronoBlocksUI(screen);

            if (hover) {
                // 🧱 在 ImGui 面板上点击：拦截事件
                ci.cancel();
                return;
            }

            // 🧭 不在 UI 上：交给方块选择逻辑处理
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
                synchronized (selectionInputHandler) {
                    if (action == GLFW.GLFW_PRESS) {
                        LOGGER.debug("[ChronoBlocks] 左键按下（shift={}）", shift);
                        selectionInputHandler.handleMouseDragStart(true);
                    } else if (action == GLFW.GLFW_RELEASE) {
                        LOGGER.debug("[ChronoBlocks] 左键释放，拖拽状态={}", selectionInputHandler.isDragging());
                        // 先检查是否在拖拽，如果在拖拽则先结束拖拽，否则处理点击
                        if (selectionInputHandler.isDragging()) {
                            selectionInputHandler.handleMouseDragEnd(true);
                        } else {
                            // 如果不在拖拽，先处理点击，然后清理拖拽状态（如果有）
                            selectionInputHandler.handleMouseClick(true, shift);
                            // 清理拖拽状态（如果 dragStart 被设置但没有真正拖拽）
                            if (selectionInputHandler.getDragStart() != null) {
                                selectionInputHandler.handleMouseDragEnd(true);
                            }
                        }
                    }
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // 右键点击处理（用于两点式建盒）
                synchronized (selectionInputHandler) {
                    if (action == GLFW.GLFW_PRESS) {
                        LOGGER.debug("[ChronoBlocks] 右键按下");
                        // 右键点击事件在按下时立即处理
                        selectionInputHandler.handleMouseRightClick();
                    }
                }
            }
        }
    }

    /**
     * 🌀 鼠标滚轮事件：在 UI 区域内拦截
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            if (isHoveringChronoBlocksUI(screen)) {
                ci.cancel();
            }
        }
    }

    /**
     * 🔒 防止 UI 区域中光标被锁定
     * 在 UI 外自动恢复锁定状态
     */
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            boolean hover = isHoveringChronoBlocksUI(screen);
            if (hover) {
                ci.cancel();
            } else if (!client.mouse.isCursorLocked()) {
                client.mouse.lockCursor();
            }
        }
    }

    /**
     * ⏱️ 每帧更新拖拽逻辑（线程安全）
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (selectionInputHandler != null) {
            synchronized (selectionInputHandler) {
                selectionInputHandler.tick();
                // 检查 dragStart 而不是 isDragging，因为 isDragging 是在 handleMouseDragUpdate 中设置的
                // 这样可以检测到拖拽开始（鼠标移动到不同方块时）
                if (selectionInputHandler.getDragStart() != null) {
                    selectionInputHandler.handleMouseDragUpdate();
                }
            }
        }
    }
}