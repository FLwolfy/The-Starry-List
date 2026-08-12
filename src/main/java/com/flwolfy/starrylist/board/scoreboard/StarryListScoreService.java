package com.flwolfy.starrylist.board.scoreboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.config.StarryListBlacklist;
import com.flwolfy.starrylist.data.state.StarryListState;
import java.util.List;
import java.util.Map;
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
  private final StarryListState state;
  private final StarryListBlacklist blacklist;

  /**
   * Creates a score service for the active server and registry.
   *
   * @param server active Minecraft server
   * @param registry current discovered leaderboard registry supplier
   * @param state world data used to persist hidden blacklist scores
   * @param blacklist active compiled player-name matcher
   */
  public StarryListScoreService(
      MinecraftServer server,
      java.util.function.Supplier<StarryListBoardRegistry> registry,
      StarryListState state,
      StarryListBlacklist blacklist
  ) {
    this.server = server;
    this.registry = registry;
    this.state = state;
    this.blacklist = blacklist;
  }

  /**
   * Reads a player's score, returning zero when no entry exists.
   *
   * @param boardId board identifier
   * @param playerId player UUID
   * @return current score
   */
  public int get(String boardId, UUID playerId) {
    Integer archived = state.archivedScore(playerId, boardId);
    if (archived != null) {
      return archived;
    }

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
    if (blacklist.matches(displayName)) {
      state.archiveScore(playerId, displayName, boardId, value);
      server.getScoreboard().resetSinglePlayerScore(holder(playerId), objective(boardId));
      return value;
    }

    state.removeArchivedScore(playerId, boardId);
    return setVisible(boardId, playerId, displayName, value);
  }

  private int setVisible(String boardId, UUID playerId, String displayName, int value) {
    ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(
        holder(playerId), objective(boardId)
    );
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
   * Adds an automatically collected statistic unless the player is blacklisted.
   *
   * @param boardId the board identifier
   * @param player the player to update
   * @param delta the signed score delta
   * @return the resulting score
   */
  public int addAutomatic(String boardId, ServerPlayer player, int delta) {
    if (blacklist.matches(player.getGameProfile().name())) {
      return get(boardId, player.getUUID());
    }

    int current = get(boardId, player.getUUID());
    long candidate = (long) current + delta;
    int safeValue = candidate > Integer.MAX_VALUE ? Integer.MAX_VALUE
        : candidate < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) candidate;
    return set(boardId, player.getUUID(), player.getGameProfile().name(), safeValue);
  }

  /**
   * Replaces an automatically collected statistic unless the player is blacklisted.
   *
   * <p>Unlike the administrator-facing {@link #set(String, ServerPlayer, int)}, this method does
   * not update either the visible or archived score of an excluded player.</p>
   *
   * @param boardId the board identifier
   * @param player the player to update
   * @param value the replacement score
   * @return the stored score, or the unchanged current score when the player is blacklisted
   */
  public int setAutomatic(String boardId, ServerPlayer player, int value) {
    if (blacklist.matches(player.getGameProfile().name())) {
      return get(boardId, player.getUUID());
    }

    return set(boardId, player.getUUID(), player.getGameProfile().name(), value);
  }

  /**
   * Accumulates automatic sub-units unless the player is blacklisted.
   *
   * @param boardId the board identifier
   * @param player the player whose state changes
   * @param key the remainder state key
   * @param amount the sub-units to add
   * @param unitsPerWhole the sub-units in one completed unit
   * @return completed whole units, or zero when the player is blacklisted
   */
  public int accumulateAutomatic(
      String boardId,
      ServerPlayer player,
      String key,
      int amount,
      int unitsPerWhole
  ) {
    if (blacklist.matches(player.getGameProfile().name())) {
      return 0;
    }

    return state.boardState(boardId, player.getUUID()).accumulate(key, amount, unitsPerWhole);
  }

  /**
   * Refreshes the vanilla display component attached to every existing StarryList score.
   *
   * @param player online player whose name should be refreshed
   */
  public void remember(ServerPlayer player) {
    reconcilePlayer(player);

    for (var board : registry.get().all()) {
      Objective objective = server.getScoreboard().getObjective(board.objectiveName());
      if (objective == null) {
        continue;
      }
      if (server.getScoreboard().getPlayerScoreInfo(holder(player.getUUID()), objective) == null) {
        continue;
      }

      ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(
          holder(player.getUUID()), objective
      );
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
    state.removeArchivedScore(playerId, boardId);
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
    return entries.size() + state.clearArchivedBoard(boardId);
  }

  /** Reconciles every visible and archived score against the active blacklist. */
  public void reconcileBlacklist() {
    ServerScoreboard scoreboard = server.getScoreboard();
    for (var board : registry.get().all()) {
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) {
        continue;
      }

      for (var entry : List.copyOf(scoreboard.listPlayerScores(objective))) {
        UUID playerId = parseUuid(entry.owner());
        if (playerId == null) {
          continue;
        }

        String playerName = currentName(
            playerId, entry.display() == null ? entry.owner() : entry.display().getString()
        );
        if (!blacklist.matches(playerName)) {
          continue;
        }

        state.archiveScore(playerId, playerName, board.id(), entry.value());
        scoreboard.resetSinglePlayerScore(holder(playerId), objective);
      }
    }

    for (Map.Entry<UUID, StarryListState.ArchivedScores> entry
        : state.archivedPlayers().entrySet()) {
      UUID playerId = entry.getKey();
      String playerName = currentName(playerId, entry.getValue().playerName());
      if (blacklist.matches(playerName)) {
        entry.getValue().scores().forEach(
            (boardId, value) -> state.archiveScore(playerId, playerName, boardId, value)
        );
        continue;
      }

      for (Map.Entry<String, Integer> score : entry.getValue().scores().entrySet()) {
        if (registry.get().get(score.getKey()).isEmpty()) {
          continue;
        }

        setVisible(score.getKey(), playerId, playerName, score.getValue());
        state.removeArchivedScore(playerId, score.getKey());
      }
    }
  }

  private void reconcilePlayer(ServerPlayer player) {
    UUID playerId = player.getUUID();
    String playerName = player.getGameProfile().name();
    if (blacklist.matches(playerName)) {
      for (var board : registry.get().all()) {
        Objective objective = objective(board.id());
        ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(
            holder(playerId), objective
        );
        if (score != null) {
          state.archiveScore(playerId, playerName, board.id(), score.value());
          server.getScoreboard().resetSinglePlayerScore(holder(playerId), objective);
        } else {
          Integer archived = state.archivedScore(playerId, board.id());
          if (archived != null) {
            state.archiveScore(playerId, playerName, board.id(), archived);
          }
        }
      }

      return;
    }

    StarryListState.ArchivedScores archived = state.archivedPlayers().get(playerId);
    if (archived == null) {
      return;
    }

    for (Map.Entry<String, Integer> score : archived.scores().entrySet()) {
      if (registry.get().get(score.getKey()).isEmpty()) {
        continue;
      }

      setVisible(score.getKey(), playerId, playerName, score.getValue());
      state.removeArchivedScore(playerId, score.getKey());
    }
  }

  private String currentName(UUID playerId, String fallback) {
    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
    return online == null ? fallback : online.getGameProfile().name();
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private Objective objective(String boardId) {
    StarryListBoard board = registry.get().get(boardId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown board: " + boardId));
    Objective objective = server.getScoreboard().getObjective(board.objectiveName());
    if (objective == null) {
      throw new IllegalStateException("Board objective is not active: " + boardId);
    }

    return objective;
  }

  private static ScoreHolder holder(UUID playerId) {
    return ScoreHolder.forNameOnly(playerId.toString());
  }
}
