package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.HitResult;

/**
 * 🧭 GizmoHandle - Gizmo 控制手柄接口
 * 
 * 所有类型的 Handle（轴、平面、角点、中心）都实现此接口
 * 
 * @author Director Team
 * @version 1.0
 */
public interface GizmoHandle {
    /**
     * 射线检测
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @return 命中结果
     */
    HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir);
    
    /**
     * 获取 Handle 的世界位置
     * 
     * @return 位置
     */
    Vec3d getPosition();
    
    /**
     * 获取 Handle 的 ID（用于标识）
     * 
     * @return ID
     */
    String getId();
    
    /**
     * 开始拖拽
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向
     */
    void onDragStart(Vec3d rayOrigin, Vec3d rayDir);
    
    /**
     * 更新拖拽
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向
     * @return 拖拽增量
     */
    Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir);
    
    /**
     * 结束拖拽
     */
    void onDragEnd();
    
    /**
     * 是否正在拖拽
     * 
     * @return true 如果正在拖拽
     */
    boolean isDragging();
    
    /**
     * 设置 Handle 位置
     * 
     * @param position 新位置
     */
    void setPosition(Vec3d position);
}

