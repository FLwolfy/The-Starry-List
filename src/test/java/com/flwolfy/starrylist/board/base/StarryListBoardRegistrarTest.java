package com.flwolfy.starrylist.board.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

public final class StarryListBoardRegistrarTest {

  @Test
  void setAutomaticReturnsZeroWithoutRuntime() {
    StarryListBoardRegistrar registrar = new StarryListBoardRegistrar(new TestBoard());
    assertEquals(0, registrar.setAutomatic(null, 42));
  }

  private static final class TestBoard extends StarryListBoard {

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
    public void register(StarryListBoardRegistrar registrar) {
    }
  }
}
