package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles, validates, and transactionally activates trusted Groovy leaderboard scripts. */
public final class StarryListScriptManager {

  private static final StarryListScriptManager INSTANCE = new StarryListScriptManager();

  private final StarryListScriptCompiler compiler = new StarryListScriptCompiler();
  private final StarryListScriptEventHub eventHub = new StarryListScriptEventHub();
  private final StarryListScriptLifecycleHub lifecycleHub = new StarryListScriptLifecycleHub();
  private Set<String> activeBoardIds = Set.of();
  private StarryListScriptSnapshot current;
  private List<ScriptInspection> inspections = List.of();
  private Set<String> previewLocales = Set.of();
  private boolean previewed;

  private StarryListScriptManager() {}

  /**
   * Returns the process-wide script catalog manager.
   *
   * @return script manager
   */
  public static StarryListScriptManager getInstance() {
    return INSTANCE;
  }

  /**
   * Loads scripts before configuration and server services initialize.
   *
   * @param registry process-wide board registry
   */
  public synchronized void initialize(StarryListBoardRegistry registry) {
    if (current != null) {
      return;
    }

    StarryListScriptSnapshot staged = null;
    try {
      PartialPreparation preparation = preparePartial(registry, false);
      staged = preparation.snapshot();
      inspections = preparation.inspections();
      try {
        commitCatalog(registry, staged);
      } catch (RuntimeException exception) {
        commitCatalog(registry, null);
        throw exception;
      }
      current = staged;
      StarryListMod.LOGGER.info(
          "Loaded {} Groovy StarryList boards from {}; skipped {} invalid script(s)",
          staged.boards().size(),
          compiler.directory(),
          inspections.stream().filter(value -> !value.valid()).count()
      );
      inspections.stream().filter(value -> !value.valid()).forEach(value ->
          StarryListMod.LOGGER.error(
              "Skipped invalid StarryList script {}: {}",
              value.sourceFile(),
              value.message()
          )
      );
    } catch (RuntimeException exception) {
      closeQuietly(staged);
      StarryListMod.LOGGER.error(
          "Failed to load Groovy StarryList boards; using built-in boards only",
          exception
      );
    }
  }

  /**
   * Compiles and validates every script without changing the active catalog.
   *
   * @param registry process-wide board registry
   * @return validation result and concise diagnostic
   */
  public synchronized OperationResult validate(StarryListBoardRegistry registry) {
    try {
      PartialPreparation preparation = preparePartial(registry, true);
      inspections = preparation.inspections();
      previewLocales = preparation.snapshot().locales();
      closeQuietly(preparation.snapshot());
      List<ScriptInspection> failures = inspections.stream()
          .filter(value -> !value.valid())
          .toList();
      if (!failures.isEmpty()) {
        String diagnostic = failures.stream()
            .map(value -> value.sourceFile() + ": " + value.message())
            .collect(java.util.stream.Collectors.joining("; "));
        return new OperationResult(false, diagnostic);
      }

      return new OperationResult(
          true,
          "Validated " + inspections.size() + " script board(s)"
      );
    } catch (RuntimeException exception) {
      StarryListMod.LOGGER.error("StarryList script validation failed", exception);
      return new OperationResult(false, rootMessage(exception));
    }
  }

  /**
   * Refreshes per-file script diagnostics without changing the active script catalog.
   *
   * @param registry process-wide board registry
   * @param validateIcons whether item components are available for icon validation
   * @return preview result with per-file failures
   */
  public synchronized OperationResult preview(
      StarryListBoardRegistry registry,
      boolean validateIcons
  ) {
    PartialPreparation preparation = preparePartial(registry, validateIcons);
    inspections = preparation.inspections();
    previewLocales = preparation.snapshot().locales();
    previewed = true;
    closeQuietly(preparation.snapshot());
    List<ScriptInspection> failures = inspections.stream()
        .filter(value -> !value.valid())
        .toList();
    return new OperationResult(
        failures.isEmpty(),
        failures.isEmpty()
            ? "Inspected " + inspections.size() + " script board(s)"
            : failures.size() + " script file(s) failed validation"
    );
  }

  /**
   * Atomically replaces the script catalog and reconciles active server state.
   *
   * @param runtime active server services, or {@code null} before server startup
   * @return reload result and concise diagnostic
   */
  public synchronized OperationResult reload(StarryListRuntime runtime) {
    return reload(runtime, false);
  }

  /**
   * Reloads scripts and the JSON configuration within the same rollback boundary.
   *
   * @param runtime active server services
   * @return combined reload result and concise diagnostic
   */
  public synchronized OperationResult reloadAll(StarryListRuntime runtime) {
    return reload(runtime, true);
  }

  private OperationResult reload(StarryListRuntime runtime, boolean reloadConfig) {
    StarryListBoardRegistry registry = StarryListBoardRegistry.getInstance();
    StarryListScriptSnapshot staged = null;
    StarryListScriptSnapshot previous = current;
    StarryListConfigData previousConfig = StarryListConfigManager.getInstance().data();
    boolean catalogChanged = false;

    try {
      PartialPreparation preparation = preparePartial(registry, runtime != null);
      staged = preparation.snapshot();
      inspections = preparation.inspections();
      previewed = false;
      previewLocales = Set.of();
      List<StarryListScriptBoard> previousBoards = boards(previous);
      Set<String> removedIds = removedIds(previousBoards, staged.boards());
      List<StarryListScriptBoard> deactivated = deactivated(previousBoards, staged.boards());

      if (runtime != null) {
        runtime.deactivateScriptBoards(deactivated);
      }

      catalogChanged = true;
      commitCatalog(registry, staged);
      boolean configApplied = reloadConfig
          ? StarryListConfigManager.getInstance().reloadRemoving(removedIds)
          : StarryListConfigManager.getInstance().removeBoards(removedIds);
      if (!configApplied) {
        throw new IllegalStateException("Could not apply the server configuration");
      }
      if (runtime != null) {
        runtime.activateScriptCatalog(removedIds);
      }

      current = staged;
      closeQuietly(previous);
      long skipped = inspections.stream().filter(value -> !value.valid()).count();
      return new OperationResult(
          true,
          "Loaded " + staged.boards().size() + " script board(s), skipped " + skipped
      );
    } catch (RuntimeException exception) {
      if (catalogChanged) {
        try {
          commitCatalog(registry, previous);
          StarryListConfigManager.getInstance().update(previousConfig);
          if (runtime != null) {
            runtime.activateScriptCatalog(Set.of());
          }
        } catch (RuntimeException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
      }
      closeQuietly(staged);
      StarryListMod.LOGGER.error("StarryList script reload failed; kept previous scripts", exception);
      return new OperationResult(false, rootMessage(exception));
    }
  }

  /**
   * Returns information about currently active script boards and subscriptions.
   *
   * @return immutable script board information in catalog order
   */
  public synchronized List<ScriptInfo> list() {
    Map<String, Boolean> status = new HashMap<>();
    eventHub.statuses().forEach(value -> status.put(value.identity(), value.active()));
    List<ScriptInfo> result = new ArrayList<>();
    for (StarryListScriptBoard board : boards(current)) {
      List<StarryListScriptSubscription> subscriptions = current.subscriptions().stream()
          .filter(value -> value.boardId().equals(board.id()))
          .toList();
      long active = subscriptions.stream()
          .filter(value -> status.getOrDefault(value.identity(), false))
          .count();
      result.add(new ScriptInfo(
          board.sourceFile(),
          board.id(),
          board.objectiveName(),
          subscriptions.size(),
          Math.toIntExact(active)
      ));
    }

    return List.copyOf(result);
  }

  /**
   * Returns the most recent per-file script validation results.
   *
   * @return immutable script inspection results
   */
  public synchronized List<ScriptInspection> inspections() {
    return inspections;
  }

  /**
   * Returns whether the diagnostics represent an uncommitted Cloth Config preview.
   *
   * @return whether a preview refresh has been performed
   */
  public synchronized boolean hasPreview() {
    return previewed;
  }

  /**
   * Returns valid board identifiers discovered by the current uncommitted preview.
   *
   * @return valid preview board identifiers in source order
   */
  public synchronized List<String> previewIds() {
    if (!previewed) {
      return List.of();
    }

    return inspections.stream()
        .filter(ScriptInspection::valid)
        .map(ScriptInspection::id)
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  /**
   * Returns script locales available to the configuration editor.
   *
   * @return preview locales when available, otherwise active script locales
   */
  public synchronized Set<String> availableLocales() {
    if (previewed) {
      return previewLocales;
    }
    return current == null ? Set.of() : current.locales();
  }

  /**
   * Returns the directory scanned for enabled Groovy files.
   *
   * @return absolute script directory
   */
  public Path directory() {
    return compiler.directory();
  }

  /**
   * Validates active script icons after Minecraft item components are bound.
   */
  public synchronized void validateActiveIcons() {
    for (StarryListScriptBoard board : boards(current)) {
      board.iconForGui();
    }
  }

  /**
   * Enables event delegates only for script boards in the active gameplay catalog.
   *
   * @param activeBoardIds currently loaded board identifiers
   */
  public synchronized void applyActiveBoards(List<String> activeBoardIds) {
    Set<String> replacement = Set.copyOf(activeBoardIds);
    Set<String> retained = new java.util.HashSet<>(this.activeBoardIds);
    retained.retainAll(replacement);
    eventHub.setActiveBoards(retained);
    lifecycleHub.setActiveBoards(replacement);
    eventHub.setActiveBoards(replacement);
    this.activeBoardIds = replacement;
  }

  private PartialPreparation preparePartial(
      StarryListBoardRegistry registry,
      boolean validateIcons
  ) {
    StarryListScriptCompiler.InspectionBatch batch = compiler.inspect(validateIcons);
    List<StarryListScriptSnapshot> accepted = new ArrayList<>();
    List<ScriptInspection> results = new ArrayList<>();
    for (StarryListScriptCompiler.Inspection inspection : batch.inspections()) {
      StarryListScriptSnapshot candidate = inspection.snapshot();
      if (candidate == null) {
        results.add(new ScriptInspection(
            inspection.sourceFile(), null, null, Map.of(), false, inspection.error()
        ));
        continue;
      }

      StarryListScriptBoard board = candidate.boards().getFirst();
      try {
        List<StarryListBoard> proposedBoards = new ArrayList<>();
        List<StarryListScriptSubscription> proposedSubscriptions = new ArrayList<>();
        List<StarryListScriptLifecycle> proposedLifecycles = new ArrayList<>();
        accepted.forEach(snapshot -> {
          proposedBoards.addAll(snapshot.boards());
          proposedSubscriptions.addAll(snapshot.subscriptions());
          proposedLifecycles.addAll(snapshot.lifecycles());
        });
        proposedBoards.add(board);
        proposedSubscriptions.addAll(candidate.subscriptions());
        proposedLifecycles.addAll(candidate.lifecycles());
        registry.validateScriptBoards(proposedBoards);
        eventHub.validate(proposedSubscriptions);
        lifecycleHub.validate(proposedLifecycles);
        accepted.add(candidate);
        results.add(new ScriptInspection(
            inspection.sourceFile(), board.id(), board.objectiveName(),
            titles(candidate, board.id()), true, ""
        ));
      } catch (RuntimeException exception) {
        closeQuietly(candidate);
        results.add(new ScriptInspection(
            inspection.sourceFile(), board.id(), board.objectiveName(),
            titles(candidate, board.id()), false,
            rootMessage(exception)
        ));
      }
    }

    List<groovy.lang.GroovyClassLoader> loaders = new ArrayList<>();
    List<StarryListScriptBoard> boards = new ArrayList<>();
    List<StarryListScriptSubscription> subscriptions = new ArrayList<>();
    List<StarryListScriptLifecycle> lifecycles = new ArrayList<>();
    Map<String, Map<String, String>> translations = new HashMap<>();
    Set<String> locales = new LinkedHashSet<>(batch.locales());
    for (StarryListScriptSnapshot snapshot : accepted) {
      loaders.addAll(snapshot.classLoaders());
      boards.addAll(snapshot.boards());
      subscriptions.addAll(snapshot.subscriptions());
      lifecycles.addAll(snapshot.lifecycles());
      snapshot.translations().forEach((locale, values) ->
          translations.computeIfAbsent(locale, ignored -> new HashMap<>()).putAll(values)
      );
    }

    return new PartialPreparation(
        new StarryListScriptSnapshot(
            loaders, boards, subscriptions, lifecycles, translations, locales
        ),
        List.copyOf(results)
    );
  }

  private static Map<String, String> titles(
      StarryListScriptSnapshot snapshot,
      String boardId
  ) {
    String key = "starrylist.script." + boardId + ".title";
    Map<String, String> result = new HashMap<>();
    snapshot.translations().forEach((locale, translations) -> {
      String title = translations.get(key);
      if (title != null) {
        result.put(locale, title);
      }
    });
    return Map.copyOf(result);
  }

  private void commitCatalog(
      StarryListBoardRegistry registry,
      StarryListScriptSnapshot snapshot
  ) {
    List<StarryListScriptBoard> boards = boards(snapshot);
    registry.replaceScriptBoards(boards);
    StarryListLangManager.getInstance().replaceScriptTranslations(
        snapshot == null ? Map.of() : snapshot.translations(),
        snapshot == null ? Set.of() : snapshot.locales()
    );
    eventHub.suspend();
    lifecycleHub.commit(snapshot == null ? List.of() : snapshot.lifecycles());
    eventHub.commit(snapshot == null ? List.of() : snapshot.subscriptions());
  }

  private static List<StarryListScriptBoard> boards(StarryListScriptSnapshot snapshot) {
    return snapshot == null ? List.of() : snapshot.boards();
  }

  private static Set<String> removedIds(
      List<StarryListScriptBoard> previous,
      List<StarryListScriptBoard> replacement
  ) {
    Set<String> retained = replacement.stream()
        .map(StarryListBoard::id)
        .collect(java.util.stream.Collectors.toSet());
    return previous.stream()
        .map(StarryListBoard::id)
        .filter(id -> !retained.contains(id))
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private static List<StarryListScriptBoard> deactivated(
      List<StarryListScriptBoard> previous,
      List<StarryListScriptBoard> replacement
  ) {
    Map<String, StarryListScriptBoard> next = replacement.stream().collect(
        java.util.stream.Collectors.toMap(StarryListBoard::id, board -> board)
    );
    return previous.stream().filter(board -> {
      StarryListScriptBoard candidate = next.get(board.id());
      return candidate == null || !candidate.objectiveName().equals(board.objectiveName());
    }).toList();
  }

  private static void closeQuietly(StarryListScriptSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }

    try {
      snapshot.close();
    } catch (IOException exception) {
      StarryListMod.LOGGER.warn("Failed to close an old Groovy classloader", exception);
    }
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
  }

  /**
   * Result returned by script validation and reload commands.
   *
   * @param success whether the operation completed
   * @param message concise human-readable result
   */
  public record OperationResult(boolean success, String message) {}

  /**
   * Active script board metadata shown by the administrator list command.
   *
   * @param sourceFile source file name
   * @param id stable board identifier
   * @param objective objective name
   * @param subscriptions total declared subscriptions
   * @param activeSubscriptions currently active subscriptions
   */
  public record ScriptInfo(
      String sourceFile,
      String id,
      String objective,
      int subscriptions,
      int activeSubscriptions
  ) {}

  /**
   * Per-file result produced by the latest validation or refresh operation.
   *
   * @param sourceFile Groovy source file name
   * @param id board identifier when metadata could be read
   * @param objective objective name when metadata could be read
   * @param titles localized preview titles keyed by locale
   * @param valid whether compilation and safety validation succeeded
   * @param message validation diagnostic when invalid
   */
  public record ScriptInspection(
      String sourceFile,
      String id,
      String objective,
      Map<String, String> titles,
      boolean valid,
      String message
  ) {
    /**
     * Resolves the preview title for a locale with English and ID fallbacks.
     *
     * @param locale Minecraft locale key
     * @return localized preview title
     */
    public String title(String locale) {
      return titles.getOrDefault(locale, titles.getOrDefault("en_us", id == null ? sourceFile : id));
    }
  }

  private record PartialPreparation(
      StarryListScriptSnapshot snapshot,
      List<ScriptInspection> inspections
  ) {}
}
