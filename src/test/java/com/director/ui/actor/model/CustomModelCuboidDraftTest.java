package com.director.ui.actor.model;
import com.director.actor.model.*;
import com.director.actor.model.texture.CuboidFaceUvs;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomModelCuboidDraftTest {
    BoneCuboid cuboid(float u) {return new BoneCuboid("torso",new Vector3f(),new Vector3f(1),"minecraft:stone","model","old",
            new CuboidFaceUvs(new float[]{u,0,4,4},new float[]{8,0,12,4},null,null,null,null));}
    @Test void independentDraftsRetainInputAndTextureChangesPreserveIndividualFaces() {
        var source=cuboid(1);var first=new CustomModelCuboidPropertyUI.Draft(source);
        var second=new CustomModelCuboidPropertyUI.Draft(cuboid(3));
        first.texture.set("new");first.faces[0][0]=2;second.faces[0][0]=9;
        assertEquals("new",first.texture.get());assertEquals(2,first.uv().north()[0]);assertEquals(8,first.uv().south()[0]);
        assertNull(first.uv().east());assertEquals(1,source.faceUvs().north()[0]);
    }
    @Test void uniformUvRequiresExplicitOperationAndCopiesArrays() {
        var draft=new CustomModelCuboidPropertyUI.Draft(cuboid(1));draft.allFaces(0);
        assertArrayEquals(draft.uv().north(),draft.uv().south());draft.faces[0][0]=7;
        assertEquals(1,draft.faces[1][0]);
    }
}
