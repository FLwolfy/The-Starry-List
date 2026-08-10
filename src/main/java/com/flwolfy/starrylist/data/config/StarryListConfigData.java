package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.data.script.StarryListScriptManager;
import com.flwolfy.starrylist.event.StarryListEventType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record StarryListConfigData(
    General general,
    Display display,
    List<CustomBoard> customBoards
) {

  private static final Pattern BOARD_ID = Pattern.compile("[a-z0-9._-]{1,64}");
  public static final List<String> BUILT_IN_IDS = List.of(
      "mining", "placing", "mob_kills", "player_kills", "deaths", "travel_distance"
  );

  public record General(StarryListLang language, int adminPermissionLevel) {}

  public record Display(
      boolean hiddenByDefault,
      boolean rotationEnabled,
      int rotationIntervalSeconds,
      List<String> defaultBoards
  ) {}

  public record CustomBoard(
      String id,
      String objectiveName,
      String displayName,
      boolean enabled,
      List<Source> sources
  ) {}

  public record Source(
      StarryListEventType trigger,
      UpdateMode update,
      int intervalSeconds,
      StarryListScript script
  ) {}

  public enum UpdateMode {
    ADD,
    SET
  }

  public static final StarryListConfigData DEFAULT = new StarryListConfigData(
      new General(StarryListLang.ENGLISH, 2),
      new Display(
          false,
          true,
          20,
          List.of("mining", "placing", "mob_kills", "player_kills")
      ),
      List.of()
  );

  /** Returns complete field paths that prevent this configuration from becoming active. */
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

    Set<String> ids = new HashSet<>(BUILT_IN_IDS);
    Set<String> objectives = new HashSet<>(List.of(
        "sl_mining", "sl_placing", "sl_mob_kills", "sl_player_kills", "sl_deaths", "sl_travel"
    ));
    List<CustomBoard> boards = customBoards == null ? List.of() : customBoards;
    for (int boardIndex = 0; boardIndex < boards.size(); boardIndex++) {
      CustomBoard board = boards.get(boardIndex);
      String root = "customBoards[" + boardIndex + "]";
      String id = board == null || board.id() == null
          ? "" : board.id().trim().toLowerCase(Locale.ROOT);
      if (!BOARD_ID.matcher(id).matches() || !ids.add(id)) invalid.add(root + ".id");
      String objective = board == null || board.objectiveName() == null
          ? "" : board.objectiveName().trim();
      if (objective.isEmpty() || objective.length() > 64 || !objectives.add(objective)) {
        invalid.add(root + ".objectiveName");
      }
      if (board == null || board.displayName() == null || board.displayName().isBlank()) {
        invalid.add(root + ".displayName");
      }
      List<Source> sources = board == null || board.sources() == null
          ? List.of() : board.sources();
      if (board != null && board.enabled() && sources.isEmpty()) invalid.add(root + ".sources");
      for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
        Source source = sources.get(sourceIndex);
        String sourceRoot = root + ".sources[" + sourceIndex + "]";
        if (source == null || source.trigger() == null) invalid.add(sourceRoot + ".trigger");
        if (source == null || source.update() == null) invalid.add(sourceRoot + ".update");
        if (source != null && source.trigger() == StarryListEventType.SCHEDULED) {
          if (source.update() != UpdateMode.SET) invalid.add(sourceRoot + ".update");
          if (source.intervalSeconds() < 1 || source.intervalSeconds() > 86400) {
            invalid.add(sourceRoot + ".intervalSeconds");
          }
        }
        if (source == null || source.script() == null || source.script().source().isBlank()
            || !StarryListScriptManager.getInstance().validate(source.script())) {
          invalid.add(sourceRoot + ".script");
        }
      }
    }

    Set<String> enabled = new HashSet<>(BUILT_IN_IDS);
    boards.stream().filter(java.util.Objects::nonNull).filter(CustomBoard::enabled)
        .map(CustomBoard::id).filter(java.util.Objects::nonNull)
        .map(value -> value.toLowerCase(Locale.ROOT)).forEach(enabled::add);
    if (defaults.stream().anyMatch(id -> !enabled.contains(id))) {
      invalid.add("display.defaultBoards");
    }
    return List.copyOf(new java.util.LinkedHashSet<>(invalid));
  }

  public static List<String> normalizeIds(List<String> ids) {
    if (ids == null) return List.of();
    return ids.stream()
        .filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .toList();
  }
}
