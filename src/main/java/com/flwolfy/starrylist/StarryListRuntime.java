package com.flwolfy.starrylist;

import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.display.StarryListDisplayManager;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import com.flwolfy.starrylist.scoreboard.StarryListScoreService;
import com.flwolfy.starrylist.scoreboard.StarryListScoreboardManager;
import net.minecraft.server.MinecraftServer;

/** Owns all world-bound StarryList services for the active server. */
public final class StarryListRuntime {

  private final StarryListState state;
  private final StarryListScoreboardManager scoreboardManager;
  private final StarryListScoreService scores;
  private final StarryListDisplayManager display;
  private volatile StarryListBoardRegistry registry;

  /**
   * Creates all world-bound services for an active server.
   *
   * @param server active Minecraft server
   */
  StarryListRuntime(MinecraftServer server) {
    this.state = server.getDataStorage().computeIfAbsent(StarryListState.TYPE);
    this.registry = new StarryListBoardRegistry();
    this.scoreboardManager = new StarryListScoreboardManager(server);
    this.scores = new StarryListScoreService(server, this::registry);
    this.display = new StarryListDisplayManager(
        server,
        state,
        () -> StarryListConfigManager.getInstance().data(),
        this::registry
    );
    scoreboardManager.reconcile(registry);
  }

  /**
   * Rebuilds translated board metadata and refreshes every online sidebar after a config reload.
   *
   */
  void applyConfig() {
    StarryListBoardRegistry next = new StarryListBoardRegistry();
    scoreboardManager.reconcile(next);
    registry = next;
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

  /**
   * Returns the current translated board definitions.
   *
   * @return current fixed leaderboard registry
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
  public StarryListDisplayManager display() {
    return display;
  }
}
