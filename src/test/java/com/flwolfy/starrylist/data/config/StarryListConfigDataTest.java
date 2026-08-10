package com.flwolfy.starrylist.data.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.event.StarryListEventType;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class StarryListConfigDataTest {

  @Test
  void defaultsAreValidAndRoundTripThroughMapper() {
    StarryListConfigData defaults = StarryListConfigData.DEFAULT;
    assertTrue(defaults.validate().isEmpty());
    assertEquals(
        defaults,
        StarryListConfigMapper.unflattenData(
            StarryListConfigMapper.flattenData(defaults),
            StarryListConfigData.class
        )
    );
  }

  @Test
  void duplicateDefaultBoardsAreRejectedWithoutChangingOrder() {
    StarryListConfigData value = new StarryListConfigData(
        StarryListConfigData.DEFAULT.general(),
        new StarryListConfigData.Display(false, true, 20, List.of("mining", "mining")),
        List.of()
    );
    assertTrue(value.validate().contains("display.defaultBoards"));
  }

  @Test
  void enabledCustomBoardRequiresSourcesAndUniqueIdentity() {
    StarryListConfigData value = new StarryListConfigData(
        StarryListConfigData.DEFAULT.general(),
        StarryListConfigData.DEFAULT.display(),
        List.of(new StarryListConfigData.CustomBoard(
            "mining", "sl_custom", "Conflict", true, List.of()
        ))
    );
    assertTrue(value.validate().contains("customBoards[0].id"));
    assertTrue(value.validate().contains("customBoards[0].sources"));
  }

  @Test
  void scheduledSourcesMustUseSetAndAValidInterval() {
    StarryListConfigData.Source source = new StarryListConfigData.Source(
        StarryListEventType.SCHEDULED,
        StarryListConfigData.UpdateMode.ADD,
        0,
        new StarryListScript("1")
    );
    StarryListConfigData value = new StarryListConfigData(
        StarryListConfigData.DEFAULT.general(),
        StarryListConfigData.DEFAULT.display(),
        List.of(new StarryListConfigData.CustomBoard(
            "vip", "sl_vip", "VIP", true, List.of(source)
        ))
    );
    assertFalse(value.validate().isEmpty());
    assertTrue(value.validate().contains("customBoards[0].sources[0].update"));
    assertTrue(value.validate().contains("customBoards[0].sources[0].intervalSeconds"));
  }

  @Test
  void eventTriggersUseLowerSnakeCaseJson() {
    Gson gson = new Gson();
    assertEquals("\"block_break\"", gson.toJson(StarryListEventType.BLOCK_BREAK));
    assertEquals(
        StarryListEventType.SCHEDULED,
        gson.fromJson("\"scheduled\"", StarryListEventType.class)
    );
  }
}
