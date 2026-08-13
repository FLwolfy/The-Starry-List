package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.StarryListMod;
import groovy.lang.Closure;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.event.Event;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

final class StarryListScriptEventHub {

  private final Map<String, Slot> slots = new LinkedHashMap<>();
  private Set<String> activeBoardIds = Set.of();

  /**
   * Validates that subscriptions can replace the currently installed delegates.
   *
   * @param subscriptions complete staged subscription set
   */
  synchronized void validate(List<StarryListScriptSubscription> subscriptions) {
    Map<String, StarryListScriptSubscription> staged = index(subscriptions);
    for (Map.Entry<String, StarryListScriptSubscription> entry : staged.entrySet()) {
      Slot existing = slots.get(entry.getKey());
      if (existing != null) {
        existing.validateCompatible(entry.getValue());
      }
    }
  }

  /**
   * Installs new permanent proxies as needed and replaces all active delegates.
   *
   * @param subscriptions complete validated subscription set
   */
  synchronized void commit(List<StarryListScriptSubscription> subscriptions) {
    Map<String, StarryListScriptSubscription> staged = index(subscriptions);
    validate(subscriptions);

    for (Slot slot : slots.values()) {
      slot.disable();
    }
    for (Map.Entry<String, StarryListScriptSubscription> entry : staged.entrySet()) {
      Slot slot = slots.get(entry.getKey());
      if (slot == null) {
        slot = new Slot(entry.getValue());
        register(entry.getValue().event(), slot.proxy());
        slots.put(entry.getKey(), slot);
      }

      slot.install(entry.getValue(), activeBoardIds.contains(entry.getValue().boardId()));
    }
  }

  synchronized void setActiveBoards(Set<String> boardIds) {
    activeBoardIds = Set.copyOf(boardIds);
    slots.values().forEach(slot -> slot.setBoardActive(activeBoardIds.contains(slot.boardId())));
  }

  synchronized void suspend() {
    slots.values().forEach(slot -> slot.setBoardActive(false));
  }

  /**
   * Returns immutable status information for every permanent subscription slot.
   *
   * @return subscription status list
   */
  synchronized List<SubscriptionStatus> statuses() {
    return slots.entrySet().stream()
        .map(entry -> new SubscriptionStatus(entry.getKey(), entry.getValue().isActive()))
        .toList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void register(Event<?> event, Object callback) {
    ((Event) event).register(callback);
  }

  private static Map<String, StarryListScriptSubscription> index(
      List<StarryListScriptSubscription> subscriptions
  ) {
    Map<String, StarryListScriptSubscription> result = new LinkedHashMap<>();
    for (StarryListScriptSubscription subscription : subscriptions) {
      StarryListScriptSubscription previous = result.put(subscription.identity(), subscription);
      if (previous != null) {
        throw new IllegalStateException("Duplicate script subscription " + subscription.identity());
      }
    }

    return result;
  }

  /**
   * Describes whether one reload-managed event subscription is active.
   *
   * @param identity stable board and subscription identity
   * @param active whether a Groovy delegate is currently active
   */
  record SubscriptionStatus(String identity, boolean active) {}

  private static final class Slot implements InvocationHandler {

    private final Event<?> event;
    private final Class<?> callbackType;
    private final Method callbackMethod;
    private final Object inactiveResult;
    private final AtomicReference<Closure<?>> delegate = new AtomicReference<>();
    private final Object proxy;
    private String identity;
    private String boardId;
    private Closure<?> installed;
    private volatile boolean failed;

    private Slot(StarryListScriptSubscription subscription) {
      event = subscription.event();
      callbackType = subscription.callbackType();
      callbackMethod = subscription.callbackMethod();
      inactiveResult = subscription.inactiveResult();
      identity = subscription.identity();
      boardId = subscription.boardId();
      proxy = Proxy.newProxyInstance(
          callbackType.getClassLoader(),
          new Class<?>[]{callbackType},
          this
      );
    }

    private Object proxy() {
      return proxy;
    }

    private boolean isActive() {
      return delegate.get() != null;
    }

    private void validateCompatible(StarryListScriptSubscription subscription) {
      if (event != subscription.event()
          || callbackType != subscription.callbackType()
          || !callbackMethod.equals(subscription.callbackMethod())) {
        throw new IllegalStateException(
            "Subscription " + subscription.identity()
                + " changed its Fabric event; use a new subscription key"
        );
      }
      if (!java.util.Objects.equals(inactiveResult, subscription.inactiveResult())) {
        throw new IllegalStateException(
            "Subscription " + subscription.identity()
                + " changed its inactive result; use a new subscription key"
        );
      }
    }

    private String boardId() {
      return boardId;
    }

    private void install(StarryListScriptSubscription subscription, boolean boardActive) {
      identity = subscription.identity();
      boardId = subscription.boardId();
      installed = subscription.callback();
      failed = false;
      delegate.set(boardActive ? installed : null);
    }

    private void setBoardActive(boolean active) {
      delegate.set(active && !failed ? installed : null);
    }

    private void disable() {
      delegate.set(null);
      installed = null;
      failed = false;
    }

    @Override
    public Object invoke(Object ignored, Method method, Object[] arguments) {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "toString" -> "StarryList script event proxy " + identity;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == arguments[0];
          default -> null;
        };
      }

      Closure<?> active = delegate.get();
      if (active == null) {
        return inactiveResult;
      }

      try {
        Object result = active.call(arguments == null ? new Object[0] : arguments);
        if (callbackMethod.getReturnType() == Void.TYPE) {
          return null;
        }

        return DefaultTypeTransformation.castToType(result, callbackMethod.getReturnType());
      } catch (Throwable throwable) {
        failed = true;
        delegate.compareAndSet(active, null);
        StarryListMod.LOGGER.error(
            "Disabled failing script subscription {} until the next successful reload",
            identity,
            throwable
        );
        return inactiveResult;
      }
    }
  }
}
