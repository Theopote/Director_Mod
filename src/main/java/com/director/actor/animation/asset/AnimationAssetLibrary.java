package com.director.actor.animation.asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Project-facing registry for new rig-based animation assets. */
public final class AnimationAssetLibrary {
    private static final AnimationAssetLibrary INSTANCE = new AnimationAssetLibrary();
    private final Map<String, AnimationAsset> assets = new LinkedHashMap<>();

    public static AnimationAssetLibrary getInstance() { return INSTANCE; }

    public Optional<AnimationAsset> register(AnimationAsset asset) {
        Objects.requireNonNull(asset, "asset");
        return Optional.ofNullable(assets.put(asset.id(), asset));
    }

    public Optional<AnimationAsset> find(String id) { return Optional.ofNullable(assets.get(id)); }
    public boolean contains(String id) { return assets.containsKey(id); }
    public boolean remove(String id) { return assets.remove(id) != null; }
    public int size() { return assets.size(); }
    public void clear() { assets.clear(); }
    public List<AnimationAsset> all() { return Collections.unmodifiableList(new ArrayList<>(assets.values())); }
}
