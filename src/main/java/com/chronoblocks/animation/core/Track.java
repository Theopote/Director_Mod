package com.chronoblocks.animation.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 📈 Track<T> - 描述容器某个属性的动画轨道。
 *
 * @param <T> 关键帧值类型
 */
public abstract class Track<T> {

    protected final String propertyPath;
    protected final List<Keyframe<T>> keyframes = new ArrayList<>();

    protected Track(String propertyPath) {
        this.propertyPath = propertyPath;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    public List<Keyframe<T>> getKeyframes() {
        return keyframes;
    }

    public abstract T evaluate(double timeSec);
}