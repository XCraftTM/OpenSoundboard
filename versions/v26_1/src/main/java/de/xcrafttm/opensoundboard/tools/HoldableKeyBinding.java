package de.xcrafttm.opensoundboard.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * A KeyBinding subclass that exposes the currently-bound key code,
 * allowing raw GLFW polling even while a screen is open.
 */
public class HoldableKeyBinding extends KeyMapping {

    public HoldableKeyBinding(String id, InputConstants.Type type, int code, Category category) {
        super(id, type, code, category);
    }

    /**
     * Returns the GLFW key code of the currently-bound key,
     * or {@code GLFW.GLFW_KEY_UNKNOWN} if unbound.
     */
    public int getBoundKeyCode() {
        return key.getValue();
    }

    /**
     * Returns the {@link InputConstants.Type} of the currently-bound key
     * so callers can distinguish keyboard keys from mouse buttons.
     */
    public InputConstants.Type getBoundKeyType() {
        return key.getType();
    }
}

