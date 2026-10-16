package de.xcrafttm.opensoundboard.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects check results and screenshots. A failed check does not stop the run, so one problem
 * does not hide the rest; the run fails at the end if anything failed. The result is written to
 * {@code opensoundboard-gametest-report.md} in the run directory, next to the screenshots.
 */
final class Report {

    static final Logger LOGGER = LoggerFactory.getLogger("OpenSoundboard GameTest");

    /** A scenario body; may throw anything, which is recorded as a failure. */
    interface Body {
        void run() throws Throwable;
    }

    private final ClientGameTestContext ctx;
    private final List<String> lines = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private int checks = 0;
    private int screenshots = 0;
    private String section = "";

    Report(ClientGameTestContext ctx) {
        this.ctx = ctx;
        lines.add("# OpenSoundboard game test report");
        lines.add("");
        lines.add("Minecraft " + minecraftVersion());
    }

    /** Run one scenario; an exception fails it (with a screenshot) and the next scenario still runs. */
    void section(String name, Body body) {
        section = name;
        lines.add("");
        lines.add("## " + name);
        LOGGER.info("[GameTest] === {} ===", name);
        try {
            body.run();
        } catch (Throwable t) {
            fail("crashed: " + t);
            LOGGER.error("[GameTest] {} crashed", name, t);
            try {
                screenshot("FAILED " + name);
            } catch (Throwable ignored) {
            }
        }
    }

    void check(String what, boolean ok) {
        check(what, ok, null);
    }

    void check(String what, boolean ok, Object actual) {
        checks++;
        String detail = actual == null ? "" : " (got: " + actual + ")";
        if (ok) {
            lines.add("- PASS " + what);
            LOGGER.info("[GameTest] PASS {}", what);
        } else {
            fail(what + detail);
        }
    }

    void info(String text) {
        lines.add("- note: " + text);
        LOGGER.info("[GameTest] {}", text);
    }

    private void fail(String what) {
        failures.add(section + ": " + what);
        lines.add("- **FAIL** " + what);
        LOGGER.error("[GameTest] FAIL {}: {}", section, what);
    }

    /** Take a screenshot; files are numbered in the order they were taken. */
    Path screenshot(String name) {
        screenshots++;
        String file = String.format("%02d %s", screenshots, name);
        Path path = ctx.takeScreenshot(file);
        lines.add("- screenshot: `" + path.getFileName() + "`");
        return path;
    }

    /** Write the report; throws if any check failed so the Gradle run fails too. */
    void finish() {
        // Summary right under "Minecraft <version>" at the top.
        lines.add(3, "");
        lines.add(4, (failures.isEmpty() ? "**All " + checks + " checks passed.**" : "**" + failures.size() + " of " + checks + " checks failed.**")
                + " " + screenshots + " screenshots.");
        if (!failures.isEmpty()) {
            lines.add(5, "");
            lines.add(6, "Failed:");
            for (int i = 0; i < failures.size(); i++) lines.add(7 + i, "- " + failures.get(i));
        }
        Path file = FabricLoader.getInstance().getGameDir().resolve("opensoundboard-gametest-report.md");
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
            LOGGER.info("[GameTest] Report written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[GameTest] Could not write the report", e);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " check(s) failed:\n - " + String.join("\n - ", failures));
        }
    }

    static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }
}
