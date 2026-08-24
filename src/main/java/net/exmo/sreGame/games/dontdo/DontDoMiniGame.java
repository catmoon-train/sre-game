package net.exmo.sreGame.games.dontdo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.DontDoSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class DontDoMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "dont_do";
   private final GameContext ctx;

   public DontDoMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "不要做挑战";
   }

   @Override
   public String icon() {
      return "diamond_pickaxe";
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
      DontDoSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      DontDoSettings settings = room.dontDoSettings();
      int min = settings.teams() ? settings.minPlayersForTeams() : this.minPlayers();
      if (room.size() < min || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c不要做挑战需要 &f" + min + "–" + this.maxPlayers()
            + " &c人（当前 &f" + room.size() + "&c）"
            + (settings.teams() ? "，组队至少两队。" : "。"));
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
      UUID matchId = this.ctx.dontDo().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的生存岛。");
      }
   }
}
