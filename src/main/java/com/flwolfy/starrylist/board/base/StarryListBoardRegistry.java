package com.flwolfy.starrylist.board.base;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Discovers, validates and owns the immutable process-wide board catalog. */
public final class StarryListBoardRegistry {

  private static final String PACKAGE_NAME = "com.flwolfy.starrylist.board";
  private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/');
  private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]+");
  private static final Pattern OBJECTIVE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
  private static volatile StarryListBoardRegistry instance;

  private final List<StarryListBoard> builtInBoards;
  private final Map<String, StarryListBoardRegistrar> registrars;
  private volatile Catalog definitions;
  private volatile Catalog catalog;
  private volatile Set<String> disabledBoardIds = Set.of();
  private volatile long revision;
  private boolean registered;

  private StarryListBoardRegistry() {
    List<StarryListBoard> discovered = discover();
    discovered.sort(Comparator.comparingInt(StarryListBoard::order)
        .thenComparing(StarryListBoard::id));
    validate(discovered);
    builtInBoards = List.copyOf(discovered);

    Map<String, StarryListBoard> ids = new LinkedHashMap<>();
    Map<String, StarryListBoardRegistrar> handles = new HashMap<>();
    for (StarryListBoard board : builtInBoards) {
      ids.put(board.id(), board);
      handles.put(board.id(), new StarryListBoardRegistrar(board));
    }

    registrars = Map.copyOf(handles);
    definitions = new Catalog(builtInBoards, Map.copyOf(ids));
    catalog = definitions;

    StarryListMod.LOGGER.info(
        "Discovered {} StarryList boards: {}",
        builtInBoards.size(),
        builtInBoards.stream().map(StarryListBoard::id).toList()
    );
  }

  /**
   * Returns the lazily discovered process-wide board registry.
   *
   * @return the board registry
   */
  public static StarryListBoardRegistry getInstance() {
    StarryListBoardRegistry current = instance;
    if (current != null) {
      return current;
    }

    synchronized (StarryListBoardRegistry.class) {
      if (instance == null) {
        instance = new StarryListBoardRegistry();
      }

      return instance;
    }
  }

  /** Registers every board collector exactly once. */
  public synchronized void registerAll() {
    if (registered) {
      return;
    }

    for (StarryListBoard board : builtInBoards) {
      try {
        ((StarryListBoardCollector) board).register(registrars.get(board.id()));
      } catch (RuntimeException | LinkageError exception) {
        throw new IllegalStateException("Failed to register board " + board.id()
            + " (" + board.getClass().getName() + ")", exception);
      }
    }

    registered = true;
  }

  /**
   * Finds a board by its case-insensitive identifier.
   *
   * @param id the board identifier
   * @return the matching board, if present
   */
  public Optional<StarryListBoard> get(String id) {
    if (id == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(catalog.byId().get(id.trim().toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns all boards in canonical order.
   *
   * @return the immutable board list
   */
  public List<StarryListBoard> all() {
    return catalog.boards();
  }

  /**
   * Returns all canonical board identifiers.
   *
   * @return the ordered identifiers
   */
  public List<String> ids() {
    return catalog.boards().stream().map(StarryListBoard::id).toList();
  }

  /**
   * Finds a discovered board regardless of whether it is currently loaded.
   *
   * @param id the board identifier
   * @return the matching discovered board, if present
   */
  public Optional<StarryListBoard> definition(String id) {
    if (id == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(definitions.byId().get(id.trim().toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns every discovered board in canonical order.
   *
   * @return immutable discovered board list
   */
  public List<StarryListBoard> definitions() {
    return definitions.boards();
  }

  /**
   * Returns every discovered board identifier in canonical order.
   *
   * @return immutable discovered identifier list
   */
  public List<String> definitionIds() {
    return definitions.boards().stream().map(StarryListBoard::id).toList();
  }

  /**
   * Checks whether the registry owns an objective name.
   *
   * @param objectiveName the objective name to check
   * @return whether a registered board owns the name
   */
  public boolean ownsObjective(String objectiveName) {
    return objectiveName != null && catalog.boards().stream()
        .anyMatch(board -> board.objectiveName().equals(objectiveName));
  }

  /**
   * Canonicalizes known identifiers and removes blanks, duplicates, and unknown values.
   *
   * @param ids the identifiers to normalize
   * @return known identifiers in canonical board order
   */
  public List<String> normalizeIds(List<String> ids) {
    if (ids == null) {
      return List.of();
    }

    Set<String> requested = ids.stream()
        .filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toSet());
    return definitions.boards().stream().map(StarryListBoard::id)
        .filter(requested::contains).toList();
  }

  /**
   * Validates script boards against the immutable built-in catalog without changing it.
   *
   * @param scriptBoards complete proposed script board set
   */
  public void validateScriptBoards(List<? extends StarryListBoard> scriptBoards) {
    buildCatalog(scriptBoards);
  }

  /**
   * Atomically replaces the complete script board overlay.
   *
   * @param scriptBoards complete validated script board set
   */
  public synchronized void replaceScriptBoards(List<? extends StarryListBoard> scriptBoards) {
    definitions = buildCatalog(scriptBoards);
    catalog = filterActive(definitions, disabledBoardIds);
    revision++;
  }

  /**
   * Atomically applies the configured board-loading state.
   *
   * @param disabledIds discovered board identifiers to exclude from gameplay
   */
  public synchronized void applyDisabledBoards(Set<String> disabledIds) {
    Set<String> replacement = disabledIds == null ? Set.of() : Set.copyOf(disabledIds);
    Catalog active = filterActive(definitions, replacement);
    if (replacement.equals(disabledBoardIds)
        && active.boards().equals(catalog.boards())) {
      return;
    }

    disabledBoardIds = replacement;
    catalog = active;
    registrars.forEach((id, registrar) -> registrar.setActive(!replacement.contains(id)));
    revision++;
  }

  /**
   * Returns the revision of the dynamic catalog.
   *
   * @return catalog revision incremented after every script commit
   */
  public long revision() {
    return revision;
  }

  private Catalog buildCatalog(List<? extends StarryListBoard> scriptBoards) {
    List<StarryListBoard> combined = new ArrayList<>(builtInBoards);
    combined.addAll(scriptBoards);
    combined.sort(Comparator.comparingInt(StarryListBoard::order)
        .thenComparing(StarryListBoard::id));
    validate(combined);

    Map<String, StarryListBoard> ids = new LinkedHashMap<>();
    for (StarryListBoard board : combined) {
      ids.put(board.id(), board);
    }

    return new Catalog(List.copyOf(combined), Map.copyOf(ids));
  }

  private static Catalog filterActive(Catalog source, Set<String> disabledIds) {
    List<StarryListBoard> boards = source.boards().stream()
        .filter(board -> !disabledIds.contains(board.id()))
        .toList();
    Map<String, StarryListBoard> ids = new LinkedHashMap<>();
    boards.forEach(board -> ids.put(board.id(), board));
    return new Catalog(boards, Map.copyOf(ids));
  }

  private static List<StarryListBoard> discover() {
    ModContainer mod = FabricLoader.getInstance().getModContainer(StarryListMod.MOD_ID)
        .orElseThrow(() -> new IllegalStateException("Could not resolve StarryList mod container"));
    Set<String> classNames = new LinkedHashSet<>();
    for (Path root : mod.getRootPaths()) {
      collectFromDirectory(root.resolve(PACKAGE_PATH), classNames);
    }

    // Loom's development classpath may expose resources and compiled classes as separate roots.
    // Anchor the fallback at the base class's own code root instead of enumerating the whole
    // classpath, preserving the current-mod-only extension boundary.
    ClassLoader loader = StarryListBoard.class.getClassLoader();
    URL ownClass = StarryListBoard.class.getResource("StarryListBoard.class");
    if (ownClass == null) {
      throw new IllegalStateException("Could not locate StarryList board code source");
    }
    collectFromResource(ownClass, classNames);

    List<StarryListBoard> result = new ArrayList<>();
    for (String className : classNames) {
      try {
        Class<?> candidate = Class.forName(className, false, loader);
        if (candidate == StarryListBoard.class
            || !StarryListBoard.class.isAssignableFrom(candidate)
            || Modifier.isAbstract(candidate.getModifiers())) {
          continue;
        }
        if (!StarryListBoardCollector.class.isAssignableFrom(candidate)) {
          throw new IllegalStateException(
              "Bundled Java board must implement StarryListBoardCollector: " + className
          );
        }
        if (!Modifier.isPublic(candidate.getModifiers())) {
          throw new IllegalStateException("Concrete board must be public: " + className);
        }
        Constructor<?> constructor = candidate.getDeclaredConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
          throw new IllegalStateException("Board constructor must be public: " + className);
        }
        result.add((StarryListBoard) constructor.newInstance());
      } catch (ReflectiveOperationException | LinkageError exception) {
        throw new IllegalStateException("Failed to instantiate board " + className, exception);
      }
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("No StarryList boards were discovered");
    }

    return result;
  }

  private static void collectFromResource(URL resource, Set<String> classNames) {
    try {
      switch (resource.getProtocol()) {
        case "file" -> {
          Path codeRoot = Path.of(resource.toURI()).getParent();
          int packageDepth = StarryListBoard.class.getPackageName().split("\\.").length;
          for (int depth = 0; depth < packageDepth; depth++) {
            if (codeRoot == null) {
              throw new IllegalStateException("Could not resolve StarryList classpath root");
            }
            codeRoot = codeRoot.getParent();
          }
          collectFromDirectory(codeRoot.resolve(PACKAGE_PATH), classNames);
        }
        case "jar" -> {
          JarURLConnection connection = (JarURLConnection) resource.openConnection();
          try (var jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
              String name = entries.nextElement().getName();
              if (name.startsWith(PACKAGE_PATH + "/")) {
                collectClassName(name, classNames);
              }
            }
          }
        }
        default -> StarryListMod.LOGGER.debug(
            "Ignoring unsupported StarryList board resource URL: {}", resource
        );
      }
    } catch (IOException | URISyntaxException exception) {
      throw new IllegalStateException("Failed to scan StarryList board resource " + resource,
          exception);
    }
  }

  private static void collectFromDirectory(Path packageRoot, Set<String> classNames) {
    if (!Files.isDirectory(packageRoot)) {
      return;
    }

    try (var paths = Files.walk(packageRoot)) {
      paths.filter(Files::isRegularFile)
          .map(packageRoot::relativize)
          .map(Path::toString)
          .map(value -> value.replace('\\', '/'))
          .map(value -> PACKAGE_PATH + "/" + value)
          .forEach(value -> collectClassName(value, classNames));
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to scan StarryList board directory " + packageRoot,
          exception);
    }
  }

  private static void collectClassName(String resourceName, Set<String> classNames) {
    if (!resourceName.endsWith(".class")) {
      return;
    }

    classNames.add(resourceName.substring(0, resourceName.length() - 6).replace('/', '.'));
  }

  private static void validate(List<? extends StarryListBoard> boards) {
    Set<String> ids = new LinkedHashSet<>();
    Set<String> objectives = new LinkedHashSet<>();
    Set<Integer> orders = new LinkedHashSet<>();
    for (StarryListBoard board : boards) {
      String id = board.id();
      String objective = board.objectiveName();
      if (id == null || id.length() > 64 || !ID_PATTERN.matcher(id).matches()) {
        throw new IllegalStateException("Invalid board ID in " + board.getClass().getName());
      }
      if (objective == null || objective.length() > 16
          || !OBJECTIVE_PATTERN.matcher(objective).matches()) {
        throw new IllegalStateException("Invalid objective name for board " + id);
      }
      if (board.order() < 0) {
        throw new IllegalStateException("Negative board order: " + id);
      }
      if (!ids.add(id)) {
        throw new IllegalStateException("Duplicate board ID: " + id);
      }
      if (!objectives.add(objective)) {
        throw new IllegalStateException("Duplicate board objective: " + objective);
      }
      if (!orders.add(board.order())) {
        throw new IllegalStateException("Duplicate board order: " + board.order());
      }
      if (board instanceof com.flwolfy.starrylist.board.script.StarryListScriptBoard) {
        continue;
      }
      for (String locale : StarryListLangManager.getInstance().coreLocales()) {
        try {
          if (board.presentationFor(locale) == null) {
            throw new IllegalArgumentException("null presentation");
          }
        } catch (RuntimeException exception) {
          throw new IllegalStateException(
              "Invalid " + locale + " presentation for board " + id,
              exception
          );
        }
      }
    }
  }

  private record Catalog(List<StarryListBoard> boards, Map<String, StarryListBoard> byId) {}
}
