package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Full-screen overlay that lists every song.
 * Each row has a ▶ preview button (plays the first 1/3 of the track, up to 15s)
 * and the song name as a button to confirm the selection.
 *
 * Calling code supplies a {@code onPick} callback that receives the chosen filename.
 */
public class SongPickerScreen extends BaseOwoScreen<FlowLayout> {

    private static final int PREVIEW_BTN_W = 30;
    private static final int CLEAR_BTN_W   = 60;

    /** Currently-previewing file name, or null. */
    private String previewingName = null;
    /** The ▶/⏹ button for the currently previewing row – needed to reset its icon. */
    private ButtonComponent previewingBtn = null;
    /** Wall-clock ms when the preview started (after seek), used for 15s cutoff. */
    private long previewStartMs = -1;
    /** True while we are waiting for the async decode to finish and isPlaying() to become true. */
    private boolean waitingForStart = false;

    private final Consumer<String> onPick;
    private final Screen parent;

    private FlowLayout list;
    private int colW;
    private TextBoxComponent searchField;

    public SongPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Text.translatable("gui.opensoundboard.wheel.picker.title"));
        this.parent  = parent;
        this.onPick  = onPick;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        colW = Math.min(400, Math.max(320, (int) (this.width * 0.55f)));

        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(10));

        // Title
        root.child(UIComponents.label(
                Text.translatable("gui.opensoundboard.wheel.picker.title").formatted(Formatting.BOLD))
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .horizontalSizing(Sizing.fixed(colW))
                .margins(Insets.bottom(4)));

        // Search bar
        searchField = UIComponents.textBox(Sizing.fixed(colW));
        searchField.setPlaceholder(Text.translatable("gui.opensoundboard.search_hint"));
        searchField.onChanged().subscribe(q -> buildList());
        root.child(searchField.margins(Insets.bottom(6)));

        // "Clear slot" button at the top
        var clearBtn = UIComponents.button(
                Text.translatable("gui.opensoundboard.wheel.picker.clear").formatted(Formatting.RED),
                b -> pickAndClose(null));
        clearBtn.sizing(Sizing.fixed(CLEAR_BTN_W), Sizing.content());
        root.child(clearBtn.margins(Insets.bottom(6)));

        // Scrollable list
        list = UIContainers.verticalFlow(Sizing.fixed(colW), Sizing.content()).gap(2);
        var scroll = UIContainers.verticalScroll(Sizing.fixed(colW), Sizing.expand(), list);
        scroll.surface(Surface.flat(0x66000000)).padding(Insets.of(4));
        root.child(scroll);

        buildList();

        // Cancel
        root.child(UIComponents.button(Text.translatable("gui.cancel"), b -> closeWithoutPick())
                .sizing(Sizing.fixed(100), Sizing.content())
                .margins(Insets.top(6)));
    }

    private void buildList() {
        list.clearChildren();

        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        File[] files = OpenSoundboardClient.soundDir.listFiles((d, n) -> n.endsWith(".mp3"));
        if (files == null) files = new File[0];

        List<File> sorted = Arrays.stream(files)
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite())
                        .reversed()
                        .thenComparing(File::getName))
                .toList();

        for (File file : sorted) {
            list.child(buildRow(file, colW));
        }
    }

    private FlowLayout buildRow(File file, int colW) {
        final String name = file.getName();
        boolean isFav = SoundboardConfig.get(name).isFavorite();

        // Outer row spans the full column width and acts as one merged component
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(colW), Sizing.content())
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.DARK_PANEL);

        // ▶ preview button – fixed width on the left
        ButtonComponent previewBtn = UIComponents.button(Text.literal("▶"), b -> togglePreview(file, b));
        previewBtn.sizing(Sizing.fixed(PREVIEW_BTN_W), Sizing.content());
        previewBtn.tooltip(Text.translatable("gui.opensoundboard.wheel.picker.preview"));

        // Song name label – left-aligned, trimmed, fills remaining width, clickable
        int trimWidth = colW - PREVIEW_BTN_W - 8; // leave 8px padding
        String labelStr = GuiTools.baseName(file);
        if (isFav) labelStr = "★ " + labelStr;
        labelStr = GuiTools.trimName(this.textRenderer, labelStr, trimWidth);

        LabelComponent nameLabel = UIComponents.label(
                Text.literal(labelStr).formatted(isFav ? Formatting.YELLOW : Formatting.WHITE));
        nameLabel.horizontalTextAlignment(HorizontalAlignment.LEFT);
        nameLabel.horizontalSizing(Sizing.expand());
        nameLabel.margins(Insets.left(4));
        nameLabel.mouseDown().subscribe((mx, my) -> {
            pickAndClose(name);
            return true;
        });

        row.child(previewBtn);
        row.child(nameLabel);
        return row;
    }

    private void togglePreview(File file, ButtonComponent btn) {
        String name = file.getName();

        if (previewingName != null && previewingName.equals(name)) {
            // Same button pressed again – stop
            stopPreview();
            return;
        }

        // Stop any existing preview (resets its button too)
        stopPreview();

        // Start playback
        SoundboardConfig.SoundData data = SoundboardConfig.get(name);
        SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());

        previewingName  = name;
        previewingBtn   = btn;
        previewStartMs  = -1;
        waitingForStart = true; // wait until isPlaying() is true before seeking

        btn.setMessage(Text.literal("⏹").formatted(Formatting.RED));
    }

    @Override
    public void tick() {
        super.tick();

        if (previewingName == null) return;

        boolean playing = SoundboardAudioSystem.isPlaying(previewingName);

        if (waitingForStart) {
            if (playing) {
                // Sound is now live – seek to 1/3 and start the timer
                long durMs = SoundboardAudioSystem.getDurationMillis(previewingName);
                if (durMs > 0) {
                    SoundboardAudioSystem.setCursor(previewingName, 1f / 3f);
                }
                previewStartMs  = System.currentTimeMillis();
                waitingForStart = false;
            }
            // Still loading – do nothing, keep the ⏹ icon showing
            return;
        }

        // Sound stopped on its own (reached end of file)
        if (!playing) {
            clearPreviewState();
            return;
        }

        // 15-second wall-clock cutoff
        if (previewStartMs > 0 && System.currentTimeMillis() - previewStartMs >= 15_000L) {
            stopPreview();
        }
    }

    private void stopPreview() {
        if (previewingName != null) {
            SoundboardAudioSystem.stop(previewingName);
        }
        clearPreviewState();
    }

    /** Clears preview state and resets the button icon without calling stop() again. */
    private void clearPreviewState() {
        if (previewingBtn != null) {
            previewingBtn.setMessage(Text.literal("▶"));
        }
        previewingName  = null;
        previewingBtn   = null;
        previewStartMs  = -1;
        waitingForStart = false;
    }

    private void pickAndClose(String fileName) {
        stopPreview();
        onPick.accept(fileName);
        if (client != null) client.setScreen(parent);
    }

    private void closeWithoutPick() {
        stopPreview();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { closeWithoutPick(); }
}












