package com.chronoblocks.ui.timeline.model.camera;

/**
 * ViewportMath - 视口计算工具类
 * 
 * <p>用于计算摄像机导出画幅在屏幕上的显示区域（Fit/Fill 策略）。
 * 支持将目标画幅比例适配到给定的屏幕区域内。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ViewportMath {
    
    private ViewportMath() {
        throw new AssertionError("Cannot instantiate ViewportMath");
    }
    
    /**
     * 视口矩形（包含外部和内部区域）
     */
    public static class ViewportRects {
        /** 外部区域（整个屏幕） */
        public final float outerX, outerY, outerW, outerH;
        
        /** 内部区域（适配后的导出画幅） */
        public final float innerX, innerY, innerW, innerH;
        
        public ViewportRects(float outerX, float outerY, float outerW, float outerH,
                            float innerX, float innerY, float innerW, float innerH) {
            this.outerX = outerX;
            this.outerY = outerY;
            this.outerW = outerW;
            this.outerH = outerH;
            this.innerX = innerX;
            this.innerY = innerY;
            this.innerW = innerW;
            this.innerH = innerH;
        }
        
        /** 内部区域右边界 */
        public float innerX2() {
            return innerX + innerW;
        }
        
        /** 内部区域下边界 */
        public float innerY2() {
            return innerY + innerH;
        }
    }
    
    /**
     * 计算 Fit 策略（保持画幅完整，可能留黑边）
     * 
     * <p>将目标画幅比例适配到给定区域内，保持完整画面，在需要时添加黑边。
     * 
     * @param outerX 外部区域 X
     * @param outerY 外部区域 Y
     * @param outerW 外部区域宽度
     * @param outerH 外部区域高度
     * @param targetAspect 目标画幅比例（宽/高）
     * @return 计算后的视口矩形
     */
    public static ViewportRects computeFit(float outerX, float outerY, float outerW, float outerH, 
                                           float targetAspect) {
        float outerAspect = outerW / outerH;
        
        float innerW, innerH;
        if (targetAspect > outerAspect) {
            // 目标更宽，以宽度为准，上下留黑边
            innerW = outerW;
            innerH = outerW / targetAspect;
        } else {
            // 目标更高，以高度为准，左右留黑边
            innerH = outerH;
            innerW = outerH * targetAspect;
        }
        
        // 居中
        float innerX = outerX + (outerW - innerW) * 0.5f;
        float innerY = outerY + (outerH - innerH) * 0.5f;
        
        return new ViewportRects(outerX, outerY, outerW, outerH,
                                 innerX, innerY, innerW, innerH);
    }
    
    /**
     * 计算 Fill 策略（填满区域，可能裁切）
     * 
     * <p>将目标画幅比例适配到给定区域内，填满整个区域，可能裁切部分画面。
     * 
     * @param outerX 外部区域 X
     * @param outerY 外部区域 Y
     * @param outerW 外部区域宽度
     * @param outerH 外部区域高度
     * @param targetAspect 目标画幅比例（宽/高）
     * @return 计算后的视口矩形
     */
    public static ViewportRects computeFill(float outerX, float outerY, float outerW, float outerH,
                                            float targetAspect) {
        float outerAspect = outerW / outerH;
        
        float innerW, innerH;
        if (targetAspect > outerAspect) {
            // 目标更宽，以高度为准，左右会被裁切
            innerH = outerH;
            innerW = outerH * targetAspect;
        } else {
            // 目标更高，以宽度为准，上下会被裁切
            innerW = outerW;
            innerH = outerW / targetAspect;
        }
        
        // 居中
        float innerX = outerX + (outerW - innerW) * 0.5f;
        float innerY = outerY + (outerH - innerH) * 0.5f;
        
        return new ViewportRects(outerX, outerY, outerW, outerH,
                                 innerX, innerY, innerW, innerH);
    }
}
