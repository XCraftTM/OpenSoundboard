package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Pie-menu editor: shows each wheel slot as a slice; click one to assign a sound via the picker. */
public class WheelLayoutEditorScreen extends OsbScreen {

    private final Screen parent;

    public WheelLayoutEditorScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.wheel.editor.title"));
        this.parent = parent;
    }

    private PieWheel wheel;
    private int hovered = -1;

    @Override
    protected void buildUi() {
        int total = SoundboardConfig.data.getWheelSoundsPerPage();
        WheelLayoutConfig.resize(total);
        boolean vanilla = vanilla();
        wheel = PieWheel.fit(this.width, this.height, total, vanilla ? 30 : 26, vanilla ? 36 : 12);

        if (vanilla) {
            add(new Button(Component.translatable("gui.done"), b -> onClose()))
                    .bounds(this.width / 2 - 100, this.height - 28, 200, 20);
        } else {
            add(new Button(Icons.CLOSE, null, b -> onClose()).ghost())
                    .bounds(this.width - 22, 6, 16, 16).tooltip(tip("gui.done"));
        }
    }

    @Override
    protected void renderContent(UiCanvas c) {
        if (vanilla()) {
            c.centeredText(getTitle().getString(), this.width / 2, 12, UiStyle.VANILLA_TEXT);
        } else {
            c.fillRect(0, 0, this.width, this.height, Theme.scrim);
            c.text(getTitle().getString(), 10, 11, Theme.text);
        }

        hovered = wheel.sliceAt(c.mouseX, c.mouseY);
        if (Math.hypot(c.mouseX - wheel.cx, c.mouseY - wheel.cy) > wheel.outer + 24) hovered = -1;

        List<PieWheel.Slice> slices = new ArrayList<>();
        for (int i = 0; i < wheel.count; i++) {
            String assigned = WheelLayoutConfig.get(i);
            if (assigned == null || assigned.isBlank()) {
                // Empty slots stay clickable in the editor, so they are not marked as empty here.
                slices.add(new PieWheel.Slice(null, 0, tip("gui.opensoundboard.wheel.editor.empty_slot"), false, false));
            } else {
                boolean fav = SoundboardConfig.get(assigned).isFavorite();
                String name = GuiTools.baseName(new File(OpenSoundboardClient.soundDir, assigned));
                slices.add(new PieWheel.Slice(fav ? Icons.STAR : null, Theme.FAVORITE, name, false, false));
            }
        }
        String title = hovered >= 0 ? slices.get(hovered).label() : tip("gui.opensoundboard.wheel.editor.hint");
        wheel.render(c, slices, hovered, title, null);
    }

    @Override
    protected boolean screenMouseClicked(double mx, double my, int button) {
        if (button != 0 || hovered < 0) return false;
        final int slot = hovered;
        UiSound.click();
        McCompat.setScreen(minecraft, new SongPickerScreen(this, name -> {
            WheelLayoutConfig.set(slot, name);
            WheelLayoutConfig.save();
        }));
        return true;
    }

    private static String tip(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        McCompat.setScreen(this.minecraft, parent);
    }
}
