package com.flwolfy.starrylist.data.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StarryListStateTest {

  @Test
  void travelRemainderCombinesCentimetersIntoWholeBlocks() {
    StarryListState state = new StarryListState();
    UUID player = UUID.randomUUID();
    assertEquals(0, state.addTravel(player, 50));
    assertEquals(1, state.addTravel(player, 50));
    assertEquals(2, state.addTravel(player, 225));
    assertEquals(1, state.addTravel(player, 75));
  }

  @Test
  void defaultProfileIsRepresentedByNoOverride() {
    StarryListState state = new StarryListState();
    UUID player = UUID.randomUUID();
    StarryListDisplayProfile custom = new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        List.of("mining"),
        false,
        30
    );
    state.setProfile(player, custom);
    assertEquals(custom, state.profile(player));
    state.setProfile(player, StarryListDisplayProfile.defaultProfile());
    assertEquals(StarryListDisplayProfile.defaultProfile(), state.profile(player));
  }
}
