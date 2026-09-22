package com.director.actor.model.bbmodel;

import java.util.List;
import java.util.Map;

/** Deserialized Blockbench .bbmodel structure (no validation). */
public final class BbModelFile {
    public Meta meta;
    public Resolution resolution;
    public List<Element> elements;
    public List<OutlinerGroup> outliner;
    /** Element uuid strings placed directly under the outliner root (not inside a group). */
    public List<String> topLevelElementRefs;
    public List<Texture> textures;

    public static final class Meta {
        public String format_version;
        public String model_format;
    }

    public static final class Resolution {
        public int width;
        public int height;
    }

    public static final class Element {
        public String uuid;
        public String name;
        public String type;
        public float[] from;
        public float[] to;
        public float[] origin;
        public float[] rotation;
        public Map<String, Face> faces;
    }

    public static final class Face {
        public float[] uv;
        public Object texture;
    }

    public static final class OutlinerGroup {
        public String name;
        public String uuid;
        public float[] origin;
        public float[] rotation;
        public List<OutlinerChild> children;
    }

    /** Either a child bone group or a reference to an element uuid. */
    public sealed interface OutlinerChild permits OutlinerGroupChild, OutlinerElementRef {
    }

    public record OutlinerGroupChild(OutlinerGroup group) implements OutlinerChild {}

    public record OutlinerElementRef(String elementUuid) implements OutlinerChild {}

    public static final class Texture {
        public String id;
        public String name;
        public String source;
        public String uuid;
    }
}
