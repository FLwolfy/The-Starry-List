package com.flwolfy.starrylist;

import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.config.StarryListBlacklist;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.display.StarryListSidebarManager;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.scoreboard.StarryListScoreService;
import com.flwolfy.starrylist.board.scoreboard.StarryListScoreboardManager;
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
    scores.reconcileBlacklist();
  }

  /**
   * Rebuilds translated board metadata and refreshes every online sidebar after a config reload.
   *
   */
  void applyConfig() {
    blacklist.apply(StarryListConfigManager.getInstance().data());
    scoreboardManager.reconcile(registry);
    scores.reconcileBlacklist();
    display.updateAll(true);
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

  /** Returns the server that owns this runtime. */
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
}
