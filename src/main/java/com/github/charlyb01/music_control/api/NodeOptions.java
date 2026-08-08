package com.github.charlyb01.music_control.api;

/** The two orthogonal properties of a music event node. */
public record NodeOptions(
        ParentMix parentMix,
        EmptyBehavior whenEmpty) {

    public NodeOptions {
        if (parentMix == null) throw new IllegalArgumentException("parentMix cannot be null");
        if (whenEmpty == null) throw new IllegalArgumentException("whenEmpty cannot be null");
    }

    public static NodeOptions defaults() {
        return new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.VANILLA);
    }
}
