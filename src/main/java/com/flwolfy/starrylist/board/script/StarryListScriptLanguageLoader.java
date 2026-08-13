package com.flwolfy.starrylist.board.script;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/** Loads flat JSON translations used exclusively by Groovy leaderboard scripts. */
final class StarryListScriptLanguageLoader {

  private static final Gson GSON = new Gson();
  private static final Pattern LOCALE = Pattern.compile("[a-z0-9][a-z0-9_-]*");
  private static final java.util.List<String> EXAMPLES = java.util.List.of("en_us", "zh_cn");

  private final Path directory;

  StarryListScriptLanguageLoader() {
    this(FabricLoader.getInstance().getConfigDir().resolve("starrylist").resolve("lang"));
  }

  StarryListScriptLanguageLoader(Path directory) {
    this.directory = directory;
  }

  Path directory() {
    return directory;
  }

  StarryListScriptLanguageCatalog load() {
    try {
      createExamples();
      Map<String, Map<String, String>> languages = new LinkedHashMap<>();
      try (var paths = Files.list(directory)) {
        for (Path path : paths.filter(Files::isRegularFile)
            .filter(value -> value.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(".json"))
            .sorted(Comparator.comparing(value -> value.getFileName().toString()))
            .toList()) {
          String fileName = path.getFileName().toString();
          String locale = fileName.substring(0, fileName.length() - 5)
              .toLowerCase(Locale.ROOT);
          if (!LOCALE.matcher(locale).matches()) {
            continue;
          }
          if (languages.put(locale, read(path)) != null) {
            throw new IllegalStateException("Duplicate script language locale " + locale);
          }
        }
      }
      return new StarryListScriptLanguageCatalog(languages);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to load StarryList script languages from " + directory,
          exception
      );
    }
  }

  private void createExamples() throws IOException {
    Files.createDirectories(directory);
    for (String locale : EXAMPLES) {
      Path target = directory.resolve(locale + ".json");
      if (Files.exists(target)) {
        continue;
      }
      String resource = "/assets/the-starry-list/script/lang/" + locale + ".json";
      try (InputStream source = StarryListScriptLanguageLoader.class.getResourceAsStream(resource)) {
        if (source == null) {
          throw new IOException("Missing bundled script language example " + resource);
        }
        Files.copy(source, target);
      }
    }
  }

  private static Map<String, String> read(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      JsonElement parsed;
      try {
        parsed = GSON.fromJson(reader, JsonElement.class);
      } catch (RuntimeException exception) {
        throw new IllegalStateException("Invalid script language JSON " + path, exception);
      }
      if (parsed == null || !parsed.isJsonObject()) {
        throw new IllegalStateException("Script language file must contain an object: " + path);
      }
      JsonObject object = parsed.getAsJsonObject();
      Map<String, String> values = new LinkedHashMap<>();
      object.entrySet().forEach(entry -> {
        JsonElement value = entry.getValue();
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()) {
          throw new IllegalStateException(
              "Script language value must be a string: " + path + " -> " + entry.getKey()
          );
        }
        values.put(entry.getKey(), value.getAsString());
      });
      return Map.copyOf(values);
    }
  }
}
