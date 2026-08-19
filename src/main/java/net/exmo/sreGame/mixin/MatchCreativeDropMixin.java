package net.exmo.sreGame.mixin;

import net.exmo.sreGame.util.MatchGuard;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MatchCreativeDropMixin {
   @Shadow
   public ServerPlayer player;

   @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
   private void sre$noCreativeDrop(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
      if (this.player == null) {
         return;
      }
      if (MatchGuard.lockCreativeInventory(this.player)) {
         ci.cancel();
         this.player.inventoryMenu.broadcastFullState();
         return;
      }
      if (!MatchGuard.inMinigame(this.player)) {
         return;
      }
      if (packet.slotNum() == -1) {
         ci.cancel();
         this.player.inventoryMenu.broadcastFullState();
      }
   }
}
