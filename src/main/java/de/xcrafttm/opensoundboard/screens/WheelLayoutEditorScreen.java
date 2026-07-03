package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.WheelLayout;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;

/** Radial editor: shows each wheel slot as a button; click one to assign a sound via the picker. */
public class WheelLayoutEditorScreen extends OsbScreen {

    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;
    private static final int CENTER_W = 120;

    private final Screen parent;

    public WheelLayoutEditorScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.wheel.editor.title"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        int total = SoundboardConfig.data.getWheelSoundsPerPage();
        WheelLayoutConfig.resize(total);
        int cx = this.width / 2;
        int cy = this.height / 2;

        for (int i = 0; i < total; i++) {
            final int slot = i;
            int[] pos = WheelLayout.buttonPos(i, total, cx, cy, BUTTON_W, BUTTON_H, CENTER_W, this.width, this.height);
            String assigned = WheelLayoutConfig.get(i);
            Button b = add(new Button(slotLabel(assigned), btn -> openPicker(slot)).secondary());
            b.bounds(pos[0], pos[1], BUTTON_W, BUTTON_H);
            if (assigned != null && !assigned.isBlank()) b.tooltip(GuiTools.baseName(new File(OpenSoundboardClient.soundDir, assigned)));
        }

        add(new Button(Component.literal("✕"), btn -> this.minecraft.setScreen(parent)).secondary())
                .bounds(this.width - 24, 4, 18, 16).tooltip(Component.translatable("gui.done").getString());
    }

    private Component slotLabel(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Component.translatable("gui.opensoundboard.wheel.editor.empty_slot");
        }
        boolean fav = SoundboardConfig.get(fileName).isFavorite();
        String base = (fav ? "★ " : "") + GuiTools.baseName(new File(OpenSoundboardClient.soundDir, fileName));
        return Component.literal(GuiTools.trimName(this.font, base, BUTTON_W - 8));
    }

    private void openPicker(int slot) {
        this.minecraft.setScreen(new SongPickerScreen(this, name -> {
            WheelLayoutConfig.set(slot, name);
            WheelLayoutConfig.save();
        }));
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, 0x99000000);
        int cx = this.width / 2;
        int cy = this.height / 2;
        c.fillRoundRect(cx - CENTER_W / 2, cy - BUTTON_H / 2, CENTER_W, BUTTON_H, Theme.PANEL);
        c.roundBorder(cx - CENTER_W / 2, cy - BUTTON_H / 2, CENTER_W, BUTTON_H, Theme.BORDER);
        c.centeredText(Component.translatable("gui.opensoundboard.wheel.editor.hint"), cx, cy - 4, Theme.TEXT_MUTED);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
