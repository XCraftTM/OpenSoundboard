package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Placeholder for the radial wheel-layout editor (full radial version + song picker land next).
 */
public class WheelLayoutEditorScreen extends OsbScreen {

    private final Screen parent;

    public WheelLayoutEditorScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.wheel.editor.title"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        add(new Button(Component.translatable("gui.done"), b -> this.minecraft.setScreen(parent)))
                .bounds(this.width / 2 - 80, this.height / 2 + 40, 160, 22);
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.centeredText(Component.translatable("gui.opensoundboard.wheel.editor.title"), this.width / 2, this.height / 2 - 20, Theme.TEXT);
        c.centeredText(Component.literal("Radial editor coming next"), this.width / 2, this.height / 2, Theme.TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
