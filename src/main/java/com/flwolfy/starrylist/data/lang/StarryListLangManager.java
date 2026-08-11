package com.flwolfy.starrylist.data.lang;

import com.flwolfy.starrylist.StarryListMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/** Loads bundled translations for server-side literal messages. */
public final class StarryListLangManager {

  private static final Gson GSON = new Gson();
  private static final StarryListLangManager INSTANCE = new StarryListLangManager();

  private final Map<String, Map<String, String>> languages = new HashMap<>();
  private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
  private volatile Map<String, Map<String, String>> scriptLanguages = Map.of();
  private volatile StarryListLang language = StarryListLang.ENGLISH;

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
  public void setLanguage(StarryListLang language) {
    this.language = languages.containsKey(language.getLangKey())
        ? language
        : StarryListLang.ENGLISH;
  }

  /**
   * Renders a translated literal component with English and key fallbacks.
   *
   * @param key translation key
   * @param arguments format arguments
   * @return rendered literal component
   */
  public Component text(String key, Object... arguments) {
    return render(language.getLangKey(), key, arguments);
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
    String normalized = locale == null ? "" : locale.toLowerCase(Locale.ROOT);
    String selected = languages.containsKey(normalized) || scriptLanguages.containsKey(normalized)
        ? normalized : language.getLangKey();
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
  public void replaceScriptTranslations(Map<String, Map<String, String>> translations) {
    scriptLanguages = translations.entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Map.copyOf(entry.getValue())
        )
    );
    warnedMissingKeys.clear();
  }

  private Component render(String locale, String key, Object... arguments) {
    String pattern = scriptLanguages.getOrDefault(locale, Map.of()).get(key);
    if (pattern == null) {
      pattern = scriptLanguages.getOrDefault(language.getLangKey(), Map.of()).get(key);
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
      pattern = languages.getOrDefault(language.getLangKey(), Map.of()).get(key);
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
}
