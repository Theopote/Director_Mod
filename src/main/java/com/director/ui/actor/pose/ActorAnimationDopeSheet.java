package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.theme.Icons;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    private static final Set<KeyId> selection = new LinkedHashSet<>();
    private static final List<CopiedKey> clipboard = new ArrayList<>();
    private static final List<CopiedTranslationKey> translationClipboard = new ArrayList<>();
    private static boolean dragCandidate;
    private static boolean marqueeCandidate;
    private static boolean interactionAdditive;
    private static float interactionStartX;
    private static float interactionStartY;
    private static float interactionCurrentX;
    private static float interactionCurrentY;
    private static String interactionAssetId;
    private static final Map<String, ViewState> views = new HashMap<>();

    private ActorAnimationDopeSheet() {}

    public static void render(TimelineModel model, UUID actorId) {
        ActorTrack actorTrack = findActorTrack(model, actorId);
        EntityAnimationClip clip = findActiveClip(actorTrack, model == null ? 0f : model.getCurrentTime());
        AnimationAsset asset = clip == null ? null
                : AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if (model == null || asset == null) {
            ImGui.textDisabled("当前播放头没有可编辑的关键帧动画");
            return;
        }

        ImGui.text("资产: " + asset.name());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("%.2fs", asset.duration()));
        if (asset.boneTracks().isEmpty()) {
            ImGui.textDisabled("旋转骨骼后将在这里显示关键帧");
            return;
        }

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
                        model.setCurrentTime(toTimelineTime(clip, frame.time()));
                    }
                }
                ImGui.treePop();
            }
        }

        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (entry.getValue().scale().isEmpty()) continue;
            if (ImGui.treeNode(Icons.SCALE + " " + entry.getKey() + " · 缩放##dopeScale:" + asset.id())) {
                for (var frame : entry.getValue().scale().keyframes()) {
                    if (ImGui.selectable(String.format("%s %.3fs  [%s]##dopeScale:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), entry.getKey(), frame.time()), false)) {
                        model.setCurrentTime(toTimelineTime(clip, frame.time()));
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
                        model.setCurrentTime(toTimelineTime(clip, frame.time()));
                    }
                }
                ImGui.treePop();
            }
        }

        pruneSelection(asset);
        List<ActorAnimationKeyframeEditing.RotationKey> selectedKeys = selectedKeys(asset);
        List<ActorAnimationKeyframeEditing.TranslationKey> selectedTranslations = selectedTranslationKeys(asset);
        int selectedCount = selectedKeys.size() + selectedTranslations.size();
        float localFrame = Math.max(0.01f, clip.getSpeed()) / Math.max(1f, model.getFrameRate());
        if (selectedCount > 0) {
            ImGui.textDisabled("已选择 " + selectedCount + " 个关键帧（Ctrl+点击多选）");
            if (ImGui.smallButton(Icons.ARROW_LEFT + " 1帧##dopePrevious")) {
                if (withinDuration(-localFrame, asset.duration())
                        && moveSelection(model, selectedKeys, selectedTranslations, -localFrame)) {
                    shiftSelection(-localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.ARROW_RIGHT + " 1帧##dopeNext")) {
                if (withinDuration(localFrame, asset.duration())
                        && moveSelection(model, selectedKeys, selectedTranslations, localFrame)) {
                    shiftSelection(localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.COPY + " 复制##dopeCopy")) copySelection(asset);
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.FLIP_HORIZONTAL + " 镜像##dopeMirrorSelected")) {
                mirrorAt(model, clip, asset, selection.stream().map(KeyId::time).distinct().toList());
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.DELETE + " 删除##dopeDelete")) {
                boolean changed = !selectedTranslations.isEmpty()
                        ? ActorAnimationKeyframeEditing.deleteTranslations(model, selectedTranslations)
                        : ActorAnimationKeyframeEditing.deleteRotations(model, selectedKeys);
                if (changed) clearSelection();
            }
        }
        if (!clipboard.isEmpty() || !translationClipboard.isEmpty()) {
            if (ImGui.smallButton(Icons.CLIPBOARD + " 粘贴到播放头##dopePaste")) {
                float localTime = Math.max(0f, (model.getCurrentTime() - clip.getStartTime())
                        * Math.max(0.01f, clip.getSpeed()));
                pasteAt(model, asset, Math.min(asset.duration(), localTime));
            }
            ImGui.sameLine();
            ImGui.textDisabled((clipboard.size() + translationClipboard.size()) + " 个");
        }
        if (selectedCount == 0 && ImGui.smallButton(Icons.FLIP_HORIZONTAL + " 镜像播放头姿态##dopeMirrorPlayhead")) {
            float localTime = Math.max(0f, (model.getCurrentTime() - clip.getStartTime())
                    * Math.max(0.01f, clip.getSpeed()));
            mirrorAt(model, clip, asset, List.of(Math.min(asset.duration(), localTime)));
        }
    }

    private static void renderCanvas(TimelineModel model, EntityAnimationClip clip, AnimationAsset asset) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (!entry.getValue().translation().isEmpty()) rows.add(new Row(entry.getKey(), entry.getValue(), Channel.TRANSLATION));
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
                    row.boneId() + (row.channel() == Channel.TRANSLATION ? " · T" : " · R"));
            for (float frameTime : frameTimes(row)) {
                float kx = timeX + (frameTime - view.startTime) * pixelsPerSecond;
                if (kx < timeX - keyRadius || kx > timeX + timeWidth + keyRadius) continue;
                boolean selected = Objects.equals(selectedAssetId, asset.id())
                        && selection.contains(new KeyId(row.boneId(), row.channel(), frameTime));
                int color = selected ? TimelineColors.rgba(255, 190, 70, 255)
                        : row.channel() == Channel.TRANSLATION ? TimelineColors.rgba(100, 220, 150, 255)
                        : TimelineColors.rgba(100, 175, 255, 255);
                draw.addRectFilled(kx - keyRadius, cy - keyRadius, kx + keyRadius, cy + keyRadius, color, 1f);
            }
        }

        float playheadLocal = Math.max(0f, (model.getCurrentTime() - clip.getStartTime())
                * Math.max(0.01f, clip.getSpeed()));
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
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            KeyId hit = hitKey(rows, mx, my, y, timeX, timeWidth, rulerHeight, rowHeight,
                    keyRadius, pixelsPerSecond, viewStartTime);
            interactionStartX = interactionCurrentX = mx;
            interactionStartY = interactionCurrentY = my;
            interactionAdditive = ImGui.getIO().getKeyCtrl();
            interactionAssetId = asset.id();
            if (hit != null) {
                boolean alreadySelected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(hit);
                if (!alreadySelected || interactionAdditive) select(asset.id(), hit, interactionAdditive);
                dragCandidate = true;
                marqueeCandidate = false;
                model.setCurrentTime(toTimelineTime(clip, hit.time()));
            } else {
                dragCandidate = false;
                marqueeCandidate = true;
            }
        }
        if (!Objects.equals(interactionAssetId, asset.id())) return;
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) && (dragCandidate || marqueeCandidate)) {
            interactionCurrentX = mx;
            interactionCurrentY = my;
        }
        float dx = interactionCurrentX - interactionStartX;
        float dy = interactionCurrentY - interactionStartY;
        boolean activated = dx * dx + dy * dy >= 16f;
        if (dragCandidate && activated) {
            float localFrame = Math.max(0.01f, clip.getSpeed()) / Math.max(1f, model.getFrameRate());
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
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left) && (dragCandidate || marqueeCandidate)) {
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
        float localFrame = Math.max(0.01f, clip.getSpeed()) / Math.max(1f, model.getFrameRate());
        float delta = snapDragDelta(dx, pixelsPerSecond, localFrame);
        List<ActorAnimationKeyframeEditing.RotationKey> keys = selectedKeys(asset);
        List<ActorAnimationKeyframeEditing.TranslationKey> translations = selectedTranslationKeys(asset);
        if (withinDuration(delta, asset.duration())
                && moveSelection(model, keys, translations, delta)) {
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

    static float snapDragDelta(float pixelDelta, float pixelsPerSecond, float localFrame) {
        if (!Float.isFinite(pixelDelta) || pixelsPerSecond <= 0f || localFrame <= 0f) return 0f;
        return Math.round((pixelDelta / pixelsPerSecond) / localFrame) * localFrame;
    }

    private static void handleNavigation(ViewState view, float duration, boolean hovered,
                                         float timeX, float timeWidth) {
        if (!hovered || dragCandidate || marqueeCandidate) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (Math.abs(wheel) < 0.01f) return;
        if (ImGui.getIO().getKeyCtrl()) {
            float anchor = Math.max(0f, Math.min(1f, (ImGui.getMousePosX() - timeX) / timeWidth));
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
        float safeAnchor = Math.max(0f, Math.min(1f, anchor));
        float pivot = oldStart + visibleDuration(duration, oldZoom) * safeAnchor;
        float result = pivot - visibleDuration(duration, newZoom) * safeAnchor;
        float max = Math.max(0f, duration - visibleDuration(duration, newZoom));
        return Math.max(0f, Math.min(max, result));
    }

    private static void zoomView(ViewState view, float duration, float newZoom, float anchor) {
        float clampedZoom = Math.max(1f, Math.min(50f, newZoom));
        view.startTime = zoomedStart(view.startTime, duration, view.zoom, clampedZoom, anchor);
        view.zoom = clampedZoom;
        clampView(view, duration);
    }

    private static void clampView(ViewState view, float duration) {
        view.zoom = Math.max(1f, Math.min(50f, view.zoom));
        float max = Math.max(0f, duration - visibleDuration(duration, view.zoom));
        view.startTime = Math.max(0f, Math.min(max, view.startTime));
    }

    private static float toTimelineTime(EntityAnimationClip clip, float localTime) {
        return clip.getStartTime() + localTime / Math.max(0.01f, clip.getSpeed());
    }

    private static void select(String assetId, KeyId key, boolean additive) {
        if (!Objects.equals(selectedAssetId, assetId) || !additive
                || (!selection.isEmpty() && selection.iterator().next().channel() != key.channel())) selection.clear();
        selectedAssetId = assetId;
        if (additive && selection.contains(key)) selection.remove(key); else selection.add(key);
    }

    private static void clearSelection() {
        selectedAssetId = null;
        selection.clear();
    }

    private static void pruneSelection(AnimationAsset asset) {
        if (!Objects.equals(selectedAssetId, asset.id())) return;
        selection.removeIf(key -> asset.findBoneTrack(key.boneId()).isEmpty()
                || (key.channel() == Channel.ROTATION
                ? asset.findBoneTrack(key.boneId()).orElseThrow().rotation().findAt(key.time()).isEmpty()
                : asset.findBoneTrack(key.boneId()).orElseThrow().translation().findAt(key.time()).isEmpty()));
        if (selection.isEmpty()) selectedAssetId = null;
    }

    private static List<ActorAnimationKeyframeEditing.RotationKey> selectedKeys(AnimationAsset asset) {
        List<ActorAnimationKeyframeEditing.RotationKey> result = new ArrayList<>();
        if (!Objects.equals(selectedAssetId, asset.id())) return result;
        for (KeyId key : selection) if (key.channel() == Channel.ROTATION) asset.findBoneTrack(key.boneId()).ifPresent(
                track -> result.add(new ActorAnimationKeyframeEditing.RotationKey(track, key.time())));
        return result;
    }

    private static List<ActorAnimationKeyframeEditing.TranslationKey> selectedTranslationKeys(AnimationAsset asset) {
        List<ActorAnimationKeyframeEditing.TranslationKey> result = new ArrayList<>();
        if (!Objects.equals(selectedAssetId, asset.id())) return result;
        for (KeyId key : selection) if (key.channel() == Channel.TRANSLATION) asset.findBoneTrack(key.boneId()).ifPresent(
                track -> result.add(new ActorAnimationKeyframeEditing.TranslationKey(track, key.time())));
        return result;
    }

    private static boolean moveSelection(TimelineModel model,
                                         List<ActorAnimationKeyframeEditing.RotationKey> rotations,
                                         List<ActorAnimationKeyframeEditing.TranslationKey> translations,
                                         float delta) {
        return !translations.isEmpty()
                ? ActorAnimationKeyframeEditing.moveTranslations(model, translations, delta)
                : ActorAnimationKeyframeEditing.moveRotations(model, rotations, delta);
    }

    private static boolean withinDuration(float delta, float duration) {
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
        if (!selection.isEmpty()) model.setCurrentTime(toTimelineTime(clip, selection.iterator().next().time()));
    }

    private static void copySelection(AnimationAsset asset) {
        clipboard.clear();
        translationClipboard.clear();
        if (selection.isEmpty()) return;
        float origin = selection.stream().map(KeyId::time).min(Float::compare).orElse(0f);
        for (KeyId key : selection) asset.findBoneTrack(key.boneId()).ifPresent(track -> {
            if (key.channel() == Channel.ROTATION) track.rotation().findAt(key.time()).ifPresent(frame -> clipboard.add(
                    new CopiedKey(key.boneId(), frame.time() - origin, frame.value(), frame.interpolation())));
            else track.translation().findAt(key.time()).ifPresent(frame -> translationClipboard.add(
                    new CopiedTranslationKey(key.boneId(), frame.time() - origin, frame.value(), frame.interpolation())));
        });
    }

    private static void pasteAt(TimelineModel model, AnimationAsset asset, float origin) {
        List<ActorAnimationKeyframeEditing.RotationPaste> pasted = new ArrayList<>();
        List<ActorAnimationKeyframeEditing.TranslationPaste> pastedTranslations = new ArrayList<>();
        Set<KeyId> pastedIds = new LinkedHashSet<>();
        for (CopiedKey copied : clipboard) {
            float time = origin + copied.offset();
            if (time > asset.duration()) return;
            BoneTrack track;
            try {
                track = asset.getOrCreateBoneTrack(copied.boneId());
            } catch (IllegalArgumentException ignored) {
                return;
            }
            pasted.add(new ActorAnimationKeyframeEditing.RotationPaste(
                    track, time, copied.value(), copied.interpolation()));
            pastedIds.add(new KeyId(copied.boneId(), Channel.ROTATION, time));
        }
        for (CopiedTranslationKey copied : translationClipboard) {
            float time = origin + copied.offset();
            if (time > asset.duration()) return;
            BoneTrack track;
            try { track = asset.getOrCreateBoneTrack(copied.boneId()); }
            catch (IllegalArgumentException ignored) { return; }
            pastedTranslations.add(new ActorAnimationKeyframeEditing.TranslationPaste(
                    track, time, copied.value(), copied.interpolation()));
            pastedIds.add(new KeyId(copied.boneId(), Channel.TRANSLATION, time));
        }
        boolean changed = !pastedTranslations.isEmpty()
                ? ActorAnimationKeyframeEditing.pasteTranslations(model, pastedTranslations)
                : ActorAnimationKeyframeEditing.pasteRotations(model, pasted);
        if (changed) {
            selectedAssetId = asset.id();
            selection.clear();
            selection.addAll(pastedIds);
        }
    }

    private static void mirrorAt(TimelineModel model, EntityAnimationClip clip,
                                 AnimationAsset asset, List<Float> times) {
        if (ActorAnimationKeyframeEditing.mirrorHumanoidPoses(model, asset, times,
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
        return row.channel() == Channel.ROTATION
                ? row.track().rotation().keyframes().stream().map(ChannelKeyframe::time).toList()
                : row.track().translation().keyframes().stream().map(ChannelKeyframe::time).toList();
    }

    private enum Channel { ROTATION, TRANSLATION }
    private record KeyId(String boneId, Channel channel, float time) {}
    private record Row(String boneId, BoneTrack track, Channel channel) {}
    private static final class ViewState {
        private float zoom = 1f;
        private float startTime;
    }
    private record CopiedKey(String boneId, float offset, Quaternionf value,
                             com.director.actor.animation.asset.InterpolationMode interpolation) {
        private CopiedKey {
            value = new Quaternionf(value);
        }
    }
    private record CopiedTranslationKey(String boneId, float offset, Vector3f value,
                                        com.director.actor.animation.asset.InterpolationMode interpolation) {
        private CopiedTranslationKey { value = new Vector3f(value); }
    }

    private static ActorTrack findActorTrack(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return null;
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) continue;
            ActorTrack payload = track.getActorTrackPayload();
            if (payload != null && payload.getActorRef() != null
                    && Objects.equals(actorId, payload.getActorRef().getActorId())) return payload;
        }
        return null;
    }

    private static EntityAnimationClip findActiveClip(ActorTrack track, float time) {
        if (track == null) return null;
        EntityAnimationClip result = null;
        for (EntityClip candidate : track.getClips()) {
            if (candidate instanceof EntityAnimationClip animation && !animation.isMuted()
                    && time >= animation.getStartTime() && time <= animation.getEndTime()
                    && AnimationAssetLibrary.getInstance().contains(animation.getAnimationId())
                    && (result == null || animation.getStartTime() >= result.getStartTime())) result = animation;
        }
        return result;
    }
}
