package com.github.charlyb01.music_control.gui.components;

import io.github.cottonmc.cotton.gui.widget.WBox;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public class TextFilter extends WBox {
    public static final int HEIGHT = 18;

    private static final Component CLEAR_TEXT = Component.nullToEmpty("×");
    private static final Component PLACEHOLDER_TEXT = Component.translatable("gui.component.filter.placeholder");

    private final Consumer<String> onChange;
    private final TextField textField;

    public void runOnChange() {
        var task = new Runnable() {
            @Override
            public void run() {
                onChange.accept(textField.getText());
            }
        };
        task.run();
    }

    public TextFilter(
        final Consumer<String> onChange,
        final int width
    ) {
        super(Axis.HORIZONTAL);
        this.onChange = onChange;
        this.setSpacing(2);

        ArrayList<Component> tooltips = new ArrayList<>(List.of(Component.translatable("gui.component.filter.tooltip"),
                Component.translatable("gui.component.filter.tooltip1")));
        this.textField = new TextField(PLACEHOLDER_TEXT, tooltips);
        this.textField.setChangedListener((s) -> runOnChange());

        WButton clearButton = new WButton(CLEAR_TEXT);
        clearButton.setOnClick(() -> this.textField.setText(""));

        this.add(this.textField, width - clearButton.getWidth() - 2, HEIGHT);
        this.add(clearButton, clearButton.getWidth(), HEIGHT);
    }
}
