package com.chronoblocks.export.video;

import net.fabricmc.loader.api.FabricLoader;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioTrack;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间线音频导出器（WAV）
 *
 * <p>当前版本导出“事件提示音 WAV”，用于打通完整链路：AudioEvent -> WAV -> FFmpeg 混流 -> MP4。</p>
 *
 * <p>说明：AudioTrackSampler 是“实时触发 Minecraft 声音引擎”的事件播放器，并不提供 PCM。
 * 因此这里采用离线合成的方式：把每个 AudioEvent 合成为一个短哔声（受 volume/pitch/trackVolume 影响），并混音成 WAV。</p>
 */
public final class TimelineAudioExporter implements AudioExporter {

    @Override
    public Path exportWav(VideoExportSettings settings, float startTimeSec, float endTimeSec) throws IOException {
        float duration = Math.max(0.001f, endTimeSec - startTimeSec);

        int sampleRate = settings.audioSampleRate;
        int channels = settings.audioChannels;
        int bitsPerSample = 16;

        long totalSamples = (long) Math.ceil(duration * sampleRate);
        long dataSize = totalSamples * channels * (bitsPerSample / 8);

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("chronoblocks").resolve("cache");
        Files.createDirectories(cacheDir);

        Path outFile = cacheDir.resolve("timeline_audio.wav");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            writeWavHeader(out, sampleRate, channels, bitsPerSample, dataSize);
            writePcm16leFromEvents(out, settings, sampleRate, channels, totalSamples, startTimeSec, endTimeSec);
        }

        return outFile;
    }

    private void writeWavHeader(DataOutputStream out,
                                int sampleRate,
                                int channels,
                                int bitsPerSample,
                                long dataSize) throws IOException {
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        long chunkSize = 36 + dataSize;

        // RIFF
        out.writeBytes("RIFF");
        out.writeInt(Integer.reverseBytes((int) chunkSize));
        out.writeBytes("WAVE");

        // fmt
        out.writeBytes("fmt ");
        out.writeInt(Integer.reverseBytes(16)); // PCM
        out.writeShort(Short.reverseBytes((short) 1)); // AudioFormat=1 PCM
        out.writeShort(Short.reverseBytes((short) channels));
        out.writeInt(Integer.reverseBytes(sampleRate));
        out.writeInt(Integer.reverseBytes((int) byteRate));
        out.writeShort(Short.reverseBytes((short) blockAlign));
        out.writeShort(Short.reverseBytes((short) bitsPerSample));

        // data
        out.writeBytes("data");
        out.writeInt(Integer.reverseBytes((int) dataSize));
    }

    /**
     * 写 PCM 16-bit little-endian。
     *
     * <p>当前实现：基于 AudioEvent 的“短哔声”占位混音。</p>
     */
    private void writePcm16leFromEvents(DataOutputStream out,
                                        VideoExportSettings settings,
                                        int sampleRate,
                                        int channels,
                                        long totalSamples,
                                        float startTimeSec,
                                        float endTimeSec) throws IOException {

        // 收集音频事件（来自时间线所有 AUDIO 轨道）
        List<EventClip> clips = collectEventClips(startTimeSec, endTimeSec);

        // 生成浮点混音缓冲（分声道）
        float[] left = new float[(int) totalSamples];
        float[] right = (channels >= 2) ? new float[(int) totalSamples] : null;

        // 每个事件合成一个短哔声（80ms），pitch 映射到频率
        int beepLen = Math.max(1, (int) (0.08f * sampleRate));
        int attackLen = Math.max(1, (int) (0.008f * sampleRate));

        for (EventClip ev : clips) {
            if (!ev.enabled) {
                continue;
            }
            // 事件时间 -> 样本索引
            float tRel = ev.timeSec - startTimeSec;
            if (tRel < 0f) {
                continue;
            }
            int startIdx = (int) Math.floor(tRel * sampleRate);
            if (startIdx >= totalSamples) {
                continue;
            }

            // 音量：event.volume * trackVolume，并限制
            float gain = clamp01(ev.volume * ev.trackVolume);

            // pitch：默认 1.0 -> 440Hz；范围 0.1~3.0
            float pitch = Math.max(0.1f, Math.min(3.0f, ev.pitch));
            float freq = 440.0f * pitch;

            int endIdx = Math.min((int) totalSamples, startIdx + beepLen);
            for (int i = startIdx; i < endIdx; i++) {
                int n = i - startIdx;
                // 简单包络：attack + 指数衰减
                float env;
                if (n < attackLen) {
                    env = n / (float) attackLen;
                } else {
                    float x = (n - attackLen) / (float) Math.max(1, (beepLen - attackLen));
                    env = (float) Math.exp(-6.0f * x);
                }
                float s = (float) Math.sin(2.0 * Math.PI * freq * (n / (double) sampleRate));
                float v = s * env * gain;

                left[i] += v;
                if (right != null) {
                    right[i] += v;
                }
            }
        }

        // 写出 PCM（clamp + 16-bit）
        for (int i = 0; i < totalSamples; i++) {
            float l = clamp11(left[i]);
            writeSample16le(out, l);
            if (channels >= 2) {
                float r = clamp11(right[i]);
                writeSample16le(out, r);
            }
        }
    }

    private static void writeSample16le(DataOutputStream out, float v) throws IOException {
        short s = (short) (clamp11(v) * Short.MAX_VALUE);
        out.writeShort(Short.reverseBytes(s));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clamp11(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static final class EventClip {
        final float timeSec;
        final float volume;
        final float pitch;
        final boolean enabled;
        final float trackVolume;

        EventClip(float timeSec, float volume, float pitch, boolean enabled, float trackVolume) {
            this.timeSec = timeSec;
            this.volume = volume;
            this.pitch = pitch;
            this.enabled = enabled;
            this.trackVolume = trackVolume;
        }
    }

    private List<EventClip> collectEventClips(float startTimeSec, float endTimeSec) {
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            return List.of();
        }

        List<EventClip> result = new ArrayList<>();
        for (TimelineTrack t : model.getTracks()) {
            if (t == null || t.getType() != TrackType.AUDIO) {
                continue;
            }
            if (t.isMuted()) {
                continue;
            }

            float trackVolume = 1.0f;
            if (t instanceof AudioTrack at) {
                trackVolume = at.getTrackVolume();
                for (AudioEvent e : at.getEventsInRange(startTimeSec, endTimeSec)) {
                    if (e == null) continue;
                    result.add(new EventClip(e.getTime(), e.getVolume(), e.getPitch(), e.isEnabled(), trackVolume));
                }
            } else {
                // 兼容：如果未来不是 AudioTrack 子类，但 automationKeyframes 里放了 AudioEvent
                for (TimelineSelectable sel : t.getAutomationKeyframes()) {
                    if (sel instanceof AudioEvent e) {
                        float time = e.getTime();
                        if (time >= startTimeSec && time <= endTimeSec) {
                            result.add(new EventClip(time, e.getVolume(), e.getPitch(), e.isEnabled(), trackVolume));
                        }
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(ev -> ev.timeSec));
        return result;
    }
}


