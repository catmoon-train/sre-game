package net.exmo.sreGame.mixin;

import net.exmo.sreGame.room.RoomChat;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class RoomChatSendMixin {
   @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
   private void sre$filterRoomChat(OutgoingChatMessage message, boolean filtered, ChatType.Bound bound, CallbackInfo ci) {
      if (!RoomChat.shouldSendTo((ServerPlayer) (Object) this)) {
         ci.cancel();
      }
   }
}
