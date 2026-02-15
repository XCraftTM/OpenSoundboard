package de.xcrafttm.opensoundboard.platform;

import java.io.File;

/**
 * Small abstraction layer so common logic doesn't directly depend on MC classes.
 */
public interface PlatformClient {

    File soundDirectory();

    File configDirectory();

    void openFile(File file);

    void openFolder(File folder);

    void runOnClientThread(Runnable runnable);
}

