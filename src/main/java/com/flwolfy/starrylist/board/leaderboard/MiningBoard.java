package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardCollector;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts successful server-side block breaks. */
public final class MiningBoard extends StarryListBoard implements StarryListBoardCollector {

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
  public OptionalInt recalculate(ServerPlayer player, StarryListBoardState state) {
    long total = 0;
    for (var block : BuiltInRegistries.BLOCK) {
      total += Math.max(0, player.getStats().getValue(Stats.BLOCK_MINED.get(block)));
    }
    return OptionalInt.of((int) Math.min(Integer.MAX_VALUE, total));
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER,
        (level, player, position, state, blockEntity) -> {
      if (player instanceof ServerPlayer serverPlayer) {
        registrar.addAutomatic(serverPlayer, 1);
      }
    });
  }
}
