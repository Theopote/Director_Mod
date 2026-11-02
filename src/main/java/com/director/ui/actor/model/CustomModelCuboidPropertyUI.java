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
    private CustomModelCuboidPropertyUI() {}
    public static void render(Actor actor,TimelineModel timeline) {
        if(actor==null || timeline==null || actor.getCustomModelId()==null) return;
        var model=CustomModelLibrary.getInstance().find(actor.getCustomModelId()).orElse(null);
        if(model==null) return;
        if(scope!=timeline || !Objects.equals(modelId,model.id())) {drafts.clear();scope=timeline;modelId=model.id();message="";}
        if(!ImGui.treeNode("贴图 / UV 编辑##customModelCuboids")) return;
        ImGui.textWrapped("修改共享模型会影响所有引用它的演员。每个面的 UV 分别保存；修改贴图 ID 不会统一各面 UV。");
        boolean blocked=timeline.isPlaying() || timeline.isScrubbing()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(blocked);
        String[] names={"北面","南面","东面","西面","顶面","底面"};
        for(int index=0;index<model.cuboids().size();index++) {
            var cuboid=model.cuboids().get(index);
            if(!cuboid.usesCustomTexture() && cuboid.textureId()==null && cuboid.faceUvs()==null) continue;
            if(!ImGui.treeNode(cuboid.boneId()+"##customCuboid:"+index)) continue;
            var draft=drafts.get(index);
            if(draft==null || draft.source!=cuboid) {draft=new Draft(cuboid);drafts.put(index,draft);}
            ImGui.pushID(index);
            ImGui.inputText("贴图 ID",draft.texture);
            for(int face=0;face<6;face++) {
                ImGui.pushID(face);
                if(ImGui.checkbox(names[face],draft.faces[face]!=null))
                    draft.faces[face]=draft.faces[face]==null?new float[]{0,0,16,16}:null;
                if(draft.faces[face]!=null) {
                    ImGui.inputFloat4("UV U0 V0 U1 V1",draft.faces[face]);
                    if(ImGui.smallButton("将此 UV 应用到全部面")) draft.allFaces(face);
                }
                ImGui.popID();
            }
            if(ImGui.button("应用")) {
                try {
                    if(CustomModelLibrary.getInstance().find(model.id()).orElse(null)!=model)
                        throw new IllegalArgumentException("模型已变化，请重新编辑");
                    CustomModelAssetEditing.updateTexturedCuboid(timeline,model.id(),index,draft.texture.get().trim(),draft.uv());
                    message="已应用，可撤销。";
                } catch(RuntimeException failure) {message=failure.getMessage();}
            }
            ImGui.sameLine();
            if(ImGui.button("取消未应用修改")) drafts.remove(index);
            ImGui.popID();ImGui.treePop();
        }
        ImGui.endDisabled();
        if(!message.isEmpty()) ImGui.textWrapped(message);
        ImGui.treePop();
    }
}
