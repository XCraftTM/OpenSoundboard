package de.xcrafttm.opensoundboard.gametest;

import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Widget;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

/**
 * Input and inspection helpers. Screens keep their widgets private, so the tests read them by
 * reflection (field names are stable in the dev environment). Clicks are real mouse events at the
 * widget's position, so they exercise the same code path as a player.
 */
final class Ui {

    private Ui() {
    }

    // ---------------------------------------------------------------- reflection

    static Object field(Object target, String name) {
        try {
            Field f = findField(target instanceof Class<?> c ? c : target.getClass(), name);
            return f.get(target instanceof Class<?> ? null : target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + name, e);
        }
    }

    static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target instanceof Class<?> c ? c : target.getClass(), name);
            f.set(target instanceof Class<?> ? null : target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write " + name, e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " in " + type.getName());
    }

    /** Call a (possibly private) method by name and argument count. */
    static Object call(Object target, String name, Object... args) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot call " + name, e);
                }
            }
        }
        throw new IllegalStateException("No method " + name + " on " + target.getClass().getName());
    }

    /** Call a public no-argument method if it exists on this Minecraft version; null otherwise. */
    static Object callIfPresent(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true); // implementation classes are often not public
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot call " + name, e);
        }
    }

    // ---------------------------------------------------------------- screens

    static Screen screen(ClientGameTestContext ctx) {
        return ctx.computeOnClient(McCompat::screen);
    }

    @SuppressWarnings("unchecked")
    static List<Widget> widgets(OsbScreen screen) {
        return (List<Widget>) field(screen, "widgets");
    }

    static <T extends Widget> T widget(ClientGameTestContext ctx, Class<T> type, Predicate<T> filter) {
        return ctx.computeOnClient(c -> {
            if (!(McCompat.screen(c) instanceof OsbScreen screen)) throw new IllegalStateException("No OpenSoundboard screen open");
            for (Widget w : widgets(screen)) {
                if (type.isInstance(w) && filter.test(type.cast(w))) return type.cast(w);
            }
            throw new IllegalStateException("No matching " + type.getSimpleName() + " on " + screen.getClass().getSimpleName());
        });
    }

    /**
     * Whether the OS text input is switched on (SDL3 on 26.3+, where no typed characters arrive
     * without it; the IME switch on 26.1/26.2). Null where it does not exist (1.21.x).
     */
    static Boolean textInputEnabled(ClientGameTestContext ctx) {
        return ctx.computeOnClient(c -> {
            Object manager = callIfPresent(c, "textInputManager");
            return manager == null ? null : (Boolean) field(manager, "textInputEnabled");
        });
    }

    /**
     * Resize the window and set the GUI scale (0 = auto). Applying the scale is resizeDisplay()
     * before 26.x and resizeGui() after, so it is looked up.
     */
    static void setWindow(ClientGameTestContext ctx, int width, int height, int guiScale) {
        ctx.getInput().resizeWindow(width, height);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(guiScale);
            if (callIfPresent(c, "resizeGui") == null) callIfPresent(c, "resizeDisplay");
        });
        ctx.waitTicks(2);
    }

    // ---------------------------------------------------------------- mouse

    /** Minecraft's left mouse button number on this version (0 with GLFW, 1 with SDL3). */
    static int leftButton() {
        int button = 0;
        while (Keys.uiMouseButton(button) != 0) button++;
        return button;
    }

    /** Move the mouse to GUI coordinates (what screens and widgets use). */
    static void moveTo(ClientGameTestContext ctx, double guiX, double guiY) {
        double[] scale = ctx.computeOnClient(c -> new double[]{
                c.getWindow().getScreenWidth() / (double) c.getWindow().getGuiScaledWidth(),
                c.getWindow().getScreenHeight() / (double) c.getWindow().getGuiScaledHeight()});
        ctx.getInput().setCursorPos(guiX * scale[0], guiY * scale[1]);
    }

    static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        moveTo(ctx, guiX, guiY);
        ctx.waitTick();
        ctx.getInput().pressMouse(leftButton());
        ctx.waitTick();
    }

    static void click(ClientGameTestContext ctx, Widget widget) {
        click(ctx, widget.x + widget.w / 2.0, widget.y + widget.h / 2.0);
    }

    /** Click the middle of row {@code index} of a list (away from the play button and star). */
    static void clickRow(ClientGameTestContext ctx, ScrollList list, int index) {
        clickRow(ctx, list, index, list.w / 2.0);
    }

    /** Click row {@code index} of a list, {@code offsetX} pixels from its left edge. */
    static void clickRow(ClientGameTestContext ctx, ScrollList list, int index, double offsetX) {
        // Scroll the row into view first, like a player would (the list clamps the value when drawn).
        ctx.runOnClient(c -> {
            @SuppressWarnings("unchecked")
            List<ScrollList.Row> rows = (List<ScrollList.Row>) field(list, "rows");
            int gap = (int) field(list, "rowGap");
            int top = 0;
            for (int i = 0; i < index; i++) top += rows.get(i).height() + gap;
            int scroll = (int) field(list, "scroll");
            if (top < scroll || top + rows.get(index).height() > scroll + list.h - 4) setField(list, "scroll", top);
        });
        ctx.waitTick();
        double y = ctx.computeOnClient(c -> {
            @SuppressWarnings("unchecked")
            List<ScrollList.Row> rows = (List<ScrollList.Row>) field(list, "rows");
            int gap = (int) field(list, "rowGap");
            int ry = list.y + (int) call(list, "inset") - (int) field(list, "scroll");
            for (int i = 0; i < index; i++) ry += rows.get(i).height() + gap;
            return ry + rows.get(index).height() / 2.0;
        });
        click(ctx, list.x + offsetX, y);
    }

    static boolean waitUntil(ClientGameTestContext ctx, int maxTicks, Predicate<Minecraft> condition) {
        for (int i = 0; i < maxTicks; i++) {
            if (ctx.computeOnClient(condition::test)) return true;
            ctx.waitTick();
        }
        return ctx.computeOnClient(condition::test);
    }
}
