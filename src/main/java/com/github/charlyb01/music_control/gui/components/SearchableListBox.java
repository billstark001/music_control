package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** ListBox with a shared search field and pluggable filter semantics. */
public final class SearchableListBox<T> extends WBox {
    private final ListBox<T> listBox;
    private final TextFilter textFilter;
    private final SearchFilter<T> filter;

    public SearchableListBox(
            List<T> data,
            Function<T, Component> label,
            SearchFilter<T> filter,
            int width,
            int height) {
        super(Axis.VERTICAL);
        this.filter = filter;
        setSpacing(0);
        this.listBox = new ListBox<>(data, label, width, height - TextFilter.HEIGHT);
        this.textFilter = new TextFilter(this::applyFilter, width);
        add(textFilter, width, TextFilter.HEIGHT);
        add(listBox, width, height - TextFilter.HEIGHT);
    }

    private void applyFilter(String query) {
        listBox.setFilter(value -> filter.matches(query, value));
    }

    public List<T> data() {
        return listBox.data();
    }

    public T selected() {
        return listBox.selected();
    }

    public void setSelected(T selected) {
        listBox.setSelected(selected);
    }

    public void setOnSelection(BiConsumer<T, ListBox<T>> onSelection) {
        listBox.setOnSelection(onSelection);
    }

    public void refresh() {
        textFilter.runOnChange();
    }
}
