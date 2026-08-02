package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.gui.components.Button;
import com.github.charlyb01.music_control.gui.components.SoundListPanel;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PlayPanel extends WBox {
    protected final static Component NONE_TEXT = Component.translatable("music.none");
    protected final static String SELECTED_KEY = "gui.music_control.label.selected";

    protected Button hoveredButton;

    public PlayPanel() {
        super(Axis.VERTICAL);
        this.setInsets(Insets.ROOT_PANEL);

        WLabel selected = new WLabel(Component.translatable(SELECTED_KEY, NONE_TEXT));
        selected.setHorizontalAlignment(HorizontalAlignment.CENTER);

        BiConsumer<Identifier, Button> onSoundClicked = (Identifier identifier, Button button) -> {
            if (identifier.equals(MusicControlClient.musicSelected)) {
                MusicControlClient.nextMusic = false;
                MusicControlClient.musicSelected = null;
                selected.setText(Component.translatable(SELECTED_KEY, NONE_TEXT));
            } else {
                MusicControlClient.nextMusic = true;
                MusicControlClient.musicSelected = identifier;
                selected.setText(Component.translatable(SELECTED_KEY, Music.getTranslatedText(identifier)));
            }

            if (this.hoveredButton != null) {
                this.hoveredButton.releaseFocus();
                if (this.hoveredButton.equals(button)) {
                    this.hoveredButton = null;
                } else {
                    this.hoveredButton = button;
                    this.hoveredButton.requestFocus();
                }
            } else {
                this.hoveredButton = button;
                this.hoveredButton.requestFocus();
            }
        };

        Consumer<Boolean> onToggle = (Boolean isEvent) -> {
            if (this.hoveredButton != null) {
                this.hoveredButton.requestFocus();
            }
        };

        this.add(new SoundListPanel(onSoundClicked, onSoundClicked, onToggle, ModConfig.get().cosmetics.gui.width, false));
        this.add(selected, ModConfig.get().cosmetics.gui.width, 20);
    }
}
