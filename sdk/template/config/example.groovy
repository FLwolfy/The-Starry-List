import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import com.flwolfy.starrylist.data.state.StarryListBoardState
import com.flwolfy.starrylist.display.StarryListSidebarManager
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

import java.util.UUID

/**
 * Complete SDK example: players collect starlight by exploring the world.
 *
 * Scoring rules:
 * - Mine an ore block:                     5 points
 * - Mine 32 non-ore blocks:                1 point
 * - Use a spyglass after the cooldown:     1 point
 * - Stargaze while this board is visible:  3 points instead
 *
 * This file demonstrates every public StarryListScriptRegistrar API. A real board may use only
 * the methods it needs. Automatic score methods handle the player blacklist internally.
 */
final class StarlightExpeditionBoard extends StarryListScriptBoard {
  private static final String POINTS_KEY = "points"
  private static final String BLOCK_REMAINDER_KEY = "ordinary_block_remainder"

  private static final int ORE_POINTS = 5
  private static final int BLOCKS_PER_SURVEY_POINT = 32
  private static final int STARGAZING_POINTS = 1
  private static final int FOCUSED_STARGAZING_POINTS = 3
  private static final long STARGAZING_COOLDOWN_TICKS = 1200L

  /**
   * Temporary runtime-only data. Persistent values belong in registrar.state(...), not this map.
   * The lifecycle cleanup below clears this cache whenever the board is replaced or deactivated.
   */
  private final Map<UUID, Long> lastStargazingTick = [:]

  // Board metadata ------------------------------------------------------------------------------

  @Override
  String id() {
    return "starlight_expedition"
  }

  @Override
  String objectiveName() {
    return "sl_starlight"
  }

  @Override
  int order() {
    return 1000
  }

  @Override
  ItemStack icon() {
    return Items.NETHER_STAR.defaultInstance
  }

  @Override
  Map translations() {
    return translatableText(
      "starrylist.script.starlight_expedition.title",
      "starrylist.script.starlight_expedition.lore.0",
      "starrylist.script.starlight_expedition.lore.1"
    )
  }

  // Registration entry point -------------------------------------------------------------------

  @Override
  void subscribe(StarryListScriptRegistrar registrar) {
    registerLifecycle(registrar)
    registerBlockBreakListener(registrar)
    registerSpyglassListener(registrar)
  }

  // Lifecycle ----------------------------------------------------------------------------------

  private void registerLifecycle(StarryListScriptRegistrar registrar) {
    registrar.onActiveStateChanged(
        {
          // runtime(): advanced access to StarryList services. This verifies that this board is
          // present in the active registry before restoring online players' persistent scores.
          registrar.runtime().registry().get(id()).orElseThrow()

          // server(): direct access to MinecraftServer. Only online players need synchronization.
          registrar.server().playerList.players.each { ServerPlayer player ->
            // state(UUID): persistent storage when only a player's identity is available.
            int savedPoints = registrar.state(player.UUID).getInt(POINTS_KEY, 0)

            // setAutomatic(): replace an absolute score, with blacklist handling built in.
            registrar.setAutomatic(player, savedPoints)
          }
        },
        {
          // onActiveStateChanged(): release resources owned by this script. Registrations created
          // with registrar.listen(...) are managed automatically and need no manual cleanup.
          lastStargazingTick.clear()
        }
    )
  }

  // Void-returning event -----------------------------------------------------------------------

  private void registerBlockBreakListener(StarryListScriptRegistrar registrar) {
    // PlayerBlockBreakEvents.After declares these exact five parameter types. Keep them explicit
    // so the IDE can complete Level, Player, BlockPos, BlockState, and BlockEntity members.
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER) {
      Level level,
      Player player,
      BlockPos position,
      BlockState blockState,
      BlockEntity blockEntity ->
      if (!(player instanceof ServerPlayer)) {
        return
      }

      ServerPlayer serverPlayer = (ServerPlayer) player
      if (blockState.is(ConventionalBlockTags.ORES)) {
        award(registrar, serverPlayer, ORE_POINTS)
        return
      }

      // accumulate(): retain partial progress. Every 32 ordinary blocks complete one point.
      int completedPoints = registrar.accumulate(
          serverPlayer,
          BLOCK_REMAINDER_KEY,
          1,
          BLOCKS_PER_SURVEY_POINT
      )
      if (completedPoints > 0) {
        award(registrar, serverPlayer, completedPoints)
      }
    }
  }

  // Value-returning event ----------------------------------------------------------------------

  private void registerSpyglassListener(StarryListScriptRegistrar registrar) {
    // UseItemCallback returns InteractionResult, so listen(...) also needs an inactive fallback.
    // PASS observes the interaction without preventing the spyglass's normal behavior.
    registrar.listen("use_spyglass", UseItemCallback.EVENT, InteractionResult.PASS) {
      Player player,
      Level level,
      InteractionHand hand ->
      if (!(player instanceof ServerPlayer)
          || !player.getItemInHand(hand).is(Items.SPYGLASS)) {
        return InteractionResult.PASS
      }

      ServerPlayer serverPlayer = (ServerPlayer) player
      long currentTick = level.gameTime
      long previousTick = lastStargazingTick.getOrDefault(
          serverPlayer.UUID,
          Long.MIN_VALUE / 2
      )

      if (currentTick - previousTick < STARGAZING_COOLDOWN_TICKS) {
        return InteractionResult.PASS
      }

      // display(): all effective sidebar preferences for this player.
      StarryListSidebarManager.EffectiveDisplay display = registrar.display(serverPlayer)

      // isEnabled(): convenient check for whether this board is in that effective board list.
      boolean focusedOnThisBoard = !display.hidden() && registrar.isEnabled(serverPlayer)
      int points = focusedOnThisBoard ? FOCUSED_STARGAZING_POINTS : STARGAZING_POINTS

      StarryListBoardState playerState = registrar.state(serverPlayer)
      int pointsBefore = playerState.getInt(POINTS_KEY, 0)
      int pointsAfter = award(registrar, serverPlayer, points)

      if (pointsAfter != pointsBefore) {
        // Start the cooldown only after a score actually changes. Blacklisted players therefore
        // produce no persistent-state or transient-cache changes.
        lastStargazingTick[serverPlayer.UUID] = currentTick
      }

      return InteractionResult.PASS
    }
  }

  // Scoring helper -----------------------------------------------------------------------------

  private static int award(
      StarryListScriptRegistrar registrar,
      ServerPlayer player,
      int amount
  ) {
    StarryListBoardState playerState = registrar.state(player)
    int previousPoints = playerState.getInt(POINTS_KEY, 0)

    // addAutomatic(): add a delta with integer saturation and internal blacklist handling.
    int updatedPoints = registrar.addAutomatic(player, amount)

    // state(ServerPlayer): board-private persistent data for this player. Mirror only an accepted
    // score change, so an automatic write rejected by the blacklist cannot alter script state.
    if (updatedPoints != previousPoints) {
      playerState.putInt(POINTS_KEY, updatedPoints)
    }

    return updatedPoints
  }
}
