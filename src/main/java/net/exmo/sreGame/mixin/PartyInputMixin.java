package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures server-authoritative inputs which Fabric's interaction callbacks do not expose. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PartyInputMixin {
   @Shadow public ServerPlayer player;

   @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
   private void sre$partyHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
      var context = SreGame.getContext();
      if (context != null && player != null) {
         context.partyGames().handleHotbar(player, player.getInventory().selected, packet.getSlot());
      }
   }

   @Inject(method = "handlePlayerCommand", at = @At("HEAD"))
   private void sre$partySneak(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
      var context = SreGame.getContext();
      if (context == null || player == null) return;
      if (packet.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY) {
         context.partyGames().handleSneak(player, true);
      } else if (packet.getAction() == ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY) {
         context.partyGames().handleSneak(player, false);
      }
   }
}
