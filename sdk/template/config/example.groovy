import com.flwolfy.starrylist.board.base.StarryListBoardPresentation
import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import java.util.UUID

/**
 * A deliberately feature-rich example: explorers collect stardust by mining and stargazing.
 *
 * The persistent "points" value mirrors successful automatic score writes. The visible leaderboard
 * can be synchronized from it while every public StarryListScriptRegistrar API is shown in context.
 */
final class StarlightExpeditionBoard extends StarryListScriptBoard {
  private static final String POINTS_KEY = "points"
  private static final String BLOCK_REMAINDER_KEY = "ordinary_block_remainder"
  private static final long STARGAZING_COOLDOWN = 1200L

  // This cache is intentionally transient. The lifecycle callback clears it on reload/disable.
  private final Map<UUID, Long> lastStargazingTick = [:]

  @Override
  String id() {
    "starlight_expedition"
  }

  @Override
  String objectiveName() {
    "sl_starlight"
  }

  @Override
  int order() {
    1000
  }

  @Override
  ItemStack icon() {
    Items.NETHER_STAR.defaultInstance
  }

  @Override
  Map<String, StarryListBoardPresentation> translations() {
    [
      en_us: text(
          "Starlight Expedition",
          "Mine ores, survey blocks, and observe the sky with a spyglass.",
          "Automatic scoring handles blacklisted players internally."
      ),
      zh_cn: text(
          "星光远征榜",
          "挖掘矿石、勘探方块，并用望远镜观测天空来收集星尘。",
          "自动计分会在内部处理黑名单玩家。"
      )
    ]
  }

  @Override
  void subscribe(StarryListScriptRegistrar registrar) {
    // onActiveStateChanged(): initialize runtime resources and clean transient resources later.
    registrar.onActiveStateChanged(
        {
          // runtime() exposes all advanced StarryList world services when they are really needed.
          registrar.runtime().registry().get(id())

          // server() is the convenient direct route to MinecraftServer.
          registrar.server().playerList.players.each { ServerPlayer player ->
            // state(UUID) is useful when code has an identity rather than a player object.
            int savedPoints = registrar.state(player.UUID).getInt(POINTS_KEY, 0)

            // setAutomatic() synchronizes an absolute value and handles blacklist internally.
            registrar.setAutomatic(player, savedPoints)
          }
        },
        {
          lastStargazingTick.clear()
        }
    )

    // Three-argument listen(): use this form for a void-returning Fabric event.
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER) {
      level, player, position, blockState, blockEntity ->
      if (!(player instanceof ServerPlayer)) {
        return
      }

      if (blockState.is(ConventionalBlockTags.ORES)) {
        award(registrar, player, 5)
        return
      }

      // accumulate(): persist partial units; every 32 ordinary blocks become one point.
      int completedSurveys = registrar.accumulate(
          player, BLOCK_REMAINDER_KEY, 1, 32
      )
      if (completedSurveys > 0) {
        award(registrar, player, completedSurveys)
      }
    }

    // Four-argument listen(): value-returning events require an inactive fallback value.
    registrar.listen("use_spyglass", UseItemCallback.EVENT, InteractionResult.PASS) {
      player, level, hand ->
      if (!(player instanceof ServerPlayer)
          || !player.getItemInHand(hand).is(Items.SPYGLASS)) {
        return InteractionResult.PASS
      }

      long now = level.gameTime
      long previous = lastStargazingTick.getOrDefault(player.UUID, Long.MIN_VALUE / 2)
      if (now - previous >= STARGAZING_COOLDOWN) {
        // display() reads all effective display preferences. isEnabled() is the board shortcut.
        def preferences = registrar.display(player)
        int focusBonus = preferences.visible() && registrar.isEnabled(player) ? 3 : 1
        int oldPoints = registrar.state(player).getInt(POINTS_KEY, 0)
        int newPoints = award(registrar, player, focusBonus)
        if (newPoints != oldPoints) {
          // Only successful automatic scoring starts the transient cooldown. A blacklisted player
          // therefore produces no score, persistent-state, or cache changes in this example.
          lastStargazingTick[player.UUID] = now
        }
      }

      // PASS observes the interaction without preventing normal spyglass behavior.
      InteractionResult.PASS
    }
  }

  private static int award(
      StarryListScriptRegistrar registrar,
      ServerPlayer player,
      int amount
  ) {
    // addAutomatic() handles saturation and blacklist internally. For an excluded player it returns
    // the unchanged hidden score, so the mirror below cannot advance accidentally.
    int newPoints = registrar.addAutomatic(player, amount)

    // state(ServerPlayer) is persistent and isolated by this board ID and player UUID. Direct state
    // storage is generic, so mirror the result already approved by the automatic scoring layer.
    def playerState = registrar.state(player)
    if (playerState.getInt(POINTS_KEY, 0) != newPoints) {
      playerState.putInt(POINTS_KEY, newPoints)
    }
    newPoints
  }
}
