package com.chronoblocks.ui.aware;

import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 播放模型提供者 - 能提供 TimelineModel 以便绑定 PlaybackOrchestrator 的面板实现此接口。
 * <p>
 * UIPanelManager 通过 {@code if (panel instanceof IPlaybackModelProvider)} 获取 model 并绑定，
 * 不引用具体 TimelinePanel，守住“不懂语义”的红线。
 *
 * @author ChronoBlocks Team
 */
public interface IPlaybackModelProvider {

    /**
     * @return 可为 null（未就绪时）
     */
    TimelineModel getModel();
}
