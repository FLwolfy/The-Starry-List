package com.flwolfy.starrylist.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.atomic.AtomicBoolean;

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
          ignored -> StarryListClothConfigGUI.refreshIfRegistryChanged()
      );
    }

    return StarryListClothConfigGUI::create;
  }
}
