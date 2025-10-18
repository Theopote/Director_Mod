package com.chronoblocks.ui.imgui;

import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;

/**
 * ImGui状态检查工具类
 * <p>
 * 用于检查和调试ImGui的Begin/End调用匹配问题，防止状态泄漏。
 * 这个工具类可以帮助开发者快速定位ImGui渲染问题。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ImGuiStateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiStateChecker.class);
    
    // 状态检查标志
    private static boolean enableStateChecking = true; // 默认启用
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 1000; // 1秒检查一次
    
    // Begin/End调用栈跟踪
    private static final ThreadLocal<Deque<String>> imguiStack = ThreadLocal.withInitial(ConcurrentLinkedDeque::new);

    /**
     * 检查ImGui状态，如果发现异常则记录警告
     * @param context 上下文信息，用于标识检查位置
     */
    public static void checkState(String context) {
        if (!enableStateChecking) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime < CHECK_INTERVAL) {
            return; // 避免过于频繁的检查
        }
        lastCheckTime = currentTime;
        
        try {
            // 检查ImGui IO状态
            if (ImGui.getIO() == null) {
                LOGGER.warn("[ImGuiStateChecker] {}: ImGui IO 为空", context);
                return;
            }
            
            // 检查帧率是否正常
            float framerate = ImGui.getIO().getFramerate();
            if (framerate < 10.0f) {
                LOGGER.warn("[ImGuiStateChecker] {}: 帧率过低: {:.1f} FPS", context, framerate);
            }
            
            // 记录当前状态信息
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ImGuiStateChecker] {}: 状态检查完成，帧率: {:.1f} FPS", context, framerate);
            }
            
        } catch (Exception e) {
            LOGGER.error("[ImGuiStateChecker] {}: 状态检查时发生错误: {}", context, e.getMessage(), e);
        }
    }
    
    /**
     * 在关键渲染点之前调用，检查渲染前状态
     * @param componentName 组件名称
     */
    public static void beforeRender(String componentName) {
        checkState("渲染前检查 [" + componentName + "]");
    }
    
    /**
     * 在关键渲染点之后调用，检查渲染后状态
     * @param componentName 组件名称
     */
    public static void afterRender(String componentName) {
        checkState("渲染后检查 [" + componentName + "]");
    }
    
    /**
     * 记录Begin调用
     * @param componentName 组件名称
     * @param windowName 窗口名称
     */
    public static void recordBegin(String componentName, String windowName) {
        if (!enableStateChecking) {
            return;
        }
        
        String context = componentName + ":" + windowName;
        Deque<String> stack = imguiStack.get();
        stack.push(context);
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[ImGuiStateChecker] {}: 开始窗口 '{}' (栈深度: {})", componentName, windowName, stack.size());
        }
    }
    
    /**
     * 记录End调用
     * @param componentName 组件名称
     * @param windowName 窗口名称
     */
    public static void recordEnd(String componentName, String windowName) {
        if (!enableStateChecking) {
            return;
        }
        
        String context = componentName + ":" + windowName;
        Deque<String> stack = imguiStack.get();
        
        if (stack.isEmpty()) {
            LOGGER.error("[ImGuiStateChecker] {}: 尝试结束窗口 '{}'，但调用栈为空！可能存在End调用不匹配", componentName, windowName);
            return;
        }
        
        String topContext = stack.pop();
        if (!topContext.equals(context)) {
            LOGGER.error("[ImGuiStateChecker] {}: 窗口结束不匹配！期望 '{}'，但栈顶是 '{}'", componentName, context, topContext);
            // 在调试模式下，尝试找到匹配的上下文
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ImGuiStateChecker] 当前调用栈: {}", stack);
                LOGGER.debug("[ImGuiStateChecker] 尝试查找匹配的上下文...");
                // 尝试找到匹配的上下文并清理到该位置
                boolean found = false;
                Deque<String> tempStack = new ConcurrentLinkedDeque<>();
                while (!stack.isEmpty()) {
                    String popped = stack.pop();
                    tempStack.push(popped);
                    if (popped.equals(context)) {
                        found = true;
                        break;
                    }
                }
                
                if (found) {
                    LOGGER.debug("[ImGuiStateChecker] 找到匹配的上下文，栈已清理到正确位置");
                    // 清空临时栈，不恢复
                    tempStack.clear();
                } else {
                    LOGGER.debug("[ImGuiStateChecker] 未找到匹配的上下文，清空整个栈以防止级联错误");
                    stack.clear();
                    tempStack.clear();
                }
            }
        }
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[ImGuiStateChecker] {}: 结束窗口 '{}' (栈深度: {})", componentName, windowName, stack.size());
        }
    }
    
    /**
     * 记录渲染异常
     * @param componentName 组件名称
     * @param operation 操作名称
     * @param error 错误信息
     */
    public static void recordError(String componentName, String operation, String error) {
        LOGGER.error("[ImGuiStateChecker] {}: {} 操作失败: {}", componentName, operation, error);
    }

    /**
     * 检查调用栈状态，如果发现不匹配则记录警告
     * @param context 上下文信息
     */
    public static void checkStackState(String context) {
        if (!enableStateChecking) {
            return;
        }
        
        Deque<String> stack = imguiStack.get();
        if (!stack.isEmpty()) {
            LOGGER.warn("[ImGuiStateChecker] {} - ImGui调用栈不为空！可能存在未匹配的Begin/End调用。", context);
            LOGGER.warn("[ImGuiStateChecker] 当前调用栈: {}", stack);
            
            // 在调试模式下，可以选择清空栈以防止级联错误
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ImGuiStateChecker] 清空调用栈以防止级联错误");
                stack.clear();
            }
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ImGuiStateChecker] {} - ImGui调用栈状态正常", context);
            }
        }
    }
    
    /**
     * 强制清理调用栈，用于异常恢复
     * @param context 上下文信息
     */
    public static void forceCleanupStack(String context) {
        if (!enableStateChecking) {
            return;
        }
        
        Deque<String> stack = imguiStack.get();
        if (!stack.isEmpty()) {
            LOGGER.warn("[ImGuiStateChecker] {} - 强制清理调用栈，清理了 {} 个未匹配的调用", context, stack.size());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ImGuiStateChecker] 清理前的调用栈: {}", stack);
            }
            stack.clear();
        }
    }
    
    /**
     * 获取当前调用栈的深度
     * @return 栈深度
     */
    public static int getStackDepth() {
        if (!enableStateChecking) {
            return 0;
        }
        
        return imguiStack.get().size();
    }
    
    /**
     * 获取当前调用栈的副本（用于调试）
     * @return 调用栈的副本
     */
    public static java.util.List<String> getStackCopy() {
        if (!enableStateChecking) {
            return java.util.Collections.emptyList();
        }
        
        return new java.util.ArrayList<>(imguiStack.get());
    }
}
