package com.chronoblocks.core.container;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * 容器方块过滤器（v5.2简化版）
 * 
 * <p>v5.2: 由于隐藏的方块现在是AIR，射线检测会自动跳过，此类的功能已简化
 * 
 * @author ChronoBlocks Team
 * @version 5.2
 */
public final class ContainerBlockFilter {
    
    private ContainerBlockFilter() {}
    
    /**
     * 过滤射线检测结果（v5.2简化版）
     * 
     * <p>由于隐藏的方块现在是AIR，射线检测会自动跳过，此方法主要用于兼容性
     * 
     * @param originalResult 原始射线检测结果
     * @param world 世界
     * @return 过滤后的结果（通常与原始结果相同）
     */
    public static BlockHitResult filterHiddenHit(BlockHitResult originalResult, World world) {
        // v5.2: 隐藏的方块已经是AIR，射线检测会自动跳过，不需要特殊处理
        return originalResult;
    }
    
    /**
     * 包装标准的射线检测调用（v5.2简化版）
     * 
     * @param context 射线检测上下文
     * @return 射线检测结果
     */
    public static BlockHitResult raycastWithFilter(RaycastContext context) {
        if (context == null) {
            return null;
        }
        
        World world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        
        // v5.2: 直接执行射线检测，隐藏的方块（AIR）会自动被跳过
        return world.raycast(context);
    }
    
    /**
     * 检查方块位置是否应该被过滤（v5.2简化版）
     * 
     * <p>v5.2: 由于隐藏的方块现在是AIR，此方法总是返回false
     * 
     * @param pos 方块位置
     * @return 是否应该被过滤（总是返回false）
     */
    public static boolean shouldFilter(BlockPos pos) {
        // v5.2: 隐藏的方块已经是AIR，不需要过滤
        return false;
    }
}

