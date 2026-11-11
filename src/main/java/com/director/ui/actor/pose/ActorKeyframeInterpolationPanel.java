package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import imgui.ImGui;
import java.util.*;

/** Shared existing-key editor used by the timeline menu and actor dope sheet. */
public final class ActorKeyframeInterpolationPanel {
    private static final Set<ActorKeyframeInterpolationPanel> instances = Collections.newSetFromMap(new WeakHashMap<>());
    private final ActorBezierHandleEditor handles = new ActorBezierHandleEditor();

    public ActorKeyframeInterpolationPanel() { instances.add(this); }

    static void cancelAll() {
        for (var panel : new ArrayList<>(instances)) panel.handles.cancel();
    }

    private static String tr(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys."+key);
    }
    public boolean render(ActorAnimationEditContext context, Collection<ActorAnimationAuthoringService.Key> selection, boolean enabled) {
        var keys=selection==null?List.<ActorAnimationAuthoringService.Key>of():new ArrayList<>(new LinkedHashSet<>(selection));
        var modes=new LinkedHashSet<InterpolationMode>();
        ChannelKeyframe<?> bezier = null;
        ChannelKeyframe<?> successor = null;
        boolean valid=context!=null && context.isCurrent() && !keys.isEmpty();
        for(var key:keys) {
            var track=context==null || key==null?null:context.asset().findBoneTrack(key.boneId()).orElse(null);
            if(track==null || key.channel()==null) { valid=false; continue; }
            KeyframeTrack<?> channel = switch (key.channel()) {
                case TRANSLATION -> track.translation();
                case ROTATION -> track.rotation();
                case SCALE -> track.scale();
            };
            ChannelKeyframe<?> frame = channel.findAt(key.time()).orElse(null);
            if(frame==null) valid=false;
            else {
                modes.add(frame.interpolation());
                if (keys.size() == 1 && frame.ease() != null) {
                    bezier = frame;
                    successor = channel.keyframes().stream().filter(next -> next.time() > frame.time()).findFirst().orElse(null);
                }
            }
        }
        String preview=modes.size()==1?tr("mode."+ modes.getFirst().name().toLowerCase(Locale.ROOT)):tr("mixed");
        boolean changed=false;
        boolean writable = enabled && valid;
        ImGui.beginDisabled(!writable);
        try {
            if(ImGui.beginCombo(tr("edit_interpolation"),preview)) {
                try {
                    for(var mode:InterpolationMode.values()) {
                        boolean selected=modes.size()==1 && modes.contains(mode);
                        if(ImGui.selectable(tr("mode."+mode.name().toLowerCase(Locale.ROOT)),selected) && !selected)
                            changed=ActorAnimationAuthoringService.setInterpolation(context,keys,mode);
                    }
                } finally { ImGui.endCombo(); }
            }
            if (!changed && bezier != null) {
                if (successor != null) {
                    ImGui.textWrapped(tr("bezier_drag"));
                    changed = handles.render(context, keys.getFirst(), bezier, successor, writable);
                } else handles.cancel();
                if (!changed) {
                    ImGui.beginDisabled(handles.active());
                    try { changed = renderEase(context, keys.getFirst(), bezier.ease()); }
                    finally { ImGui.endDisabled(); }
                }
            } else handles.cancel();
        } finally { ImGui.endDisabled(); }
        ImGui.textWrapped(tr("interpolation_hint"));
        return changed;
    }

    private static boolean renderEase(ActorAnimationEditContext context, ActorAnimationAuthoringService.Key key, BezierEase ease) {
        ImGui.textWrapped(tr("bezier_hint"));
        float[] points = {ease.x1(), ease.y1(), ease.x2(), ease.y2()};
        String[] labels = {"X1", "Y1", "X2", "Y2"};
        boolean submitted = false;
        for (int i = 0; i < points.length; i++) {
            var input = new imgui.type.ImFloat(points[i]);
            if (ImGui.inputFloat(labels[i] + "##actorEase", input, 0f, 0f, "%.3f",
                    imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue) && Float.isFinite(input.get())) {
                points[i] = i % 2 == 0 ? Math.clamp(input.get(), 0f, 1f) : input.get();
                submitted = true;
            }
        }
        return submitted && ActorAnimationAuthoringService.setBezierEase(context, key,
                new BezierEase(points[0], points[1], points[2], points[3]));
    }
}
