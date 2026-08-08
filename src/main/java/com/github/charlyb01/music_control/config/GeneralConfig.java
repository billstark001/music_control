package com.github.charlyb01.music_control.config;

import com.github.charlyb01.music_control.categories.Music;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "general")
public class GeneralConfig implements ConfigData {
    @ConfigEntry.Gui.CollapsibleObject
    public Timer timer = new Timer();

    public static class Timer {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public BiomeSwitchBehavior changeMusicOnBiomeSwitch = BiomeSwitchBehavior.IF_INCOMPATIBLE;
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
        public int fadeOutDuration = 5;
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
        public int fadeInDuration = 5;
    }

    @ConfigEntry.Gui.CollapsibleObject
    public Misc misc = new Misc();

    public static class Misc {
        @ConfigEntry.Gui.Tooltip(count = 2)
        @ConfigEntry.BoundedDiscrete(min = 1, max = 25)
        public int musicQueue = 10;
        @ConfigEntry.Gui.Tooltip(count = 5)
        public String musicCategoryStart = Music.DEFAULT_MUSICS;
        @ConfigEntry.Gui.Tooltip()
        @ConfigEntry.BoundedDiscrete(min = 1, max = 100)
        public int volumeIncrement = 5;
    }
}
