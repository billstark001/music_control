package com.github.charlyb01.music_control.api;

@FunctionalInterface
public interface GraphContributor {
    void contribute(GraphRegistrar registrar);
}
