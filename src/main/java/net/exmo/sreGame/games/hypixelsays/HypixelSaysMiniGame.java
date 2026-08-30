package net.exmo.sreGame.games.hypixelsays;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class HypixelSaysMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "hypixel_says";
   private final GameContext ctx;
   public HypixelSaysMiniGame(GameContext ctx) { this.ctx = ctx; }
   @Override public String id() { return ID; }
   @Override public String displayName() { return "我说你做"; }
   @Override public String icon() { return "note_block"; }
   @Override public int minPlayers() { return 2; }
   @Override public int maxPlayers() { return 24; }
   @Override public void openSetup(ServerPlayer host, GameRoom room) {
      ctx.send(host, "&d我说你做 &7— 固定 15 轮，每轮 5 秒。第 1 名 +3，第 2 名 +2，其余完成者 +1。");
      ctx.send(host, "&7任务池含 54 项可判定指令；本局规则不可修改。");
   }
   @Override public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (room.size() < minPlayers() || room.size() > maxPlayers()) { ctx.send(actor, "&c我说你做需要 &f2–24 &c人（当前 &f" + room.size() + "&c）。"); return false; }
      if (!room.allReady()) { List<String> waiting = new ArrayList<>(); for (UUID uuid : room.members()) if (!room.isReady(uuid)) waiting.add(ctx.name(uuid)); ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting)); return false; }
      return true;
   }
   @Override public void start(GameRoom room, ServerPlayer actor) { if (ctx.hypixelSays().start(room) == null) ctx.send(actor, "&c没有空闲的我说你做场地。"); }
}
