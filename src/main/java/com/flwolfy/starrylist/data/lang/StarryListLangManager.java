package com.flwolfy.starrylist.data.lang;

import com.flwolfy.starrylist.StarryListMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/** Loads bundled translations for server-side literal messages. */
public final class StarryListLangManager {

  private static final Gson GSON = new Gson();
  private static final StarryListLangManager INSTANCE = new StarryListLangManager();

  private final Map<String, Map<String, String>> languages = new HashMap<>();
  private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
  private volatile ScriptCatalog scriptCatalog = new ScriptCatalog(Map.of(), Set.of());
  private volatile String language = StarryListLang.ENGLISH.getLangKey();

  private StarryListLangManager() {
    for (StarryListLang candidate : StarryListLang.values()) {
      String path = "/assets/" + StarryListMod.MOD_ID + "/lang/"
          + candidate.getLangKey() + ".json";
      try (InputStreamReader reader = new InputStreamReader(
          Objects.requireNonNull(getClass().getResourceAsStream(path)),
          StandardCharsets.UTF_8
      )) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        languages.put(candidate.getLangKey(), GSON.fromJson(reader, type));
      } catch (Exception exception) {
        StarryListMod.LOGGER.error("Failed to load language {}", candidate.getLangKey(), exception);
      }
    }
  }

  /**
   * Returns the process-wide server translation owner.
   *
   * @return process-wide server translation manager
   */
  public static StarryListLangManager getInstance() {
    return INSTANCE;
  }

  /**
   * Selects the language used to render server-side literal messages.
   *
   * @param language requested language
   */
  public void setLanguage(String language) {
    String normalized = normalize(language);
    this.language = availableLocales().contains(normalized)
        ? normalized
        : StarryListLang.ENGLISH.getLangKey();
  }

  /**
   * Selects one of the bundled server languages.
   *
   * @param language requested bundled language
   */
  public void setLanguage(StarryListLang language) {
    setLanguage(language == null ? null : language.getLangKey());
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
    String selected = availableLocales().contains(normalized) ? normalized : language;
    return render(selected, key, arguments);
  }

  /**
   * Renders text for one of StarryList's bundled server languages.
   *
   * @param language the requested bundled language
   * @param key the translation key
   * @param arguments the format arguments
   * @return the rendered literal component
   */
  public Component textFor(StarryListLang language, String key, Object... arguments) {
    return textFor(language == null ? null : language.getLangKey(), key, arguments);
  }

  /**
   * Atomically replaces translations supplied by the active Groovy boards.
   *
   * @param translations locale maps containing script translation keys
   */
  public void replaceScriptTranslations(
      Map<String, Map<String, String>> translations,
      Set<String> locales
  ) {
    Map<String, Map<String, String>> copied = translations.entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(
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
    return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(result));
  }

  /**
   * Returns a friendly built-in language name or the locale key for custom languages.
   *
   * @param locale locale key
   * @return user-facing language label
   */
  public String languageName(String locale) {
    String normalized = normalize(locale);
    for (StarryListLang candidate : StarryListLang.values()) {
      if (candidate.getLangKey().equals(normalized)) {
        return candidate.toString();
      }
    }
    return normalized;
  }

  private Component render(String locale, String key, Object... arguments) {
    Map<String, Map<String, String>> scriptLanguages = scriptCatalog.translations();
    String pattern = scriptLanguages.getOrDefault(locale, Map.of()).get(key);
    if (pattern == null) {
      pattern = scriptLanguages.getOrDefault(language, Map.of()).get(key);
    }
    if (pattern == null) {
      pattern = scriptLanguages.getOrDefault(
          StarryListLang.ENGLISH.getLangKey(), Map.of()).get(key);
    }
    Map<String, String> selected = languages.getOrDefault(locale, Map.of());
    if (pattern == null) {
      pattern = selected.get(key);
    }
    if (pattern == null) {
      pattern = languages.getOrDefault(language, Map.of()).get(key);
    }
    if (pattern == null) {
      pattern = languages.getOrDefault(StarryListLang.ENGLISH.getLangKey(), Map.of()).get(key);
    }
    if (pattern == null) {
      if (warnedMissingKeys.add(key)) {
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

  private static String normalize(String locale) {
    return locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
  }

  private record ScriptCatalog(
      Map<String, Map<String, String>> translations,
      Set<String> locales
  ) {
  }
}
