package com.flwolfy.starrylist.scoreboard;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.state.StarryListState;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Creates, updates, migrates, tracks, and safely prunes managed scoreboard objectives. */
public final class StarryListScoreboardManager {

  private final MinecraftServer server;
  private final StarryListState state;

  public StarryListScoreboardManager(MinecraftServer server, StarryListState state) {
    this.server = server;
    this.state = state;
  }

  public void reconcile(StarryListBoardRegistry registry) {
    ServerScoreboard scoreboard = server.getScoreboard();
    Map<String, String> previous = state.managedObjectives();
    for (StarryListBoardDefinition board : registry.all()) {
      String priorName = previous.get(board.id());
      Objective objective = scoreboard.getObjective(board.objectiveName());
      boolean knownOrphan = state.orphanObjectives().contains(board.objectiveName());
      if (objective != null && !knownOrphan
          && (priorName == null || !priorName.equals(board.objectiveName()))) {
        throw new IllegalStateException(
            "Objective '" + board.objectiveName() + "' is not owned by StarryList board " + board.id()
        );
      }
    }
    Set<String> activeIds = new HashSet<>();
    for (StarryListBoardDefinition board : registry.all()) {
      activeIds.add(board.id());
      String priorName = previous.get(board.id());
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) {
        objective = scoreboard.addObjective(
            board.objectiveName(),
            ObjectiveCriteria.DUMMY,
            Component.literal(board.displayName()),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            null
        );
      } else {
        objective.setDisplayName(Component.literal(board.displayName()));
        objective.setRenderType(ObjectiveCriteria.RenderType.INTEGER);
      }
      if (priorName != null && !priorName.equals(board.objectiveName())) {
        migrate(scoreboard, priorName, objective);
        Objective previousObjective = scoreboard.getObjective(priorName);
        if (previousObjective != null) scoreboard.stopTrackingObjective(previousObjective);
      }
      state.manageObjective(board.id(), board.objectiveName());
      if (board.enabled()) {
        scoreboard.startTrackingObjective(objective);
      } else {
        scoreboard.stopTrackingObjective(objective);
      }
    }
    previous.keySet().stream().filter(id -> !activeIds.contains(id)).forEach(id -> {
      Objective removed = scoreboard.getObjective(previous.get(id));
      if (removed != null) scoreboard.stopTrackingObjective(removed);
      state.removeManagedBoard(id);
    });
  }

  private static void migrate(ServerScoreboard scoreboard, String previousName, Objective target) {
    Objective previous = scoreboard.getObjective(previousName);
    if (previous == null) return;
    for (var entry : java.util.List.copyOf(scoreboard.listPlayerScores(previous))) {
      var access = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.owner()), target);
      access.set(entry.value());
      if (entry.display() != null) access.display(entry.display());
    }
  }

  public int prune() {
    int removed = 0;
    ServerScoreboard scoreboard = server.getScoreboard();
    for (String name : state.orphanObjectives()) {
      Objective objective = scoreboard.getObjective(name);
      if (objective != null) {
        scoreboard.removeObjective(objective);
        removed++;
      }
    }
    state.clearOrphans();
    StarryListMod.LOGGER.info("Pruned {} orphan StarryList objectives", removed);
    return removed;
  }
}
