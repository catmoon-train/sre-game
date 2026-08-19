package net.exmo.sreGame.game;

import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public interface MiniGame {
   String id();

   String displayName();

   String icon();

   int minPlayers();

   int maxPlayers();

   void openSetup(ServerPlayer host, GameRoom room);

   boolean canStart(GameRoom room, ServerPlayer actor);

   void start(GameRoom room, ServerPlayer actor);
}
