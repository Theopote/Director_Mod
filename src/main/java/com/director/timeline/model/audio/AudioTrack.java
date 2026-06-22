package com.director.timeline.model.audio;

import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineSelectable;

import java.util.*;

/**
 * AudioTrack v1.0
 *
 * <p>Director 时间线中的"音频事件轨道"。
 *
 * <p>每条音频轨道管理一组 AudioEvent（点事件）。
 * 这些事件代表：
 * <ul>
 *   <li>方块放置音效</li>
 *   <li>方块破坏音效</li>
 *   <li>摄像机切换声</li>
 *   <li>动画事件触发声</li>
 *   <li>用户手动添加的音效</li>
 * </ul>
 *
 * <p>轨道是可多条并列存在的，不限制数量。
 * 
 * <p>设计亮点：
 * <ul>
 *   <li>完全兼容 TimelineTrack - 支持新增/删除、UI显示、选中、右键菜单、拖动、Undo/Redo</li>
 *   <li>支持未来混音 - trackVolume 和 muted 未来可直接参与导演模式实时音频播放和视频导出混音</li>
 *   <li>自动排序事件 - addEvent() 后自动排序，确保 UI 和播放一致</li>
 *   <li>getAutomationKeyframes() 统一返回 AudioEvent - 完美兼容自动化轨道的渲染逻辑</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public class AudioTrack extends TimelineTrack {

    /** 本轨道所有声音事件 */
    private final List<AudioEvent> events = new ArrayList<>();

    /** 是否静音本轨道 */
    private boolean muted = false;

    /** 轨道音量（0.0-1.0）未来用于混音系统 */
    private float trackVolume = 1.0f;

    /**
     * 构造函数
     * 
     * @param name 轨道名称
     */
    public AudioTrack(String name) {
        super(name, TrackType.AUDIO);
    }

    // =====================================================================
    // Event Management
    // =====================================================================

    /**
     * 添加事件到轨道
     * 
     * @param event 音频事件
     */
    public void addEvent(AudioEvent event) {
        if (event != null && !events.contains(event)) {
            events.add(event);
            sortEvents();
        }
    }

    /**
     * 移除事件（通过ID）
     * 
     * @param id 事件ID
     */
    public void removeEvent(UUID id) {
        events.removeIf(e -> e != null && e.getId().equals(id));
    }

    /**
     * 移除事件（通过对象）
     * 
     * @param event 音频事件
     */
    public void removeEvent(AudioEvent event) {
        events.remove(event);
    }

    /**
     * 获取所有事件
     * 
     * @return 不可修改的事件列表
     */
    public List<AudioEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * 根据ID获取事件
     * 
     * @param id 事件ID
     * @return 音频事件，如果不存在则返回 null
     */
    public AudioEvent getEventById(UUID id) {
        if (id == null) {
            return null;
        }
        for (AudioEvent e : events) {
            if (e != null && e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 查找指定时间范围内的事件
     * 
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     * @return 时间范围内的事件列表
     */
    public List<AudioEvent> getEventsInRange(float startTime, float endTime) {
        List<AudioEvent> result = new ArrayList<>();
        for (AudioEvent event : events) {
            if (event == null) {
                continue;
            }
            float eventTime = event.getTime();
            if (eventTime >= startTime && eventTime <= endTime) {
                result.add(event);
            }
        }
        return result;
    }

    /**
     * 清空所有事件
     */
    public void clearEvents() {
        events.clear();
    }

    /**
     * 检查是否有事件
     * 
     * @return 是否有事件
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * 按时间排序事件
     * 
     * <p>在添加事件后自动调用，确保 UI 和播放时事件按时间顺序排列。
     */
    public void sortEvents() {
        events.sort(Comparator.comparingDouble(AudioEvent::getStartTime));
    }

    /**
     * 重写 getAutomationKeyframes() 以返回 AudioEvent
     * 
     * <p>这使得 AudioEvent 可以被当作自动化关键帧处理，
     * 完美兼容自动化轨道的渲染逻辑。
     */
    @Override
    public List<TimelineSelectable> getAutomationKeyframes() {
        // AudioEvent 实现了 TimelineSelectable，需要显式转换为 List<TimelineSelectable>
        return new ArrayList<>(events);
    }

    /**
     * 让 TimelineHitTest / 交互系统能正确识别音频事件“存在自动化关键帧”。
     * 否则 hitTestAutomationKeyframes 会因为父类的 automationKeyframes 为空而无法命中音频点。
     */
    @Override
    public boolean hasAutomationKeyframes() {
        return !events.isEmpty();
    }

    /**
     * 兼容 Timeline 命令系统：把 AudioEvent 当作轨道级 keyframe 进行 add/remove/move。
     * 这样 RemoveAutomationKeyframeCommand / MoveAutomationKeyframeCommand 才能对音频事件生效。
     */
    @Override
    public void addAutomationKeyframe(TimelineSelectable keyframe) {
        if (keyframe instanceof AudioEvent ae) {
            addEvent(ae);
            return;
        }
        super.addAutomationKeyframe(keyframe);
    }

    @Override
    public boolean removeAutomationKeyframe(UUID keyframeId) {
        if (keyframeId == null) {
            return false;
        }
        AudioEvent ev = getEventById(keyframeId);
        if (ev != null) {
            removeEvent(ev);
            return true;
        }
        return super.removeAutomationKeyframe(keyframeId);
    }

    // =====================================================================
    // Track-level controls
    // =====================================================================

    /**
     * 检查轨道是否静音
     * 
     * @return 是否静音
     */
    @Override
    public boolean isMuted() {
        return muted;
    }

    /**
     * 设置轨道静音状态
     * 
     * @param muted 是否静音
     */
    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * 获取轨道音量
     * 
     * @return 轨道音量（0.0-1.0）
     */
    public float getTrackVolume() {
        return trackVolume;
    }

    /**
     * 设置轨道音量
     * 
     * @param v 轨道音量（0.0-1.0），会自动限制在有效范围内
     */
    public void setTrackVolume(float v) {
        this.trackVolume = Math.max(0f, Math.min(1f, v));
    }

    // =====================================================================
    // Utilities
    // =====================================================================

    @Override
    public String toString() {
        return "AudioTrack{" +
                "name='" + getName() + '\'' +
                ", events=" + events.size() +
                ", muted=" + muted +
                ", volume=" + trackVolume +
                '}';
    }
}
