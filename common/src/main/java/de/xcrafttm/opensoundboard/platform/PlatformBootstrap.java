package de.xcrafttm.opensoundboard.platform;

public final class PlatformBootstrap {

    private static PlatformClient client;

    private PlatformBootstrap() {}

    public static void setClient(PlatformClient platformClient) {
        client = platformClient;
    }

    public static PlatformClient client() {
        if (client == null) throw new IllegalStateException("PlatformClient not initialized");
        return client;
    }
}

