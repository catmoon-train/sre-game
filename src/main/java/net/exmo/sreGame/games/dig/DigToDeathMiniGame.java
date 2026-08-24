package net.exmo.sreGame.games.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.DigToDeathSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class DigToDeathMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "dig_to_death";
   private final GameContext ctx;

   public DigToDeathMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "掘一死战";
   }

   @Override
   public String icon() {
      return "snow_block";
   }

   @Override
   public int minPlayers() {
      return 2;
   }

   @Override
   public int maxPlayers() {
      return 64;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      DigToDeathSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c掘一死战需要 &f" + this.minPlayers() + "–" + this.maxPlayers()
            + " &c人（当前 &f" + room.size() + "&c）。");
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
      UUID matchId = this.ctx.digToDeath().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的掘一死战场地。");
      }
   }
}
