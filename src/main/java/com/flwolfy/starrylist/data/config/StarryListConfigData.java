package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable server configuration for language and leaderboard display behavior.
 *
 * @param general general server settings
 * @param display default sidebar settings
 * @param blacklist player-name exclusion patterns
 */
public record StarryListConfigData(
    General general,
    Display display,
    Blacklist blacklist
) {

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
   * @param enabledBoards enabled default board identifiers in discovered canonical order
   */
  public record Display(
      boolean hiddenByDefault,
      boolean rotationEnabled,
      int rotationIntervalSeconds,
      List<String> enabledBoards
  ) {
    /**
     * Creates display settings with an immutable enabled-board collection.
     *
     * @param hiddenByDefault whether the default sidebar is hidden
     * @param rotationEnabled whether multiple default boards rotate
     * @param rotationIntervalSeconds the default rotation interval in seconds
     * @param enabledBoards the enabled default board identifiers
     */
    public Display {
      enabledBoards = enabledBoards == null ? null : List.copyOf(enabledBoards);
    }
  }

  /**
   * Player-name patterns excluded from automatic scoring and visible objectives.
   *
   * @param playerNamePatterns case-insensitive Java regular expressions matched against full names
   */
  public record Blacklist(List<String> playerNamePatterns) {
    /**
     * Creates blacklist settings with an immutable pattern collection.
     *
     * @param playerNamePatterns the player-name regular expressions
     */
    public Blacklist {
      playerNamePatterns = playerNamePatterns == null ? null : List.copyOf(playerNamePatterns);
    }
  }

  public static final StarryListConfigData DEFAULT = new StarryListConfigData(
      new General(StarryListLang.ENGLISH, 2),
      new Display(
          false,
          true,
          20,
          List.of("mining", "placing", "mob_kills")
      ),
      new Blacklist(List.of())
  );

  /**
   * Returns field paths that prevent this configuration from becoming active.
   *
   * @return immutable field paths for every invalid setting
   */
  public List<String> validate() {
    List<String> invalid = new ArrayList<>();
    if (general == null || general.language() == null) {
      invalid.add("general.language");
    }

    if (general == null || general.adminPermissionLevel() < 0
        || general.adminPermissionLevel() > 4) {
      invalid.add("general.adminPermissionLevel");
    }
    if (display == null || display.rotationIntervalSeconds() < 1
        || display.rotationIntervalSeconds() > 3600) {
      invalid.add("display.rotationIntervalSeconds");
    }

    if (display == null || display.enabledBoards() == null) {
      invalid.add("display.enabledBoards");
    }

    List<String> normalizedInput = display == null || display.enabledBoards() == null
        ? List.of() : display.enabledBoards().stream()
            .filter(java.util.Objects::nonNull)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .toList();
    if (display != null && display.enabledBoards() != null
        && display.enabledBoards().stream().anyMatch(
            value -> value == null || value.isBlank()
        )) {
      invalid.add("display.enabledBoards");
    }
    if (new HashSet<>(normalizedInput).size() != normalizedInput.size()) {
      invalid.add("display.enabledBoards");
    }

    Set<String> registeredIds = Set.copyOf(StarryListBoardRegistry.getInstance().ids());
    if (normalizedInput.stream().anyMatch(id -> !registeredIds.contains(id))) {
      invalid.add("display.enabledBoards");
    }
    if (blacklist == null || blacklist.playerNamePatterns() == null) {
      invalid.add("blacklist.playerNamePatterns");
    } else {
      for (String expression : blacklist.playerNamePatterns()) {
        if (expression == null || expression.isBlank()) {
          invalid.add("blacklist.playerNamePatterns");
          break;
        }
        try {
          Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException exception) {
          invalid.add("blacklist.playerNamePatterns");
          break;
        }
      }
    }

    return List.copyOf(new java.util.LinkedHashSet<>(invalid));
  }

  /**
   * Normalizes board identifiers into discovered canonical display order.
   *
   * @param ids board identifiers to normalize
   * @return normalized nonblank identifiers
   */
  public static List<String> normalizeIds(List<String> ids) {
    if (ids == null) {
      return List.of();
    }

    return StarryListBoardRegistry.getInstance().normalizeIds(ids);
  }
}
