package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class LuckyPillarFishingMixin {
   @Shadow
   private int nibble;

   @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
   private void sreGame$luckyPillarCatch(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
      if (this.nibble <= 0) {
         return;
      }
      FishingHook self = (FishingHook) (Object) this;
      if (!(self.getPlayerOwner() instanceof ServerPlayer player)) {
         return;
      }
      var ctx = SreGame.getContext();
      if (ctx == null || !ctx.luckyPillar().handleFishingCatch(player)) {
         return;
      }
      self.discard();
      cir.setReturnValue(1);
   }
}
