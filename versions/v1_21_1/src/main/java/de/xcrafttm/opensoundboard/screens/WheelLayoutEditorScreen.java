package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.WheelLayout;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Shows the radial wheel layout with the current slot assignments.
 * Clicking any slot (filled or empty) opens the {@link SongPickerScreen}
 * to assign or replace that slot.
 */
public class WheelLayoutEditorScreen extends BaseOwoScreen<FlowLayout> {

    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;

    private final Screen parent;
    private FlowLayout root;

    public WheelLayoutEditorScreen(Screen parent) {
        super(Text.translatable("gui.opensoundboard.wheel.editor.title"));
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        root.surface(Surface.BLANK).padding(Insets.of(0));

        WheelLayoutConfig.resize(SoundboardConfig.data.getWheelSoundsPerPage());
        rebuildSlots();
    }

    // ----------------------------------------------------------------
    // Slot buttons
    // ----------------------------------------------------------------

    private void rebuildSlots() {
        root.clearChildren();

        int total = SoundboardConfig.data.getWheelSoundsPerPage();
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Dim background
        // (handled by render override below)

        // Centre label
        ButtonComponent centre = Components.button(
                Text.translatable("gui.opensoundboard.wheel.editor.hint").formatted(Formatting.GRAY),
                b -> {});
        centre.sizing(Sizing.fixed(120), Sizing.fixed(BUTTON_H));
        centre.positioning(Positioning.absolute(cx - 60, cy - BUTTON_H / 2));
        centre.active(false);
        root.child(centre);

        for (int i = 0; i < total; i++) {
            final int slot = i;
            int[] pos = WheelLayout.buttonPos(i, total, cx, cy, BUTTON_W, BUTTON_H, 120, this.width, this.height);

            String assigned = WheelLayoutConfig.get(i);
            Text label = slotLabel(assigned);

            ButtonComponent btn = Components.button(label, b -> openPicker(slot));
            btn.sizing(Sizing.fixed(BUTTON_W), Sizing.fixed(BUTTON_H));
            btn.positioning(Positioning.absolute(pos[0], pos[1]));
            root.child(btn);
        }

        // Done button (bottom-centre)
        ButtonComponent doneBtn = Components.button(
                Text.translatable("gui.done"),
                b -> { if (client != null) client.setScreen(parent); });
        doneBtn.sizing(Sizing.fixed(100), Sizing.fixed(BUTTON_H));
        doneBtn.positioning(Positioning.absolute(cx - 50, cy + 120));
        root.child(doneBtn);
    }

    private Text slotLabel(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Text.translatable("gui.opensoundboard.wheel.editor.empty_slot").formatted(Formatting.DARK_GRAY);
        }
        File f = new File(OpenSoundboardClient.soundDir, fileName);
        boolean isFav = SoundboardConfig.get(fileName).isFavorite();
        String base = GuiTools.baseName(f);
        if (isFav) base = "★ " + base;
        base = GuiTools.trimName(this.textRenderer, base, BUTTON_W - 8);
        return Text.literal(base).formatted(isFav ? Formatting.YELLOW : Formatting.WHITE);
    }

    private void openPicker(int slot) {
        if (client == null) return;
        client.setScreen(new SongPickerScreen(this, fileName -> {
            WheelLayoutConfig.set(slot, fileName);
            WheelLayoutConfig.save();
            rebuildSlots();
        }));
    }

    // ----------------------------------------------------------------
    // Layout – delegates to shared WheelLayout utility
    // ----------------------------------------------------------------

    // (buttonPos removed – WheelLayout.buttonPos is called directly above)

    // ----------------------------------------------------------------
    // Rendering / misc
    // ----------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark vignette so the world stays visible
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
