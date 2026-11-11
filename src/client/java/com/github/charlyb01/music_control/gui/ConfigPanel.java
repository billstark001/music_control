package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.ResourcePackUtils;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.gui.components.SoundConfigPanel;
import com.github.charlyb01.music_control.gui.components.SoundListPanel;
import com.github.charlyb01.music_control.platform.ClientCompat;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WCardPanel;
import io.github.cottonmc.cotton.gui.widget.WText;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

public final class ConfigPanel extends WBox {
    public enum Page { TRACKS, GRAPH }

    private static SoundListPanel.Kind basicKind = SoundListPanel.Kind.EVENT;

    private final WCardPanel cardPanel = new WCardPanel();
    private final WBox resourcePackCard = new WBox(Axis.VERTICAL).setSpacing(2);
    private final WBox musicConfigCard = new WBox(Axis.VERTICAL).setSpacing(0);
    private final Minecraft client;
    private final Page page;
    private final int mainTabIndex;
    private final MusicConfigDraft draft;
    private final SoundConfigPanel.EditorState editorState;
    private Screen previousScreen;

    private final class Editor {
        private final SoundConfigPanel.Page editorPage;
        private final WBox body = new WBox(Axis.HORIZONTAL).setSpacing(2);
        private final SoundListPanel browser;
        private final int columnWidth;
        private final int height;
        private SoundConfigPanel details;

        private Editor(int width, int height) {
            this.editorPage = page == Page.GRAPH
                    ? SoundConfigPanel.Page.GRAPH : SoundConfigPanel.Page.BASIC;
            this.columnWidth = (width - 2) / 2;
            this.height = height;
            if (page == Page.TRACKS) {
                this.browser = new SoundListPanel(
                        this::select,
                        this::select,
                        (kind, source) -> {
                            basicKind = kind;
                            clearDetails();
                        },
                        columnWidth,
                        height,
                        basicKind);
            } else {
                this.browser = new SoundListPanel(this::select, columnWidth, height);
            }
            body.add(browser, columnWidth, height);
            body.layout();
        }

        private void select(Identifier identifier, SoundListPanel source) {
            clearDetails();
            source.setSelected(identifier);
            boolean fromEvent = page == Page.GRAPH
                    || source.kind() == SoundListPanel.Kind.EVENT;
            details = new SoundConfigPanel(
                    identifier,
                    fromEvent,
                    editorPage,
                    columnWidth,
                    height,
                    draft,
                    editorState);
            body.add(details, columnWidth, height);
            if (body.getHost() != null) details.setHost(body.getHost());
            body.layout();
        }

        private void clearDetails() {
            if (details == null) return;
            body.remove(details);
            details = null;
            body.layout();
        }
    }

    public ConfigPanel(
            Minecraft client,
            Page page,
            int mainTabIndex,
            MusicConfigDraft draft,
            SoundConfigPanel.EditorState editorState) {
        super(Axis.VERTICAL);
        setInsets(new Insets(4));
        setHorizontalAlignment(HorizontalAlignment.LEFT);
        setSpacing(0);
        this.client = client;
        this.page = page;
        this.mainTabIndex = mainTabIndex;
        this.draft = draft;
        this.editorState = editorState;

        setupResourcePackPanel();
        setupEditorPanel();
        cardPanel.add(resourcePackCard);
        cardPanel.add(musicConfigCard);
        add(cardPanel);
        updateLayout();
    }

    @Override
    public void onShown() {
        super.onShown();
        updateLayout();
    }

    private void updateLayout() {
        if (ResourcePackUtils.wasCreatedOrIsEnabled()) {
            musicConfigCard.layout();
            cardPanel.setSelectedCard(musicConfigCard);
        } else {
            resourcePackCard.layout();
            cardPanel.setSelectedCard(resourcePackCard);
        }
    }

    private void backToMusicScreen(PackRepository manager) {
        client.options.updateResourcePacks(manager);
        Screen targetScreen = previousScreen;
        ClientCompat.setScreen(client, targetScreen);
        previousScreen = null;

        if (!ResourcePackUtils.wasCreatedOrIsEnabled()) {
            updateLayout();
            return;
        }

        client.reloadResourcePacks().whenComplete((ignored, exception) ->
                client.execute(() -> {
                    if (ClientCompat.getScreen(client) == targetScreen) {
                        ClientCompat.setScreen(client,
                                new MusicControlScreen(new MusicControlGUI(client, mainTabIndex)));
                    }
                }));
    }

    private void setupResourcePackPanel() {
        WText text = new WText(Component.translatable("gui.music_control.label.resourcePack"));
        text.setHorizontalAlignment(HorizontalAlignment.CENTER);

        WButton enableButton = new WButton(Component.translatable("gui.music_control.button.enable"));
        enableButton.setEnabled(ResourcePackUtils.exists());
        enableButton.setOnClick(() -> {
            previousScreen = ClientCompat.getScreen(client);
            ClientCompat.setScreen(client, new PackSelectionScreen(
                    client.getResourcePackRepository(),
                    this::backToMusicScreen,
                    client.getResourcePackDirectory(),
                    Component.translatable("resourcePack.title")));
        });

        WButton createButton = new WButton(Component.translatable("gui.music_control.button.create"));
        createButton.setOnClick(() -> {
            ResourcePackUtils.createResourcePack();
            updateLayout();
        });

        WBox buttons = new WBox(Axis.HORIZONTAL).setSpacing(4);
        buttons.setHorizontalAlignment(HorizontalAlignment.CENTER);
        buttons.add(enableButton, 100, 20);
        buttons.add(createButton, 100, 20);
        resourcePackCard.add(text, ModConfig.get().cosmetics.gui.width, 18);
        resourcePackCard.add(buttons, ModConfig.get().cosmetics.gui.width, 20);
    }

    private void setupEditorPanel() {
        int width = ModConfig.get().cosmetics.gui.width;
        int actionRowHeight = 22;
        int editorHeight = ModConfig.get().cosmetics.gui.height - actionRowHeight;
        Editor editor = new Editor(width, editorHeight);

        WButton saveButton = new WButton(Component.translatable("gui.music_control.button.save"));
        saveButton.setOnClick(() -> {
            var graph = draft.graphSnapshot();
            saveButton.setEnabled(false);
            draft.apply();
            ResourcePackUtils.writeConfig(graph);
            client.reloadResourcePacks().whenComplete((ignored, exception) ->
                    client.execute(() -> saveButton.setEnabled(true)));
        });

        WBox buttons = new WBox(Axis.HORIZONTAL).setSpacing(4);
        buttons.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        buttons.setVerticalAlignment(VerticalAlignment.BOTTOM);
        if (ResourcePackUtils.needsMigration()) {
            WButton migrateButton = new WButton(Component.translatable("gui.music_control.button.migrate"));
            migrateButton.setOnClick(() -> {
                Screen current = ClientCompat.getScreen(client);
                ClientCompat.setScreen(client, new MigrateScreen(current));
            });
            buttons.add(migrateButton, 100, 20);
        }
        buttons.add(saveButton, 100, 20);

        musicConfigCard.add(editor.body, width, editorHeight);
        musicConfigCard.add(buttons, width, actionRowHeight);
    }
}
