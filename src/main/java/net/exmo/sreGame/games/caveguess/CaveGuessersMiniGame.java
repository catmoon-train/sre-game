package net.exmo.sreGame.games.caveguess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.caveguess.gui.CaveSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class CaveGuessersMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "cave_guess";
   private final GameContext ctx;

   public CaveGuessersMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "洞穴猜猜乐";
   }

   @Override
   public String icon() {
      return "pointed_dripstone";
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
      CaveSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.caveSettings().totalRounds() < 1) {
         this.ctx.send(actor, "&c请在设置里至少开启一种模式。");
         return false;
      }
      if (this.ctx.caveWords().resolved(room).isEmpty()) {
         this.ctx.send(actor, "&c词库为空。请在设置里导入词库。");
         return false;
      }
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c洞穴猜猜乐需要 &f" + this.minPlayers() + "–" + this.maxPlayers() + " &c人（推荐 4–8）。");
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
      UUID matchId = this.ctx.caveGuess().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有足够的场地。");
      }
   }
}
