package com.flwolfy.starrylist.mixin;

import com.flwolfy.starrylist.StarryListMod;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converts vanilla-confirmed continuous movement statistic increments into travel events. */
@Mixin(ServerPlayer.class)
abstract class StarryListServerPlayerMixin {

  private static final Set<String> STARRY_LIST$MOVEMENT_STATS = Set.of(
      "walk_one_cm",
      "crouch_one_cm",
      "sprint_one_cm",
      "walk_on_water_one_cm",
      "walk_under_water_one_cm",
      "swim_one_cm",
      "climb_one_cm",
      "fall_one_cm",
      "fly_one_cm",
      "aviate_one_cm",
      "minecart_one_cm",
      "boat_one_cm",
      "pig_one_cm",
      "horse_one_cm",
      "strider_one_cm",
      "happy_ghast_one_cm",
      "nautilus_one_cm"
  );

  @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
  private void starryList$awardMovementStat(Stat<?> stat, int amount, CallbackInfo callback) {
    if (amount <= 0 || stat.getType() != Stats.CUSTOM || !(stat.getValue() instanceof Identifier id)) {
      return;
    }
    if (STARRY_LIST$MOVEMENT_STATS.contains(id.getPath())) {
      StarryListMod.onTravel((ServerPlayer) (Object) this, amount);
    }
  }
}
