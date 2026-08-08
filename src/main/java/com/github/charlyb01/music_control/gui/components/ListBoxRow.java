package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.impl.client.NarrationMessages;
import io.github.cottonmc.cotton.gui.widget.TooltipBuilder;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.data.InputResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.BooleanSupplier;

/** One compact, flat and keyboard-accessible ListBox row. */
final class ListBoxRow extends WLabel {
    private Component label = Component.empty();
    private BooleanSupplier selected = () -> false;
    private Runnable onSelect = () -> {};
    private boolean lastSelected;
    private boolean lastHovered;

    ListBoxRow() {
        super(Component.empty());
    }

    void configure(Component label, BooleanSupplier selected, Runnable onSelect) {
        this.label = label;
        this.selected = selected;
        this.onSelect = onSelect;
        refreshAppearance(true);
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public InputResult onClick(MouseButtonEvent click, boolean doubled) {
        if (!isWithinBounds((int) click.x(), (int) click.y())) return InputResult.IGNORED;
        activate();
        return InputResult.PROCESSED;
    }

    @Override
    public InputResult onKeyPressed(KeyEvent input) {
        if (!isActivationKey(input.key())) return InputResult.IGNORED;
        activate();
        return InputResult.PROCESSED;
    }

    private void activate() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        onSelect.run();
        refreshAppearance(true);
    }

    @Override
    public void tick() {
        super.tick();
        refreshAppearance(false);
    }

    private void refreshAppearance(boolean force) {
        boolean isSelected = selected.getAsBoolean();
        boolean hovered = isHovered() || isFocused();
        if (!force && isSelected == lastSelected && hovered == lastHovered) return;
        lastSelected = isSelected;
        lastHovered = hovered;
        String marker = isSelected ? "▶ " : hovered ? "› " : "  ";
        int markerWidth = Minecraft.getInstance().font.width(marker);
        setText(Component.literal(marker).append(
                CompactText.fit(label, getWidth() - markerWidth - 2)));
        setColor(isSelected ? 0xFFE0A020 : DEFAULT_TEXT_COLOR,
                isSelected ? 0xFFFFD060 : DEFAULT_DARKMODE_TEXT_COLOR);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        refreshAppearance(true);
    }

    @Override
    public void addNarrations(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, AbstractWidget.wrapDefaultNarrationMessage(label));
        if (isFocused()) {
            builder.add(NarratedElementType.USAGE, NarrationMessages.Vanilla.BUTTON_USAGE_FOCUSED);
        } else if (isHovered()) {
            builder.add(NarratedElementType.USAGE, NarrationMessages.Vanilla.BUTTON_USAGE_HOVERED);
        }
    }

    @Override
    public void addTooltip(TooltipBuilder builder) {
        builder.add(label);
    }
}
