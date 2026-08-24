package net.exmo.sreGame.games.pillarpummel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.pillarpummel.gui.PummelSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class PillarPummelMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "pillar_pummel";
   private final GameContext ctx;

   public PillarPummelMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "柱联壁合";
   }

   @Override
   public String icon() {
      return "red_concrete_powder";
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
      PummelSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      PillarPummelSettings settings = room.pillarPummelSettings();
      int min = settings.teamCount();
      int max = settings.maxPlayers();
      if (room.size() < min || room.size() > max) {
         this.ctx.send(actor, "&c柱联壁合需要 &f" + min + "–" + max
            + " &c人（" + settings.teamCount() + " 队×" + settings.teamSize()
            + "，当前 &f" + room.size() + "&c）。");
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
      UUID matchId = this.ctx.pillarPummel().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的柱联壁合场地。");
      }
   }
}
