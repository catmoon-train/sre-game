package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class DontDoDropMixin {
   @Inject(method = "drop(Z)Z", at = @At("RETURN"))
   private void sre$dontDoDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
      if (!Boolean.TRUE.equals(cir.getReturnValue())) {
         return;
      }
      ServerPlayer player = (ServerPlayer) (Object) this;
      if (SreGame.getContext() != null) {
         SreGame.getContext().dontDo().handleDrop(player);
      }
   }

   @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
   private void sre$dontDoDropStack(net.minecraft.world.item.ItemStack stack, boolean throwRandomly, boolean retainOwnership, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.item.ItemEntity> cir) {
      if (cir.getReturnValue() == null) {
         return;
      }
      ServerPlayer player = (ServerPlayer) (Object) this;
      if (SreGame.getContext() != null) {
         SreGame.getContext().dontDo().handleDrop(player);
      }
   }
}
