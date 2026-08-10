package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.List;

/** Mutable editor snapshot used only while a Cloth Config screen is open. */
final class StarryListClothConfigBuilder {

  StarryListLang language;
  int adminPermissionLevel;
  boolean hiddenByDefault;
  boolean rotationEnabled;
  int rotationIntervalSeconds;
  List<String> defaultBoards;

  StarryListClothConfigBuilder(StarryListConfigData data) {
    language = data.general().language();
    adminPermissionLevel = data.general().adminPermissionLevel();
    hiddenByDefault = data.display().hiddenByDefault();
    rotationEnabled = data.display().rotationEnabled();
    rotationIntervalSeconds = data.display().rotationIntervalSeconds();
    defaultBoards = List.copyOf(data.display().defaultBoards());
  }

  StarryListConfigData build() {
    return new StarryListConfigData(
        new StarryListConfigData.General(language, adminPermissionLevel),
        new StarryListConfigData.Display(
            hiddenByDefault,
            rotationEnabled,
            rotationIntervalSeconds,
            List.copyOf(defaultBoards)
        )
    );
  }
}
