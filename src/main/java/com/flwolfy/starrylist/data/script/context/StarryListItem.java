package com.flwolfy.starrylist.data.script.context;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public record StarryListItem(
    String id,
    int count,
    int damage,
    String customName
) {

  public static StarryListItem from(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return new StarryListItem("minecraft:air", 0, 0, "");
    return new StarryListItem(
        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
        stack.getCount(),
        stack.getDamageValue(),
        stack.getCustomName() == null ? "" : stack.getCustomName().getString()
    );
  }
}
