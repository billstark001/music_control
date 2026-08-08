package com.github.charlyb01.music_control.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "cosmetics")
public class CosmeticsConfig implements ConfigData {
    @ConfigEntry.Gui.CollapsibleObject
    public Display display = new Display();

    public static class Display {
        @ConfigEntry.Gui.Tooltip
        public boolean showMusicToast = true;
        public boolean atMusicStart = true;
        public boolean remainingSeconds = false;
        @ConfigEntry.Gui.Tooltip()
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public DisplayType type = DisplayType.JUKEBOX;
    }

    @ConfigEntry.Gui.CollapsibleObject
    public Gui gui = new Gui();

    public static class Gui {
        @ConfigEntry.BoundedDiscrete(min = 140, max = 300)
        public int height = 180;
        @ConfigEntry.BoundedDiscrete(min = 300, max = 600)
        public int width = 360;
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public ScrollSpeed scrollSpeed = ScrollSpeed.NORMAL;
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public FilterOperator filterOperator = FilterOperator.AND;
    }
}
