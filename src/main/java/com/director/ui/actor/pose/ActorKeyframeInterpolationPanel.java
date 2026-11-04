package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import imgui.ImGui;
import java.util.*;

/** Shared existing-key editor used by the timeline menu and actor dope sheet. */
public final class ActorKeyframeInterpolationPanel {
    private ActorKeyframeInterpolationPanel() {}
    private static String tr(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys."+key);
    }
    public static boolean render(ActorAnimationEditContext context, Collection<ActorAnimationAuthoringService.Key> selection, boolean enabled) {
        var keys=selection==null?List.<ActorAnimationAuthoringService.Key>of():new ArrayList<>(new LinkedHashSet<>(selection));
        var modes=new LinkedHashSet<InterpolationMode>();
        BezierEase ease = null;
        boolean valid=context!=null && context.isCurrent() && !keys.isEmpty();
        for(var key:keys) {
            var track=context==null || key==null?null:context.asset().findBoneTrack(key.boneId()).orElse(null);
            if(track==null || key.channel()==null) { valid=false; continue; }
            ChannelKeyframe<?> frame=switch(key.channel()) {
                case TRANSLATION -> track.translation().findAt(key.time()).orElse(null);
                case ROTATION -> track.rotation().findAt(key.time()).orElse(null);
                case SCALE -> track.scale().findAt(key.time()).orElse(null);
            };
            if(frame==null) valid=false;
            else {
                modes.add(frame.interpolation());
                if (keys.size() == 1) ease = frame.ease();
            }
        }
        String preview=modes.size()==1?tr("mode."+modes.iterator().next().name().toLowerCase(Locale.ROOT)):tr("mixed");
        boolean changed=false;
        ImGui.beginDisabled(!enabled || !valid);
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
            if (!changed && ease != null)
                changed = renderEase(context, keys.getFirst(), ease);
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
                points[i] = Math.clamp(input.get(), 0f, 1f);
                submitted = true;
            }
        }
        return submitted && ActorAnimationAuthoringService.setBezierEase(context, key,
                new BezierEase(points[0], points[1], points[2], points[3]));
    }
}
