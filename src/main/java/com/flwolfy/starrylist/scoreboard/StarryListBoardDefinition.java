package com.flwolfy.starrylist.scoreboard;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.script.context.StarryListBoard;
import java.util.List;

public record StarryListBoardDefinition(
    String id,
    String objectiveName,
    String displayName,
    boolean builtIn,
    boolean enabled,
    List<StarryListConfigData.Source> sources
) {

  public StarryListBoardDefinition {
    sources = List.copyOf(sources);
  }

  public StarryListBoard scriptView() {
    return new StarryListBoard(id, displayName, objectiveName);
  }
}
