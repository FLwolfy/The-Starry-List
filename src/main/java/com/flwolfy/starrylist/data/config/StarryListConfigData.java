package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
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
 * @param boards board loading settings
 * @param blacklist player-name exclusion patterns
 */
public record StarryListConfigData(
    General general,
    Display display,
    Boards boards,
    Blacklist blacklist
) {

  /**
   * General server settings.
   *
   * @param language server message language
   * @param adminPermissionLevel required vanilla administrator permission level
   */
  public record General(String language, int adminPermissionLevel) {}

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
   * Controls which discovered board modules are loaded into the active catalog.
   *
   * @param disabledBoards discovered built-in board identifiers excluded from gameplay
   * @param enabledScriptBoards Groovy board identifiers explicitly enabled for gameplay
   */
  public record Boards(List<String> disabledBoards, List<String> enabledScriptBoards) {
    /**
     * Creates board loading settings with an immutable disabled-board collection.
     *
     * @param disabledBoards discovered built-in board identifiers excluded from gameplay
     * @param enabledScriptBoards Groovy board identifiers explicitly enabled for gameplay
     */
    public Boards {
      disabledBoards = disabledBoards == null ? null : List.copyOf(disabledBoards);
      enabledScriptBoards = enabledScriptBoards == null
          ? null : List.copyOf(enabledScriptBoards);
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
      new General("en_us", 2),
      new Display(
          false,
          true,
          20,
          List.of("mining", "placing", "mob_kills")
      ),
      new Boards(List.of(), List.of()),
      new Blacklist(List.of())
  );

  /**
   * Returns field paths that prevent this configuration from becoming active.
   *
   * @return immutable field paths for every invalid setting
   */
  public List<String> validate() {
    List<String> invalid = new ArrayList<>();
    if (general == null || general.language() == null
        || !general.language().matches("[a-z0-9][a-z0-9_-]*")) {
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

    Set<String> registeredIds = new HashSet<>(
        StarryListBoardRegistry.getInstance().definitionIds()
    );
    registeredIds.addAll(
        com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance().previewIds()
    );
    if (normalizedInput.stream().anyMatch(id -> !registeredIds.contains(id))) {
      invalid.add("display.enabledBoards");
    }
    if (boards == null || boards.disabledBoards() == null) {
      invalid.add("boards.disabledBoards");
    } else {
      List<String> disabled = normalizeInput(boards.disabledBoards());
      if (boards.disabledBoards().stream().anyMatch(
          value -> value == null || value.isBlank()
      ) || new HashSet<>(disabled).size() != disabled.size()
          || disabled.stream().anyMatch(id -> !registeredIds.contains(id))) {
        invalid.add("boards.disabledBoards");
      }
    }
    if (boards == null || boards.enabledScriptBoards() == null) {
      invalid.add("boards.enabledScriptBoards");
    } else {
      List<String> enabledScripts = normalizeInput(boards.enabledScriptBoards());
      Set<String> scriptIds = StarryListBoardRegistry.getInstance().definitions().stream()
          .filter(com.flwolfy.starrylist.board.script.StarryListScriptBoard.class::isInstance)
          .map(com.flwolfy.starrylist.board.base.StarryListBoard::id)
          .collect(java.util.stream.Collectors.toSet());
      scriptIds.addAll(
          com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance().previewIds()
      );
      if (boards.enabledScriptBoards().stream().anyMatch(
          value -> value == null || value.isBlank()
      ) || new HashSet<>(enabledScripts).size() != enabledScripts.size()
          || enabledScripts.stream().anyMatch(id -> !scriptIds.contains(id))) {
        invalid.add("boards.enabledScriptBoards");
      }
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

    List<String> normalized = new ArrayList<>(
        StarryListBoardRegistry.getInstance().normalizeIds(ids)
    );
    Set<String> requested = new HashSet<>(normalizeInput(ids));
    for (String previewId
        : com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance().previewIds()) {
      if (requested.contains(previewId) && !normalized.contains(previewId)) {
        normalized.add(previewId);
      }
    }

    return List.copyOf(normalized);
  }

  private static List<String> normalizeInput(List<String> ids) {
    return ids.stream()
        .filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .toList();
  }
}
