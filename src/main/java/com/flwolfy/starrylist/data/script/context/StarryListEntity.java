package com.flwolfy.starrylist.data.script.context;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public record StarryListEntity(String uuid, String type, String name) {

  public static StarryListEntity from(Entity entity) {
    if (entity == null) return null;
    return new StarryListEntity(
        entity.getUUID().toString(),
        EntityType.getKey(entity.getType()).toString(),
        entity.getName().getString()
    );
  }
}
