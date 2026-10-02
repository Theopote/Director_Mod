package com.director.actor.path;
import com.director.actor.Actor;
import com.director.actor.preview.ActorPreviewManager;
import org.joml.Vector3f;

/** Standing preview collision bounds independent of whether an entity currently exists. */
public record ActorCollisionDimensions(double width,double height) {
    public ActorCollisionDimensions {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width<=0 || height<=0)
            throw new IllegalArgumentException("Invalid actor collision dimensions");
    }
    public ActorCollisionDimensions scaled(Vector3f scale) {
        if (scale==null || !scale.isFinite() || scale.x<=0 || scale.y<=0 || scale.z<=0)
            throw new IllegalArgumentException("Invalid actor collision scale");
        return new ActorCollisionDimensions(width*Math.max(scale.x,scale.z),height*scale.y);
    }
    public static ActorCollisionDimensions forActor(Actor actor,Vector3f scale) {
        if (actor==null) throw new IllegalArgumentException("Actor is missing");
        var dimensions=ActorPreviewManager.previewEntityType(actor.getType()).getDimensions();
        return new ActorCollisionDimensions(dimensions.width(),dimensions.height()).scaled(scale);
    }
}
