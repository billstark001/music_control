package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.ResourcePackUtils;
import com.github.charlyb01.music_control.config.ModConfig;
import io.github.cottonmc.cotton.gui.client.CottonClientScreen;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.*;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * A confirmation dialog that lets the user choose how to migrate the
 * music configuration to the current Minecraft version.
 *
 * <ul>
 *   <li><b>In-place</b> – overwrites the existing resource pack.</li>
 *   <li><b>New resource pack</b> – creates a fresh resource pack with a
 *       version-stamped name and applies the migrated config there.</li>
 * </ul>
 *
 * <p>After a successful migration the parent (music) screen is closed so that
 * it is rebuilt fresh on the next open — ensuring the "Migrate" button no
 * longer appears once the migration is done.</p>
 *
 * <p>Layout Note: LibGUI's WBox + CENTER centering has coordinate calculation bugs during layout phase,
 * and WToggleButton additionally has rendering coordinate vs bbox inconsistency bugs.
 * Using WPlainPanel with absolute positioning can completely avoid the above issues.</p>
 */
public class MigrateScreen extends CottonClientScreen {

    public MigrateScreen(Screen previousScreen) {
        super(buildGui(previousScreen));
    }

    private static LightweightGuiDescription buildGui(Screen previousScreen) {
        LightweightGuiDescription desc = new LightweightGuiDescription();

        final MinecraftClient client = MinecraftClient.getInstance();
        final int dialogWidth = Math.max(ModConfig.get().cosmetics.gui.width, 380);

        // Read padding on all sides from ROOT_PANEL (typically 8px each)
        final int IL = Insets.ROOT_PANEL.left();
        final int IT = Insets.ROOT_PANEL.top();
        final int IR = Insets.ROOT_PANEL.right();
        final int IB = Insets.ROOT_PANEL.bottom();
        final int cw = dialogWidth - IL - IR;  // Available content width

        String storedVersion  = ResourcePackUtils.readMetadataVersion();
        String currentVersion = net.minecraft.SharedConstants.getGameVersion().name();
        final String captured = storedVersion;

        WPlainPanel root = new WPlainPanel();
        int y = IT;  // Current drawing y position (starting from top padding)

        // ── Title ──────────────────────────────────────────────────
        WLabel title = new WLabel(
                Text.translatable("gui.music_control.migrate.title"));
        title.setHorizontalAlignment(HorizontalAlignment.CENTER);
        root.add(title, IL, y, cw, 12);
        y += 16;

        // ── Version Information ────────────────────────────────────
        WLabel info = new WLabel(Text.translatable(
                "gui.music_control.migrate.info",
                storedVersion != null ? storedVersion : "?",
                currentVersion));
        info.setHorizontalAlignment(HorizontalAlignment.CENTER);
        root.add(info, IL, y, cw, 12);
        y += 16;

        // ── Question Text (with line wrapping) ──────────────────────
        WText question = new WText(
                Text.translatable("gui.music_control.migrate.question"));
        question.setHorizontalAlignment(HorizontalAlignment.CENTER);
        root.add(question, IL, y, cw, 20);
        y += 26;

        // ── Apply Immediately Toggle ───────────────────────────────
        // WToggleButton visual alignment with WBox CENTER is misaligned (LibGUI bug).
        // Using absolute positioning for manual centering aligns visual and interaction perfectly.
        final boolean[] applyImmediately = {true};
        WToggleButton applyToggle = new WToggleButton(
                Text.translatable("gui.music_control.migrate.applyImmediately"));
        applyToggle.setToggle(true);
        applyToggle.setOnToggle(v -> applyImmediately[0] = v);
        final int toggleW = 210;  // 9px checkbox + 4px gap + estimated text width
        root.add(applyToggle, IL + (cw - toggleW) / 2, y, toggleW, 20);
        y += 28;

        // ── Button Row ─────────────────────────────────────────────
        final int bW1 = 110, bW2 = 130, bW3 = 80, gap = 4, bh = 20;
        final int totalBtnW = bW1 + gap + bW2 + gap + bW3;
        final int bx = IL + (cw - totalBtnW) / 2;  // Center the entire row

        WButton inPlaceButton = new WButton(
                Text.translatable("gui.music_control.migrate.inPlace"));
        inPlaceButton.setOnClick(() -> {
            ResourcePackUtils.migrateConfig(true, true, captured);
            client.reloadResources();
            client.setScreen(null);
        });

        WButton newPackButton = new WButton(
                Text.translatable("gui.music_control.migrate.newPack"));
        newPackButton.setOnClick(() -> {
            ResourcePackUtils.migrateConfig(false, applyImmediately[0], captured);
            client.reloadResources();
            client.setScreen(null);
        });

        WButton cancelButton = new WButton(
                Text.translatable("gui.music_control.migrate.cancel"));
        cancelButton.setOnClick(() -> client.setScreen(previousScreen));

        root.add(inPlaceButton, bx,                          y, bW1, bh);
        root.add(newPackButton, bx + bW1 + gap,              y, bW2, bh);
        root.add(cancelButton,  bx + bW1 + gap + bW2 + gap,  y, bW3, bh);
        y += bh + IB;

        // Must explicitly set size; CottonClientScreen uses this to calculate panel background dimensions
        root.setSize(dialogWidth, y);

        desc.setRootPanel(root);
        return desc;
    }
}