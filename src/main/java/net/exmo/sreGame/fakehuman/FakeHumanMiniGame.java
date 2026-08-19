package net.exmo.sreGame.fakehuman;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.FakeHumanSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class FakeHumanMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "who_is_fake";
   private final GameContext ctx;

   public FakeHumanMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "谁是伪人";
   }

   @Override
   public String icon() {
      return "iron_door";
   }

   @Override
   public int minPlayers() {
      return 4;
   }

   @Override
   public int maxPlayers() {
      return 8;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      FakeHumanSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c谁是伪人需要 &f" + this.minPlayers() + "–" + this.maxPlayers() + " &c人（推荐 6）。");
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
      UUID matchId = this.ctx.fakeHuman().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有空闲的安全屋场地。");
      }
   }
}
