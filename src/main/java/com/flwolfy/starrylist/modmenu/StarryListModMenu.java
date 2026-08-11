package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.modmenu.screen.StarryListClothConfigScreenController;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Optional ModMenu bridge. Cloth Config remains optional at runtime. */
public final class StarryListModMenu implements ModMenuApi {

  private static final AtomicBoolean REFRESH_REGISTERED = new AtomicBoolean();

  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    if (!FabricLoader.getInstance().isModLoaded("cloth-config2")) {
      return parent -> parent;
    }

    if (REFRESH_REGISTERED.compareAndSet(false, true)) {
      ClientTickEvents.END_CLIENT_TICK.register(
          ignored -> StarryListClothConfigScreenController.refreshIfRegistryChanged()
      );
    }

    return StarryListClothConfigScreenController::create;
  }
}
