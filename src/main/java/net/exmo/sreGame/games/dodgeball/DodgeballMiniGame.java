package net.exmo.sreGame.games.dodgeball;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.dodgeball.gui.DodgeballSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class DodgeballMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "dodgeball";
   private final GameContext ctx;

   public DodgeballMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "躲避球";
   }

   @Override
   public String icon() {
      return "snowball";
   }

   @Override
   public int minPlayers() {
      return 2;
   }

   @Override
   public int maxPlayers() {
      return 16;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      DodgeballSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c躲避球需要 &f" + this.minPlayers() + "–" + this.maxPlayers()
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
      UUID matchId = this.ctx.dodgeball().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的躲避球场地。");
      }
   }
}
