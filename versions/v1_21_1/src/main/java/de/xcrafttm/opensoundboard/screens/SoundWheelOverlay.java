package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.WheelLayout;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.DrawContext;
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

    /** Current page; static so it survives re-opens while the key is held. */
    private static int page = 0;

    /** Current folder being browsed; static so it survives re-opens while the key is held. */
    private static File currentFolder = null;

    // Sentinel used for folder entries in allSounds
    private static final String BACK_SENTINEL    = "__BACK__";
    private static final String FOLDER_SENTINEL  = "__FOLDER__";

    // Files shown on this page, in the same order as "soundButtons"
    private final List<File> pageFiles = new ArrayList<>();
    // Only real sound buttons (no fillers, no center button), in the same order as "pageFiles"
    private final List<ButtonComponent> soundButtons = new ArrayList<>();

    public SoundWheelOverlay() {
        super(Text.literal("Sound Wheel"));
    }

    /** Call this to fully reset folder/page state when intentionally closing the wheel. */
    public static void resetState() {
        currentFolder = null;
        page = 0;
    }

    private FlowLayout root;

    // ---------------------------------------------------------------
    // owo-ui setup
    // ---------------------------------------------------------------

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        root.surface(Surface.BLANK);
        root.padding(Insets.of(0));

        // Restore last opened folder from config if not already set in static state
        if (currentFolder == null) {
            currentFolder = SoundboardConfig.resolveLastOpenedFolder(OpenSoundboardClient.soundDir);
        }

        loadSounds();
        rebuildUI(root);
    }

    private void rebuildUI(FlowLayout root) {
        root.clearChildren();
        pageFiles.clear();
        soundButtons.clear();

        List<File> sounds = currentPageSounds();
        int numButtons = perPage();
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Center indicator
        String pageText = allSounds.isEmpty()
                ? Text.translatable("gui.opensoundboard.wheel.empty").getString()
                : Text.translatable("gui.opensoundboard.wheel.page",
                    String.valueOf(page + 1), String.valueOf(totalPages())).getString();

        int centerW = 120;

        // Folder label above center button (only visible when inside a folder)
        if (currentFolder != null) {
            var folderLabel = Components.label(
                    Text.literal(currentFolder.getName()).formatted(Formatting.YELLOW));
            folderLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);
            folderLabel.sizing(Sizing.fixed(centerW), Sizing.content());
            folderLabel.positioning(Positioning.absolute(cx - centerW / 2, cy - BUTTON_H / 2 - 14));
            root.child(folderLabel);
        }

        ButtonComponent centerBtn = Components.button(Text.literal(pageText), b -> {});
        centerBtn.sizing(Sizing.fixed(centerW), Sizing.fixed(BUTTON_H));
        centerBtn.positioning(Positioning.absolute(cx - centerW / 2, cy - BUTTON_H / 2));
        centerBtn.active(false);
        root.child(centerBtn);

        for (int i = 0; i < numButtons; i++) {
            int[] pos = WheelLayout.buttonPos(i, numButtons, cx, cy, BUTTON_W, BUTTON_H, centerW, this.width, this.height);

            if (i < sounds.size() && sounds.get(i) != null) {
                File file = sounds.get(i);
                pageFiles.add(file);

                boolean isBack   = file.getName().equals(BACK_SENTINEL);
                boolean isFolder = file.isDirectory();

                MutableText buttonText;
                if (isBack) {
                    buttonText = Text.literal("← ").formatted(Formatting.GRAY)
                            .append(Text.literal(currentFolder != null ? currentFolder.getName() : "")
                                    .formatted(Formatting.GRAY));
                } else if (isFolder) {
                    String folderLabel = GuiTools.trimName(this.textRenderer, "📁 " + file.getName(), BUTTON_W - 8);
                    buttonText = Text.literal(folderLabel).formatted(Formatting.YELLOW);
                } else {
                    String label = GuiTools.baseName(file);
                    boolean isFav     = SoundboardConfig.get(file.getName()).isFavorite();
                    boolean isPlaying = SoundboardAudioSystem.isPlaying(file.getName());
                    int trimWidth = BUTTON_W - 8
                            - (isFav     ? this.textRenderer.getWidth("★ ") : 0)
                            - (isPlaying ? this.textRenderer.getWidth(" ▶") : 0);
                    label = GuiTools.trimName(this.textRenderer, label, trimWidth);
                    if (isFav) {
                        buttonText = Text.literal("★ ").formatted(Formatting.GOLD)
                                .append(Text.literal(label).formatted(isPlaying ? Formatting.GREEN : Formatting.YELLOW));
                    } else {
                        buttonText = Text.literal(label).formatted(isPlaying ? Formatting.GREEN : Formatting.WHITE);
                    }
                    if (isPlaying) buttonText.append(Text.literal(" ▶").formatted(Formatting.GREEN));
                }

                ButtonComponent btn = Components.button(buttonText, b -> {});
                btn.sizing(Sizing.fixed(BUTTON_W), Sizing.fixed(BUTTON_H));
                btn.positioning(Positioning.absolute(pos[0], pos[1]));

                if (isBack) {
                    btn.mouseDown().subscribe((mx, my, b) -> {
                        currentFolder = null;
                        SoundboardConfig.saveLastOpenedFolder(null);
                        page = 0;
                        loadSounds();
                        rebuildUI(root);
                        return true;
                    });
                } else if (isFolder) {
                    final File folderFile = file;
                    btn.mouseDown().subscribe((mx, my, b) -> {
                        currentFolder = folderFile;
                        SoundboardConfig.saveLastOpenedFolder(folderFile);
                        page = 0;
                        loadSounds();
                        rebuildUI(root);
                        return true;
                    });
                }

                root.child(btn);
                soundButtons.add(btn);
            } else {
                ButtonComponent filler = Components.button(Text.empty(), b -> {});
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
            int total = perPage();
            WheelLayoutConfig.resize(total);
            allSounds = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                String name = WheelLayoutConfig.get(i);
                if (name != null && !name.isBlank()) {
                    File f = new File(OpenSoundboardClient.soundDir, name);
                    allSounds.add(f.exists() ? f : null);
                } else {
                    allSounds.add(null);
                }
            }
            return;
        }

        allSounds = new ArrayList<>();

        if (currentFolder != null) {
            // Inside a folder: first slot = back, rest = mp3s
            allSounds.add(new File(OpenSoundboardClient.soundDir, BACK_SENTINEL));
            File[] files = currentFolder.listFiles((d, n) -> n.endsWith(".mp3"));
            if (files != null) {
                Arrays.stream(files)
                        .filter(f -> !SoundboardConfig.data.isWheelFavoritesOnly()
                                || SoundboardConfig.get(f.getName()).isFavorite())
                        .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite())
                                .reversed().thenComparing(File::getName))
                        .forEach(allSounds::add);
            }
        } else {
            // Root: subfolders first (when enabled), then mp3s
            if (SoundboardConfig.data.isShowSubfolders()) {
                File[] subdirs = OpenSoundboardClient.soundDir.listFiles(File::isDirectory);
                if (subdirs != null) {
                    Arrays.sort(subdirs, Comparator.comparing(File::getName));
                    for (File sub : subdirs) {
                        File[] mp3s = sub.listFiles((d, n) -> n.endsWith(".mp3"));
                        if (mp3s != null && mp3s.length > 0) allSounds.add(sub);
                    }
                }
            }
            File[] files = OpenSoundboardClient.soundDir.listFiles((d, n) -> n.endsWith(".mp3"));
            if (files != null) {
                Arrays.stream(files)
                        .filter(f -> !SoundboardConfig.data.isWheelFavoritesOnly()
                                || SoundboardConfig.get(f.getName()).isFavorite())
                        .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite())
                                .reversed().thenComparing(File::getName))
                        .forEach(allSounds::add);
            }
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
    public void close() {
        super.close();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------
    // Called by OpenSoundboardClient when the wheel key is released
    // ---------------------------------------------------------------

    public void playHoveredAndClose() {
        for (int i = 0; i < soundButtons.size(); i++) {
            ButtonComponent btn = soundButtons.get(i);
            if (btn == null || !btn.isHovered()) continue;

            File file = pageFiles.get(i);

            // Folder/back entries are click-only – don't close when hovering them on key-release
            if (file.getName().equals(BACK_SENTINEL) || file.isDirectory()) return;

            // Normal sound – play it and close
            String name = file.getName();
            var data = SoundboardConfig.get(name);
            boolean isPlaying = SoundboardAudioSystem.isPlaying(name);
            String mode = SoundboardConfig.data.getKeybindMode();

            switch (mode) {
                case "pause_resume" -> {
                    if (isPlaying) {
                        if (SoundboardAudioSystem.isPaused(name)) SoundboardAudioSystem.resume(name);
                        else SoundboardAudioSystem.pause(name);
                    } else {
                        SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                    }
                }
                case "play_restart" -> {
                    if (isPlaying) SoundboardAudioSystem.stop(name);
                    SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                }
                default -> {
                    if (isPlaying) SoundboardAudioSystem.stop(name);
                    else SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                }
            }
            break;
        }
        close();
    }
}
