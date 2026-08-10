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

  public StarryListScoreService(
      MinecraftServer server,
      java.util.function.Supplier<StarryListBoardRegistry> registry
  ) {
    this.server = server;
    this.registry = registry;
  }

  public int get(String boardId, UUID playerId) {
    Objective objective = objective(boardId);
    ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(holder(playerId), objective);
    return score == null ? 0 : score.value();
  }

  public int set(String boardId, ServerPlayer player, int value) {
    return set(boardId, player.getUUID(), player.getGameProfile().name(), value);
  }

  public int set(String boardId, UUID playerId, String displayName, int value) {
    ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(holder(playerId), objective(boardId));
    score.set(value);
    score.display(Component.literal(displayName));
    return value;
  }

  public int add(String boardId, ServerPlayer player, int delta) {
    return add(boardId, player.getUUID(), player.getGameProfile().name(), delta);
  }

  /** Refreshes the vanilla display component attached to every existing StarryList score. */
  public void remember(ServerPlayer player) {
    for (var board : registry.get().all()) {
      Objective objective = server.getScoreboard().getObjective(board.objectiveName());
      if (objective == null) continue;
      if (server.getScoreboard().getPlayerScoreInfo(holder(player.getUUID()), objective) == null) continue;
      ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(holder(player.getUUID()), objective);
      score.display(Component.literal(player.getGameProfile().name()));
    }
  }

  public int add(String boardId, UUID playerId, String displayName, int delta) {
    int current = get(boardId, playerId);
    int next;
    try {
      next = Math.addExact(current, delta);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Score update exceeds the 32-bit integer range", exception);
    }
    return set(boardId, playerId, displayName, next);
  }

  public void reset(String boardId, UUID playerId) {
    server.getScoreboard().resetSinglePlayerScore(holder(playerId), objective(boardId));
  }

  public int resetAll(String boardId) {
    ServerScoreboard scoreboard = server.getScoreboard();
    Objective objective = objective(boardId);
    var entries = ListCopy.copy(scoreboard.listPlayerScores(objective));
    entries.forEach(entry -> scoreboard.resetSinglePlayerScore(
        ScoreHolder.forNameOnly(entry.owner()), objective
    ));
    return entries.size();
  }

  public Objective objective(String boardId) {
    StarryListBoardDefinition board = registry.get().get(boardId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown board: " + boardId));
    Objective objective = server.getScoreboard().getObjective(board.objectiveName());
    if (objective == null) throw new IllegalStateException("Board objective is not active: " + boardId);
    return objective;
  }

  public static ScoreHolder holder(UUID playerId) {
    return ScoreHolder.forNameOnly(playerId.toString());
  }

  private static final class ListCopy {
    private static java.util.List<net.minecraft.world.scores.PlayerScoreEntry> copy(
        java.util.Collection<net.minecraft.world.scores.PlayerScoreEntry> entries
    ) {
      return java.util.List.copyOf(entries);
    }
  }
}
