package net.exmo.sreGame.games.football;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class FootballMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "football";
   private final GameContext ctx;
   public FootballMiniGame(GameContext ctx) { this.ctx = ctx; }
   @Override public String id() { return ID; }
   @Override public String displayName() { return "足球大战"; }
   @Override public String icon() { return "slime_ball"; }
   @Override public int minPlayers() { return 2; }
   @Override public int maxPlayers() { return 24; }
   @Override public void openSetup(ServerPlayer host, GameRoom room) {
      this.ctx.send(host, "&a足球大战固定规则：&f4 分钟 &7· &f2–24 人 &7· &f自动红蓝分队。");
      this.ctx.send(host, "&7撞球带球，空手左键射门；按两次跳跃键可二段跳。返回房间面板即可开始。");
   }
   @Override public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < minPlayers() || room.size() > maxPlayers()) { this.ctx.send(actor, "&c足球大战需要 &f2–24 &c人（当前 &f" + room.size() + "&c）。"); return false; }
      if (!room.allReady()) { List<String> waiting = new ArrayList<>(); for (UUID id : room.members()) if (!room.isReady(id)) waiting.add(this.ctx.name(id)); this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting)); return false; }
      return true;
   }
   @Override public void start(GameRoom room, ServerPlayer actor) { if (this.ctx.football().start(room) == null) this.ctx.send(actor, "&c没有空闲的足球场地。"); }
}
