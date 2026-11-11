package com.github.charlyb01.music_control.gui.components;

import com.github.charlyb01.music_control.categories.Music;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WCardPanel;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static com.github.charlyb01.music_control.categories.Music.ALL_MUSICS;
import static com.github.charlyb01.music_control.categories.Music.EVENTS;
import static com.github.charlyb01.music_control.categories.Music.MUSIC_BY_NAMESPACE;

/** Compact searchable browser for music tracks and sound events. */
public final class SoundListPanel extends WBox {
    public enum Kind { MUSIC, EVENT }

    private final SearchableListBox<Identifier> musicList;
    private final SearchableListBox<Identifier> eventList;
    private final WCardPanel cards = new WCardPanel();
    private Kind kind;

    public SoundListPanel(
            BiConsumer<Identifier, SoundListPanel> onMusic,
            BiConsumer<Identifier, SoundListPanel> onEvent,
            BiConsumer<Kind, SoundListPanel> onKindChanged,
            int width,
            int height,
            Kind initialKind) {
        super(Axis.VERTICAL);
        setSpacing(0);
        this.kind = initialKind;

        ArrayList<Identifier> events = new ArrayList<>(EVENTS);
        ArrayList<Identifier> musics = new ArrayList<>();
        MUSIC_BY_NAMESPACE.getOrDefault(ALL_MUSICS, new java.util.HashSet<>())
                .forEach(music -> musics.add(music.getIdentifier()));

        int listHeight = height - SelectBox.HEIGHT;
        musicList = searchable(musics, width, listHeight);
        eventList = searchable(events, width, listHeight);
        musicList.setOnSelection((value, source) -> onMusic.accept(value, this));
        eventList.setOnSelection((value, source) -> onEvent.accept(value, this));
        cards.add(musicList);
        cards.add(eventList);

        SelectBox<Kind> kindSelect = new SelectBox<>(List.of(Kind.MUSIC, Kind.EVENT), initialKind,
                value -> Component.translatable("gui.music_control.browser.kind."
                        + value.name().toLowerCase(java.util.Locale.ROOT)));
        kindSelect.setOnChange(value -> {
            this.kind = value;
            showCurrentCard();
            clearSelection();
            onKindChanged.accept(value, this);
        });
        add(kindSelect, width, SelectBox.HEIGHT);
        add(cards, width, listHeight);
        showCurrentCard();
    }

    /** Event-only variant used by the graph editor. */
    public SoundListPanel(
            BiConsumer<Identifier, SoundListPanel> onEvent,
            int width,
            int height) {
        super(Axis.VERTICAL);
        setSpacing(0);
        this.kind = Kind.EVENT;
        ArrayList<Identifier> events = new ArrayList<>(EVENTS);
        this.eventList = searchable(events, width, height);
        this.musicList = null;
        eventList.setOnSelection((value, source) -> onEvent.accept(value, this));
        add(eventList, width, height);
    }

    private static SearchableListBox<Identifier> searchable(
            List<Identifier> values, int width, int height) {
        values.sort(Music.TRANSLATED_ORDER);
        return new SearchableListBox<>(values, Music::getTranslatedText,
                IdentifierSearchFilter.INSTANCE, width, height);
    }

    public Kind kind() {
        return kind;
    }

    public void clearSelection() {
        if (musicList != null) musicList.setSelected(null);
        eventList.setSelected(null);
    }

    public void setSelected(Identifier value) {
        if (kind == Kind.MUSIC && musicList != null) musicList.setSelected(value);
        else eventList.setSelected(value);
    }

    private void showCurrentCard() {
        cards.setSelectedCard(kind == Kind.EVENT ? eventList : musicList);
    }
}
