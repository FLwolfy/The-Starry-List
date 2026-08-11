package com.flwolfy.starrylist.board.base;

import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Self-contained extension point for one StarryList leaderboard.
 *
 * <p>Concrete boards are discovered recursively in the {@code com.flwolfy.starrylist.board}
 * module tree. Each board owns its identity, localization keys, icon and event-driven statistic
 * registration.</p>
 */
public abstract class StarryListBoard {

  /** Stable configuration, command and SavedData identifier. */
  public abstract String id();

  /** Stable, language-independent vanilla scoreboard objective name. */
  public abstract String objectiveName();

  /** Canonical display and rotation position; lower values appear first. */
  public abstract int order();

  /** Fresh icon stack used by SGUI. */
  public abstract ItemStack icon();

  /** Translation key for the board title. Override only when the conventional key is unsuitable. */
  protected String titleTranslationKey() {
    return "starrylist.board." + id() + ".title";
  }

  /** Translation keys for board-specific lore lines. */
  protected List<String> loreTranslationKeys() {
    return List.of("starrylist.board." + id() + ".description");
  }

  /** Resolves localized presentation through the shared language manager. */
  public final StarryListBoardPresentation presentation(StarryListLang language) {
    StarryListLangManager translations = StarryListLangManager.getInstance();
    return new StarryListBoardPresentation(
        translations.textFor(language, titleTranslationKey()).getString(),
        loreTranslationKeys().stream()
            .map(key -> translations.textFor(language, key).getString())
            .toList()
    );
  }

  /** Registers this board's statistic collectors once during common mod initialization. */
  public abstract void register(StarryListBoardRegistrar registrar);

  /** Resolves presentation with English fallback. */
  public final StarryListBoardPresentation presentationFor(String locale) {
    StarryListLangManager translations = StarryListLangManager.getInstance();
    return new StarryListBoardPresentation(
        translations.textFor(locale, titleTranslationKey()).getString(),
        loreTranslationKeys().stream()
            .map(key -> translations.textFor(locale, key).getString())
            .toList()
    );
  }

  /** Resolves the configured server-language objective title. */
  public final Component displayName() {
    return StarryListLangManager.getInstance().text(titleTranslationKey());
  }

  /** Resolves a player's preferred localized title. */
  public final Component displayName(ServerPlayer player) {
    return StarryListLangManager.getInstance().textFor(
        player.clientInformation().language(), titleTranslationKey()
    );
  }

  /** Resolves a player's localized board-specific lore. */
  public final List<Component> lore(ServerPlayer player) {
    return presentationFor(player.clientInformation().language()).lore().stream()
        .map(line -> (Component) Component.literal(line))
        .toList();
  }

  /** Resolves and validates the icon after Minecraft's item components are available. */
  public final ItemStack iconForGui() {
    ItemStack stack = icon();
    if (stack == null || stack.isEmpty()) {
      throw new IllegalStateException("Board supplied an empty SGUI icon: " + id());
    }
    return stack;
  }
}
