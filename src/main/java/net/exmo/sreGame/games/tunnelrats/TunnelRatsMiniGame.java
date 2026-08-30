package net.exmo.sreGame.games.tunnelrats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.TunnelRatsSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class TunnelRatsMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "tunnel_rats";
   private final GameContext ctx;

   public TunnelRatsMiniGame(GameContext ctx) { this.ctx = ctx; }
   @Override public String id() { return ID; }
   @Override public String displayName() { return "地道战：Tunnel Rats"; }
   @Override public String icon() { return "iron_pickaxe"; }
   @Override public int minPlayers() { return 2; }
   @Override public int maxPlayers() { return 32; }

   @Override public void openSetup(ServerPlayer host, GameRoom room) {
      TunnelRatsSetupGui.open(this.ctx, host, room);
   }

   @Override public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < minPlayers() || room.size() > maxPlayers()) {
         this.ctx.send(actor, "&c地道战需要 &f2–32 &c人（当前 &f" + room.size() + "&c）。");
         return false;
      }
      int red = 0;
      int blue = 0;
      for (UUID uuid : room.members()) {
         if (room.duelSettings().teamOf(uuid) == 1) red++;
         else if (room.duelSettings().teamOf(uuid) == 2) blue++;
      }
      if (red == 0 || blue == 0) {
         this.ctx.send(actor, "&c红、蓝两队都至少需要一名玩家。请在房间面板调整队伍。");
         return false;
      }
      if (red > 16 || blue > 16) {
         this.ctx.send(actor, "&c地道战每队最多 &f16 &c人（红队 " + red + "，蓝队 " + blue + "）。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) if (!room.isReady(uuid)) waiting.add(this.ctx.name(uuid));
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      return true;
   }

   @Override public void start(GameRoom room, ServerPlayer actor) {
      if (this.ctx.tunnelRats().start(room) == null) {
         this.ctx.send(actor, "&c没有空闲的地道战场地。请稍后重试。");
      }
   }
}
