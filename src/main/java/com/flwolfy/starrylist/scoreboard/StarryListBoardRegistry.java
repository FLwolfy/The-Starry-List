package com.flwolfy.starrylist.scoreboard;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.event.StarryListEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable-in-practice registry rebuilt after each successful configuration reload. */
public final class StarryListBoardRegistry {

  private final Map<String, StarryListBoardDefinition> boards = new LinkedHashMap<>();

  public StarryListBoardRegistry(StarryListConfigData config) {
    addBuiltIn("mining", "sl_mining", "starrylist.board.mining");
    addBuiltIn("placing", "sl_placing", "starrylist.board.placing");
    addBuiltIn("mob_kills", "sl_mob_kills", "starrylist.board.mob_kills");
    addBuiltIn("player_kills", "sl_player_kills", "starrylist.board.player_kills");
    addBuiltIn("deaths", "sl_deaths", "starrylist.board.deaths");
    addBuiltIn("travel_distance", "sl_travel", "starrylist.board.travel_distance");
    for (StarryListConfigData.CustomBoard custom : config.customBoards()) {
      String id = custom.id().trim().toLowerCase(Locale.ROOT);
      boards.put(id, new StarryListBoardDefinition(
          id,
          custom.objectiveName().trim(),
          custom.displayName(),
          false,
          custom.enabled(),
          custom.sources()
      ));
    }
  }

  private void addBuiltIn(String id, String objective, String translationKey) {
    boards.put(id, new StarryListBoardDefinition(
        id,
        objective,
        StarryListLangManager.getInstance().text(translationKey).getString(),
        true,
        true,
        List.of()
    ));
  }

  public Optional<StarryListBoardDefinition> get(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(boards.get(id.toLowerCase(Locale.ROOT)));
  }

  public Collection<StarryListBoardDefinition> all() {
    return List.copyOf(boards.values());
  }

  public List<StarryListBoardDefinition> enabled() {
    return boards.values().stream().filter(StarryListBoardDefinition::enabled).toList();
  }

  public List<StarryListBoardDefinition> forEvent(StarryListEventType event) {
    List<StarryListBoardDefinition> matching = new ArrayList<>();
    for (StarryListBoardDefinition board : boards.values()) {
      if (!board.enabled() || board.builtIn()) continue;
      if (board.sources().stream().anyMatch(source -> source.trigger() == event)) matching.add(board);
    }
    return List.copyOf(matching);
  }
}
