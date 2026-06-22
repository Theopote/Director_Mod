package com.director.timeline.model;

/**
 * 时间线可选择对象接口
 * 
 * <p>定义可以在时间线上选择的对象的通用接口。
 * 支持不同类型的对象（片段、摄像机关键帧、天气关键帧等）统一管理。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>统一的选择系统：所有时间线对象都可以被选择和编辑</li>
 *   <li>类型安全：支持不同类型的对象有不同的属性</li>
 *   <li>扩展性：未来可以添加摄像机、天气等新的对象类型</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public interface TimelineSelectable {
    
    /**
     * 获取对象的唯一标识符
     * 
     * @return UUID
     */
    java.util.UUID getId();
    
    /**
     * 获取对象的显示名称
     * 
     * @return 名称
     */
    String getName();
    
    /**
     * 获取对象在时间线上的开始时间
     * 
     * @return 开始时间（秒）
     */
    float getStartTime();
    
    /**
     * 获取对象在时间线上的结束时间
     * 
     * @return 结束时间（秒）
     */
    float getEndTime();
    
    /**
     * 获取对象的类型标识
     * 
     * @return 类型字符串（如 "Clip", "CameraKeyframe", "WeatherKeyframe" 等）
     */
    String getType();
}

