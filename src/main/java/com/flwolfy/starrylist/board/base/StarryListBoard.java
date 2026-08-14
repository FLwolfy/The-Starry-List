package com.flwolfy.starrylist.board.base;

import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Self-contained extension point for one StarryList leaderboard.
 *
 * <p>Bundled Java boards are discovered recursively in the
 * {@code com.flwolfy.starrylist.board} module tree, while Groovy boards are added by the script
 * manager. Every board owns its identity, localization keys and icon.</p>
 */
public abstract class StarryListBoard {

  private static final ClassValue<Boolean> RECALCULATION_SUPPORT = new ClassValue<>() {
    @Override
    protected Boolean computeValue(Class<?> type) {
      try {
        return type.getMethod(
            "recalculate", ServerPlayer.class, StarryListBoardState.class
        ).getDeclaringClass() != StarryListBoard.class;
      } catch (NoSuchMethodException exception) {
        throw new IllegalStateException("Missing StarryList recalculation hook", exception);
      }
    }
  };

  /**
   * Returns the stable identifier used by configuration, commands, and saved data.
   *
   * @return the board identifier
   */
  public abstract String id();

  /**
   * Returns the stable, language-independent vanilla scoreboard objective name.
   *
   * @return the objective name
   */
  public abstract String objectiveName();

  /**
   * Returns the canonical display and rotation position.
   *
   * @return the board order, where lower values appear first
   */
  public abstract int order();

  /**
   * Creates the icon displayed in the player GUI.
   *
   * @return a fresh, non-empty icon stack
   */
  public abstract ItemStack icon();

  /**
   * Reconstructs this board's absolute score from authoritative player data.
   *
   * <p>This hook is optional. Implementations that support administrator-triggered recalculation
   * override it and return the replacement score. The command owns the actual scoreboard write so
   * blacklist archives and display names continue to follow the normal administrator policy.</p>
   *
   * @param player online player whose score is being reconstructed
   * @param state persistent state scoped to this board and player
   * @return the absolute replacement score, or empty when recalculation is unsupported
   */
  public OptionalInt recalculate(ServerPlayer player, StarryListBoardState state) {
    return OptionalInt.empty();
  }

  /**
   * Returns whether the concrete board overrides {@link #recalculate(ServerPlayer,
   * StarryListBoardState)}.
   *
   * @return whether this board supports score recalculation
   */
  public final boolean supportsRecalculation() {
    return RECALCULATION_SUPPORT.get(getClass());
  }

  /**
   * Returns the translation key for the board title.
   *
   * @return the title translation key
   */
  protected String titleTranslationKey() {
    return "starrylist.board." + id() + ".title";
  }

  /**
   * Returns the translation keys for board-specific lore lines.
   *
   * @return the ordered lore translation keys
   */
  protected List<String> loreTranslationKeys() {
    return List.of("starrylist.board." + id() + ".description");
  }

  /**
   * Resolves the presentation for a locale, with English as the fallback language.
   *
   * @param locale the client locale key
   * @return the localized title and lore
   */
  public final StarryListBoardPresentation presentationFor(String locale) {
    StarryListLangManager translations = StarryListLangManager.getInstance();
    return new StarryListBoardPresentation(
        translations.textFor(locale, titleTranslationKey()).getString(),
        loreTranslationKeys().stream()
            .map(key -> translations.textFor(locale, key).getString())
            .toList()
    );
  }

  /**
   * Resolves the objective title in the configured server language.
   *
   * @return the localized objective title
   */
  public final Component displayName() {
    return StarryListLangManager.getInstance().text(titleTranslationKey());
  }

  /**
   * Resolves the board-specific lore in the configured server language.
   *
   * @return the localized lore lines
   */
  public final List<Component> lore() {
    StarryListLangManager translations = StarryListLangManager.getInstance();
    return loreTranslationKeys().stream()
        .map(translations::text)
        .toList();
  }

  /**
   * Resolves and validates the icon after Minecraft's item components are available.
   *
   * @return a non-empty icon stack
   * @throws IllegalStateException if the board supplies an empty icon
   */
  public final ItemStack iconForGui() {
    ItemStack stack = icon();
    if (stack == null || stack.isEmpty()) {
      throw new IllegalStateException("Board supplied an empty SGUI icon: " + id());
    }
    return stack;
  }
}
