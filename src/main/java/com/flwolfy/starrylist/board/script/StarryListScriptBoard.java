package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardPresentation;
import java.util.List;
import java.util.Map;

/** Base type implemented by hot-reloadable Groovy leaderboard classes. */
public abstract class StarryListScriptBoard extends StarryListBoard {

  private String sourceFile = "<unknown>";
  private int loreLines;
  private StarryListScriptLanguageCatalog languageCatalog;

  /**
   * Returns localized titles and lore keyed by Minecraft locale.
   *
   * @return localized board presentations, including an {@code en_us} entry
   */
  public abstract Map<String, StarryListBoardPresentation> translations();

  /**
   * Declares reload-managed Fabric event subscriptions for this board.
   *
   * @param registrar the script-safe subscription and board service registrar
   */
  public abstract void subscribe(StarryListScriptRegistrar registrar);

  /**
   * Creates one localized presentation for use in a Groovy map literal.
   *
   * @param title the localized board title
   * @param lore the localized lore lines
   * @return the presentation value
   */
  protected final StarryListBoardPresentation text(String title, String... lore) {
    return new StarryListBoardPresentation(title, List.of(lore));
  }

  /**
   * Resolves a title and ordered lore keys from every discovered script language file.
   *
   * @param titleKey title translation key, required in {@code en_us.json}
   * @param loreKeys ordered lore translation keys, required in {@code en_us.json}
   * @return localized presentations keyed by discovered Minecraft locale
   */
  protected final Map<String, StarryListBoardPresentation> translatableText(
      String titleKey,
      String... loreKeys
  ) {
    if (languageCatalog == null) {
      throw new IllegalStateException("Script language catalog is not bound: " + sourceFile);
    }
    return languageCatalog.presentations(sourceFile, titleKey, loreKeys);
  }

  /**
   * Returns the source file that produced this script board.
   *
   * @return the source file name
   */
  public final String sourceFile() {
    return sourceFile;
  }

  @Override
  protected final String titleTranslationKey() {
    return "starrylist.script." + id() + ".title";
  }

  @Override
  protected final List<String> loreTranslationKeys() {
    return java.util.stream.IntStream.range(0, loreLines)
        .mapToObj(index -> "starrylist.script." + id() + ".lore." + index)
        .toList();
  }

  void bindSourceFile(String sourceFile) {
    this.sourceFile = sourceFile;
  }

  void bindLoreLines(int loreLines) {
    this.loreLines = loreLines;
  }

  void bindLanguageCatalog(StarryListScriptLanguageCatalog languageCatalog) {
    this.languageCatalog = java.util.Objects.requireNonNull(languageCatalog, "languageCatalog");
  }
}
