package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts whole blocks from vanilla-confirmed continuous movement statistics. */
public final class TravelDistanceBoard extends StarryListBoard {

  private static final String REMAINDER_KEY = "centimeterRemainder";
  private static final List<Identifier> MOVEMENT_STATS = List.of(
      Stats.WALK_ONE_CM,
      Stats.CROUCH_ONE_CM,
      Stats.SPRINT_ONE_CM,
      Stats.WALK_ON_WATER_ONE_CM,
      Stats.WALK_UNDER_WATER_ONE_CM,
      Stats.SWIM_ONE_CM,
      Stats.CLIMB_ONE_CM,
      Stats.FALL_ONE_CM,
      Stats.FLY_ONE_CM,
      Stats.AVIATE_ONE_CM,
      Stats.MINECART_ONE_CM,
      Stats.BOAT_ONE_CM,
      Stats.PIG_ONE_CM,
      Stats.HORSE_ONE_CM,
      Stats.STRIDER_ONE_CM,
      Stats.HAPPY_GHAST_ONE_CM,
      Stats.NAUTILUS_ONE_CM
  );
  private final Map<UUID, Map<Identifier, Integer>> baselines = new HashMap<>();

  @Override
  public String id() {
    return "travel_distance";
  }

  @Override
  public String objectiveName() {
    return "sl_travel";
  }

  @Override
  public int order() {
    return 5;
  }

  @Override
  public ItemStack icon() {
    return Items.COMPASS.getDefaultInstance();
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    registrar.onActiveStateChanged(
        () -> registrar.server().getPlayerList().getPlayers().forEach(this::initialize),
        baselines::clear
    );
    registrar.listen("player_join", ServerPlayerEvents.JOIN, this::initialize);
    registrar.listen(
        "player_leave",
        ServerPlayerEvents.LEAVE,
        player -> baselines.remove(player.getUUID())
    );

    registrar.listen(
        "player_respawn",
        ServerPlayerEvents.AFTER_RESPAWN,
        (oldPlayer, newPlayer, alive) -> initialize(newPlayer)
    );

    registrar.listen(
        "server_tick",
        ServerTickEvents.END_SERVER_TICK,
        server -> server.getPlayerList().getPlayers().forEach(player -> sample(player, registrar))
    );
  }

  private void initialize(ServerPlayer player) {
    Map<Identifier, Integer> values = new HashMap<>();
    for (Identifier statistic : MOVEMENT_STATS) {
      values.put(statistic, value(player, statistic));
    }

    baselines.put(player.getUUID(), values);
  }

  private void sample(ServerPlayer player, StarryListBoardRegistrar registrar) {
    Map<Identifier, Integer> previous = baselines.get(player.getUUID());
    if (previous == null) {
      initialize(player);
      return;
    }

    for (Identifier statistic : MOVEMENT_STATS) {
      int current = value(player, statistic);
      int earlier = previous.put(statistic, current);
      if (current <= earlier) {
        continue;
      }

      int blocks = registrar.accumulate(player, REMAINDER_KEY, current - earlier, 100);
      if (blocks > 0) {
        registrar.addAutomatic(player, blocks);
      }
    }
  }

  private static int value(ServerPlayer player, Identifier statistic) {
    return player.getStats().getValue(Stats.CUSTOM.get(statistic));
  }
}
