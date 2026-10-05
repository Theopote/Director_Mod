package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.ui.theme.Icons;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Compact dope sheet for the active rig animation on the selected actor. */
public final class ActorAnimationDopeSheet {
    private static String selectedAssetId;
    private static final ActorKeyframeClipboard keyClipboard=ActorKeyframeClipboard.getInstance();
    private static final ActorKeyframeTimePanel timePanel=new ActorKeyframeTimePanel();
    private static ActorAnimationEditContext renderContext;
    private static ActorAnimationEditContext interactionContext;
    private static SelectionContext selectionContext;
    private static Map<KeyId, Object> interactionSource = Map.of();
    private record SelectionContext(TimelineModel model, UUID actorId, EntityAnimationClip clip, AnimationAsset asset) {}
    public static boolean isKeyInteractionActive() { return dragCandidate || marqueeCandidate; }
    public static void cancelKeyInteraction() {
        dragCandidate=marqueeCandidate=false; interactionContext=null; interactionSource=Map.of(); interactionViewport=null;
    }
    private static boolean canWrite() {
        return renderContext != null && renderContext.isCurrent()
                && !ActorBoneGizmoController.getInstance().isDragging() && !ActorBonePropertiesPanel.hasNumericDraft();
    }
    private static List<ActorAnimationAuthoringService.Key> assetKeys() {
        return selection.stream().map(key -> new ActorAnimationAuthoringService.Key(key.boneId(),
                ActorAnimationAuthoringService.Channel.valueOf(key.channel().name()), key.time())).toList();
    }
    private static Map<KeyId, Object> selectedFrames(AnimationAsset asset) {
        Map<KeyId, Object> result = new HashMap<>();
        for (KeyId key : selection) asset.findBoneTrack(key.boneId()).ifPresent(track -> {
            Object frame = switch (key.channel()) {
                case TRANSLATION -> track.translation().findAt(key.time()).orElse(null);
                case ROTATION -> track.rotation().findAt(key.time()).orElse(null);
                case SCALE -> track.scale().findAt(key.time()).orElse(null);
            };
            result.put(key, frame);
        });
        return result;
    }
    private static final Set<KeyId> selection = new LinkedHashSet<>();
    private static boolean dragCandidate;
    private static boolean marqueeCandidate;
    private static boolean interactionAdditive;
    private static float interactionStartX;
    private static float interactionStartY;
    private static float interactionCurrentX;
    private static float interactionCurrentY;
    private static String interactionAssetId;
    private static float interactionFrameRate;
    private static ActorDopeSheetViewport interactionViewport;
    private static final Map<String, ViewState> views = new HashMap<>();

    private ActorAnimationDopeSheet() {}

    private static long observedSelectionRevision=-1;
    private static List<ActorAnimationAuthoringService.Key> importedKeys=List.of();
    public static void render(TimelineModel model, UUID actorId) {
        try { renderInternal(model,actorId); }
        finally {
            if(renderContext!=null && !assetKeys().equals(importedKeys)
                    && model.getSelectedClip()==renderContext.clip().getTimelineClip()) {
                ActorKeyframeSelection.getInstance().replace(model,renderContext.track(),renderContext.clip(),renderContext.asset(),assetKeys(),true);
                observedSelectionRevision=ActorKeyframeSelection.getInstance().revision();
            }
        }
    }
    private static void renderInternal(TimelineModel model, UUID actorId) {
        if (!com.director.actor.rig.ActorRigCompatibility.editable(actorId)) {
            clearSelection();
            selectionContext = null;
            renderContext = interactionContext = null;
            dragCandidate = marqueeCandidate = false;
            return;
        }
        renderContext = ActorAnimationEditContext.resolve(model, actorId);
        EntityAnimationClip clip = renderContext == null ? null : renderContext.clip();
        AnimationAsset asset = renderContext == null ? null : renderContext.asset();
        SelectionContext next = new SelectionContext(model, actorId, clip, asset);
        if (!Objects.equals(selectionContext, next)) {
            clearSelection();
            dragCandidate = marqueeCandidate = false;
            interactionContext = null;
            selectionContext = next;
        }
        var shared=ActorKeyframeSelection.getInstance().snapshot(model);
        if(observedSelectionRevision!=ActorKeyframeSelection.getInstance().revision()) {
            dragCandidate=marqueeCandidate=false; interactionContext=null; timePanel.cancel();
        }
        observedSelectionRevision=ActorKeyframeSelection.getInstance().revision();
        selection.clear(); selectedAssetId=null;
        if(shared!=null && renderContext!=null && shared.owner()==renderContext.track() && shared.clip()==clip && shared.asset()==asset) {
            for(var key:shared.keys()) selection.add(new KeyId(key.boneId(),Channel.valueOf(key.channel().name()),key.time()));
            if(!selection.isEmpty()) selectedAssetId=asset.id();
        }
        importedKeys=assetKeys();
        if (asset == null) {
            ImGui.textDisabled(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.unavailable"));
            return;
        }
        if (!canWrite()) ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.unavailable"));
        ImGui.text("资产: " + asset.name());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("%.2fs", asset.duration()));
        if (asset.boneTracks().isEmpty()) {
            ImGui.textDisabled("旋转骨骼后将在这里显示关键帧");
        }

        ImGui.textDisabled(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.time_mapping",
                model.getCurrentTime(),renderContext.assetTime(),clip.getSpeed()));
        if(clip.isLoop() && asset.loopMode()!=com.director.actor.animation.asset.AnimationLoopMode.ONCE)
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.loop_seek_hint"));
        renderCanvas(model, clip, asset);

        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (entry.getValue().translation().isEmpty()) continue;
            if (ImGui.treeNode(Icons.MOVE + " " + entry.getKey() + " · 位移##dopeTranslation:" + asset.id())) {
                for (var frame : entry.getValue().translation().keyframes()) {
                    KeyId key = new KeyId(entry.getKey(), Channel.TRANSLATION, frame.time());
                    boolean selected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(key);
                    if (ImGui.selectable(String.format("%s %.3fs  [%s]##dopeTranslation:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), entry.getKey(), frame.time()), selected)) {
                        select(asset.id(), key, ImGui.getIO().getKeyCtrl());
                        seekToKey(model,clip,frame.time());
                    }
                }
                ImGui.treePop();
            }
        }

        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (entry.getValue().scale().isEmpty()) continue;
            if (ImGui.treeNode(Icons.SCALE + " " + entry.getKey() + " · 缩放##dopeScale:" + asset.id())) {
                for (var frame : entry.getValue().scale().keyframes()) {
                    KeyId key = new KeyId(entry.getKey(), Channel.SCALE, frame.time());
                    boolean selected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(key);
                    if (ImGui.selectable(String.format("%s %.3fs  [%s]##dopeScale:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), entry.getKey(), frame.time()), selected)) {
                        select(asset.id(), key, ImGui.getIO().getKeyCtrl());
                        seekToKey(model,clip,frame.time());
                    }
                }
                ImGui.treePop();
            }
        }

        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (entry.getValue().rotation().isEmpty()) continue;
            String boneId = entry.getKey();
            if (ImGui.treeNode(boneId + "##dopeBone:" + asset.id())) {
                for (ChannelKeyframe<Quaternionf> frame : entry.getValue().rotation().keyframes()) {
                    KeyId key = new KeyId(boneId, Channel.ROTATION, frame.time());
                    boolean selected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(key);
                    String label = String.format("%s %.3fs  [%s]##dope:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), boneId, frame.time());
                    if (ImGui.selectable(label, selected)) {
                        select(asset.id(), key, ImGui.getIO().getKeyCtrl());
                        seekToKey(model,clip,frame.time());
                    }
                }
                ImGui.treePop();
            }
        }

        renderContext = ActorAnimationEditContext.capture(model, renderContext.track(), clip);
        pruneSelection(asset);
        int selectedCount = selection.size();
        float localFrame = frameStep(clip.getSpeed(),model.getFrameRate());
        if (selectedCount > 0) {
            ImGui.textDisabled("已选择 " + selectedCount + " 个关键帧（Ctrl+点击多选）");
            ImGui.beginDisabled(!canMove() || isKeyInteractionActive());
            if (ImGui.smallButton(Icons.ARROW_LEFT + " 1帧##dopePrevious")) {
                if (withinDuration(-localFrame, asset.duration())
                        && moveSelection(-localFrame)) {
                    shiftSelection(-localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.ARROW_RIGHT + " 1帧##dopeNext")) {
                if (withinDuration(localFrame, asset.duration())
                        && moveSelection(localFrame)) {
                    shiftSelection(localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            ImGui.endDisabled();
            if (ImGui.smallButton(Icons.COPY + " 复制##dopeCopy")) copySelection(asset);
            ImGui.sameLine();
            ImGui.beginDisabled(!canWrite() || isKeyInteractionActive());
            if (ImGui.smallButton(Icons.FLIP_HORIZONTAL + " 镜像##dopeMirrorSelected")) {
                mirrorAt(model, clip, asset, selection.stream().map(KeyId::time).distinct().toList());
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.DELETE + " 删除##dopeDelete")) {
                boolean changed = ActorAnimationAuthoringService.delete(renderContext, assetKeys());
                if (changed) clearSelection();
            }
            ImGui.endDisabled();
        }
        if(!selection.isEmpty()) {
            Float delta=timePanel.render(renderContext,assetKeys(),canWrite() && !isKeyInteractionActive());
            if(delta!=null) {
                var old=new ArrayList<>(selection); selection.clear();
                for(var key:old) selection.add(new KeyId(key.boneId(),key.channel(),key.time()+delta));
                timePanel.cancel();
            }
            ActorKeyframeInterpolationPanel.render(renderContext,assetKeys(),canWrite() && !isKeyInteractionActive());
        } else timePanel.cancel();
        if (keyClipboard.size()>0) {
            ImGui.beginDisabled(!canWrite() || isKeyInteractionActive() || !keyClipboard.compatible(asset));
            if (ImGui.smallButton(Icons.CLIPBOARD + " 粘贴到播放头##dopePaste")) {
                float localTime = renderContext.assetTime();
                pasteAt(model, asset, Math.min(asset.duration(), localTime));
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.textDisabled(keyClipboard.size() + " 个");
        }
        ImGui.beginDisabled(!canWrite() || isKeyInteractionActive());
        if (selectedCount == 0 && ImGui.smallButton(Icons.FLIP_HORIZONTAL + " 镜像播放头姿态##dopeMirrorPlayhead")) {
            float localTime = renderContext.assetTime();
            mirrorAt(model, clip, asset, List.of(Math.min(asset.duration(), localTime)));
        }
        ImGui.endDisabled();
    }

    private static void renderCanvas(TimelineModel model, EntityAnimationClip clip, AnimationAsset asset) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (!entry.getValue().translation().isEmpty()) rows.add(new Row(entry.getKey(), entry.getValue(), Channel.TRANSLATION));
            if (!entry.getValue().scale().isEmpty()) rows.add(new Row(entry.getKey(), entry.getValue(), Channel.SCALE));
            if (!entry.getValue().rotation().isEmpty()) rows.add(new Row(entry.getKey(), entry.getValue(), Channel.ROTATION));
        }
        if (rows.isEmpty()) return;

        ViewState view = views.computeIfAbsent(asset.id(), ignored -> new ViewState());
        clampView(view, asset.duration());
        if (ImGui.smallButton(Icons.ZOOM_IN + "##dopeZoomIn")) zoomView(view, asset.duration(), view.zoom * 1.5f, 0.5f);
        ImGui.sameLine();
        if (ImGui.smallButton(Icons.ZOOM_OUT + "##dopeZoomOut")) zoomView(view, asset.duration(), view.zoom / 1.5f, 0.5f);
        ImGui.sameLine();
        if (ImGui.smallButton(Icons.FIT_SCREEN + " 适配##dopeFit")) {
            view.zoom = 1f;
            view.startTime = 0f;
        }
        ImGui.sameLine();
        ImGui.textDisabled(String.format("%.1fx  %.2f–%.2fs", view.zoom, view.startTime,
                view.startTime + visibleDuration(asset.duration(), view.zoom)));

        final float labelWidth = 72f;
        final float rulerHeight = 18f;
        final float rowHeight = 22f;
        final float keyRadius = 5f;
        float width = Math.max(140f, ImGui.getContentRegionAvailX());
        float height = rulerHeight + rows.size() * rowHeight;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float timeX = x + labelWidth;
        float timeWidth = Math.max(1f, width - labelWidth);
        ImGui.invisibleButton("##actorDopeCanvas:" + asset.id(), width, height);
        boolean hovered = ImGui.isItemHovered();
        handleNavigation(view, asset.duration(), hovered, timeX, timeWidth);
        float visibleDuration = visibleDuration(asset.duration(), view.zoom);
        float pixelsPerSecond = timeWidth / visibleDuration;
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x, y, x + width, y + height, TimelineColors.rgba(24, 27, 32, 255), 3f);
        draw.addLine(timeX, y, timeX, y + height, TimelineColors.rgba(75, 80, 90, 255), 1f);

        for (int tick = 0; tick <= 4; tick++) {
            float tx = timeX + timeWidth * tick / 4f;
            draw.addLine(tx, y, tx, y + height, TimelineColors.rgba(65, 70, 80, 120), 1f);
            draw.addText(tx + 2f, y + 2f, TimelineColors.rgba(165, 170, 180, 255),
                    String.format("%.1f", view.startTime + visibleDuration * tick / 4f));
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            float cy = y + rulerHeight + rowIndex * rowHeight + rowHeight * 0.5f;
            draw.addLine(x, y + rulerHeight + (rowIndex + 1) * rowHeight,
                    x + width, y + rulerHeight + (rowIndex + 1) * rowHeight,
                    TimelineColors.rgba(55, 60, 68, 180), 1f);
            draw.addText(x + 4f, cy - 7f, TimelineColors.rgba(200, 205, 215, 255),
                    row.boneId() + switch (row.channel()) {
                        case TRANSLATION -> " · T";
                        case SCALE -> " · S";
                        case ROTATION -> " · R";
                    });
            draw.pushClipRect(timeX,y,timeX+timeWidth,y+height,true);
            try {
            for (float frameTime : frameTimes(row)) {
                float kx = timeX + (frameTime - view.startTime) * pixelsPerSecond;
                if (kx < timeX - keyRadius || kx > timeX + timeWidth + keyRadius) continue;
                boolean selected = Objects.equals(selectedAssetId, asset.id())
                        && selection.contains(new KeyId(row.boneId(), row.channel(), frameTime));
                int color = selected ? TimelineColors.rgba(255, 190, 70, 255)
                        : row.channel() == Channel.TRANSLATION ? TimelineColors.rgba(100, 220, 150, 255)
                        : row.channel() == Channel.SCALE ? TimelineColors.rgba(200, 120, 255, 255)
                        : TimelineColors.rgba(100, 175, 255, 255);
                draw.addRectFilled(kx - keyRadius, cy - keyRadius, kx + keyRadius, cy + keyRadius, color, 1f);
            }
            } finally { draw.popClipRect(); }
        }

        float playheadLocal = renderContext.assetTime();
        float playheadX = timeX + (playheadLocal - view.startTime) * pixelsPerSecond;
        if (playheadX >= timeX && playheadX <= timeX + timeWidth) {
            draw.addLine(playheadX, y, playheadX, y + height, TimelineColors.rgba(255, 90, 80, 230), 1.5f);
        }

        handleCanvasInput(model, clip, asset, rows, x, y, timeX, timeWidth,
                rulerHeight, rowHeight, keyRadius, pixelsPerSecond, view.startTime, hovered, draw);
    }

    private static void handleCanvasInput(TimelineModel model, EntityAnimationClip clip, AnimationAsset asset,
                                          List<Row> rows, float x, float y, float timeX, float timeWidth,
                                          float rulerHeight, float rowHeight, float keyRadius,
                                          float pixelsPerSecond, float viewStartTime,
                                          boolean hovered, ImDrawList draw) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        var viewport=new ActorDopeSheetViewport(x,y,timeX,timeWidth,rulerHeight+rows.size()*rowHeight,
                pixelsPerSecond,viewStartTime,model.getFrameRate(),rows);
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && !ActorBoneGizmoController.getInstance().isDragging() && !ActorBonePropertiesPanel.hasNumericDraft()) {
            com.director.ui.timeline.render.ActorBoneTimelineRows.cancelAllKeyInteractions();
            ActorKeyframeTimePanel.cancelAll();
            model.setSelectedClip(clip.getTimelineClip());
            renderContext=ActorAnimationEditContext.capture(model,renderContext.track(),clip);
            KeyId hit = hitKey(rows, mx, my, y, timeX, timeWidth, rulerHeight, rowHeight,
                    keyRadius, pixelsPerSecond, viewStartTime);
            interactionStartX = interactionCurrentX = mx;
            interactionStartY = interactionCurrentY = my;
            interactionAdditive = ImGui.getIO().getKeyCtrl();
            interactionAssetId = asset.id();
            interactionFrameRate=model.getFrameRate();
            interactionViewport=viewport;
            if (hit != null) {
                boolean alreadySelected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(hit);
                if (!alreadySelected || interactionAdditive) select(asset.id(), hit, interactionAdditive);
                dragCandidate = canMove();
                marqueeCandidate = false;
                seekToKey(model,clip,hit.time());
                renderContext = ActorAnimationEditContext.capture(model, renderContext.track(), clip);
                interactionContext = renderContext;
                interactionSource = selectedFrames(asset);
            } else {
                dragCandidate = false;
                marqueeCandidate = canWrite();
                interactionContext=renderContext;
            }
        }
        if (!Objects.equals(interactionAssetId, asset.id())) return;
        if((dragCandidate || marqueeCandidate) && !ImGui.isMouseDown(ImGuiMouseButton.Left)
                && !ImGui.isMouseReleased(ImGuiMouseButton.Left)) cancelKeyInteraction();
        if ((dragCandidate || marqueeCandidate) && (interactionContext == null || !interactionContext.isCurrent()
                || !canWrite() || interactionViewport==null || !interactionViewport.matches(viewport)
                || model.getFrameRate()!=interactionFrameRate || (dragCandidate && !interactionSource.equals(selectedFrames(asset)))
                || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape))) {
            cancelKeyInteraction();
        }
        if ((ImGui.isMouseDown(ImGuiMouseButton.Left) || ImGui.isMouseReleased(ImGuiMouseButton.Left))
                && (dragCandidate || marqueeCandidate)) {
            interactionCurrentX = mx;
            interactionCurrentY = my;
        }
        float dx = interactionCurrentX - interactionStartX;
        float dy = interactionCurrentY - interactionStartY;
        boolean activated = dx * dx + dy * dy >= 16f;
        draw.pushClipRect(timeX,y,timeX+timeWidth,y+rulerHeight+rows.size()*rowHeight,true);
        try {
        if (dragCandidate && activated) {
            float localFrame = frameStep(clip.getSpeed(),model.getFrameRate());
            float delta = snapDragDelta(dx, pixelsPerSecond, localFrame);
            for (KeyId key : selection) {
                float ghostX = timeX + (key.time() + delta - viewStartTime) * pixelsPerSecond;
                int row = rowIndex(rows, key.boneId(), key.channel());
                if (row >= 0) {
                    float cy = y + rulerHeight + row * rowHeight + rowHeight * 0.5f;
                    draw.addRect(ghostX - keyRadius, cy - keyRadius, ghostX + keyRadius, cy + keyRadius,
                            TimelineColors.rgba(255, 215, 110, 230), 1f, 0, 2f);
                }
            }
        } else if (marqueeCandidate && activated) {
            float x1 = Math.min(interactionStartX, interactionCurrentX);
            float y1 = Math.min(interactionStartY, interactionCurrentY);
            float x2 = Math.max(interactionStartX, interactionCurrentX);
            float y2 = Math.max(interactionStartY, interactionCurrentY);
            draw.addRectFilled(x1, y1, x2, y2, TimelineColors.rgba(80, 160, 255, 35), 2f);
            draw.addRect(x1, y1, x2, y2, TimelineColors.rgba(80, 160, 255, 190), 2f, 0, 1.5f);
        }
        } finally { draw.popClipRect(); }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left) && (dragCandidate || marqueeCandidate)) {
            if(!hovered) { cancelKeyInteraction(); return; }
            if (dragCandidate && activated) finishDrag(model, clip, asset, pixelsPerSecond, dx);
            else if (marqueeCandidate && activated) finishMarquee(asset, rows, y, timeX, rulerHeight,
                    rowHeight, pixelsPerSecond, viewStartTime);
            else if (marqueeCandidate && !interactionAdditive) clearSelection();
            dragCandidate = false;
            marqueeCandidate = false;
            interactionAssetId = null;
        }
    }

    private static void finishDrag(TimelineModel model, EntityAnimationClip clip, AnimationAsset asset,
                                   float pixelsPerSecond, float dx) {
        float localFrame = frameStep(clip.getSpeed(),model.getFrameRate());
        float delta = snapDragDelta(dx, pixelsPerSecond, localFrame);
        if (withinDuration(delta, asset.duration())
                && moveSelection(delta)) {
            shiftSelection(delta);
            seekToFirst(model, clip);
        }
    }

    private static void finishMarquee(AnimationAsset asset, List<Row> rows, float y, float timeX,
                                      float rulerHeight, float rowHeight, float pixelsPerSecond,
                                      float viewStartTime) {
        if (!interactionAdditive || !Objects.equals(selectedAssetId, asset.id())) selection.clear();
        selectedAssetId = asset.id();
        float x1 = Math.min(interactionStartX, interactionCurrentX);
        float x2 = Math.max(interactionStartX, interactionCurrentX);
        float y1 = Math.min(interactionStartY, interactionCurrentY);
        float y2 = Math.max(interactionStartY, interactionCurrentY);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            float cy = y + rulerHeight + rowIndex * rowHeight + rowHeight * 0.5f;
            if (cy < y1 || cy > y2) continue;
            for (float frameTime : frameTimes(row)) {
                float kx = timeX + (frameTime - viewStartTime) * pixelsPerSecond;
                if (kx >= x1 && kx <= x2) selection.add(new KeyId(row.boneId(), row.channel(), frameTime));
            }
        }
        if (selection.isEmpty()) selectedAssetId = null;
    }

    private static KeyId hitKey(List<Row> rows, float mx, float my, float y, float timeX, float timeWidth,
                                float rulerHeight, float rowHeight, float radius, float pixelsPerSecond,
                                float viewStartTime) {
        if (mx < timeX || mx > timeX + timeWidth) return null;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            float cy = y + rulerHeight + rowIndex * rowHeight + rowHeight * 0.5f;
            if (Math.abs(my - cy) > radius + 3f) continue;
            for (float frameTime : frameTimes(row)) {
                if (Math.abs(mx - (timeX + (frameTime - viewStartTime) * pixelsPerSecond)) <= radius + 3f)
                    return new KeyId(row.boneId(), row.channel(), frameTime);
            }
        }
        return null;
    }

    private static int rowIndex(List<Row> rows, String boneId, Channel channel) {
        for (int i = 0; i < rows.size(); i++) if (Objects.equals(rows.get(i).boneId(), boneId)
                && rows.get(i).channel() == channel) return i;
        return -1;
    }

    static float frameStep(float speed,float frameRate) {
        if(!Float.isFinite(speed) || !Float.isFinite(frameRate) || frameRate<=0) return 0;
        return Math.max(.01f,speed)/frameRate;
    }
    static float snapDragDelta(float pixelDelta, float pixelsPerSecond, float localFrame) {
        if (!Float.isFinite(pixelDelta) || !Float.isFinite(pixelsPerSecond) || !Float.isFinite(localFrame)
                || pixelsPerSecond <= 0f || localFrame <= 0f) return 0f;
        double result=Math.floor(pixelDelta/(double)pixelsPerSecond/localFrame+.5)*localFrame;
        float delta=(float)result;
        return Float.isFinite(delta)?delta:0;
    }

    private static void handleNavigation(ViewState view, float duration, boolean hovered,
                                         float timeX, float timeWidth) {
        if (!hovered || dragCandidate || marqueeCandidate) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (Math.abs(wheel) < 0.01f) return;
        if (ImGui.getIO().getKeyCtrl()) {
            float anchor = Math.clamp((ImGui.getMousePosX() - timeX) / timeWidth, 0f, 1f);
            zoomView(view, duration, view.zoom * (wheel > 0f ? 1.25f : 0.8f), anchor);
        } else if (ImGui.getIO().getKeyShift()) {
            view.startTime -= wheel * visibleDuration(duration, view.zoom) * 0.1f;
            clampView(view, duration);
        }
    }

    static float visibleDuration(float duration, float zoom) {
        return Math.max(0.001f, duration) / Math.max(1f, zoom);
    }

    static float zoomedStart(float oldStart, float duration, float oldZoom, float newZoom, float anchor) {
        float safeAnchor = Math.clamp(anchor, 0f, 1f);
        float pivot = oldStart + visibleDuration(duration, oldZoom) * safeAnchor;
        float result = pivot - visibleDuration(duration, newZoom) * safeAnchor;
        float max = Math.max(0f, duration - visibleDuration(duration, newZoom));
        return Math.max(0f, Math.min(max, result));
    }

    private static void zoomView(ViewState view, float duration, float newZoom, float anchor) {
        float clampedZoom = Math.clamp(newZoom, 1f, 50f);
        view.startTime = zoomedStart(view.startTime, duration, view.zoom, clampedZoom, anchor);
        view.zoom = clampedZoom;
        clampView(view, duration);
    }

    private static void clampView(ViewState view, float duration) {
        view.zoom = Math.clamp(view.zoom, 1f, 50f);
        float max = Math.max(0f, duration - visibleDuration(duration, view.zoom));
        view.startTime = Math.clamp(view.startTime, 0f, max);
    }

    private static void seekToKey(TimelineModel model,EntityAnimationClip clip,float localTime) {
        var asset=AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if(asset==null) return;
        var mode=clip.isLoop()?asset.loopMode():com.director.actor.animation.asset.AnimationLoopMode.ONCE;
        com.director.ui.timeline.render.ActorKeyframeProjection.nearest(localTime,asset.duration(),mode,
                clip.getStartTime(),clip.getEndTime(),clip.getSpeed(),model.getCurrentTime()).ifPresent(model::setCurrentTime);
    }
    private static boolean canMove() {
        return canWrite() && (!renderContext.loop() || renderContext.loopMode()==com.director.actor.animation.asset.AnimationLoopMode.ONCE);
    }

    private static void select(String assetId, KeyId key, boolean additive) {
        renderContext.model().setSelectedClip(renderContext.clip().getTimelineClip());
        renderContext=ActorAnimationEditContext.capture(renderContext.model(),renderContext.track(),renderContext.clip());
        if (!Objects.equals(selectedAssetId, assetId) || !additive) selection.clear();
        selectedAssetId = assetId;
        if (additive && selection.contains(key)) selection.remove(key); else selection.add(key);
    }

    private static void clearSelection() {
        selectedAssetId = null;
        selection.clear();
    }

    private static void pruneSelection(AnimationAsset asset) {
        if (!Objects.equals(selectedAssetId, asset.id())) return;
        selection.removeIf(key -> asset.findBoneTrack(key.boneId()).isEmpty() || switch (key.channel()) {
            case ROTATION -> asset.findBoneTrack(key.boneId()).orElseThrow().rotation().findAt(key.time()).isEmpty();
            case TRANSLATION -> asset.findBoneTrack(key.boneId()).orElseThrow().translation().findAt(key.time()).isEmpty();
            case SCALE -> asset.findBoneTrack(key.boneId()).orElseThrow().scale().findAt(key.time()).isEmpty();
        });
        if (selection.isEmpty()) selectedAssetId = null;
    }

    private static boolean moveSelection(float delta) {
        var target=interactionContext!=null && dragCandidate?interactionContext:renderContext;
        var keys=assetKeys();
        return canMove() && ActorAnimationAuthoringService.moveTime(target,keys,
                keys.size()==1?keys.getFirst().time()+delta:delta);
    }

    private static boolean withinDuration(float delta, float duration) {
        if(!canMove()) return false;
        duration=Math.min(duration,(renderContext.end()-renderContext.start())*Math.max(.01f,renderContext.speed()));
        for (KeyId key : selection) {
            float target = key.time() + delta;
            if (target < 0f || target > duration) return false;
        }
        return true;
    }

    private static void shiftSelection(float delta) {
        Set<KeyId> shifted = new LinkedHashSet<>();
        for (KeyId key : selection) shifted.add(new KeyId(key.boneId(), key.channel(), key.time() + delta));
        selection.clear();
        selection.addAll(shifted);
    }

    private static void seekToFirst(TimelineModel model, EntityAnimationClip clip) {
        if (!selection.isEmpty()) seekToKey(model,clip,selection.iterator().next().time());
    }

    private static void copySelection(AnimationAsset asset) {
        keyClipboard.copy(asset,assetKeys());
    }

    private static void pasteAt(TimelineModel model, AnimationAsset asset, float origin) {
        float limit=Math.min(asset.duration(),(renderContext.end()-renderContext.start())*Math.max(.01f,renderContext.speed()));
        var keys=keyClipboard.at(asset,origin,limit);
        if (canWrite() && ActorAnimationAuthoringService.paste(renderContext, keys)) {
            selectedAssetId=asset.id(); selection.clear();
            for(var key:keys) selection.add(new KeyId(key.boneId(),Channel.valueOf(key.channel().name()),key.time()));
        }
    }

    private static void mirrorAt(TimelineModel model, EntityAnimationClip clip,
                                 AnimationAsset asset, List<Float> times) {
        if (canWrite() && ActorAnimationAuthoringService.mirror(renderContext, times,
                ActorAnimationAuthoringSettings.getInstance().getInterpolation())) {
            selectedAssetId = asset.id();
            selection.clear();
            String[] bones = {
                    com.director.actor.rig.skeleton.HumanoidRig.BODY,
                    com.director.actor.rig.skeleton.HumanoidRig.HEAD,
                    com.director.actor.rig.skeleton.HumanoidRig.LEFT_ARM,
                    com.director.actor.rig.skeleton.HumanoidRig.RIGHT_ARM,
                    com.director.actor.rig.skeleton.HumanoidRig.LEFT_LEG,
                    com.director.actor.rig.skeleton.HumanoidRig.RIGHT_LEG
            };
            for (float time : times) for (String bone : bones) selection.add(new KeyId(bone, Channel.ROTATION, time));
            seekToFirst(model, clip);
        }
    }

    private static List<Float> frameTimes(Row row) {
        return switch (row.channel()) {
            case ROTATION -> row.track().rotation().keyframes().stream().map(ChannelKeyframe::time).toList();
            case TRANSLATION -> row.track().translation().keyframes().stream().map(ChannelKeyframe::time).toList();
            case SCALE -> row.track().scale().keyframes().stream().map(ChannelKeyframe::time).toList();
        };
    }

    private enum Channel { ROTATION, TRANSLATION, SCALE }
    private record KeyId(String boneId, Channel channel, float time) {}
    private record Row(String boneId, BoneTrack track, Channel channel) {}
    private static final class ViewState {
        private float zoom = 1f;
        private float startTime;
    }

}
