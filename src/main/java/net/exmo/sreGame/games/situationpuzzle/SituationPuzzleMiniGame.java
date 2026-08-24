package net.exmo.sreGame.games.situationpuzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.ai.AiProviderConfig;
import net.exmo.sreGame.gui.SituationPuzzleSetupGui;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;

public final class SituationPuzzleMiniGame implements net.exmo.sreGame.game.MiniGame {
   public static final String ID = "situation_puzzle";
   private final GameContext ctx;

   public SituationPuzzleMiniGame(GameContext ctx) {
      this.ctx = ctx;
   }

   @Override
   public String id() {
      return ID;
   }

   @Override
   public String displayName() {
      return "海龟汤";
   }

   @Override
   public String icon() {
      return "writable_book";
   }

   @Override
   public int minPlayers() {
      return 1;
   }

   @Override
   public int maxPlayers() {
      return 64;
   }

   @Override
   public void openSetup(ServerPlayer host, GameRoom room) {
      SituationPuzzleSetupGui.open(this.ctx, host, room);
   }

   @Override
   public boolean canStart(GameRoom room, ServerPlayer actor) {
      SituationPuzzleSettings s = room.situationPuzzleSettings();
      if (this.ctx.situationPuzzle().needsAiPassword(room)
            && !this.ctx.situationPuzzle().isAiAuthorized(actor.getUUID())) {
         this.ctx.situationPuzzle().promptAiPassword(actor, room);
         return false;
      }
      if (s.soloMode()) {
         if (room.size() != 1) {
            this.ctx.send(actor, "&c单人模式仅限 1 人。当前 &f" + room.size() + " &c人。");
            return false;
         }
         AiProviderConfig gen = this.ctx.aiConfig().provider(s.aiProviderName());
         if (gen == null || !gen.hasApiKey()) gen = this.ctx.aiConfig().generatorProvider();
         AiProviderConfig ans = this.ctx.aiConfig().provider(s.aiProviderName());
         if (ans == null || !ans.hasApiKey()) ans = this.ctx.aiConfig().answererProvider();
         if (gen == null || !gen.hasApiKey() || ans == null || !ans.hasApiKey()) {
            this.ctx.send(actor, "&c单人模式需要 AI 出题与回答均启用（配置 &fconfig/sre-game/ai.json&c）。");
            return false;
         }
         return true;
      }
      if (room.size() < 2 || room.size() > this.maxPlayers()) {
         this.ctx.send(actor, "&c海龟汤需要 &f2–" + this.maxPlayers() + " &c人。");
         return false;
      }
      if (!room.allReady()) {
         List<String> waiting = new ArrayList<>();
         for (UUID uuid : room.members()) {
            if (!room.isReady(uuid)) waiting.add(this.ctx.name(uuid));
         }
         this.ctx.send(actor, "&c还有玩家未准备：&f" + String.join("&7, &f", waiting));
         return false;
      }
      if (s.puzzleSource() == SituationPuzzleSettings.PuzzleSource.AI) {
         AiProviderConfig gen = this.ctx.aiConfig().provider(s.aiProviderName());
         if (gen == null || !gen.hasApiKey()) gen = this.ctx.aiConfig().generatorProvider();
         if (gen == null || !gen.hasApiKey()) {
            this.ctx.send(actor, "&cAI 出题未启用（未配置 API key）。请在设置改为手填，或让 OP 配置 &fconfig/sre-game/ai.json&c。");
            return false;
         }
      }
      return true;
   }

   @Override
   public void start(GameRoom room, ServerPlayer actor) {
      this.ctx.situationPuzzle().start(room);
   }
}
