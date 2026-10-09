package com.director.ui.actor.edit;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class ActorScaleEditingTest {
    @Test void preservesExistingProportionsOnEveryAxis() {
        for (int axis=0; axis<3; axis++) {
            var base=new Vector3f(1,2,3);
            assertEquals(new Vector3f(2,4,6), ActorScaleEditing.proportional(base, axis, base.get(axis)*2));
        }
    }
    @Test void boundsKeepProportions() {
        var result=ActorScaleEditing.proportional(new Vector3f(1,2,4),0,1000);
        assertEquals(new Vector3f(25,50,100),result);
        result=ActorScaleEditing.proportional(new Vector3f(1,2,4),0,-1);
        assertEquals(.01f,result.x,1e-6f);
        assertEquals(.04f,result.z,1e-6f);
    }
}
