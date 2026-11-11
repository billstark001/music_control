package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.gui.components.SoundListPanel;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PlayPanel extends WBox {
    protected final static Component NONE_TEXT = Component.translatable("music.none");
    protected final static String SELECTED_KEY = "gui.music_control.label.selected";

    public PlayPanel() {
        super(Axis.VERTICAL);
        this.setInsets(new Insets(4));

        WLabel selected = new WLabel(Component.translatable(SELECTED_KEY, NONE_TEXT));
        selected.setHorizontalAlignment(HorizontalAlignment.CENTER);

        java.util.function.BiConsumer<Identifier, SoundListPanel> onSoundClicked = (identifier, browser) -> {
            if (identifier.equals(MusicControlClient.musicSelected)) {
                MusicControlClient.nextMusic = false;
                MusicControlClient.musicSelected = null;
                selected.setText(Component.translatable(SELECTED_KEY, NONE_TEXT));
                browser.clearSelection();
            } else {
                MusicControlClient.nextMusic = true;
                MusicControlClient.musicSelected = identifier;
                selected.setText(Component.translatable(SELECTED_KEY, Music.getTranslatedText(identifier)));
                browser.setSelected(identifier);
            }
        };

        SoundListPanel browser = new SoundListPanel(
                onSoundClicked,
                onSoundClicked,
                (ignored, source) -> source.setSelected(MusicControlClient.musicSelected),
                ModConfig.get().cosmetics.gui.width,
                ModConfig.get().cosmetics.gui.height - 20,
                SoundListPanel.Kind.MUSIC);
        browser.setSelected(MusicControlClient.musicSelected);
        this.add(browser);
        this.add(selected, ModConfig.get().cosmetics.gui.width, 20);
    }
}
