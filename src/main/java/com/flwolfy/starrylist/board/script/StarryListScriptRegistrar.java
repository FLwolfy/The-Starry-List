package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import com.flwolfy.starrylist.display.StarryListSidebarManager;
import groovy.lang.Closure;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/** Services and generic reload-managed event registration exposed to Groovy boards. */
public final class StarryListScriptRegistrar {

  private final StarryListScriptBoard board;
  private final List<StarryListScriptSubscription> subscriptions = new ArrayList<>();

  StarryListScriptRegistrar(StarryListScriptBoard board) {
    this.board = board;
  }

  /**
   * Registers a reload-managed callback for a void-returning Fabric event. Scripts must use this
   * method instead of {@link Event#register(Object)} because Fabric events cannot remove callbacks;
   * the stable key lets StarryList replace or deactivate the callback across script reloads.
   *
   * @param key stable subscription key unique within the board
   * @param event Fabric event instance
   * @param callback Groovy event callback
   */
  public void listen(String key, Event<?> event, Closure<?> callback) {
    addSubscription(key, event, null, callback, true);
  }

  /**
   * Registers a reload-managed callback for a value-returning Fabric event. Scripts must use this
   * method instead of {@link Event#register(Object)} because Fabric events cannot remove callbacks;
   * the stable key lets StarryList replace or deactivate the callback across script reloads.
   *
   * @param key stable subscription key unique within the board
   * @param event Fabric event instance
   * @param inactiveResult value returned while the subscription is inactive
   * @param callback Groovy event callback
   */
  public void listen(
      String key,
      Event<?> event,
      Object inactiveResult,
      Closure<?> callback
  ) {
    addSubscription(key, event, inactiveResult, callback, false);
  }

  /**
   * Adds an automatically collected statistic for this board.
   *
   * @param player the player whose score changes
   * @param delta the amount to add
   * @return the resulting score
   */
  public int addAutomatic(ServerPlayer player, int delta) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime == null || runtime.registry().get(board.id()).isEmpty()
        ? 0 : runtime.scores().addAutomatic(board.id(), player, delta);
  }

  /**
   * Checks whether a player is blacklisted from automatic scoring.
   *
   * @param player the player to check
   * @return whether the player is blacklisted
   */
  public boolean isBlacklisted(ServerPlayer player) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime != null && runtime.scores().isBlacklisted(player);
  }

  /**
   * Resolves a player's current effective StarryList display settings.
   *
   * @param player the player to inspect
   * @return effective visibility, boards, rotation, and interval
   */
  public StarryListSidebarManager.EffectiveDisplay display(ServerPlayer player) {
    return runtime().display().effective(player.getUUID());
  }

  /**
   * Checks whether this board is enabled in a player's effective display settings.
   *
   * @param player the player to inspect
   * @return whether this board is enabled
   */
  public boolean isEnabled(ServerPlayer player) {
    return display(player).boards().contains(board.id());
  }

  /**
   * Returns this board's persistent state for a player.
   *
   * @param player the owning player
   * @return the board-specific persistent state
   */
  public StarryListBoardState state(ServerPlayer player) {
    return state(player.getUUID());
  }

  /**
   * Returns this board's persistent state for a player UUID.
   *
   * @param playerId the owning player UUID
   * @return the board-specific persistent state
   */
  public StarryListBoardState state(UUID playerId) {
    return runtime().state().boardState(board.id(), playerId);
  }

  /**
   * Resolves the active StarryList runtime.
   *
   * @return the active runtime
   * @throws IllegalStateException if no server is active
   */
  public StarryListRuntime runtime() {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    if (runtime == null) {
      throw new IllegalStateException("StarryList runtime is not active");
    }

    return runtime;
  }

  /**
   * Resolves the active Minecraft server.
   *
   * @return the active server
   * @throws IllegalStateException if no server is active
   */
  public MinecraftServer server() {
    return runtime().server();
  }

  /**
   * Accumulates sub-units in this board's persistent player state.
   *
   * @param player the player whose state changes
   * @param key the remainder state key
   * @param amount the sub-units to add
   * @param unitsPerWhole the sub-units in one whole unit
   * @return completed whole units
   */
  public int accumulate(ServerPlayer player, String key, int amount, int unitsPerWhole) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    if (runtime == null || runtime.registry().get(board.id()).isEmpty()
        || amount <= 0 || unitsPerWhole <= 0 || isBlacklisted(player)) {
      return 0;
    }

    return state(player).accumulate(key, amount, unitsPerWhole);
  }

  List<StarryListScriptSubscription> subscriptions() {
    return List.copyOf(subscriptions);
  }

  private void addSubscription(
      String key,
      Event<?> event,
      Object inactiveResult,
      Closure<?> callback,
      boolean requireVoid
  ) {
    if (key == null || !key.matches("[a-z0-9_.-]+")) {
      throw new IllegalArgumentException("Invalid subscription key in " + board.id() + ": " + key);
    }
    if (event == null || callback == null) {
      throw new IllegalArgumentException("Event and callback are required for " + board.id());
    }
    if (subscriptions.stream().anyMatch(value -> value.key().equals(key))) {
      throw new IllegalArgumentException("Duplicate subscription key " + board.id() + "/" + key);
    }

    Object invoker = event.invoker();
    Class<?> callbackType = findCallbackType(invoker);
    Method callbackMethod = findCallbackMethod(callbackType);
    if (requireVoid && callbackMethod.getReturnType() != Void.TYPE) {
      throw new IllegalArgumentException(
          "Event " + callbackType.getName() + " requires an inactive result"
      );
    }
    if (!requireVoid && callbackMethod.getReturnType() == Void.TYPE) {
      throw new IllegalArgumentException(
          "Void event " + callbackType.getName() + " must use listen(key, event, closure)"
      );
    }

    Object fallback = inactiveResult;
    if (!requireVoid) {
      if (inactiveResult == null && callbackMethod.getReturnType().isPrimitive()) {
        throw new IllegalArgumentException(
            "Primitive event " + callbackType.getName() + " requires a non-null inactive result"
        );
      }
      try {
        fallback = DefaultTypeTransformation.castToType(
            inactiveResult, callbackMethod.getReturnType()
        );
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException(
            "Inactive result is incompatible with " + callbackMethod.getReturnType().getName(),
            exception
        );
      }
    }

    subscriptions.add(new StarryListScriptSubscription(
        board.id(), key, event, callbackType, callbackMethod, fallback, callback
    ));
  }

  private static Class<?> findCallbackType(Object invoker) {
    List<Class<?>> candidates = java.util.Arrays.stream(invoker.getClass().getInterfaces())
        .filter(type -> type != java.io.Serializable.class)
        .toList();
    if (candidates.size() != 1) {
      throw new IllegalArgumentException(
          "Could not infer one Fabric callback interface from " + invoker.getClass().getName()
      );
    }

    return candidates.getFirst();
  }

  private static Method findCallbackMethod(Class<?> callbackType) {
    List<Method> methods = java.util.Arrays.stream(callbackType.getMethods())
        .filter(method -> Modifier.isAbstract(method.getModifiers()))
        .filter(method -> method.getDeclaringClass() != Object.class)
        .toList();
    if (methods.size() != 1) {
      throw new IllegalArgumentException(
          "Fabric callback must be a functional interface: " + callbackType.getName()
      );
    }

    return methods.getFirst();
  }
}
