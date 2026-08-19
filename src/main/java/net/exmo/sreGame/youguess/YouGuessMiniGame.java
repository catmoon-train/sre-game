package net.exmo.sreGame.youguess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.YouGuessSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class YouGuessMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "you_guess";
   private final GameContext ctx;

   public YouGuessMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "你建我猜";
   }

   @Override
   public String icon() {
      return "painting";
   }

   @Override
   public int minPlayers() {
      return 2;
   }

   @Override
   public int maxPlayers() {
      return 20;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      YouGuessSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.resolvedWords(this.ctx).isEmpty()) {
         this.ctx.send(actor, "&c词库为空。请在设置里导入词库，或让 OP 用 &f/sregame words &c添加。");
         return false;
      }
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c你建我猜需要 &f" + this.minPlayers() + "–" + this.maxPlayers() + " &c人。");
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
      UUID matchId = this.ctx.youGuess().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c没有足够的建筑场地。");
      }
   }
}
