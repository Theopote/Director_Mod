package com.director.season;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.season.SeasonSample;
/** One terrain section sees one tint snapshot even if the playhead changes during its build. */
public final class SeasonTintContext {
    private record Snapshot(SeasonSample sample) {}
    private static final ThreadLocal<Snapshot> PINNED = new ThreadLocal<>();
    private SeasonTintContext() {}
    public static SeasonSample sample() {
        var pinned=PINNED.get();
        return pinned==null ? WorldRenderOverride.get().seasonSample() : pinned.sample();
    }
    public static Scope openScope() { return new Scope(); }
    public static final class Scope implements AutoCloseable {
        private final Snapshot previous=PINNED.get();
        private Scope() {
            var sample=sample();
            PINNED.set(new Snapshot(sample==null?null:sample.copy()));
        }
        @Override public void close() { if(previous==null) PINNED.remove(); else PINNED.set(previous); }
    }
}
