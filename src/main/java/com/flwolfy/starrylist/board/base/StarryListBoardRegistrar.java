package com.flwolfy.starrylist.board.base;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.state.StarryListBoardState;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime-safe services bound to one discovered board. */
public final class StarryListBoardRegistrar {

  private final StarryListBoard board;
  private final Set<String> subscriptionKeys = new LinkedHashSet<>();
  private volatile boolean active = true;
  private Runnable activated = () -> {};
  private Runnable deactivated = () -> {};
  private boolean lifecycleRegistered;

  StarryListBoardRegistrar(StarryListBoard board) {
    this.board = board;
  }

  /**
   * Registers one permanent proxy for a void-returning Fabric event.
   *
   * <p>Fabric events cannot unregister callbacks. Built-in boards must therefore use this method
   * instead of calling {@link Event#register(Object)} directly: the proxy is installed only once
   * and enables or suppresses its delegate as the board is hot-enabled or hot-disabled.</p>
   *
   * @param key stable subscription key unique within this board
   * @param event Fabric event instance
   * @param callback callback invoked only while this board is active
   * @param <T> Fabric callback interface
   */
  public <T> void listen(String key, Event<T> event, T callback) {
    listenInternal(key, event, null, callback, true);
  }

  /**
   * Registers one permanent proxy for a value-returning Fabric event.
   *
   * <p>Fabric events cannot unregister callbacks. Built-in boards must therefore use this method
   * instead of calling {@link Event#register(Object)} directly. While the board is disabled, the
   * permanent proxy returns {@code inactiveResult} without invoking the board callback.</p>
   *
   * @param key stable subscription key unique within this board
   * @param event Fabric event instance
   * @param inactiveResult value returned while this board is inactive
   * @param callback callback invoked only while this board is active
   * @param <T> Fabric callback interface
   */
  public <T> void listen(String key, Event<T> event, Object inactiveResult, T callback) {
    listenInternal(key, event, inactiveResult, callback, false);
  }

  /**
   * Registers cleanup and reconstruction actions for board loading-state changes.
   *
   * @param onActivated action run after the board enters the active catalog
   * @param onDeactivated action run after the board leaves the active catalog
   * @throws IllegalStateException if lifecycle actions were already registered for this board
   */
  public synchronized void onActiveStateChanged(
      Runnable onActivated,
      Runnable onDeactivated
  ) {
    if (lifecycleRegistered) {
      throw new IllegalStateException("Board lifecycle already registered for " + board.id());
    }
    if (onActivated == null || onDeactivated == null) {
      throw new IllegalArgumentException("Board lifecycle actions cannot be null");
    }

    activated = onActivated;
    deactivated = onDeactivated;
    lifecycleRegistered = true;
  }

  /**
   * Returns whether this board is currently loaded in the active catalog.
   *
   * @return whether managed event callbacks are enabled
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Adds an automatically collected statistic while respecting blacklist and overflow rules.
   *
   * @param player the player whose score changes
   * @param delta the amount to add
   * @return the resulting score, or zero when no runtime is active
   */
  public int addAutomatic(ServerPlayer player, int delta) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime == null || runtime.registry().get(board.id()).isEmpty()
        ? 0 : runtime.scores().addAutomatic(board.id(), player, delta);
  }

  /**
   * Checks whether a player is excluded from automatic board processing.
   *
   * @param player the player to check
   * @return whether the player is blacklisted
   */
  public boolean isBlacklisted(ServerPlayer player) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    return runtime != null && runtime.scores().isBlacklisted(player);
  }

  /**
   * Returns this board's persistent state namespace for a player.
   *
   * @param player the owning player
   * @return the board-specific state
   */
  public StarryListBoardState state(ServerPlayer player) {
    return state(player.getUUID());
  }

  /**
   * Returns this board's persistent state namespace for a player UUID.
   *
   * @param playerId the owning player UUID
   * @return the board-specific state
   */
  public StarryListBoardState state(UUID playerId) {
    return runtime().state().boardState(board.id(), playerId);
  }

  /**
   * Resolves the active world-bound runtime.
   *
   * @return the active runtime
   * @throws IllegalStateException if no server runtime is active
   */
  public StarryListRuntime runtime() {
    return requireRuntime();
  }

  /**
   * Resolves the active server without retaining it during mod initialization.
   *
   * @return the active server
   * @throws IllegalStateException if no server runtime is active
   */
  public MinecraftServer server() {
    return runtime().server();
  }

  /**
   * Accumulates positive sub-units in persistent board state and returns completed whole units.
   *
   * @param player the player whose state changes
   * @param key the board-state key that stores the remainder
   * @param amount the number of sub-units to add
   * @param unitsPerWhole the number of sub-units in one whole unit
   * @return the number of completed whole units
   */
  public int accumulate(ServerPlayer player, String key, int amount, int unitsPerWhole) {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    if (runtime == null || runtime.registry().get(board.id()).isEmpty()
        || amount <= 0 || unitsPerWhole <= 0 || isBlacklisted(player)) {
      return 0;
    }

    return state(player).accumulate(key, amount, unitsPerWhole);
  }

  synchronized void setActive(boolean replacement) {
    if (active == replacement) {
      return;
    }

    active = replacement;
    try {
      (replacement ? activated : deactivated).run();
    } catch (RuntimeException exception) {
      StarryListMod.LOGGER.error(
          "Failed to apply active state {} for board {}",
          replacement,
          board.id(),
          exception
      );
    }
  }

  @SuppressWarnings("unchecked")
  private synchronized <T> void listenInternal(
      String key,
      Event<T> event,
      Object inactiveResult,
      T callback,
      boolean requireVoid
  ) {
    if (key == null || !key.matches("[a-z0-9_.-]+")) {
      throw new IllegalArgumentException("Invalid subscription key in " + board.id() + ": " + key);
    }
    if (event == null || callback == null) {
      throw new IllegalArgumentException("Event and callback are required for " + board.id());
    }
    if (!subscriptionKeys.add(key)) {
      throw new IllegalArgumentException("Duplicate subscription key " + board.id() + "/" + key);
    }

    Class<?> callbackType = findCallbackType(event.invoker());
    Method callbackMethod = findCallbackMethod(callbackType);
    if (!callbackType.isInstance(callback)) {
      throw new IllegalArgumentException(
          "Callback does not implement " + callbackType.getName() + " for " + board.id()
      );
    }
    if (requireVoid && callbackMethod.getReturnType() != Void.TYPE) {
      throw new IllegalArgumentException(
          "Event " + callbackType.getName() + " requires an inactive result"
      );
    }
    if (!requireVoid && callbackMethod.getReturnType() == Void.TYPE) {
      throw new IllegalArgumentException(
          "Void event " + callbackType.getName() + " must use listen(key, event, callback)"
      );
    }
    if (!requireVoid && !compatible(callbackMethod.getReturnType(), inactiveResult)) {
      throw new IllegalArgumentException(
          "Inactive result is incompatible with " + callbackMethod.getReturnType().getName()
      );
    }

    Object proxy = Proxy.newProxyInstance(
        callbackType.getClassLoader(),
        new Class<?>[]{callbackType},
        (ignored, method, arguments) -> {
          if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxyDescription(key), ignored, method, arguments);
          }
          if (!active) {
            return inactiveResult;
          }

          try {
            return method.invoke(callback, arguments);
          } catch (InvocationTargetException exception) {
            throw exception.getCause();
          }
        }
    );
    event.register((T) proxy);
  }

  private String proxyDescription(String key) {
    return "StarryList board event proxy " + board.id() + "/" + key;
  }

  private static Object objectMethod(
      String description,
      Object proxy,
      Method method,
      Object[] arguments
  ) {
    return switch (method.getName()) {
      case "toString" -> description;
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
      default -> null;
    };
  }

  private static Class<?> findCallbackType(Object invoker) {
    var candidates = java.util.Arrays.stream(invoker.getClass().getInterfaces())
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
    var methods = java.util.Arrays.stream(callbackType.getMethods())
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

  private static boolean compatible(Class<?> returnType, Object value) {
    if (!returnType.isPrimitive()) {
      return value == null || returnType.isInstance(value);
    }
    if (value == null) {
      return false;
    }

    return switch (returnType.getName()) {
      case "boolean" -> value instanceof Boolean;
      case "byte" -> value instanceof Byte;
      case "short" -> value instanceof Short;
      case "int" -> value instanceof Integer;
      case "long" -> value instanceof Long;
      case "float" -> value instanceof Float;
      case "double" -> value instanceof Double;
      case "char" -> value instanceof Character;
      default -> false;
    };
  }

  private static StarryListRuntime requireRuntime() {
    StarryListRuntime runtime = StarryListMod.getRuntime();
    if (runtime == null) {
      throw new IllegalStateException("StarryList runtime is not active");
    }

    return runtime;
  }
}
