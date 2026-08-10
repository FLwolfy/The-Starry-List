package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable server configuration for language and leaderboard display behavior.
 *
 * @param general general server settings
 * @param display default sidebar settings
 */
public record StarryListConfigData(
    General general,
    Display display
) {

  private static final List<String> BUILT_IN_IDS = List.of(
      "mining", "placing", "mob_kills", "player_kills", "deaths", "travel_distance"
  );

  /**
   * General server settings.
   *
   * @param language server message language
   * @param adminPermissionLevel required vanilla administrator permission level
   */
  public record General(StarryListLang language, int adminPermissionLevel) {}

  /**
   * Default sidebar settings inherited by players without a custom display profile.
   *
   * @param hiddenByDefault whether the default sidebar is hidden
   * @param rotationEnabled whether multiple default boards rotate
   * @param rotationIntervalSeconds default rotation interval in seconds
   * @param defaultBoards ordered default board identifiers
   */
  public record Display(
      boolean hiddenByDefault,
      boolean rotationEnabled,
      int rotationIntervalSeconds,
      List<String> defaultBoards
  ) {
    /** Ensures the configured board order cannot be mutated through the source list. */
    public Display {
      defaultBoards = defaultBoards == null ? null : List.copyOf(defaultBoards);
    }
  }

  /** Default configuration written when no configuration file exists. */
  public static final StarryListConfigData DEFAULT = new StarryListConfigData(
      new General(StarryListLang.ENGLISH, 2),
      new Display(
          false,
          true,
          20,
          List.of("mining", "placing", "mob_kills", "player_kills")
      )
  );

  /**
   * Returns field paths that prevent this configuration from becoming active.
   *
   * @return immutable field paths for every invalid setting
   */
  public List<String> validate() {
    List<String> invalid = new ArrayList<>();
    if (general == null || general.language() == null) invalid.add("general.language");
    if (general == null || general.adminPermissionLevel() < 0
        || general.adminPermissionLevel() > 4) {
      invalid.add("general.adminPermissionLevel");
    }
    if (display == null || display.rotationIntervalSeconds() < 1
        || display.rotationIntervalSeconds() > 3600) {
      invalid.add("display.rotationIntervalSeconds");
    }

    List<String> defaults = display == null || display.defaultBoards() == null
        ? List.of() : normalizeIds(display.defaultBoards());
    if (display == null || (!display.hiddenByDefault() && defaults.isEmpty())) {
      invalid.add("display.defaultBoards");
    }
    if (new HashSet<>(defaults).size() != defaults.size()) {
      invalid.add("display.defaultBoards");
    }
    Set<String> builtIns = Set.copyOf(BUILT_IN_IDS);
    if (defaults.stream().anyMatch(id -> !builtIns.contains(id))) {
      invalid.add("display.defaultBoards");
    }
    return List.copyOf(new java.util.LinkedHashSet<>(invalid));
  }

  /**
   * Normalizes board identifiers while preserving their order.
   *
   * @param ids board identifiers to normalize
   * @return normalized nonblank identifiers
   */
  public static List<String> normalizeIds(List<String> ids) {
    if (ids == null) return List.of();
    return ids.stream()
        .filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .toList();
  }
}
