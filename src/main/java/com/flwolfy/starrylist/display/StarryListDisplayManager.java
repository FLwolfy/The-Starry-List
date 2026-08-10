package com.flwolfy.starrylist.display;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.state.StarryListDisplayProfile;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

/** Computes effective per-player display profiles and sends vanilla sidebar packets on change. */
public final class StarryListDisplayManager {

  private final MinecraftServer server;
  private final StarryListState state;
  private final java.util.function.Supplier<StarryListConfigData> config;
  private final java.util.function.Supplier<StarryListBoardRegistry> registry;
  private final Map<UUID, String> displayedObjectives = new HashMap<>();
  private long ticks;

  public StarryListDisplayManager(
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

  public void tick() {
    ticks++;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) update(player, false);
  }

  public void update(ServerPlayer player, boolean force) {
    EffectiveDisplay effective = effective(player.getUUID());
    Objective objective = null;
    if (!effective.hidden() && !effective.boards().isEmpty()) {
      int index = effective.rotationEnabled() && effective.boards().size() > 1
          ? (int) ((ticks / (effective.intervalSeconds() * 20L)) % effective.boards().size())
          : 0;
      String boardId = effective.boards().get(index);
      objective = registry.get().get(boardId)
          .map(board -> server.getScoreboard().getObjective(board.objectiveName()))
          .orElse(null);
    }
    String nextName = objective == null ? "" : objective.getName();
    String previous = displayedObjectives.get(player.getUUID());
    if (force || !nextName.equals(previous)) {
      player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
      displayedObjectives.put(player.getUUID(), nextName);
    }
  }

  public void remove(ServerPlayer player) {
    displayedObjectives.remove(player.getUUID());
  }

  public void updateAll(boolean force) {
    server.getPlayerList().getPlayers().forEach(player -> update(player, force));
  }

  public EffectiveDisplay effective(UUID playerId) {
    StarryListConfigData.Display defaults = config.get().display();
    StarryListDisplayProfile profile = state.profile(playerId);
    if (profile.mode() == StarryListDisplayProfile.Mode.HIDDEN) {
      return new EffectiveDisplay(true, List.of(), false, defaults.rotationIntervalSeconds());
    }
    List<String> requested = profile.mode() == StarryListDisplayProfile.Mode.DEFAULT
        ? defaults.defaultBoards() : profile.boards();
    List<String> available = StarryListConfigData.normalizeIds(requested).stream()
        .filter(id -> registry.get().get(id).map(board -> board.enabled()).orElse(false))
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

  public StarryListDisplayProfile editableProfile(UUID playerId) {
    StarryListDisplayProfile current = state.profile(playerId);
    if (current.mode() == StarryListDisplayProfile.Mode.CUSTOM) return current;
    EffectiveDisplay effective = effective(playerId);
    List<String> boards = effective.boards().isEmpty()
        ? StarryListConfigData.normalizeIds(config.get().display().defaultBoards())
        : effective.boards();
    return new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        boards,
        config.get().display().rotationEnabled(),
        config.get().display().rotationIntervalSeconds()
    );
  }

  public record EffectiveDisplay(
      boolean hidden,
      List<String> boards,
      boolean rotationEnabled,
      int intervalSeconds
  ) {}
}
