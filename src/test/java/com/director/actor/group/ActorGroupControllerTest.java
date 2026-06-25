package com.director.actor.group;

import com.director.actor.ActorGroup;
import com.director.actor.action.ActorActionType;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorGroupControllerTest {

    private static final float EPS = 0.0001f;

    @Test
    void lineFormationSpreadsAroundCenter() {
        ActorGroup group = new ActorGroup("line");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.setFormationType(ActorGroup.FormationType.LINE);
        group.setFormationSpacing(2.0f);

        ActorGroupController controller = ActorGroupController.getInstance();
        List<ActorGroupController.FormationPosition> positions = controller.calculateFormation(
                group,
                new Vec3d(0.0, 64.0, 0.0),
                0.0f
        );

        assertEquals(3, positions.size());
        assertEquals(-2.0, positions.get(0).position().x, EPS);
        assertEquals(0.0, positions.get(1).position().x, EPS);
        assertEquals(2.0, positions.get(2).position().x, EPS);
    }

    @Test
    void followLeaderFormationKeepsSingleFile() {
        ActorGroup group = new ActorGroup("follow");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.setFormationType(ActorGroup.FormationType.FOLLOW_LEADER);
        group.setFormationSpacing(1.5f);

        ActorGroupController controller = ActorGroupController.getInstance();
        List<ActorGroupController.FormationPosition> positions = controller.calculateFormation(
                group,
                new Vec3d(10.0, 70.0, 20.0),
                0.0f
        );

        assertEquals(3, positions.size());
        assertEquals(20.0, positions.get(0).position().z, EPS);
        assertEquals(18.5, positions.get(1).position().z, EPS);
        assertEquals(17.0, positions.get(2).position().z, EPS);
    }

    @Test
    void sequentialDelayPolicyOffsetsByStep() {
        ActorGroup group = new ActorGroup("delay-seq");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.setDelayPolicy(ActorGroup.DelayPolicy.SEQUENTIAL);
        group.setDelayStepSeconds(0.2f);

        ActorGroupController controller = ActorGroupController.getInstance();
        List<ActorGroupController.ActionTrigger> triggers = controller.applyDelayPolicy(
                group,
                ActorActionType.WALK,
                5.0f
        );

        assertEquals(3, triggers.size());
        assertEquals(5.0f, triggers.get(0).triggerTime(), EPS);
        assertEquals(5.2f, triggers.get(1).triggerTime(), EPS);
        assertEquals(5.4f, triggers.get(2).triggerTime(), EPS);
    }

    @Test
    void noneDelayPolicyUsesSameBaseTime() {
        ActorGroup group = new ActorGroup("delay-none");
        group.addActor(UUID.randomUUID());
        group.addActor(UUID.randomUUID());
        group.setDelayPolicy(ActorGroup.DelayPolicy.NONE);
        group.setDelayStepSeconds(0.5f);

        ActorGroupController controller = ActorGroupController.getInstance();
        List<ActorGroupController.ActionTrigger> triggers = controller.applyDelayPolicy(
                group,
                ActorActionType.IDLE,
                3.0f
        );

        assertEquals(2, triggers.size());
        assertEquals(3.0f, triggers.get(0).triggerTime(), EPS);
        assertEquals(3.0f, triggers.get(1).triggerTime(), EPS);
    }
}
