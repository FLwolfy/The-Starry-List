package com.flwolfy.starrylist.data.config;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

/** Loads, validates, atomically updates, and persists the server configuration. */
public final class StarryListConfigManager {

  private static final Path CONFIG_DIRECTORY = FabricLoader.getInstance().getConfigDir()
      .resolve("starrylist");
  private static final Path CONFIG_PATH = CONFIG_DIRECTORY.resolve("starrylist.json");

  private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .create();
  private static final StarryListConfigManager INSTANCE = new StarryListConfigManager();

  private volatile StarryListConfigData data;
  private volatile Consumer<StarryListConfigData> applyListener = ignored -> {};

  private StarryListConfigManager() {
    data = loadAtStartup();
    applyBoardLoading(data);
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
   * Reads and canonicalizes the configuration file for an editor without applying it.
   *
   * @return editable file snapshot, or the active snapshot if the file cannot be read
   */
  public StarryListConfigData loadForEditing() {
    LOCK.writeLock().lock();
    try {
      LoadResult result = readAndMerge(Set.of());
      saveStatic(result.data());
      return result.data();
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to load StarryList config for editing", exception);
      return data;
    } finally {
      LOCK.writeLock().unlock();
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
    return reloadRemoving(Set.of());
  }

  /**
   * Reloads the configuration while accepting identifiers removed by the same script transaction.
   *
   * @param removedBoardIds script identifiers to remove before validation
   * @return whether the file was successfully loaded and activated
   */
  public boolean reloadRemoving(Set<String> removedBoardIds) {
    LOCK.writeLock().lock();
    StarryListConfigData previous = data;
    try {
      LoadResult result = readAndMerge(removedBoardIds);
      activate(result.data());
      saveStatic(result.data());

      return true;
    } catch (Exception exception) {
      if (data != previous) {
        try {
          activate(previous);
        } catch (RuntimeException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
      }
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

  /**
   * Validates and persists a configuration without changing the active runtime snapshot.
   *
   * <p>The saved values become active only after {@link #reload()} or the next game startup.</p>
   *
   * @param replacement proposed configuration file contents
   * @return whether the replacement was successfully validated and saved
   */
  public boolean savePending(StarryListConfigData replacement) {
    if (replacement == null) {
      return false;
    }

    List<String> invalid = replacement.validate();
    if (!invalid.isEmpty()) {
      StarryListMod.LOGGER.error("Refusing invalid pending StarryList config fields: {}", invalid);
      return false;
    }

    replacement = canonicalize(replacement);
    LOCK.writeLock().lock();
    try {
      saveStatic(replacement);
      return true;
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to save pending StarryList config", exception);
      return false;
    } finally {
      LOCK.writeLock().unlock();
    }
  }

  /**
   * Removes unavailable script board identifiers from the active default configuration.
   *
   * @param boardIds identifiers to remove
   * @return whether the cleaned configuration was activated and saved
   */
  public boolean removeBoards(Set<String> boardIds) {
    if (boardIds.isEmpty()) {
      applyBoardLoading(data());
      return true;
    }

    StarryListConfigData current = data();
    List<String> enabled = current.display().enabledBoards().stream()
        .filter(id -> !boardIds.contains(id))
        .toList();
    List<String> disabled = current.boards().disabledBoards().stream()
        .filter(id -> !boardIds.contains(id))
        .toList();
    List<String> enabledScripts = current.boards().enabledScriptBoards().stream()
        .filter(id -> !boardIds.contains(id))
        .toList();
    if (enabled.equals(current.display().enabledBoards())
        && disabled.equals(current.boards().disabledBoards())
        && enabledScripts.equals(current.boards().enabledScriptBoards())) {
      return true;
    }

    return update(new StarryListConfigData(
        current.general(),
        new StarryListConfigData.Display(
            current.display().hiddenByDefault(),
            current.display().rotationEnabled(),
            current.display().rotationIntervalSeconds(),
            enabled
        ),
        new StarryListConfigData.Boards(disabled, enabledScripts),
        current.blacklist()
    ));
  }

  private void activate(StarryListConfigData replacement) {
    StarryListConfigData previous = data;
    data = replacement;
    applyBoardLoading(replacement);
    StarryListLangManager.getInstance().setLanguage(replacement.general().language());
    try {
      applyListener.accept(replacement);
    } catch (RuntimeException exception) {
      data = previous;
      applyBoardLoading(previous);
      StarryListLangManager.getInstance().setLanguage(previous.general().language());
      try {
        applyListener.accept(previous);
      } catch (RuntimeException rollbackFailure) {
        exception.addSuppressed(rollbackFailure);
      }
      throw exception;
    }
  }

  private StarryListConfigData loadAtStartup() {
    try {
      if (Files.notExists(CONFIG_PATH)) {
        saveStatic(StarryListConfigData.DEFAULT);
        return StarryListConfigData.DEFAULT;
      }
      LoadResult result = readAndMerge(Set.of());
      saveStatic(result.data());
      return result.data();
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to load StarryList config; using defaults", exception);
      try {
        saveStatic(StarryListConfigData.DEFAULT);
      } catch (Exception saveException) {
        exception.addSuppressed(saveException);
        StarryListMod.LOGGER.error("Failed to write default StarryList config", saveException);
      }
    }
    return StarryListConfigData.DEFAULT;
  }

  private static LoadResult readAndMerge(Set<String> removedBoardIds) throws Exception {
    try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
      JsonObject target;
      try {
        JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
        target = parsed != null && parsed.isJsonObject()
            ? parsed.getAsJsonObject() : new JsonObject();
      } catch (RuntimeException exception) {
        StarryListMod.LOGGER.warn(
            "Could not parse StarryList config; replacing it with merged defaults",
            exception
        );
        target = new JsonObject();
      }

      StarryListConfigData loaded = merge(target, removedBoardIds);
      StarryListMod.LOGGER.info("Loaded StarryList config from {}", CONFIG_PATH);
      return new LoadResult(loaded);
    }
  }

  private static StarryListConfigData merge(JsonObject root, Set<String> removedBoardIds) {
    StarryListConfigData defaults = StarryListConfigData.DEFAULT;
    JsonObject general = object(root, "general");
    JsonObject display = object(root, "display");
    JsonObject boards = object(root, "boards");
    JsonObject blacklist = object(root, "blacklist");

    String language = language(general, "language", defaults.general().language());
    int permission = integer(
        general,
        "adminPermissionLevel",
        defaults.general().adminPermissionLevel(),
        0,
        4
    );
    boolean hidden = bool(
        display, "hiddenByDefault", defaults.display().hiddenByDefault()
    );
    boolean rotation = bool(
        display, "rotationEnabled", defaults.display().rotationEnabled()
    );
    int interval = integer(
        display,
        "rotationIntervalSeconds",
        defaults.display().rotationIntervalSeconds(),
        1,
        3600
    );
    List<String> enabled = boardIds(
        display.get("enabledBoards"),
        defaults.display().enabledBoards(),
        removedBoardIds
    );
    List<String> disabled = boardIds(
        boards.get("disabledBoards"),
        defaults.boards().disabledBoards(),
        removedBoardIds
    );
    List<String> enabledScripts = scriptBoardIds(
        boards.get("enabledScriptBoards"),
        defaults.boards().enabledScriptBoards(),
        removedBoardIds
    );
    List<String> patterns = patterns(
        blacklist.get("playerNamePatterns"),
        defaults.blacklist().playerNamePatterns()
    );

    return new StarryListConfigData(
        new StarryListConfigData.General(language, permission),
        new StarryListConfigData.Display(hidden, rotation, interval, enabled),
        new StarryListConfigData.Boards(disabled, enabledScripts),
        new StarryListConfigData.Blacklist(patterns)
    );
  }

  private static JsonObject object(JsonObject parent, String key) {
    JsonElement value = parent.get(key);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
  }

  private static String language(
      JsonObject object,
      String key,
      String fallback
  ) {
    try {
      JsonElement value = object.get(key);
      if (value == null || !value.isJsonPrimitive()
          || !value.getAsJsonPrimitive().isString()) {
        return fallback;
      }
      String locale = value.getAsString().trim().toLowerCase(Locale.ROOT);
      Set<String> available = new HashSet<>(
          StarryListLangManager.getInstance().availableLocales()
      );
      available.addAll(
          com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance()
              .availableLocales()
      );
      if (!locale.matches("[a-z0-9][a-z0-9_-]*") || !available.contains(locale)) {
        StarryListMod.LOGGER.warn(
            "Unsupported StarryList language {}; using {}",
            locale,
            fallback
        );
        return fallback;
      }
      return locale;
    } catch (RuntimeException exception) {
      return fallback;
    }
  }

  private static boolean bool(JsonObject object, String key, boolean fallback) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive()
        && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
  }

  private static int integer(
      JsonObject object,
      String key,
      int fallback,
      int minimum,
      int maximum
  ) {
    try {
      JsonElement value = object.get(key);
      if (value == null || !value.isJsonPrimitive()
          || !value.getAsJsonPrimitive().isNumber()) {
        return fallback;
      }
      java.math.BigDecimal number = value.getAsBigDecimal().stripTrailingZeros();
      if (number.scale() > 0) {
        return fallback;
      }
      int result = number.intValueExact();
      return result >= minimum && result <= maximum ? result : fallback;
    } catch (ArithmeticException | NumberFormatException exception) {
      return fallback;
    }
  }

  private static List<String> boardIds(
      JsonElement value,
      List<String> fallback,
      Set<String> removedBoardIds
  ) {
    if (value == null || !value.isJsonArray()) {
      return fallback;
    }

    Set<String> known = new HashSet<>(StarryListBoardRegistry.getInstance().definitionIds());
    known.addAll(com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance()
        .previewIds());
    Set<String> accepted = new HashSet<>();
    for (JsonElement entry : value.getAsJsonArray()) {
      if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
        continue;
      }
      String id = entry.getAsString().trim().toLowerCase(Locale.ROOT);
      if (!id.isBlank() && known.contains(id) && !removedBoardIds.contains(id)) {
        accepted.add(id);
      }
    }

    return StarryListConfigData.normalizeIds(new ArrayList<>(accepted));
  }

  private static List<String> scriptBoardIds(
      JsonElement value,
      List<String> fallback,
      Set<String> removedBoardIds
  ) {
    Set<String> scripts = StarryListBoardRegistry.getInstance().definitions().stream()
        .filter(com.flwolfy.starrylist.board.script.StarryListScriptBoard.class::isInstance)
        .map(com.flwolfy.starrylist.board.base.StarryListBoard::id)
        .collect(java.util.stream.Collectors.toSet());
    scripts.addAll(com.flwolfy.starrylist.board.script.StarryListScriptManager.getInstance()
        .previewIds());
    return boardIds(value, fallback, removedBoardIds).stream()
        .filter(scripts::contains)
        .toList();
  }

  private static List<String> patterns(JsonElement value, List<String> fallback) {
    if (value == null || !value.isJsonArray()) {
      return fallback;
    }

    List<String> accepted = new ArrayList<>();
    for (JsonElement entry : value.getAsJsonArray()) {
      if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
        continue;
      }
      String expression = entry.getAsString();
      if (expression.isBlank()) {
        continue;
      }
      try {
        Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        accepted.add(expression);
      } catch (PatternSyntaxException ignored) {
      }
    }

    return List.copyOf(accepted);
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
        new StarryListConfigData.Boards(
            StarryListConfigData.normalizeIds(value.boards().disabledBoards()),
            StarryListConfigData.normalizeIds(value.boards().enabledScriptBoards())
        ),
        value.blacklist()
    );
  }

  private static void applyBoardLoading(StarryListConfigData value) {
    StarryListBoardRegistry registry = StarryListBoardRegistry.getInstance();
    Set<String> enabledScripts = Set.copyOf(value.boards().enabledScriptBoards());
    Set<String> scriptIds = registry.definitions().stream()
        .filter(com.flwolfy.starrylist.board.script.StarryListScriptBoard.class::isInstance)
        .map(com.flwolfy.starrylist.board.base.StarryListBoard::id)
        .collect(java.util.stream.Collectors.toSet());
    Set<String> disabled = value.boards().disabledBoards().stream()
        .filter(id -> !scriptIds.contains(id))
        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    scriptIds.stream().filter(id -> !enabledScripts.contains(id)).forEach(disabled::add);
    registry.applyDisabledBoards(disabled);
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

  private record LoadResult(StarryListConfigData data) {}
}
