package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts successful server-side block breaks. */
public final class MiningBoard extends StarryListBoard {

  @Override
  public String id() {
    return "mining";
  }

  @Override
  public String objectiveName() {
    return "sl_mining";
  }

  @Override
  public int order() {
    return 0;
  }

  @Override
  public ItemStack icon() {
    return Items.DIAMOND_PICKAXE.getDefaultInstance();
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    PlayerBlockBreakEvents.AFTER.register((level, player, position, state, blockEntity) -> {
      if (player instanceof ServerPlayer serverPlayer) {
        registrar.addAutomatic(serverPlayer, 1);
      }
    });
  }
}
