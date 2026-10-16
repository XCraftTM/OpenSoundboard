package de.xcrafttm.opensoundboard.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.screens.DownloaderScreen;
import de.xcrafttm.opensoundboard.screens.SongPickerScreen;
import de.xcrafttm.opensoundboard.screens.SoundWheelOverlay;
import de.xcrafttm.opensoundboard.screens.SoundboardConfigScreen;
import de.xcrafttm.opensoundboard.screens.SoundboardScreen;
import de.xcrafttm.opensoundboard.screens.WheelLayoutEditorScreen;
import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundLibrary;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.VoiceBackend;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Choice;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.xcrafttm.opensoundboard.gametest.TestSounds.*;

/**
 * End-to-end tests that drive the real client: they open the mod's screens, click and type like a
 * player, check the results, and take screenshots of every screen in both interface styles. Run
 * with {@code gradlew :<version>:runClientGameTest}; the screenshots and a report land in the
 * test run directory (see {@link Report}).
 */
public class OpenSoundboardGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        Report report = new Report(ctx);

        report.section("Setup", () -> {
            TestSounds.createLibrary(OpenSoundboardClient.soundDir.toPath());
            ctx.runOnClient(c -> {
                // A test window can lose focus (e.g. clicking another window while watching). That
                // would pause the singleplayer server, and Fabric's test framework can then hang
                // forever when the test leaves the world.
                c.options.pauseOnLostFocus = false;
                SoundboardConfig.data.setVanillaComponents(false);
                SoundboardConfig.data.setSingleSongAtATime(true);
                SoundboardConfig.data.setKeybindMode("play_stop");
                SoundboardConfig.data.setWheelCustomLayout(false);
                SoundboardConfig.save();
            });
            report.info("Test library: 5 sounds in the root folder, 2 in Games/, 1 in Games/Retro/ (generated silent MP3s)");
            Ui.setWindow(ctx, 1280, 720, 2);
            report.info("Window 1280x720 at GUI scale 2 (the \"Small window\" section covers the default size)");
        });
        report.section("Main screen", () -> mainScreen(ctx, report));
        report.section("Search bar: click and type", () -> searchBar(ctx, report));
        report.section("Search bar: typing anywhere", () -> typeAnywhere(ctx, report));
        report.section("Playback", () -> playback(ctx, report));
        report.section("Folders and same-named sounds", () -> folders(ctx, report));
        report.section("Keybind capture", () -> keybindCapture(ctx, report));
        report.section("Settings", () -> settings(ctx, report));
        report.section("Downloader", () -> downloader(ctx, report));
        report.section("Wheel editor and song picker", () -> wheelEditor(ctx, report));
        report.section("Vanilla style", () -> vanillaStyle(ctx, report));
        report.section("In a world: keybinds and the sound wheel", () -> inWorld(ctx, report));
        report.section("Small window", () -> smallWindow(ctx, report));

        ctx.runOnClient(c -> SoundboardAudioSystem.stopAll());
        ctx.setScreen(net.minecraft.client.gui.screens.TitleScreen::new); // the framework requires ending here
        ctx.waitTick();
        report.finish();
    }

    // ---------------------------------------------------------------- scenarios

    private static void mainScreen(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        report.check("The soundboard screen opens", Ui.screen(ctx) instanceof SoundboardScreen);
        int rows = ctx.computeOnClient(c -> list(c).rowCount());
        report.check("The root list shows the Games folder, a divider, and the 5 root sounds", rows == ROOT_ROWS, rows);
        String output = ctx.computeOnClient(c -> {
            VoiceBackend backend = SoundboardAudioSystem.activeBackend();
            return backend == null ? "none" : backend.id();
        });
        report.check("Without a voice chat server, sounds play locally", "local".equals(output), output);
        report.screenshot("Soundboard (modern style)");
    }

    private static void searchBar(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        Ui.click(ctx, ctx.computeOnClient(OpenSoundboardGameTest::searchField));
        report.check("Clicking the search bar focuses it", ctx.computeOnClient(c -> searchField(c).isFocused()));
        Boolean textInput = Ui.textInputEnabled(ctx);
        if (textInput == null) report.info("This version has no text input switch to check (GLFW always delivers typed characters)");
        else report.check("OS text input (SDL on 26.3, IME on 26.1/26.2) is on while the search bar is focused", textInput);

        ctx.getInput().typeChars("air");
        ctx.waitTick();
        String text = ctx.computeOnClient(c -> searchField(c).getText());
        report.check("Typed text appears in the search bar", "air".equals(text), text);
        List<String> results = resultKeys(ctx);
        report.check("Searching \"air\" finds only Airhorn", results.equals(List.of(AIRHORN)), results);
        report.screenshot("Search bar with typed text");

        for (int i = 0; i < 3; i++) ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        text = ctx.computeOnClient(c -> searchField(c).getText());
        report.check("Backspace deletes the typed text", text.isEmpty(), text);
    }

    private static void typeAnywhere(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        report.check("Nothing is focused when the screen opens", !ctx.computeOnClient(c -> searchField(c).isFocused()));
        Boolean textInput = Ui.textInputEnabled(ctx);
        if (textInput != null) report.check("OS text input is on, so typing anywhere can start a search", textInput);

        ctx.getInput().typeChars("bru");
        ctx.waitTick();
        String text = ctx.computeOnClient(c -> searchField(c).getText());
        report.check("Typing without clicking goes into the search bar", "bru".equals(text), text);
        report.check("...and focuses it", ctx.computeOnClient(c -> searchField(c).isFocused()));
        List<String> results = resultKeys(ctx);
        report.check("Searching \"bru\" finds only Bruh", results.equals(List.of(BRUH)), results);
        report.screenshot("Typing anywhere starts a search");
    }

    private static void playback(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        ctx.getInput().typeChars("long");
        ctx.waitTick();
        ctx.getInput().pressKey(InputConstants.KEY_RETURN);
        boolean started = Ui.waitUntil(ctx, 200, c -> playing(LONG_SONG) && !loading(LONG_SONG));
        report.check("Enter plays the first search result", started);
        ctx.waitTicks(20);
        report.screenshot("Playing a sound");

        ctx.getInput().pressKey(InputConstants.KEY_RETURN);
        report.check("Enter again stops it", Ui.waitUntil(ctx, 20, c -> !playing(LONG_SONG)));

        // Start and stop before the decode finishes: before the fix it started playing anyway.
        ctx.runOnClient(c -> {
            File song = SoundLibrary.find(LONG_SONG);
            SoundboardAudioSystem.toggle(song);
            SoundboardAudioSystem.toggle(song);
        });
        ctx.waitTicks(60);
        report.check("Starting and immediately stopping a sound leaves it stopped", !ctx.computeOnClient(c -> playing(LONG_SONG)));

        // Two quick plays: before the fix both copies played on top of each other.
        ctx.runOnClient(c -> {
            File song = SoundLibrary.find(LONG_SONG);
            SoundboardAudioSystem.play(song);
            SoundboardAudioSystem.play(song);
        });
        Ui.waitUntil(ctx, 200, c -> !loading(LONG_SONG));
        int copies = ctx.computeOnClient(c -> activeCopies(LONG_SONG));
        report.check("Playing a sound twice quickly gives one copy, not two", copies == 1, copies);

        // Single song at a time, with both decodes running at once.
        ctx.runOnClient(c -> {
            SoundboardAudioSystem.stopAll();
            SoundboardAudioSystem.play(SoundLibrary.find(LONG_SONG));
            SoundboardAudioSystem.play(SoundLibrary.find(APPLAUSE));
        });
        Ui.waitUntil(ctx, 200, c -> !loading(LONG_SONG) && !loading(APPLAUSE));
        boolean onlyLast = ctx.computeOnClient(c -> playing(APPLAUSE) && !playing(LONG_SONG));
        report.check("\"Single song at a time\" keeps only the last sound, even when both were still loading", onlyLast);
        ctx.runOnClient(c -> SoundboardAudioSystem.stopAll());
    }

    private static void folders(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        Ui.clickRow(ctx, ctx.computeOnClient(OpenSoundboardGameTest::list), 0);
        ctx.waitTicks(2);
        File folder = ctx.computeOnClient(c -> (File) Ui.field(SoundboardScreen.class, "currentFolder"));
        report.check("Clicking the Games folder opens it", folder != null && folder.getName().equals("Games"), folder);
        int rows = ctx.computeOnClient(c -> list(c).rowCount());
        report.check("Games shows the Retro folder, a divider, and its 2 sounds", rows == 4, rows);
        report.screenshot("Inside the Games folder");

        ctx.runOnClient(c -> Ui.call(soundboard(c), "openFolder", (Object) null));
        ctx.waitTicks(2);
        ctx.getInput().typeChars("intro");
        ctx.waitTick();
        Set<String> intros = Set.copyOf(resultKeys(ctx));
        report.check("Searching from the root finds both intro.mp3 files (root and Games/)",
                intros.equals(Set.of(INTRO_ROOT, INTRO_GAMES)), intros);
        report.screenshot("Search finds sounds in subfolders");

        boolean separate = ctx.computeOnClient(c -> {
            SoundboardConfig.get(INTRO_ROOT).setFavorite(true);
            boolean other = SoundboardConfig.get(INTRO_GAMES).isFavorite();
            SoundboardConfig.get(INTRO_ROOT).setFavorite(false);
            return !other;
        });
        report.check("Same-named sounds in different folders keep separate settings", separate);
    }

    private static void keybindCapture(ClientGameTestContext ctx, Report report) {
        openSoundboard(ctx);
        // Rows: Games, divider, Airhorn, Applause, Bruh, ...
        Ui.clickRow(ctx, ctx.computeOnClient(OpenSoundboardGameTest::list), 4);
        String selected = ctx.computeOnClient(c -> {
            File file = (File) Ui.field(soundboard(c), "selected");
            return file == null ? null : SoundLibrary.key(file);
        });
        report.check("Clicking a sound selects it", BRUH.equals(selected), selected);

        Ui.click(ctx, ctx.computeOnClient(c -> (Button) Ui.field(soundboard(c), "bindBtn")));
        report.check("The keybind button starts listening", binding(ctx));

        ctx.getInput().holdControl();
        ctx.waitTick();
        report.check("Pressing Ctrl alone keeps listening for the actual key", binding(ctx));
        ctx.getInput().pressKey(InputConstants.KEY_K);
        ctx.getInput().releaseControl();
        SoundboardConfig.KeyBind bind = ctx.computeOnClient(c -> SoundboardConfig.get(BRUH).getKeybind());
        report.check("Ctrl + K is saved as the keybind (by key name, with Ctrl)",
                bind != null && "key.keyboard.k".equals(bind.getKey()) && bind.isCtrl() && !bind.isShift() && !bind.isAlt(),
                bind);
        ctx.waitTicks(2);
        report.screenshot("Keybind set to Ctrl + K");

        Ui.click(ctx, ctx.computeOnClient(c -> (Button) Ui.field(soundboard(c), "bindBtn")));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        bind = ctx.computeOnClient(c -> SoundboardConfig.get(BRUH).getKeybind());
        report.check("Escape while listening removes the keybind", bind == null, bind);
        report.check("...and the screen stays open", Ui.screen(ctx) instanceof SoundboardScreen);
    }

    private static void settings(ClientGameTestContext ctx, Report report) {
        ctx.setScreen(() -> new SoundboardConfigScreen(null));
        ctx.waitTicks(2);
        report.check("The settings screen opens", Ui.screen(ctx) instanceof SoundboardConfigScreen);
        String[] pages = {"General", "Audio", "Sound Wheel", "Appearance"};
        for (int i = 0; i < pages.length; i++) {
            int page = i;
            ctx.runOnClient(c -> Ui.call(McCompat.screen(c), "switchPage", page));
            ctx.waitTicks(2);
            report.screenshot("Settings - " + pages[i]);
        }

        // The accent color field sits inside a scrolling panel: typing must reach it too.
        TextField hex = ctx.computeOnClient(c -> (TextField) Ui.field(McCompat.screen(c), "hexField"));
        Ui.click(ctx, hex);
        Boolean textInput = Ui.textInputEnabled(ctx);
        if (textInput != null) report.check("OS text input is on for a text field inside a scrolling panel", textInput);
        ctx.getInput().pressKey(InputConstants.KEY_END);
        for (int i = 0; i < 7; i++) ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        ctx.getInput().typeChars("#3366CC");
        ctx.waitTicks(2);
        String accent = ctx.computeOnClient(c -> SoundboardConfig.data.getAccentColor());
        report.check("Typing a hex color changes the accent color", "#3366CC".equals(accent), accent);
        report.screenshot("Settings - custom accent color");
        ctx.runOnClient(c -> {
            SoundboardConfig.data.resetAppearance();
            SoundboardConfig.save();
            Ui.call(McCompat.screen(c), "switchPage", 0);
        });
    }

    private static void downloader(ClientGameTestContext ctx, Report report) {
        ctx.setScreen(() -> new DownloaderScreen(null));
        ctx.waitTicks(2);
        report.check("The downloader opens", Ui.screen(ctx) instanceof DownloaderScreen);
        Ui.click(ctx, Ui.widget(ctx, TextField.class, f -> true));
        ctx.getInput().typeChars("https://example.com/video");
        ctx.waitTick();
        String text = ctx.computeOnClient(c -> ((TextField) Ui.field(McCompat.screen(c), "input")).getText());
        report.check("Typing a link into the downloader works", "https://example.com/video".equals(text), text);
        report.screenshot("Downloader - link mode");

        Ui.click(ctx, Ui.widget(ctx, Choice.class, w -> true));
        ctx.waitTicks(2);
        report.screenshot("Downloader - search mode");
        Ui.click(ctx, Ui.widget(ctx, Choice.class, w -> true)); // back to link mode for the next run
        ctx.waitTick();
    }

    private static void wheelEditor(ClientGameTestContext ctx, Report report) {
        ctx.runOnClient(c -> {
            SoundboardConfig.data.setWheelCustomLayout(true);
            WheelLayoutConfig.set(0, null);
        });
        ctx.setScreen(() -> new WheelLayoutEditorScreen(null));
        ctx.waitTicks(2);
        report.screenshot("Wheel editor");

        moveToSlice(ctx, 0);
        ctx.waitTick();
        ctx.getInput().pressMouse(Ui.leftButton());
        ctx.waitTicks(2);
        report.check("Clicking a slot opens the song picker", Ui.screen(ctx) instanceof SongPickerScreen);
        report.screenshot("Song picker");

        // Picker rows are sorted by name: Airhorn, Applause, Bruh, intro, intro, Level Up, Long Song, ...
        int longSong = 6;
        ScrollList pickerList = ctx.computeOnClient(c -> (ScrollList) Ui.field(McCompat.screen(c), "list"));
        Ui.clickRow(ctx, pickerList, longSong, 8); // the play button at the left edge
        boolean previewing = Ui.waitUntil(ctx, 200, c -> playing(SoundboardAudioSystem.previewKey(LONG_SONG)) && !loading(SoundboardAudioSystem.previewKey(LONG_SONG)));
        report.check("The play button previews the sound", previewing);
        report.check("The preview is separate from real playback (only you hear it)", !ctx.computeOnClient(c -> playing(LONG_SONG)));

        Ui.clickRow(ctx, pickerList, longSong);
        ctx.waitTicks(2);
        String slot = ctx.computeOnClient(c -> WheelLayoutConfig.get(0));
        report.check("Picking a sound assigns it to the slot (by its path)", LONG_SONG.equals(slot), slot);
        report.check("Picking stops the preview", !ctx.computeOnClient(c -> playing(SoundboardAudioSystem.previewKey(LONG_SONG))));
        report.check("...and returns to the editor", Ui.screen(ctx) instanceof WheelLayoutEditorScreen);
        report.screenshot("Wheel editor with a slot assigned");

        ctx.runOnClient(c -> {
            WheelLayoutConfig.set(0, null);
            WheelLayoutConfig.save();
            SoundboardConfig.data.setWheelCustomLayout(false);
            SoundboardConfig.save();
        });
    }

    private static void vanillaStyle(ClientGameTestContext ctx, Report report) {
        ctx.runOnClient(c -> SoundboardConfig.data.setVanillaComponents(true));
        openSoundboard(ctx);
        report.screenshot("Soundboard (vanilla style)");

        Ui.click(ctx, ctx.computeOnClient(OpenSoundboardGameTest::searchField));
        ctx.getInput().typeChars("app");
        ctx.waitTick();
        List<String> results = resultKeys(ctx);
        report.check("Search works in the vanilla style", results.equals(List.of(APPLAUSE)), results);
        report.screenshot("Vanilla style search");

        ctx.setScreen(() -> new SoundboardConfigScreen(null));
        ctx.waitTicks(2);
        report.screenshot("Settings (vanilla style)");
        ctx.setScreen(() -> new DownloaderScreen(null));
        ctx.waitTicks(2);
        report.screenshot("Downloader (vanilla style)");

        ctx.runOnClient(c -> {
            SoundboardConfig.data.setVanillaComponents(false);
            SoundboardConfig.save();
        });
    }

    private static void inWorld(ClientGameTestContext ctx, Report report) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            waitForWorld(ctx, world);
            report.screenshot("In a world");

            // A per-sound keybind plays its sound (keybinds are polled every tick).
            ctx.runOnClient(c -> {
                SoundboardAudioSystem.stopAll();
                SoundboardConfig.get(LONG_SONG).setKeybind(Keys.keyBind(InputConstants.KEY_K, 0, 0));
                SoundboardConfig.get(BRUH).setKeybind(Keys.keyBind(InputConstants.KEY_K, 0, Keys.MOD_CONTROL));
            });
            ctx.getInput().holdKeyFor(InputConstants.KEY_K, 3);
            report.check("Pressing K plays the sound bound to K",
                    Ui.waitUntil(ctx, 200, c -> playing(LONG_SONG) && !loading(LONG_SONG)));
            report.check("...but not the one bound to Ctrl + K", !ctx.computeOnClient(c -> playing(BRUH)));
            ctx.waitTicks(5); // the key has to be seen released before the next press counts
            ctx.getInput().holdKeyFor(InputConstants.KEY_K, 3);
            report.check("Pressing K again stops it (\"play / stop\" mode)", Ui.waitUntil(ctx, 20, c -> !playing(LONG_SONG)));
            ctx.runOnClient(c -> {
                SoundboardConfig.get(LONG_SONG).setKeybind(null);
                SoundboardConfig.get(BRUH).setKeybind(null);
                SoundboardAudioSystem.stopAll();
            });

            // The sound wheel: hold its key, point at a sound, release to play it.
            ctx.runOnClient(c -> {
                KeyMapping wheelKey = OpenSoundboardClient.wheelKeyMapping();
                wheelKey.setKey(Keys.keyboardType().getOrCreate(InputConstants.KEY_G));
                KeyMapping.resetMapping();
            });
            ctx.getInput().holdKey(InputConstants.KEY_G);
            boolean opened = Ui.waitUntil(ctx, 40, c -> McCompat.screen(c) instanceof SoundWheelOverlay);
            report.check("Holding the wheel key opens the sound wheel", opened);
            if (opened) {
                int slot = ctx.computeOnClient(c -> {
                    @SuppressWarnings("unchecked")
                    List<File> slots = (List<File>) Ui.field(McCompat.screen(c), "slots");
                    for (int i = 0; i < slots.size(); i++) {
                        if (slots.get(i) != null && slots.get(i).getName().equals("Long Song.mp3")) return i;
                    }
                    return -1;
                });
                report.check("Long Song is on the first wheel page", slot >= 0, slot);
                moveToSlice(ctx, slot);
                ctx.waitTicks(2);
                int hovered = ctx.computeOnClient(c -> (int) Ui.field(McCompat.screen(c), "hovered"));
                report.check("Pointing at a slice highlights it", hovered == slot, hovered);
                report.screenshot("Sound wheel");
            }
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            report.check("Releasing the key closes the wheel", Ui.waitUntil(ctx, 20, c -> McCompat.screen(c) == null));
            report.check("...and plays the pointed-at sound", Ui.waitUntil(ctx, 200, c -> playing(LONG_SONG)));

            openSoundboard(ctx);
            ctx.waitTicks(10);
            report.screenshot("Soundboard in a world (while playing)");
            ctx.runOnClient(c -> SoundboardAudioSystem.stopAll());
            ctx.setScreen(() -> null);
            // Leave only once the server is running normally (not paused), see pauseOnLostFocus above.
            Ui.waitUntil(ctx, 40, c -> c.getSingleplayerServer() == null || !c.getSingleplayerServer().isPaused());
            ctx.waitTicks(2);
        }
    }

    /** Minecraft's default window (854x480, automatic GUI scale): records how much of the list fits. */
    private static void smallWindow(ClientGameTestContext ctx, Report report) {
        Ui.setWindow(ctx, 854, 480, 0);
        for (boolean vanilla : new boolean[]{false, true}) {
            ctx.runOnClient(c -> SoundboardConfig.data.setVanillaComponents(vanilla));
            openSoundboard(ctx);
            String style = vanilla ? "vanilla" : "modern";
            double rows = ctx.computeOnClient(c -> {
                ScrollList list = list(c);
                @SuppressWarnings("unchecked")
                List<ScrollList.Row> all = (List<ScrollList.Row>) Ui.field(list, "rows");
                return list.h / (double) (all.get(all.size() - 1).height() + (int) Ui.field(list, "rowGap"));
            });
            report.info(String.format("Default window, %s style: the sound list fits %.1f rows", style, rows));
            report.screenshot("Small window - " + style + " style");
        }
        ctx.runOnClient(c -> {
            SoundboardConfig.data.setVanillaComponents(false);
            SoundboardConfig.save();
        });
    }

    // ---------------------------------------------------------------- helpers

    private static void openSoundboard(ClientGameTestContext ctx) {
        ctx.runOnClient(c -> {
            Ui.setField(SoundboardScreen.class, "currentFolder", null);
            SoundboardConfig.saveLastOpenedFolder(null);
        });
        ctx.setScreen(SoundboardScreen::new);
        ctx.waitTicks(3);
    }

    private static SoundboardScreen soundboard(Minecraft c) {
        return (SoundboardScreen) McCompat.screen(c);
    }

    private static TextField searchField(Minecraft c) {
        return (TextField) Ui.field(soundboard(c), "search");
    }

    private static ScrollList list(Minecraft c) {
        return (ScrollList) Ui.field(soundboard(c), "list");
    }

    private static boolean binding(ClientGameTestContext ctx) {
        return ctx.computeOnClient(c -> (boolean) Ui.field(soundboard(c), "binding"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> resultKeys(ClientGameTestContext ctx) {
        return ctx.computeOnClient(c -> ((List<File>) Ui.field(soundboard(c), "results")).stream()
                .map(SoundLibrary::key)
                .collect(Collectors.toList()));
    }

    private static boolean playing(String key) {
        return SoundboardAudioSystem.isPlaying(key);
    }

    private static boolean loading(String key) {
        return SoundboardAudioSystem.isLoading(key);
    }

    /** How many copies of {@code key} are in the mixer right now. */
    private static int activeCopies(String key) {
        Collection<?> sounds = (Collection<?>) Ui.field(SoundboardAudioSystem.class, "activeSounds");
        int count = 0;
        for (Object sound : sounds) {
            if (key.equals(Ui.field(sound, "key"))) count++;
        }
        return count;
    }

    /** Point the mouse at the middle of slice {@code index} of the pie wheel on screen. */
    private static void moveToSlice(ClientGameTestContext ctx, int index) {
        double[] point = ctx.computeOnClient(c -> {
            Object wheel = Ui.field(McCompat.screen(c), "wheel");
            int cx = (int) Ui.field(wheel, "cx");
            int cy = (int) Ui.field(wheel, "cy");
            int inner = (int) Ui.field(wheel, "inner");
            int outer = (int) Ui.field(wheel, "outer");
            int count = (int) Ui.field(wheel, "count");
            double angle = -Math.PI / 2 + index * (2 * Math.PI / count); // slice 0 is at the top
            double radius = (inner + outer) / 2.0;
            return new double[]{cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius};
        });
        Ui.moveTo(ctx, point[0], point[1]);
    }

    /**
     * Wait until the world is loaded and drawn. The helper for this moved between Fabric API
     * versions (getClientWorld() on 1.21.11, getConnection() on 26.x), so it is looked up.
     */
    private static void waitForWorld(ClientGameTestContext ctx, TestSingleplayerContext world) {
        Object holder = Ui.callIfPresent(world, "getConnection");
        if (holder == null) holder = Ui.callIfPresent(world, "getClientWorld");
        if (holder != null) {
            try {
                Method wait = holder.getClass().getMethod("waitForChunksRender");
                wait.setAccessible(true);
                wait.invoke(holder);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        ctx.waitTicks(100);
    }
}
