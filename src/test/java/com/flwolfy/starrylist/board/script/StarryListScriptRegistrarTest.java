package com.flwolfy.starrylist.board.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flwolfy.starrylist.board.base.StarryListBoardPresentation;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

public final class StarryListScriptRegistrarTest {

  @Test
  void setAutomaticReturnsZeroWithoutRuntime() {
    StarryListScriptRegistrar registrar = new StarryListScriptRegistrar(new TestBoard());
    assertEquals(0, registrar.setAutomatic(null, 42));
  }

  private static final class TestBoard extends StarryListScriptBoard {

    @Override
    public String id() {
      return "test";
    }

    @Override
    public String objectiveName() {
      return "sl_test";
    }

    @Override
    public int order() {
      return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack icon() {
      return null;
    }

    @Override
    public Map<String, StarryListBoardPresentation> translations() {
      return Map.of();
    }

    @Override
    public void subscribe(StarryListScriptRegistrar registrar) {
    }
  }
}
