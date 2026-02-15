package de.xcrafttm.opensoundboard.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores per-sound settings. This is persisted separately from the global config.
 */
@Data
@NoArgsConstructor
public class SoundStore {
    Map<String, SoundboardConfig.SoundData> sounds = new HashMap<>();
}
