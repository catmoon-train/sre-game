package net.exmo.sreGame.mixin;

import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.wifi.starrailexpress.game.GameUtils", remap = false)
public abstract class SreForceKillMixin {
   @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
   private static void sre$chickenHorseKill(Player victim, boolean spawnBody, Player killer,
      ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
      if (!(victim instanceof ServerPlayer player)) {
         return;
      }
      GameContext ctx = SreGame.getContext();
      if (ctx == null || !ctx.chickenHorse().isPlaying(player)) {
         return;
      }
      player.hurt(player.damageSources().magic(), 8.0F);
      ci.cancel();
   }
}
