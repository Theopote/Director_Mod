package com.chronoblocks.core.animation.transform;

import java.util.Map;
import java.util.Random;

/**
 * 动画变换器接口
 * 
 * <p>定义方块在出现时的变换效果（位移、旋转、缩放、透明度等）。
 * 每个动画模式对应一个实现类。
 * 
 * <p>设计理念：
 * <ul>
 *   <li>输入：归一化进度 (0.0 - 1.0)，经过 Easing 处理</li>
 *   <li>输出：变换矩阵参数（位移、旋转、缩放）和渲染参数（透明度、颜色）</li>
 *   <li>支持随机微调：每个方块可以有略微不同的效果</li>
 * </ul>
 * 
 * <p>注意：此接口不直接操作 MatrixStack，而是返回变换参数，
 * 由调用方应用到渲染管线中。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface IAnimationTransformer {
    
    /**
     * 变换结果
     * 包含所有变换参数
     */
    class TransformResult {
        /** 位移偏移 (X, Y, Z) */
        public float translateX = 0.0f;
        public float translateY = 0.0f;
        public float translateZ = 0.0f;
        
        /** 旋转角度 (度) */
        public float rotateX = 0.0f;
        public float rotateY = 0.0f;
        public float rotateZ = 0.0f;
        
        /** 缩放比例 (X, Y, Z) */
        public float scaleX = 1.0f;
        public float scaleY = 1.0f;
        public float scaleZ = 1.0f;
        
        /** 透明度 (0.0 - 1.0) */
        public float opacity = 1.0f;
        
        /** 颜色色调 (RGB, 0.0 - 1.0) */
        public float colorR = 1.0f;
        public float colorG = 1.0f;
        public float colorB = 1.0f;
        
        /**
         * 重置为默认值
         */
        public void reset() {
            translateX = translateY = translateZ = 0.0f;
            rotateX = rotateY = rotateZ = 0.0f;
            scaleX = scaleY = scaleZ = 1.0f;
            opacity = 1.0f;
            colorR = colorG = colorB = 1.0f;
        }
    }
    
    /**
     * 计算变换结果
     * 
     * @param progress 归一化进度 (0.0 - 1.0)，已经过 Easing 处理
     * @param params 参数配置 Map
     * @param random 该方块的随机数生成器（用于产生不同的效果）
     * @return 变换结果
     */
    TransformResult transform(float progress, Map<String, Object> params, Random random);
    
    /**
     * 获取显示名称
     */
    String getDisplayName();
    
    /**
     * 获取描述
     */
    String getDescription();
    
    /**
     * 获取默认参数
     * 
     * @return 默认参数 Map
     */
    Map<String, Object> getDefaultParams();
    
    /**
     * 获取推荐的默认缓动函数
     * 
     * @return 推荐的缓动函数名称
     */
    String getRecommendedEasing();
    
    /**
     * 渲染参数UI（可选实现）
     * 
     * <p>如果 Transformer 支持 UI 渲染，实现此方法来自定义参数编辑界面。
     * 如果不支持（返回 false），UI 适配器将使用默认的参数编辑方式。
     * 
     * <p>注意：由于架构限制（core 包不应依赖 UI 包），此方法使用反射来访问 UI 类。
     * 如果 UI 类不可用，实现应返回 false，UI 适配器会使用默认方式渲染。
     * 
     * @param params 参数配置 Map（可修改）
     * @return 是否修改了参数（用于标记是否需要保存）
     */
    default boolean renderUI(java.util.Map<String, Object> params) {
        // 默认实现：不渲染任何UI
        return false;
    }
    
    /**
     * 应用变换到参数 Map（新 API，用于 BlockAnimationEngine.sample()）
     * 
     * <p>此方法将变换结果写入 params Map，而不是返回 TransformResult。
     * 这是为了支持新的采样架构。
     * 
     * <p>默认实现：调用 transform() 并将结果转换为 params。
     * 子类可以重写此方法以直接写入 params（更高效）。
     * 
     * <p>写入的键名约定：
     * <ul>
     *   <li>dx, dy, dz: 平移偏移（float）</li>
     *   <li>scale: 缩放比例（float，默认 1.0）</li>
     *   <li>yaw, pitch, roll: 旋转角度（float，度）</li>
     *   <li>alpha: 透明度（float [0..1]，默认 1.0）</li>
     * </ul>
     * 
     * @param easedT 缓动后的进度 [0..1]
     * @param params 参数配置 Map（将在此 Map 中写入变换参数）
     * @param random 每个方块独立随机源
     */
    default void apply(float easedT, Map<String, Object> params, Random random) {
        // 默认实现：调用 transform() 并将结果转换为 params
        TransformResult result = transform(easedT, params, random);
        
        if (result != null) {
            // 平移
            params.put("dx", result.translateX);
            params.put("dy", result.translateY);
            params.put("dz", result.translateZ);
            
            // 缩放（统一缩放）
            float avgScale = (result.scaleX + result.scaleY + result.scaleZ) / 3.0f;
            params.put("scale", avgScale);
            
            // 旋转
            params.put("yaw", result.rotateY);
            params.put("pitch", result.rotateX);
            params.put("roll", result.rotateZ);
            
            // 透明度
            params.put("alpha", result.opacity);
        }
    }
}

