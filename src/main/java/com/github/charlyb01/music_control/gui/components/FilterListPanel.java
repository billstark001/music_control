package com.github.charlyb01.music_control.gui.components;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.cottonmc.cotton.gui.widget.WListPanel;
import io.github.cottonmc.cotton.gui.widget.WWidget;

public class FilterListPanel<D, W extends WWidget> extends WListPanel<D, W> {

    private final List<D> source;
    private Predicate<D> activeFilter;

    public FilterListPanel(List<D> data, Supplier<W> supplier, BiConsumer<D, W> configurator) {
        super(data, supplier, configurator);
        this.source = data;
    }

    public void runFilter(Predicate<D> filter) {
        this.activeFilter = filter;
        refresh();
    }

    public void refresh() {
        Predicate<D> filter = this.activeFilter;
        if (filter == null) {
            this.data = this.source;
            this.layout();
            return;
        }

        ArrayList<D> dataResult = new ArrayList<>();
        for (var item : this.source) {
            if (filter.test(item)) {
                dataResult.add(item);
            }
        }

        this.data = dataResult;
        this.layout();
    }
}
