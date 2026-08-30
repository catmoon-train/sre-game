package net.exmo.sreGame.games.partygames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.PartyGameSetupGui;
import net.exmo.sreGame.games.partygames.official.OfficialPartyGames;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

/** Thin MiniGame registration layer; all runtime behaviour lives in PartyGameManager. */
public final class PartyMiniGame implements net.exmo.sreGame.game.MiniGame {
   private final GameContext ctx;
   private final PartyGameType type;

   public PartyMiniGame(GameContext ctx, PartyGameType type) { this.ctx = ctx; this.type = type; }
   public PartyGameType type() { return this.type; }
   @Override public String id() { return this.type.id(); }
   @Override public String displayName() { return this.type.displayName(); }
   @Override public String icon() { return this.type.icon(); }
   @Override public int minPlayers() { return 2; }
   @Override public int maxPlayers() { return this.type.maxPlayers(); }
   @Override public void openSetup(ServerPlayer host, GameRoom room) { PartyGameSetupGui.open(this.ctx, host, room, this.type); }

   @Override public boolean canStart(GameRoom room, ServerPlayer actor) {
      if (!this.ctx.partyGames().isEnabled(this.type)) {
         this.ctx.send(actor, "&c该小游戏当前已被管理员禁用。" );
         return false;
      }
      if (this.ctx.partyGames().isSceneGame(this.type) && !this.ctx.partyGames().scenes().ready(this.type)) {
         this.ctx.send(actor, "&c官方场景不可用：&f" + this.ctx.partyGames().scenes().status(this.type));
         return false;
      }
      if (room.size() < minPlayers() || room.size() > maxPlayers()) {
         this.ctx.send(actor, "&c" + displayName() + "需要 &f" + minPlayers() + "–" + maxPlayers() + " &c人（当前 &f" + room.size() + "&c）。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) if (!room.isReady(uuid)) waiting.add(this.ctx.name(uuid));
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      if (!this.ctx.partyGames().isSceneGame(this.type) && this.ctx.partyGames().maps().choose(this.type, room.partyGameSettings().mapId(this.type)) == null) {
         this.ctx.send(actor, "&c没有启用的 " + displayName() + " 地图模板。");
         return false;
      }
      return true;
   }

   @Override public void start(GameRoom room, ServerPlayer actor) {
      if (this.ctx.partyGames().start(room, this.type) == null) this.ctx.send(actor, "&c没有空闲的派对小游戏场地。");
   }
}
