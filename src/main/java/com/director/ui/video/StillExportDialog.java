package com.director.ui.video;

import com.director.core.DirectorServices;
import com.director.export.video.*;
import com.director.timeline.model.*;
import com.director.ui.DirectorUIManager;
import imgui.ImGui;
import imgui.type.*;
import java.nio.file.*;
import java.util.*;

/** Still export uses the same exclusive render queue and state restoration as video. */
public final class StillExportDialog {
    private static boolean requested;
    private static final ImString path=new ImString(1024);
    private static final ImInt width=new ImInt(1920),height=new ImInt(1080),camera=new ImInt();
    private static boolean nativeResolution,overwrite;
    private static TimelineModel openedModel;
    private static float time;
    private static String error="";
    private StillExportDialog() {}
    public static void open() {
        openedModel=DirectorServices.get().containerTimeline().getTimelineModel();
        time=openedModel==null?0:openedModel.getCurrentTime();
        requested=true;overwrite=false;error="";camera.set(0);
        if(openedModel!=null) {
            var tracks=openedModel.getAllTracks().stream().filter(t->t.getType()==TrackType.CAMERA).toList();
            int index=tracks.indexOf(com.director.timeline.playback.camera.TimelineCameraController.getInstance().getActiveCameraTrack());
            if(index>=0) camera.set(index+1);
        }
    }
    public static void renderIfNeeded(DirectorUIManager manager) {
        String popup="导出当前帧 PNG##directorStillExport";
        if(requested) {ImGui.openPopup(popup);requested=false;}
        if(!ImGui.beginPopupModal(popup,imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.pushTextWrapPos(ImGui.getCursorPosX()+480);
        try {
            var model=DirectorServices.get().containerTimeline().getTimelineModel();
            var tracks=model==null?List.<TimelineTrack>of():model.getAllTracks().stream().filter(t->t.getType()==TrackType.CAMERA).toList();
            ImGui.text("输出时间："+String.format(Locale.ROOT,"%.3fs",time));
            ImGui.textDisabled("捕获世界画面，保留场景背景。");
            String[] cameras=new String[tracks.size()+1];cameras[0]="玩家视角";
            for(int i=0;i<tracks.size();i++) cameras[i+1]="摄像机 · "+tracks.get(i).getName();
            if(camera.get()>=cameras.length) camera.set(0);
            ImGui.combo("视角",camera,cameras);
            if(ImGui.button("1:1")) {width.set(1080);height.set(1080);}ImGui.sameLine();
            if(ImGui.button("16:9")) {width.set(1920);height.set(1080);}ImGui.sameLine();
            if(ImGui.button("9:16")) {width.set(1080);height.set(1920);}
            ImGui.inputInt("宽度（自定义）",width);ImGui.inputInt("高度（自定义）",height);
            if(ImGui.checkbox("按输出分辨率重新渲染",nativeResolution)) nativeResolution=!nativeResolution;
            ImGui.textWrapped(nativeResolution?"按指定尺寸渲染世界，画质与性能取决于显卡及光影兼容性。":"使用窗口画面裁切缩放到输出尺寸；提高输出尺寸不会增加画面细节。");
            ImGui.inputText("文件",path,imgui.flag.ImGuiInputTextFlags.ReadOnly);ImGui.sameLine();
            if(ImGui.button("选择...")) {
                try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
                    var filters=stack.mallocPointer(1);filters.put(stack.UTF8("*.png")).flip();
                    String result=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog("导出当前帧",path.get().isBlank()?"frame.png":path.get(),filters,"PNG image");
                    if(result!=null) {path.set(result.toLowerCase(Locale.ROOT).endsWith(".png")?result:result+".png");overwrite=false;}
                }
            }
            boolean exists=false;
            try {exists=!path.get().isBlank() && Files.exists(Path.of(path.get()),LinkOption.NOFOLLOW_LINKS);}
            catch(InvalidPathException invalid) {error="输出路径无效";}
            if(exists && ImGui.checkbox("覆盖已有 PNG 文件",overwrite)) overwrite=!overwrite;
            boolean blocked=model==null || model!=openedModel || model.isPlaying() || model.isScrubbing()
                    || net.minecraft.client.MinecraftClient.getInstance().world==null || VideoExportManager.getInstance().isExporting()
                    || com.director.ui.actor.pose.ActorBonePropertiesPanel.hasNumericDraft()
                    || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().hasPendingDraft()
                    || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                    || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging();
            if(blocked) ImGui.textWrapped("请先暂停播放并完成当前编辑；导出需要已加载的世界，且不能与其他导出同时进行。");
            ImGui.beginDisabled(blocked || path.get().isBlank() || exists && !overwrite);
            if(ImGui.button("导出 PNG")) {
                var settings=new VideoExportSettings();settings.format=VideoExportSettings.FORMAT_PNG_STILL;
                settings.stillTime=time;settings.width=width.get();settings.height=height.get();settings.outputPath=path.get();
                settings.nativeResolution=nativeResolution;settings.enableAudio=false;
                settings.cameraMode=camera.get()==0?VideoExportSettings.CAMERA_PLAYER:VideoExportSettings.CAMERA_TRACK;
                settings.cameraTrackIndex=Math.max(0,camera.get()-1);
                settings.cameraTrackName=camera.get()==0?null:tracks.get(camera.get()-1).getName();
                error=settings.validationError();
                if(error==null) {manager.startVideoExport(settings);ImGui.closeCurrentPopup();}
            }
            ImGui.endDisabled();ImGui.sameLine();if(ImGui.button("取消")) ImGui.closeCurrentPopup();
            if(error!=null && !error.isBlank()) ImGui.textWrapped(error);
        } finally {ImGui.popTextWrapPos();ImGui.endPopup();}
    }
}
