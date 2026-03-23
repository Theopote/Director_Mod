package com.director.client.gizmo.util;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.Axis;

/**
 * 🧭 SnapUtil - 吸附工具类
 * 
 * @author Director Team
 * @version 1.0
 */
public class SnapUtil {
    
    /**
     * 对向量进行步长吸附
     * 
     * @param v 原始向量
     * @param step 步长
     * @param axisOrNull 指定轴（null 表示所有轴）
     * @return 吸附后的向量
     */
    public static Vec3d snapVec(Vec3d v, double step, Axis axisOrNull) {
        double x = (axisOrNull == null || axisOrNull == Axis.X) ? snap(v.x, step) : v.x;
        double y = (axisOrNull == null || axisOrNull == Axis.Y) ? snap(v.y, step) : v.y;
        double z = (axisOrNull == null || axisOrNull == Axis.Z) ? snap(v.z, step) : v.z;
        return new Vec3d(x, y, z);
    }
    
    /**
     * 对向量在两个轴上吸附
     * 
     * @param v 原始向量
     * @param step 步长
     * @param a1 轴1
     * @param a2 轴2
     * @return 吸附后的向量
     */
    public static Vec3d snapVec2(Vec3d v, double step, Axis a1, Axis a2) {
        double x = (a1 == Axis.X || a2 == Axis.X) ? snap(v.x, step) : v.x;
        double y = (a1 == Axis.Y || a2 == Axis.Y) ? snap(v.y, step) : v.y;
        double z = (a1 == Axis.Z || a2 == Axis.Z) ? snap(v.z, step) : v.z;
        return new Vec3d(x, y, z);
    }
    
    /**
     * 对单个值进行步长吸附
     * 
     * @param value 原始值
     * @param step 步长
     * @return 吸附后的值
     */
    public static double snap(double value, double step) {
        return Math.round(value / step) * step;
    }
}

