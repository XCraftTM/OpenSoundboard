package de.xcrafttm.opensoundboard.platform;

import java.util.Objects;

public final class PlatformBootstrap {

    private static PlatformClient client;

    private PlatformBootstrap() {}

    public static void setClient(PlatformClient platformClient) {
        client = Objects.requireNonNull(platformClient, "platformClient");
    }

    public static PlatformClient client() {
        if (client == null) throw new IllegalStateException("PlatformClient not initialized");
        return client;
    }
}

