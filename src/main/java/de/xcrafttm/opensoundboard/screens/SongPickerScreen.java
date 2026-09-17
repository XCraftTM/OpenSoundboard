package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundLibrary;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Picker used by the wheel editor: search + list with a preview button per row. */
public class SongPickerScreen extends OsbScreen {

    private final Screen parent;
    private final Consumer<String> onPick;

    private TextField search;
    private ScrollList list;

    private String previewingName = null;
    private long previewStartMs = -1;
    private boolean waitingForStart = false;

    public SongPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("gui.opensoundboard.wheel.picker.title"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void buildUi() {
        boolean vanilla = vanilla();
        int ctl = UiStyle.controlHeight();
        int gap = vanilla ? 4 : Theme.GAP;
        layoutFrame(360, 0);

        int y = bodyY;
        String query = search != null ? search.getText() : "";
        search = add(new TextField().icon(Icons.SEARCH).placeholder(tip("gui.opensoundboard.search_hint"))
                .onChange(s -> buildList()));
        search.setText(query);

        Button clear = new Button(Component.translatable("gui.opensoundboard.wheel.picker.clear"), b -> pick(null)).secondary();
        clear.tooltip(tip("tooltip.opensoundboard.wheel.picker.clear"));
        if (vanilla) {
            search.bounds(bodyX, y, bodyW, ctl);
            layoutFooter(add(clear), add(new Button(Component.translatable("gui.cancel"), b -> onClose())));
        } else {
            int clearW = 70;
            search.bounds(bodyX, y, bodyW - clearW - gap, ctl);
            add(clear).bounds(bodyX + bodyW - clearW, y, clearW, ctl);
            addHeaderButton(Icons.CLOSE, tip("gui.cancel"), this::onClose);
        }
        y += ctl + gap + 2;

        list = add(new ScrollList().framed(true).gap(vanilla ? 2 : 1));
        list.emptyText(tip("gui.opensoundboard.no_results"));
        list.bounds(bodyX, y, bodyW, bodyY + bodyH - y);
        buildList();
    }

    private void buildList() {
        list.clearRows();
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        List<File> sorted = SoundLibrary.allSounds(SoundLibrary.root()).stream()
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed()
                        .thenComparing(f -> f.getName().toLowerCase()))
                .collect(Collectors.toList());
        for (File f : sorted) list.addRow(row(f));
    }

    private ScrollList.Row row(File file) {
        final String name = file.getName();
        final String folderHint = SoundLibrary.folderOf(file);
        return new ScrollList.Row() {
            public int height() {
                return SoundRows.rowHeight();
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                SoundRows.drawSound(c, file, rx, ry, rw, height(), false, hovered, true, folderHint);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                if (SoundRows.inPlayZone(mx, rx)) togglePreview(file);
                else pick(name);
                return true;
            }

            public String tooltip(double mx, int rx, int rw) {
                if (SoundRows.inPlayZone(mx, rx)) return tip("gui.opensoundboard.wheel.picker.preview");
                return tip("tooltip.opensoundboard.wheel.picker.pick");
            }
        };
    }

    private void togglePreview(File file) {
        if (file.getName().equals(previewingName)) {
            stopPreview();
            return;
        }
        stopPreview();
        SoundboardConfig.SoundData data = SoundboardConfig.get(file.getName());
        SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
        previewingName = file.getName();
        previewStartMs = -1;
        waitingForStart = true;
    }

    private void stopPreview() {
        if (previewingName != null) SoundboardAudioSystem.stop(previewingName);
        previewingName = null;
        previewStartMs = -1;
        waitingForStart = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (previewingName == null) return;
        boolean playing = SoundboardAudioSystem.isPlaying(previewingName);
        if (waitingForStart) {
            if (playing) {
                if (SoundboardAudioSystem.getDurationMillis(previewingName) > 0)
                    SoundboardAudioSystem.setCursor(previewingName, 1f / 3f);
                previewStartMs = System.currentTimeMillis();
                waitingForStart = false;
            }
            return;
        }
        if (!playing) {
            stopPreview();
        } else if (previewStartMs > 0 && System.currentTimeMillis() - previewStartMs >= 15_000L) {
            stopPreview();
        }
    }

    private void pick(String fileName) {
        stopPreview();
        onPick.accept(fileName);
        McCompat.setScreen(this.minecraft, parent);
    }

    @Override
    public void onClose() {
        stopPreview();
        McCompat.setScreen(this.minecraft, parent);
    }

    @Override
    protected void renderContent(UiCanvas c) {
        renderFrame(c, getTitle().getString());
    }

    private static String tip(String key) {
        return Component.translatable(key).getString();
    }
}
