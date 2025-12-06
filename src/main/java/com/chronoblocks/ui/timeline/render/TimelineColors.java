package com.chronoblocks.ui.timeline.render;

/**
 * 时间线渲染颜色常量（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>定义所有时间线相关的颜色常量</li>
 *   <li>提供颜色工具方法（rgba, lighten, adjustAlpha）</li>
 *   <li>集中管理颜色，便于主题切换</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public final class TimelineColors {

    // ========== 颜色常量 ==========
    
    // 关键帧颜色
    public static final int COLOR_KEYFRAME = rgba(255, 220, 64, 255);
    public static final int COLOR_KEYFRAME_HOVER = rgba(255, 240, 100, 255);
    /** 选中关键帧的颜色（高亮） */
    public static final int COLOR_KEYFRAME_SELECTED = rgba(100, 200, 255, 255);
    /** 选中关键帧的外圈颜色 */
    public static final int COLOR_KEYFRAME_SELECTED_RING = rgba(100, 200, 255, 200);
    
    // 播放头颜色
    public static final int COLOR_PLAYHEAD = rgba(255, 80, 80, 220);
    /** 播放头投影颜色（深灰色，与三角形手柄投影一致） */
    public static final int COLOR_PLAYHEAD_SHADOW = rgba(0, 0, 0, 100);
    
    // 选择边框颜色
    public static final int COLOR_SELECTED_BORDER = rgba(255, 255, 80, 255);
    
    // 背景颜色
    public static final int COLOR_BG_DARK = rgba(30, 30, 30, 220);
    public static final int COLOR_BG_MEDIUM = rgba(45, 45, 45, 200);
    public static final int COLOR_RULER_BG = rgba(40, 40, 40, 220);
    
    // 轨道标签背景颜色
    public static final int COLOR_TRACK_LABEL_BG = rgba(24, 24, 24, 220);
    /** 轨道名称区域交替行背景色（偶数行），用于斑马纹效果 (Zebra Striping) */
    public static final int COLOR_TRACK_LABEL_BG_ALT = rgba(32, 32, 32, 220);
    
    // 分割线和控件颜色
    /** 轨道分割线颜色（深色缝隙，全贯通） */
    public static final int COLOR_SEPARATOR_LINE = rgba(0, 0, 0, 150);
    public static final int COLOR_RESIZE_HANDLE = rgba(180, 180, 180, 150);
    public static final int COLOR_DROP_HIGHLIGHT = rgba(100, 200, 255, 80);
    
    // 文本颜色
    public static final int COLOR_TEXT_LIGHT = rgba(220, 220, 220, 255);
    public static final int COLOR_TEXT_MEDIUM = rgba(200, 200, 200, 255);
    public static final int COLOR_TEXT_DARK = rgba(10, 10, 10, 255);

    // ========== 颜色工具方法 ==========

    /**
     * 创建 ARGB 颜色值（零 GC 优化）
     * 
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @param a Alpha 分量 (0-255)
     * @return ARGB 格式的颜色值
     */
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }

    /**
     * 提亮颜色（零 GC 优化版本）
     * 
     * <p>性能优化：使用纯位运算，完全避免创建 java.awt.Color 对象。
     * 在渲染循环中，每帧可能有数百个片段需要调用此方法，移除对象分配可以
     * 显著减少 GC 压力，避免周期性微卡顿。
     * 
     * @param col 原始颜色（ARGB 格式）
     * @return 提亮后的颜色（亮度增加 30%）
     */
    public static int lighten(int col) {
        int a = (col >> 24) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;

        // 直接位运算，无需创建 Color 对象
        // 亮度增加 30%：乘以 1.3
        int newR = Math.min(255, (int)(r * 1.3f));
        int newG = Math.min(255, (int)(g * 1.3f));
        int newB = Math.min(255, (int)(b * 1.3f));

        return ((a & 0xFF) << 24) | ((newB & 0xFF) << 16) | ((newG & 0xFF) << 8) | ((newR & 0xFF));
    }

    /**
     * 调整颜色的 Alpha 通道（参数化版本）
     * 
     * @param col 原始颜色（ARGB 格式）
     * @param alpha 新的 Alpha 值 (0-255)
     * @return 调整 Alpha 后的颜色
     */
    public static int adjustAlpha(int col, int alpha) {
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        // 限制 alpha 范围
        alpha = Math.max(0, Math.min(255, alpha));
        return ((alpha & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }
    
    /**
     * 调整颜色的 Alpha 通道（使用默认值 100）
     * 
     * <p>向后兼容方法，调用参数化版本
     * 
     * @param col 原始颜色（ARGB 格式）
     * @return 调整 Alpha 后的颜色（Alpha = 100）
     */
    public static int adjustAlpha(int col) {
        return adjustAlpha(col, 100);
    }
    
    /**
     * 将颜色变灰（用于锁定状态）
     * 
     * <p>将颜色转换为灰度，并降低亮度，用于表示锁定状态。
     * 使用零 GC 优化的位运算实现。
     * 
     * @param col 原始颜色（ARGB 格式）
     * @return 灰度化后的颜色（亮度降低 50%，饱和度降低 80%）
     */
    public static int grayscale(int col) {
        int a = (col >> 24) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        
        // 转换为灰度：使用标准权重
        // 灰度值 = 0.299*R + 0.587*G + 0.114*B
        int gray = (int)(r * 0.299f + g * 0.587f + b * 0.114f);
        
        // 降低亮度 50%
        gray = (int)(gray * 0.5f);
        
        // 限制范围
        gray = Math.max(0, Math.min(255, gray));
        
        // 返回灰度颜色（RGB 都使用相同的灰度值）
        return ((a & 0xFF) << 24) | ((gray & 0xFF) << 16) | ((gray & 0xFF) << 8) | ((gray & 0xFF));
    }

    // 防止实例化
    private TimelineColors() {
        throw new UnsupportedOperationException("Utility class");
    }
}

