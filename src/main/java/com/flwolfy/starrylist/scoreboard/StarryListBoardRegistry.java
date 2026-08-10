package com.flwolfy.starrylist.scoreboard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.item.Items;

/** Immutable registry of the six fixed leaderboards. */
public final class StarryListBoardRegistry {

  private static final List<StarryListBoardDefinition> BOARDS = List.of(
      new StarryListBoardDefinition(
          "mining", "sl_mining", "starrylist.board.mining", Items.DIAMOND_PICKAXE
      ),
      new StarryListBoardDefinition(
          "placing", "sl_placing", "starrylist.board.placing", Items.BRICKS
      ),
      new StarryListBoardDefinition(
          "mob_kills", "sl_mob_kills", "starrylist.board.mob_kills", Items.ZOMBIE_HEAD
      ),
      new StarryListBoardDefinition(
          "player_kills", "sl_player_kills", "starrylist.board.player_kills", Items.PLAYER_HEAD
      ),
      new StarryListBoardDefinition(
          "deaths", "sl_deaths", "starrylist.board.deaths", Items.SKELETON_SKULL
      ),
      new StarryListBoardDefinition(
          "travel_distance", "sl_travel", "starrylist.board.travel_distance", Items.COMPASS
      )
  );
  private static final Map<String, StarryListBoardDefinition> BY_ID = BOARDS.stream()
      .collect(Collectors.toUnmodifiableMap(StarryListBoardDefinition::id, Function.identity()));

  /** Creates a registry containing exactly the six built-in leaderboards. */
  public StarryListBoardRegistry() {}

  /**
   * Finds a built-in leaderboard by its case-insensitive identifier.
   *
   * @param id board identifier
   * @return matching board, or an empty optional for an unknown identifier
   */
  public Optional<StarryListBoardDefinition> get(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns all six leaderboards in their stable built-in order.
   *
   * @return immutable leaderboard collection
   */
  public List<StarryListBoardDefinition> all() {
    return BOARDS;
  }

  /**
   * Returns the stable identifiers of all fixed leaderboards.
   *
   * @return immutable fixed board identifier list
   */
  public static List<String> ids() {
    return BOARDS.stream().map(StarryListBoardDefinition::id).toList();
  }
}
