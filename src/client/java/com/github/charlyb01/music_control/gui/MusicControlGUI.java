package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.gui.components.OverlayPanel;
import com.github.charlyb01.music_control.gui.components.PopupHost;
import com.github.charlyb01.music_control.gui.components.SoundConfigPanel;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WTabPanel;
import io.github.cottonmc.cotton.gui.widget.WWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class MusicControlGUI extends LightweightGuiDescription implements PopupHost {
    private final OverlayPanel overlay;

    public MusicControlGUI(final Minecraft client) {
        this(client, 0);
    }

    public MusicControlGUI(final Minecraft client, final int selectedTab) {
        MusicConfigDraft draft = new MusicConfigDraft();
        SoundConfigPanel.EditorState editorState = new SoundConfigPanel.EditorState();
        WTabPanel tabs = new WTabPanel();
        tabs.add(new PlayPanel(), tab -> tab.title(Component.translatable("gui.music_control.panel.play")));
        tabs.add(new HistoryPanel(), tab -> tab.title(Component.translatable("gui.music_control.panel.history")));
        tabs.add(new ConfigPanel(client, ConfigPanel.Page.TRACKS, 2, draft, editorState),
                tab -> tab.title(Component.translatable("gui.music_control.config_page.tracks")));
        tabs.add(new ConfigPanel(client, ConfigPanel.Page.GRAPH, 3, draft, editorState),
                tab -> tab.title(Component.translatable("gui.music_control.config_page.graph")));
        tabs.setSelectedIndex(selectedTab);
        this.overlay = new OverlayPanel(tabs);
        this.setRootPanel(overlay);
    }

    @Override
    public void showPopup(WWidget popup, int x, int y, int width, int height) {
        overlay.showPopup(popup, x, y, width, height);
    }

    @Override
    public void closePopup() {
        overlay.closePopup();
    }
}
