package com.flwolfy.starrylist.event;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.script.StarryListCommandExecutor;
import com.flwolfy.starrylist.data.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.script.context.StarryListContext;
import com.flwolfy.starrylist.data.script.context.StarryListPlayer;
import com.flwolfy.starrylist.data.script.context.StarryListPosition;
import com.flwolfy.starrylist.scoreboard.StarryListBoardDefinition;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import com.flwolfy.starrylist.scoreboard.StarryListScoreService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Routes immutable gameplay contexts to built-in and custom leaderboard sources. */
public final class StarryListEventDispatcher {

  private final MinecraftServer server;
  private final java.util.function.Supplier<StarryListBoardRegistry> registry;
  private final StarryListScoreService scores;
  private final AtomicLong generation = new AtomicLong();
  private final Set<String> pending = ConcurrentHashMap.newKeySet();
  private final Map<String, Thread> tasks = new ConcurrentHashMap<>();
  private final Map<String, Long> lastErrorLog = new ConcurrentHashMap<>();
  private long ticks;

  public StarryListEventDispatcher(
      MinecraftServer server,
      java.util.function.Supplier<StarryListBoardRegistry> registry,
      StarryListScoreService scores
  ) {
    this.server = server;
    this.registry = registry;
    this.scores = scores;
  }

  public void invalidate() {
    generation.incrementAndGet();
    StarryListScriptManager.getInstance().clearCache();
  }

  public void dispatch(
      StarryListEventType event,
      ServerPlayer player,
      StarryListContext context,
      String builtInBoard,
      int builtInDelta
  ) {
    if (builtInBoard != null && builtInDelta != 0) scores.add(builtInBoard, player, builtInDelta);
    for (StarryListBoardDefinition board : registry.get().forEvent(event)) {
      for (int index = 0; index < board.sources().size(); index++) {
        StarryListConfigData.Source source = board.sources().get(index);
        if (source.trigger() != event) continue;
        evaluateNow(board, source, index, player, context);
      }
    }
  }

  public void tickScheduled() {
    ticks++;
    for (StarryListBoardDefinition board : registry.get().enabled()) {
      if (board.builtIn()) continue;
      for (int index = 0; index < board.sources().size(); index++) {
        StarryListConfigData.Source source = board.sources().get(index);
        if (source.trigger() != StarryListEventType.SCHEDULED) continue;
        if (ticks % (source.intervalSeconds() * 20L) != 0) continue;
        int sourceIndex = index;
        server.getPlayerList().getPlayers().forEach(player ->
            evaluateScheduled(board, source, sourceIndex, player)
        );
      }
    }
  }

  public void refreshScheduled(ServerPlayer player) {
    for (StarryListBoardDefinition board : registry.get().enabled()) {
      if (board.builtIn()) continue;
      for (int index = 0; index < board.sources().size(); index++) {
        StarryListConfigData.Source source = board.sources().get(index);
        if (source.trigger() == StarryListEventType.SCHEDULED) {
          evaluateScheduled(board, source, index, player);
        }
      }
    }
  }

  private void evaluateNow(
      StarryListBoardDefinition board,
      StarryListConfigData.Source source,
      int sourceIndex,
      ServerPlayer player,
      StarryListContext context
  ) {
    try {
      int previous = scores.get(board.id(), player.getUUID());
      int result = evaluate(board, source, player, context, previous);
      apply(board, source, player, result);
    } catch (RuntimeException exception) {
      logError(board, sourceIndex, exception);
    }
  }

  private void evaluateScheduled(
      StarryListBoardDefinition board,
      StarryListConfigData.Source source,
      int sourceIndex,
      ServerPlayer player
  ) {
    String key = board.id() + ":" + sourceIndex + ":" + player.getUUID();
    if (!pending.add(key)) return;
    long expectedGeneration = generation.get();
    StarryListPlayer playerSnapshot = StarryListPlayer.from(player);
    StarryListContext context = StarryListContext.scheduled(
        new StarryListContext.Presence(StarryListPosition.from(player))
    );
    int previous = scores.get(board.id(), player.getUUID());
    Thread task = Thread.ofVirtual().unstarted(() -> {
      try {
        int result = evaluate(board, source, playerSnapshot, context, previous);
        server.execute(() -> {
          try {
            if (generation.get() != expectedGeneration) return;
            ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
            if (online != null) apply(board, source, online, result);
          } finally {
            pending.remove(key);
            tasks.remove(key);
          }
        });
      } catch (RuntimeException exception) {
        pending.remove(key);
        tasks.remove(key);
        logError(board, sourceIndex, exception);
      }
    });
    tasks.put(key, task);
    task.start();
  }

  /** Interrupts every unfinished scheduled script while the server is stopping. */
  public void shutdown() {
    generation.incrementAndGet();
    tasks.values().forEach(Thread::interrupt);
    tasks.clear();
    pending.clear();
  }

  private int evaluate(
      StarryListBoardDefinition board,
      StarryListConfigData.Source source,
      ServerPlayer player,
      StarryListContext context,
      int previous
  ) {
    return evaluate(board, source, StarryListPlayer.from(player), context, previous);
  }

  private int evaluate(
      StarryListBoardDefinition board,
      StarryListConfigData.Source source,
      StarryListPlayer player,
      StarryListContext context,
      int previous
  ) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("player", player);
    arguments.put("context", context);
    arguments.put("previousScore", previous);
    arguments.put("board", board.scriptView());
    arguments.put("now", System.currentTimeMillis());
    return StarryListScriptManager.getInstance().evaluate(
        source.script(),
        Map.of("minecraft", new StarryListCommandExecutor(server)),
        arguments
    );
  }

  private void apply(
      StarryListBoardDefinition board,
      StarryListConfigData.Source source,
      ServerPlayer player,
      int result
  ) {
    if (source.update() == StarryListConfigData.UpdateMode.ADD) {
      scores.add(board.id(), player, result);
    } else {
      scores.set(board.id(), player, result);
    }
  }

  private void logError(StarryListBoardDefinition board, int sourceIndex, RuntimeException exception) {
    String key = board.id() + ":" + sourceIndex;
    long now = System.currentTimeMillis();
    long previous = lastErrorLog.getOrDefault(key, 0L);
    if (now - previous >= 30_000L) {
      lastErrorLog.put(key, now);
      StarryListMod.LOGGER.error(
          "Custom board {} source {} failed; score unchanged",
          board.id(), sourceIndex, exception
      );
    }
  }
}
