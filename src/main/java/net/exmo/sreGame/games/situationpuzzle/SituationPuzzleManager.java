package net.exmo.sreGame.games.situationpuzzle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.server.level.ServerPlayer;

public final class SituationPuzzleManager {
   private final GameContext ctx;
   private final Map<UUID, SituationPuzzleMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, SituationPuzzleMatch> byId = new ConcurrentHashMap<>();
   /** 已通过 AI 模式密码校验的房主（一次性令牌，开局成功后清除）。 */
   private final java.util.Set<UUID> aiAuthPassed = ConcurrentHashMap.newKeySet();

   public SituationPuzzleManager(GameContext ctx) {
      this.ctx = ctx;
   }

   public SituationPuzzleMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public SituationPuzzleMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return this.byPlayer.containsKey(player.getUUID());
   }

   /** 本局是否使用 AI（单人模式或 AI 出题）。 */
   public boolean usesAi(SituationPuzzleSettings s) {
      return s.soloMode() || s.puzzleSource() == SituationPuzzleSettings.PuzzleSource.AI;
   }

   /** AI 模式是否需要密码（全局 aiPassword 非空且本局使用 AI）。 */
   public boolean needsAiPassword(GameRoom room) {
      String pw = this.ctx.aiConfig().aiPassword();
      return pw != null && !pw.isEmpty() && usesAi(room.situationPuzzleSettings());
   }

   public boolean isAiAuthorized(UUID host) {
      return this.aiAuthPassed.contains(host);
   }

   public void grantAiAuth(UUID host) {
      this.aiAuthPassed.add(host);
   }

   public void consumeAiAuth(UUID host) {
      this.aiAuthPassed.remove(host);
   }

   public void promptAiPassword(ServerPlayer host, GameRoom room) {
      net.exmo.sreGame.input.ChatPrompt.await(host,
            net.exmo.sreGame.input.ChatPrompt.Kind.SITUATION_AI_PASSWORD, room.id(),
            "&e海龟汤 AI 模式已启用密码保护。请在聊天输入密码以开始对局。");
   }

   public UUID start(GameRoom room) {
      this.consumeAiAuth(room.host());
      SituationPuzzleMatch match = new SituationPuzzleMatch(this.ctx, room, room.situationPuzzleSettings());
      this.byId.put(match.id(), match);
      for (UUID uuid : room.members()) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      room.setState(RoomState.PLAYING);
      match.start();
      return match.id();
   }

   public void tick() {
      for (SituationPuzzleMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      SituationPuzzleMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(SituationPuzzleMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (SituationPuzzleMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      SituationPuzzleMatch match = this.get(player.getUUID());
      return match != null && match.handleChat(player, message);
   }
}
