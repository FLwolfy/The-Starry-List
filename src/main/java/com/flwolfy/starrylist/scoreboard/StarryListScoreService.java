package com.flwolfy.starrylist.scoreboard;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;

/** The only application service allowed to mutate StarryList scoreboard entries. */
public final class StarryListScoreService {

  private final MinecraftServer server;
  private final java.util.function.Supplier<StarryListBoardRegistry> registry;

  /**
   * Creates a score service for the active server and registry.
   *
   * @param server active Minecraft server
   * @param registry current fixed leaderboard registry supplier
   */
  public StarryListScoreService(
      MinecraftServer server,
      java.util.function.Supplier<StarryListBoardRegistry> registry
  ) {
    this.server = server;
    this.registry = registry;
  }

  /**
   * Reads a player's score, returning zero when no entry exists.
   *
   * @param boardId board identifier
   * @param playerId player UUID
   * @return current score
   */
  public int get(String boardId, UUID playerId) {
    Objective objective = objective(boardId);
    ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(holder(playerId), objective);
    return score == null ? 0 : score.value();
  }

  /**
   * Replaces a player's score and refreshes its display name.
   *
   * @param boardId board identifier
   * @param player player to update
   * @param value replacement score
   * @return stored score
   */
  public int set(String boardId, ServerPlayer player, int value) {
    return set(boardId, player.getUUID(), player.getGameProfile().name(), value);
  }

  private int set(String boardId, UUID playerId, String displayName, int value) {
    ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(holder(playerId), objective(boardId));
    score.set(value);
    score.display(Component.literal(displayName));
    return value;
  }

  /**
   * Adds a delta to a player's score and refreshes its display name.
   *
   * @param boardId board identifier
   * @param player player to update
   * @param delta signed score delta
   * @return updated score
   * @throws IllegalArgumentException when the result exceeds the 32-bit integer range
   */
  public int add(String boardId, ServerPlayer player, int delta) {
    return add(boardId, player.getUUID(), player.getGameProfile().name(), delta);
  }

  /**
   * Refreshes the vanilla display component attached to every existing StarryList score.
   *
   * @param player online player whose name should be refreshed
   */
  public void remember(ServerPlayer player) {
    for (var board : registry.get().all()) {
      Objective objective = server.getScoreboard().getObjective(board.objectiveName());
      if (objective == null) continue;
      if (server.getScoreboard().getPlayerScoreInfo(holder(player.getUUID()), objective) == null) continue;
      ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(holder(player.getUUID()), objective);
      score.display(Component.literal(player.getGameProfile().name()));
    }
  }

  private int add(String boardId, UUID playerId, String displayName, int delta) {
    int current = get(boardId, playerId);
    int next;
    try {
      next = Math.addExact(current, delta);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Score update exceeds the 32-bit integer range", exception);
    }
    return set(boardId, playerId, displayName, next);
  }

  /**
   * Removes one player's entry from a leaderboard.
   *
   * @param boardId board identifier
   * @param playerId player UUID
   */
  public void reset(String boardId, UUID playerId) {
    server.getScoreboard().resetSinglePlayerScore(holder(playerId), objective(boardId));
  }

  /**
   * Removes every score entry from a leaderboard.
   *
   * @param boardId board identifier
   * @return number of removed entries
   */
  public int resetAll(String boardId) {
    ServerScoreboard scoreboard = server.getScoreboard();
    Objective objective = objective(boardId);
    var entries = java.util.List.copyOf(scoreboard.listPlayerScores(objective));
    entries.forEach(entry -> scoreboard.resetSinglePlayerScore(
        ScoreHolder.forNameOnly(entry.owner()), objective
    ));
    return entries.size();
  }

  private Objective objective(String boardId) {
    StarryListBoardDefinition board = registry.get().get(boardId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown board: " + boardId));
    Objective objective = server.getScoreboard().getObjective(board.objectiveName());
    if (objective == null) throw new IllegalStateException("Board objective is not active: " + boardId);
    return objective;
  }

  private static ScoreHolder holder(UUID playerId) {
    return ScoreHolder.forNameOnly(playerId.toString());
  }
}
