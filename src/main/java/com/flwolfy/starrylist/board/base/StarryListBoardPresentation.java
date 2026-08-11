package com.flwolfy.starrylist.board.base;

import java.util.List;

/**
 * Localized player-facing presentation resolved from language resources.
 *
 * @param title the localized board title
 * @param lore the localized board-specific lore
 */
public record StarryListBoardPresentation(String title, List<String> lore) {

  /**
   * Creates an immutable, validated board presentation.
   *
   * @param title the localized board title
   * @param lore the localized board-specific lore
   */
  public StarryListBoardPresentation {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Blank board title");
    }

    lore = lore == null ? List.of() : List.copyOf(lore);

    if (lore.stream().anyMatch(line -> line == null || line.isBlank())) {
      throw new IllegalArgumentException("Blank board lore");
    }
  }
}
