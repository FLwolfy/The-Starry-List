package com.flwolfy.starrylist.scoreboard;

import java.util.List;
import java.util.Map;

/** Pure fixed identifiers shared by config code without initializing Minecraft item registries. */
public final class StarryListBoardIds {

  private static final List<String> VALUES = List.of(
      "mining", "placing", "mob_kills", "player_kills", "deaths", "travel_distance"
  );
  private static final Map<String, String> OBJECTIVE_NAMES = Map.of(
      "mining", "sl_mining",
      "placing", "sl_placing",
      "mob_kills", "sl_mob_kills",
      "player_kills", "sl_player_kills",
      "deaths", "sl_deaths",
      "travel_distance", "sl_travel"
  );

  private StarryListBoardIds() {}

  /** Returns all leaderboard identifiers in their canonical display order. */
  public static List<String> values() {
    return VALUES;
  }

  /**
   * Returns the persistent vanilla objective name for a board identifier.
   *
   * <p>These names are deliberately language-independent. Only an objective's display title is
   * translated, so changing the configured language always reuses the same score storage.</p>
   *
   * @param boardId built-in board identifier
   * @return fixed objective name
   */
  public static String objectiveName(String boardId) {
    String name = OBJECTIVE_NAMES.get(boardId);
    if (name == null) throw new IllegalArgumentException("Unknown board: " + boardId);
    return name;
  }

  /** Returns whether an objective name belongs to one of StarryList's six boards. */
  public static boolean ownsObjective(String objectiveName) {
    return objectiveName != null && OBJECTIVE_NAMES.containsValue(objectiveName);
  }

  /** Returns all persistent objective names in canonical board order. */
  public static List<String> objectiveNames() {
    return VALUES.stream().map(StarryListBoardIds::objectiveName).toList();
  }
}
