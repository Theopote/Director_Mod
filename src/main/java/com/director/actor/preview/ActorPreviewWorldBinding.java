package com.director.actor.preview;

import java.util.Objects;

/** World instance identity owns resources; equal dimension keys do not imply the same world. */
final class ActorPreviewWorldBinding<W> {
    private W world;
    private long revision;
    private final Runnable release;
    ActorPreviewWorldBinding(Runnable release) { this.release=Objects.requireNonNull(release); }
    boolean bind(W next) {
        if(world==next) return false;
        release.run();world=next;revision++;return true;
    }
    void disconnect() { release.run();world=null;revision++; }
    W world() { return world; }
    long revision() { return revision; }
}
