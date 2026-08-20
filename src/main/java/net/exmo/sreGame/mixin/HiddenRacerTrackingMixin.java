package net.exmo.sreGame.mixin;

import net.exmo.sreGame.games.chicken.ChickenHorseVisibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class HiddenRacerTrackingMixin {
   @Shadow
   @Final
   Entity entity;

   @Shadow
   public abstract void removePlayer(ServerPlayer player);

   @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
   private void sre$hideOtherRacers(ServerPlayer player, CallbackInfo ci) {
      if (ChickenHorseVisibility.hiddenFrom(player, this.entity)) {
         this.removePlayer(player);
         ci.cancel();
      }
   }
}
