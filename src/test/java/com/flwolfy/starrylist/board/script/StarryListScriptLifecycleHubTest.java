package com.flwolfy.starrylist.board.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public final class StarryListScriptLifecycleHubTest {

  @Test
  void activatesAndDeactivatesOnlyOnStateTransitions() {
    List<String> calls = new ArrayList<>();
    StarryListScriptLifecycleHub hub = new StarryListScriptLifecycleHub();
    hub.commit(List.of(lifecycle("board", calls, "on", "off")));

    hub.setActiveBoards(Set.of("board"));
    hub.setActiveBoards(Set.of("board"));
    hub.setActiveBoards(Set.of());
    hub.setActiveBoards(Set.of());

    assertEquals(List.of("on", "off"), calls);
  }

  @Test
  void cleansOldLifecycleBeforeActivatingReplacement() {
    List<String> calls = new ArrayList<>();
    StarryListScriptLifecycleHub hub = new StarryListScriptLifecycleHub();
    hub.commit(List.of(lifecycle("board", calls, "old-on", "old-off")));
    hub.setActiveBoards(Set.of("board"));

    hub.commit(List.of(lifecycle("board", calls, "new-on", "new-off")));
    hub.commit(List.of());

    assertEquals(List.of("old-on", "old-off", "new-on", "new-off"), calls);
  }

  @Test
  void rejectsDuplicateBoardLifecycles() {
    StarryListScriptLifecycleHub hub = new StarryListScriptLifecycleHub();
    StarryListScriptLifecycle lifecycle = new StarryListScriptLifecycle(
        "board", () -> {}, () -> {}
    );

    assertThrows(IllegalStateException.class, () -> hub.validate(List.of(
        lifecycle, lifecycle
    )));
  }

  private static StarryListScriptLifecycle lifecycle(
      String boardId,
      List<String> calls,
      String activated,
      String deactivated
  ) {
    return new StarryListScriptLifecycle(
        boardId,
        () -> calls.add(activated),
        () -> calls.add(deactivated)
    );
  }
}
