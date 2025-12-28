package com.chronoblocks.ui.cursor;

import com.chronoblocks.client.gizmo.Axis;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

/**
 * ImGuiCursorController v1.0
 * ---------------------------
 * 用于在 ImGui 环境下切换鼠标光标形态
 * 取代 GLFW 层的光标控制逻辑
 */
public final class ImGuiCursorController {
    
    public enum CursorType {
        DEFAULT,
        RESIZE_X,   // 水平双箭头 (X 轴)
        RESIZE_Y,   // 垂直双箭头 (Y 轴)
        RESIZE_Z    // Z 轴（在某些平台/后端对角光标不稳定，统一回退到双向箭头）
    }
    
    private static final ImGuiCursorController INSTANCE = new ImGuiCursorController();
    
    private CursorType currentType = CursorType.DEFAULT;
    
    private ImGuiCursorController() {}
    
    public static ImGuiCursorController get() {
        return INSTANCE;
    }
    
    /** 设置当前逻辑光标类型 */
    public void setCursorType(CursorType type) {
        if (type == null) {
            type = CursorType.DEFAULT;
        }
        this.currentType = type;
    }
    
    /** 根据缩放轴切换光标 */
    public void setCursorByAxis(Axis axis) {
        if (axis == null) {
            currentType = CursorType.DEFAULT;
            return;
        }
        
        switch (axis) {
            case X -> currentType = CursorType.RESIZE_X;
            case Y -> currentType = CursorType.RESIZE_Y;
            case Z -> currentType = CursorType.RESIZE_Z;
        }
    }
    
    /** 在 ImGui 帧内调用此方法即可应用光标 */
    public void applyToImGui() {
        if (currentType == CursorType.DEFAULT) {
            // 让 ImGui 自己处理默认光标逻辑（包括窗口/分割条等状态）
            return;
        }
        
        int imguiCursor = switch (currentType) {
            case RESIZE_X -> ImGuiMouseCursor.ResizeEW;   // 水平双箭头
            case RESIZE_Y -> ImGuiMouseCursor.ResizeNS;   // 垂直双箭头
            // ✅ Windows + 部分 ImGui 后端对 ResizeNESW/ResizeNWSE 的系统光标支持不一致，
            // 为了“蓝轴也稳定出现双向箭头”，这里统一回退到 ResizeEW（双向箭头）。
            case RESIZE_Z -> ImGuiMouseCursor.ResizeEW;
            default -> ImGuiMouseCursor.Arrow;
        };
        
        ImGui.setMouseCursor(imguiCursor);
    }
}

