package net.exmo.sreGame.games.pushthebutton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.pushthebutton.gui.PushTheButtonSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class PushTheButtonMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "push_the_button";
   private final GameContext ctx;

   public PushTheButtonMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "拍下按钮";
   }

   @Override
   public String icon() {
      return "stone_button";
   }

   @Override
   public int minPlayers() {
      return 4;
   }

   @Override
   public int maxPlayers() {
      return 24;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      PushTheButtonSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < this.minPlayers() || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c拍下按钮需要 &f" + this.minPlayers() + "–" + this.maxPlayers()
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
      UUID id = this.ctx.pushTheButton().start(room);
      if (id == null) {
         this.ctx.send(actor, "&c没有空闲的飞船场地。");
      }
   }
}
