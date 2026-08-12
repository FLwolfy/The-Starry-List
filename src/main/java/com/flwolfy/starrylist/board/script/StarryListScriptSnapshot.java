package com.flwolfy.starrylist.board.script;

import groovy.lang.GroovyClassLoader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

final class StarryListScriptSnapshot implements AutoCloseable {

  private final List<GroovyClassLoader> classLoaders;
  private final List<StarryListScriptBoard> boards;
  private final List<StarryListScriptSubscription> subscriptions;
  private final List<StarryListScriptLifecycle> lifecycles;
  private final Map<String, Map<String, String>> translations;

  StarryListScriptSnapshot(
      GroovyClassLoader classLoader,
      List<StarryListScriptBoard> boards,
      List<StarryListScriptSubscription> subscriptions,
      List<StarryListScriptLifecycle> lifecycles,
      Map<String, Map<String, String>> translations
  ) {
    this(List.of(classLoader), boards, subscriptions, lifecycles, translations);
  }

  StarryListScriptSnapshot(
      List<GroovyClassLoader> classLoaders,
      List<StarryListScriptBoard> boards,
      List<StarryListScriptSubscription> subscriptions,
      List<StarryListScriptLifecycle> lifecycles,
      Map<String, Map<String, String>> translations
  ) {
    this.classLoaders = List.copyOf(classLoaders);
    this.boards = List.copyOf(boards);
    this.subscriptions = List.copyOf(subscriptions);
    this.lifecycles = List.copyOf(lifecycles);
    this.translations = translations.entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Map.copyOf(entry.getValue())
        )
    );
  }

  List<StarryListScriptBoard> boards() {
    return boards;
  }

  List<StarryListScriptSubscription> subscriptions() {
    return subscriptions;
  }

  List<StarryListScriptLifecycle> lifecycles() {
    return lifecycles;
  }

  Map<String, Map<String, String>> translations() {
    return translations;
  }

  List<GroovyClassLoader> classLoaders() {
    return classLoaders;
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    for (GroovyClassLoader classLoader : classLoaders) {
      try {
        classLoader.clearCache();
        classLoader.close();
      } catch (IOException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
