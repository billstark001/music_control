package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.widget.TooltipBuilder;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Compact HTML-select-like control with an overlay ListBox popup. */
public final class SelectBox<T> extends WButton {
    public static final int HEIGHT = 20;

    private final List<T> options;
    private final Function<T, Component> label;
    private final List<Component> tooltip = new ArrayList<>();
    private Consumer<T> onChange = ignored -> {};
    private T value;

    public SelectBox(List<T> options, T value, Function<T, Component> label) {
        this.options = List.copyOf(options);
        if (this.options.isEmpty()) throw new IllegalArgumentException("SelectBox needs at least one option");
        this.label = Objects.requireNonNull(label);
        this.value = Objects.requireNonNull(value);
        setAlignment(HorizontalAlignment.LEFT);
        refreshLabel();
        setOnClick(this::open);
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        setValue(value, false);
    }

    public void setOnChange(Consumer<T> onChange) {
        this.onChange = Objects.requireNonNull(onChange);
    }

    public SelectBox<T> addTooltip(Component line) {
        tooltip.add(line);
        return this;
    }

    private void open() {
        if (getHost() instanceof PopupHost popupHost) {
            int height = Math.min(options.size(), 8) * ListBox.ROW_HEIGHT
                    + ListBox.VERTICAL_PADDING * 2;
            ListBox<T> choices = new ListBox<>(new ArrayList<>(options), label, getWidth(), height);
            choices.setSelected(value);
            choices.setOnSelection((choice, source) -> {
                setValue(choice, true);
                popupHost.closePopup();
            });
            popupHost.showPopup(choices, getAbsoluteX(), getAbsoluteY() + getHeight(), getWidth(), height);
            return;
        }

        int index = options.indexOf(value);
        setValue(options.get((index + 1) % options.size()), true);
    }

    private void setValue(T value, boolean notify) {
        if (!options.contains(value)) throw new IllegalArgumentException("Unknown SelectBox value: " + value);
        this.value = value;
        refreshLabel();
        if (notify) onChange.accept(value);
    }

    private void refreshLabel() {
        Component arrow = Component.literal(" ▾");
        int reserved = Minecraft.getInstance().font.width("   ▾") + 4;
        setLabel(Component.literal("  ")
                .append(CompactText.fit(label.apply(value), getWidth() - reserved))
                .append(arrow));
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        refreshLabel();
    }

    @Override
    public void addTooltip(TooltipBuilder builder) {
        super.addTooltip(builder);
        builder.add(label.apply(value));
        tooltip.forEach(builder::add);
    }
}
