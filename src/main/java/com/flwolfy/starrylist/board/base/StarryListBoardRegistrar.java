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

  /** Adds an automatic statistic, respecting blacklist and integer overflow rules. */
  public int addAutomatic(ServerPlayer player, int delta) {
    StarryListRuntime runtime = StarryListMod.runtime();
    return runtime == null ? 0 : runtime.scores().addAutomatic(board.id(), player, delta);
  }

  /** Returns whether this player is excluded from automatic board processing. */
  public boolean isBlacklisted(ServerPlayer player) {
    StarryListRuntime runtime = StarryListMod.runtime();
    return runtime != null && runtime.scores().isBlacklisted(player);
  }

  /** Returns this board's persistent per-player state namespace. */
  public StarryListBoardState state(ServerPlayer player) {
    return state(player.getUUID());
  }

  /** Returns this board's persistent state namespace for an arbitrary player UUID. */
  public StarryListBoardState state(UUID playerId) {
    return runtime().state().boardState(board.id(), playerId);
  }

  /** Resolves the active world-bound runtime only when board code actually needs it. */
  public StarryListRuntime runtime() {
    return requireRuntime();
  }

  /** Resolves the currently active server without retaining it during Mod initialization. */
  public MinecraftServer server() {
    return runtime().server();
  }

  /**
   * Accumulates positive sub-units in persistent board state and returns completed whole units.
   */
  public int accumulate(ServerPlayer player, String key, int amount, int unitsPerWhole) {
    if (amount <= 0 || unitsPerWhole <= 0 || isBlacklisted(player)) return 0;
    return state(player).accumulate(key, amount, unitsPerWhole);
  }

  private static StarryListRuntime requireRuntime() {
    StarryListRuntime runtime = StarryListMod.runtime();
    if (runtime == null) throw new IllegalStateException("StarryList runtime is not active");
    return runtime;
  }
}
