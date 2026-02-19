package de.xcrafttm.opensoundboard.tools;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * A KeyBinding subclass that exposes the currently-bound key code,
 * allowing raw GLFW polling even while a screen is open.
 */
public class HoldableKeyBinding extends KeyBinding {

    public HoldableKeyBinding(String id, InputUtil.Type type, int code, Category category) {
        super(id, type, code, category);
    }

    /**
     * Returns the GLFW key code of the currently-bound key,
     * or {@code GLFW.GLFW_KEY_UNKNOWN} if unbound.
     */
    public int getBoundKeyCode() {
        return boundKey.getCode();
    }

    /**
     * Returns the {@link InputUtil.Type} of the currently-bound key
     * so callers can distinguish keyboard keys from mouse buttons.
     */
    public InputUtil.Type getBoundKeyType() {
        return boundKey.getCategory();
    }
}

