package com.github.charlyb01.music_control.gui.components;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.ParentMix;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.gui.MusicConfigDraft;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WCardPanel;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.github.charlyb01.music_control.categories.Music.ALL_MUSICS;
import static com.github.charlyb01.music_control.categories.Music.EVENTS;
import static com.github.charlyb01.music_control.categories.Music.MUSIC_BY_NAMESPACE;
import static com.github.charlyb01.music_control.categories.Music.TRANSLATED_ORDER;

/** Compact transactional editor for either track rules or event-graph routing. */
public final class SoundConfigPanel extends WBox {
    public enum Page { BASIC, GRAPH }

    /** Per-screen navigation state retained while switching selected events. */
    public static final class EditorState {
        private Action action = Action.REMOVE;
        private int basicMode;
        private int graphMode;
    }

    private enum Action { ADD, REMOVE }

    private enum EditMode {
        TRACKS("tracks"),
        SOUND_LINKS("sound_links"),
        PARENTS("parents"),
        BIOMES("biomes"),
        DIMENSIONS("dimensions");

        private final String key;

        EditMode(String key) {
            this.key = key;
        }
    }

    private final WCardPanel cards = new WCardPanel();
    private final EnumMap<EditMode, SearchableListBox<Identifier>> addLists = new EnumMap<>(EditMode.class);
    private final EnumMap<EditMode, SearchableListBox<Identifier>> removeLists = new EnumMap<>(EditMode.class);
    private final MusicConfigDraft draft;
    private final Identifier subject;
    private final boolean fromEvent;
    private final Page page;
    private final List<EditMode> modes;
    private final EditorState state;
    private EditMode mode;

    public SoundConfigPanel(
            Identifier subject,
            boolean fromEvent,
            Page page,
            int width,
            int height,
            MusicConfigDraft draft,
            EditorState state) {
        super(Axis.VERTICAL);
        setSpacing(0);
        this.subject = subject;
        this.fromEvent = fromEvent;
        this.page = page;
        this.draft = draft;
        this.state = state;
        this.modes = page == Page.GRAPH
                ? List.of(EditMode.PARENTS, EditMode.BIOMES, EditMode.DIMENSIONS)
                : fromEvent ? List.of(EditMode.TRACKS, EditMode.SOUND_LINKS) : List.of(EditMode.TRACKS);
        int storedMode = page == Page.GRAPH ? state.graphMode : state.basicMode;
        this.mode = modes.get(Math.min(storedMode, modes.size() - 1));

        int controlsHeight = SelectBox.HEIGHT;
        if (page == Page.GRAPH) {
            draft.ensureGraphNode(subject);
            setupGraphOptions(width);
            controlsHeight += SelectBox.HEIGHT * 2;
        }
        setupModeBar(width);
        setupLists(width, Math.max(ListBox.ROW_HEIGHT + TextFilter.HEIGHT, height - controlsHeight));
        add(cards, width, Math.max(ListBox.ROW_HEIGHT + TextFilter.HEIGHT, height - controlsHeight));
        selectCurrentList();
    }

    private void setupGraphOptions(int width) {
        SelectBox<ParentMix> parentMix = new SelectBox<>(
                List.of(ParentMix.values()), draft.parentMix(subject), SoundConfigPanel::parentMixLabel);
        parentMix.addTooltip(Component.translatable("gui.music_control.graph.parent_mix.tooltip"));
        parentMix.setOnChange(value -> draft.setParentMix(subject, value));

        SelectBox<EmptyBehavior> whenEmpty = new SelectBox<>(
                List.of(EmptyBehavior.values()), draft.whenEmpty(subject), SoundConfigPanel::emptyLabel);
        whenEmpty.addTooltip(Component.translatable("gui.music_control.graph.when_empty.tooltip"));
        whenEmpty.setOnChange(value -> draft.setWhenEmpty(subject, value));

        add(parentMix, width, SelectBox.HEIGHT);
        add(whenEmpty, width, SelectBox.HEIGHT);
    }

    private void setupModeBar(int width) {
        SelectBox<Action> action = new SelectBox<>(List.of(Action.values()), state.action,
                value -> Component.translatable("gui.music_control.graph.action."
                        + value.name().toLowerCase(Locale.ROOT)));
        action.setOnChange(value -> {
            state.action = value;
            selectCurrentList();
        });

        WBox bar = new WBox(Axis.HORIZONTAL).setSpacing(0);
        if (modes.size() > 1) {
            SelectBox<EditMode> modeSelect = new SelectBox<>(modes, mode, SoundConfigPanel::modeLabel);
            modeSelect.addTooltip(Component.translatable("gui.music_control.graph.mode.tooltip"));
            modeSelect.setOnChange(value -> {
                mode = value;
                if (page == Page.GRAPH) state.graphMode = modes.indexOf(value);
                else state.basicMode = modes.indexOf(value);
                selectCurrentList();
            });
            bar.add(action, width / 2, SelectBox.HEIGHT);
            bar.add(modeSelect, width - width / 2, SelectBox.HEIGHT);
        } else if (fromEvent) {
            bar.add(action, width, SelectBox.HEIGHT);
        } else {
            bar.add(action, width / 2, SelectBox.HEIGHT);
            WLabel assignment = new WLabel(Component.translatable(
                    "gui.music_control.graph.mode.event_assignments"));
            assignment.setVerticalAlignment(VerticalAlignment.CENTER);
            bar.add(assignment, width - width / 2, SelectBox.HEIGHT);
        }
        add(bar, width, SelectBox.HEIGHT);
    }

    private void setupLists(int width, int height) {
        for (EditMode editMode : modes) {
            ArrayList<Identifier> removing = removalValues(editMode);
            ArrayList<Identifier> adding = additionValues(editMode, removing);
            SearchableListBox<Identifier> add = searchable(adding, width, height);
            SearchableListBox<Identifier> remove = searchable(removing, width, height);

            add.setOnSelection((value, source) -> {
                if (!addValue(editMode, value)) return;
                move(value, adding, removing);
                source.setSelected(null);
                add.refresh();
                remove.refresh();
                refreshParentCandidates(editMode, adding, removing);
            });
            remove.setOnSelection((value, source) -> {
                removeValue(editMode, value);
                move(value, removing, adding);
                source.setSelected(null);
                remove.refresh();
                add.refresh();
                refreshParentCandidates(editMode, adding, removing);
            });
            addLists.put(editMode, add);
            removeLists.put(editMode, remove);
            cards.add(remove);
            cards.add(add);
        }
    }

    private static SearchableListBox<Identifier> searchable(
            List<Identifier> values, int width, int height) {
        values.sort(TRANSLATED_ORDER);
        return new SearchableListBox<>(values, Music::getTranslatedText,
                IdentifierSearchFilter.INSTANCE, width, height);
    }

    private ArrayList<Identifier> removalValues(EditMode editMode) {
        return switch (editMode) {
            case TRACKS -> new ArrayList<>(fromEvent
                    ? draft.sounds(subject)
                    : draft.eventsForSound(subject));
            case SOUND_LINKS -> new ArrayList<>(draft.linkedEvents(subject));
            case PARENTS -> new ArrayList<>(draft.graphParents(subject));
            case BIOMES -> new ArrayList<>(draft.biomesForNode(subject));
            case DIMENSIONS -> new ArrayList<>(draft.dimensionsForNode(subject));
        };
    }

    private ArrayList<Identifier> additionValues(EditMode editMode, List<Identifier> removing) {
        Set<Identifier> candidates = new HashSet<>();
        switch (editMode) {
            case TRACKS -> {
                if (fromEvent) {
                    MUSIC_BY_NAMESPACE.getOrDefault(ALL_MUSICS, new HashSet<>())
                            .forEach(music -> candidates.add(music.getIdentifier()));
                } else {
                    candidates.addAll(EVENTS);
                }
            }
            case SOUND_LINKS -> candidates.addAll(EVENTS);
            case PARENTS -> {
                candidates.addAll(EVENTS);
                candidates.addAll(draft.graphNodes());
                candidates.removeIf(candidate -> !draft.canAddGraphParent(subject, candidate));
            }
            case BIOMES -> candidates.addAll(SoundEventRegistry.NAME_BIOME_MAP.keySet());
            case DIMENSIONS -> {
                candidates.addAll(draft.dimensions());
                Minecraft client = Minecraft.getInstance();
                if (client.getConnection() != null) {
                    client.getConnection().levels().forEach(level -> candidates.add(level.identifier()));
                }
            }
        }
        candidates.remove(subject);
        candidates.removeAll(removing);
        ArrayList<Identifier> result = new ArrayList<>(candidates);
        result.sort(TRANSLATED_ORDER);
        return result;
    }

    private boolean addValue(EditMode editMode, Identifier value) {
        switch (editMode) {
            case TRACKS -> {
                if (fromEvent) draft.addSound(subject, value);
                else draft.addSound(value, subject);
            }
            case SOUND_LINKS -> draft.addLinkedEvent(subject, value);
            case PARENTS -> {
                return draft.addGraphParent(subject, value);
            }
            case BIOMES -> draft.bindBiome(value, subject);
            case DIMENSIONS -> draft.bindDimension(value, subject);
        }
        return true;
    }

    private void removeValue(EditMode editMode, Identifier value) {
        switch (editMode) {
            case TRACKS -> {
                if (fromEvent) draft.removeSound(subject, value);
                else draft.removeSound(value, subject);
            }
            case SOUND_LINKS -> draft.removeLinkedEvent(subject, value);
            case PARENTS -> draft.removeGraphParent(subject, value);
            case BIOMES -> draft.unbindBiome(value, subject);
            case DIMENSIONS -> draft.unbindDimension(value, subject);
        }
    }

    private void refreshParentCandidates(
            EditMode editMode, List<Identifier> adding, List<Identifier> removing) {
        if (editMode != EditMode.PARENTS) return;
        adding.clear();
        adding.addAll(additionValues(EditMode.PARENTS, removing));
        addLists.get(EditMode.PARENTS).refresh();
    }

    private static void move(Identifier value, List<Identifier> source, List<Identifier> destination) {
        source.remove(value);
        if (!destination.contains(value)) destination.add(value);
        destination.sort(TRANSLATED_ORDER);
    }

    private void selectCurrentList() {
        SearchableListBox<Identifier> list = state.action == Action.ADD
                ? addLists.get(mode) : removeLists.get(mode);
        if (list != null) cards.setSelectedCard(list);
    }

    private static Component modeLabel(EditMode value) {
        return Component.translatable("gui.music_control.graph.mode." + value.key);
    }

    private static Component parentMixLabel(ParentMix value) {
        return Component.translatable(
                "gui.music_control.graph.parent_mix",
                Component.translatable("gui.music_control.graph.parent_mix."
                        + value.name().toLowerCase(Locale.ROOT)));
    }

    private static Component emptyLabel(EmptyBehavior value) {
        return Component.translatable(
                "gui.music_control.graph.when_empty",
                Component.translatable("gui.music_control.graph.when_empty."
                        + value.name().toLowerCase(Locale.ROOT)));
    }
}
