package com.github.charlyb01.music_control.gui;

import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WTabPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class MusicControlGUI extends LightweightGuiDescription {

    public MusicControlGUI(final Minecraft client) {
        WTabPanel tabs = new WTabPanel();
        tabs.add(new PlayPanel(), tab -> tab.title(Component.translatable("gui.music_control.panel.play")));
        tabs.add(new HistoryPanel(), tab -> tab.title(Component.translatable("gui.music_control.panel.history")));
        tabs.add(new ConfigPanel(client), tab -> tab.title(Component.translatable("gui.music_control.panel.config")));
        tabs.setHost(this);
        this.setRootPanel(tabs);
    }
}
