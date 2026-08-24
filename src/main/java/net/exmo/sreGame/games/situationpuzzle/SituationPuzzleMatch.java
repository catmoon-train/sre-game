package net.exmo.sreGame.games.situationpuzzle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.ai.AiProviderConfig;
import net.exmo.sreGame.ai.AiService;
import net.exmo.sreGame.ai.AnswerType;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class SituationPuzzleMatch {
   public enum Phase { PREPARING, PLAYING, REVEAL, ENDED }

   private static final int REVEAL_SECONDS = 8;
   private static final int MAX_QUESTION_LEN = 80;
   private static final int MAX_TITLE_LEN = 120;
   private static final int MAX_TRUTH_LEN = 600;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final SituationPuzzleSettings settings;
   private final boolean solo;
   private final SidebarBoard board;
   private final List<Question> questions = new ArrayList<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final java.util.Set<UUID> remaining = ConcurrentHashMap.newKeySet();

   private Phase phase = Phase.PREPARING;
   private String puzzleTitle;
   private String puzzleTruth;
   private boolean begun;
   private int ticksLeft;
   private int boardTicks;
   private boolean generating;
   private ManualStage manualStage = ManualStage.NONE;

   private enum ManualStage { NONE, TITLE, TRUTH }

   public SituationPuzzleMatch(GameContext ctx, GameRoom room, SituationPuzzleSettings settings) {
      this.ctx = ctx;
      this.room = room;
      this.settings = settings;
      this.seats = List.copyOf(room.members());
      this.remaining.addAll(this.seats);
      this.solo = settings.soloMode();
      this.board = new SidebarBoard(ctx.server());
   }

   public UUID id() { return this.id; }
   public Phase phase() { return this.phase; }
   public boolean contains(UUID uuid) { return this.remaining.contains(uuid); }
   public boolean isPlaying(UUID uuid) { return this.remaining.contains(uuid); }
   public String puzzleTitle() { return this.puzzleTitle; }
   public List<Question> questions() { return List.copyOf(this.questions); }

   /** 房主按 1 起始序号设置/修改某条提问的回答（多人模式，PLAYING 阶段）。返回是否成功。 */
   public boolean setAnswer(int oneBasedIndex, AnswerType type, ServerPlayer actor) {
      if (this.phase != Phase.PLAYING || this.solo) return false;
      if (this.room.host() == null || !this.room.host().equals(actor.getUUID())) return false;
      int idx = oneBasedIndex - 1;
      if (idx < 0 || idx >= this.questions.size()) return false;
      Question target = this.questions.get(idx);
      target.setAnswerType(type);
      this.ctx.broadcast(this.room, "&b" + target.askerName() + " &7： &f" + target.question());
      this.ctx.broadcast(this.room, "&6[房主] &f" + type.label());
      this.pushBoard();
      return true;
   }

   /** 解析本局出题用的 AI 提供商；优先房间设置，回退全局 generator。无可用则返回 null。 */
   private AiProviderConfig resolveGenerator() {
      AiProviderConfig p = this.ctx.aiConfig().provider(this.settings.aiProviderName());
      if (p != null && p.hasApiKey()) return p;
      return this.ctx.aiConfig().generatorProvider();
   }

   /** 解析本局回答用的 AI 提供商；优先房间设置，回退全局 answerer。无可用则返回 null。 */
   private AiProviderConfig resolveAnswerer() {
      AiProviderConfig p = this.ctx.aiConfig().provider(this.settings.aiProviderName());
      if (p != null && p.hasApiKey()) return p;
      return this.ctx.aiConfig().answererProvider();
   }

   public void start() {
      this.begun = true;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.saved.put(uuid, Saved.capture(player));
            this.board.create(player, this.title());
            player.closeContainer();
            player.getInventory().clearContent();
            player.setGameMode(GameType.ADVENTURE);
         }
      }
      this.ctx.broadcast(this.room, "&d&l海龟汤 &7— 情景推理游戏");
      if (this.solo) {
         this.ctx.broadcast(this.room, "&e单人模式：AI 出题并担任主持人。聊天提问，AI 回答是/不是/无关。");
         this.ctx.broadcast(this.room, "&7输入 &f放弃 &7查看汤底；AI 判定你还原真相时自动揭晓。");
         this.beginAiGenerate();
      } else if (this.settings.puzzleSource() == SituationPuzzleSettings.PuzzleSource.AI) {
         this.ctx.broadcast(this.room, "&eAI 出题中，请稍候…");
         this.beginAiGenerate();
      } else {
         this.beginManualEntry();
      }
   }

   private void beginAiGenerate() {
      this.generating = true;
      this.phase = Phase.PREPARING;
      AiService ai = this.ctx.ai();
      AiProviderConfig provider = this.resolveGenerator();
      if (ai == null || provider == null || !provider.hasApiKey()) {
         this.generating = false;
         this.ctx.broadcast(this.room, "&cAI 出题未启用（未配置 API key）。改为房主手填模式。");
         this.beginManualEntry();
         return;
      }
      String system = SituationPuzzlePrompts.generatorSystemPromptWithDifficulty(
            SituationPuzzlePrompts.difficultyPrompt(this.settings.difficulty()));
      ai.generatePuzzle(provider, system, SituationPuzzlePrompts.GENERATOR_USER_PROMPT)
            .thenAccept(result -> this.ctx.server().execute(() -> this.onPuzzleGenerated(result)));
   }

   private void onPuzzleGenerated(AiService.GenerateResult result) {
      if (this.phase == Phase.ENDED) return;
      this.generating = false;
      if (!result.success()) {
         this.ctx.broadcast(this.room, "&cAI 出题失败：&f" + result.error());
         this.ctx.broadcast(this.room, "&7改为房主手填模式。");
         this.beginManualEntry();
         return;
      }
      this.puzzleTitle = result.title();
      this.puzzleTruth = result.truth();
      this.beginPlaying();
   }

   private void beginManualEntry() {
      this.phase = Phase.PREPARING;
      this.manualStage = ManualStage.TITLE;
      ServerPlayer host = this.ctx.player(this.room.host());
      if (host == null) {
         this.ctx.broadcast(this.room, "&c房主不在线，无法手填题目。");
         this.finish();
         return;
      }
      this.ctx.broadcast(this.room, "&e房主手填题目模式。等待房主输入汤面与汤底。");
      this.ctx.send(host, "&a请在聊天输入 &f汤面&7（题目情景，1–120 字）&a。输入 &f取消 &a中止。");
   }

   private void beginPlaying() {
      this.phase = Phase.PLAYING;
      this.manualStage = ManualStage.NONE;
      this.ctx.broadcast(this.room, "&6========== 海龟汤开始 ==========");
      this.ctx.broadcast(this.room, "&d【汤面】 &f" + this.puzzleTitle);
      if (this.solo) {
         this.ctx.broadcast(this.room, "&7在聊天提问，AI 主持人会回答。");
      } else {
         ServerPlayer host = this.ctx.player(this.room.host());
         if (host != null) {
            this.ctx.send(host, "&a【汤底（仅你可见）】 &f" + this.puzzleTruth);
            this.ctx.send(host, "&7猜谜者提问后，聊天回答 &f是/不是/是或不是/无关 &7或 &f1/2/4/3 &7回答最早未答的问题。");
            this.ctx.send(host, "&7可用 &f/sp answer <序号> <是|不是|是或不是|无关> &7修改任意提问的回答；&f/sp say <文本> &7发言；&f/sp list &7查看记录。");
            this.ctx.send(host, "&7输入 &f揭晓 &7公布汤底并结束；或用 &f/room end &7直接终止对局。");
         }
         this.ctx.broadcast(this.room, "&7猜谜者聊天提问，房主回答是/不是/无关。");
      }
      this.pushBoard();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) return;
      this.boardTicks++;
      if (this.phase == Phase.PREPARING && this.generating) {
         for (UUID uuid : this.remaining) {
            ServerPlayer p = this.ctx.player(uuid);
            if (p != null) p.displayClientMessage(TextUtil.color("&dAI 出题中…"), true);
         }
      }
      if (this.boardTicks % 10 == 0) this.pushBoard();
      if (this.phase == Phase.REVEAL) {
         this.ticksLeft--;
         if (this.ticksLeft <= 0) this.finish();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      if (!this.remaining.contains(player.getUUID())) return false;
      String text = message == null ? "" : message.trim();
      if (this.phase == Phase.PREPARING) {
         return this.handleManualEntryChat(player, text);
      }
      if (this.phase != Phase.PLAYING) return false;
      if (this.solo) return this.handleSoloChat(player, text);
      return this.handleMultiChat(player, text);
   }

   private boolean handleManualEntryChat(ServerPlayer player, String text) {
      if (this.manualStage == ManualStage.NONE) return false;
      if (!this.room.isHost(player.getUUID())) {
         this.ctx.send(player, "&7等待房主输入题目…");
         return true;
      }
      if (isCancel(text)) {
         this.manualStage = ManualStage.NONE;
         this.ctx.broadcast(this.room, "&c房主取消了出题，对局结束。");
         this.finish();
         return true;
      }
      if (text.isEmpty()) {
         this.ctx.send(player, "&c内容不能为空。");
         return true;
      }
      if (this.manualStage == ManualStage.TITLE) {
         if (text.length() > MAX_TITLE_LEN) text = text.substring(0, MAX_TITLE_LEN);
         this.puzzleTitle = text;
         this.manualStage = ManualStage.TRUTH;
         this.ctx.send(player, "&a汤面已记录。请输入 &f汤底&7（完整真相，1–600 字）&a。输入 &f取消 &a中止。");
         return true;
      }
      if (this.manualStage == ManualStage.TRUTH) {
         if (text.length() > MAX_TRUTH_LEN) text = text.substring(0, MAX_TRUTH_LEN);
         this.puzzleTruth = text;
         this.manualStage = ManualStage.NONE;
         this.beginPlaying();
         return true;
      }
      return true;
   }

   private boolean handleSoloChat(ServerPlayer player, String text) {
      if (isCancel(text)) {
         this.ctx.broadcast(this.room, "&e玩家放弃，揭晓汤底。");
         this.beginReveal(false);
         return true;
      }
      if (text.isEmpty() || text.length() > MAX_QUESTION_LEN) {
         this.ctx.send(player, "&c请输入 1–" + MAX_QUESTION_LEN + " 字的提问或推理。");
         return true;
      }
      this.ctx.broadcast(this.room, "&b" + player.getGameProfile().getName() + " &7： &f" + text);
      Question q = new Question(player.getUUID(), player.getGameProfile().getName(), text);
      this.questions.add(q);
      this.pushBoard();
      AiService ai = this.ctx.ai();
      AiProviderConfig provider = this.resolveAnswerer();
      if (ai == null || provider == null || !provider.hasApiKey()) {
         this.ctx.broadcast(this.room, "&cAI 主持人未启用，无法回答。");
         return true;
      }
      String system = SituationPuzzlePrompts.SOLO_ANSWERER_SYSTEM_PROMPT;
      String user = SituationPuzzlePrompts.soloAnswererUserPrompt(this.puzzleTruth, text);
      ai.answerSoloQuestion(provider, system, user)
            .thenAccept(result -> this.ctx.server().execute(() -> this.onSoloAnswer(q, result)))
            .exceptionally(ex -> { this.ctx.server().execute(() -> this.ctx.broadcast(this.room, "&cAI 回答失败。")); return null; });
      return true;
   }

   private void onSoloAnswer(Question q, AiService.SoloAnswerResult result) {
      if (this.phase == Phase.ENDED || this.phase != Phase.PLAYING) return;
      q.setAnswerType(result.answerType());
      this.ctx.broadcast(this.room, "&6[主持人] &f" + result.answerType().label());
      this.pushBoard();
      if (result.solved()) {
         this.ctx.broadcast(this.room, "&a&lAI 判定你已还原真相！自动揭晓。");
         this.beginReveal(true);
      }
   }

   private boolean handleMultiChat(ServerPlayer player, String text) {
      if (this.room.isHost(player.getUUID())) {
         if (isReveal(text)) {
            this.ctx.broadcast(this.room, "&e房主揭晓汤底。");
            this.beginReveal(false);
            return true;
         }
         AnswerType parsed = parseAnswer(text);
         if (parsed == null) {
            this.ctx.send(player, "&7请回答 &f是/不是/无关 &7或 &f1/2/3&7，或输入 &f揭晓 &7结束。");
            return true;
         }
         Question target = this.firstUnanswered();
         if (target == null) {
            this.ctx.send(player, "&7当前没有待回答的问题。");
            return true;
         }
         target.setAnswerType(parsed);
         this.ctx.broadcast(this.room, "&b" + target.askerName() + " &7： &f" + target.question());
         this.ctx.broadcast(this.room, "&6[房主] &f" + parsed.label());
         this.pushBoard();
         return true;
      }
      if (text.isEmpty() || text.length() > MAX_QUESTION_LEN) {
         this.ctx.send(player, "&c请输入 1–" + MAX_QUESTION_LEN + " 字的提问。");
         return true;
      }
      Question q = new Question(player.getUUID(), player.getGameProfile().getName(), text);
      this.questions.add(q);
      this.ctx.broadcast(this.room, "&b" + player.getGameProfile().getName() + " &7： &f" + text);
      ServerPlayer host = this.ctx.player(this.room.host());
      if (host != null) {
         this.ctx.send(host, "&e新提问待回答： &f" + text + " &7（聊天 是/不是/无关）");
      }
      if (this.settings.aiAssistHost() && host != null) {
         this.suggestAnswer(q, host);
      }
      this.pushBoard();
      return true;
   }

   private void suggestAnswer(Question q, ServerPlayer host) {
      AiService ai = this.ctx.ai();
      AiProviderConfig provider = this.resolveAnswerer();
      if (ai == null || provider == null || !provider.hasApiKey()) return;
      String system = SituationPuzzlePrompts.ANSWERER_SYSTEM_PROMPT;
      String user = SituationPuzzlePrompts.answererUserPrompt(this.puzzleTruth, q.question());
      ai.answerQuestion(provider, system, user)
            .thenAccept(result -> this.ctx.server().execute(() -> {
               if (this.phase == Phase.ENDED) return;
               this.ctx.send(host, "&7[AI 建议] &f" + result.label());
            }))
            .exceptionally(ex -> null);
   }

   private Question firstUnanswered() {
      for (Question q : this.questions) if (!q.isAnswered()) return q;
      return null;
   }

   private void beginReveal(boolean solved) {
      this.phase = Phase.REVEAL;
      this.ticksLeft = REVEAL_SECONDS * 20;
      this.ctx.broadcast(this.room, "&6========== 揭晓汤底 ==========");
      this.ctx.broadcast(this.room, "&d【汤面】 &f" + this.puzzleTitle);
      this.ctx.broadcast(this.room, "&a【汤底】 &f" + this.puzzleTruth);
      if (solved) {
         this.ctx.broadcast(this.room, "&a&l玩家还原了真相！");
      }
      this.pushBoard();
   }

   public void onLeave(UUID uuid) {
      if (!this.remaining.remove(uuid)) return;
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) this.restore(player);
      this.board.remove(uuid);
      if (!this.begun) return;
      if (this.solo) {
         this.ctx.broadcast(this.room, "&c玩家离开，单人海龟汤结束。");
         this.finish();
         return;
      }
      if (this.room.isHost(uuid)) {
         this.ctx.broadcast(this.room, "&c房主离开，对局结束。");
         this.finish();
         return;
      }
      if (this.remaining.size() < 2) {
         this.ctx.broadcast(this.room, "&c人数不足，提前结束。");
         this.finish();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) this.finish();
   }

   private void finish() {
      this.phase = Phase.ENDED;
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) this.restore(player);
      }
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.situationPuzzle().remove(this);
   }

   private String title() { return "&d海龟汤"; }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7&m---------------");
      lines.add("&7阶段 &f" + phaseLabel());
      if (this.puzzleTitle != null) {
         lines.add("&d汤面 &f" + truncate(this.puzzleTitle, 24));
      } else if (this.generating) {
         lines.add("&dAI 出题中…");
      } else if (this.manualStage != ManualStage.NONE) {
         lines.add("&e等待房主出题");
      }
      int answered = 0;
      for (Question q : this.questions) if (q.isAnswered()) answered++;
      lines.add("&7提问 &f" + this.questions.size() + " &7已答 &f" + answered);
      Question pending = this.firstUnanswered();
      if (pending != null) lines.add("&e待答 &f" + truncate(pending.question(), 28));
      lines.add("&7&m---------------");
      for (UUID uuid : this.remaining) {
         ServerPlayer p = this.ctx.player(uuid);
         if (p != null) this.board.update(p, lines);
      }
   }

   private String phaseLabel() {
      return switch (this.phase) {
         case PREPARING -> "准备中";
         case PLAYING -> "提问中";
         case REVEAL -> "揭晓";
         case ENDED -> "结束";
      };
   }

   private static String truncate(String s, int max) {
      if (s == null) return "";
      return s.length() <= max ? s : s.substring(0, max) + "…";
   }

   private static boolean isCancel(String text) {
      return "取消".equals(text) || "cancel".equalsIgnoreCase(text) || "放弃".equals(text) || "give up".equalsIgnoreCase(text);
   }

   private static boolean isReveal(String text) {
      return "揭晓".equals(text) || "reveal".equalsIgnoreCase(text) || "结束".equals(text) || "end".equalsIgnoreCase(text);
   }

   private static AnswerType parseAnswer(String text) {
      return AnswerType.fromLabel(text);
   }

   private void restore(ServerPlayer player) {
      player.closeContainer();
      this.board.remove(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         ServerLevel overworld = this.ctx.server().overworld();
         player.teleportTo(overworld, overworld.getSharedSpawnPos().getX() + 0.5,
               overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ() + 0.5, 0.0F, 0.0F);
      }
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                        Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>();
         Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) items.add(inv.getItem(i).copy());
         return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
               player.gameMode.getGameModeForPlayer(), items);
      }

      void apply(ServerPlayer player, GameContext ctx) {
         ServerLevel level = ctx.server().getLevel(this.dimension);
         if (level == null) level = ctx.server().overworld();
         player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
         player.setGameMode(this.gameType);
         Inventory inv = player.getInventory();
         inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), this.items.size()); i++) inv.setItem(i, this.items.get(i).copy());
      }
   }
}
