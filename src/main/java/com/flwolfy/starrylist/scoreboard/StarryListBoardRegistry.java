package com.flwolfy.starrylist.scoreboard;

import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable registry of the six fixed leaderboards. */
public final class StarryListBoardRegistry {

  private final Map<String, StarryListBoardDefinition> boards = new LinkedHashMap<>();

  /** Creates a registry containing exactly the six built-in leaderboards. */
  public StarryListBoardRegistry() {
    addBuiltIn("mining", "sl_mining", "starrylist.board.mining");
    addBuiltIn("placing", "sl_placing", "starrylist.board.placing");
    addBuiltIn("mob_kills", "sl_mob_kills", "starrylist.board.mob_kills");
    addBuiltIn("player_kills", "sl_player_kills", "starrylist.board.player_kills");
    addBuiltIn("deaths", "sl_deaths", "starrylist.board.deaths");
    addBuiltIn("travel_distance", "sl_travel", "starrylist.board.travel_distance");
  }

  private void addBuiltIn(String id, String objective, String translationKey) {
    boards.put(id, new StarryListBoardDefinition(
        id,
        objective,
        StarryListLangManager.getInstance().text(translationKey).getString()
    ));
  }

  /**
   * Finds a built-in leaderboard by its case-insensitive identifier.
   *
   * @param id board identifier
   * @return matching board, or an empty optional for an unknown identifier
   */
  public Optional<StarryListBoardDefinition> get(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(boards.get(id.toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns all six leaderboards in their stable built-in order.
   *
   * @return immutable leaderboard collection
   */
  public Collection<StarryListBoardDefinition> all() {
    return List.copyOf(boards.values());
  }
}
