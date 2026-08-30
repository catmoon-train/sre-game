package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DodgeballSwingMixin {
   @Shadow
   public ServerPlayer player;

   @Inject(method = "handleAnimate", at = @At("TAIL"))
   private void sre$dodgeballCatch(ServerboundSwingPacket packet, CallbackInfo ci) {
      if (packet.getHand() != InteractionHand.MAIN_HAND || this.player == null) {
         return;
      }
      var ctx = SreGame.getContext();
      if (ctx != null) {
         ctx.dodgeball().handleSwing(this.player);
         ctx.football().handleSwing(this.player);
         ctx.partyGames().handleLeftClick(this.player);
      }
   }
}
