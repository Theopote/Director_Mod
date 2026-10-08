package com.director.ui.timeline.render;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.core.DirectorServices;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.*;
import imgui.flag.ImGuiMouseButton;
import java.util.*;

/** Root TRS rows are a view of one shared key, not three independent keys. */
final class ActorRootTimelineRows {
    static final int COUNT = 3;
    record Hit(EntityClip clip, EntityClipKeyframe key, float x) {}
    static List<EntityClip> clips(TimelineTrack track) {
        var payload = track.getActorTrackPayload();
        return payload == null ? List.of() : payload.getClips().stream()
                .filter(c -> c instanceof EntitySpawnClip || c instanceof EntityTransformClip).toList();
    }
    static boolean selectedAt(TimelineModel model, EntityClip clip, EntityClipKeyframe key) {
        return model.getSelectedClip() == clip.getTimelineClip() && Math.abs(key.getTime() - model.getCurrentTime()) <= 1e-4f;
    }
    private static String tr(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.root_rows." + key);
    }
    void render(ImDrawList draw, TimelineModel model, TimelineTrack track, float x, float left, float right,
                float top, float height, TimelineRenderMath math) {
        String[] labels = {tr("position"), tr("rotation"), tr("scale")};
        int[] colors = {TimelineColors.rgba(100,190,255,255), TimelineColors.rgba(255,190,90,255), TimelineColors.rgba(140,220,130,255)};
        for (int row = 0; row < COUNT; row++) {
            float y = top + row * height;
            draw.addRectFilled(x, y, right, y + height, TimelineColors.rgba(29,32,37,255));
            draw.pushClipRect(x, y, left, y + height, true);
            draw.addText(x + 16, y + 4, TimelineColors.COLOR_TEXT_LIGHT, labels[row]); draw.popClipRect();
            draw.addLine(left, y, left, y + height, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.addLine(x, y + height, right, y + height, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.pushClipRect(left, y, right, y + height, true);
            try {
                for (var clip : clips(track)) for (var key : clip.getKeyframes()) {
                    float kx = math.timeToX(key.getTime()), ky = y + height / 2;
                    if (kx < left - 6 || kx > right + 6) continue;
                    int color = clip.isMuted() || track.isMuted() ? TimelineColors.rgba(110,110,110,255)
                            : selectedAt(model,clip,key) ? TimelineColors.rgba(255,255,255,255) : colors[row];
                    draw.addQuadFilled(kx,ky-5,kx+5,ky,kx,ky+5,kx-5,ky,color);
                }
            } finally { draw.popClipRect(); }
        }
    }
    boolean click(TimelineModel model, TimelineTrack track, float mouseX, float left, int row, TimelineRenderMath math) {
        if (row < 0 || row >= COUNT) return false;
        // Consume right clicks on root rows; they must not reach bone or clip menus.
        if (!ImGui.isMouseClicked(ImGuiMouseButton.Left)) return true;
        var preferred = model.getSelectedClip();
        List<Hit> hits = new ArrayList<>();
        if (mouseX >= left) for (var clip : clips(track)) for (var key : clip.getKeyframes()) {
            float x = math.timeToX(key.getTime());
            if (Math.abs(x - mouseX) <= 7) hits.add(new Hit(clip,key,x));
        }
        var preferredHits = hits.stream().filter(h -> h.clip().getTimelineClip() == preferred).toList();
        if (!preferredHits.isEmpty()) hits = new ArrayList<>(preferredHits);
        if (hits.size() > 1) { ImGui.setTooltip(tr("ambiguous")); return true; }
        ActorSelectionHub.selectActor(track.getActorTrackPayload().getActorRef().getActorId());
        com.director.ui.actor.pose.ActorBoneSelectionController.getInstance().deactivate();
        if (hits.size() == 1) {
            var hit = hits.getFirst();
            model.setSelectedClip(hit.clip().getTimelineClip()); model.setCurrentTime(hit.key().getTime());
            DirectorServices.get().uiSelection().setCurrentSelection(hit.clip().getTimelineClip());
        }
        return true;
    }
}
