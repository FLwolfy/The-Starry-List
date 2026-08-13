package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.board.base.StarryListBoardPresentation;
import groovy.lang.GroovyClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.codehaus.groovy.control.CompilerConfiguration;

final class StarryListScriptCompiler {

  private static final String ENGLISH = "en_us";
  private static final String LOCALE_PATTERN = "[a-z0-9][a-z0-9_-]*";

  private final Path directory = FabricLoader.getInstance().getConfigDir()
      .resolve("starrylist")
      .resolve("boards");
  private final StarryListScriptLanguageLoader languageLoader =
      new StarryListScriptLanguageLoader();

  Path directory() {
    return directory;
  }

  StarryListScriptSnapshot compile(boolean validateIcons) {
    createDirectory();
    StarryListScriptLanguageCatalog languageCatalog = languageLoader.load();
    GroovyClassLoader loader = newLoader();

    try {
      List<Path> sources = sources();
      List<StarryListScriptBoard> boards = new ArrayList<>();
      for (Path source : sources) {
        boards.add(compileOne(loader, source, validateIcons, languageCatalog));
      }

      List<StarryListScriptSubscription> subscriptions = new ArrayList<>();
      List<StarryListScriptLifecycle> lifecycles = new ArrayList<>();
      Map<String, Map<String, String>> translations = new HashMap<>();
      for (StarryListScriptBoard board : boards) {
        addTranslations(board, translations);
        StarryListScriptRegistrar registrar = new StarryListScriptRegistrar(board);
        board.subscribe(registrar);
        subscriptions.addAll(registrar.subscriptions());
        lifecycles.addAll(registrar.lifecycles());
      }

      return new StarryListScriptSnapshot(
          loader, boards, subscriptions, lifecycles, translations, languageCatalog.locales()
      );
    } catch (Throwable throwable) {
      try {
        loader.clearCache();
        loader.close();
      } catch (IOException closeFailure) {
        throwable.addSuppressed(closeFailure);
      }

      throw new IllegalStateException("Failed to compile StarryList scripts", throwable);
    }
  }

  List<Inspection> inspect(boolean validateIcons) {
    createDirectory();
    StarryListScriptLanguageCatalog languageCatalog = languageLoader.load();
    List<Inspection> result = new ArrayList<>();
    try {
      for (Path source : sources()) {
        GroovyClassLoader loader = newLoader();
        StarryListScriptSnapshot snapshot = null;
        try {
          StarryListScriptBoard board = compileOne(
              loader, source, validateIcons, languageCatalog
          );
          Map<String, Map<String, String>> translations = new HashMap<>();
          addTranslations(board, translations);
          StarryListScriptRegistrar registrar = new StarryListScriptRegistrar(board);
          board.subscribe(registrar);
          snapshot = new StarryListScriptSnapshot(
              loader,
              List.of(board),
              registrar.subscriptions(),
              registrar.lifecycles(),
              translations,
              languageCatalog.locales()
          );
          result.add(new Inspection(
              source.getFileName().toString(), snapshot, languageCatalog.locales(), null
          ));
        } catch (Throwable throwable) {
          close(loader, throwable);
          result.add(new Inspection(
              source.getFileName().toString(),
              null,
              languageCatalog.locales(),
              rootMessage(throwable)
          ));
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to inspect StarryList scripts", exception);
    }

    return List.copyOf(result);
  }

  private static GroovyClassLoader newLoader() {
    CompilerConfiguration configuration = new CompilerConfiguration();
    configuration.addCompilationCustomizers(new StarryListScriptCompilationCustomizer());
    return new GroovyClassLoader(StarryListScriptBoard.class.getClassLoader(), configuration);
  }

  private static void close(GroovyClassLoader loader, Throwable throwable) {
    try {
      loader.clearCache();
      loader.close();
    } catch (IOException closeFailure) {
      throwable.addSuppressed(closeFailure);
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

  private void createDirectory() {
    try {
      Files.createDirectories(directory);
      Path defaultBoard = directory.resolve("ore.groovy");
      if (Files.notExists(defaultBoard)) {
        try (InputStream source = StarryListScriptCompiler.class.getResourceAsStream(
            "/assets/the-starry-list/script/ore.groovy"
        )) {
          if (source == null) {
            throw new IOException("Missing bundled default ore board");
          }

          Files.copy(source, defaultBoard);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create script directory " + directory, exception);
    }
  }

  private List<Path> sources() throws IOException {
    try (var paths = Files.list(directory)) {
      return paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".groovy"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
    }
  }

  private static StarryListScriptBoard compileOne(
      GroovyClassLoader loader,
      Path source,
      boolean validateIcon,
      StarryListScriptLanguageCatalog languageCatalog
  )
      throws ReflectiveOperationException, IOException {
    Set<Class<?>> before = Arrays.stream(loader.getLoadedClasses())
        .map(type -> (Class<?>) type)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    loader.parseClass(source.toFile());
    List<Class<?>> boardClasses = new ArrayList<>();
    for (Class<?> type : loader.getLoadedClasses()) {
      if (!before.contains(type)
          && type != StarryListScriptBoard.class
          && StarryListScriptBoard.class.isAssignableFrom(type)
          && !Modifier.isAbstract(type.getModifiers())) {
        boardClasses.add(type);
      }
    }
    if (boardClasses.size() != 1) {
      throw new IllegalStateException(
          source.getFileName() + " must contain exactly one concrete StarryListScriptBoard"
      );
    }

    Class<?> boardClass = boardClasses.getFirst();
    if (!Modifier.isPublic(boardClass.getModifiers())) {
      throw new IllegalStateException("Script board must be public: " + boardClass.getName());
    }
    Constructor<?> constructor = boardClass.getDeclaredConstructor();
    if (!Modifier.isPublic(constructor.getModifiers())) {
      throw new IllegalStateException(
          "Script board must have a public no-argument constructor: " + boardClass.getName()
      );
    }

    StarryListScriptBoard board = (StarryListScriptBoard) constructor.newInstance();
    board.bindSourceFile(source.getFileName().toString());
    board.bindLanguageCatalog(languageCatalog);
    if (validateIcon && (board.icon() == null || board.icon().isEmpty())) {
      throw new IllegalStateException("Script board has an empty icon: " + source.getFileName());
    }

    return board;
  }

  private static void addTranslations(
      StarryListScriptBoard board,
      Map<String, Map<String, String>> translations
  ) {
    Map<?, ?> provided = board.translations();
    if (provided == null || provided.isEmpty()) {
      throw new IllegalStateException("Script board has no translations: " + board.sourceFile());
    }

    Map<String, StarryListBoardPresentation> normalized = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : provided.entrySet()) {
      if (!(entry.getKey() instanceof CharSequence localeValue)
          || !(entry.getValue() instanceof StarryListBoardPresentation presentation)) {
        throw new IllegalStateException(
            "Invalid translation entry in " + board.sourceFile()
        );
      }

      String locale = localeValue.toString().toLowerCase(Locale.ROOT);
      if (!locale.matches(LOCALE_PATTERN)) {
        throw new IllegalStateException(
            "Invalid translation locale " + locale + " in " + board.sourceFile()
        );
      }
      if (normalized.put(locale, presentation) != null) {
        throw new IllegalStateException(
            "Duplicate translation locale " + locale + " in " + board.sourceFile()
        );
      }
    }

    StarryListBoardPresentation english = normalized.get(ENGLISH);
    if (english == null) {
      throw new IllegalStateException("Missing en_us translation in " + board.sourceFile());
    }
    board.bindLoreLines(english.lore().size());
    for (Map.Entry<String, StarryListBoardPresentation> entry : normalized.entrySet()) {
      if (entry.getValue().lore().size() != english.lore().size()) {
        throw new IllegalStateException(
            "Every locale must provide " + english.lore().size() + " lore lines in "
                + board.sourceFile()
        );
      }

      Map<String, String> locale = translations.computeIfAbsent(
          entry.getKey(), ignored -> new LinkedHashMap<>()
      );
      String prefix = "starrylist.script." + board.id();
      putUnique(locale, prefix + ".title", entry.getValue().title(), board.sourceFile());
      for (int index = 0; index < entry.getValue().lore().size(); index++) {
        putUnique(
            locale,
            prefix + ".lore." + index,
            entry.getValue().lore().get(index),
            board.sourceFile()
        );
      }
    }
  }

  private static void putUnique(
      Map<String, String> translations,
      String key,
      String value,
      String source
  ) {
    if (translations.put(key, value) != null) {
      throw new IllegalStateException("Duplicate translation " + key + " from " + source);
    }
  }

  record Inspection(
      String sourceFile,
      StarryListScriptSnapshot snapshot,
      Set<String> locales,
      String error
  ) {
  }
}
