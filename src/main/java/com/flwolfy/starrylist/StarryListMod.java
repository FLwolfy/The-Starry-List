package com.flwolfy.starrylist;

import com.flwolfy.starrylist.command.StarryListAdminCommand;
import com.flwolfy.starrylist.command.StarryListCommand;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.script.context.StarryListBlock;
import com.flwolfy.starrylist.data.script.context.StarryListContext;
import com.flwolfy.starrylist.data.script.context.StarryListEntity;
import com.flwolfy.starrylist.data.script.context.StarryListItem;
import com.flwolfy.starrylist.data.script.context.StarryListPlayer;
import com.flwolfy.starrylist.data.script.context.StarryListPosition;
import com.flwolfy.starrylist.event.StarryListEventType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The common StarryList entry point. All gameplay features remain server-side. */
public final class StarryListMod implements ModInitializer {

  public static final String MOD_ID = "the-starry-list";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static volatile StarryListRuntime runtime;

  @Override
  public void onInitialize() {
    StarryListConfigManager config = StarryListConfigManager.getInstance();

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      StarryListCommand.register(dispatcher);
      StarryListAdminCommand.register(dispatcher);
    });
    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      runtime = new StarryListRuntime(server);
      config.setApplyListener(replacement -> runtime.applyConfig(replacement));
      LOGGER.info("The-Starry-List is ready");
    });
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
      StarryListRuntime active = runtime;
      runtime = null;
      config.setApplyListener(null);
      if (active != null) active.shutdown();
    });
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      StarryListRuntime active = runtime;
      if (active != null) active.tick();
    });

    PlayerBlockBreakEvents.AFTER.register((level, player, position, state, blockEntity) -> {
      if (player instanceof ServerPlayer serverPlayer) {
        dispatchBlockBreak(serverPlayer, level, position, state);
      }
    });
    ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, victim, source) -> {
      Entity credited = source.getEntity() == null ? killer : source.getEntity();
      if (credited instanceof ServerPlayer player) dispatchKill(player, victim, source);
    });
    ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
      if (entity instanceof ServerPlayer player) dispatchDeath(player, source);
    });
    ServerPlayerEvents.JOIN.register(StarryListMod::onJoin);
    ServerPlayerEvents.LEAVE.register(StarryListMod::onLeave);
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      StarryListRuntime active = runtime;
      if (active != null) active.display().update(newPlayer, true);
    });
  }

  /** Called by the placement mixin after BlockItem reports a successful server-side placement. */
  public static void onBlockPlaced(
      ServerPlayer player,
      Level level,
      BlockPos position,
      BlockState state,
      ItemStack item
  ) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.events().dispatch(
        StarryListEventType.BLOCK_PLACE,
        player,
        StarryListContext.blockPlace(new StarryListContext.BlockPlace(
            StarryListBlock.from(state),
            StarryListPosition.from(level, position),
            StarryListItem.from(item)
        )),
        "placing",
        1
    );
  }

  /** Called by the stat mixin for a confirmed vanilla continuous-movement increment. */
  public static void onTravel(ServerPlayer player, String movementType, int centimeters) {
    StarryListRuntime active = runtime;
    if (active == null || centimeters <= 0) return;
    int wholeBlocks = active.state().addTravel(player.getUUID(), centimeters);
    active.events().dispatch(
        StarryListEventType.TRAVEL,
        player,
        StarryListContext.travel(new StarryListContext.Travel(
            centimeters,
            centimeters / 100.0,
            movementType,
            StarryListPosition.from(player)
        )),
        "travel_distance",
        wholeBlocks
    );
  }

  public static StarryListRuntime runtime() {
    return runtime;
  }

  private static void dispatchBlockBreak(
      ServerPlayer player,
      Level level,
      BlockPos position,
      BlockState state
  ) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.events().dispatch(
        StarryListEventType.BLOCK_BREAK,
        player,
        StarryListContext.blockBreak(new StarryListContext.BlockBreak(
            StarryListBlock.from(state),
            StarryListPosition.from(level, position),
            StarryListItem.from(player.getMainHandItem())
        )),
        "mining",
        1
    );
  }

  private static void dispatchKill(ServerPlayer player, Entity victim, DamageSource source) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    String damageType = source.typeHolder().getRegisteredName();
    if (victim instanceof ServerPlayer victimPlayer) {
      active.events().dispatch(
          StarryListEventType.PLAYER_KILL,
          player,
          StarryListContext.playerKill(new StarryListContext.PlayerKill(
              StarryListPlayer.from(victimPlayer), damageType
          )),
          "player_kills",
          1
      );
    } else {
      active.events().dispatch(
          StarryListEventType.MOB_KILL,
          player,
          StarryListContext.mobKill(new StarryListContext.MobKill(
              StarryListEntity.from(victim), damageType
          )),
          "mob_kills",
          1
      );
    }
  }

  private static void dispatchDeath(ServerPlayer player, DamageSource source) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    StarryListPlayer killer = source.getEntity() instanceof ServerPlayer serverPlayer
        ? StarryListPlayer.from(serverPlayer) : null;
    active.events().dispatch(
        StarryListEventType.PLAYER_DEATH,
        player,
        StarryListContext.playerDeath(new StarryListContext.PlayerDeath(
            killer, source.typeHolder().getRegisteredName()
        )),
        "deaths",
        1
    );
  }

  private static void onJoin(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.state().rememberPlayer(player.getUUID(), player.getGameProfile().name());
    active.scores().remember(player);
    active.events().dispatch(
        StarryListEventType.PLAYER_JOIN,
        player,
        StarryListContext.join(new StarryListContext.Presence(StarryListPosition.from(player))),
        null,
        0
    );
    active.events().refreshScheduled(player);
    active.display().update(player, true);
  }

  private static void onLeave(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.events().dispatch(
        StarryListEventType.PLAYER_LEAVE,
        player,
        StarryListContext.leave(new StarryListContext.Presence(StarryListPosition.from(player))),
        null,
        0
    );
    active.display().remove(player);
  }
}
