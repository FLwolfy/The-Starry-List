package com.flwolfy.starrylist.board.base;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime-safe services bound to one discovered board. */
public final class StarryListBoardRegistrar {

  private final StarryListBoard board;

  StarryListBoardRegistrar(StarryListBoard board) {
    this.board = board;
  }

  /**
   * Adds an automatically collected statistic while respecting blacklist and overflow rules.
   *
   * @param player the player whose score changes
   * @param delta the amount to add
   * @return the resulting score, or zero when no runtime is active
   */
  public int addAutomatic(ServerPlayer player, int delta) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime == null ? 0 : runtime.scores().addAutomatic(board.id(), player, delta);
  }

  /**
   * Checks whether a player is excluded from automatic board processing.
   *
   * @param player the player to check
   * @return whether the player is blacklisted
   */
  public boolean isBlacklisted(ServerPlayer player) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime != null && runtime.scores().isBlacklisted(player);
  }

  /**
   * Returns this board's persistent state namespace for a player.
   *
   * @param player the owning player
   * @return the board-specific state
   */
  public StarryListBoardState state(ServerPlayer player) {
    return state(player.getUUID());
  }

  /**
   * Returns this board's persistent state namespace for a player UUID.
   *
   * @param playerId the owning player UUID
   * @return the board-specific state
   */
  public StarryListBoardState state(UUID playerId) {
    return runtime().state().boardState(board.id(), playerId);
  }

  /**
   * Resolves the active world-bound runtime.
   *
   * @return the active runtime
   * @throws IllegalStateException if no server runtime is active
   */
  public StarryListRuntime runtime() {
    return requireRuntime();
  }

  /**
   * Resolves the active server without retaining it during mod initialization.
   *
   * @return the active server
   * @throws IllegalStateException if no server runtime is active
   */
  public MinecraftServer server() {
    return runtime().server();
  }

  /**
   * Accumulates positive sub-units in persistent board state and returns completed whole units.
   *
   * @param player the player whose state changes
   * @param key the board-state key that stores the remainder
   * @param amount the number of sub-units to add
   * @param unitsPerWhole the number of sub-units in one whole unit
   * @return the number of completed whole units
   */
  public int accumulate(ServerPlayer player, String key, int amount, int unitsPerWhole) {
    if (amount <= 0 || unitsPerWhole <= 0 || isBlacklisted(player)) {
      return 0;
    }

    return state(player).accumulate(key, amount, unitsPerWhole);
  }

  private static StarryListRuntime requireRuntime() {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    if (runtime == null) {
      throw new IllegalStateException("StarryList runtime is not active");
    }

    return runtime;
  }
}
