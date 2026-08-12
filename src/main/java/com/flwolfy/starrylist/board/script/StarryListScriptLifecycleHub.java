package com.flwolfy.starrylist.board.script;

import com.flwolfy.starrylist.StarryListMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns reload-safe activation and cleanup callbacks for Groovy boards. */
final class StarryListScriptLifecycleHub {

  private final Map<String, Slot> slots = new LinkedHashMap<>();
  private Set<String> activeBoardIds = Set.of();

  synchronized void validate(List<StarryListScriptLifecycle> lifecycles) {
    index(lifecycles);
  }

  synchronized void commit(List<StarryListScriptLifecycle> lifecycles) {
    Map<String, StarryListScriptLifecycle> staged = index(lifecycles);
    slots.values().forEach(Slot::deactivate);
    slots.clear();

    staged.forEach((boardId, lifecycle) -> {
      Slot slot = new Slot(lifecycle);
      slots.put(boardId, slot);
      if (activeBoardIds.contains(boardId)) {
        slot.activate();
      }
    });
  }

  synchronized void setActiveBoards(Set<String> boardIds) {
    activeBoardIds = Set.copyOf(boardIds);
    slots.forEach((boardId, slot) -> slot.setActive(activeBoardIds.contains(boardId)));
  }

  private static Map<String, StarryListScriptLifecycle> index(
      List<StarryListScriptLifecycle> lifecycles
  ) {
    Map<String, StarryListScriptLifecycle> result = new LinkedHashMap<>();
    for (StarryListScriptLifecycle lifecycle : lifecycles) {
      if (result.put(lifecycle.boardId(), lifecycle) != null) {
        throw new IllegalStateException(
            "Duplicate script lifecycle for board " + lifecycle.boardId()
        );
      }
    }
    return result;
  }

  private static final class Slot {

    private final StarryListScriptLifecycle lifecycle;
    private boolean active;

    private Slot(StarryListScriptLifecycle lifecycle) {
      this.lifecycle = lifecycle;
    }

    private void setActive(boolean replacement) {
      if (replacement) {
        activate();
      } else {
        deactivate();
      }
    }

    private void activate() {
      if (active) {
        return;
      }
      active = true;
      invoke("activate", lifecycle.activated());
    }

    private void deactivate() {
      if (!active) {
        return;
      }
      active = false;
      invoke("deactivate", lifecycle.deactivated());
    }

    private void invoke(String action, Runnable callback) {
      try {
        callback.run();
      } catch (Throwable throwable) {
        StarryListMod.LOGGER.error(
            "Failed to {} Groovy board lifecycle {}", action, lifecycle.boardId(), throwable
        );
      }
    }
  }
}
