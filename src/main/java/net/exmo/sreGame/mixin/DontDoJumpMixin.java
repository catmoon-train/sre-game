package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DontDoJumpMixin {
   @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
   private void sre$dontDoJump(CallbackInfo ci) {
      if ((Object) this instanceof ServerPlayer player && SreGame.getContext() != null) {
         SreGame.getContext().dontDo().handleJump(player);
         SreGame.getContext().hypixelSays().handleJump(player);
         if (SreGame.getContext().partyGames().handleJump(player)) ci.cancel();
      }
   }
}
