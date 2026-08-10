package com.flwolfy.starrylist;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.script.StarryListShellExecutor;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.display.StarryListDisplayManager;
import com.flwolfy.starrylist.display.StarryListDisplayScheduler;
import com.flwolfy.starrylist.event.StarryListEventDispatcher;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import com.flwolfy.starrylist.scoreboard.StarryListScoreService;
import com.flwolfy.starrylist.scoreboard.StarryListScoreboardManager;
import net.minecraft.server.MinecraftServer;

/** Owns all world-bound StarryList services for the active server. */
public final class StarryListRuntime {

  private final MinecraftServer server;
  private final StarryListState state;
  private final StarryListScoreboardManager scoreboardManager;
  private final StarryListScoreService scores;
  private final StarryListDisplayManager display;
  private final StarryListDisplayScheduler displayScheduler;
  private final StarryListEventDispatcher events;
  private volatile StarryListBoardRegistry registry;

  public StarryListRuntime(MinecraftServer server) {
    this.server = server;
    this.state = server.getDataStorage().computeIfAbsent(StarryListState.TYPE);
    this.registry = new StarryListBoardRegistry(StarryListConfigManager.getInstance().data());
    this.scoreboardManager = new StarryListScoreboardManager(server, state);
    this.scores = new StarryListScoreService(server, this::registry);
    this.display = new StarryListDisplayManager(
        server,
        state,
        () -> StarryListConfigManager.getInstance().data(),
        this::registry
    );
    this.displayScheduler = new StarryListDisplayScheduler(display);
    this.events = new StarryListEventDispatcher(server, this::registry, scores);
    scoreboardManager.reconcile(registry);
  }

  public void applyConfig(StarryListConfigData config) {
    StarryListBoardRegistry next = new StarryListBoardRegistry(config);
    scoreboardManager.reconcile(next);
    registry = next;
    events.invalidate();
    display.updateAll(true);
  }

  public void tick() {
    displayScheduler.tick();
    events.tickScheduled();
  }

  public void shutdown() {
    events.shutdown();
    StarryListShellExecutor.shutdown();
  }

  public MinecraftServer server() {
    return server;
  }

  public StarryListState state() {
    return state;
  }

  public StarryListBoardRegistry registry() {
    return registry;
  }

  public StarryListScoreService scores() {
    return scores;
  }

  public StarryListDisplayManager display() {
    return display;
  }

  public StarryListEventDispatcher events() {
    return events;
  }

  public StarryListScoreboardManager scoreboardManager() {
    return scoreboardManager;
  }
}
