package com.flwolfy.starrylist.data.state;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;

/**
 * Persistent per-player sidebar mode, enabled boards, and rotation settings.
 *
 * @param mode relationship to server defaults
 * @param boards enabled personal board identifiers in discovered canonical order
 * @param rotationEnabled whether multiple boards rotate
 * @param rotationIntervalSeconds personal rotation interval in seconds
 */
public record StarryListDisplayProfile(
    Mode mode,
    List<String> boards,
    boolean rotationEnabled,
    int rotationIntervalSeconds
) {

  /** Supported relationships between a player profile and server defaults. */
  public enum Mode {
    /** Inherit the server's current display configuration. */
    DEFAULT,

    /** Use the player's saved board selection and rotation settings. */
    CUSTOM,

    /** Hide the sidebar while retaining the player's saved preferences. */
    HIDDEN
  }

  static final Codec<StarryListDisplayProfile> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.STRING.xmap(
              value -> Mode.valueOf(value.toUpperCase(Locale.ROOT)),
              value -> value.name().toLowerCase(Locale.ROOT)
          ).fieldOf("mode").forGetter(StarryListDisplayProfile::mode),
          Codec.STRING.listOf().fieldOf("boards").forGetter(StarryListDisplayProfile::boards),
          Codec.BOOL.fieldOf("rotationEnabled").forGetter(StarryListDisplayProfile::rotationEnabled),
          Codec.INT.fieldOf("rotationIntervalSeconds").forGetter(
              StarryListDisplayProfile::rotationIntervalSeconds
          )
      ).apply(instance, StarryListDisplayProfile::new)
  );

  /**
   * Creates a display profile with an immutable, canonically ordered board list.
   *
   * @param mode the relationship to server defaults
   * @param boards the enabled personal board identifiers
   * @param rotationEnabled whether multiple boards rotate
   * @param rotationIntervalSeconds the personal rotation interval in seconds
   */
  public StarryListDisplayProfile {
    boards = StarryListConfigData.normalizeIds(boards);
  }

  /**
   * Returns the implicit profile used when a player has no saved override.
   *
   * @return default display profile
   */
  public static StarryListDisplayProfile defaultProfile() {
    return new StarryListDisplayProfile(Mode.DEFAULT, List.of(), true, 20);
  }
}
