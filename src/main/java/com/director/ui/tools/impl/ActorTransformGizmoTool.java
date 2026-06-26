package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;

/**
 * 演员变换 Gizmo 工具（选择 / 旋转 / 缩放）。
 * <p>切换这些工具时保留已选演员与 Gizmo 目标，仅变更 {@link GizmoMode}。
 */
abstract class ActorTransformGizmoTool extends ActorGizmoModeTool {

    protected ActorTransformGizmoTool(String id, String icon, GizmoMode mode) {
        super(id, icon, mode);
    }

    @Override
    public void onDeactivate() {
        // 变换类工具之间切换时不清除 Gizmo，避免选中态闪灭
    }
}
