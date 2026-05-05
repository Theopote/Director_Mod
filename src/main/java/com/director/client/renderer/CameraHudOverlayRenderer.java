package com.director.client.renderer;

import com.director.ui.camera.path.CameraPathEditController;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.CameraViewportProfile;
import com.director.ui.timeline.model.camera.ViewportMath;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import com.director.ui.timeline.render.TimelineColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Screen-space overlays that must stay on the Minecraft game window.
 */
public final class CameraHudOverlayRenderer {

    private CameraHudOverlayRenderer() {
    }

    public static void render(DrawContext context) {
        if (context == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int screenW = Math.max(1, client.getWindow().getScaledWidth());
        int screenH = Math.max(1, client.getWindow().getScaledHeight());

        renderCameraViewportOverlay(context, screenW, screenH);
        CameraPathEditController.getInstance().renderHudOverlay(context);
    }

    private static void renderCameraViewportOverlay(DrawContext context, int screenW, int screenH) {
        TimelineTrack activeCameraTrack = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (activeCameraTrack == null) {
            return;
        }

        CameraViewportProfile profile = activeCameraTrack.getCameraViewportProfile();
        if (profile == null) {
            return;
        }

        float targetAspect = profile.getAspectRatio();
        ViewportMath.ViewportRects viewport;
        if (profile.getPreviewScalePolicy() == CameraViewportProfile.PreviewScalePolicy.FIT) {
            viewport = ViewportMath.computeFit(0, 0, screenW, screenH, targetAspect);
        } else {
            viewport = ViewportMath.computeFill(0, 0, screenW, screenH, targetAspect);
        }

        if (profile.isShowLetterboxMask()) {
            drawLetterboxMask(context, viewport, profile.getLetterboxAlpha());
        }

        drawRectOutline(context,
                Math.round(viewport.innerX),
                Math.round(viewport.innerY),
                Math.round(viewport.innerX2()),
                Math.round(viewport.innerY2()),
                TimelineColors.rgba(255, 255, 255, 255),
                2);

        if (profile.isShowSafeAreas()) {
            drawSafeAreas(context, viewport, profile.getActionSafe(), profile.getTitleSafe());
        }

        if (profile.isShowRuleOfThirds()) {
            drawRuleOfThirds(context, viewport);
        }

        if (profile.isShowCenterCross()) {
            drawCenterCross(context, viewport);
        }
    }

    private static void drawLetterboxMask(DrawContext context, ViewportMath.ViewportRects viewport, float alpha) {
        int color = TimelineColors.rgba(0, 0, 0, (int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255f));

        int outerX = Math.round(viewport.outerX);
        int outerY = Math.round(viewport.outerY);
        int outerX2 = Math.round(viewport.outerX + viewport.outerW);
        int outerY2 = Math.round(viewport.outerY + viewport.outerH);

        int innerX = Math.round(viewport.innerX);
        int innerY = Math.round(viewport.innerY);
        int innerX2 = Math.round(viewport.innerX2());
        int innerY2 = Math.round(viewport.innerY2());

        if (innerY > outerY) {
            context.fill(outerX, outerY, outerX2, innerY, color);
        }
        if (innerY2 < outerY2) {
            context.fill(outerX, innerY2, outerX2, outerY2, color);
        }
        if (innerX > outerX) {
            context.fill(outerX, innerY, innerX, innerY2, color);
        }
        if (innerX2 < outerX2) {
            context.fill(innerX2, innerY, outerX2, innerY2, color);
        }
    }

    private static void drawSafeAreas(DrawContext context, ViewportMath.ViewportRects viewport, float actionSafe, float titleSafe) {
        float actionW = viewport.innerW * actionSafe;
        float actionH = viewport.innerH * actionSafe;
        float actionX = viewport.innerX + (viewport.innerW - actionW) * 0.5f;
        float actionY = viewport.innerY + (viewport.innerH - actionH) * 0.5f;

        drawRectOutline(context,
                Math.round(actionX),
                Math.round(actionY),
                Math.round(actionX + actionW),
                Math.round(actionY + actionH),
                TimelineColors.rgba(0, 255, 0, 200),
                1);

        float titleW = viewport.innerW * titleSafe;
        float titleH = viewport.innerH * titleSafe;
        float titleX = viewport.innerX + (viewport.innerW - titleW) * 0.5f;
        float titleY = viewport.innerY + (viewport.innerH - titleH) * 0.5f;

        drawRectOutline(context,
                Math.round(titleX),
                Math.round(titleY),
                Math.round(titleX + titleW),
                Math.round(titleY + titleH),
                TimelineColors.rgba(255, 0, 0, 200),
                1);
    }

    private static void drawRuleOfThirds(DrawContext context, ViewportMath.ViewportRects viewport) {
        int color = TimelineColors.rgba(255, 255, 255, 136);

        int x1 = Math.round(viewport.innerX + viewport.innerW / 3.0f);
        int x2 = Math.round(viewport.innerX + viewport.innerW * 2.0f / 3.0f);
        int y1 = Math.round(viewport.innerY + viewport.innerH / 3.0f);
        int y2 = Math.round(viewport.innerY + viewport.innerH * 2.0f / 3.0f);

        int top = Math.round(viewport.innerY);
        int bottom = Math.round(viewport.innerY2());
        int left = Math.round(viewport.innerX);
        int right = Math.round(viewport.innerX2());

        drawVerticalLine(context, x1, top, bottom, color, 1);
        drawVerticalLine(context, x2, top, bottom, color, 1);
        drawHorizontalLine(context, left, right, y1, color, 1);
        drawHorizontalLine(context, left, right, y2, color, 1);
    }

    private static void drawCenterCross(DrawContext context, ViewportMath.ViewportRects viewport) {
        int color = TimelineColors.rgba(255, 255, 255, 170);

        int cx = Math.round(viewport.innerX + viewport.innerW * 0.5f);
        int cy = Math.round(viewport.innerY + viewport.innerH * 0.5f);

        int halfLen = Math.max(8, Math.round(Math.min(viewport.innerW, viewport.innerH) * 0.04f));
        drawHorizontalLine(context, cx - halfLen, cx + halfLen, cy, color, 1);
        drawVerticalLine(context, cx, cy - halfLen, cy + halfLen, color, 1);
    }

    private static void drawRectOutline(DrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        int t = Math.max(1, thickness);
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        context.fill(x1, y1, x2, y1 + t, color);
        context.fill(x1, y2 - t, x2, y2, color);
        context.fill(x1, y1 + t, x1 + t, y2 - t, color);
        context.fill(x2 - t, y1 + t, x2, y2 - t, color);
    }

    private static void drawHorizontalLine(DrawContext context, int x1, int x2, int y, int color, int thickness) {
        int t = Math.max(1, thickness);
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        context.fill(left, y, right, y + t, color);
    }

    private static void drawVerticalLine(DrawContext context, int x, int y1, int y2, int color, int thickness) {
        int t = Math.max(1, thickness);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        context.fill(x, top, x + t, bottom, color);
    }
}
