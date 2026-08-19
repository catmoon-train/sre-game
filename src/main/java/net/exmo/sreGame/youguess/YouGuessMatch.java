package net.exmo.sreGame.youguess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.Plot;
import net.exmo.sreGame.buildwar.WordBank;
import net.exmo.sreGame.draw.Canvas;
import net.exmo.sreGame.draw.DrawKit;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.util.WordHint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class YouGuessMatch {
   public enum Phase {
      PICKING,
      PLAYING,
      REVEAL,
      ENDED
   }

   public static final String TEAM_NAME = "sreyg";
   private static final int REVEAL_SECONDS = 6;
   private static final int PICK_SECONDS = 20;
   private static final int BUILDER_PEAK_PER_PLAYER = 2;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Set<UUID> remaining = ConcurrentHashMap.newKeySet();
   private final Plot plot;
   private final Canvas canvas;
   private final boolean drawing;
   private final List<String> words;
   private final int totalRounds;
   private final int buildTicks;
   private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<UUID> correctOrder = new ArrayList<>();
   private final SidebarBoard board;
   private Phase phase = Phase.PLAYING;
   private int round = 1;
   private int ticksLeft;
   private int boardTicks;
   private UUID builder;
   private String word = "星空";
   private String fallbackWord = "星空";
   private boolean wordLocked;
   private boolean begun;
   private List<String> pickChoices = List.of();
   private final Set<String> offeredWords = new HashSet<>();

   public YouGuessMatch(GameContext ctx, GameRoom room, List<UUID> seats, Plot plot, List<String> words, boolean drawing) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.remaining.addAll(seats);
      this.plot = plot;
      this.canvas = Canvas.of(plot);
      this.drawing = drawing;
      this.words = words.isEmpty() ? List.of("星空") : List.copyOf(words);
      this.totalRounds = room.youGuessSettings().resolvedRounds(seats.size());
      this.buildTicks = room.youGuessSettings().buildSeconds() * 20;
      this.board = new SidebarBoard(ctx.server());
      for (UUID uuid : seats) {
         this.scores.put(uuid, 0);
      }
   }

   public UUID id() {
      return this.id;
   }

   public Phase phase() {
      return this.phase;
   }

   public boolean contains(UUID uuid) {
      return this.remaining.contains(uuid);
   }

   public boolean isBuilder(UUID uuid) {
      return uuid != null && uuid.equals(this.builder);
   }

   public boolean drawing() {
      return this.drawing;
   }

   public Canvas canvas() {
      return this.canvas;
   }

   public boolean canPaint(UUID uuid) {
      return this.drawing && this.phase == Phase.PLAYING && this.isBuilder(uuid);
   }

   public Plot plot() {
      return this.plot;
   }

   public String themeWord() {
      return this.drawing || this.phase != Phase.PLAYING || !this.wordLocked ? null : this.word;
   }

   public void start() {
      this.begun = true;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.saved.put(uuid, Saved.capture(player));
            this.board.create(player, this.title());
         }
      }
      this.beginRound();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      this.actionBars();
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      if (this.phase == Phase.PICKING) {
         if (this.ticksLeft <= 0) {
            this.lockFallbackIfNeeded();
            this.beginPlaying();
         }
         return;
      }
      if (this.phase == Phase.PLAYING && this.allGuessersCorrect()) {
         this.beginReveal();
         return;
      }
      if (this.ticksLeft <= 0) {
         if (this.phase == Phase.PLAYING) {
            this.beginReveal();
         } else if (this.phase == Phase.REVEAL) {
            this.nextRoundOrFinish();
         }
      }
   }

   public boolean handleGuess(ServerPlayer player, String text) {
      if (this.phase == Phase.PICKING) {
         return this.handleThemeChat(player, text);
      }
      if (this.phase != Phase.PLAYING || !this.remaining.contains(player.getUUID())) {
         return false;
      }
      if (this.isBuilder(player.getUUID())) {
         this.ctx.send(player, this.drawing ? "&7绘画中请不要在聊天泄露主题。" : "&7建造中请不要在聊天泄露主题。");
         return true;
      }
      String guess = text == null ? "" : text.trim();
      if (guess.isEmpty() || guess.length() > 32) {
         this.ctx.send(player, "&c请输入你猜的主题词。");
         return true;
      }
      if (this.correctOrder.contains(player.getUUID())) {
         String after = text == null ? "" : text.trim();
         if (!after.isEmpty()) {
            String line = "&a[已猜] &f" + player.getGameProfile().getName() + "&7： &f" + after;
            for (UUID uuid : this.correctOrder) {
               this.sendGuessChat(uuid, line);
            }
            this.sendGuessChat(this.builder, line);
         }
         return true;
      }
      if (!guess.equalsIgnoreCase(this.word)) {
         this.ctx.broadcast(this.room, "&e" + player.getGameProfile().getName() + " &7猜： &f" + guess);
         this.ctx.send(player, "&c不对，再看看。");
         return true;
      }
      this.correctOrder.add(player.getUUID());
      int rank = this.correctOrder.size();
      int points = guesserPoints(rank);
      this.addScore(player.getUUID(), points);
      this.ctx.broadcast(this.room, "&a" + player.getGameProfile().getName()
         + " &a猜对了！&7第 &f" + rank + " &7名 &8+&e" + points);
      this.pushBoard();
      return true;
   }

   private void sendGuessChat(UUID uuid, String line) {
      if (uuid == null || !this.remaining.contains(uuid)) {
         return;
      }
      this.ctx.send(this.ctx.player(uuid), line);
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
      DrawKit.clear(uuid);
      if (!this.begun) {
         if (this.remaining.size() < 2) {
            this.finish();
         }
         return;
      }
      if (this.remaining.size() < 2) {
         this.ctx.broadcast(this.room, "&c人数不足，提前结算。");
         this.finish();
         return;
      }
      if (this.isBuilder(uuid) && this.phase == Phase.PLAYING) {
         this.ctx.broadcast(this.room, this.drawing ? "&c画手离开，本轮结束。" : "&c建造者离开，本轮结束。");
         this.beginReveal();
      } else if (this.isBuilder(uuid) && this.phase == Phase.PICKING) {
         this.builder = this.pickBuilder();
         this.wordLocked = false;
         this.ticksLeft = PICK_SECONDS * 20;
         this.ctx.broadcast(this.room, "&e" + (this.drawing ? "画手" : "建造者")
            + "已更换为 &f" + this.ctx.name(this.builder) + " &e，请重新输入主题。");
         this.beginPicking();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish();
      }
   }

   private boolean handleThemeChat(ServerPlayer player, String text) {
      if (!this.remaining.contains(player.getUUID())) {
         return false;
      }
      if (!this.isBuilder(player.getUUID())) {
         return false;
      }
      if (this.wordLocked) {
         this.ctx.send(player, "&a主题已选定： &e" + this.word);
         return true;
      }
      String word = text == null ? "" : text.trim();
      if (word.isEmpty() || word.length() > 32) {
         this.ctx.send(player, "&c请输入 1–32 个字的主题词。");
         return true;
      }
      if (!this.pickChoices.isEmpty()) {
         int index = WordBank.indexOfChoice(word, this.pickChoices);
         if (index < 0) {
            this.ctx.send(player, "&c请输入 &f1–" + this.pickChoices.size() + " &c或完整主题词。");
            return true;
         }
         word = this.pickChoices.get(index);
      }
      this.word = word;
      this.wordLocked = true;
      this.ctx.send(player, "&a已选定主题： &e" + word + " &7（不要告诉别人）");
      this.beginPlaying();
      return true;
   }

   private void beginRound() {
      this.correctOrder.clear();
      this.builder = this.pickBuilder();
      this.fallbackWord = this.words.get((this.round - 1) % this.words.size());
      this.wordLocked = false;
      this.pickChoices = List.of();
      if (this.room.youGuessSettings().customTheme()) {
         this.beginPicking();
      } else if (this.room.youGuessSettings().pickFromThree()) {
         this.pickChoices = WordBank.pickUnique(this.room.resolvedWords(this.ctx), this.offeredWords, 3);
         this.beginPicking();
      } else {
         this.word = this.fallbackWord;
         this.wordLocked = true;
         this.beginPlaying();
      }
   }

   private void beginPicking() {
      this.phase = Phase.PICKING;
      this.ticksLeft = PICK_SECONDS * 20;
      ServerLevel level = this.ctx.plots().level();
      boolean three = !this.pickChoices.isEmpty();
      this.ctx.broadcast(this.room, "&e第 &f" + this.round + "&e/&f" + this.totalRounds
         + " &e轮选词：&f" + this.ctx.name(this.builder)
         + (three
            ? (this.drawing ? " &e请从三个绘画主题中挑选，" : " &e请从三个建造主题中挑选，")
            : (this.drawing ? " &e请在聊天输入绘画主题，" : " &e请在聊天输入建造主题，"))
         + "&f" + PICK_SECONDS + " &e秒后超时随机。");
      int watchIndex = 0;
      int watchers = Math.max(1, this.remaining.size() - 1);
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null || level == null) {
            continue;
         }
         player.closeContainer();
         player.getInventory().clearContent();
         player.setInvisible(false);
         this.ensureTeam(player);
         this.board.create(player, this.title());
         if (uuid.equals(this.builder)) {
            player.setGameMode(GameType.ADVENTURE);
            if (this.drawing) {
               this.plot.teleportCanvas(player, level);
               DrawKit.startFlying(player);
            } else {
               this.plot.teleport(player, level);
            }
            if (three) {
               this.givePickItems(player, this.pickChoices);
               this.ctx.send(player, "&a请三选一（聊天 &f1/2/3 &a或右键）：");
               for (int i = 0; i < this.pickChoices.size(); i++) {
                  this.ctx.send(player, "&e" + (i + 1) + ". &f" + this.pickChoices.get(i));
               }
            } else {
               this.ctx.send(player, "&a请输入本轮主题词。超时将随机。");
            }
         } else {
            player.setGameMode(GameType.SPECTATOR);
            if (this.drawing) {
               this.plot.teleportCanvasWatch(player, level, watchIndex++, watchers);
            } else {
               this.plot.teleportWatch(player, level, watchIndex++, watchers);
            }
            this.ctx.send(player, this.drawing ? "&7请等待画手选定主题。" : "&7请等待建造者选定主题。");
         }
      }
      this.pushBoard();
   }

   private void givePickItems(ServerPlayer player, List<String> options) {
      String[] icons = {"paper", "map", "name_tag"};
      for (int i = 0; i < options.size() && i < 3; i++) {
         player.getInventory().setItem(i, GuiItems.action(icons[i],
            "&e" + (i + 1) + ". &f" + options.get(i),
            List.of("&e右键选择该主题", "&7不要告诉别人"),
            "pick-theme", "i", String.valueOf(i)));
      }
   }

   public boolean handlePickItem(ServerPlayer player, ItemStack stack) {
      if (this.phase != Phase.PICKING || !"pick-theme".equals(GuiItems.actionTag(stack))) {
         return false;
      }
      String raw = GuiItems.extraTag(stack, "i");
      if (raw == null) {
         return false;
      }
      try {
         return this.handleThemeChat(player, String.valueOf(Integer.parseInt(raw) + 1));
      } catch (NumberFormatException e) {
         return false;
      }
   }

   private void lockFallbackIfNeeded() {
      if (this.wordLocked) {
         return;
      }
      if (!this.pickChoices.isEmpty()) {
         this.fallbackWord = this.pickChoices.get(ThreadLocalRandom.current().nextInt(this.pickChoices.size()));
      }
      this.word = this.fallbackWord;
      this.wordLocked = true;
      ServerPlayer builder = this.ctx.player(this.builder);
      if (builder != null) {
         this.ctx.send(builder, "&7超时未输入，主题随机为： &e" + this.word);
      }
   }

   private void beginPlaying() {
      this.phase = Phase.PLAYING;
      this.ticksLeft = this.buildTicks;
      ServerLevel level = this.ctx.plots().level();
      if (level != null) {
         this.ctx.plots().clearInterior(level, this.plot);
         if (this.drawing) {
            this.canvas.install(level);
         }
      }
      this.ctx.broadcast(this.room, "&6第 &f" + this.round + "&6/&f" + this.totalRounds
         + " &6轮：&e" + this.ctx.name(this.builder) + (this.drawing ? " &6正在绘画，其他人旁观猜词。" : " &6正在建造，其他人旁观猜词。"));
      int watchIndex = 0;
      int watchers = Math.max(1, this.remaining.size() - 1);
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null || level == null) {
            continue;
         }
         player.closeContainer();
         player.getInventory().clearContent();
         player.setInvisible(false);
         this.ensureTeam(player);
         this.board.create(player, this.title());
         if (uuid.equals(this.builder)) {
            if (this.drawing) {
               this.plot.teleportCanvas(player, level);
               player.setGameMode(GameType.ADVENTURE);
               DrawKit.give(player);
            } else {
               this.plot.teleport(player, level);
               player.setGameMode(GameType.CREATIVE);
            }
            this.ctx.send(player, "&a你的主题： &e" + this.word + " &7（不要告诉别人）");
            player.displayClientMessage(TextUtil.color("&a主题： &f" + this.word), false);
         } else {
            player.setGameMode(GameType.SPECTATOR);
            if (this.drawing) {
               this.plot.teleportCanvasWatch(player, level, watchIndex++, watchers);
            } else {
               this.plot.teleportWatch(player, level, watchIndex++, watchers);
            }
            this.ctx.send(player, "&b旁观猜词：主题共 &f" + WordHint.label(this.word)
               + " &b。在聊天框输入你认为的主题。猜对有分，越早越高。");
         }
      }
      this.pushBoard();
   }

   private void beginReveal() {
      this.phase = Phase.REVEAL;
      this.ticksLeft = REVEAL_SECONDS * 20;
      int correct = this.correctOrder.size();
      int guessers = 0;
      for (UUID uuid : this.remaining) {
         if (!uuid.equals(this.builder)) {
            guessers++;
         }
      }
      int builderGain = builderPoints(correct, guessers, this.remaining.size());
      if (this.builder != null && this.remaining.contains(this.builder) && builderGain > 0) {
         this.addScore(this.builder, builderGain);
      }
      int percent = guessers == 0 ? 0 : (int) Math.round(100.0 * correct / guessers);
      this.ctx.broadcast(this.room, "&6揭晓：主题是 &e" + this.word
         + " &8| &7猜对 &f" + correct + "&7/&f" + guessers + " &8(" + percent + "%)"
         + (this.builder == null ? "" : (this.drawing ? " &8| &e画手 +" : " &8| &e建造者 +") + builderGain + " &7（50% 最高）"));
      this.pushBoard();
   }

   private void nextRoundOrFinish() {
      if (this.round >= this.totalRounds) {
         this.finish();
         return;
      }
      this.round++;
      this.beginRound();
   }

   private UUID pickBuilder() {
      List<UUID> alive = new ArrayList<>();
      for (UUID uuid : this.seats) {
         if (this.remaining.contains(uuid)) {
            alive.add(uuid);
         }
      }
      if (alive.isEmpty()) {
         return null;
      }
      return alive.get((this.round - 1) % alive.size());
   }

   private boolean allGuessersCorrect() {
      for (UUID uuid : this.remaining) {
         if (!uuid.equals(this.builder) && !this.correctOrder.contains(uuid)) {
            return false;
         }
      }
      return this.remaining.size() > 1;
   }

   private static int guesserPoints(int rank) {
      return Math.max(1, 6 - rank);
   }

   /** 猜对比例的抛物线：0% 与 100% 最低，50% 最高；峰值 = 总人数 × 2。 */
   static int builderPoints(int correct, int guessers, int totalPlayers) {
      if (guessers <= 0 || totalPlayers <= 0) {
         return 0;
      }
      double ratio = correct / (double) guessers;
      if (guessers == 1) {
         ratio = correct >= 1 ? 0.5 : 0.0;
      }
      double wave = 4.0 * ratio * (1.0 - ratio);
      int peak = totalPlayers * BUILDER_PEAK_PER_PLAYER;
      return (int) Math.round(peak * wave);
   }

   private void addScore(UUID uuid, int delta) {
      this.scores.merge(uuid, delta, Integer::sum);
   }

   private void finish() {
      this.phase = Phase.ENDED;
      if (this.begun) {
         List<UUID> ranked = new ArrayList<>(this.seats);
         ranked.sort(Comparator.comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed());
         this.ctx.broadcast(this.room, this.drawing ? "&d&l你画我猜结算" : "&d&l你建我猜结算");
         int place = 1;
         for (UUID uuid : ranked) {
            this.ctx.broadcast(this.room, "&8" + place + ". &f" + this.ctx.name(uuid)
               + " &e" + this.scores.getOrDefault(uuid, 0) + " 分");
            place++;
         }
         if (!ranked.isEmpty()) {
            this.ctx.broadcast(this.room, "&6胜者： &e" + this.ctx.name(ranked.get(0))
               + " &7（" + this.scores.getOrDefault(ranked.get(0), 0) + " 分）");
         }
      }
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.plots().release(List.of(this.plot));
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.youGuess().remove(this);
   }

   private void pushBoard() {
      List<UUID> ranked = new ArrayList<>(this.remaining);
      ranked.sort(Comparator.comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed());
      int seconds = Math.max(0, this.ticksLeft / 20);
      String clock = (seconds / 60) + ":" + String.format("%02d", seconds % 60);
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7&m---------------");
         lines.add("&7轮次 &f" + this.round + "&7/&f" + this.totalRounds);
         lines.add((this.drawing ? "&7画手 &e" : "&7建造 &e") + this.ctx.name(this.builder));
         if (this.phase == Phase.PLAYING) {
            lines.add(this.isBuilder(uuid) ? "&a主题 &f" + this.word : "&b聊天猜词 &f" + WordHint.label(this.word));
            lines.add("&7剩余 &f" + clock);
         } else if (this.phase == Phase.PICKING) {
            lines.add(this.isBuilder(uuid)
               ? (this.wordLocked ? "&a已选 &f" + this.word : "&e聊天输入主题")
               : "&7等待选词");
            lines.add("&7剩余 &f" + clock);
         } else {
            lines.add("&6揭晓 &e" + this.word);
            lines.add("&7下轮 &f" + clock);
         }
         lines.add("&7&m---------------");
         int shown = 0;
         for (UUID row : ranked) {
            if (shown >= 8) {
               break;
            }
            boolean self = row.equals(uuid);
            lines.add((self ? "&a" : "&f") + this.ctx.name(row) + " &e" + this.scores.getOrDefault(row, 0));
            shown++;
         }
         lines.add("&7&m---------------");
         this.board.update(player, lines);
      }
   }

   private void actionBars() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      String clock = (seconds / 60) + ":" + String.format("%02d", seconds % 60);
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         String msg;
         if (this.phase == Phase.PLAYING) {
            msg = this.isBuilder(uuid)
               ? (this.drawing ? "&a绘画 &f" : "&a建造 &f") + this.word + " &8| &7" + clock
               : "&b猜词 &f" + WordHint.label(this.word) + " &8| &7" + clock + (this.correctOrder.contains(uuid) ? " &a已猜对" : "");
         } else if (this.phase == Phase.PICKING) {
            msg = this.isBuilder(uuid)
               ? (this.wordLocked ? "&a已选 &f" + this.word + " &8| &7" + clock : "&e输入主题词 &8| &7" + clock)
               : "&7等待选定主题 &8| &7" + clock;
         } else {
            msg = "&6揭晓 &e" + this.word + " &8| &7" + clock;
         }
         player.displayClientMessage(TextUtil.color(msg), true);
      }
   }

   private void enforce() {
      ServerLevel level = this.ctx.plots().level();
      if (level == null) {
         return;
      }
      int watchIndex = 0;
      int watchers = Math.max(1, this.remaining.size() - (this.builder != null && this.remaining.contains(this.builder) ? 1 : 0));
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         player.setInvisible(false);
         if (this.drawing && this.isBuilder(uuid)
            && (this.phase == Phase.PLAYING || this.phase == Phase.PICKING)) {
            DrawKit.allowFlight(player);
         }
         boolean watcher = !uuid.equals(this.builder);
         if (watcher && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
         }
         boolean inside = player.serverLevel() == level && (watcher
            ? this.plot.containsWatch(player.getX(), player.getY(), player.getZ())
            : this.plot.contains(player.getX(), player.getY(), player.getZ()));
         if (inside) {
            if (!uuid.equals(this.builder)) {
               watchIndex++;
            }
            continue;
         }
         if (uuid.equals(this.builder)) {
            if (this.drawing) {
               this.plot.teleportCanvas(player, level);
            } else {
               this.plot.teleport(player, level);
            }
         } else if (this.drawing) {
            this.plot.teleportCanvasWatch(player, level, watchIndex++, watchers);
         } else {
            this.plot.teleportWatch(player, level, watchIndex++, watchers);
         }
      }
   }

   private String title() {
      return this.drawing ? "&d你画我猜" : "&d你建我猜";
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
      DrawKit.clear(player.getUUID());
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
