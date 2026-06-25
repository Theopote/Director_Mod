package com.director.actor.group;

import com.director.actor.ActorGroup;
import com.director.actor.action.ActorActionType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ActorGroupController
 *
 * <p>负责编队位置计算与组动作延迟策略。</p>
 */
public class ActorGroupController {

    private static final ActorGroupController INSTANCE = new ActorGroupController();

    private ActorGroupController() {
    }

    public static ActorGroupController getInstance() {
        return INSTANCE;
    }

    public List<FormationPosition> calculateFormation(
            ActorGroup group,
            Vec3d centerPosition,
            float facingYaw
    ) {
        List<FormationPosition> positions = new ArrayList<>();
        List<UUID> actorIds = group.getActorIds();
        if (actorIds.isEmpty()) {
            return positions;
        }

        float spacing = group.getFormationSpacing();
        switch (group.getFormationType()) {
            case FREE -> {
                for (UUID actorId : actorIds) {
                    positions.add(new FormationPosition(actorId, centerPosition, facingYaw));
                }
            }
            case LINE -> {
                int count = actorIds.size();
                float startOffset = -(count - 1) * spacing / 2f;
                for (int i = 0; i < count; i++) {
                    float offset = startOffset + i * spacing;
                    Vec3d pos = offsetPosition(centerPosition, offset, 0, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
            case CIRCLE -> {
                int count = actorIds.size();
                float radius = spacing * count / (2f * (float) Math.PI);
                for (int i = 0; i < count; i++) {
                    float angle = 360f * i / count;
                    Vec3d pos = offsetPosition(
                            centerPosition,
                            (float) (radius * Math.cos(Math.toRadians(angle))),
                            (float) (radius * Math.sin(Math.toRadians(angle))),
                            facingYaw
                    );
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw + angle));
                }
            }
            case GRID -> {
                int count = actorIds.size();
                int cols = (int) Math.ceil(Math.sqrt(count));
                for (int i = 0; i < count; i++) {
                    int row = i / cols;
                    int col = i % cols;
                    float x = (col - cols / 2f) * spacing;
                    float z = row * spacing;
                    Vec3d pos = offsetPosition(centerPosition, x, z, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
            case FOLLOW_LEADER -> {
                positions.add(new FormationPosition(actorIds.getFirst(), centerPosition, facingYaw));
                for (int i = 1; i < actorIds.size(); i++) {
                    Vec3d pos = offsetPosition(centerPosition, 0, -i * spacing, facingYaw);
                    positions.add(new FormationPosition(actorIds.get(i), pos, facingYaw));
                }
            }
        }
        return positions;
    }

    public List<ActionTrigger> applyDelayPolicy(
            ActorGroup group,
            ActorActionType action,
            float baseTime
    ) {
        List<ActionTrigger> triggers = new ArrayList<>();
        List<UUID> actorIds = group.getActorIds();
        if (actorIds.isEmpty()) {
            return triggers;
        }

        float delayStep = group.getDelayStepSeconds();
        switch (group.getDelayPolicy()) {
            case NONE -> {
                for (UUID actorId : actorIds) {
                    triggers.add(new ActionTrigger(actorId, action, baseTime));
                }
            }
            case SEQUENTIAL -> {
                for (int i = 0; i < actorIds.size(); i++) {
                    triggers.add(new ActionTrigger(actorIds.get(i), action, baseTime + i * delayStep));
                }
            }
            case WAVE -> {
                int count = actorIds.size();
                for (int i = 0; i < count; i++) {
                    int waveIndex = (i % 2 == 0) ? i / 2 : count - 1 - i / 2;
                    triggers.add(new ActionTrigger(actorIds.get(i), action, baseTime + waveIndex * delayStep));
                }
            }
            case RANDOM -> {
                long seed = group.getGroupId().getMostSignificantBits() ^ group.getGroupId().getLeastSignificantBits();
                java.util.Random random = new java.util.Random(seed);
                for (UUID actorId : actorIds) {
                    float randomDelay = random.nextFloat() * delayStep * actorIds.size();
                    triggers.add(new ActionTrigger(actorId, action, baseTime + randomDelay));
                }
            }
        }

        return triggers;
    }

    private Vec3d offsetPosition(Vec3d center, float xOffset, float zOffset, float yaw) {
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        double x = center.x + xOffset * cos - zOffset * sin;
        double z = center.z + xOffset * sin + zOffset * cos;
        return new Vec3d(x, center.y, z);
    }

    public record FormationPosition(UUID actorId, Vec3d position, float yaw) {
    }

    public record ActionTrigger(UUID actorId, ActorActionType action, float triggerTime) {
    }
}
