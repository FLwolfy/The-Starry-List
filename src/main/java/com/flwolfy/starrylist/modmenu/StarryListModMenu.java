package com.flwolfy.starrylist.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Optional ModMenu bridge. Cloth Config remains optional at runtime. */
public final class StarryListModMenu implements ModMenuApi {

  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    if (!FabricLoader.getInstance().isModLoaded("cloth-config2")) {
      return parent -> parent;
    }

    return StarryListClothConfigGUI::create;
  }
}
