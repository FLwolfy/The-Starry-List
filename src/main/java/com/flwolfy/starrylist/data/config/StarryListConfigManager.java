package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.data.lang.StarryListLangAdapter;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.data.script.StarryListScriptAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/** Loads, validates, atomically updates, and persists the server configuration. */
public final class StarryListConfigManager {

  public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("starrylist.json");

  private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
  private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(StarryListLang.class, new StarryListLangAdapter())
      .registerTypeAdapter(StarryListScript.class, new StarryListScriptAdapter())
      .setPrettyPrinting()
      .create();
  private static final StarryListConfigManager INSTANCE = new StarryListConfigManager();

  private volatile StarryListConfigData data;
  private volatile Consumer<StarryListConfigData> applyListener = ignored -> {};

  private StarryListConfigManager() {
    data = loadAtStartup();
    StarryListLangManager.getInstance().setLanguage(data.general().language());
  }

  public static StarryListConfigManager getInstance() {
    return INSTANCE;
  }

  public StarryListConfigData data() {
    LOCK.readLock().lock();
    try {
      return data;
    } finally {
      LOCK.readLock().unlock();
    }
  }

  public void setApplyListener(Consumer<StarryListConfigData> listener) {
    applyListener = listener == null ? ignored -> {} : listener;
  }

  public boolean reload() {
    LOCK.writeLock().lock();
    try {
      LoadResult result = readAndValidate(false);
      if (!result.valid()) return false;
      activate(result.data());
      if (result.normalized()) saveStatic(result.data());
      return true;
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to reload StarryList config", exception);
      return false;
    } finally {
      LOCK.writeLock().unlock();
    }
  }

  public boolean update(StarryListConfigData replacement) {
    if (replacement == null) return false;
    List<String> invalid = replacement.validate();
    if (!invalid.isEmpty()) {
      StarryListMod.LOGGER.error("Refusing invalid StarryList config fields: {}", invalid);
      return false;
    }
    LOCK.writeLock().lock();
    StarryListConfigData previous = data;
    try {
      activate(replacement);
      saveStatic(replacement);
      return true;
    } catch (Exception exception) {
      if (data != previous) {
        try {
          activate(previous);
        } catch (RuntimeException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
      }
      StarryListMod.LOGGER.error("Failed to update StarryList config", exception);
      return false;
    } finally {
      LOCK.writeLock().unlock();
    }
  }

  private void activate(StarryListConfigData replacement) {
    StarryListConfigData previous = data;
    data = replacement;
    StarryListLangManager.getInstance().setLanguage(replacement.general().language());
    try {
      applyListener.accept(replacement);
    } catch (RuntimeException exception) {
      data = previous;
      StarryListLangManager.getInstance().setLanguage(previous.general().language());
      throw exception;
    }
  }

  private StarryListConfigData loadAtStartup() {
    try {
      if (Files.notExists(CONFIG_PATH)) {
        saveStatic(StarryListConfigData.DEFAULT);
        return StarryListConfigData.DEFAULT;
      }
      LoadResult result = readAndValidate(true);
      if (result.valid()) {
        if (result.normalized()) saveStatic(result.data());
        return result.data();
      }
      backupInvalid();
      saveStatic(StarryListConfigData.DEFAULT);
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to load StarryList config; using defaults", exception);
      if (Files.exists(CONFIG_PATH)) backupInvalid();
      try {
        saveStatic(StarryListConfigData.DEFAULT);
      } catch (Exception saveException) {
        exception.addSuppressed(saveException);
        StarryListMod.LOGGER.error("Failed to write default StarryList config", saveException);
      }
    }
    return StarryListConfigData.DEFAULT;
  }

  private static LoadResult readAndValidate(boolean startup) throws Exception {
    try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
      JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
      if (parsed == null || !parsed.isJsonObject()) {
        StarryListMod.LOGGER.error("StarryList config root must be an object");
        return new LoadResult(StarryListConfigData.DEFAULT, false, false);
      }
      JsonObject target = parsed.getAsJsonObject();
      JsonObject defaults = GSON.toJsonTree(StarryListConfigData.DEFAULT).getAsJsonObject();
      boolean normalized = mergeDefaults(target, defaults);
      StarryListConfigData loaded = GSON.fromJson(target, StarryListConfigData.class);
      List<String> invalid = loaded == null ? List.of("root") : loaded.validate();
      if (!invalid.isEmpty()) {
        StarryListMod.LOGGER.error("Invalid StarryList config fields: {}", invalid);
        return new LoadResult(StarryListConfigData.DEFAULT, false, normalized);
      }
      StarryListMod.LOGGER.info("Loaded StarryList config from {}", CONFIG_PATH);
      return new LoadResult(loaded, true, normalized);
    } catch (Exception exception) {
      if (!startup) StarryListMod.LOGGER.error("Could not parse StarryList config", exception);
      throw exception;
    }
  }

  private static boolean mergeDefaults(JsonObject target, JsonObject defaults) {
    boolean changed = false;
    for (var entry : defaults.entrySet()) {
      if (!target.has(entry.getKey()) || target.get(entry.getKey()).isJsonNull()) {
        target.add(entry.getKey(), entry.getValue().deepCopy());
        changed = true;
      } else if (entry.getValue().isJsonObject() && target.get(entry.getKey()).isJsonObject()) {
        changed |= mergeDefaults(
            target.getAsJsonObject(entry.getKey()),
            entry.getValue().getAsJsonObject()
        );
      }
    }
    return changed;
  }

  private static void saveStatic(StarryListConfigData value) throws Exception {
    Files.createDirectories(CONFIG_PATH.getParent());
    Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
    try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
      GSON.toJson(value, writer);
    }
    try {
      Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void backupInvalid() {
    try {
      String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
      Files.move(
          CONFIG_PATH,
          CONFIG_PATH.resolveSibling("starrylist.invalid-" + timestamp + ".json"),
          StandardCopyOption.REPLACE_EXISTING
      );
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to back up invalid StarryList config", exception);
    }
  }

  private record LoadResult(StarryListConfigData data, boolean valid, boolean normalized) {}
}
