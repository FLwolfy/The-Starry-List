import com.flwolfy.starrylist.board.base.StarryListBoardPresentation
import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

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
  Map<String, StarryListBoardPresentation> translations() {
    return [
      en_us: text("Ores Mined", "Counts blocks in the conventional ores tag."),
      zh_cn: text("矿石挖掘榜", "统计玩家挖掘任意通用矿石标签方块的数量。")
    ]
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
