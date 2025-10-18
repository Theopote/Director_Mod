package com.chronoblocks.ui.input;

import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 输入焦点管理器
 * <p>
 * 负责管理ChronoBlocks UI的输入焦点状态，根据光标位置智能切换输入模式：
 * - 光标在UI面板上时：UI接收输入，游戏不接收
 * - 光标在空白区域时：游戏接收输入（需要按住鼠标中键移动视角）
 * <p>
 * 这是一个单例类，全局唯一
 */
public class InputFocusManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(InputFocusManager.class);
    
    // 单例实例
    private static volatile InputFocusManager instance;
    
    // ChronoBlocks窗口是否打开
    private volatile boolean chronoBlocksScreenActive = false;
    
    // 是否应该捕获鼠标输入（光标在UI面板上）
    private volatile boolean shouldCaptureMouse = false;
    
    // 是否应该捕获键盘输入（光标在UI面板上或输入框激活）
    private volatile boolean shouldCaptureKeyboard = false;
    
    // 是否允许游戏输入（光标在空白区域）
    private volatile boolean allowGameInput = true;

    // 调试模式
    private static final boolean DEBUG = Boolean.getBoolean("chronoblocks.input.debug");
    
    private InputFocusManager() {
        LOGGER.info("[InputFocus] 输入焦点管理器初始化");
    }
    
    /**
     * 获取单例实例
     */
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
    
    /**
     * 更新输入焦点状态
     * <p>
     * 应该在每帧渲染时调用，检测光标位置并更新焦点状态
     */
    public void update() {
        if (!chronoBlocksScreenActive) {
            // ChronoBlocks窗口未打开，重置所有状态
            shouldCaptureMouse = false;
            shouldCaptureKeyboard = false;
            allowGameInput = true;
            return;
        }
        
        try {
            ImGuiIO io = ImGui.getIO();
            
            // 检查ImGui是否想要捕获输入
            boolean imguiWantsMouse = io.getWantCaptureMouse();
            boolean imguiWantsKeyboard = io.getWantCaptureKeyboard();
            
            // 获取当前鼠标位置
            float mouseX = io.getMousePosX();
            float mouseY = io.getMousePosY();
            
            // 更新捕获状态
            shouldCaptureMouse = imguiWantsMouse;
            shouldCaptureKeyboard = imguiWantsKeyboard;
            
            // 游戏输入：只有在光标不在UI上时才允许
            allowGameInput = !imguiWantsMouse;
            
        } catch (Exception e) {
            LOGGER.error("[InputFocus] 更新输入焦点状态时发生错误", e);
        }
    }
    
    /**
     * 设置ChronoBlocks窗口激活状态
     */
    public void setChronoBlocksScreenActive(boolean active) {
        if (this.chronoBlocksScreenActive != active) {
            this.chronoBlocksScreenActive = active;
            
            if (!active) {
                // 窗口关闭时，重置所有状态
                shouldCaptureMouse = false;
                shouldCaptureKeyboard = false;
                allowGameInput = true;
                
                // 恢复鼠标锁定（游戏模式）
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.mouse != null) {
                    client.mouse.lockCursor();
                }
            } else {
                // 窗口打开时，解锁鼠标
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.mouse != null) {
                    client.mouse.unlockCursor();
                }
            }
        }
    }

    /**
     * 检查是否应该捕获键盘输入
     * <p>
     * 用于Mixin中判断是否应该拦截键盘事件
     */
    public boolean shouldCaptureKeyboard() {
        boolean result = chronoBlocksScreenActive && shouldCaptureKeyboard;
        if (DEBUG) {
            LOGGER.trace("[InputFocus] shouldCaptureKeyboard: active={}, capture={}, result={}",
                chronoBlocksScreenActive, shouldCaptureKeyboard, result);
        }
        return result;
    }
}