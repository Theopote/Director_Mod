package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.ui.actor.edit.ActorTransformEditService;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * 选中演员时，在属性面板展示播放头时刻的变换与朝向数值，并写回时间线片段。
 */
public final class ActorLiveTransformPropertyUI {

    private static final ImFloat posX = new ImFloat();
    private static final ImFloat posY = new ImFloat();
    private static final ImFloat posZ = new ImFloat();
    private static final ImFloat rotYaw = new ImFloat();
    private static final ImFloat rotPitch = new ImFloat();
    private static final ImFloat rotRoll = new ImFloat();
    private static final ImFloat scaleX = new ImFloat(1f);
    private static final ImFloat scaleY = new ImFloat(1f);
    private static final ImFloat scaleZ = new ImFloat(1f);
    private static final ImFloat lookAtX = new ImFloat();
    private static final ImFloat lookAtY = new ImFloat();
    private static final ImFloat lookAtZ = new ImFloat();
    private static final ImFloat lookAtStrength = new ImFloat(1f);
    private static final ImInt lookAtModeIndex = new ImInt();

    private static UUID cachedActorId;
    private static float cachedTime = Float.NaN;

    private ActorLiveTransformPropertyUI() {
    }

    public static void render(UUID actorId) {
        if (actorId == null) {
            return;
        }
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            ImGui.textDisabled("时间线未加载");
            return;
        }
        float time = model.getCurrentTime();
        syncFromSample(actorId, time);

        if (ImGui.collapsingHeader(Icons.TRANSFORM + " 当前变换（播放头）", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textDisabled(String.format("时间: %.2fs — 修改将写入 Transform 片段", time));
            ImGui.spacing();

            boolean changed = false;
            UITheme.beginGroup("位置");
            changed |= inputFloat("X##actorLivePosX", posX);
            changed |= inputFloat("Y##actorLivePosY", posY);
            changed |= inputFloat("Z##actorLivePosZ", posZ);
            UITheme.endGroup();

            UITheme.beginGroup("旋转 (°)");
            changed |= inputFloat("偏航 Yaw##actorLiveRotY", rotYaw);
            changed |= inputFloat("俯仰 Pitch##actorLiveRotP", rotPitch);
            changed |= inputFloat("翻滚 Roll##actorLiveRotR", rotRoll);
            UITheme.endGroup();

            UITheme.beginGroup("缩放");
            changed |= inputFloat("X##actorLiveScaleX", scaleX);
            changed |= inputFloat("Y##actorLiveScaleY", scaleY);
            changed |= inputFloat("Z##actorLiveScaleZ", scaleZ);
            UITheme.endGroup();

            if (changed) {
                ActorTransformEditService.applyTransform(
                        actorId,
                        time,
                        new Vector3f(posX.get(), posY.get(), posZ.get()),
                        new Vector3f(rotYaw.get(), rotPitch.get(), rotRoll.get()),
                        new Vector3f(scaleX.get(), scaleY.get(), scaleZ.get()));
            }
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " 当前朝向（播放头）", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode[] modes = {
                    EntityLookAtClip.TargetMode.WORLD_POINT,
                    EntityLookAtClip.TargetMode.CAMERA
            };
            String[] modeLabels = {"世界坐标点", "看向摄像机"};
            if (ImGui.combo("目标模式##actorLiveLookAtMode", lookAtModeIndex, modeLabels)) {
                applyCurrentLookAt(actorId, time);
            }

            float[] strength = {lookAtStrength.get()};
            if (ImGui.sliderFloat("强度##actorLiveLookAtStrength", strength, 0f, 1f, "%.2f")) {
                lookAtStrength.set(strength[0]);
                applyCurrentLookAt(actorId, time);
            }

            if (modes[lookAtModeIndex.get()] == EntityLookAtClip.TargetMode.WORLD_POINT) {
                UITheme.beginGroup("世界坐标");
                boolean pointChanged = false;
                pointChanged |= inputFloat("X##actorLiveLookX", lookAtX);
                pointChanged |= inputFloat("Y##actorLiveLookY", lookAtY);
                pointChanged |= inputFloat("Z##actorLiveLookZ", lookAtZ);
                UITheme.endGroup();
                if (pointChanged) {
                    applyCurrentLookAt(actorId, time);
                }
            } else if (ImGui.button(Icons.TARGET + " 应用看向摄像机##actorLiveLookCam")) {
                lookAtModeIndex.set(1);
                applyCurrentLookAt(actorId, time);
            }
        }

        ImGui.spacing();
        ImGui.textDisabled("G/R/S/T：视口 Gizmo 模式；此处与时间线片段编辑等效。");
    }

    private static void applyCurrentLookAt(UUID actorId, float time) {
        if (lookAtModeIndex.get() == 0) {
            ActorTransformEditService.applyLookAtWorldPoint(
                    actorId, time, lookAtX.get(), lookAtY.get(), lookAtZ.get(), lookAtStrength.get());
        } else {
            ActorTransformEditService.applyLookAtCamera(actorId, time, lookAtStrength.get());
        }
    }

    private static void syncFromSample(UUID actorId, float time) {
        if (Objects.equals(actorId, cachedActorId) && Math.abs(time - cachedTime) < 1e-4f) {
            return;
        }
        ActorTransformEditService.TransformSnapshot transform =
                ActorTransformEditService.sampleTransform(actorId, time);
        posX.set(transform.position().x);
        posY.set(transform.position().y);
        posZ.set(transform.position().z);
        rotYaw.set(transform.rotation().x);
        rotPitch.set(transform.rotation().y);
        rotRoll.set(transform.rotation().z);
        scaleX.set(transform.scale().x);
        scaleY.set(transform.scale().y);
        scaleZ.set(transform.scale().z);

        ActorTransformEditService.LookAtSnapshot lookAt =
                ActorTransformEditService.sampleLookAt(actorId, time);
        lookAtX.set(lookAt.worldPoint().x);
        lookAtY.set(lookAt.worldPoint().y);
        lookAtZ.set(lookAt.worldPoint().z);
        lookAtStrength.set(lookAt.strength());
        lookAtModeIndex.set(lookAt.mode() == EntityLookAtClip.TargetMode.WORLD_POINT ? 0 : 1);

        cachedActorId = actorId;
        cachedTime = time;
    }

    private static boolean inputFloat(String label, ImFloat value) {
        return ImGui.inputFloat(label, value);
    }
}
