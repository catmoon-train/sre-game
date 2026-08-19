package net.exmo.sreGame.mixin;

import java.util.function.Predicate;
import net.exmo.sreGame.room.RoomChat;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class RoomChatMixin {
   @Inject(
      method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
      at = @At("HEAD")
   )
   private void sre$beginRoomChat(PlayerChatMessage message, Predicate<ServerPlayer> filter, ServerPlayer sender, ChatType.Bound bound, CallbackInfo ci) {
      RoomChat.begin(sender);
   }

   @Inject(
      method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
      at = @At("RETURN")
   )
   private void sre$endRoomChat(PlayerChatMessage message, Predicate<ServerPlayer> filter, ServerPlayer sender, ChatType.Bound bound, CallbackInfo ci) {
      RoomChat.end();
   }
}
