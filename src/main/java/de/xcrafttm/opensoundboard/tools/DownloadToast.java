package de.xcrafttm.opensoundboard.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Native Minecraft toast notifications for a background YouTube download. */
final class DownloadToast {

    private static final SystemToast.SystemToastId ACTIVE =
            new SystemToast.SystemToastId(86_400_000L);
    private static final SystemToast.SystemToastId RESULT =
            new SystemToast.SystemToastId(5_000L);

    private DownloadToast() {
    }

    static void preparing(int progress) {
        Component detail = progress > 0
                ? Component.translatable("toast.opensoundboard.youtube.preparing_progress", progress)
                : Component.translatable("toast.opensoundboard.youtube.preparing");
        updateActive(detail);
    }

    static void downloading(int progress) {
        updateActive(Component.translatable("toast.opensoundboard.youtube.progress", progress));
    }

    static void cancelling() {
        updateActive(Component.translatable("toast.opensoundboard.youtube.cancelling"));
    }

    static void completed() {
        finish(Component.translatable("toast.opensoundboard.youtube.completed"),
                Component.translatable("message.opensoundboard.download_completed"));
    }

    static void failed() {
        finish(Component.translatable("toast.opensoundboard.youtube.failed"),
                Component.translatable("message.opensoundboard.youtube.failed"));
    }

    static void cancelled() {
        finish(Component.translatable("toast.opensoundboard.youtube.cancelled"),
                Component.translatable("message.opensoundboard.youtube.cancelled"));
    }

    private static void updateActive(Component detail) {
        onClient(client -> {
            //? if >=26.2 {
            /*SystemToast.addOrUpdate(client.gui.toastManager(), ACTIVE,
                    Component.translatable("toast.opensoundboard.youtube.downloading"), detail);
            *///?} else if >=1.21.11 {
            SystemToast.addOrUpdate(client.getToastManager(), ACTIVE,
                    Component.translatable("toast.opensoundboard.youtube.downloading"), detail);
            //?} else {
            /*SystemToast.addOrUpdate(client.getToasts(), ACTIVE,
                    Component.translatable("toast.opensoundboard.youtube.downloading"), detail);
            *///?}
        });
    }

    private static void finish(Component title, Component detail) {
        onClient(client -> {
            //? if >=26.2 {
            /*SystemToast.forceHide(client.gui.toastManager(), ACTIVE);
            SystemToast.addOrUpdate(client.gui.toastManager(), RESULT, title, detail);
            *///?} else if >=1.21.11 {
            SystemToast.forceHide(client.getToastManager(), ACTIVE);
            SystemToast.addOrUpdate(client.getToastManager(), RESULT, title, detail);
            //?} else {
            /*SystemToast.forceHide(client.getToasts(), ACTIVE);
            SystemToast.addOrUpdate(client.getToasts(), RESULT, title, detail);
            *///?}
        });
    }

    private static void onClient(java.util.function.Consumer<Minecraft> action) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) client.execute(() -> action.accept(client));
    }
}
