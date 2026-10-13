package com.director.ui.lighting;

import com.director.lighting.PointLight;
import com.director.timeline.model.TimelineModel;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Live previews become one history entry when a property gesture loses focus. */
public final class PointLightParameterEdit {
    private final TimelineModel model;
    private final PointLight before;
    private final long revision;
    private PointLight after;
    private boolean closed;
    private com.director.lighting.LightTimelineClip clip;
    private com.director.lighting.LightKeyframe key;
    public PointLightParameterEdit(TimelineModel model,com.director.lighting.LightTimelineClip clip,com.director.lighting.LightKeyframe key) {
        this(model,key.value());this.clip=clip;this.key=key;
    }
    public boolean belongsToKey(TimelineModel model,com.director.lighting.LightKeyframe key) {
        return this.model==model && this.key==key && isCurrent();
    }

    public PointLightParameterEdit(TimelineModel model, PointLight light) {
        this.model = Objects.requireNonNull(model);
        before = Objects.requireNonNull(light);
        after = light;
        revision = model.getActorEditContextRevision();
    }
    public TimelineModel model() { return model; }
    public boolean belongsTo(TimelineModel model, UUID id) {
        return key==null && this.model == model && before.id().equals(id) && isCurrent();
    }
    private boolean isCurrent() {
        return !closed && model.getActorEditContextRevision() == revision
                && (key==null ? after.equals(model.getPointLight(before.id()))
                    : com.director.lighting.LightLifecycle.containsClip(model,clip) && clip.getClipKeyframes().contains(key) && after.equals(key.value()));
    }
    public boolean preview(PointLight light) {
        if (!before.id().equals(light.id())) throw new IllegalArgumentException("Light ID mismatch");
        if (!isCurrent() || after.equals(light)) return false;
        if(key==null) model.replacePointLight(light.id(), light);else key.setValue(light);
        after = light;
        return true;
    }
    public boolean commit(BiConsumer<PointLight, PointLight> record) {
        boolean changed = isCurrent() && !before.equals(after);
        closed = true;
        if (changed) record.accept(before, after);
        return changed;
    }
}
