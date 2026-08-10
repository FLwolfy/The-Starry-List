package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;

/**
 * Persistent per-player sidebar mode, board order, and rotation settings.
 *
 * @param mode relationship to server defaults
 * @param boards ordered personal board identifiers
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
    /** Follow the active server display configuration. */
    DEFAULT,
    /** Use the player's stored board order and rotation settings. */
    CUSTOM,
    /** Hide the StarryList sidebar. */
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

  /** Ensures a profile cannot be mutated through its source board list. */
  public StarryListDisplayProfile {
    boards = List.copyOf(boards);
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
