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
    private static final int BLOCK_FRAMES = 8192;
    private final Runnable checkCanceled;

    public TimelineAudioExporter() {
        this(() -> {});
    }

    public TimelineAudioExporter(Runnable checkCanceled) {
        this.checkCanceled = java.util.Objects.requireNonNull(checkCanceled);
    }

    @Override
    public Path exportWav(VideoExportSettings settings, float startTimeSec, float endTimeSec) throws IOException {
        checkCanceled.run();
        double duration = (double) endTimeSec - startTimeSec;
        if (!Double.isFinite(duration) || duration <= 0) throw new IOException("音频时间范围无效");

        int sampleRate = settings.audioSampleRate;
        int channels = settings.audioChannels;
        int bitsPerSample = 16;

        long totalSamples = (long) Math.ceil(duration * sampleRate);
        long dataSize = totalSamples * channels * (bitsPerSample / 8);

        if (dataSize > 0xFFFFFFFFL - 36 || dataSize < 0) {
            throw new IOException("音频超过 RIFF WAV 的 4 GiB 限制，请缩短导出范围");
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("director").resolve("cache");
        Files.createDirectories(cacheDir);

        Path outFile = Files.createTempFile(cacheDir, "timeline_audio_", ".wav");
        boolean completed = false;

        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(outFile, StandardOpenOption.TRUNCATE_EXISTING)))) {
                writeWavHeader(out, sampleRate, channels, bitsPerSample, dataSize);
                writePcm16leFromEvents(out, sampleRate, channels, totalSamples, startTimeSec, endTimeSec);
            }
            completed = true;
            return outFile;
        } finally {
            if (!completed) Files.deleteIfExists(outFile);
        }
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
        int sampleRate,
        int channels,
        long totalSamples,
        float startTimeSec,
        float endTimeSec) throws IOException {

        // Snapshot mutable editor state on its owning client thread.
        List<EventClip> clips;
        try {
            clips = MinecraftClient.getInstance().submit(() -> collectEventClips(0f, endTimeSec))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("音频采样被中断", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("无法读取时间线音频", e);
        }
        checkCanceled.run();
        SoundResolver resolver = new SoundResolver(checkCanceled);
        List<MixClip> sounds = new ArrayList<>();
        for (EventClip ev : clips) {
            checkCanceled.run();
            if (!ev.enabled || ev.soundId == null || ev.soundId.isBlank()) continue;
            ResolvedSound resolved = resolver.resolveAndDecode(ev.soundId, ev.timeSec);
            if (resolved == null || resolved.decoded == null || resolved.decoded.samples.length == 0) continue;
            float gain = clamp01(ev.volume * ev.trackVolume * resolved.volume);
            float pitch = Math.max(0.1f, Math.min(3.0f, ev.pitch * resolved.pitch));
            long startFrame = (long) Math.floor(((double) ev.timeSec - startTimeSec) * sampleRate);
            sounds.add(new MixClip(resolved.decoded, startFrame, gain, pitch));
        }
        // Memory usage is independent of export duration. Negative starts retain sound tails.
        float[] mix = new float[BLOCK_FRAMES * channels];
        for (long blockStart = 0; blockStart < totalSamples; blockStart += BLOCK_FRAMES) {
            checkCanceled.run();
            int frames = (int) Math.min(BLOCK_FRAMES, totalSamples - blockStart);
            java.util.Arrays.fill(mix, 0f);
            for (MixClip clip : sounds) {
                checkCanceled.run();
                DecodedSound sound = clip.sound();
                PcmAudioMixer.mix(mix, frames, channels, sampleRate, blockStart,
                        sound.samples, sound.channels, sound.sampleRate,
                        clip.startFrame(), clip.gain(), clip.pitch());
            }
            for (int sample = 0; sample < frames * channels; sample++) writeSample16le(out, mix[sample]);
        }
    }

    private record MixClip(DecodedSound sound, long startFrame, float gain, float pitch) {}

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
        private final Runnable checkCanceled;

        SoundResolver(Runnable checkCanceled) {
            this.checkCanceled = checkCanceled;
        }

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
                    checkCanceled.run();
                }
                return new DecodedSound(samples.toFloatArray(), channels, sampleRate);
            } catch (java.util.concurrent.CancellationException canceled) {
                throw canceled;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

}
