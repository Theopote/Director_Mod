package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.actor.edit.ActorClipPropertyEditSession;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

/** Shared ImGui lifetime for explicit Transform and LookAt clip inspectors. */
public final class ActorClipInspectorGesture {
    private ActorClipPropertyEditSession session;
    private String widget;
    private int lastFrame = -1;
    private boolean rendered, released, cancelledUntilRelease, dirty;
    private String error = "";
    private boolean readOnlyRendered;

    public EntityClip begin(EntityClip source) {
        ActorRuntimeDiagnosticsUI.render(source);
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        int frame = ImGui.getFrameCount();
        boolean competing = com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        if (session != null && (frame != lastFrame + 1 || competing || !session.isCurrent(model, source))) cancel();
        lastFrame = frame; rendered = false; released = false;
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) cancel();
        if (source == null) return null;
        if (competing) return readOnly(source, "gizmo");
        if (cancelledUntilRelease) {
            if (ImGui.isMouseDown(0) || ImGui.isAnyItemActive()) return readOnly(source, "unavailable");
            cancelledUntilRelease = false;
        }
        if (session == null) {
            try { session = new ActorClipPropertyEditSession(model, source); widget = null; dirty = false; }
            catch (RuntimeException unavailable) {
                var owner=source.getTimelineClip()==null?null:source.getTimelineClip().getParentTrack();
                String reason = model == null ? "no_project" : model.isPlaying() ? "playing" : model.isScrubbing() ? "scrubbing"
                        : owner != null && owner.isLocked() ? "locked" : owner != null && owner.isMuted() ? "muted"
                        : source.isMuted() ? "clip_muted" : "unavailable";
                return readOnly(source, reason);
            }
        }
        return session.draft();
    }
    private EntityClip readOnly(EntityClip source, String reason) {
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.readonly",
                ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector." + reason)));
        var draft = ActorClipPropertyEditSession.detached(source);
        readOnlyRendered = true;
        ImGui.beginDisabled();
        return draft;
    }
    public void observe(String id, boolean changed) {
        dirty |= changed;
        if (id.equals(widget)) { rendered = true; released |= ImGui.isItemDeactivated(); }
        if (ImGui.isItemActive()) { widget = id; rendered = true; }
        if (changed && !ImGui.isItemActive()) released = true;
    }
    public void requestCommit() { released = true; dirty = true; }
    public void report(String message) {
        error = message;
        cancel();
    }
    public void end() {
        if(readOnlyRendered) {ImGui.endDisabled();readOnlyRendered=false;return;}
        if (session != null && widget != null && !rendered) cancel();
        if (session != null && (released || !ImGui.isAnyItemActive())) {
            var edit = session; session = null; widget = null;
            try { if (!edit.commit()) error = ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.cancelled"); else if (dirty) error = ""; }
            catch (RuntimeException failure) { edit.cancel(); error = failure.getMessage(); }
        }
        if (error != null && !error.isEmpty()) ImGui.textWrapped(error);
    }
    private void cancel() {
        if (session != null) session.cancel();
        session = null; widget = null; cancelledUntilRelease = true;
    }
}
