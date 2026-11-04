package com.director.ui.actor.pose;

import com.director.actor.animation.asset.BezierEase;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

/** An uncommitted handle gesture belongs to one editor, segment and canvas. */
final class ActorBezierHandleEditor {
    private static final float PADDING = 12f;
    private static final float HIT_RADIUS = 9f;

    private record Plot(float left, float top, float width, float height) {
        float x(float value) { return left + value * width; }
        float y(float value) { return top + (1f - value) * height; }
    }

    private record Drag(ActorAnimationEditContext context, ActorAnimationAuthoringService.Key key,
                        ChannelKeyframe<?> source, ChannelKeyframe<?> successor, Plot plot,
                        boolean first, float mouseX, float mouseY) {}

    private Drag drag;

    void cancel() { drag = null; }
    boolean active() { return drag != null; }

    boolean render(ActorAnimationEditContext context, ActorAnimationAuthoringService.Key key,
                   ChannelKeyframe<?> source, ChannelKeyframe<?> successor, boolean enabled) {
        float width = Math.max(2 * PADDING + 1, Math.min(240f, ImGui.getContentRegionAvailX()));
        float height = 132f;
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        var plot = new Plot(x + PADDING, y + PADDING, width - 2 * PADDING, height - 2 * PADDING);
        ImGui.invisibleButton("##actorBezierHandles", width, height);
        boolean released = ImGui.isMouseReleased(ImGuiMouseButton.Left) && ImGui.isItemDeactivated();
        if (drag != null && (!enabled || !drag.context().equals(context) || !drag.key().equals(key)
                || !drag.source().equals(source) || !drag.successor().equals(successor) || !drag.plot().equals(plot)
                || ImGui.isKeyPressed(ImGuiKey.Escape) || (!ImGui.isItemActive() && !released))) cancel();

        var ease = source.ease();
        float mouseX = ImGui.getMousePosX(), mouseY = ImGui.getMousePosY();
        if (enabled && drag == null && ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            float first = distance(mouseX, mouseY, plot.x(ease.x1()), plot.y(ease.y1()));
            float second = distance(mouseX, mouseY, plot.x(ease.x2()), plot.y(ease.y2()));
            if (Math.min(first, second) <= HIT_RADIUS)
                drag = new Drag(context, key, source, successor, plot, first <= second, mouseX, mouseY);
        }
        if (drag != null)
            ease = offsetHandle(ease, drag.first(), (mouseX - drag.mouseX()) / plot.width(),
                    (drag.mouseY() - mouseY) / plot.height());
        draw(plot, ease);
        if (drag == null || !released) return false;

        cancel();
        return ActorAnimationAuthoringService.setBezierEase(context, key, ease);
    }

    static BezierEase offsetHandle(BezierEase ease, boolean first, float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return ease;
        return first
                ? new BezierEase(Math.clamp(ease.x1() + dx, 0f, 1f), Math.clamp(ease.y1() + dy, 0f, 1f), ease.x2(), ease.y2())
                : new BezierEase(ease.x1(), ease.y1(), Math.clamp(ease.x2() + dx, 0f, 1f), Math.clamp(ease.y2() + dy, 0f, 1f));
    }

    private static float distance(float x, float y, float handleX, float handleY) {
        return (float) Math.hypot(x - handleX, y - handleY);
    }

    private static void draw(Plot plot, BezierEase ease) {
        var draw = ImGui.getWindowDrawList();
        draw.addRectFilled(plot.left(), plot.top(), plot.x(1), plot.y(0), TimelineColors.rgba(24, 27, 32, 255));
        int guide = TimelineColors.rgba(110, 117, 130, 255);
        draw.addLine(plot.x(0), plot.y(0), plot.x(ease.x1()), plot.y(ease.y1()), guide);
        draw.addLine(plot.x(1), plot.y(1), plot.x(ease.x2()), plot.y(ease.y2()), guide);
        int curve = TimelineColors.rgba(130, 200, 255, 255);
        float previousY = plot.y(0);
        for (int i = 1; i <= 64; i++) {
            float time = i / 64f;
            float nextY = plot.y(ease.map(time));
            draw.addLine(plot.x((i - 1) / 64f), previousY, plot.x(time), nextY, curve, 2f);
            previousY = nextY;
        }
        draw.addCircleFilled(plot.x(ease.x1()), plot.y(ease.y1()), 5f, TimelineColors.rgba(255, 190, 70, 255));
        draw.addCircleFilled(plot.x(ease.x2()), plot.y(ease.y2()), 5f, TimelineColors.rgba(235, 165, 255, 255));
    }
}
