package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts non-player living entities killed by a player. */
public final class MobKillsBoard extends StarryListBoard {

  @Override public String id() { return "mob_kills"; }
  @Override public String objectiveName() { return "sl_mob_kills"; }
  @Override public int order() { return 2; }
  @Override public ItemStack icon() { return Items.IRON_SWORD.getDefaultInstance(); }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, victim, source) -> {
      var credited = source.getEntity() == null ? killer : source.getEntity();
      if (credited instanceof ServerPlayer player && !(victim instanceof ServerPlayer)) {
        registrar.addAutomatic(player, 1);
      }
    });
  }
}
