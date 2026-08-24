package net.exmo.sreGame.games.fillinthewall;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.fillinthewall.gui.FillWallSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class FillInTheWallMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "fill_in_the_wall";

   private final GameContext ctx;

   public FillInTheWallMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "填墙游戏";
   }

   @Override
   public String icon() {
      return "blue_concrete";
   }

   @Override
   public int minPlayers() {
      return 1;
   }

   @Override
   public int maxPlayers() {
      return 32;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      FillWallSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < 1 || room.size() > 32) {
         this.ctx.send(actor, "&c填墙游戏需要 &f1–32 &c人（当前 &f" + room.size() + "&c）。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) {
            if (!room.isReady(uuid)) {
               waiting.add(this.ctx.name(uuid));
            }
         }
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      return true;
   }

   @Override
   public void start(GameRoom room, ServerPlayer actor) {
      UUID matchId = this.ctx.fillInTheWall().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的填墙游戏场地。");
      }
   }
}
