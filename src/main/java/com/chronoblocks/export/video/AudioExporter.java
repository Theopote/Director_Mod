package com.chronoblocks.export.video;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 音频导出接口
 *
 * <p>根据时间范围导出一条“可供 FFmpeg 混流”的 WAV 文件。</p>
 */
public interface AudioExporter {

    /**
     * 导出指定时间范围内的音频到 WAV 文件。
     *
     * @param settings 导出设置（包含采样率/声道等）
     * @param startTimeSec 起始时间（秒）
     * @param endTimeSec 结束时间（秒）
     * @return WAV 文件路径（调用方可选择在任务结束后删除）
     */
    Path exportWav(VideoExportSettings settings, float startTimeSec, float endTimeSec) throws IOException;
}


