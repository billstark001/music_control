package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.widget.WWidget;

public interface PopupHost {
    void showPopup(WWidget popup, int x, int y, int width, int height);
    void closePopup();
}
