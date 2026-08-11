package com.flwolfy.starrylist.board.scoreboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.state.StarryListState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Creates and refreshes every discovered vanilla scoreboard objective. */
public final class StarryListScoreboardManager {

  private final MinecraftServer server;
  private final java.util.Map<String, Objective> trackedObjectives = new java.util.HashMap<>();

  /**
   * Creates an objective manager for the active server.
   *
   * @param server active Minecraft server
   */
  public StarryListScoreboardManager(MinecraftServer server) {
    this.server = server;
  }

  /**
   * Ensures every registered board has an active objective with the current translated name.
   *
   * @param registry discovered leaderboard registry
   */
  public void reconcile(StarryListBoardRegistry registry) {
    ServerScoreboard scoreboard = server.getScoreboard();
    for (StarryListBoard board : registry.all()) {
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) {
        objective = scoreboard.addObjective(
            board.objectiveName(),
            ObjectiveCriteria.DUMMY,
            board.displayName(),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            null
        );
      } else {
        objective.setDisplayName(board.displayName());
        objective.setRenderType(ObjectiveCriteria.RenderType.INTEGER);
      }
      if (trackedObjectives.get(board.objectiveName()) != objective) {
        scoreboard.startTrackingObjective(objective);
        trackedObjectives.put(board.objectiveName(), objective);
      }
    }
  }

  /**
   * Archives and removes objectives whose script definitions are being removed or replaced.
   *
   * @param boards old script board definitions to deactivate
   * @param state world state receiving score archives
   */
  public void archiveAndRemove(List<? extends StarryListBoard> boards, StarryListState state) {
    ServerScoreboard scoreboard = server.getScoreboard();
    for (StarryListBoard board : boards) {
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) {
        continue;
      }

      Map<String, StarryListState.InactiveScore> archived = new HashMap<>();
      for (var entry : List.copyOf(scoreboard.listPlayerScores(objective))) {
        archived.put(entry.owner(), new StarryListState.InactiveScore(
            entry.value(),
            entry.display() == null ? entry.owner() : entry.display().getString()
        ));
      }
      state.archiveInactiveBoard(board.id(), archived);
      trackedObjectives.remove(board.objectiveName());
      scoreboard.removeObjective(objective);
    }
  }

  /**
   * Restores archived scores for every currently registered script board.
   *
   * @param boards current script board definitions
   * @param state world state containing inactive score archives
   */
  public void restoreArchived(List<? extends StarryListBoard> boards, StarryListState state) {
    ServerScoreboard scoreboard = server.getScoreboard();
    for (StarryListBoard board : boards) {
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) {
        continue;
      }

      Map<String, StarryListState.InactiveScore> archived = state.inactiveBoardScores(board.id());
      for (Map.Entry<String, StarryListState.InactiveScore> entry : archived.entrySet()) {
        var score = scoreboard.getOrCreatePlayerScore(
            ScoreHolder.forNameOnly(entry.getKey()), objective
        );
        score.set(entry.getValue().value());
        score.display(Component.literal(entry.getValue().displayName()));
      }
      if (!archived.isEmpty()) {
        state.clearInactiveBoard(board.id());
      }
    }
  }
}
