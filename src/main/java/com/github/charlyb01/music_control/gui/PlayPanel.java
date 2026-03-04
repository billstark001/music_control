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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jsignal.rx.Effect;
import org.jsignal.rx.Signal;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlayPanel extends WBox {
    protected final static Text NONE_TEXT = Text.translatable("music.none");
    protected final static String SELECTED_KEY = "gui.music_control.label.selected";

    private final Signal<Identifier> selectedMusic = Signal.create(null);
    private final Signal<Button> focusedButton = Signal.create(null);
    @SuppressWarnings("unused")
    private final Effect labelEffect;

    public PlayPanel() {
        super(Axis.VERTICAL);
        this.setInsets(Insets.ROOT_PANEL);

        WLabel selected = new WLabel(Text.translatable(SELECTED_KEY, NONE_TEXT));
        selected.setHorizontalAlignment(HorizontalAlignment.CENTER);

        this.labelEffect = new Effect(() -> {
            Identifier id = selectedMusic.get();
            selected.setText(id == null
                    ? Text.translatable(SELECTED_KEY, NONE_TEXT)
                    : Text.translatable(SELECTED_KEY, Music.getTranslatedText(id)));
        });
        this.labelEffect.run();

        BiConsumer<Identifier, Button> onSoundClicked = (Identifier identifier, Button button) -> {
            if (identifier.equals(selectedMusic.get())) {
                MusicControlClient.nextMusic = false;
                MusicControlClient.musicSelected = null;
                Button prev = focusedButton.get();
                if (prev != null) prev.releaseFocus();
                focusedButton.accept(__ -> null);
                selectedMusic.accept(__ -> null);
            } else {
                MusicControlClient.nextMusic = true;
                MusicControlClient.musicSelected = identifier;
                Button prev = focusedButton.get();
                if (prev != null) prev.releaseFocus();
                focusedButton.accept(__ -> button);
                button.requestFocus();
                selectedMusic.accept(__ -> identifier);
            }
        };

        Consumer<Boolean> onToggle = (Boolean isEvent) -> {
            Button btn = focusedButton.get();
            if (btn != null) {
                btn.requestFocus();
            }
        };

        this.add(new SoundListPanel(onSoundClicked, onSoundClicked, onToggle, ModConfig.get().cosmetics.gui.width, false));
        this.add(selected, ModConfig.get().cosmetics.gui.width, 20);
    }
}
