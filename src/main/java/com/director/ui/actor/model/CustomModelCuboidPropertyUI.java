package com.director.ui.actor.model;

import com.director.actor.Actor;
import com.director.actor.model.*;
import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.*;

/** Detached drafts per cuboid; changing a texture preserves each face's UV. */
public final class CustomModelCuboidPropertyUI {
    static final class Draft {
        final BoneCuboid source;
        final ImString texture;
        final float[][] faces;
        Draft(BoneCuboid source) {
            this.source=source; texture=new ImString(source.textureId()==null?"":source.textureId(),256);
            var uv=source.faceUvs();
            float[][] values=uv==null?new float[6][]:new float[][]{uv.north(),uv.south(),uv.east(),uv.west(),uv.up(),uv.down()};
            faces=new float[6][];
            for(int i=0;i<6;i++) faces[i]=values[i]==null?null:values[i].clone();
        }
        CuboidFaceUvs uv() {return new CuboidFaceUvs(faces[0],faces[1],faces[2],faces[3],faces[4],faces[5]);}
        void allFaces(int index) {
            if(faces[index]==null) return;
            for(int i=0;i<6;i++) faces[i]=faces[index].clone();
        }
    }
    private static final Map<Integer,Draft> drafts=new HashMap<>();
    private static TimelineModel scope;
    private static String modelId, message="";
    private static String tr(String key) { return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.cuboid." + key); }
    private CustomModelCuboidPropertyUI() {}
    public static void render(Actor actor,TimelineModel timeline) {
        if(actor==null || timeline==null || actor.getCustomModelId()==null) return;
        var model=CustomModelLibrary.getInstance().find(actor.getCustomModelId()).orElse(null);
        if(model==null) return;
        if(scope!=timeline || !Objects.equals(modelId,model.id())) {drafts.clear();scope=timeline;modelId=model.id();message="";}
        if(!ImGui.treeNode(tr("title") + "##customModelCuboids")) return;
        ImGui.textWrapped(tr("shared"));
        boolean blocked=timeline.isPlaying() || timeline.isScrubbing()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(blocked);
        String[] names={tr("north"),tr("south"),tr("east"),tr("west"),tr("up"),tr("down")};
        for(int index=0;index<model.cuboids().size();index++) {
            var cuboid=model.cuboids().get(index);
            if(!cuboid.usesCustomTexture() && cuboid.textureId()==null && cuboid.faceUvs()==null) continue;
            if(!ImGui.treeNode(cuboid.boneId()+"##customCuboid:"+index)) continue;
            var draft=drafts.get(index);
            if(draft==null || draft.source!=cuboid) {draft=new Draft(cuboid);drafts.put(index,draft);}
            ImGui.pushID(index);
            ImGui.inputText(tr("texture"),draft.texture);
            for(int face=0;face<6;face++) {
                ImGui.pushID(face);
                if(ImGui.checkbox(names[face],draft.faces[face]!=null))
                    draft.faces[face]=draft.faces[face]==null?new float[]{0,0,16,16}:null;
                if(draft.faces[face]!=null) {
                    ImGui.inputFloat4(tr("uv"),draft.faces[face]);
                    if(ImGui.smallButton(tr("apply_all"))) draft.allFaces(face);
                }
                ImGui.popID();
            }
            if(ImGui.button(tr("apply"))) {
                try {
                    if(CustomModelLibrary.getInstance().find(model.id()).orElse(null)!=model)
                        throw new IllegalArgumentException(tr("changed"));
                    CustomModelAssetEditing.updateTexturedCuboid(timeline,model.id(),index,draft.texture.get().trim(),draft.uv());
                    message=tr("applied");
                } catch(RuntimeException failure) {message=failure.getMessage();}
            }
            ImGui.sameLine();
            if(ImGui.button(tr("cancel"))) drafts.remove(index);
            ImGui.popID();ImGui.treePop();
        }
        ImGui.endDisabled();
        if(!message.isEmpty()) ImGui.textWrapped(message);
        ImGui.treePop();
    }
}
