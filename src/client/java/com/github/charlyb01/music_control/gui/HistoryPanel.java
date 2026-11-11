package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.gui.components.ListBox;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.resources.Identifier;

import static com.github.charlyb01.music_control.categories.MusicCategories.PLAYED_MUSICS;

public class HistoryPanel extends WBox {
    public HistoryPanel() {
        super(Axis.VERTICAL);
        this.setInsets(new Insets(4));

        ArrayList<Identifier> musics = new ArrayList<>(PLAYED_MUSICS);
        Collections.reverse(musics);

        ListBox<Identifier> playedList = new ListBox<>(
                musics,
                Music::getTranslatedText,
                ModConfig.get().cosmetics.gui.width,
                ModConfig.get().cosmetics.gui.height);
        playedList.setOnSelection((identifier, source) -> {
            PLAYED_MUSICS.remove(identifier);
            musics.remove(identifier);
            source.setSelected(null);
            source.refresh();
        });

        this.add(playedList);
    }
}
