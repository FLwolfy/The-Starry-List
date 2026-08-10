package com.flwolfy.starrylist.data.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flwolfy.starrylist.data.script.context.StarryListPlayer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StarryListScriptManagerTest {

  private final StarryListScriptManager scripts = StarryListScriptManager.getInstance();

  @Test
  void evaluatesPositiveZeroAndNegativeIntegers() {
    assertEquals(5, evaluate("2 + 3"));
    assertEquals(0, evaluate("0"));
    assertEquals(-7, evaluate("-7"));
  }

  @Test
  void exposesMathNamespace() {
    assertEquals(9, evaluate("math:max(4, 9)"));
  }

  @Test
  void exposesAllowlistedRecordContextMethods() {
    int result = scripts.evaluate(
        new StarryListScript("player.name() == 'Alex' ? 1 : 0"),
        Map.of(),
        Map.of("player", new StarryListPlayer("uuid", "Alex"))
    );
    assertEquals(1, result);
  }

  @Test
  void rejectsFractionalOverflowAndWrongTypes() {
    assertThrows(IllegalArgumentException.class, () -> evaluate("1.5"));
    assertThrows(IllegalArgumentException.class, () -> evaluate("2147483648"));
    assertThrows(IllegalArgumentException.class, () -> evaluate("'not-a-number'"));
  }

  @Test
  void validationCompilesWithoutExecutingNamespaces() {
    assertTrue(scripts.validate(new StarryListScript("shell:runInt('never executed')")));
    assertFalse(scripts.validate(new StarryListScript("if (")));
  }

  private int evaluate(String source) {
    return scripts.evaluate(new StarryListScript(source), Map.of(), Map.of());
  }
}
