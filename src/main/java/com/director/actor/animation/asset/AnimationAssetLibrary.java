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
    private long revision;
    private final Runnable metadataChanged;
    public AnimationAssetLibrary() {
        var owner = new java.lang.ref.WeakReference<>(this);
        metadataChanged = () -> { var library=owner.get(); if(library!=null) library.revision++; };
    }
    public long revision() { return revision; }

    public static AnimationAssetLibrary getInstance() { return INSTANCE; }

    public Optional<AnimationAsset> register(AnimationAsset asset) {
        Objects.requireNonNull(asset, "asset");
        var previous=assets.put(asset.id(), asset);
        if(previous!=asset) {
            if(previous!=null) previous.removeMetadataListener(metadataChanged);
            asset.addMetadataListener(metadataChanged); revision++;
        }
        return Optional.ofNullable(previous);
    }

    public Optional<AnimationAsset> find(String id) { return Optional.ofNullable(assets.get(id)); }
    /** Editor insert: unlike register, never replaces an existing asset. */
    public boolean addIfAbsent(AnimationAsset asset) {
        Objects.requireNonNull(asset, "asset");
        if(assets.putIfAbsent(asset.id(), asset)!=null) return false;
        asset.addMetadataListener(metadataChanged); revision++; return true;
    }
    public boolean contains(String id) { return assets.containsKey(id); }
    public boolean remove(String id) {
        var removed=assets.remove(id); if(removed==null) return false;
        removed.removeMetadataListener(metadataChanged); revision++; return true;
    }
    public int size() { return assets.size(); }
    public void clear() {
        if(assets.isEmpty()) return;
        assets.values().forEach(asset->asset.removeMetadataListener(metadataChanged));
        assets.clear(); revision++;
    }
    public List<AnimationAsset> all() { return Collections.unmodifiableList(new ArrayList<>(assets.values())); }
}
