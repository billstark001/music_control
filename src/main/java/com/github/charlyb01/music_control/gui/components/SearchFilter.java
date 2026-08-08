package com.github.charlyb01.music_control.gui.components;

/** Shared search contract used by every searchable list. */
@FunctionalInterface
public interface SearchFilter<T> {
    boolean matches(String query, T value);
}
