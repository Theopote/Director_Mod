package com.chronoblocks.testing;

import com.chronoblocks.ui.timeline.TimelineModel;
import com.chronoblocks.ui.timeline.TimelineTrack;
import com.chronoblocks.ui.timeline.TimelineClip;
import com.chronoblocks.ui.timeline.TrackType;

/**
 * 演示时间线数据工厂
 * 
 * 职责：创建用于演示和测试的时间线数据
 * 这个类将测试数据的创建逻辑与生产代码分离
 * 
 * @author ChronoBlocks Team
 * @version 7.4
 */
public class DemoTimelineFactory {
    
    /**
     * 用演示数据填充 TimelineModel
     * @param model 要填充的时间线模型
     */
    public static void populateWithDemoData(TimelineModel model) {
        model.getTracks().clear(); // 清空现有轨道
        
        // 容器轨道（方块动画） - 扩展到20秒
        TimelineTrack containerTrack = new TimelineTrack("建筑物出现", TrackType.CONTAINER);
        TimelineClip containerClip1 = new TimelineClip("建筑A", 0.0f, 5.0f, TrackType.CONTAINER.getClipColor());
        TimelineClip containerClip2 = new TimelineClip("建筑B", 6.0f, 12.0f, TrackType.CONTAINER.getClipColor());
        TimelineClip containerClip3 = new TimelineClip("建筑C", 13.0f, 18.0f, TrackType.CONTAINER.getClipColor());
        containerClip1.addKeyframe(0.5f);
        containerClip1.addKeyframe(2.5f);
        containerTrack.addClip(containerClip1);
        containerTrack.addClip(containerClip2);
        containerTrack.addClip(containerClip3);
        
        // 摄像机轨道 - 扩展到20秒
        TimelineTrack cameraTrack = new TimelineTrack("主摄像机", TrackType.CAMERA);
        TimelineClip cameraClip = new TimelineClip("环绕飞行", 0.0f, 20.0f, TrackType.CAMERA.getClipColor());
        cameraClip.addKeyframe(0.0f);
        cameraClip.addKeyframe(5.0f);
        cameraClip.addKeyframe(10.0f);
        cameraClip.addKeyframe(15.0f);
        cameraClip.addKeyframe(20.0f);
        cameraTrack.addClip(cameraClip);
        
        // 天气轨道 - 扩展到20秒
        TimelineTrack weatherTrack = new TimelineTrack("时间与天气", TrackType.WEATHER);
        TimelineClip weatherClip1 = new TimelineClip("黎明", 0.0f, 5.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip2 = new TimelineClip("正午", 5.0f, 12.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip3 = new TimelineClip("黄昏", 12.0f, 17.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip4 = new TimelineClip("夜晚", 17.0f, 20.0f, TrackType.WEATHER.getClipColor());
        weatherTrack.addClip(weatherClip1);
        weatherTrack.addClip(weatherClip2);
        weatherTrack.addClip(weatherClip3);
        weatherTrack.addClip(weatherClip4);
        
        // 特效轨道 - 扩展到20秒
        TimelineTrack effectTrack = new TimelineTrack("粒子特效", TrackType.EFFECT);
        TimelineClip effectClip1 = new TimelineClip("烟雾", 2.0f, 8.0f, TrackType.EFFECT.getClipColor());
        TimelineClip effectClip2 = new TimelineClip("火焰", 10.0f, 16.0f, TrackType.EFFECT.getClipColor());
        effectTrack.addClip(effectClip1);
        effectTrack.addClip(effectClip2);
        
        model.addTrack(containerTrack);
        model.addTrack(cameraTrack);
        model.addTrack(weatherTrack);
        model.addTrack(effectTrack);
    }
}

