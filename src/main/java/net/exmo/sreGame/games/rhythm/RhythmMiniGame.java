package net.exmo.sreGame.games.rhythm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.game.MiniGame;
import net.exmo.sreGame.gui.RhythmSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class RhythmMiniGame implements MiniGame {
   public static final String ID = "rhythm_game";
   private final GameContext ctx;

   public RhythmMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "节奏大师";
   }

   @Override
   public String icon() {
      return "note_block";
   }

   @Override
   public int minPlayers() {
      return 1;
   }

   @Override
   public int maxPlayers() {
      return 4;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      RhythmSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      RhythmSettings s = room.rhythmSettings();
      int min = s.mode().minPlayers();
      int max = s.mode().maxPlayers();
      if (room.size() < min || room.size() > max) {
         this.ctx.send(actor, "&c模式「" + s.mode().label() + "」需要 &f" + min + "–" + max + " &c人（当前 &f" + room.size() + "&c 人）。");
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
      UUID matchId = this.ctx.rhythm().start(room);
      if (matchId == null) {
         this.ctx.send(actor, "&c开局失败。");
      }
   }
}
