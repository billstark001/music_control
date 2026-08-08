package com.github.charlyb01.music_control.api;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/** Stable entry point for third-party graph declarations. */
public final class MusicControlApi {
    private static final Map<Identifier, GraphContributor> CONTRIBUTORS = new LinkedHashMap<>();

    private MusicControlApi() {}

    /** Registers a declaration which is replayed for every resource reload. */
    public static synchronized void register(Identifier contributionId, GraphContributor contributor) {
        if (contributionId == null || contributor == null) {
            throw new IllegalArgumentException("contributionId and contributor are required");
        }
        if (CONTRIBUTORS.putIfAbsent(contributionId, contributor) != null) {
            throw new IllegalStateException("Duplicate music graph contribution: " + contributionId);
        }
    }

    /** Internal immutable view used while constructing a reload snapshot. */
    public static synchronized Map<Identifier, GraphContributor> contributors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CONTRIBUTORS));
    }
}
