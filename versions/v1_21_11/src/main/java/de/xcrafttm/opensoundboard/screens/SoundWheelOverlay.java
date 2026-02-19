package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.WheelLayout;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SoundWheelOverlay extends BaseOwoScreen<FlowLayout> {

    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;

    private List<File> allSounds = List.of();
    private int page = 0;

    // Files shown on this page, in the same order as "soundButtons"
    private final List<File> pageFiles = new ArrayList<>();
    // Only real sound buttons (no fillers, no center button), in the same order as "pageFiles"
    private final List<ButtonComponent> soundButtons = new ArrayList<>();

    public SoundWheelOverlay() {
        super(Text.literal("Sound Wheel"));
    }

    private FlowLayout root;

    // ---------------------------------------------------------------
    // owo-ui setup
    // ---------------------------------------------------------------

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        // Optional: translucent surface behind UI elements
        root.surface(Surface.BLANK);
        root.padding(Insets.of(0));

        loadSounds();
        rebuildUI(root);
    }

    private void rebuildUI(FlowLayout root) {
        root.clearChildren();
        pageFiles.clear();
        soundButtons.clear();

        List<File> sounds = currentPageSounds();
        int numButtons = perPage(); // keep consistent spacing on every page
        int cx = this.width / 2;
        int cy = this.height / 2;

        // --- Center indicator (inactive) ---
        String pageText = allSounds.isEmpty()
                ? Text.translatable("gui.opensoundboard.wheel.empty").getString()
                : Text.translatable("gui.opensoundboard.wheel.page",
                String.valueOf(page + 1), String.valueOf(totalPages())).getString();

        int centerW = 120;
        ButtonComponent centerBtn = UIComponents.button(Text.literal(pageText), b -> {});
        centerBtn.sizing(Sizing.fixed(centerW), Sizing.fixed(BUTTON_H));
        centerBtn.positioning(Positioning.absolute(cx - centerW / 2, cy - BUTTON_H / 2));
        centerBtn.active(false);
        root.child(centerBtn);

        // -----------------------------------------------------------------
        // TUNING – change WheelLayout constants to affect both screens
        // -----------------------------------------------------------------
        for (int i = 0; i < numButtons; i++) {
            int[] pos = WheelLayout.buttonPos(
                    i, numButtons,
                    cx, cy,
                    BUTTON_W, BUTTON_H,
                    centerW,
                    this.width, this.height
            );

            if (i < sounds.size() && sounds.get(i) != null) {
                File file = sounds.get(i);
                pageFiles.add(file);

                String label = GuiTools.baseName(file);
                boolean isFav = SoundboardConfig.get(file.getName()).isFavorite();
                boolean isPlaying = SoundboardAudioSystem.isPlaying(file.getName());

                int trimWidth = BUTTON_W - 8
                        - (isFav ? this.textRenderer.getWidth("★ ") : 0)
                        - (isPlaying ? this.textRenderer.getWidth(" ▶") : 0);
                label = GuiTools.trimName(this.textRenderer, label, trimWidth);

                MutableText buttonText;
                if (isFav) {
                    buttonText = Text.literal("★ ").formatted(Formatting.GOLD)
                            .append(Text.literal(label).formatted(isPlaying ? Formatting.GREEN : Formatting.YELLOW));
                } else {
                    buttonText = Text.literal(label).formatted(isPlaying ? Formatting.GREEN : Formatting.WHITE);
                }
                if (isPlaying) buttonText.append(Text.literal(" ▶").formatted(Formatting.GREEN));

                ButtonComponent btn = UIComponents.button(buttonText, b -> {});
                btn.sizing(Sizing.fixed(BUTTON_W), Sizing.fixed(BUTTON_H));
                btn.positioning(Positioning.absolute(pos[0], pos[1]));
                root.child(btn);

                // IMPORTANT: keep mapping stable (no center/fillers included)
                soundButtons.add(btn);
            } else {
                ButtonComponent filler = UIComponents.button(Text.empty(), b -> {});
                filler.sizing(Sizing.fixed(BUTTON_W), Sizing.fixed(BUTTON_H));
                filler.positioning(Positioning.absolute(pos[0], pos[1]));
                filler.active(false);
                root.child(filler);
            }
        }
    }

    // ---------------------------------------------------------------
    // Data / paging
    // ---------------------------------------------------------------

    private void loadSounds() {
        if (SoundboardConfig.data.isWheelCustomLayout()) {
            // Custom layout: slots defined by WheelLayoutConfig; resolve to files
            int total = perPage();
            WheelLayoutConfig.resize(total);
            allSounds = new java.util.ArrayList<>();
            for (int i = 0; i < total; i++) {
                String name = WheelLayoutConfig.get(i);
                if (name != null && !name.isBlank()) {
                    File f = new File(OpenSoundboardClient.soundDir, name);
                    allSounds.add(f.exists() ? f : null); // null = missing file, rendered as empty
                } else {
                    allSounds.add(null);
                }
            }
        } else {
            File[] files = OpenSoundboardClient.soundDir.listFiles((dir, name) -> name.endsWith(".mp3"));
            if (files == null) files = new File[0];

            boolean favOnly = SoundboardConfig.data.isWheelFavoritesOnly();
            allSounds = Arrays.stream(files)
                    .filter(f -> !favOnly || SoundboardConfig.get(f.getName()).isFavorite())
                    .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite())
                            .reversed()
                            .thenComparing(File::getName))
                    .collect(Collectors.toList());
        }
    }

    private int perPage() {
        return Math.max(1, SoundboardConfig.data.getWheelSoundsPerPage());
    }

    private int totalPages() {
        if (SoundboardConfig.data.isWheelCustomLayout()) return 1;
        if (allSounds.isEmpty()) return 1;
        return (int) Math.ceil((double) allSounds.size() / perPage());
    }

    private List<File> currentPageSounds() {
        if (SoundboardConfig.data.isWheelCustomLayout()) {
            // Custom layout: return the full slot list (may contain nulls for empty slots)
            return allSounds;
        }
        int start = page * perPage();
        int end = Math.min(start + perPage(), allSounds.size());
        if (start >= allSounds.size()) return List.of();
        return allSounds.subList(start, end);
    }

    // ---------------------------------------------------------------
    // Layout helper (left + right arcs, no center clipping)
    // ---------------------------------------------------------------


    // ---------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Vignette overlay, keep world visible
        context.fill(0, 0, this.width, this.height, 0x66000000);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int total = totalPages();
        if (total <= 1) return true;

        page = verticalAmount > 0
                ? (page - 1 + total) % total
                : (page + 1) % total;

        rebuildUI(root);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key);
    }

    // ---------------------------------------------------------------
    // Called by OpenSoundboardClient when the wheel key is released
    // ---------------------------------------------------------------

    public void playHoveredAndClose() {
        // Use soundButtons mapping (prevents center/fillers shifting indices)
        for (int i = 0; i < soundButtons.size(); i++) {
            ButtonComponent btn = soundButtons.get(i);
            if (btn != null && btn.isHovered()) {
                File file = pageFiles.get(i);

                String name = file.getName();
                var data = SoundboardConfig.get(name);
                boolean isPlaying = SoundboardAudioSystem.isPlaying(name);
                String mode = SoundboardConfig.data.getKeybindMode();

                switch (mode) {
                    case "pause_resume" -> {
                        if (isPlaying) {
                            if (SoundboardAudioSystem.isPaused(name)) {
                                SoundboardAudioSystem.resume(name);
                            } else {
                                SoundboardAudioSystem.pause(name);
                            }
                        } else {
                            SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                        }
                    }
                    case "play_restart" -> {
                        if (isPlaying) SoundboardAudioSystem.stop(name);
                        SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                    }
                    default -> { // "play_stop"
                        if (isPlaying) {
                            SoundboardAudioSystem.stop(name);
                        } else {
                            SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                        }
                    }
                }
                break;
            }
        }
        close();
    }
}
