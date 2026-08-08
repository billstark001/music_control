package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.GuiDescription;
import io.github.cottonmc.cotton.gui.widget.WPanel;
import io.github.cottonmc.cotton.gui.widget.WWidget;
import io.github.cottonmc.cotton.gui.widget.data.InputResult;
import net.minecraft.client.input.MouseButtonEvent;

/** Root panel that can place a dismissible popup above normal content. */
public final class OverlayPanel extends WPanel implements PopupHost {
    private final WWidget content;
    private WWidget popup;
    private final WWidget dismissLayer = new WWidget() {
        @Override
        public InputResult onClick(MouseButtonEvent click, boolean doubled) {
            closePopup();
            return InputResult.PROCESSED;
        }

        @Override
        public boolean canHover() {
            return false;
        }
    };

    public OverlayPanel(WWidget content) {
        this.content = content;
        content.setParent(this);
        children.add(content);
        if (content instanceof WPanel panel) panel.layout();
        setSize(content.getWidth(), content.getHeight());
    }

    @Override
    public void showPopup(WWidget popup, int x, int y, int width, int height) {
        closePopup();
        this.popup = popup;
        int popupX = Math.max(0, Math.min(x, getWidth() - width));
        int popupY = Math.max(0, Math.min(y, getHeight() - height));

        dismissLayer.setParent(this);
        dismissLayer.setLocation(0, 0);
        dismissLayer.setSize(getWidth(), getHeight());
        popup.setParent(this);
        popup.setLocation(popupX, popupY);
        popup.setSize(width, height);
        children.add(dismissLayer);
        children.add(popup);
        GuiDescription currentHost = getHost();
        if (currentHost != null) {
            dismissLayer.setHost(currentHost);
            popup.setHost(currentHost);
        }
        layout();
    }

    @Override
    public void closePopup() {
        if (popup == null) return;
        popup.onHidden();
        children.remove(popup);
        children.remove(dismissLayer);
        popup = null;
    }

    @Override
    public void layout() {
        content.setLocation(0, 0);
        if (content instanceof WPanel panel) panel.layout();
        if (popup instanceof WPanel panel) panel.layout();
    }
}
