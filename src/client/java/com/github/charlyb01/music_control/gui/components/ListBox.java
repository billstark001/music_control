package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.client.BackgroundPainter;
import io.github.cottonmc.cotton.gui.client.ScreenDrawing;
import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** A compact, single-selection list box backed by LibGui's virtualized list. */
public class ListBox<T> extends WBox {
    public static final int ROW_HEIGHT = 13;
    public static final int VERTICAL_PADDING = 2;
    private static final BackgroundPainter BACKGROUND = (context, left, top, panel) -> {
        int fill = panel.shouldRenderInDarkMode() ? 0xFF202020 : 0xFFC6C6C6;
        int outline = panel.shouldRenderInDarkMode() ? 0xFF909090 : 0xFF404040;
        ScreenDrawing.coloredRect(context, left, top, panel.getWidth(), panel.getHeight(), fill);
        ScreenDrawing.coloredRect(context, left, top, panel.getWidth(), 1, outline);
        ScreenDrawing.coloredRect(context, left, top + panel.getHeight() - 1, panel.getWidth(), 1, outline);
        ScreenDrawing.coloredRect(context, left, top, 1, panel.getHeight(), outline);
        ScreenDrawing.coloredRect(context, left + panel.getWidth() - 1, top, 1, panel.getHeight(), outline);
    };

    private final List<T> data;
    private final FilterListPanel<T, ListBoxRow> rows;
    private final Function<T, Component> label;
    private BiConsumer<T, ListBox<T>> onSelection = (value, source) -> {};
    private T selected;

    public ListBox(List<T> data, Function<T, Component> label, int width, int height) {
        super(Axis.VERTICAL);
        this.data = Objects.requireNonNull(data);
        this.label = Objects.requireNonNull(label);
        setSpacing(0);

        this.rows = new FilterListPanel<>(data, ListBoxRow::new, this::configureRow);
        this.rows.setListItemHeight(ROW_HEIGHT);
        this.rows.setGap(0);
        this.rows.setInsets(new Insets(VERTICAL_PADDING, 3));
        this.rows.setBackgroundPainter(BACKGROUND);
        add(this.rows, width, height);
    }

    private void configureRow(T value, ListBoxRow row) {
        row.configure(label.apply(value), () -> Objects.equals(selected, value), () -> {
            selected = value;
            onSelection.accept(value, this);
        });
    }

    public List<T> data() {
        return data;
    }

    public T selected() {
        return selected;
    }

    public void setSelected(T selected) {
        this.selected = selected;
    }

    public void setOnSelection(BiConsumer<T, ListBox<T>> onSelection) {
        this.onSelection = Objects.requireNonNull(onSelection);
    }

    public void setFilter(Predicate<T> filter) {
        rows.runFilter(filter);
    }

    public void refresh() {
        rows.refresh();
    }
}
