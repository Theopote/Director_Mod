package com.director.export.video;

import com.director.core.DirectorServices;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间线音频导出器（WAV）
 *
 * <p>当前版本导出 Minecraft 原版音效（基于 soundId -> sounds.json -> ogg 解码）。</p>
 *
 * <p>说明：AudioTrackSampler 是实时触发声音引擎的事件播放器，并不提供 PCM。
 * 因此这里采用离线解码 + 混音的方式：将每个 AudioEvent 对应的 OGG 解码成 PCM，再混音成 WAV。</p>
 */
public final class TimelineAudioExporter implements AudioExporter {

    private static final int MAX_SOUND_EVENT_DEPTH = 6;

    @Override
    public Path exportWav(VideoExportSettings settings, float startTimeSec, float endTimeSec) throws IOException {
        float duration = Math.max(0.001f, endTimeSec - startTimeSec);

        int sampleRate = settings.audioSampleRate;
        int channels = settings.audioChannels;
        int bitsPerSample = 16;

        long totalSamples = (long) Math.ceil(duration * sampleRate);
        long dataSize = totalSamples * channels * (bitsPerSample / 8);

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("director").resolve("cache");
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
     * <p>当前实现：基于 AudioEvent 的真实 OGG 音效混音。</p>
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

        // 生成浮点混音缓冲（交错声道）
        float[] mix = new float[(int) totalSamples * channels];
        SoundResolver resolver = new SoundResolver();

        for (EventClip ev : clips) {
            if (!ev.enabled) {
                continue;
            }
            if (ev.soundId == null || ev.soundId.isBlank()) {
                continue;
            }

            float tRel = ev.timeSec - startTimeSec;
            if (tRel < 0f) {
                continue;
            }

            ResolvedSound resolved = resolver.resolveAndDecode(ev.soundId, ev.timeSec);
            if (resolved == null || resolved.decoded == null || resolved.decoded.samples.length == 0) {
                continue;
            }

            float gain = clamp01(ev.volume * ev.trackVolume * resolved.volume);
            float pitch = Math.max(0.1f, Math.min(3.0f, ev.pitch * resolved.pitch));
            mixDecodedSound(mix, channels, sampleRate, resolved.decoded, tRel, gain, pitch);
        }

        // 写出 PCM（clamp + 16-bit）
        int totalFrames = (int) totalSamples;
        for (int frame = 0; frame < totalFrames; frame++) {
            int base = frame * channels;
            for (int ch = 0; ch < channels; ch++) {
                float v = clamp11(mix[base + ch]);
                writeSample16le(out, v);
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
        final String soundId;

        EventClip(float timeSec, float volume, float pitch, boolean enabled, float trackVolume, String soundId) {
            this.timeSec = timeSec;
            this.volume = volume;
            this.pitch = pitch;
            this.enabled = enabled;
            this.trackVolume = trackVolume;
            this.soundId = soundId;
        }
    }

    private List<EventClip> collectEventClips(float startTimeSec, float endTimeSec) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return List.of();
        }

        List<EventClip> result = new ArrayList<>();
        for (TimelineTrack t : model.getTracks()) {
            if (t == null || t.getType() != TrackType.AUDIO) {
                continue;
            }
            if (!com.director.timeline.audio.TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), t)) {
                continue;
            }

            float trackVolume = 1.0f;
            if (t instanceof AudioTrack at) {
                trackVolume = at.getTrackVolume();
                for (AudioEvent e : at.getEventsInRange(startTimeSec, endTimeSec)) {
                    if (e == null) continue;
                    result.add(new EventClip(e.getTime(), e.getVolume() * com.director.ui.timeline.audio.TimelineAudioService.getInstance().getVolumeMultiplierForEvent(e), e.getPitch(), e.isEnabled(), trackVolume, e.getSoundId()));
                }
            } else {
                // 兼容：如果未来不是 AudioTrack 子类，但 automationKeyframes 里放了 AudioEvent
                for (TimelineSelectable sel : t.getAutomationKeyframes()) {
                    if (sel instanceof AudioEvent e) {
                        float time = e.getTime();
                        if (time >= startTimeSec && time <= endTimeSec) {
                            result.add(new EventClip(time, e.getVolume() * com.director.ui.timeline.audio.TimelineAudioService.getInstance().getVolumeMultiplierForEvent(e), e.getPitch(), e.isEnabled(), trackVolume, e.getSoundId()));
                        }
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(ev -> ev.timeSec));
        return result;
    }

    private static final class DecodedSound {
        final float[] samples;
        final int channels;
        final int sampleRate;

        DecodedSound(float[] samples, int channels, int sampleRate) {
            this.samples = samples;
            this.channels = channels;
            this.sampleRate = sampleRate;
        }
    }
    private static final class ResolvedSound {
        final DecodedSound decoded;
        final float volume;
        final float pitch;

        ResolvedSound(DecodedSound decoded, float volume, float pitch) {
            this.decoded = decoded;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
    private static final class SoundResolver {
        private final Map<Identifier, DecodedSound> cache = new HashMap<>();

        ResolvedSound resolveAndDecode(String soundId, float eventTimeSec) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }
            SoundManager soundManager = client.getSoundManager();
            ResourceManager resourceManager = client.getResourceManager();
            if (soundManager == null || resourceManager == null) {
                return null;
            }

            Identifier eventId = Identifier.tryParse(soundId);
            if (eventId == null) {
                return null;
            }

            long seed = ((long) eventId.hashCode() << 32) ^ (long) Float.floatToIntBits(eventTimeSec);
            Random random = Random.create(seed);
            Sound sound = resolveSound(soundManager, eventId, random, 0);
            if (sound == null) {
                return null;
            }

            Identifier soundFile = sound.getLocation();
            if (soundFile == null) {
                return null;
            }

            DecodedSound decoded = cache.computeIfAbsent(soundFile, id -> decodeSound(resourceManager, sound));
            if (decoded == null) {
                return null;
            }

            float volume = Math.max(0.0f, sound.getVolume().get(random));
            float pitch = Math.max(0.1f, sound.getPitch().get(random));
            return new ResolvedSound(decoded, volume, pitch);
        }

        private Sound resolveSound(SoundManager soundManager, Identifier eventId, Random random, int depth) {
            if (depth > MAX_SOUND_EVENT_DEPTH) {
                return null;
            }
            WeightedSoundSet set = soundManager.get(eventId);
            if (set == null || set == SoundManager.INTENTIONALLY_EMPTY_SOUND_SET) {
                return null;
            }
            Sound sound = set.getSound(random);
            if (sound == null || sound == SoundManager.MISSING_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                return null;
            }
            if (sound.getRegistrationType() == Sound.RegistrationType.SOUND_EVENT) {
                Identifier next = sound.getIdentifier();
                if (next == null) {
                    return null;
                }
                return resolveSound(soundManager, next, random, depth + 1);
            }
            return sound;
        }

        private DecodedSound decodeSound(ResourceFactory resourceFactory, Sound sound) {
            Identifier location = sound.getLocation();
            if (location == null) {
                return null;
            }

            try (var in = resourceFactory.open(location);
                 var stream = new OggAudioStream(in)) {
                var format = stream.getFormat();
                int channels = Math.max(1, format.getChannels());
                int sampleRate = Math.max(1, (int) format.getSampleRate());

                FloatArrayList samples = new FloatArrayList();
                FloatConsumer consumer = samples::add;
                while (stream.read(consumer)) {
                    // keep reading
                }
                return new DecodedSound(samples.toFloatArray(), channels, sampleRate);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void mixDecodedSound(float[] mix,
                                        int mixChannels,
                                        int mixSampleRate,
                                        DecodedSound sound,
        float startTimeSec,
                                        float gain,
                                        float pitch) {
        if (mixChannels <= 0 || mixSampleRate <= 0) {
            return;
        }

        int totalFrames = mix.length / mixChannels;
        int startFrame = (int) Math.floor(startTimeSec * mixSampleRate);
        if (startFrame >= totalFrames) {
            return;
        }

        float[] src = sound.samples;
        int srcChannels = Math.max(1, sound.channels);
        int srcFrames = src.length / srcChannels;
        if (srcFrames == 0) {
            return;
        }

        float srcStep = pitch * (sound.sampleRate / (float) mixSampleRate);
        if (srcStep <= 0f) {
            return;
        }

        for (int dstFrame = 0; startFrame + dstFrame < totalFrames; dstFrame++) {
            float srcPos = dstFrame * srcStep;
            int srcIndex = (int) srcPos;
            if (srcIndex >= srcFrames) {
                break;
            }
            float frac = srcPos - srcIndex;

            if (mixChannels == 1) {
                float mono = sampleInterleaved(src, srcChannels, srcFrames, srcIndex, frac, 0);
                if (srcChannels >= 2) {
                    float r = sampleInterleaved(src, srcChannels, srcFrames, srcIndex, frac, 1);
                    mono = 0.5f * (mono + r);
                }
                int outIdx = (startFrame + dstFrame);
                mix[outIdx] += mono * gain;
            } else {
                int outBase = (startFrame + dstFrame) * mixChannels;
                float left = sampleInterleaved(src, srcChannels, srcFrames, srcIndex, frac, 0);
                float right = (srcChannels >= 2)
                        ? sampleInterleaved(src, srcChannels, srcFrames, srcIndex, frac, 1)
                        : left;
                mix[outBase] += left * gain;
                mix[outBase + 1] += right * gain;
            }
        }
    }

    private static float sampleInterleaved(float[] src,
        int channels,
                                           int frames,
                                           int index,
                                           float frac,
                                           int channel) {
        int ch = Math.min(channel, channels - 1);
        int base = index * channels + ch;
        float s0 = src[base];
        if (frac <= 0f || index + 1 >= frames) {
            return s0;
        }
        float s1 = src[(index + 1) * channels + ch];
        return s0 + (s1 - s0) * frac;
    }}






























