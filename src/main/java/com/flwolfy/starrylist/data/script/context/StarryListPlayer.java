package com.flwolfy.starrylist.data.script.context;

import net.minecraft.server.level.ServerPlayer;

public record StarryListPlayer(String uuid, String name) {

  public static StarryListPlayer from(ServerPlayer player) {
    return new StarryListPlayer(player.getUUID().toString(), player.getGameProfile().name());
  }
}
