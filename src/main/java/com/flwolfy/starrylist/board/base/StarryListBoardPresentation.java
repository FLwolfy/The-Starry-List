package com.flwolfy.starrylist.board.base;

import java.util.List;

/** Localized player-facing title and lore resolved from language resources. */
public record StarryListBoardPresentation(String title, List<String> lore) {

  /** Copies lore so a board cannot mutate presentation data after registration. */
  public StarryListBoardPresentation {
    if (title == null || title.isBlank()) throw new IllegalArgumentException("Blank board title");
    lore = lore == null ? List.of() : List.copyOf(lore);
    if (lore.stream().anyMatch(line -> line == null || line.isBlank())) {
      throw new IllegalArgumentException("Blank board lore");
    }
  }
}
