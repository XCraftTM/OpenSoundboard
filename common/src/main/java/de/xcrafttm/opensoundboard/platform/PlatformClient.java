package de.xcrafttm.opensoundboard.platform;

import java.io.File;

/** Supplies the one platform-specific path needed by the mapping-neutral config module. */
public interface PlatformClient {

    File configDirectory();
}

