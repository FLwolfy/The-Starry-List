package com.flwolfy.starrylist;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.scoreboard.StarryListScoreService;
import com.flwolfy.starrylist.board.scoreboard.StarryListScoreboardManager;
import com.flwolfy.starrylist.data.config.StarryListBlacklist;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.display.StarryListSidebarManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/** Owns all world-bound StarryList services for the active server. */
public final class StarryListRuntime {

  private final MinecraftServer server;
  private final StarryListState state;
  private final StarryListBlacklist blacklist;
  private final StarryListScoreboardManager scoreboardManager;
  private final StarryListScoreService scores;
  private final StarryListSidebarManager display;
  private final StarryListBoardRegistry registry;
  private Map<String, StarryListBoard> activeBoards;

  /**
   * Creates all world-bound services for an active server.
   *
   * @param server active Minecraft server
   */
  StarryListRuntime(MinecraftServer server) {
    this.server = server;
    this.state = server.getDataStorage().computeIfAbsent(StarryListState.TYPE);
    this.blacklist = new StarryListBlacklist();
    this.blacklist.apply(StarryListConfigManager.getInstance().data());
    this.registry = StarryListBoardRegistry.getInstance();
    this.scoreboardManager = new StarryListScoreboardManager(server);
    this.scores = new StarryListScoreService(server, this::registry, state, blacklist);
    this.display = new StarryListSidebarManager(
        server,
        state,
        () -> StarryListConfigManager.getInstance().data(),
        this::registry
    );
    scoreboardManager.reconcile(registry);
    scoreboardManager.restoreArchived(registry.all(), state);
    activeBoards = index(registry.all());
    scores.reconcileBlacklist();
  }

  /**
   * Rebuilds translated board metadata and refreshes every online sidebar after a config reload.
   */
  void applyConfig() {
    reconcileCatalog();
    blacklist.apply(StarryListConfigManager.getInstance().data());
    scores.reconcileBlacklist();
    display.updateAll(true);
    com.flwolfy.starrylist.display.StarryListPlayerSGUI.refreshAll(this);
  }

  /** Advances per-player sidebar rotation by one server tick. */
  void tick() {
    display.tick();
  }

  /**
   * Returns persistent display and travel state.
   *
   * @return persistent state for the active world
   */
  public StarryListState state() {
    return state;
  }

  /**
   * Returns the server that owns this runtime.
   *
   * @return the active server
   */
  public MinecraftServer server() {
    return server;
  }

  /**
   * Returns the current translated board definitions.
   *
   * @return immutable discovered leaderboard registry
   */
  public StarryListBoardRegistry registry() {
    return registry;
  }

  /**
   * Returns the service used for all score reads and mutations.
   *
   * @return authoritative leaderboard score service
   */
  public StarryListScoreService scores() {
    return scores;
  }

  /**
   * Returns the per-player sidebar coordinator.
   *
   * @return per-player sidebar display manager
   */
  public StarryListSidebarManager display() {
    return display;
  }

  /**
   * Permanently removes persisted data for board identifiers no longer discovered.
   *
   * @return counts of removed orphaned values
   */
  public StarryListState.PruneResult pruneOrphanedData() {
    return state.pruneOrphanedBoards(Set.copyOf(registry.definitionIds()));
  }

  /**
   * Archives objectives that must be removed before a script catalog replacement.
   *
   * @param boards old script definitions being removed or changing objective names
   */
  public void deactivateScriptBoards(List<? extends StarryListBoard> boards) {
    scoreboardManager.archiveAndRemove(boards, state);
  }

  /**
   * Reconciles all services after a script catalog replacement.
   *
   * @param removedIds script identifiers removed from the new catalog
   */
  public void activateScriptCatalog(Set<String> removedIds) {
    state.removeBoardsFromProfiles(removedIds);
    reconcileCatalog();
    scores.reconcileBlacklist();
    display.updateAll(true);
    com.flwolfy.starrylist.display.StarryListPlayerSGUI.refreshAll(this);
  }

  private void reconcileCatalog() {
    Map<String, StarryListBoard> next = index(registry.all());
    List<StarryListBoard> deactivated = activeBoards.entrySet().stream()
        .filter(entry -> {
          StarryListBoard replacement = next.get(entry.getKey());
          return replacement == null
              || !replacement.objectiveName().equals(entry.getValue().objectiveName());
        })
        .map(Map.Entry::getValue)
        .toList();
    scoreboardManager.archiveAndRemove(deactivated, state);
    scoreboardManager.reconcile(registry);
    scoreboardManager.restoreArchived(registry.all(), state);
    activeBoards = next;
    com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance()
        .applyActiveBoards(registry.ids());
  }

  private static Map<String, StarryListBoard> index(List<StarryListBoard> boards) {
    Map<String, StarryListBoard> result = new LinkedHashMap<>();
    boards.forEach(board -> result.put(board.id(), board));
    return Map.copyOf(result);
  }
}
