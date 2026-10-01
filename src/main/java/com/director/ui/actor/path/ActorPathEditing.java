package com.director.ui.actor.path;

import com.director.actor.ActorRegistryManager;
import com.director.actor.path.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.ActorRouteSampler;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import java.util.*;

/** Path geometry transactions. Library definitions and route instances are separate targets. */
public final class ActorPathEditing {
    private ActorPathEditing() {}
    public record Geometry(List<Vector3f> points, ActorRouteClip.InterpolationMode mode) {
        public Geometry {
            if (points == null || mode == null || points.size() < 2 || points.size() > 2048
                    || points.stream().anyMatch(point -> point == null || !point.isFinite()))
                throw new IllegalArgumentException("路径需要 2–2048 个有效锚点");
            points = points.stream().map(Vector3f::new).toList();
        }
        public void apply(ActorPathDefinition path) { path.setWaypoints(points); path.setInterpolationMode(mode); }
        public void apply(ActorRouteClip route) { route.setWaypoints(points); route.setInterpolationMode(mode); }
        public boolean matches(List<Vector3f> current, ActorRouteClip.InterpolationMode currentMode) {
            return mode == currentMode && points.equals(current);
        }
    }

    public static final class Target {
        private final TimelineModel model;
        private final float time;
        private final TimelineTrack owner;
        private final ActorTrack payload;
        private final UUID actorId;
        private final ActorRouteClip route;
        private final ActorPathLibrary library;
        private final ActorPathDefinition path;
        private final Geometry before;
        private final float start, end, speed, jumpStart, jumpDuration, jumpHeight;
        private final ActorRouteClip.LoopMode loop;
        private final boolean align;
        private final long terrainRevision;
        private final Runnable save;

        private Target(TimelineModel model, TimelineTrack owner, ActorRouteClip route,
                       ActorPathLibrary library, ActorPathDefinition path, Runnable save) {
            this.model = Objects.requireNonNull(model); time = model.getCurrentTime();
            this.owner = owner; this.route = route; this.library = library; this.path = path; this.save = save;
            payload = owner == null ? null : owner.getActorTrackPayload();
            actorId = payload == null || payload.getActorRef() == null ? null : payload.getActorRef().getActorId();
            before = route == null ? new Geometry(path.getWaypoints(), path.getInterpolationMode())
                    : new Geometry(route.getWaypoints(), route.getInterpolationMode());
            start = route == null ? 0 : route.getStartTime(); end = route == null ? 0 : route.getEndTime();
            speed = route == null ? 0 : route.getSpeed(); loop = route == null ? null : route.getLoopMode();
            align = route != null && route.isAlignRotationToRoute();
            jumpStart = route == null ? 0 : route.getJumpStart(); jumpDuration = route == null ? 0 : route.getJumpDuration();
            jumpHeight = route == null ? 0 : route.getJumpHeight(); terrainRevision = route == null ? 0 : route.getTerrainRevision();
            if (!isCurrent()) throw new IllegalArgumentException("路径当前不可编辑，请重新选择目标");
        }
        public TimelineModel model() { return model; }
        public TimelineTrack owner() { return owner; }
        public ActorPathLibrary library() { return library; }
        public boolean isCurrent() {
            if (model.isPlaying() || !Float.isFinite(time) || time < 0 || Float.compare(time, model.getCurrentTime()) != 0) return false;
            if (route == null) return library.get(path.getId()) == path && !path.isLocked()
                    && before.matches(path.getWaypoints(), path.getInterpolationMode());
            return validOwner(model, owner, payload, actorId) && payload.getClips().contains(route) && !route.isMuted()
                    && route.getTimelineClip() != null && owner.getClips().contains(route.getTimelineClip())
                    && before.matches(route.getWaypoints(), route.getInterpolationMode())
                    && start == route.getStartTime() && end == route.getEndTime() && speed == route.getSpeed()
                    && loop == route.getLoopMode() && align == route.isAlignRotationToRoute()
                    && jumpStart == route.getJumpStart() && jumpDuration == route.getJumpDuration()
                    && jumpHeight == route.getJumpHeight() && terrainRevision == route.getTerrainRevision();
        }
        public boolean commit(List<Vector3f> points, ActorRouteClip.InterpolationMode mode) {
            if (!isCurrent()) return false;
            Geometry after = new Geometry(points, mode);
            if (before.equals(after)) return true;
            if (route == null) {
                TimelineCommandBridge.execute(new StateChangeCommand(model, "Edit library path",
                        () -> { requirePath(library, path); requireGeometry(path, after); persistGeometry(path, before, after, save); },
                        () -> { requirePath(library, path); requireGeometry(path, before); persistGeometry(path, after, before, save); }));
            } else {
                float newEnd = start + duration(after, speed);
                rejectOverlap(payload, route, start, newEnd);
                TimelineCommandBridge.execute(new StateChangeCommand(model, "Edit actor route",
                        () -> applyRoute(after, before, newEnd, end), () -> applyRoute(before, after, end, newEnd)));
            }
            return true;
        }
        private void applyRoute(Geometry expected, Geometry next, float expectedEnd, float nextEnd) {
            requireEditing(model);
            if (!validOwner(model, owner, payload, actorId) || !payload.getClips().contains(route)
                    || route.getTimelineClip() == null || !owner.getClips().contains(route.getTimelineClip())
                    || !expected.matches(route.getWaypoints(), route.getInterpolationMode())
                    || route.getStartTime() != start || route.getEndTime() != expectedEnd
                    || route.getSpeed() != speed || route.getLoopMode() != loop
                    || route.isAlignRotationToRoute() != align || route.getJumpStart() != jumpStart
                    || route.getJumpDuration() != jumpDuration || route.getJumpHeight() != jumpHeight)
                throw new IllegalArgumentException("路线目标或行为已变化，请重新选择");
            rejectOverlap(payload, route, start, nextEnd);
            next.apply(route); route.setTimeRange(start, nextEnd);
        }
    }

    public static Target route(TimelineModel model, TimelineTrack owner, ActorRouteClip route) {
        return new Target(model, owner, Objects.requireNonNull(route), null, null, null);
    }
    public static Target library(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path, Runnable save) {
        return new Target(model, null, null, Objects.requireNonNull(library), Objects.requireNonNull(path), Objects.requireNonNull(save));
    }
    private static void persistGeometry(ActorPathDefinition path, Geometry next, Geometry rollback, Runnable save) {
        next.apply(path);
        try { save.run(); }
        catch (RuntimeException failure) {
            rollback.apply(path);
            try { save.run(); } catch (RuntimeException repair) { failure.addSuppressed(repair); }
            throw failure;
        }
    }
    public static ActorPathDefinition saveNew(TimelineModel model, ActorPathLibrary library,
            List<Vector3f> points, ActorRouteClip.InterpolationMode mode, Runnable save) {
        requireEditing(model); Geometry geometry = new Geometry(points, mode);
        var path = new ActorPathDefinition(library.generateUniqueName("Path")); geometry.apply(path);
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Save actor path", () -> {
            if (library.get(path.getId()) != path) throw new IllegalArgumentException("路径资源已被替换");
            requireGeometry(path, geometry);
            library.remove(path.getId());
            try { save.run(); } catch (RuntimeException failure) { library.add(path); throw failure; }
        }, () -> {
            if (library.get(path.getId()) != null) throw new IllegalArgumentException("路径 ID 已存在");
            if (!library.isNameAvailable(path.getName(), null)) throw new IllegalArgumentException("路径名称已存在");
            library.add(path);
            try { save.run(); } catch (RuntimeException failure) { library.remove(path.getId()); throw failure; }
        }));
        return path;
    }
    public static void rename(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path, String name, Runnable save) {
        requireEditing(model); requirePath(library, path);
        if (path.isLocked()) throw new IllegalArgumentException("路径已锁定");
        var next = name == null ? "" : name.trim(); var old = path.getName();
        if (next.isEmpty() || !library.isNameAvailable(next, path.getId())) throw new IllegalArgumentException("路径名称为空或已存在");
        if (old.equals(next)) return;
        libraryChange(model, library, path, "Rename actor path", () -> renameChecked(library, path, old),
                () -> renameChecked(library, path, next), save);
    }
    private static void renameChecked(ActorPathLibrary library, ActorPathDefinition path, String name) {
        if (!library.isNameAvailable(name, path.getId())) throw new IllegalArgumentException("路径名称已存在");
        path.setName(name);
    }
    public static void setLocked(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path, boolean value, Runnable save) {
        requireEditing(model); requirePath(library, path); boolean old = path.isLocked();
        if (old == value) return;
        libraryChange(model, library, path, "Lock actor path", () -> path.setLocked(old), () -> path.setLocked(value), save);
    }
    public static void setVisible(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path, boolean value, Runnable save) {
        requireEditing(model); requirePath(library, path); boolean old = path.isVisible();
        if (old == value) return;
        libraryChange(model, library, path, "Show actor path", () -> path.setVisible(old), () -> path.setVisible(value), save);
    }
    public static void remove(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path, Runnable save) {
        requireEditing(model); requirePath(library, path);
        if (path.isLocked()) throw new IllegalArgumentException("路径已锁定");
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor path", () -> {
            if (library.get(path.getId()) != null || !library.isNameAvailable(path.getName(), null))
                throw new IllegalArgumentException("路径恢复位置已被其他资源占用");
            library.add(path);
            try { save.run(); } catch (RuntimeException failure) { library.remove(path.getId()); throw failure; }
        }, () -> {
            requirePath(library, path);
            if (path.isLocked()) throw new IllegalArgumentException("路径已锁定");
            library.remove(path.getId());
            try { save.run(); } catch (RuntimeException failure) { library.add(path); throw failure; }
        }));
    }
    private static void libraryChange(TimelineModel model, ActorPathLibrary library, ActorPathDefinition path,
            String description, Runnable before, Runnable after, Runnable save) {
        TimelineCommandBridge.execute(new StateChangeCommand(model, description,
                () -> { requirePath(library, path); changeAndSave(before, after, save); },
                () -> { requirePath(library, path); changeAndSave(after, before, save); }));
    }
    private static void changeAndSave(Runnable next, Runnable rollback, Runnable save) {
        next.run();
        try { save.run(); }
        catch (RuntimeException failure) { rollback.run(); throw failure; }
    }
    private static void requirePath(ActorPathLibrary library, ActorPathDefinition path) {
        if (path == null || library.get(path.getId()) != path) throw new IllegalArgumentException("路径资源已删除或替换");
    }
    private static void requireGeometry(ActorPathDefinition path, Geometry expected) {
        if (!expected.matches(path.getWaypoints(), path.getInterpolationMode())) throw new IllegalArgumentException("路径几何已变化");
    }
    public static EntityTimelineClip attach(TimelineModel model, TimelineTrack owner, List<Vector3f> points,
            ActorRouteClip.InterpolationMode mode, String name) {
        requireEditing(model);
        var payload = owner == null ? null : owner.getActorTrackPayload();
        UUID actorId = payload == null || payload.getActorRef() == null ? null : payload.getActorRef().getActorId();
        if (!validOwner(model, owner, payload, actorId)) throw new IllegalArgumentException("请先选择可编辑的演员轨道");
        Geometry geometry = new Geometry(points, mode);
        float start = model.getCurrentTime(), end = start + duration(geometry, 2);
        rejectOverlap(payload, null, start, end);
        var route = new ActorRouteClip(name, start, end); geometry.apply(route);
        route.setSpeed(2); route.setLoopMode(ActorRouteClip.LoopMode.ONCE); route.setAlignRotationToRoute(true);
        var wrapper = new EntityTimelineClip(route, payload.getColorRgba());
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Apply actor path", () -> {
            if (owner.getActorTrackPayload() != payload || !payload.getClips().contains(route)
                    || !owner.getClips().contains(wrapper)) throw new IllegalArgumentException("路线绑定已变化");
            owner.removeClip(wrapper); payload.removeClip(route.getId());
        }, () -> {
            requireEditing(model);
            if (!validOwner(model, owner, payload, actorId) || payload.getClips().contains(route) || owner.getClips().contains(wrapper))
                throw new IllegalArgumentException("路线目标已变化");
            rejectOverlap(payload, null, start, end);
            payload.addClip(route);
            try { if (!owner.addClip(wrapper)) throw new IllegalStateException("无法添加路线片段"); }
            catch (RuntimeException failure) { owner.removeClip(wrapper); payload.removeClip(route.getId()); throw failure; }
        }));
        return wrapper;
    }
    static boolean validOwner(TimelineModel model, TimelineTrack owner, ActorTrack payload, UUID actorId) {
        return owner != null && payload != null && actorId != null && model.getAllTracks().contains(owner)
                && owner.getType() == TrackType.ACTOR && !owner.isLocked() && !owner.isMuted()
                && owner.getActorTrackPayload() == payload && payload.getActorRef() != null
                && actorId.equals(payload.getActorRef().getActorId())
                && ActorRegistryManager.getInstance().getActor(actorId).isPresent();
    }
    static void requireEditing(TimelineModel model) {
        if (model == null || model.isPlaying() || !Float.isFinite(model.getCurrentTime()) || model.getCurrentTime() < 0)
            throw new IllegalArgumentException("请先暂停时间线再编辑路径");
    }
    private static float duration(Geometry geometry, float speed) {
        float duration = Math.max(.1f, ActorRouteSampler.computeGroundCurveLength(geometry.points, geometry.mode) / Math.max(.05f, speed));
        if (!Float.isFinite(duration)) throw new IllegalArgumentException("路径长度超出有效范围");
        return duration;
    }
    static void rejectOverlap(ActorTrack payload, ActorRouteClip excluded, float start, float end) {
        if (!Float.isFinite(end)) throw new IllegalArgumentException("路径时间范围无效");
        for (var clip : payload.getClips()) if (clip != excluded && !clip.isMuted()
                && (clip instanceof ActorRouteClip || clip instanceof EntityTransformClip)
                && clip.getEndTime() > start && clip.getStartTime() < end)
            throw new IllegalArgumentException("路线与已有移动片段重叠，请调整播放头或片段时间");
    }
}
