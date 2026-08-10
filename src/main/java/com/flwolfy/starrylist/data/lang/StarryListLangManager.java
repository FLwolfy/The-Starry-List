package com.flwolfy.starrylist.data.lang;

import com.flwolfy.starrylist.StarryListMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

  public static StarryListLangManager getInstance() {
    return INSTANCE;
  }

  public void setLanguage(StarryListLang language) {
    this.language = languages.containsKey(language.getLangKey())
        ? language
        : StarryListLang.ENGLISH;
  }

  public Component text(String key, Object... arguments) {
    Map<String, String> selected = languages.getOrDefault(
        language.getLangKey(),
        languages.getOrDefault(StarryListLang.ENGLISH.getLangKey(), Map.of())
    );
    String pattern = selected.get(key);
    if (pattern == null) {
      pattern = languages.getOrDefault(StarryListLang.ENGLISH.getLangKey(), Map.of()).get(key);
    }
    if (pattern == null) {
      if (warnedMissingKeys.add(key)) StarryListMod.LOGGER.warn("Missing language key: {}", key);
      return Component.literal(key);
    }
    try {
      return Component.literal(pattern.formatted(arguments));
    } catch (RuntimeException ignored) {
      return Component.literal(pattern);
    }
  }
}
