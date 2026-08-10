package com.flwolfy.starrylist.data.script.context;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record StarryListPosition(double x, double y, double z, String dimension) {

  public static StarryListPosition from(ServerPlayer player) {
    return new StarryListPosition(
        player.getX(), player.getY(), player.getZ(), player.level().dimension().identifier().toString()
    );
  }

  public static StarryListPosition from(Level level, BlockPos position) {
    return new StarryListPosition(
        position.getX(), position.getY(), position.getZ(), level.dimension().identifier().toString()
    );
  }
}
