package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardCollector;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts other players killed by a player. */
public final class PlayerKillsBoard extends StarryListBoard implements StarryListBoardCollector {

  @Override
  public String id() {
    return "player_kills";
  }

  @Override
  public String objectiveName() {
    return "sl_player_kills";
  }

  @Override
  public int order() {
    return 3;
  }

  @Override
  public ItemStack icon() {
    return Items.DIAMOND_SWORD.getDefaultInstance();
  }

  @Override
  public OptionalInt recalculate(ServerPlayer player, StarryListBoardState state) {
    return OptionalInt.of(Math.max(
        0, player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS))
    ));
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    registrar.listen(
        "player_kill",
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY,
        (level, killer, victim, source) -> {
          var credited = source.getEntity() == null ? killer : source.getEntity();
          if (credited instanceof ServerPlayer player && victim instanceof ServerPlayer) {
            registrar.addAutomatic(player, 1);
          }
        }
    );
  }
}
