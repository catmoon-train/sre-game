package net.exmo.sreGame.mixin;

import net.exmo.sreGame.util.MatchGuard;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MatchDropLockMixin {
   @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
   private void sre$noHotbarDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
      if (MatchGuard.lockItemDrops((ServerPlayer) (Object) this)) {
         cir.setReturnValue(false);
      }
   }
}
