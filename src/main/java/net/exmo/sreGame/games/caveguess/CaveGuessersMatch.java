package net.exmo.sreGame.games.caveguess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildwar.Plot;
import net.exmo.sreGame.games.caveguess.mode.CaveModeHandler;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.util.WordHint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class CaveGuessersMatch {
   public enum Phase {
      INTRO,
      DESCRIBE,
      REVEAL,
      ENDED
   }

   public static final String TEAM_NAME = "srecv";
   private static final int INTRO_TICKS = 40;
   private static final int REVEAL_TICKS = 100;
   private static final int FAST_BONUS_TICKS = 200;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Set<UUID> remaining = ConcurrentHashMap.newKeySet();
   private final Plot plot;
   private final CaveArena arena;
   private final List<CaveWord> bank;
   private final List<CaveMode> schedule;
   private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Set<String> usedWords = new HashSet<>();
   private final Set<UUID> correct = ConcurrentHashMap.newKeySet();
   private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
   private final SidebarBoard board;
   private Phase phase = Phase.INTRO;
   private int roundIndex;
   private int ticksLeft;
   private int boardTicks;
   private int describeTicks;
   private int voidCount;
   private boolean begun;
   private boolean guessedThisRound;
   private UUID performer;
   private UUID isolatedGuesser;
   private CaveMode mode = CaveMode.WATCH_WORDS;
   private CaveMode laidOut;
   private CaveModeHandler handler;
   private CaveWord word = CaveWord.plain("苦力怕");
   private List<String> tuneOptions = List.of();
   private List<UUID> uniqueClueOwners = List.of();

   public CaveGuessersMatch(GameContext ctx, GameRoom room, List<UUID> seats, Plot plot, List<CaveWord> bank) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.remaining.addAll(seats);
      this.plot = plot;
      this.arena = new CaveArena(plot);
      this.bank = bank.isEmpty() ? List.of(CaveWord.plain("苦力怕")) : List.copyOf(bank);
      this.schedule = new ArrayList<>(room.caveSettings().schedule());
      this.board = new SidebarBoard(ctx.server());
      for (UUID uuid : seats) {
         this.scores.put(uuid, 0);
      }
   }

   public UUID id() {
      return this.id;
   }

   public GameContext ctx() {
      return this.ctx;
   }

   public GameRoom room() {
      return this.room;
   }

   public Plot plot() {
      return this.plot;
   }

   public CaveArena arena() {
      return this.arena;
   }

   public Phase phase() {
      return this.phase;
   }

   public CaveMode mode() {
      return this.mode;
   }

   public CaveModeHandler handler() {
      return this.handler;
   }

   public CaveWord word() {
      return this.word;
   }

   public UUID performer() {
      return this.performer;
   }

   public UUID isolatedGuesser() {
      return this.isolatedGuesser;
   }

   public Set<UUID> remaining() {
      return this.remaining;
   }

   public CaveGuessersSettings settings() {
      return this.room.caveSettings();
   }

   public List<String> tuneOptions() {
      return this.tuneOptions;
   }

   public void setTuneOptions(List<String> options) {
      this.tuneOptions = options == null ? List.of() : List.copyOf(options);
   }

   public void setUniqueClueOwners(List<UUID> owners) {
      this.uniqueClueOwners = owners == null ? List.of() : List.copyOf(owners);
   }

   public boolean isPerformer(UUID uuid) {
      return uuid != null && uuid.equals(this.performer);
   }

   public boolean isIsolatedGuesser(UUID uuid) {
      return uuid != null && uuid.equals(this.isolatedGuesser);
   }

   public boolean canBuild(UUID uuid) {
      return this.phase == Phase.DESCRIBE
         && this.mode == CaveMode.SHADOW
         && this.isPerformer(uuid);
   }

   public boolean stageContains(BlockPos pos) {
      return this.arena.inStage(pos);
   }

   public int describeTicks() {
      return this.describeTicks;
   }

   public int totalRounds() {
      return Math.max(1, this.schedule.size());
   }

   public ServerLevel level() {
      return this.ctx.plots().level();
   }

   public void start() {
      this.begun = true;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.saved.put(uuid, Saved.capture(player));
            this.board.create(player, "&d洞穴猜猜乐");
         }
      }
      this.trimSchedule();
      if (this.schedule.isEmpty()) {
         this.ctx.broadcast(this.room, "&c没有可进行的模式，对局结束。");
         this.finish();
         return;
      }
      this.roundIndex = 0;
      this.beginIntro();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.phase == Phase.DESCRIBE) {
         this.describeTicks++;
         if (this.handler != null) {
            this.handler.onDescribeTick(this);
         }
      }
      this.enforce();
      this.actionBars();
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      if (this.ticksLeft > 0) {
         return;
      }
      if (this.phase == Phase.INTRO) {
         this.beginDescribe();
      } else if (this.phase == Phase.DESCRIBE) {
         this.beginReveal(false);
      } else if (this.phase == Phase.REVEAL) {
         this.nextRoundOrFinish();
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      if (player == null || !this.remaining.contains(player.getUUID())) {
         return false;
      }
      if (this.phase != Phase.DESCRIBE || this.handler == null) {
         this.ctx.send(player, "&7请等待下一阶段。");
         return true;
      }
      return this.handler.handleChat(this, player, message);
   }

   public boolean handleGui(ServerPlayer player, String action, String extra) {
      if (player == null || this.phase != Phase.DESCRIBE || this.handler == null) {
         return false;
      }
      if (!this.remaining.contains(player.getUUID())) {
         return false;
      }
      return this.handler.handleGui(this, player, action, extra);
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      if (player == null || this.phase != Phase.DESCRIBE || this.handler == null) {
         return false;
      }
      String action = GuiItems.actionTag(stack);
      if (action == null || !action.startsWith("cave-")) {
         return false;
      }
      return this.handler.handleUseItem(this, player, action);
   }

   public boolean tryGuess(ServerPlayer player, String text) {
      if (this.phase != Phase.DESCRIBE || player == null) {
         return true;
      }
      UUID uuid = player.getUUID();
      if (!this.remaining.contains(uuid)) {
         return true;
      }
      if (this.mode != CaveMode.ONE_OR_NONE && this.isPerformer(uuid)) {
         this.ctx.send(player, "&7描述者不能猜。");
         return true;
      }
      if (this.mode == CaveMode.ONE_OR_NONE && !this.isIsolatedGuesser(uuid)) {
         this.ctx.send(player, "&7只有本轮猜测者可以答题。");
         return true;
      }
      if (this.eliminated.contains(uuid)) {
         this.ctx.send(player, "&c你已经猜错，不能再猜。");
         return true;
      }
      if (this.correct.contains(uuid)) {
         this.ctx.send(player, "&a你已经猜对了。");
         return true;
      }
      String guess = text == null ? "" : text.trim();
      if (guess.isEmpty() || guess.length() > 32) {
         this.ctx.send(player, "&c请输入你猜的词。");
         return true;
      }
      if (!CaveWords.matches(guess, this.word.word())) {
         this.ctx.broadcast(this.room, "&e" + player.getGameProfile().getName() + " &7猜： &f" + guess);
         this.ctx.send(player, "&c不对，再想想。");
         return true;
      }
      this.onCorrect(player);
      return true;
   }

   public void markWrongChoice(ServerPlayer player) {
      if (player == null) {
         return;
      }
      this.eliminated.add(player.getUUID());
      this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " 选错了，不能再猜。");
   }

   public void voidWatchWord() {
      this.voidCount++;
      if (this.performer != null) {
         this.addScore(this.performer, -1);
      }
      this.ctx.broadcast(this.room, "&c描述含禁用词，本题作废，描述者 &c-1");
      if (this.voidCount >= 2 || !this.pickWord(this.mode)) {
         this.ctx.broadcast(this.room, "&7本轮不再换词。");
         this.beginReveal(false);
         return;
      }
      this.describeTicks = 0;
      this.ticksLeft = this.mode.describeSeconds() * 20;
      this.correct.clear();
      this.eliminated.clear();
      this.guessedThisRound = false;
      if (this.handler != null) {
         this.handler.onCleanup(this);
         this.handler.onPrepare(this);
      }
      this.pushBoard();
   }

   public void onLeave(UUID uuid) {
      if (!this.remaining.remove(uuid)) {
         return;
      }
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      }
      this.board.remove(uuid);
      if (this.remaining.size() < 2) {
         this.ctx.broadcast(this.room, "&c人数不足，提前结算。");
         this.finish();
         return;
      }
      if (this.phase == Phase.DESCRIBE && (this.isPerformer(uuid) || this.isIsolatedGuesser(uuid))) {
         this.ctx.broadcast(this.room, "&c关键角色离开，本轮结束。");
         this.beginReveal(false);
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish();
      }
   }

   public boolean voiceMuted(UUID uuid) {
      return this.phase == Phase.DESCRIBE && this.handler != null && this.handler.voiceMute(this, uuid);
   }

   public void broadcast(String message) {
      this.ctx.broadcast(this.room, message);
   }

   public void send(UUID uuid, String message) {
      this.ctx.send(this.ctx.player(uuid), message);
   }

   public void addScore(UUID uuid, int delta) {
      if (uuid != null && this.remaining.contains(uuid)) {
         this.scores.merge(uuid, delta, Integer::sum);
      }
   }

   public void showClues(String text) {
      this.arena.showText(this.level(), text);
      this.broadcast("&b线索： &f" + text.replace('\n', ' '));
   }

   public void showDescription(String text) {
      this.arena.showText(this.level(), text);
      for (UUID uuid : this.remaining) {
         if (!this.isPerformer(uuid)) {
            this.send(uuid, "&b描述： &f" + text);
         }
      }
   }

   public void showTags(List<String> tags) {
      String joined = String.join(" · ", tags);
      this.arena.showText(this.level(), joined + "\n" + WordHint.label(this.word.word()));
      for (UUID uuid : this.remaining) {
         if (!this.isPerformer(uuid)) {
            this.send(uuid, "&b标签： &f" + joined + " &8| &7" + WordHint.label(this.word.word()));
         }
      }
   }

   public ItemStack reopenItem(String material, String name, String action) {
      return GuiItems.action(material, name, List.of("&e右键重新打开界面"), action);
   }

   private void onCorrect(ServerPlayer player) {
      UUID uuid = player.getUUID();
      this.correct.add(uuid);
      this.guessedThisRound = true;
      int reward = this.guesserReward();
      this.addScore(uuid, reward);
      String extra = "";
      if (this.describeTicks <= FAST_BONUS_TICKS) {
         this.addScore(uuid, 1);
         extra = " &8| &e最快+1";
      }
      if (this.mode == CaveMode.ONE_OR_NONE) {
         for (UUID owner : this.uniqueClueOwners) {
            this.addScore(owner, 1);
         }
      } else if (this.performer != null) {
         this.addScore(this.performer, 1);
      }
      this.broadcast("&a" + player.getGameProfile().getName() + " &a猜对了！ &8+&e" + reward + extra);
      this.beginReveal(true);
   }

   private int guesserReward() {
      if (this.mode == CaveMode.ONE_OR_NONE) {
         return 3;
      }
      if (this.mode == CaveMode.TUNE && this.settings().freeTuneGuess()) {
         return 3;
      }
      return 2;
   }

   private void beginIntro() {
      this.prepareRound();
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.INTRO;
      this.ticksLeft = INTRO_TICKS;
      this.broadcast("&6第 &f" + (this.roundIndex + 1) + "&6/&f" + this.schedule.size()
         + " 轮 · &e" + this.mode.display());
      this.seatPlayers(false);
      this.pushBoard();
   }

   private void beginDescribe() {
      this.phase = Phase.DESCRIBE;
      this.describeTicks = 0;
      this.ticksLeft = this.mode.describeSeconds() * 20;
      this.seatPlayers(true);
      if (this.handler != null) {
         this.handler.onPrepare(this);
      }
      this.pushBoard();
   }

   private void beginReveal(boolean guessed) {
      if (this.phase == Phase.REVEAL || this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.REVEAL;
      this.ticksLeft = REVEAL_TICKS;
      if (this.handler != null) {
         this.handler.onSettle(this, guessed);
         this.handler.onCleanup(this);
      }
      this.arena.clearText(this.level());
      if (guessed || this.guessedThisRound) {
         this.broadcast("&6揭晓： &e" + this.word.word());
      } else {
         this.broadcast("&7超时无人猜对。答案是 &e" + this.word.word());
      }
      this.pushBoard();
   }

   private void nextRoundOrFinish() {
      this.roundIndex++;
      if (this.roundIndex >= this.schedule.size()) {
         this.finish();
         return;
      }
      this.beginIntro();
   }

   private void prepareRound() {
      this.correct.clear();
      this.eliminated.clear();
      this.uniqueClueOwners = List.of();
      this.tuneOptions = List.of();
      this.voidCount = 0;
      this.guessedThisRound = false;
      this.mode = this.schedule.get(this.roundIndex);
      if (!this.pickWord(this.mode)) {
         this.broadcast("&e" + this.mode.display() + " 词库不足，跳过该模式。");
         this.schedule.remove(this.roundIndex);
         if (this.roundIndex >= this.schedule.size()) {
            this.finish();
            return;
         }
         this.prepareRound();
         return;
      }
      this.assignRoles();
      this.ensureLayout();
      this.handler = this.mode.create();
   }

   private void trimSchedule() {
      List<CaveWord> tune = this.ctx.caveWords().tunePool(this.bank);
      if (tune.size() < 4) {
         this.schedule.removeIf(mode -> mode == CaveMode.TUNE);
         if (tune.size() < 4) {
            this.broadcast("&e曲调词不足 4 个，「那是什么调」已跳过。");
         }
      }
   }

   private boolean pickWord(CaveMode mode) {
      CaveWord picked = this.ctx.caveWords().pick(this.bank, this.usedWords, mode == CaveMode.TUNE);
      if (picked == null) {
         return false;
      }
      this.word = picked;
      this.usedWords.add(picked.word());
      if (mode == CaveMode.TUNE) {
         this.tuneOptions = this.ctx.caveWords().choicesFor(picked, this.bank);
      }
      return true;
   }

   private void assignRoles() {
      List<UUID> alive = this.alive();
      if (alive.isEmpty()) {
         this.performer = null;
         this.isolatedGuesser = null;
         return;
      }
      UUID rotated = alive.get(this.roundIndex % alive.size());
      if (this.mode == CaveMode.ONE_OR_NONE) {
         this.isolatedGuesser = rotated;
         this.performer = null;
      } else {
         this.performer = rotated;
         this.isolatedGuesser = null;
      }
   }

   private List<UUID> alive() {
      List<UUID> alive = new ArrayList<>();
      for (UUID uuid : this.seats) {
         if (this.remaining.contains(uuid)) {
            alive.add(uuid);
         }
      }
      return alive;
   }

   private void ensureLayout() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      if (this.laidOut != this.mode) {
         this.ctx.plots().clearInterior(level, this.plot);
         this.arena.build(level, this.mode);
         this.laidOut = this.mode;
      } else if (this.mode == CaveMode.SHADOW) {
         this.arena.resetShadow(level);
      } else {
         this.arena.clearText(level);
      }
   }

   private void seatPlayers(boolean describe) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int watchIndex = 0;
      int watchers = Math.max(1, this.remaining.size() - (this.performer != null && this.remaining.contains(this.performer) ? 1 : 0)
         - (this.isolatedGuesser != null && this.remaining.contains(this.isolatedGuesser) ? 1 : 0));
      if (this.mode == CaveMode.ONE_OR_NONE) {
         watchers = Math.max(1, this.remaining.size() - 1);
      }
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         player.closeContainer();
         player.getInventory().clearContent();
         player.setInvisible(false);
         this.ensureTeam(player);
         this.board.create(player, "&d洞穴猜猜乐");
         boolean isolated = this.mode == CaveMode.ONE_OR_NONE && this.isIsolatedGuesser(uuid);
         boolean performer = this.isPerformer(uuid);
         if (this.mode == CaveMode.SHADOW) {
            if (performer) {
               player.setGameMode(describe ? GameType.CREATIVE : GameType.ADVENTURE);
               this.arena.teleportStage(player, level);
            } else {
               player.setGameMode(GameType.ADVENTURE);
               this.arena.teleportViewing(player, level, watchIndex++, watchers);
            }
         } else if (performer || isolated) {
            player.setGameMode(GameType.ADVENTURE);
            this.arena.teleportBooth(player, level);
         } else {
            player.setGameMode(GameType.ADVENTURE);
            this.arena.teleportViewing(player, level, watchIndex++, watchers);
         }
         if (describe) {
            this.tellRole(player);
         }
      }
   }

   private void tellRole(ServerPlayer player) {
      UUID uuid = player.getUUID();
      this.ctx.send(player, "&6模式： &e" + this.mode.display());
      if (this.mode == CaveMode.ONE_OR_NONE) {
         if (this.isIsolatedGuesser(uuid)) {
            this.ctx.send(player, "&b你是猜测者。其他人为你提供唯一线索。");
         } else {
            this.ctx.send(player, "&a目标词： &e" + this.word.word() + " &7（不要告诉猜测者）");
         }
         return;
      }
      if (this.isPerformer(uuid)) {
         this.ctx.send(player, "&a目标词： &e" + this.word.word() + " &7（不要直接说出来）");
      } else {
         this.ctx.send(player, "&b你是猜测者。主题共 &f" + WordHint.label(this.word.word()));
      }
   }

   private void enforce() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int watchIndex = 0;
      int watchers = Math.max(1, this.remaining.size() - 1);
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         player.setInvisible(false);
         boolean performer = this.isPerformer(uuid);
         boolean isolated = this.isIsolatedGuesser(uuid);
         boolean inside;
         if (this.mode == CaveMode.SHADOW) {
            inside = player.serverLevel() == level && (performer
               ? this.arena.inStageArea(player.getX(), player.getY(), player.getZ())
               : this.arena.inViewing(player.getX(), player.getY(), player.getZ()));
         } else if (performer || isolated) {
            inside = player.serverLevel() == level && this.arena.inBooth(player.getX(), player.getY(), player.getZ());
         } else {
            inside = player.serverLevel() == level && this.arena.inViewing(player.getX(), player.getY(), player.getZ());
         }
         if (inside) {
            if (!performer && !isolated) {
               watchIndex++;
            }
            continue;
         }
         if (this.mode == CaveMode.SHADOW && performer) {
            this.arena.teleportStage(player, level);
         } else if (performer || isolated) {
            this.arena.teleportBooth(player, level);
         } else {
            this.arena.teleportViewing(player, level, watchIndex++, watchers);
         }
      }
   }

   private void pushBoard() {
      List<UUID> ranked = new ArrayList<>(this.remaining);
      ranked.sort(Comparator.comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed());
      String clock = this.clock();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7&m---------------");
         lines.add("&7轮次 &f" + Math.min(this.roundIndex + 1, this.schedule.size()) + "&7/&f" + this.schedule.size());
         lines.add("&e" + this.mode.display());
         if (this.phase == Phase.DESCRIBE) {
            if (this.isPerformer(uuid) || (this.mode != CaveMode.ONE_OR_NONE && this.isPerformer(uuid))) {
               lines.add("&a目标 &f" + this.word.word());
            } else if (this.mode == CaveMode.ONE_OR_NONE && !this.isIsolatedGuesser(uuid)) {
               lines.add("&a目标 &f" + this.word.word());
            } else {
               lines.add("&b猜词 &f" + WordHint.label(this.word.word()));
            }
            lines.add("&7剩余 &f" + clock);
            if (this.handler != null) {
               lines.addAll(this.handler.boardExtra(this, uuid));
            }
         } else if (this.phase == Phase.INTRO) {
            lines.add("&7准备开始");
         } else {
            lines.add("&6揭晓 &e" + this.word.word());
            lines.add("&7下轮 &f" + clock);
         }
         lines.add("&7&m---------------");
         int shown = 0;
         for (UUID row : ranked) {
            if (shown >= 6) {
               break;
            }
            lines.add((row.equals(uuid) ? "&a" : "&f") + this.ctx.name(row) + " &e" + this.scores.getOrDefault(row, 0));
            shown++;
         }
         this.board.update(player, lines);
      }
   }

   private void actionBars() {
      String clock = this.clock();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         String msg = "&7" + this.mode.display() + " &8| &f" + clock;
         if (this.phase == Phase.DESCRIBE && this.handler != null) {
            String extra = this.handler.actionBar(this, uuid);
            if (extra != null) {
               msg = extra + " &8| &7" + clock;
            }
         } else if (this.phase == Phase.REVEAL) {
            msg = "&6揭晓 &e" + this.word.word() + " &8| &7" + clock;
         }
         player.displayClientMessage(TextUtil.color(msg), true);
      }
   }

   private String clock() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
   }

   private void finish() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      if (this.handler != null) {
         this.handler.onCleanup(this);
      }
      if (this.begun) {
         List<UUID> ranked = new ArrayList<>(this.seats);
         ranked.sort(Comparator.comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed());
         this.broadcast("&d&l洞穴猜猜乐结算");
         int place = 1;
         for (UUID uuid : ranked) {
            this.broadcast("&8" + place + ". &f" + this.ctx.name(uuid)
               + " &e" + this.scores.getOrDefault(uuid, 0) + " 分");
            place++;
         }
         if (!ranked.isEmpty()) {
            this.broadcast("&6冠军： &e" + this.ctx.name(ranked.get(0))
               + " &7（" + this.scores.getOrDefault(ranked.get(0), 0) + " 分）");
         }
      }
      this.board.removeAll();
      this.arena.clearText(this.level());
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.plots().release(List.of(this.plot));
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.caveGuess().remove(this);
   }

   private void ensureTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayerTeam(TEAM_NAME);
      if (team == null) {
         team = board.addPlayerTeam(TEAM_NAME);
         team.setCollisionRule(Team.CollisionRule.NEVER);
         team.setNameTagVisibility(Team.Visibility.ALWAYS);
         team.setSeeFriendlyInvisibles(true);
      }
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void restore(ServerPlayer player) {
      Scoreboard scoreboard = this.ctx.server().getScoreboard();
      PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
      if (current != null) {
         scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
      }
      player.setInvisible(false);
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

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos, float yaw, float pitch, GameType gameType, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>();
         Inventory inv = player.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) {
            items.add(inv.getItem(i).copy());
         }
         return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
            player.gameMode.getGameModeForPlayer(), items);
      }

      void apply(ServerPlayer player, GameContext ctx) {
         ServerLevel level = ctx.server().getLevel(this.dimension);
         if (level == null) {
            level = ctx.server().overworld();
         }
         player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
         player.setGameMode(this.gameType);
         Inventory inv = player.getInventory();
         inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), this.items.size()); i++) {
            inv.setItem(i, this.items.get(i).copy());
         }
      }
   }
}
