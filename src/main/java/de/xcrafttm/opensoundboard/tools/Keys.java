package de.xcrafttm.opensoundboard.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Cross-version keyboard and mouse helpers. Minecraft 26.3 replaced GLFW with SDL3: key values
 * became SDL scancodes, modifier flags use SDL bits, and mouse buttons are 1-based. Everything
 * else in the mod goes through this class so no GLFW or SDL names leak into the UI code.
 */
public final class Keys {

    public static final int ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int ENTER = InputConstants.KEY_RETURN;
    public static final int NUMPAD_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int DELETE = InputConstants.KEY_DELETE;
    public static final int LEFT = InputConstants.KEY_LEFT;
    public static final int RIGHT = InputConstants.KEY_RIGHT;
    public static final int HOME = InputConstants.KEY_HOME;
    public static final int END = InputConstants.KEY_END;
    public static final int A = InputConstants.KEY_A;
    public static final int C = InputConstants.KEY_C;
    public static final int V = InputConstants.KEY_V;
    public static final int X = InputConstants.KEY_X;
    public static final int U = InputConstants.KEY_U;

    //? if >=26.3 {
    public static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    public static final int MOD_ALT = InputConstants.MOD_ALT;
    //?} else {
    /*public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;
    *///?}

    private Keys() {
    }

    /** Input type for keyboard bindings ({@code KEYSYM} before 26.3, {@code KEYBOARD} after). */
    public static InputConstants.Type keyboardType() {
        //? if >=26.3 {
        return InputConstants.Type.KEYBOARD;
        //?} else {
        /*return InputConstants.Type.KEYSYM;
        *///?}
    }

    public static int unknownKey() {
        return InputConstants.UNKNOWN.getValue();
    }

    public static boolean isKeyDown(int key) {
        if (key == unknownKey() || key < 0) return false;
        //? if >=26.3 {
        return InputConstants.isKeyDown(key);
        //?} else if >=1.21.11 {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        *///?} else {
        /*return InputConstants.isKeyDown(McCompat.windowHandle(Minecraft.getInstance()), key);
        *///?}
    }

    /** Whether the mouse button with Minecraft's numbering for this version is held. */
    public static boolean isMouseDown(int button) {
        //? if >=26.3 {
        if (button < 1 || button > 32) return false;
        return (org.lwjgl.sdl.SDLMouse.nSDL_GetMouseState(0L, 0L) & (1 << (button - 1))) != 0;
        //?} else {
        /*return org.lwjgl.glfw.GLFW.glfwGetMouseButton(McCompat.windowHandle(Minecraft.getInstance()), button)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        *///?}
    }

    /**
     * Mouse button from an input event, normalized to 0 = left, 1 = right, 2 = middle. 26.3 (SDL)
     * numbers buttons from 1; all widgets use the 0-based GLFW numbering.
     */
    public static int uiMouseButton(int button) {
        //? if >=26.3 {
        return button - 1;
        //?} else {
        /*return button;
        *///?}
    }

    /** Whether the bound key or mouse button of {@code key} is currently held. */
    public static boolean isDown(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.MOUSE ? isMouseDown(key.getValue()) : isKeyDown(key.getValue());
    }

    public static boolean shiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean controlDown() {
        return isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean altDown() {
        return isKeyDown(InputConstants.KEY_LALT) || isKeyDown(InputConstants.KEY_RALT);
    }
}
