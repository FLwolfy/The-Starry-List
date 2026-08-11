package com.flwolfy.starrylist;

import com.flwolfy.starrylist.command.StarryListAdminCommand;
import com.flwolfy.starrylist.command.StarryListCommand;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The common StarryList entry point. All gameplay features remain server-side. */
public final class StarryListMod implements ModInitializer {

  /** Fabric mod identifier used by metadata, resources, logging, and SavedData. */
  public static final String MOD_ID = "the-starry-list";
  /** Shared mod logger. */
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static volatile StarryListRuntime runtime;

  /** Creates the Fabric entry point. */
  public StarryListMod() {}

  /** {@inheritDoc} */
  @Override
  public void onInitialize() {
    StarryListConfigManager config = StarryListConfigManager.getInstance();

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      StarryListCommand.register(dispatcher);
      StarryListAdminCommand.register(dispatcher);
    });
    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      runtime = new StarryListRuntime(server);
      config.setApplyListener(ignored -> runtime.applyConfig());
      LOGGER.info("The-Starry-List is ready");
    });
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
      runtime = null;
      config.setApplyListener(null);
    });
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      StarryListRuntime active = runtime;
      if (active != null) active.tick();
    });

    PlayerBlockBreakEvents.AFTER.register((level, player, position, state, blockEntity) -> {
      if (player instanceof ServerPlayer serverPlayer) {
        dispatchBlockBreak(serverPlayer);
      }
    });
    ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, victim, source) -> {
      Entity credited = source.getEntity() == null ? killer : source.getEntity();
      if (credited instanceof ServerPlayer player) dispatchKill(player, victim);
    });
    ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
      if (entity instanceof ServerPlayer player) dispatchDeath(player);
    });
    ServerPlayerEvents.JOIN.register(StarryListMod::onJoin);
    ServerPlayerEvents.LEAVE.register(StarryListMod::onLeave);
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      StarryListRuntime active = runtime;
      if (active != null) active.display().update(newPlayer, true);
    });
  }

  /**
   * Increments the placing board after a successful server-side block placement.
   *
   * @param player player credited with the placement
   */
  public static void onBlockPlaced(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.scores().addAutomatic("placing", player, 1);
  }

  /**
   * Converts a confirmed vanilla continuous-movement statistic into whole leaderboard blocks.
   *
   * @param player player credited with the movement
   * @param centimeters movement increment reported by the vanilla statistic
   */
  public static void onTravel(ServerPlayer player, int centimeters) {
    StarryListRuntime active = runtime;
    if (active == null || centimeters <= 0 || active.scores().isBlacklisted(player)) return;
    int wholeBlocks = active.state().addTravel(player.getUUID(), centimeters);
    if (wholeBlocks > 0) active.scores().addAutomatic("travel_distance", player, wholeBlocks);
  }

  /**
   * Returns the services associated with the currently running server.
   *
   * @return active server runtime, or {@code null} while no server is running
   */
  public static StarryListRuntime runtime() {
    return runtime;
  }

  private static void dispatchBlockBreak(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.scores().addAutomatic("mining", player, 1);
  }

  private static void dispatchKill(ServerPlayer player, Entity victim) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    if (victim instanceof ServerPlayer) {
      active.scores().addAutomatic("player_kills", player, 1);
    } else {
      active.scores().addAutomatic("mob_kills", player, 1);
    }
  }

  private static void dispatchDeath(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.scores().addAutomatic("deaths", player, 1);
  }

  private static void onJoin(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.scores().remember(player);
    active.display().update(player, true);
  }

  private static void onLeave(ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.display().remove(player);
  }
}
