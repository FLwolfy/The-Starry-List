import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import com.flwolfy.starrylist.data.state.StarryListBoardState
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

import java.util.OptionalInt

final class OreMiningBoard extends StarryListScriptBoard {
  @Override
  String id() {
    return "ore_mining"
  }

  @Override
  String objectiveName() {
    return "sl_ore"
  }

  @Override
  int order() {
    return 100
  }

  @Override
  ItemStack icon() {
    return Items.RAW_IRON.defaultInstance
  }

  @Override
  OptionalInt recalculate(ServerPlayer player, StarryListBoardState state) {
    long total = 0
    BuiltInRegistries.BLOCK.each { block ->
      if (block.defaultBlockState().is(ConventionalBlockTags.ORES)) {
        total += Math.max(0, player.stats.getValue(Stats.BLOCK_MINED.get(block)))
      }
    }
    return OptionalInt.of((int) Math.min(Integer.MAX_VALUE, total))
  }

  @Override
  Map translations() {
    return translatableText(
      "starrylist.script.ore_mining.title",
      "starrylist.script.ore_mining.lore.0"
    )
  }

  @Override
  void subscribe(StarryListScriptRegistrar registrar) {
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER) {
      Level level,
      Player player,
      BlockPos position,
      BlockState blockState,
      BlockEntity blockEntity ->
      // Fabric declares Player here even though this event normally runs on the server.
      if (player instanceof ServerPlayer
          && blockState.is(ConventionalBlockTags.ORES)) {
        registrar.addAutomatic((ServerPlayer) player, 1)
      }
    }
  }
}
