package net.exmo.sreGame.room;

import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;

public final class RoomChat {
   private static final ThreadLocal<ServerPlayer> SENDER = new ThreadLocal<>();

   private RoomChat() {
   }

   public static void begin(ServerPlayer sender) {
      SENDER.set(sender);
   }

   public static void end() {
      SENDER.remove();
   }

   public static boolean shouldSendTo(ServerPlayer viewer) {
      ServerPlayer sender = SENDER.get();
      if (sender == null || viewer == null) {
         return true;
      }
      return canSee(viewer, sender);
   }

   public static boolean canSee(ServerPlayer viewer, ServerPlayer sender) {
      if (viewer == sender) {
         return true;
      }
      GameContext ctx = SreGame.getContext();
      if (ctx == null) {
         return true;
      }
      GameRoom viewRoom = ctx.rooms().getByPlayer(viewer.getUUID());
      GameRoom sendRoom = ctx.rooms().getByPlayer(sender.getUUID());
      if (viewRoom != null && viewRoom == sendRoom) {
         if (ctx != null && !ctx.fakeHuman().sameChatZone(viewer, sender)) {
            return false;
         }
         return true;
      }
      if (!leaks(sendRoom)) {
         return false;
      }
      return listensOutside(viewRoom);
   }

   private static boolean leaks(GameRoom room) {
      return room == null || room.chatMode() == RoomChatMode.ALL;
   }

   private static boolean listensOutside(GameRoom room) {
      if (room == null) {
         return true;
      }
      return room.chatMode() != RoomChatMode.ROOM_ONLY;
   }
}
