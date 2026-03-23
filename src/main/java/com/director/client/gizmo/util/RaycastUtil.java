package com.director.client.gizmo.util;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 🧭 RaycastUtil - 射线检测工具类
 * 
 * @author Director Team
 * @version 1.0
 */
public class RaycastUtil {
    
    /**
     * 计算射线与包围盒的交点
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param box 包围盒
     * @return 距离（如果命中），否则返回 Double.POSITIVE_INFINITY
     */
    public static double rayToBoxDistance(Vec3d rayOrigin, Vec3d rayDir, Box box) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        
        // X 轴
        if (Math.abs(rayDir.x) < 1e-6) {
            if (rayOrigin.x < box.minX || rayOrigin.x > box.maxX) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invD = 1.0 / rayDir.x;
            double t1 = (box.minX - rayOrigin.x) * invD;
            double t2 = (box.maxX - rayOrigin.x) * invD;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        
        // Y 轴
        if (Math.abs(rayDir.y) < 1e-6) {
            if (rayOrigin.y < box.minY || rayOrigin.y > box.maxY) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invD = 1.0 / rayDir.y;
            double t1 = (box.minY - rayOrigin.y) * invD;
            double t2 = (box.maxY - rayOrigin.y) * invD;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        
        // Z 轴
        if (Math.abs(rayDir.z) < 1e-6) {
            if (rayOrigin.z < box.minZ || rayOrigin.z > box.maxZ) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invD = 1.0 / rayDir.z;
            double t1 = (box.minZ - rayOrigin.z) * invD;
            double t2 = (box.maxZ - rayOrigin.z) * invD;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        
        if (tMin > tMax || tMax < 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        double t = tMin >= 0 ? tMin : tMax;
        return t >= 0 ? t : Double.POSITIVE_INFINITY;
    }
    
    /**
     * 计算射线与球体的交点距离
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param center 球心
     * @param radius 半径
     * @return 距离（如果命中），否则返回 Double.POSITIVE_INFINITY
     */
    public static double rayToSphereDistance(Vec3d rayOrigin, Vec3d rayDir, Vec3d center, double radius) {
        Vec3d oc = rayOrigin.subtract(center);
        double b = oc.dotProduct(rayDir);
        double c = oc.dotProduct(oc) - radius * radius;
        double disc = b * b - c;
        
        if (disc < 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        double t = -b - Math.sqrt(disc);
        if (t < 0) {
            t = -b + Math.sqrt(disc);
        }
        
        return (t >= 0) ? t : Double.POSITIVE_INFINITY;
    }
    
    /**
     * 计算射线与平面的交点
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param planePoint 平面上一点
     * @param planeNormal 平面法线（归一化）
     * @return 交点（如果命中），否则返回 null
     */
    public static Vec3d rayToPlaneIntersection(Vec3d rayOrigin, Vec3d rayDir, Vec3d planePoint, Vec3d planeNormal) {
        double denom = rayDir.dotProduct(planeNormal);
        if (Math.abs(denom) < 1e-6) {
            return null; // 射线与平面平行
        }
        
        Vec3d toPlane = planePoint.subtract(rayOrigin);
        double t = toPlane.dotProduct(planeNormal) / denom;
        
        if (t < 0) {
            return null; // 交点在射线后方
        }
        
        return rayOrigin.add(rayDir.multiply(t));
    }
}

