package com.director.lighting;
import com.director.timeline.model.clip.ClipKeyframe;
import com.director.timeline.model.TimelineInterpolation;
import java.util.Objects;
public final class LightKeyframe extends ClipKeyframe {
    private PointLight value;
    public LightKeyframe(float time,PointLight value) { super(time);this.value=Objects.requireNonNull(value); }
    private LightKeyframe(java.util.UUID id,float time,PointLight value) { super(id,time,TimelineInterpolation.LINEAR);this.value=Objects.requireNonNull(value); }
    public PointLight value() { return value; }
    public void setValue(PointLight value) { this.value=Objects.requireNonNull(value); }
    @Override public LightKeyframe copy() {
        var copy=new LightKeyframe(getTime(),value);copy.setInterpolation(getInterpolation());return copy;
    }
    @Override public LightKeyframe snapshot() {
        var copy=new LightKeyframe(getId(),getTime(),value);copy.setInterpolation(getInterpolation());return copy;
    }
    @Override public boolean hasSameStateAs(ClipKeyframe other) {
        return super.hasSameStateAs(other) && value.equals(((LightKeyframe) other).value());
    }
}
