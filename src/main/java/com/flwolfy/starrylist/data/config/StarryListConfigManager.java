package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.data.lang.StarryListLangAdapter;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
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

  private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
      .resolve("starrylist.json");

  private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
  private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(StarryListLang.class, new StarryListLangAdapter())
      .setPrettyPrinting()
      .create();
  private static final StarryListConfigManager INSTANCE = new StarryListConfigManager();

  private volatile StarryListConfigData data;
  private volatile Consumer<StarryListConfigData> applyListener = ignored -> {};

  private StarryListConfigManager() {
    data = loadAtStartup();
    StarryListLangManager.getInstance().setLanguage(data.general().language());
  }

  /**
   * Returns the process-wide configuration owner.
   *
   * @return process-wide configuration manager
   */
  public static StarryListConfigManager getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the currently active configuration.
   *
   * @return current validated immutable configuration snapshot
   */
  public StarryListConfigData data() {
    LOCK.readLock().lock();
    try {
      return data;
    } finally {
      LOCK.readLock().unlock();
    }
  }

  /**
   * Sets the callback invoked after a replacement configuration becomes active.
   *
   * @param listener apply callback, or {@code null} to clear it
   */
  public void setApplyListener(Consumer<StarryListConfigData> listener) {
    applyListener = listener == null ? ignored -> {} : listener;
  }

  /**
   * Reloads and validates the configuration file without replacing a valid active snapshot on failure.
   *
   * @return whether the file was successfully loaded and activated
   */
  public boolean reload() {
    LOCK.writeLock().lock();
    try {
      LoadResult result = readAndValidate(false);
      if (!result.valid()) {
        return false;
      }

      activate(result.data());
      if (result.normalized()) {
        saveStatic(result.data());
      }

      return true;
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to reload StarryList config", exception);
      return false;
    } finally {
      LOCK.writeLock().unlock();
    }
  }

  /**
   * Validates, activates, and persists a replacement configuration.
   *
   * @param replacement proposed configuration snapshot
   * @return whether the replacement was successfully saved and activated
   */
  public boolean update(StarryListConfigData replacement) {
    if (replacement == null) {
      return false;
    }

    List<String> invalid = replacement.validate();
    if (!invalid.isEmpty()) {
      StarryListMod.LOGGER.error("Refusing invalid StarryList config fields: {}", invalid);
      return false;
    }

    replacement = canonicalize(replacement);
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
        if (result.normalized()) {
          saveStatic(result.data());
        }

        return result.data();
      }
      backupInvalid();
      saveStatic(StarryListConfigData.DEFAULT);
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to load StarryList config; using defaults", exception);
      if (Files.exists(CONFIG_PATH)) {
        backupInvalid();
      }

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
      boolean normalized = false;

      StarryListConfigData loaded = GSON.fromJson(target, StarryListConfigData.class);
      List<String> invalid = loaded == null ? List.of("root") : loaded.validate();
      if (!invalid.isEmpty()) {
        StarryListMod.LOGGER.error("Invalid StarryList config fields: {}", invalid);
        return new LoadResult(StarryListConfigData.DEFAULT, false, normalized);
      }
      if (loaded != null && loaded.display() != null && loaded.display().enabledBoards() != null) {
        List<String> canonical = StarryListConfigData.normalizeIds(loaded.display().enabledBoards());
        if (!canonical.equals(loaded.display().enabledBoards())) {
          loaded = new StarryListConfigData(
              loaded.general(),
              new StarryListConfigData.Display(
                  loaded.display().hiddenByDefault(),
                  loaded.display().rotationEnabled(),
                  loaded.display().rotationIntervalSeconds(),
                  canonical
              ),
              loaded.blacklist()
          );
          normalized = true;
        }
      }
      StarryListMod.LOGGER.info("Loaded StarryList config from {}", CONFIG_PATH);
      return new LoadResult(loaded, true, normalized);
    } catch (Exception exception) {
      if (!startup) {
        StarryListMod.LOGGER.error("Could not parse StarryList config", exception);
      }

      throw exception;
    }
  }

  private static StarryListConfigData canonicalize(StarryListConfigData value) {
    if (value.display() == null || value.display().enabledBoards() == null) {
      return value;
    }

    return new StarryListConfigData(
        value.general(),
        new StarryListConfigData.Display(
            value.display().hiddenByDefault(),
            value.display().rotationEnabled(),
            value.display().rotationIntervalSeconds(),
            StarryListConfigData.normalizeIds(value.display().enabledBoards())
        ),
        value.blacklist()
    );
  }

  private static void saveStatic(StarryListConfigData value) throws Exception {
    Files.createDirectories(CONFIG_PATH.getParent());
    Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
    try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
      GSON.toJson(value, writer);
    }
    try {
      Files.move(
          temporary,
          CONFIG_PATH,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE
      );
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
