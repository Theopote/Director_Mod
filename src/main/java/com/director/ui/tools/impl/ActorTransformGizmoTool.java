package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;

/**
 * 演员变换 Gizmo 工具（选择 / 旋转 / 缩放）。
 * <p>切换工具时保留演员选择，取消旧拖动并由新工具重新设置 {@link GizmoMode}。
 */
abstract class ActorTransformGizmoTool extends ActorGizmoModeTool {

    protected ActorTransformGizmoTool(String id, String icon, GizmoMode mode) {
        super(id, icon, mode);
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
    }
}
