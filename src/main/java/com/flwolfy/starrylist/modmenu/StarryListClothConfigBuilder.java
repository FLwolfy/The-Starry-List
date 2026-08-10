package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.List;
import java.util.ArrayList;
import java.util.function.UnaryOperator;

/** Mutable editor snapshot used only while a Cloth Config screen is open. */
public final class StarryListClothConfigBuilder {

  StarryListLang language;
  int adminPermissionLevel;
  boolean hiddenByDefault;
  boolean rotationEnabled;
  int rotationIntervalSeconds;
  List<String> defaultBoards;
  List<StarryListConfigData.CustomBoard> customBoards;
  boolean customBoardStructureChanged;

  public StarryListClothConfigBuilder(StarryListConfigData data) {
    language = data.general().language();
    adminPermissionLevel = data.general().adminPermissionLevel();
    hiddenByDefault = data.display().hiddenByDefault();
    rotationEnabled = data.display().rotationEnabled();
    rotationIntervalSeconds = data.display().rotationIntervalSeconds();
    defaultBoards = List.copyOf(data.display().defaultBoards());
    customBoards = List.copyOf(data.customBoards());
  }

  public StarryListConfigData build() {
    return new StarryListConfigData(
        new StarryListConfigData.General(language, adminPermissionLevel),
        new StarryListConfigData.Display(
            hiddenByDefault,
            rotationEnabled,
            rotationIntervalSeconds,
            List.copyOf(defaultBoards)
        ),
        List.copyOf(customBoards)
    );
  }

  public void updateBoard(int index, UnaryOperator<StarryListConfigData.CustomBoard> update) {
    if (customBoardStructureChanged) return;
    if (index < 0 || index >= customBoards.size()) return;
    List<StarryListConfigData.CustomBoard> copy = new ArrayList<>(customBoards);
    copy.set(index, update.apply(copy.get(index)));
    customBoards = List.copyOf(copy);
  }

  public void updateSource(
      int boardIndex,
      int sourceIndex,
      UnaryOperator<StarryListConfigData.Source> update
  ) {
    updateBoard(boardIndex, board -> {
      if (sourceIndex < 0 || sourceIndex >= board.sources().size()) return board;
      List<StarryListConfigData.Source> sources = new ArrayList<>(board.sources());
      sources.set(sourceIndex, update.apply(sources.get(sourceIndex)));
      return new StarryListConfigData.CustomBoard(
          board.id(), board.objectiveName(), board.displayName(), board.enabled(), List.copyOf(sources)
      );
    });
  }
}
