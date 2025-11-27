package com.chronoblocks.ui.util;

import com.chronoblocks.core.animation.effect.EffectInstance;

/**
 * 特效剪贴板管理器
 * 
 * <p>用于在 UI 中复制/粘贴特效实例
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class EffectClipboardManager {
    
    private static EffectInstance copiedEffect = null;
    
    private EffectClipboardManager() {}
    
    /**
     * 设置复制的特效实例
     * 
     * @param effect 要复制的特效实例
     */
    public static void setCopiedEffect(EffectInstance effect) {
        copiedEffect = effect != null ? effect.copy() : null;
    }
    
    /**
     * 获取复制的特效实例（不删除）
     * 
     * @return 复制的特效实例，如果没有则返回 null
     */
    public static EffectInstance getCopiedEffect() {
        return copiedEffect != null ? copiedEffect.copy() : null;
    }
    
    /**
     * 检查是否有复制的特效
     * 
     * @return 如果有复制的特效返回 true
     */
    public static boolean hasCopiedEffect() {
        return copiedEffect != null;
    }
    
    /**
     * 清空剪贴板
     */
    public static void clear() {
        copiedEffect = null;
    }
}

