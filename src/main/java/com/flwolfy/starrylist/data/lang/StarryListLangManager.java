package com.flwolfy.starrylist.data.lang;

import com.flwolfy.starrylist.StarryListMod;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

/** Loads bundled translations for server-side literal messages. */
public final class StarryListLangManager {

  private final Map<String, StarryListLanguage> languages;
  private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
  private volatile ScriptCatalog scriptCatalog = new ScriptCatalog(Map.of(), Set.of());
  private volatile String language = StarryListLanguageLoader.DEFAULT_LOCALE;

  private StarryListLangManager() {
    this(new StarryListLanguageLoader().load());
  }

  StarryListLangManager(Map<String, StarryListLanguage> languages) {
    this.languages = Collections.unmodifiableMap(new LinkedHashMap<>(languages));
  }

  /**
   * Returns the process-wide server translation owner.
   *
   * @return process-wide server translation manager
   */
  public static StarryListLangManager getInstance() {
    return Holder.INSTANCE;
  }

  /**
   * Selects the language used to render server-side literal messages.
   *
   * @param language requested language
   */
  public void setLanguage(String language) {
    String normalized = normalize(language);
    this.language = isAvailable(normalized)
        ? normalized
        : StarryListLanguageLoader.DEFAULT_LOCALE;
  }

  /**
   * Renders a translated literal component with English and key fallbacks.
   *
   * @param key translation key
   * @param arguments format arguments
   * @return rendered literal component
   */
  public Component text(String key, Object... arguments) {
    return render(language, key, arguments);
  }

  /**
   * Renders text for a requested locale with configured-language and English fallbacks.
   *
   * @param locale requested resource-pack locale
   * @param key translation key
   * @param arguments format arguments
   * @return rendered literal component
   */
  public Component textFor(String locale, String key, Object... arguments) {
    String normalized = normalize(locale);
    String selected = isAvailable(normalized) ? normalized : language;

    return render(selected, key, arguments);
  }

  /**
   * Atomically replaces translations supplied by the active Groovy boards.
   *
   * @param translations locale maps containing script translation keys
   * @param locales locales discovered for the active script catalog
   */
  public void replaceScriptTranslations(
      Map<String, Map<String, String>> translations,
      Set<String> locales
  ) {
    Map<String, Map<String, String>> copied = translations.entrySet().stream().collect(
        Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Map.copyOf(entry.getValue())
        )
    );

    scriptCatalog = new ScriptCatalog(copied, Set.copyOf(locales));
    warnedMissingKeys.clear();
  }

  /**
   * Returns bundled and active script locales in stable lexical order.
   *
   * @return immutable available locale keys
   */
  public Set<String> availableLocales() {
    Set<String> result = new TreeSet<>(languages.keySet());
    result.addAll(scriptCatalog.locales());

    return Collections.unmodifiableSet(new LinkedHashSet<>(result));
  }

  /**
   * Returns the locales backed by bundled core language resources.
   *
   * @return immutable core locale keys in stable lexical order
   */
  public Set<String> coreLocales() {
    return languages.keySet();
  }

  /**
   * Returns a friendly built-in language name or the locale key for custom languages.
   *
   * @param locale locale key
   * @return user-facing language label
   */
  public String languageName(String locale) {
    String normalized = normalize(locale);
    StarryListLanguage bundled = languages.get(normalized);
    return bundled == null ? normalized : bundled.name();
  }

  private Component render(String locale, String key, Object... arguments) {
    String pattern = resolveScriptPattern(locale, key);
    pattern = pattern == null ? resolveCorePattern(locale, key) : pattern;

    if (pattern == null) {
      if (warnedMissingKeys.add("all\0" + locale + '\0' + key)) {
        StarryListMod.LOGGER.warn("Missing language key: {}", key);
      }

      return Component.literal(key);
    }

    try {
      return Component.literal(pattern.formatted(arguments));
    } catch (RuntimeException ignored) {
      return Component.literal(pattern);
    }
  }

  private String resolveScriptPattern(String locale, String key) {
    Map<String, Map<String, String>> translations = scriptCatalog.translations();
    String pattern = translations.getOrDefault(locale, Map.of()).get(key);

    if (pattern == null) {
      pattern = translations.getOrDefault(language, Map.of()).get(key);
    }
    if (pattern == null) {
      pattern = translations.getOrDefault(
          StarryListLanguageLoader.DEFAULT_LOCALE,
          Map.of()
      ).get(key);
    }

    return pattern;
  }

  private String resolveCorePattern(String locale, String key) {
    String pattern = translation(locale, key);
    if (pattern != null) {
      return pattern;
    }
    warnMissingCoreTranslation(locale, key);

    if (!language.equals(locale)) {
      pattern = translation(language, key);
      if (pattern != null) {
        return pattern;
      }
      warnMissingCoreTranslation(language, key);
    }

    return translation(StarryListLanguageLoader.DEFAULT_LOCALE, key);
  }

  private static String normalize(String locale) {
    return locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
  }

  private String translation(String locale, String key) {
    StarryListLanguage bundled = languages.get(locale);
    return bundled == null ? null : bundled.translations().get(key);
  }

  private boolean isAvailable(String locale) {
    return languages.containsKey(locale) || scriptCatalog.locales().contains(locale);
  }

  private void warnMissingCoreTranslation(String locale, String key) {
    if (!StarryListLanguageLoader.DEFAULT_LOCALE.equals(locale)
        && languages.containsKey(locale)
        && warnedMissingKeys.add("core\0" + locale + '\0' + key)) {
      StarryListMod.LOGGER.warn(
          "Missing {} core language key {}; using en_us fallback",
          locale,
          key
      );
    }
  }

  private record ScriptCatalog(
      Map<String, Map<String, String>> translations,
      Set<String> locales
  ) {}

  private static final class Holder {
    private static final StarryListLangManager INSTANCE = new StarryListLangManager();
  }
}
