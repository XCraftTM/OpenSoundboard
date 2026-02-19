package de.xcrafttm.opensoundboard.tools;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * A KeyBinding that can be polled via raw GLFW even while a screen is open.
 * Stores the bound key ourselves to avoid needing to access private fields.
 */
public class HoldableKeyBinding extends KeyBinding {

    private InputUtil.Type boundType;
    private int boundCode;

    public HoldableKeyBinding(String id, InputUtil.Type type, int code, String category) {
        super(id, type, code, category);
        this.boundType = type;
        this.boundCode = code;
    }

    @Override
    public void setBoundKey(InputUtil.Key key) {
        super.setBoundKey(key);
        this.boundType = key.getCategory();
        this.boundCode = key.getCode();
    }

    /**
     * Returns true if this keybind is currently physically held,
     * using raw GLFW — works even when a screen is open.
     */
    public boolean isHeldDown(long windowHandle) {
        if (boundCode == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (boundType == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(windowHandle, boundCode) == GLFW.GLFW_PRESS;
        } else {
            return GLFW.glfwGetKey(windowHandle, boundCode) == GLFW.GLFW_PRESS;
        }
    }
}
