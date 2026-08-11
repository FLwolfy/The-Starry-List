package com.flwolfy.starrylist;

import com.flwolfy.starrylist.command.StarryListAdminCommand;
import com.flwolfy.starrylist.command.StarryListCommand;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The common StarryList entry point. All gameplay features remain server-side. */
public final class StarryListMod implements ModInitializer {

  /** Fabric mod identifier used by metadata, resources, logging, and SavedData. */
  public static final String MOD_ID = "the-starry-list";
  /** Shared mod logger. */
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static volatile StarryListRuntime runtime;

  /** {@inheritDoc} */
  @Override
  public void onInitialize() {
    StarryListBoardRegistry boards = StarryListBoardRegistry.getInstance();
    StarryListConfigManager config = StarryListConfigManager.getInstance();
    boards.registerAll();

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

    ServerPlayerEvents.JOIN.register(StarryListMod::onJoin);
    ServerPlayerEvents.LEAVE.register(StarryListMod::onLeave);
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      StarryListRuntime active = runtime;
      if (active != null) active.display().update(newPlayer, true);
    });
  }

  /**
   * Returns the services associated with the currently running server.
   *
   * @return active server runtime, or {@code null} while no server is running
   */
  public static StarryListRuntime runtime() {
    return runtime;
  }

  private static void onJoin(net.minecraft.server.level.ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.scores().remember(player);
    active.display().update(player, true);
  }

  private static void onLeave(net.minecraft.server.level.ServerPlayer player) {
    StarryListRuntime active = runtime;
    if (active == null) return;
    active.display().remove(player);
  }
}
