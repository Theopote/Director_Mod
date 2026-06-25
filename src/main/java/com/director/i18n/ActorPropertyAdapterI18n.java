package com.director.i18n;

import com.director.actor.ActorGroup;
import com.director.actor.ActorType;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.model.entity.EntityLookAtClip;

/**
 * Actor property panel UI strings ({@code gui.director.properties.adapter.actor.*}).
 */
public final class ActorPropertyAdapterI18n {

    private ActorPropertyAdapterI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    public static String[] actorTypeLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.ActorType.PLAYER),
                tr(DirectorLang.Properties.AdapterActor.ActorType.VILLAGER),
                tr(DirectorLang.Properties.AdapterActor.ActorType.COW),
                tr(DirectorLang.Properties.AdapterActor.ActorType.SHEEP),
                tr(DirectorLang.Properties.AdapterActor.ActorType.ZOMBIE),
                tr(DirectorLang.Properties.AdapterActor.ActorType.SKELETON),
                tr(DirectorLang.Properties.AdapterActor.ActorType.CUSTOM)
        };
    }

    public static String actorTypeLabel(ActorType type) {
        if (type == null) {
            return tr(DirectorLang.Properties.AdapterActor.ActorType.CUSTOM);
        }
        return actorTypeLabels()[type.ordinal()];
    }

    public static String[] formationTypeLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.Formation.FREE),
                tr(DirectorLang.Properties.AdapterActor.Formation.LINE),
                tr(DirectorLang.Properties.AdapterActor.Formation.CIRCLE),
                tr(DirectorLang.Properties.AdapterActor.Formation.GRID),
                tr(DirectorLang.Properties.AdapterActor.Formation.FOLLOW_LEADER)
        };
    }

    public static String[] delayPolicyLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.DelayPolicy.NONE),
                tr(DirectorLang.Properties.AdapterActor.DelayPolicy.SEQUENTIAL),
                tr(DirectorLang.Properties.AdapterActor.DelayPolicy.WAVE),
                tr(DirectorLang.Properties.AdapterActor.DelayPolicy.RANDOM)
        };
    }

    public static String[] routeInterpolationLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.Route.INTERP_LINEAR),
                tr(DirectorLang.Properties.AdapterActor.Route.INTERP_SMOOTH)
        };
    }

    public static String[] routeLoopLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.Route.LOOP_ONCE),
                tr(DirectorLang.Properties.AdapterActor.Route.LOOP_LOOP),
                tr(DirectorLang.Properties.AdapterActor.Route.LOOP_PING_PONG)
        };
    }

    public static String[] lookAtTargetModeLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterActor.LookAt.MODE_WORLD_POINT),
                tr(DirectorLang.Properties.AdapterActor.LookAt.MODE_ENTITY),
                tr(DirectorLang.Properties.AdapterActor.LookAt.MODE_CAMERA)
        };
    }

    public static String formationLabel(ActorGroup.FormationType type) {
        if (type == null) {
            return formationTypeLabels()[0];
        }
        return formationTypeLabels()[type.ordinal()];
    }

    public static String delayPolicyLabel(ActorGroup.DelayPolicy policy) {
        if (policy == null) {
            return delayPolicyLabels()[0];
        }
        return delayPolicyLabels()[policy.ordinal()];
    }

    public static String routeInterpolationLabel(ActorRouteClip.InterpolationMode mode) {
        if (mode == null) {
            return routeInterpolationLabels()[0];
        }
        return routeInterpolationLabels()[mode.ordinal()];
    }

    public static String routeLoopLabel(ActorRouteClip.LoopMode mode) {
        if (mode == null) {
            return routeLoopLabels()[0];
        }
        return routeLoopLabels()[mode.ordinal()];
    }

    public static String lookAtModeLabel(EntityLookAtClip.TargetMode mode) {
        if (mode == null) {
            return lookAtTargetModeLabels()[2];
        }
        return lookAtTargetModeLabels()[mode.ordinal()];
    }
}
