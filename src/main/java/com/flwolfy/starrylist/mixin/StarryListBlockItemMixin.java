package com.flwolfy.starrylist.mixin;

import com.flwolfy.starrylist.StarryListMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts exactly one successful BlockItem placement action. */
@Mixin(BlockItem.class)
abstract class StarryListBlockItemMixin {

  @Inject(method = "place", at = @At("RETURN"))
  private void starryList$afterPlace(
      BlockPlaceContext context,
      CallbackInfoReturnable<InteractionResult> callback
  ) {
    if (!callback.getReturnValue().consumesAction()
        || !(context.getPlayer() instanceof ServerPlayer player)
        || context.getLevel().isClientSide()) {
      return;
    }
    StarryListMod.onBlockPlaced(player);
  }
}
