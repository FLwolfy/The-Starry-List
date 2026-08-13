package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.board.base.StarryListBoardPresentation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable external translations available while one script catalog is compiled. */
final class StarryListScriptLanguageCatalog {

  private static final String ENGLISH = "en_us";

  private final Map<String, Map<String, String>> languages;
  private final Set<String> warnedFallbacks = new HashSet<>();

  StarryListScriptLanguageCatalog(Map<String, Map<String, String>> languages) {
    Map<String, Map<String, String>> copy = new LinkedHashMap<>();
    languages.forEach((locale, values) -> copy.put(locale, Map.copyOf(values)));
    this.languages = Map.copyOf(copy);
  }

  Set<String> locales() {
    return languages.keySet();
  }

  Map<String, StarryListBoardPresentation> presentations(
      String sourceFile,
      String titleKey,
      String... loreKeys
  ) {
    List<String> keys = validateKeys(titleKey, loreKeys);
    Map<String, String> english = languages.getOrDefault(ENGLISH, Map.of());
    for (String key : keys) {
      if (!english.containsKey(key)) {
        throw new IllegalStateException(
            "Missing en_us translation " + key + " required by " + sourceFile
        );
      }
    }

    Map<String, StarryListBoardPresentation> result = new LinkedHashMap<>();
    languages.forEach((locale, values) -> {
      List<String> resolved = keys.stream()
          .map(key -> resolve(sourceFile, locale, values, english, key))
          .toList();
      result.put(locale, new StarryListBoardPresentation(
          resolved.getFirst(), resolved.subList(1, resolved.size())
      ));
    });
    return Map.copyOf(result);
  }

  private static List<String> validateKeys(String titleKey, String[] loreKeys) {
    List<String> keys = new ArrayList<>();
    keys.add(titleKey);
    if (loreKeys != null) {
      keys.addAll(List.of(loreKeys));
    }
    if (keys.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new IllegalArgumentException("Translation keys must not be blank");
    }
    if (new HashSet<>(keys).size() != keys.size()) {
      throw new IllegalArgumentException("Translation keys must be unique");
    }
    return List.copyOf(keys);
  }

  private String resolve(
      String sourceFile,
      String locale,
      Map<String, String> values,
      Map<String, String> english,
      String key
  ) {
    String value = values.get(key);
    if (value != null) {
      return value;
    }
    String warning = sourceFile + '\0' + locale + '\0' + key;
    if (!ENGLISH.equals(locale) && warnedFallbacks.add(warning)) {
      StarryListMod.LOGGER.warn(
          "Missing {} translation {} for {}; using en_us",
          locale,
          key,
          sourceFile
      );
    }
    return english.get(key);
  }
}
