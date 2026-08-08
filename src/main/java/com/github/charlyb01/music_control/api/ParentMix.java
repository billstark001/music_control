package com.github.charlyb01.music_control.api;

/** How a non-empty node combines its direct pool with the aggregate parent pool. */
public enum ParentMix {
    EXCLUSIVE,
    HALF,
    PROPORTIONAL,
    PARENTS_ONLY
}
