package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.ArrayList;
import java.util.List;

/** Mutable editor snapshot used only while a Cloth Config screen is open. */
final class StarryListClothConfigBuilder {

  StarryListLang language;
  int adminPermissionLevel;
  boolean hiddenByDefault;
  boolean rotationEnabled;
  int rotationIntervalSeconds;
  List<String> disabledBoards;
  List<String> enabledBoards;
  List<String> playerNamePatterns;

  StarryListClothConfigBuilder(StarryListConfigData data) {
    language = data.general().language();
    adminPermissionLevel = data.general().adminPermissionLevel();
    hiddenByDefault = data.display().hiddenByDefault();
    rotationEnabled = data.display().rotationEnabled();
    rotationIntervalSeconds = data.display().rotationIntervalSeconds();
    disabledBoards = List.copyOf(data.boards().disabledBoards());
    enabledBoards = List.copyOf(data.display().enabledBoards());
    playerNamePatterns = List.copyOf(data.blacklist().playerNamePatterns());
  }

  StarryListConfigData build() {
    return new StarryListConfigData(
        new StarryListConfigData.General(language, adminPermissionLevel),
        new StarryListConfigData.Display(
            hiddenByDefault,
            rotationEnabled,
            rotationIntervalSeconds,
            List.copyOf(enabledBoards)
        ),
        new StarryListConfigData.Boards(List.copyOf(disabledBoards)),
        new StarryListConfigData.Blacklist(List.copyOf(playerNamePatterns))
    );
  }

  void setBoardEnabled(String boardId, boolean enabled) {
    List<String> next = new ArrayList<>(enabledBoards);
    if (enabled) {
      if (!next.contains(boardId)) {
        next.add(boardId);
      }
    } else {
      next.remove(boardId);
    }

    enabledBoards = StarryListConfigData.normalizeIds(next);
  }

  void setBoardLoaded(String boardId, boolean loaded) {
    List<String> next = new ArrayList<>(disabledBoards);
    if (loaded) {
      next.remove(boardId);
    } else if (!next.contains(boardId)) {
      next.add(boardId);
    }

    disabledBoards = StarryListConfigData.normalizeIds(next);
  }
}
