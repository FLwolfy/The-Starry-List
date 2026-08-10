package com.flwolfy.starrylist.data.script.context;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

public record StarryListBlock(String id, Map<String, String> properties) {

  public StarryListBlock {
    properties = Map.copyOf(properties);
  }

  public static StarryListBlock from(BlockState state) {
    Map<String, String> properties = new LinkedHashMap<>();
    state.getValues().forEach(value ->
        properties.put(value.property().getName(), value.valueName())
    );
    return new StarryListBlock(
        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
        properties
    );
  }
}
