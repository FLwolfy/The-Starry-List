package com.flwolfy.starrylist.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Creates and refreshes the six fixed vanilla scoreboard objectives. */
public final class StarryListScoreboardManager {

  private final MinecraftServer server;

  /**
   * Creates an objective manager for the active server.
   *
   * @param server active Minecraft server
   */
  public StarryListScoreboardManager(MinecraftServer server) {
    this.server = server;
  }

  /**
   * Ensures every built-in board has an active objective with the current translated name.
   *
   * @param registry fixed leaderboard registry
   */
  public void reconcile(StarryListBoardRegistry registry) {
    ServerScoreboard scoreboard = server.getScoreboard();
    for (StarryListBoardDefinition board : registry.all()) {
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
      scoreboard.startTrackingObjective(objective);
    }
  }
}
