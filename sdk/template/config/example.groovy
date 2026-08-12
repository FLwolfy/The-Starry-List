import com.flwolfy.starrylist.board.base.StarryListBoardPresentation
import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

final class ExampleOreMiningBoard extends StarryListScriptBoard {
  @Override
  String id() {
    "example_ore_mining"
  }

  @Override
  String objectiveName() {
    "sl_example_ore"
  }

  @Override
  int order() {
    1000
  }

  @Override
  ItemStack icon() {
    Items.RAW_IRON.defaultInstance
  }

  @Override
  Map<String, StarryListBoardPresentation> translations() {
    [
      en_us: text("Example Ores Mined", "Counts blocks in the conventional ores tag."),
      zh_cn: text("示例矿石挖掘榜", "统计玩家挖掘任意通用矿石标签方块的数量。")
    ]
  }

  @Override
  void subscribe(StarryListScriptRegistrar registrar) {
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER) {
      level, player, position, state, blockEntity ->
      if (player instanceof ServerPlayer && state.is(ConventionalBlockTags.ORES)) {
        registrar.addAutomatic(player, 1)
      }
    }
  }

  // Absolute statistics such as balances or levels can be synchronized instead of incremented.
  private static int synchronizeValue(
      StarryListScriptRegistrar registrar,
      ServerPlayer player,
      int currentValue
  ) {
    registrar.setAutomatic(player, currentValue)
  }
}
