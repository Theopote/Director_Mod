package com.director.client.gizmo;

/**
 * GizmoMode - Gizmo 操作模式枚举
 *
 * <p>定义 Gizmo 的四种基本操作模式：
 * <ul>
 *   <li>TRANSLATE - 平移模式（编辑位置）</li>
 *   <li>ROTATE - 旋转模式（编辑朝向）</li>
 *   <li>SCALE - 缩放模式（编辑缩放）</li>
 *   <li>LOOK_AT - LookAt 模式（编辑看向目标）</li>
 * </ul>
 *
 * <p>导演心智模型：
 * <ul>
 *   <li>"我现在用的是什么 Gizmo，我就在时间线上编辑什么属性"</li>
 *   <li>TRANSLATE → EntityTransformClip.fromPos/toPos</li>
 *   <li>ROTATE → EntityTransformClip.fromRot/toRot</li>
 *   <li>SCALE → EntityTransformClip.fromScale/toScale</li>
 *   <li>LOOK_AT → EntityLookAtClip（点击世界点或摄像机）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public enum GizmoMode {
    /**
     * 平移模式：编辑位置
     */
    TRANSLATE,

    /**
     * 旋转模式：编辑朝向
     */
    ROTATE,

    /**
     * 缩放模式：编辑缩放
     */
    SCALE,

    /**
     * LookAt 模式：编辑看向目标
     * <p>点击世界中的点 → 生成/修改 LookAtClip
     */
    LOOK_AT
}

