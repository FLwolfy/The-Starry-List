package com.flwolfy.starrylist.display;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.state.StarryListDisplayProfile;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.scoreboard.StarryListBoardIds;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

/** Computes effective per-player display profiles and sends vanilla sidebar packets on change. */
public final class StarryListSidebarManager {

  private final MinecraftServer server;
  private final StarryListState state;
  private final java.util.function.Supplier<StarryListConfigData> config;
  private final java.util.function.Supplier<StarryListBoardRegistry> registry;
  private final Map<UUID, String> displayedObjectives = new HashMap<>();
  private final Map<UUID, Long> rotationStartedAt = new HashMap<>();
  private final java.util.Set<UUID> syncedPlayers = new HashSet<>();
  private long ticks;

  /**
   * Creates a display manager for per-player vanilla sidebar packets.
   *
   * @param server active Minecraft server
   * @param state world-scoped player display state
   * @param config active configuration supplier
   * @param registry fixed leaderboard registry supplier
   */
  public StarryListSidebarManager(
      MinecraftServer server,
      StarryListState state,
      java.util.function.Supplier<StarryListConfigData> config,
      java.util.function.Supplier<StarryListBoardRegistry> registry
  ) {
    this.server = server;
    this.state = state;
    this.config = config;
    this.registry = registry;
  }

  /** Advances sidebar rotation and updates every online player. */
  public void tick() {
    ticks++;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) update(player, false);
  }

  /**
   * Recomputes and, when needed, sends one player's sidebar objective.
   *
   * @param player online player
   * @param force whether to resend even if the selected objective is unchanged
   */
  public void update(ServerPlayer player, boolean force) {
    syncObjectives(player);
    if (force) rotationStartedAt.put(player.getUUID(), ticks);
    EffectiveDisplay effective = effective(player.getUUID());
    Objective objective = null;
    if (!effective.hidden() && !effective.boards().isEmpty()) {
      int index = effective.rotationEnabled() && effective.boards().size() > 1
          ? (int) (((ticks - rotationStartedAt.computeIfAbsent(player.getUUID(), ignored -> ticks))
              / (effective.intervalSeconds() * 20L)) % effective.boards().size())
          : 0;
      String boardId = effective.boards().get(index);
      objective = registry.get().get(boardId)
          .map(board -> server.getScoreboard().getObjective(board.objectiveName()))
          .orElse(null);
    }
    String nextName = objective == null ? "" : objective.getName();
    String previous = displayedObjectives.get(player.getUUID());
    if (objective == null) {
      releaseSidebar(player, previous);
      displayedObjectives.put(player.getUUID(), "");
      return;
    }
    if (force || !nextName.equals(previous)) {
      player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
      displayedObjectives.put(player.getUUID(), nextName);
    }
  }

  /**
   * Removes cached display state for a disconnecting player.
   *
   * @param player disconnecting player
   */
  public void remove(ServerPlayer player) {
    displayedObjectives.remove(player.getUUID());
    rotationStartedAt.remove(player.getUUID());
    syncedPlayers.remove(player.getUUID());
  }

  /**
   * Recomputes sidebar state for every online player.
   *
   * @param force whether to resend unchanged objectives
   */
  public void updateAll(boolean force) {
    server.getPlayerList().getPlayers().forEach(player -> update(player, force));
  }

  /**
   * Resolves server defaults and a player's saved profile into effective display behavior.
   *
   * @param playerId player UUID
   * @return effective display settings
   */
  public EffectiveDisplay effective(UUID playerId) {
    StarryListConfigData.Display defaults = config.get().display();
    StarryListDisplayProfile profile = state.profile(playerId);
    if (profile.mode() == StarryListDisplayProfile.Mode.HIDDEN) {
      return new EffectiveDisplay(true, List.of(), false, defaults.rotationIntervalSeconds());
    }
    List<String> requested = profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
        ? defaults.enabledBoards() : profile.boards();
    List<String> available = StarryListConfigData.normalizeIds(requested).stream()
        .filter(id -> registry.get().get(id).isPresent())
        .distinct()
        .toList();
    boolean hidden = profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
        && defaults.hiddenByDefault();
    return new EffectiveDisplay(
        hidden || available.isEmpty(),
        available,
        profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
            ? defaults.rotationEnabled() : profile.rotationEnabled(),
        profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
            ? defaults.rotationIntervalSeconds() : profile.rotationIntervalSeconds()
    );
  }

  /**
   * Returns whether visibility is disabled by the hide setting rather than by an empty board list.
   *
   * @param playerId player UUID
   * @return whether the player explicitly hides the sidebar or inherits hidden-by-default
   */
  public boolean hiddenBySetting(UUID playerId) {
    StarryListDisplayProfile profile = state.profile(playerId);
    return profile.mode() == StarryListDisplayProfile.Mode.HIDDEN
        || (profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
            && config.get().display().hiddenByDefault());
  }

  /**
   * Returns a mutable-command starting profile, copying server defaults when necessary.
   *
   * @param playerId player UUID
   * @return custom profile suitable for a player display edit
   */
  public StarryListDisplayProfile editableProfile(UUID playerId) {
    StarryListDisplayProfile current = state.profile(playerId);
    if (current.mode() == StarryListDisplayProfile.Mode.CUSTOM) return current;
    if (current.mode() == StarryListDisplayProfile.Mode.HIDDEN) {
      return new StarryListDisplayProfile(
          StarryListDisplayProfile.Mode.CUSTOM,
          current.boards(),
          current.rotationEnabled(),
          current.rotationIntervalSeconds()
      );
    }
    EffectiveDisplay effective = effective(playerId);
    List<String> boards = effective.boards().isEmpty()
        ? StarryListConfigData.normalizeIds(config.get().display().enabledBoards())
        : effective.boards();
    return new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        boards,
        config.get().display().rotationEnabled(),
        config.get().display().rotationIntervalSeconds()
    );
  }

  private void syncObjectives(ServerPlayer player) {
    if (!syncedPlayers.add(player.getUUID())) return;
    net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
    for (var board : registry.get().all()) {
      Objective objective = scoreboard.getObjective(board.objectiveName());
      if (objective == null) continue;
      for (Packet<?> packet : scoreboard.getStartTrackingPackets(objective)) {
        player.connection.send(packet);
      }
    }
  }

  /**
   * Releases a sidebar only when this manager previously put a StarryList objective there.
   *
   * <p>Sending an unconditional null objective would clear a sidebar owned by another mod every
   * time a hidden profile was refreshed. When StarryList relinquishes a sidebar, restore the
   * server's normal SIDEBAR objective if it is not one of ours. Per-player packet-only sidebars
   * from other mods cannot be introspected, so avoiding packets after ownership is released is the
   * safest cooperative behavior.</p>
   */
  private void releaseSidebar(ServerPlayer player, String previous) {
    if (!StarryListBoardIds.ownsObjective(previous)) return;
    Objective fallback = server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
    if (fallback != null && StarryListBoardIds.ownsObjective(fallback.getName())) fallback = null;
    player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, fallback));
  }

  /**
   * Fully resolved sidebar settings used by the display packet scheduler.
   *
   * @param hidden whether the sidebar is hidden
   * @param boards ordered available board identifiers
   * @param rotationEnabled whether multiple boards rotate
   * @param intervalSeconds active rotation interval in seconds
   */
  public record EffectiveDisplay(
      boolean hidden,
      List<String> boards,
      boolean rotationEnabled,
      int intervalSeconds
  ) {}
}
