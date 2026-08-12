package com.flwolfy.starrylist.board.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flwolfy.starrylist.board.base.StarryListBoardPresentation;
import groovy.lang.Closure;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

public final class StarryListScriptRegistrarTest {

  @Test
  void setAutomaticReturnsZeroWithoutRuntime() {
    StarryListScriptRegistrar registrar = new StarryListScriptRegistrar(new TestBoard());
    assertEquals(0, registrar.setAutomatic(null, 42));
  }

  @Test
  void registersOneLazyLifecyclePair() {
    StarryListScriptRegistrar registrar = new StarryListScriptRegistrar(new TestBoard());
    AtomicInteger activations = new AtomicInteger();
    AtomicInteger deactivations = new AtomicInteger();

    registrar.onActiveStateChanged(
        callback(activations::incrementAndGet),
        callback(deactivations::incrementAndGet)
    );

    assertEquals(0, activations.get());
    assertEquals(0, deactivations.get());
    assertEquals(1, registrar.lifecycles().size());
    assertThrows(IllegalStateException.class, () -> registrar.onActiveStateChanged(
        callback(() -> {}), callback(() -> {})
    ));
  }

  private static Closure<?> callback(Runnable action) {
    return new Closure<Object>(null) {
      public Object doCall() {
        action.run();
        return null;
      }
    };
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
