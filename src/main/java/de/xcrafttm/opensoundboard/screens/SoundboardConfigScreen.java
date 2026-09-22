package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.VoiceBackend;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.TextWrap;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Choice;
import de.xcrafttm.opensoundboard.ui.widgets.OptionRow;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollPanel;
import de.xcrafttm.opensoundboard.ui.widgets.SectionHeader;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.Swatches;
import de.xcrafttm.opensoundboard.ui.widgets.TabBar;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * Settings, split into four pages: General, Audio, Sound Wheel, and Appearance.
 *
 * Every page is described once as a list of {@link Opt} entries and then laid out for the active
 * style: the modern style shows a sidebar and settings rows (title + description + control), the
 * vanilla style mirrors Minecraft's own option screens with a tab strip and a two-column grid of
 * option buttons.
 */
public class SoundboardConfigScreen extends OsbScreen {

    private enum Page {
        GENERAL(Icons.SLIDERS, "config.opensoundboard.page.general"),
        AUDIO(Icons.SPEAKER, "config.opensoundboard.page.audio"),
        WHEEL(Icons.WHEEL, "config.opensoundboard.page.wheel"),
        APPEARANCE(Icons.PALETTE, "config.opensoundboard.page.appearance");

        final Icons icon;
        final String key;

        Page(Icons icon, String key) {
            this.icon = icon;
            this.key = key;
        }
    }

    // ---- option model -----------------------------------------------------------------------

    private interface Opt {
    }

    private record Section(String key) implements Opt {
    }

    private record Bool(String key, boolean value, Consumer<Boolean> set, boolean rebuild) implements Opt {
    }

    private record Pick<T>(String key, List<T> values, T value, Function<T, Component> label,
                           Consumer<T> set, boolean rebuild) implements Opt {
    }

    private record Range(String key, double min, double max, double step, double value,
                         DoubleFunction<String> format, DoubleConsumer set, boolean rebuildOnCommit) implements Opt {
    }

    private record Action(String key, Component button, Runnable run, boolean enabled) implements Opt {
    }

    private record AccentColor() implements Opt {
    }

    /** Read-only value, e.g. the current audio output. */
    private record Info(String key, Component value) implements Opt {
    }

    private static final int SIDEBAR_W = 100;
    private static final int VANILLA_TABS_H = 24;
    private static final int VANILLA_COL_GAP = 10;
    private static final int VANILLA_GUTTER = 8;

    private static Page lastPage = Page.GENERAL;

    private final Screen parent;
    private final Map<Page, Integer> scrolls = new EnumMap<>(Page.class);
    private ScrollPanel panel;
    private TextField hexField;

    public SoundboardConfigScreen(Screen parent) {
        super(Component.translatable("title.opensoundboard.config"));
        this.parent = parent;
    }

    private static SoundboardConfig cfg() {
        return SoundboardConfig.data;
    }

    // ---- pages ------------------------------------------------------------------------------

    private List<Opt> options(Page page) {
        List<Opt> o = new ArrayList<>();
        switch (page) {
            case GENERAL -> {
                o.add(new Section("config.opensoundboard.section.playback"));
                o.add(new Bool("option.opensoundboard.playWhileMuted", cfg().isPlayWhileMuted(), cfg()::setPlayWhileMuted, false));
                o.add(new Bool("option.opensoundboard.playLocally", cfg().isPlayLocally(), cfg()::setPlayLocally, false));
                o.add(new Bool("option.opensoundboard.singleSongAtATime", cfg().isSingleSongAtATime(), cfg()::setSingleSongAtATime, false));
                o.add(new Bool("option.opensoundboard.loopAll", cfg().isLoopAll(), v -> {
                    cfg().setLoopAll(v);
                    SoundboardAudioSystem.setGlobalLooping(v);
                }, false));

                o.add(new Section("config.opensoundboard.section.controls"));
                o.add(new Pick<>("option.opensoundboard.keybindMode", List.of("play_stop", "pause_resume", "play_restart"),
                        cfg().getKeybindMode(), v -> Component.translatable("option.opensoundboard.keybindMode." + v),
                        cfg()::setKeybindMode, false));
                o.add(new Pick<>("option.opensoundboard.skipAmount", List.of(1, 3, 5, 10, 15, 30),
                        cfg().getSkipAmountSeconds(), v -> Component.translatable("config.opensoundboard.seconds", v),
                        cfg()::setSkipAmountSeconds, false));

                o.add(new Section("config.opensoundboard.section.library"));
                o.add(new Bool("option.opensoundboard.showSubfolders", cfg().isShowSubfolders(), cfg()::setShowSubfolders, false));
                o.add(new Pick<>("option.opensoundboard.sortMode", List.of("name", "date", "length"),
                        cfg().getSortMode(), v -> Component.translatable("gui.opensoundboard.sort." + v),
                        cfg()::setSortMode, false));
                o.add(new Pick<>("option.opensoundboard.sortDirection", List.of(true, false),
                        cfg().isSortAscending(), v -> Component.translatable(v
                        ? "config.opensoundboard.sort.ascending" : "config.opensoundboard.sort.descending"),
                        cfg()::setSortAscending, false));
                o.add(new Action("option.opensoundboard.soundsFolder", Component.translatable("gui.opensoundboard.open_folder"),
                        () -> McCompat.openFolder(OpenSoundboardClient.soundDir), true));
            }
            case AUDIO -> {
                o.add(new Section("config.opensoundboard.section.output"));
                o.add(new Info("option.opensoundboard.currentOutput", Component.literal(SoundboardAudioSystem.outputLabel())));
                List<VoiceBackend> voiceChats = SoundboardAudioSystem.voiceChats();
                if (voiceChats.size() > 1) {
                    List<String> ids = new ArrayList<>();
                    ids.add("auto");
                    voiceChats.forEach(b -> ids.add(b.id()));
                    String current = ids.contains(cfg().getPreferredVoiceChat()) ? cfg().getPreferredVoiceChat() : "auto";
                    o.add(new Pick<>("option.opensoundboard.voiceChat", ids, current, id -> voiceChats.stream()
                            .filter(b -> b.id().equals(id)).findFirst()
                            .<Component>map(b -> Component.literal(b.name()))
                            .orElse(Component.translatable("config.opensoundboard.voiceChat.auto")),
                            cfg()::setPreferredVoiceChat, true));
                }
                o.add(new Bool("option.opensoundboard.localPlayback", cfg().isLocalPlayback(), cfg()::setLocalPlayback, true));

                o.add(new Section("config.opensoundboard.section.volume"));
                o.add(new Bool("option.opensoundboard.syncGlobalVolume", cfg().isSyncGlobalVolume(), v -> {
                    cfg().setSyncGlobalVolume(v);
                    if (v) cfg().setGlobalPlayerVolume(cfg().getGlobalLocalVolume());
                }, true));
                if (cfg().isSyncGlobalVolume()) {
                    o.add(new Range("option.opensoundboard.globalVolume", 0, 1, 0.01, cfg().getGlobalLocalVolume(),
                            SoundboardConfigScreen::percent, v -> {
                        cfg().setGlobalLocalVolume((float) v);
                        cfg().setGlobalPlayerVolume((float) v);
                    }, false));
                } else {
                    o.add(new Range("option.opensoundboard.globalLocalVolume", 0, 1, 0.01, cfg().getGlobalLocalVolume(),
                            SoundboardConfigScreen::percent, v -> cfg().setGlobalLocalVolume((float) v), false));
                    o.add(new Range("option.opensoundboard.globalPlayerVolume", 0, 1, 0.01, cfg().getGlobalPlayerVolume(),
                            SoundboardConfigScreen::percent, v -> cfg().setGlobalPlayerVolume((float) v), false));
                }
                o.add(new Bool("option.opensoundboard.masterVolumeOnMainScreen", cfg().isMasterVolumeOnMainScreen(),
                        cfg()::setMasterVolumeOnMainScreen, false));

                o.add(new Section("config.opensoundboard.section.sounds"));
                o.add(new Bool("option.opensoundboard.syncAudio", cfg().isSyncAudio(), cfg()::setSyncAudio, false));
                o.add(new Action("option.opensoundboard.stopAll", Component.translatable("gui.opensoundboard.stop_all"),
                        SoundboardAudioSystem::stopAll, true));
            }
            case WHEEL -> {
                o.add(new Section("config.opensoundboard.section.wheelKey"));
                o.add(new Action("option.opensoundboard.wheelKey", wheelKeyLabel(),
                        () -> McCompat.setScreen(this.minecraft,
                                new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, this.minecraft.options)),
                        true));

                o.add(new Section("option.opensoundboard.wheel.header"));
                o.add(new Pick<>("option.opensoundboard.wheelSoundsPerPage", List.of(6, 8, 12, 16),
                        cfg().getWheelSoundsPerPage(), v -> Component.literal(String.valueOf(v)),
                        cfg()::setWheelSoundsPerPage, false));
                o.add(new Bool("option.opensoundboard.wheelFavoritesOnly", cfg().isWheelFavoritesOnly(), cfg()::setWheelFavoritesOnly, false));
                o.add(new Bool("option.opensoundboard.wheelCustomLayout", cfg().isWheelCustomLayout(), cfg()::setWheelCustomLayout, true));
                o.add(new Action("option.opensoundboard.wheelEditor", Component.translatable("gui.opensoundboard.wheel.editor.open"),
                        () -> McCompat.setScreen(this.minecraft, new WheelLayoutEditorScreen(this)),
                        cfg().isWheelCustomLayout()));
            }
            case APPEARANCE -> {
                o.add(new Section("config.opensoundboard.section.style"));
                o.add(new Pick<>("option.opensoundboard.interfaceStyle", List.of(false, true), cfg().isVanillaComponents(),
                        v -> Component.translatable(v ? "config.opensoundboard.style.vanilla" : "config.opensoundboard.style.modern"),
                        cfg()::setVanillaComponents, true));

                if (!vanilla()) {
                    o.add(new Section("config.opensoundboard.section.colors"));
                    o.add(new AccentColor());
                    o.add(new Pick<>("option.opensoundboard.surfaceTone", Theme.TONES.stream().map(Theme.Tone::key).toList(),
                            Theme.tone(cfg().getSurfaceTone()).key(),
                            v -> Component.translatable("config.opensoundboard.tone." + v), cfg()::setSurfaceTone, false));
                    o.add(new Range("option.opensoundboard.panelOpacity", 0.6, 1.0, 0.05, cfg().getPanelOpacity(),
                            SoundboardConfigScreen::percent, v -> cfg().setPanelOpacity((float) v), false));
                    o.add(new Bool("option.opensoundboard.roundedCorners", cfg().isRoundedCorners(), cfg()::setRoundedCorners, false));
                }

                o.add(new Section("config.opensoundboard.section.size"));
                o.add(new Range("option.opensoundboard.uiWidth", 0.6, 1.0, 0.05, cfg().getUiWidthScale(),
                        SoundboardConfigScreen::percent, v -> cfg().setUiWidthScale((float) v), true));
                if (!vanilla()) {
                    o.add(new Range("option.opensoundboard.uiHeight", 0.7, 1.0, 0.05, cfg().getUiHeightScale(),
                            SoundboardConfigScreen::percent, v -> cfg().setUiHeightScale((float) v), true));
                }
                o.add(new Range("option.opensoundboard.fontScale", 0.75, 1.25, 0.05, cfg().getFontScale(),
                        SoundboardConfigScreen::percent, v -> cfg().setFontScale((float) v), true));
                o.add(new Action("option.opensoundboard.resetAppearance", Component.translatable("gui.opensoundboard.reset"), () -> {
                    cfg().resetAppearance();
                    SoundboardConfig.save();
                    rebuildKeepingScroll();
                }, true));
            }
        }
        return o;
    }

    private static Component wheelKeyLabel() {
        KeyMapping key = OpenSoundboardClient.wheelKeyMapping();
        if (key == null || key.isUnbound()) return Component.translatable("config.opensoundboard.key.unbound");
        return key.getTranslatedKeyMessage();
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    // ---- layout -----------------------------------------------------------------------------

    @Override
    protected void buildUi() {
        hexField = null;
        Page page = lastPage;
        List<TabBar.Tab> tabs = new ArrayList<>();
        for (Page p : Page.values()) tabs.add(new TabBar.Tab(p.icon, Component.translatable(p.key)));

        if (vanilla()) {
            layoutFrame(310, 0, VANILLA_TABS_H);
            int tabsW = Math.min(400, this.width) - 28;
            add(new TabBar(tabs, page.ordinal(), this::switchPage)).bounds((this.width - tabsW) / 2, 0, tabsW, VANILLA_TABS_H);
            int gridW = vanillaColumnWidth() * 2 + VANILLA_COL_GAP;
            panel = add(new ScrollPanel().bottomPadding(4));
            panel.bounds((this.width - gridW) / 2 - VANILLA_GUTTER, bodyY - 2, gridW + VANILLA_GUTTER * 2 + 8, bodyH + 4);
            layVanilla(options(page));
            layoutFooter(add(new Button(Component.translatable("gui.done"), b -> onClose())));
        } else {
            layoutFrame(470, 0);
            int top = frameY + Theme.HEADER_H + 1;
            add(new TabBar(tabs, page.ordinal(), this::switchPage))
                    .bounds(frameX + 1, top + 6, SIDEBAR_W - 1, Page.values().length * (TabBar.MODERN_ITEM_H + 1));
            int px = frameX + SIDEBAR_W + Theme.PAD;
            panel = add(new ScrollPanel().bottomPadding(6));
            panel.bounds(px, top + 6, frameX + frameW - Theme.PAD - px, frameY + frameH - 6 - (top + 6));
            layModern(options(page));
            addHeaderButton(Icons.CLOSE, Component.translatable("gui.done").getString(), this::onClose);
        }
        panel.setScroll(scrolls.getOrDefault(page, 0));
    }

    private void switchPage(int index) {
        scrolls.put(lastPage, panel.getScroll());
        lastPage = Page.values()[index];
        rebuildUi();
    }

    private void rebuildKeepingScroll() {
        scrolls.put(lastPage, panel.getScroll());
        rebuildUi();
    }

    private void changed(boolean rebuild) {
        SoundboardConfig.save();
        if (rebuild) rebuildKeepingScroll();
    }

    private static String title(String key) {
        return Component.translatable(key).getString();
    }

    private static String description(String key) {
        String tipKey = key.replace("option.", "tooltip.");
        String text = Component.translatable(tipKey).getString();
        return text.equals(tipKey) ? null : text;
    }

    // modern ------------------------------------------------------------------------------

    private void layModern(List<Opt> opts) {
        var font = this.minecraft.font;
        int lh = (int) Math.ceil(font.lineHeight * UiStyle.fontScale());
        int sectionH = Math.max(14, lh + 5);
        int ctlH = 16;

        // First pass without a scrollbar to find out whether one is needed.
        int w = panel.innerWidth(layModern(opts, panel.w, lh, sectionH, ctlH, false));
        layModern(opts, w, lh, sectionH, ctlH, true);
    }

    /** Lays out (or, with {@code place = false}, only measures) the modern rows; returns the height. */
    private int layModern(List<Opt> opts, int w, int lh, int sectionH, int ctlH, boolean place) {
        var font = this.minecraft.font;
        int ctlW = Math.max(110, Math.min(180, (int) (w * 0.4)));
        int y = 0;
        boolean first = true;

        for (Opt opt : opts) {
            if (opt instanceof Section s) {
                if (!first) y += 10;
                if (place) panel.addChild(new SectionHeader(title(s.key())), 0, y, w, sectionH);
                y += sectionH + 2;
                first = false;
                continue;
            }
            first = false;

            String key = opt instanceof Bool b ? b.key()
                    : opt instanceof Pick<?> p ? p.key()
                    : opt instanceof Range r ? r.key()
                    : opt instanceof Action a ? a.key()
                    : opt instanceof Info i ? i.key()
                    : "option.opensoundboard.accentColor";
            int reserved = opt instanceof Bool ? 14
                    : opt instanceof AccentColor ? 0
                    : opt instanceof Info info ? balancedWidth(info.value().getString(), Math.max(ctlW, w * 9 / 20))
                    : ctlW;
            String desc = description(key);
            int rowH = OptionRow.rowHeight(font, lh, desc, OptionRow.textWidth(w, reserved), ctlH);
            if (opt instanceof AccentColor) rowH += 18;
            // Long read-only values wrap onto several lines instead of being cut off.
            List<String> infoLines = opt instanceof Info info ? TextWrap.wrap(font, info.value().getString(), reserved, 3) : List.of();
            rowH = Math.max(rowH, infoLines.size() * (lh + 1) + 10);

            if (place) {
                int cy = y + (rowH - ctlH) / 2;
                if (opt instanceof Bool b) {
                    Toggle toggle = new Toggle(b.value(), v -> {
                        b.set().accept(v);
                        changed(b.rebuild());
                    });
                    panel.addChild(new OptionRow(title(key), desc, reserved).onClick(toggle::toggle), 0, y, w, rowH);
                    panel.addChild(toggle, w - 6 - 12, y + (rowH - 12) / 2, 12, 12);
                } else if (opt instanceof Pick<?> p) {
                    panel.addChild(new OptionRow(title(key), desc, reserved), 0, y, w, rowH);
                    panel.addChild(choice(p, false), w - 6 - ctlW, cy, ctlW, ctlH);
                } else if (opt instanceof Range r) {
                    panel.addChild(new OptionRow(title(key), desc, reserved), 0, y, w, rowH);
                    panel.addChild(slider(r, null), w - 6 - ctlW, cy, ctlW, ctlH);
                } else if (opt instanceof Action a) {
                    int bw = Math.min(ctlW, Math.max(70, (int) Math.ceil(font.width(a.button().getString()) * UiStyle.fontScale()) + 20));
                    OptionRow row = new OptionRow(title(key), desc, reserved);
                    row.active = a.enabled();
                    panel.addChild(row, 0, y, w, rowH);
                    Button button = new Button(a.button(), btn -> a.run().run()).secondary();
                    button.active = a.enabled();
                    panel.addChild(button, w - 6 - bw, cy, bw, ctlH);
                } else if (opt instanceof Info) {
                    panel.addChild(new OptionRow(title(key), desc, reserved), 0, y, w, rowH);
                    panel.addChild(new Widget() {
                        @Override
                        public void draw(UiCanvas c) {
                            int lineH = c.lineHeight() + 1;
                            int top = y + (h - infoLines.size() * lineH + 1) / 2 + 1;
                            for (int i = 0; i < infoLines.size(); i++) {
                                c.rightText(infoLines.get(i), x + w, top + i * lineH, Theme.accent);
                            }
                        }
                    }, w - 6 - reserved, y, reserved, rowH);
                } else if (opt instanceof AccentColor) {
                    panel.addChild(new OptionRow(title(key), desc, reserved).alignTop(), 0, y, w, rowH);
                    int swatchSize = 12;
                    int hexW = 58;
                    int swatchesW = Math.min(w - 12 - hexW - 10, Theme.ACCENTS.size() * (swatchSize + 7) - 7);
                    int sy = y + rowH - swatchSize - 8;
                    panel.addChild(new Swatches(Theme.ACCENTS,
                                    () -> SoundboardConfig.parseHexColor(cfg().getAccentColor()),
                                    this::setAccent)
                                    .names(i -> title("config.opensoundboard.accent." + Theme.ACCENTS.get(i).key())),
                            8, sy, swatchesW, swatchSize);
                    hexField = new TextField().maxLength(7).onChange(this::onHexTyped);
                    hexField.setText(cfg().getAccentColor());
                    hexField.tooltip(title("tooltip.opensoundboard.accentHex"));
                    panel.addChild(hexField, w - 6 - hexW, sy - 2, hexW, 16);
                }
            }
            y += rowH + 2;
        }
        return y;
    }

    /**
     * The narrowest width (at most {@code maxWidth}) that wraps {@code text} into as few lines as
     * {@code maxWidth} does, so the lines come out evenly long instead of leaving one word alone.
     */
    private int balancedWidth(String text, int maxWidth) {
        var font = this.minecraft.font;
        int full = TextWrap.width(font, text) + 4;
        if (full <= maxWidth) return full;
        int lines = TextWrap.wrap(font, text, maxWidth, 0).size();
        // Never narrower than the longest word, which would get cut off instead of wrapped.
        int lo = 1;
        for (String word : text.split(" ")) lo = Math.max(lo, TextWrap.width(font, word));
        int hi = Math.max(lo, maxWidth);
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (TextWrap.wrap(font, text, mid, 0).size() <= lines) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    private void setAccent(int rgb) {
        cfg().setAccentColor(SoundboardConfig.formatHexColor(rgb));
        SoundboardConfig.save();
        if (hexField != null) {
            hexField.setText(cfg().getAccentColor());
            hexField.setInvalid(false);
        }
    }

    private void onHexTyped(String value) {
        int rgb = SoundboardConfig.parseHexColor(value);
        hexField.setInvalid(rgb < 0);
        if (rgb >= 0) {
            cfg().setAccentColor(SoundboardConfig.formatHexColor(rgb));
            SoundboardConfig.save();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Widget choice(Pick<?> p, boolean vanillaLabel) {
        Pick raw = p;
        Choice choice = new Choice(raw.values(), raw.value(), raw.label(), v -> {
            raw.set().accept(v);
            changed(raw.rebuild());
        });
        if (vanillaLabel) choice.label(Component.translatable(p.key()));
        String tip = description(p.key());
        if (tip != null && vanillaLabel) choice.tooltip(tip);
        return choice;
    }

    private Slider slider(Range r, Component vanillaLabel) {
        double span = r.max() - r.min();
        int steps = (int) Math.round(span / r.step());
        Slider slider = new Slider((r.value() - r.min()) / span, v -> r.set().accept(r.min() + v * span))
                .steps(steps)
                .onCommit(v -> changed(r.rebuildOnCommit()));
        slider.readout(v -> {
            String value = r.format().apply(r.min() + v * span);
            return vanillaLabel == null
                    ? Component.literal(value)
                    : Component.translatable("options.generic_value", vanillaLabel, value);
        });
        return slider;
    }

    // vanilla -----------------------------------------------------------------------------

    private int vanillaColumnWidth() {
        return Math.min(150, (bodyW - VANILLA_COL_GAP) / 2);
    }

    private void layVanilla(List<Opt> opts) {
        int colW = vanillaColumnWidth();
        int gap = VANILLA_COL_GAP;
        int left = VANILLA_GUTTER;
        int y = 4;
        int col = 0;
        boolean first = true;

        for (Opt opt : opts) {
            if (opt instanceof Section s) {
                if (col != 0) {
                    col = 0;
                    y += 24;
                }
                if (!first) y += 4;
                panel.addChild(new SectionHeader(title(s.key())), left, y, colW * 2 + gap, 20);
                y += 22;
                first = false;
                continue;
            }
            first = false;

            Widget widget;
            if (opt instanceof Bool b) {
                Toggle toggle = new Toggle(b.value(), v -> {
                    b.set().accept(v);
                    changed(b.rebuild());
                }).label(Component.translatable(b.key()));
                widget = toggle.tooltip(description(b.key()));
            } else if (opt instanceof Pick<?> p) {
                widget = choice(p, true);
            } else if (opt instanceof Range r) {
                widget = slider(r, Component.translatable(r.key())).tooltip(description(r.key()));
            } else if (opt instanceof Info info) {
                Button button = new Button(Component.translatable("options.generic_value",
                        Component.translatable(info.key()), info.value()), btn -> {
                });
                button.active = false;
                widget = button.tooltip(description(info.key()));
            } else if (opt instanceof Action a) {
                Button button = new Button(vanillaActionLabel(a), btn -> a.run().run());
                button.active = a.enabled();
                widget = button.tooltip(description(a.key()));
            } else {
                continue;
            }

            panel.addChild(widget, left + col * (colW + gap), y, colW, 20);
            col++;
            if (col == 2) {
                col = 0;
                y += 24;
            }
        }
    }

    private static Component vanillaActionLabel(Action a) {
        if ("option.opensoundboard.wheelKey".equals(a.key())) {
            return Component.translatable("options.generic_value", Component.translatable(a.key()), a.button());
        }
        if ("option.opensoundboard.resetAppearance".equals(a.key())) return Component.translatable(a.key());
        return a.button();
    }

    // ---- frame ------------------------------------------------------------------------------

    @Override
    protected void renderContent(UiCanvas c) {
        if (vanilla()) {
            renderFrame(c, null);
            return;
        }
        renderFrame(c, getTitle().getString());
        int top = frameY + Theme.HEADER_H + 1;
        int bottom = frameY + frameH - 1;
        c.fillRect(frameX + 1, top, SIDEBAR_W - 1, bottom - top, Theme.header);
        c.vLine(frameX + SIDEBAR_W, top, bottom - top, Theme.border);
        c.text(c.trimText("v" + modVersion(), SIDEBAR_W - 16), frameX + 9, bottom - c.lineHeight() - 6, Theme.textFaint);
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(OpenSoundboardClient.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .map(v -> v.contains("+") ? v.substring(0, v.indexOf('+')) : v)
                .orElse("");
    }

    @Override
    public void onClose() {
        if (panel != null) scrolls.put(lastPage, panel.getScroll());
        McCompat.setScreen(this.minecraft, parent);
    }
}
