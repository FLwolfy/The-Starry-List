package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardCollector;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts player deaths. */
public final class DeathsBoard extends StarryListBoard implements StarryListBoardCollector {

  @Override
  public String id() {
    return "deaths";
  }

  @Override
  public String objectiveName() {
    return "sl_deaths";
  }

  @Override
  public int order() {
    return 4;
  }

  @Override
  public ItemStack icon() {
    return Items.TOTEM_OF_UNDYING.getDefaultInstance();
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    registrar.listen("player_death", ServerLivingEntityEvents.AFTER_DEATH, (entity, source) -> {
      if (entity instanceof ServerPlayer player) {
        registrar.addAutomatic(player, 1);
      }
    });
  }
}
