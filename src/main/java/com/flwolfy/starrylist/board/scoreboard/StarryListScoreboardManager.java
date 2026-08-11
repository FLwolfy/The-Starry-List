package com.flwolfy.starrylist.board.scoreboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
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
}
