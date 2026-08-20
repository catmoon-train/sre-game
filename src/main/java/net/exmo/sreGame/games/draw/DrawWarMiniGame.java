package net.exmo.sreGame.games.draw;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.BuildWarSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class DrawWarMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "draw_war";
   private final GameContext ctx;

   public DrawWarMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "绘画战争";
   }

   @Override
   public String icon() {
      return "map";
   }

   @Override
   public int minPlayers() {
      return 3;
   }

   @Override
   public int maxPlayers() {
      return 20;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      BuildWarSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (this.ctx.words().isEmpty() && room.resolvedWords(this.ctx).isEmpty()) {
         this.ctx.send(actor, "&c词库为空。请在设置里导入词库，或让 OP 用 &f/sregame words &c添加。");
         return false;
      }
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c绘画战争需要 &f" + this.minPlayers() + "–" + this.maxPlayers() + " &c人（当前 &f" + room.size() + "&c）。");
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
      UUID matchId = this.ctx.buildWar().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有足够的画布场地（最多同时 20 格）。");
      }
   }
}
