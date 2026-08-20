package net.exmo.sreGame.games.buildwar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.draw.Canvas;
import net.exmo.sreGame.games.draw.DrawKit;
import net.exmo.sreGame.gui.BuildWarVoteGui;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.util.WordHint;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class BuildWarMatch {
   public enum Phase {
      PICKING,
      BUILDING,
      GUESSING,
      REVIEW,
      SCORING,
      ENDED
   }

   public static final String TEAM_NAME = "srebw";
   public static final int WATCH_SECONDS = 10;
   public static final int SCORE_SECONDS = 15;
   public static final int PICK_SECONDS = 20;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final List<BuildGroup> groups;
   private final Map<UUID, BuildGroup> groupOf = new HashMap<>();
   private final Set<UUID> remaining = ConcurrentHashMap.newKeySet();
   private final Map<UUID, String> lastGuess = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Map<UUID, Map<Integer, Integer>> votes = new ConcurrentHashMap<>();
   private final Set<UUID> scoredCurrent = ConcurrentHashMap.newKeySet();
   private final Set<Integer> customPicked = ConcurrentHashMap.newKeySet();
   private final BuildWarSettings settings;
   private final boolean drawing;
   private final int totalRounds;
   private final int guessTicks;
   private Phase phase = Phase.BUILDING;
   private int round = 1;
   private int ticksLeft;
   private int reviewGroup;
   private int reviewSnap;
   private int[] homeBuild = new int[0];
   private int[] homeGuess = new int[0];
   private final List<Set<Integer>> builtThemes = new ArrayList<>();
   private final List<Set<Integer>> guessedThemes = new ArrayList<>();
   private final List<List<Integer>> buildSeq = new ArrayList<>();
   private final List<List<Integer>> guessSeq = new ArrayList<>();
   private final List<Set<String>> seenWords = new ArrayList<>();
   private UUID singleBuilder;
   private UUID singleGuesser;
   private final Set<UUID> singleHasBuilt = ConcurrentHashMap.newKeySet();
   private final Set<UUID> singleHasGuessed = ConcurrentHashMap.newKeySet();
   private final Map<Integer, List<String>> pickChoices = new HashMap<>();
   private final Set<String> offeredWords = new HashSet<>();
   private boolean begun;

   public BuildWarMatch(GameContext ctx, GameRoom room, List<UUID> seats, List<Plot> claimed, List<String> startingWords, boolean drawing) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.remaining.addAll(seats);
      this.settings = room.buildWarSettings();
      this.drawing = drawing;
      this.totalRounds = this.settings.rounds();
      this.guessTicks = this.settings.guessSeconds() * 20;
      int themeCount = Math.max(1, Math.min(seats.size(), claimed.size()));
      boolean share = this.settings.extraBuildTogether();
      List<BuildGroup> built = new ArrayList<>(themeCount);
      for (int i = 0; i < themeCount; i++) {
         String word = i < startingWords.size() && startingWords.get(i) != null && !startingWords.get(i).isBlank()
            ? startingWords.get(i) : "星空";
         built.add(new BuildGroup(i, claimed.get(i), word));
      }
      for (int i = 0; i < seats.size(); i++) {
         UUID uuid = seats.get(i);
         BuildGroup group = built.get(i % themeCount);
         if (share || i < themeCount) {
            group.addMember(uuid);
         }
         this.groupOf.put(uuid, group);
      }
      this.groups = List.copyOf(built);
      for (int i = 0; i < this.groups.size(); i++) {
         this.builtThemes.add(new HashSet<>());
         this.guessedThemes.add(new HashSet<>());
         this.buildSeq.add(new ArrayList<>());
         this.guessSeq.add(new ArrayList<>());
         this.seenWords.add(new HashSet<>());
      }
   }

   public UUID id() {
      return this.id;
   }

   public GameRoom room() {
      return this.room;
   }

   public Phase phase() {
      return this.phase;
   }

   public List<UUID> seats() {
      return this.seats;
   }

   public List<BuildGroup> groups() {
      return this.groups;
   }

   public BuildGroup groupOf(UUID uuid) {
      return this.groupOf.get(uuid);
   }

   public boolean isBuilder(UUID uuid) {
      if (uuid == null) {
         return false;
      }
      if (this.singleChain()) {
         if (this.phase == Phase.GUESSING) {
            return uuid.equals(this.singleGuesser);
         }
         return uuid.equals(this.singleBuilder);
      }
      BuildGroup group = this.groupOf.get(uuid);
      return group != null && group.contains(uuid);
   }

   public boolean drawing() {
      return this.drawing;
   }

   public boolean canPaint(UUID uuid) {
      return this.drawing && this.phase == Phase.BUILDING && this.isBuilder(uuid);
   }

   public BuildGroup reviewingGroup() {
      if (this.reviewGroup < 0 || this.reviewGroup >= this.groups.size()) {
         return null;
      }
      return this.groups.get(this.reviewGroup);
   }

   public boolean contains(UUID uuid) {
      return this.remaining.contains(uuid);
   }

   public Plot boundPlot(UUID uuid) {
      if (this.phase == Phase.REVIEW || this.phase == Phase.SCORING) {
         BuildGroup current = this.reviewingGroup();
         return current == null ? null : current.plot();
      }
      if (this.phase == Phase.GUESSING) {
         BuildGroup view = this.guessTheme(uuid);
         return view == null ? null : view.plot();
      }
      if (this.phase == Phase.BUILDING) {
         BuildGroup theme = this.buildTheme(uuid);
         return theme == null ? null : theme.plot();
      }
      BuildGroup home = this.groupOf.get(uuid);
      return home == null ? null : home.plot();
   }

   public String themeWord(UUID uuid) {
      if (this.drawing || this.phase != Phase.BUILDING) {
         return null;
      }
      BuildGroup theme = this.buildTheme(uuid);
      return theme == null ? null : theme.currentWord();
   }

   public List<String> historyOf(UUID uuid) {
      BuildGroup group = this.groupOf.get(uuid);
      return group == null ? List.of() : group.wordChain();
   }

   public int voteScore(UUID voter, int groupId) {
      Map<Integer, Integer> map = this.votes.get(voter);
      if (map == null) {
         return 0;
      }
      return map.getOrDefault(groupId, 0);
   }

   public void setVote(UUID voter, int groupId, int score) {
      this.votes.computeIfAbsent(voter, id -> new ConcurrentHashMap<>()).put(groupId, Math.max(1, Math.min(5, score)));
      this.scoredCurrent.add(voter);
   }

   public boolean hasScoredCurrent(UUID uuid) {
      return this.scoredCurrent.contains(uuid);
   }

   public void start() {
      this.begun = true;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.saved.put(uuid, Saved.capture(player));
         }
      }
      this.reshuffleRoles();
      if (this.singleChain()) {
         this.ctx.broadcast(this.room, "&6本局 &f1 &6个主题，共 &f" + this.totalRounds + " &6轮。"
            + (this.drawing
               ? " &7每轮随机抽人画，猜出的词留给这个主题，下一轮再随机抽人画。"
               : " &7每轮随机抽人建，猜出的词留给这个主题，下一轮再随机抽人建。"));
      } else {
         int builders = 0;
         for (BuildGroup group : this.groups) {
            builders += group.members().size();
         }
         int extras = Math.max(0, this.seats.size() - builders);
         this.ctx.broadcast(this.room, "&6本局 &f" + this.groups.size() + " &6个主题，共 &f" + this.totalRounds + " &6轮。"
            + (this.drawing
               ? " &7每轮随机分配你去画哪个主题。"
               : " &7每轮随机分配你去建哪个主题。")
            + " &7同一把尽量不重复建、不重复猜，也不会猜自己建过的主题。"
            + " &7结算按主题从开局到结束。"
            + (extras > 0 ? (this.settings.extraBuildTogether()
               ? (this.drawing ? " &7多余人数一起画。" : " &7多余人数一起建。")
               : " &7多余 &f" + extras + " &7人旁观。") : ""));
      }
      if (this.settings.customTheme() || this.settings.pickFromThree()) {
         this.beginPicking();
      } else {
         this.beginBuilding();
      }
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.enforce();
      this.actionBars();
      if (this.phase == Phase.SCORING && this.scoredCurrent.containsAll(this.remaining) && !this.remaining.isEmpty()) {
         this.advance();
         return;
      }
      if (this.ticksLeft <= 0) {
         this.advance();
      }
   }

   public boolean handleThemeChat(ServerPlayer player, String text) {
      if (this.phase != Phase.PICKING || !this.remaining.contains(player.getUUID()) || !this.isBuilder(player.getUUID())) {
         return false;
      }
      String word = text == null ? "" : text.trim();
      if (word.isEmpty() || word.length() > 32) {
         this.ctx.send(player, "&c请输入 1–32 个字的主题词。");
         return true;
      }
      BuildGroup group = this.buildTheme(player.getUUID());
      if (group == null) {
         return true;
      }
      if (this.customPicked.contains(group.id())) {
         this.ctx.send(player, "&a本组已选定主题： &e" + group.currentWord());
         return true;
      }
      List<String> options = this.pickChoices.get(group.id());
      if (options != null && !options.isEmpty()) {
         int index = WordBank.indexOfChoice(word, options);
         if (index < 0) {
            this.ctx.send(player, "&c请输入 &f1–" + options.size() + " &c或完整主题词。");
            return true;
         }
         word = options.get(index);
      }
      group.setOpeningWord(word);
      this.customPicked.add(group.id());
      this.notifyAssigned(group, "&a已选定主题： &e" + word + " &7（不要告诉别人）");
      if (this.allThemesPicked()) {
         this.applyFallbackThemes();
         this.beginBuilding();
      }
      return true;
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

   public boolean handleGuess(ServerPlayer player, String text) {
      if (this.phase != Phase.GUESSING || !this.remaining.contains(player.getUUID())) {
         return false;
      }
      String guess = text == null ? "" : text.trim();
      if (guess.isEmpty() || guess.length() > 32) {
         this.ctx.send(player, "&c请输入 1–32 个字的主题词。");
         return true;
      }
      if (this.singleChain() && player.getUUID().equals(this.singleBuilder)) {
         this.ctx.send(player, this.drawing
            ? "&7你本轮刚画完，由 &f" + this.ctx.name(this.singleGuesser) + " &7猜词。"
            : "&7你本轮刚建完，由 &f" + this.ctx.name(this.singleGuesser) + " &7猜词。");
         return true;
      }
      this.lastGuess.put(player.getUUID(), guess);
      if (this.isBuilder(player.getUUID())) {
         this.ctx.send(player, "&a已记录猜词： &f" + guess);
      } else {
         this.ctx.send(player, "&a已记录（旁观，不影响下一轮主题）： &f" + guess);
      }
      if (this.allBuildersGuessed()) {
         this.ticksLeft = 0;
      }
      return true;
   }

   public boolean handleScoreChat(ServerPlayer player, String text) {
      if (this.phase != Phase.SCORING || !this.remaining.contains(player.getUUID())) {
         return false;
      }
      String trimmed = text == null ? "" : text.trim();
      if (!trimmed.matches("[1-5]")) {
         return false;
      }
      this.acceptScore(player, Integer.parseInt(trimmed));
      return true;
   }

   public boolean handleScoreItem(ServerPlayer player, ItemStack stack) {
      if (this.phase != Phase.SCORING || !this.remaining.contains(player.getUUID())) {
         return false;
      }
      String raw = GuiItems.extraTag(stack, "score");
      if (raw == null) {
         return false;
      }
      try {
         this.acceptScore(player, Integer.parseInt(raw));
         return true;
      } catch (NumberFormatException e) {
         return false;
      }
   }

   public void acceptScore(ServerPlayer player, int score) {
      BuildGroup group = this.reviewingGroup();
      if (group == null) {
         return;
      }
      int safe = Math.max(1, Math.min(5, score));
      this.setVote(player.getUUID(), group.id(), safe);
      this.ctx.send(player, "&a已给主题 &f" + (group.id() + 1) + " &a打 &e" + safe + " &a分。");
   }

   public void onLeave(UUID uuid) {
      if (!this.remaining.remove(uuid)) {
         return;
      }
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      }
      if (!this.begun) {
         if (this.remaining.size() < 3) {
            this.finish();
         }
         return;
      }
      if (this.remaining.isEmpty()) {
         this.finish();
         return;
      }
      if (this.remaining.size() < 3 && this.phase != Phase.ENDED
         && this.phase != Phase.REVIEW && this.phase != Phase.SCORING) {
         this.ctx.broadcast(this.room, "&c人数不足 3 人，进入" + (this.drawing ? "画作链" : "建筑链") + "回放与评分。");
         if (this.phase == Phase.BUILDING) {
            this.snapshotGroups();
         } else if (this.phase == Phase.GUESSING) {
            this.applyGuesses();
         }
         this.beginReview();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish();
      }
   }

   private void advance() {
      switch (this.phase) {
         case PICKING -> {
            this.applyFallbackThemes();
            this.beginBuilding();
         }
         case BUILDING -> this.beginGuessing();
         case GUESSING -> {
            this.applyGuesses();
            if (this.round >= this.totalRounds) {
               this.beginReview();
            } else {
               this.round++;
               this.clearGroupInteriors();
               this.reshuffleRoles();
               this.beginBuilding();
            }
         }
         case REVIEW -> this.advanceReview();
         case SCORING -> this.advanceScoring();
         case ENDED -> {
         }
      }
   }

   private void beginPicking() {
      this.phase = Phase.PICKING;
      this.ticksLeft = PICK_SECONDS * 20;
      this.customPicked.clear();
      this.pickChoices.clear();
      boolean three = this.settings.pickFromThree() && !this.settings.customTheme();
      if (three) {
         List<String> pool = this.room.resolvedWords(this.ctx);
         for (BuildGroup group : this.groups) {
            this.pickChoices.put(group.id(), WordBank.pickUnique(pool, this.offeredWords, 3));
         }
      }
      this.hidePlayers();
      ServerLevel level = this.ctx.plots().level();
      this.ctx.broadcast(this.room, "&e开局选词：" + (this.drawing ? "画手" : "建造者")
         + (three
            ? "请在 &f" + PICK_SECONDS + " &e秒内从三个主题中挑选（聊天 &f1/2/3 &e或右键物品），各组选项互不重复。"
            : "请在 &f" + PICK_SECONDS + " &e秒内聊天输入主题，超时则随机。"));
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         BuildGroup group = this.buildTheme(uuid);
         if (player == null || group == null || level == null) {
            continue;
         }
         player.closeContainer();
         player.getInventory().clearContent();
         player.setInvisible(true);
         this.ensureTeam(player, false);
         if (this.isBuilder(uuid)) {
            player.setGameMode(GameType.ADVENTURE);
            if (this.drawing) {
               group.plot().teleportCanvas(player, level);
               DrawKit.startFlying(player);
            } else {
               group.plot().teleport(player, level);
            }
            List<String> options = this.pickChoices.get(group.id());
            if (options != null && !options.isEmpty()) {
               this.givePickItems(player, options);
               this.ctx.send(player, "&a主题 &f" + (group.id() + 1) + " &a请三选一：");
               for (int i = 0; i < options.size(); i++) {
                  this.ctx.send(player, "&e" + (i + 1) + ". &f" + options.get(i));
               }
               this.title(player, "&e三选一主题", "&7聊天 1/2/3 或右键  ·  " + PICK_SECONDS + " 秒");
            } else {
               this.ctx.send(player, "&a请输入主题 &f" + (group.id() + 1) + " &a的主题词。超时将随机。");
               this.title(player, "&e输入主题词", "&7聊天输入  ·  " + PICK_SECONDS + " 秒");
            }
         } else {
            this.toSpectator(player);
            if (this.drawing) {
               group.plot().teleportCanvasWatch(player, level, 0, 1);
            } else {
               group.plot().teleportWatch(player, level, 0, 1);
            }
            this.ctx.send(player, "&7请等待建造者选定主题。");
         }
      }
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

   private void applyFallbackThemes() {
      for (BuildGroup group : this.groups) {
         if (this.customPicked.contains(group.id())) {
            continue;
         }
         this.customPicked.add(group.id());
         List<String> options = this.pickChoices.get(group.id());
         if (options != null && !options.isEmpty()) {
            String word = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            group.setOpeningWord(word);
         }
         this.notifyAssigned(group, "&7超时未输入，主题随机为： &e" + group.currentWord());
      }
   }

   private boolean allThemesPicked() {
      for (BuildGroup group : this.groups) {
         boolean assigned = false;
         for (UUID uuid : this.remaining) {
            if (this.isBuilder(uuid) && group.equals(this.buildTheme(uuid))) {
               assigned = true;
               break;
            }
         }
         if (assigned && !this.customPicked.contains(group.id())) {
            return false;
         }
      }
      return true;
   }

   private void beginBuilding() {
      this.phase = Phase.BUILDING;
      this.ticksLeft = this.settings.buildSecondsForRound(this.round) * 20;
      this.lastGuess.clear();
      this.hidePlayers();
      ServerLevel level = this.ctx.plots().level();
      if (level != null && this.drawing) {
         for (BuildGroup group : this.groups) {
            Canvas.of(group.plot()).install(level);
         }
      }
      int[] watchAt = new int[this.groups.size()];
      int[] watchTotal = new int[this.groups.size()];
      for (UUID uuid : this.remaining) {
         if (!this.isBuilder(uuid)) {
            BuildGroup theme = this.buildTheme(uuid);
            if (theme != null) {
               watchTotal[theme.id()]++;
            }
         }
      }
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         BuildGroup theme = this.buildTheme(uuid);
         if (player == null || theme == null || level == null) {
            continue;
         }
         player.closeContainer();
         player.getInventory().clearContent();
         player.setInvisible(true);
         this.ensureTeam(player, false);
         if (this.isBuilder(uuid)) {
            if (this.drawing) {
               theme.plot().teleportCanvas(player, level);
               player.setGameMode(GameType.ADVENTURE);
               DrawKit.give(player);
            } else {
               theme.plot().teleport(player, level);
               player.setGameMode(GameType.CREATIVE);
            }
            this.ctx.send(player, "&6第 &f" + this.round + "&6/&f" + this.totalRounds
               + " &6轮 &8| &7主题 &f" + (theme.id() + 1)
               + " &6： &e" + theme.currentWord());
            player.displayClientMessage(TextUtil.color("&e主题： &f" + theme.currentWord()), false);
         } else {
            this.toSpectator(player);
            if (this.drawing) {
               theme.plot().teleportCanvasWatch(player, level, watchAt[theme.id()]++, Math.max(1, watchTotal[theme.id()]));
            } else {
               theme.plot().teleportWatch(player, level, watchAt[theme.id()]++, Math.max(1, watchTotal[theme.id()]));
            }
            if (this.singleChain() && uuid.equals(this.singleGuesser)) {
               this.ctx.send(player, this.drawing
                  ? "&b本轮旁观。猜词阶段由你来猜；下一轮会再随机抽人画。"
                  : "&b本轮旁观。猜词阶段由你来猜；下一轮会再随机抽人建。");
            } else {
               this.ctx.send(player, "&b你本局旁观：观看主题 &f" + (theme.id() + 1) + (this.drawing ? " &b绘画，不要泄露主题。" : " &b建造，不要泄露主题。"));
            }
         }
      }
   }

   private void beginGuessing() {
      this.snapshotGroups();
      this.rememberBuilds();
      this.assignGuessesForRound();
      this.phase = Phase.GUESSING;
      this.ticksLeft = this.guessTicks;
      this.lastGuess.clear();
      this.hidePlayers();
      ServerLevel level = this.ctx.plots().level();
      int[] watchAt = new int[this.groups.size()];
      int[] watchTotal = new int[this.groups.size()];
      for (UUID uuid : this.remaining) {
         BuildGroup view = this.guessTheme(uuid);
         if (view != null) {
            watchTotal[view.id()]++;
         }
      }
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         BuildGroup view = this.guessTheme(uuid);
         if (player == null || view == null || level == null) {
            continue;
         }
         player.closeContainer();
         this.toSpectator(player);
         player.getInventory().clearContent();
         this.ensureTeam(player, false);
         if (this.drawing) {
            view.plot().teleportCanvasWatch(player, level, watchAt[view.id()]++, Math.max(1, watchTotal[view.id()]));
         } else {
            view.plot().teleportWatch(player, level, watchAt[view.id()]++, Math.max(1, watchTotal[view.id()]));
         }
         String hint = " &7（" + WordHint.label(view.currentWord()) + "）";
         if (this.isBuilder(uuid)) {
            this.ctx.send(player, this.singleChain()
               ? (this.drawing
                  ? "&b轮到你猜词：看着画作输入主题" + hint + "。猜的词留给这个主题，下一轮随机抽人画。"
                  : "&b轮到你猜词：看着建筑输入主题" + hint + "。猜的词留给这个主题，下一轮随机抽人建。")
               : "&b猜词：看着主题 &f" + (view.id() + 1) + " &b，共 &f" + WordHint.label(view.currentWord())
                  + " &b。聊天输入。这不是你建过的主题；下一轮会尽量给你新主题去"
                  + (this.drawing ? "画。" : "建。"));
         } else if (this.singleChain() && uuid.equals(this.singleBuilder)) {
            this.ctx.send(player, this.drawing ? "&7你本轮已画完，由下一位猜词。" : "&7你本轮已建完，由下一位猜词。");
         } else {
            this.ctx.send(player, "&b旁观猜词：看着主题 &f" + (view.id() + 1)
               + " &b，共 &f" + WordHint.label(view.currentWord()) + " &b。你的猜词不影响下一轮主题。");
         }
      }
   }

   private void applyGuesses() {
      if (this.singleChain()) {
         this.applySingleChainGuess();
         return;
      }
      int n = this.groups.size();
      if (this.homeGuess == null || this.homeGuess.length != n) {
         this.assignGuessesForRound();
      }
      for (BuildGroup theme : this.groups) {
         BuildGroup guesserHome = this.guesserHomeOf(theme);
         String guess = this.firstGuess(guesserHome);
         if (guess != null) {
            theme.recordWord(guess);
            this.notifyHome(guesserHome, "&7主题 &f" + (theme.id() + 1) + " &7下一轮词是你刚猜的： &e" + guess
               + " &7（下一轮会尽量给你还没建过的主题）");
         } else {
            theme.wordChain().add(theme.currentWord());
            this.notifyHome(guesserHome, "&7无人猜词，主题 &f" + (theme.id() + 1) + " &7下一轮仍是： &e" + theme.currentWord());
         }
      }
      this.rememberGuesses();
   }

   private void applySingleChainGuess() {
      BuildGroup group = this.groups.get(0);
      UUID guesser = this.singleGuesser;
      String guess = guesser == null ? null : this.lastGuess.get(guesser);
      if (guess != null && !guess.isBlank()) {
         group.recordWord(guess);
         ServerPlayer next = this.ctx.player(guesser);
         if (next != null) {
            this.ctx.send(next, "&7这个主题下一轮是你刚猜的： &e" + guess
               + " &7（将尽量抽还没建过的人来" + (this.drawing ? "画" : "建") + "）");
         }
         this.singleHasGuessed.add(guesser);
         return;
      }
      group.wordChain().add(group.currentWord());
      ServerPlayer next = this.ctx.player(guesser);
      if (next != null) {
         this.ctx.send(next, "&7无人猜词，下一轮主题不变： &e" + group.currentWord());
      }
   }

   private void beginReview() {
      this.reviewGroup = 0;
      this.reviewSnap = 0;
      this.showPlayers();
      this.ctx.broadcast(this.room, this.drawing
         ? "&6&l开始回放：按每个主题从开局画到最终猜词。"
         : "&6&l开始回放：按每个主题从开局建筑到最终猜词。");
      this.showReviewSnapshot();
   }

   private void advanceReview() {
      BuildGroup group = this.reviewingGroup();
      if (group == null) {
         this.finish();
         return;
      }
      this.reviewSnap++;
      if (this.reviewSnap >= Math.max(1, this.captureCount(group))) {
         this.beginScoring();
         return;
      }
      this.showReviewSnapshot();
   }

   private void showReviewSnapshot() {
      while (this.reviewGroup < this.groups.size() && this.captureCount(this.groups.get(this.reviewGroup)) == 0) {
         this.reviewGroup++;
         this.reviewSnap = 0;
      }
      if (this.reviewGroup >= this.groups.size()) {
         this.finish();
         return;
      }
      BuildGroup group = this.groups.get(this.reviewGroup);
      this.phase = Phase.REVIEW;
      this.ticksLeft = WATCH_SECONDS * 20;
      ServerLevel level = this.ctx.plots().level();
      if (level != null) {
         this.ctx.plots().clearInterior(level, group.plot());
         int snap = Math.min(this.reviewSnap, Math.max(0, this.captureCount(group) - 1));
         if (this.drawing) {
            Canvas canvas = Canvas.of(group.plot());
            canvas.install(level);
            if (snap < group.drawings().size()) {
               canvas.restore(level, group.drawings().get(snap));
            }
         } else if (snap < group.snapshots().size()) {
            group.snapshots().get(snap).restore(level, group.plot());
         }
      }
      int shown = this.reviewSnap + 1;
      int total = Math.max(1, this.captureCount(group));
      String word = group.wordForSnapshot(this.reviewSnap);
      this.ctx.broadcast(this.room, "&6▶ 主题 &f" + (group.id() + 1) + "&7/&f" + this.groups.size()
         + " &8| &7开局 &e" + group.startWord()
         + " &8| &e第 " + shown + "/" + total + " 轮"
         + " &8| &7本轮 &e" + word);
      this.ctx.broadcast(this.room, "&7本轮" + (this.drawing ? "画手" : "建造") + "： &f" + this.buildersOfSnap(group)
         + " &8| &7演变： &f" + group.chainText());
      this.gatherAt(group, "&6回放 &f主题" + (group.id() + 1) + " &7第" + shown + "轮", "&e" + group.startWord() + " &7→ &e" + word);
   }

   private void beginScoring() {
      BuildGroup group = this.reviewingGroup();
      if (group == null) {
         this.finish();
         return;
      }
      this.phase = Phase.SCORING;
      this.ticksLeft = SCORE_SECONDS * 20;
      this.scoredCurrent.clear();
      this.showPlayers();
      this.ctx.broadcast(this.room, "&6评分：主题 &f" + (group.id() + 1)
         + " &7开局 &e" + group.startWord() + " &8| &f" + group.chainText());
      this.ctx.broadcast(this.room, this.drawing
         ? "&e聊天输入 1–5，或点热键栏/菜单打分。给这条主题从开局到结束的画作打分。"
         : "&e聊天输入 1–5，或点热键栏/菜单打分。给这条主题从开局到结束的建筑打分。");
      int index = 0;
      int total = this.remaining.size();
      ServerLevel level = this.ctx.plots().level();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         if (level != null) {
            if (this.drawing) {
               group.plot().teleportCanvasWatch(player, level, index++, total);
            } else {
               group.plot().teleportWatch(player, level, index++, total);
            }
         }
         player.setGameMode(GameType.ADVENTURE);
         player.setInvisible(false);
         this.ensureTeam(player, true);
         this.giveScoreItems(player);
         this.title(player, "&6给主题 " + (group.id() + 1) + " 打分", "&e1–5 分  聊天/热键/菜单");
         BuildWarVoteGui.open(this.ctx, player, this);
      }
   }

   private void advanceScoring() {
      this.reviewGroup++;
      this.reviewSnap = 0;
      if (this.reviewGroup >= this.groups.size()) {
         this.finish();
         return;
      }
      this.showReviewSnapshot();
   }

   private void snapshotGroups() {
      ServerLevel level = this.ctx.plots().level();
      if (level == null) {
         return;
      }
      for (BuildGroup group : this.groups) {
         group.addSnapBuilders(this.assignedBuilders(group));
         if (this.drawing) {
            group.addDrawing(Canvas.of(group.plot()).capture(level));
         } else {
            group.addSnapshot(PlotSnapshot.capture(level, group.plot()));
         }
      }
   }

   private void clearGroupInteriors() {
      ServerLevel level = this.ctx.plots().level();
      if (level == null) {
         return;
      }
      for (BuildGroup group : this.groups) {
         this.ctx.plots().clearInterior(level, group.plot());
      }
   }

   private void gatherAt(BuildGroup group, String title, String subtitle) {
      ServerLevel level = this.ctx.plots().level();
      int index = 0;
      int total = this.remaining.size();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         player.closeContainer();
         this.toSpectator(player);
         player.getInventory().clearContent();
         this.ensureTeam(player, true);
         if (level != null) {
            if (this.drawing) {
               group.plot().teleportCanvasWatch(player, level, index++, total);
            } else {
               group.plot().teleportWatch(player, level, index++, total);
            }
         }
         this.title(player, title, subtitle);
      }
   }

   private void giveScoreItems(ServerPlayer player) {
      player.getInventory().clearContent();
         String[] mats = {"white_dye", "orange_dye", "magenta_dye", "light_blue_dye", "lime_dye"};
      for (int i = 0; i < 5; i++) {
         int score = i + 1;
         player.getInventory().setItem(i, GuiItems.action(
            mats[i],
            "&e&l" + score + " 分",
            List.of("&7右键或聊天输入 &f" + score),
            "score",
            "score",
            String.valueOf(score)
         ));
      }
   }

   private void finish() {
      this.phase = Phase.ENDED;
      if (this.begun) {
         for (UUID uuid : this.seats) {
            Map<Integer, Integer> map = this.votes.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());
            for (BuildGroup group : this.groups) {
               map.putIfAbsent(group.id(), 3);
            }
         }
         this.ctx.broadcast(this.room, this.drawing ? "&6&l绘画战争结算（按主题）" : "&6&l建筑战争结算（按主题）");
         for (BuildGroup group : this.groups) {
            this.ctx.broadcast(this.room, "&8• &6主题 &f" + (group.id() + 1)
               + " &7开局 &e" + group.startWord()
               + " &8| &7演变 &f" + group.chainText()
               + " &8| &a" + trim(this.averageFor(group.id())) + " 分");
         }
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      List<Plot> plots = new ArrayList<>();
      for (BuildGroup group : this.groups) {
         plots.add(group.plot());
      }
      this.ctx.plots().release(plots);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.buildWar().remove(this);
   }

   private double averageFor(int groupId) {
      int sum = 0;
      int n = 0;
      for (UUID voter : this.seats) {
         Map<Integer, Integer> map = this.votes.get(voter);
         if (map != null && map.containsKey(groupId)) {
            sum += map.get(groupId);
            n++;
         }
      }
      return n == 0 ? 3.0 : sum / (double) n;
   }

   private static String trim(double value) {
      return String.format("%.1f", value);
   }

   private boolean allBuildersGuessed() {
      for (UUID uuid : this.remaining) {
         if (this.isBuilder(uuid) && !this.lastGuess.containsKey(uuid)) {
            return false;
         }
      }
      return true;
   }

   private boolean singleChain() {
      return this.groups.size() == 1;
   }

   private List<UUID> rotation() {
      List<UUID> list = new ArrayList<>();
      for (UUID uuid : this.seats) {
         if (this.remaining.contains(uuid)) {
            list.add(uuid);
         }
      }
      return list;
   }

   private void reshuffleRoles() {
      if (this.singleChain()) {
         this.pickSingleRoles();
         this.homeBuild = new int[]{0};
         this.homeGuess = new int[]{0};
         return;
      }
      this.homeBuild = this.assignPermutation((home, theme) -> this.avoidCost(home, theme, false));
      this.homeGuess = new int[this.groups.size()];
   }

   private void assignGuessesForRound() {
      if (this.singleChain() || this.groups.size() <= 1) {
         this.homeGuess = new int[]{0};
         return;
      }
      int n = this.groups.size();
      if (this.homeBuild == null || this.homeBuild.length != n) {
         this.homeGuess = new int[n];
         for (int h = 0; h < n; h++) {
            this.homeGuess[h] = Math.floorMod(h + 1, n);
         }
         return;
      }
      this.homeGuess = this.assignPermutation((home, theme) -> this.avoidCost(home, theme, true));
      if (this.hasOwnBuildGuess()) {
         this.homeGuess = this.bestShiftedGuess();
      }
   }

   private boolean hasOwnBuildGuess() {
      if (this.homeBuild == null || this.homeGuess == null) {
         return false;
      }
      int n = Math.min(this.homeBuild.length, this.homeGuess.length);
      for (int h = 0; h < n; h++) {
         if (this.homeGuess[h] == this.homeBuild[h]) {
            return true;
         }
      }
      return false;
   }

   private int[] bestShiftedGuess() {
      int n = this.groups.size();
      int[] best = new int[n];
      int bestCost = Integer.MAX_VALUE;
      for (int k = 1; k < n; k++) {
         int[] cur = new int[n];
         int cost = 0;
         for (int h = 0; h < n; h++) {
            cur[h] = Math.floorMod(this.homeBuild[h] + k, n);
            cost += this.avoidCost(h, cur[h], true);
         }
         if (cost < bestCost) {
            bestCost = cost;
            best = cur;
         }
      }
      return best;
   }

   private int avoidCost(int home, int theme, boolean guessing) {
      int cost = 0;
      if (guessing && this.homeBuild != null && home < this.homeBuild.length && this.homeBuild[home] == theme) {
         cost += 100000;
      }
      if (home < this.builtThemes.size() && this.builtThemes.get(home).contains(theme)) {
         cost += 10000;
      }
      if (home < this.guessedThemes.size() && this.guessedThemes.get(home).contains(theme)) {
         cost += 10000;
      }
      if (this.usedInLastRounds(home, theme, 2)) {
         cost += 20000;
      }
      if (home < this.seenWords.size() && theme >= 0 && theme < this.groups.size()) {
         String word = normWord(this.groups.get(theme).currentWord());
         if (!word.isEmpty() && this.seenWords.get(home).contains(word)) {
            cost += 8000;
         }
      }
      return cost;
   }

   private boolean usedInLastRounds(int home, int theme, int rounds) {
      return this.inRecent(this.buildSeq, home, theme, rounds)
         || this.inRecent(this.guessSeq, home, theme, rounds);
   }

   private boolean inRecent(List<List<Integer>> seq, int home, int theme, int rounds) {
      if (home < 0 || home >= seq.size()) {
         return false;
      }
      List<Integer> list = seq.get(home);
      int from = Math.max(0, list.size() - rounds);
      for (int i = from; i < list.size(); i++) {
         if (list.get(i) == theme) {
            return true;
         }
      }
      return false;
   }

   private static String normWord(String word) {
      return word == null ? "" : word.trim();
   }

   private int[] assignPermutation(java.util.function.IntBinaryOperator cost) {
      int n = this.groups.size();
      int[] best = new int[n];
      for (int i = 0; i < n; i++) {
         best[i] = i;
      }
      if (n <= 1) {
         return best;
      }
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int bestCost = Integer.MAX_VALUE;
      int[] cur = new int[n];
      int tries = Math.min(500, 50 * n * n);
      for (int attempt = 0; attempt < tries; attempt++) {
         for (int i = 0; i < n; i++) {
            cur[i] = i;
         }
         for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = cur[i];
            cur[i] = cur[j];
            cur[j] = tmp;
         }
         int c = 0;
         for (int h = 0; h < n; h++) {
            c += cost.applyAsInt(h, cur[h]);
            if (c >= bestCost) {
               break;
            }
         }
         if (c < bestCost) {
            bestCost = c;
            System.arraycopy(cur, 0, best, 0, n);
            if (c == 0) {
               return best;
            }
         }
      }
      int[] dfs = new int[n];
      boolean[] used = new boolean[n];
      int[] limits = {1, 8000, 10000, 20000, 100000, Integer.MAX_VALUE};
      for (int limit : limits) {
         used = new boolean[n];
         if (this.dfsPermutation(0, dfs, used, cost, limit)) {
            return dfs;
         }
      }
      return best;
   }

   private boolean dfsPermutation(int home, int[] out, boolean[] used, java.util.function.IntBinaryOperator cost, int maxCost) {
      int n = out.length;
      if (home == n) {
         return true;
      }
      int[] order = new int[n];
      for (int i = 0; i < n; i++) {
         order[i] = i;
      }
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      for (int i = n - 1; i > 0; i--) {
         int j = rng.nextInt(i + 1);
         int tmp = order[i];
         order[i] = order[j];
         order[j] = tmp;
      }
      for (int t : order) {
         if (used[t]) {
            continue;
         }
         if (cost.applyAsInt(home, t) >= maxCost) {
            continue;
         }
         out[home] = t;
         used[t] = true;
         if (this.dfsPermutation(home + 1, out, used, cost, maxCost)) {
            return true;
         }
         used[t] = false;
      }
      return false;
   }

   private void rememberBuilds() {
      if (this.homeBuild == null) {
         return;
      }
      for (int h = 0; h < this.homeBuild.length && h < this.builtThemes.size(); h++) {
         int theme = this.homeBuild[h];
         this.builtThemes.get(h).add(theme);
         if (h < this.buildSeq.size()) {
            this.buildSeq.get(h).add(theme);
         }
         if (h < this.seenWords.size() && theme >= 0 && theme < this.groups.size()) {
            String word = normWord(this.groups.get(theme).currentWord());
            if (!word.isEmpty()) {
               this.seenWords.get(h).add(word);
            }
         }
      }
      if (this.singleBuilder != null) {
         this.singleHasBuilt.add(this.singleBuilder);
      }
   }

   private void rememberGuesses() {
      if (this.homeGuess == null) {
         return;
      }
      for (int h = 0; h < this.homeGuess.length && h < this.guessedThemes.size(); h++) {
         int theme = this.homeGuess[h];
         this.guessedThemes.get(h).add(theme);
         if (h < this.guessSeq.size()) {
            this.guessSeq.get(h).add(theme);
         }
         if (h < this.seenWords.size() && h < this.groups.size()) {
            String word = normWord(this.firstGuess(this.groups.get(h)));
            if (!word.isEmpty()) {
               this.seenWords.get(h).add(word);
            }
         }
      }
   }

   private BuildGroup guesserHomeOf(BuildGroup theme) {
      if (theme == null || this.groups.isEmpty()) {
         return theme;
      }
      if (this.homeGuess != null) {
         for (int h = 0; h < this.homeGuess.length && h < this.groups.size(); h++) {
            if (this.homeGuess[h] == theme.id()) {
               return this.groups.get(h);
            }
         }
      }
      return this.groups.get(Math.floorMod(theme.id() + 1, this.groups.size()));
   }

   private void pickSingleRoles() {
      List<UUID> rot = this.rotation();
      if (rot.isEmpty()) {
         this.singleBuilder = null;
         this.singleGuesser = null;
         return;
      }
      List<UUID> buildPool = new ArrayList<>();
      for (UUID uuid : rot) {
         if (!this.singleHasBuilt.contains(uuid)) {
            buildPool.add(uuid);
         }
      }
      if (buildPool.isEmpty()) {
         buildPool.addAll(rot);
         if (buildPool.size() > 1 && this.singleBuilder != null) {
            buildPool.remove(this.singleBuilder);
         }
      }
      this.singleBuilder = buildPool.get(ThreadLocalRandom.current().nextInt(buildPool.size()));
      List<UUID> guessPool = new ArrayList<>();
      for (UUID uuid : rot) {
         if (!uuid.equals(this.singleBuilder) && !this.singleHasGuessed.contains(uuid)) {
            guessPool.add(uuid);
         }
      }
      if (guessPool.isEmpty()) {
         for (UUID uuid : rot) {
            if (!uuid.equals(this.singleBuilder)) {
               guessPool.add(uuid);
            }
         }
      }
      if (guessPool.isEmpty()) {
         guessPool.add(this.singleBuilder);
      }
      this.singleGuesser = guessPool.get(ThreadLocalRandom.current().nextInt(guessPool.size()));
   }

   private BuildGroup buildTheme(UUID uuid) {
      BuildGroup home = this.groupOf.get(uuid);
      if (home == null) {
         return null;
      }
      if (this.singleChain() || this.homeBuild == null || this.homeBuild.length == 0) {
         return this.groups.isEmpty() ? home : this.groups.get(0);
      }
      int idx = Math.floorMod(this.homeBuild[Math.floorMod(home.id(), this.homeBuild.length)], this.groups.size());
      return this.groups.get(idx);
   }

   private BuildGroup guessTheme(UUID uuid) {
      BuildGroup home = this.groupOf.get(uuid);
      if (home == null) {
         return null;
      }
      if (this.singleChain() || this.groups.isEmpty()) {
         return this.groups.isEmpty() ? home : this.groups.get(0);
      }
      if (this.homeGuess != null && this.homeGuess.length > 0) {
         int idx = Math.floorMod(this.homeGuess[Math.floorMod(home.id(), this.homeGuess.length)], this.groups.size());
         return this.groups.get(idx);
      }
      int n = this.groups.size();
      int viewHome = Math.floorMod(home.id() + 1, n);
      int idx = this.homeBuild == null || this.homeBuild.length == 0
         ? viewHome
         : Math.floorMod(this.homeBuild[viewHome], n);
      return this.groups.get(idx);
   }

   private List<UUID> assignedBuilders(BuildGroup theme) {
      List<UUID> who = new ArrayList<>();
      if (theme == null) {
         return who;
      }
      for (UUID uuid : this.remaining) {
         if (this.isBuilder(uuid) && theme.equals(this.buildTheme(uuid))) {
            who.add(uuid);
         }
      }
      return who;
   }

   private String firstGuess(BuildGroup home) {
      if (home == null) {
         return null;
      }
      for (UUID member : home.members()) {
         if (!this.remaining.contains(member)) {
            continue;
         }
         String value = this.lastGuess.get(member);
         if (value != null && !value.isBlank()) {
            return value;
         }
      }
      return null;
   }

   private void notifyHome(BuildGroup home, String message) {
      if (home == null) {
         return;
      }
      for (UUID member : home.members()) {
         if (!this.remaining.contains(member)) {
            continue;
         }
         ServerPlayer player = this.ctx.player(member);
         if (player != null) {
            this.ctx.send(player, message);
         }
      }
   }

   private void notifyAssigned(BuildGroup theme, String message) {
      for (UUID uuid : this.assignedBuilders(theme)) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.ctx.send(player, message);
         }
      }
   }

   private String buildersOfSnap(BuildGroup theme) {
      List<UUID> who = theme == null ? List.of() : theme.buildersAt(this.reviewSnap);
      if (who.isEmpty() && this.singleChain() && !this.seats.isEmpty()) {
         UUID uuid = this.seats.get(Math.floorMod(this.reviewSnap, this.seats.size()));
         return this.ctx.name(uuid);
      }
      if (who.isEmpty()) {
         return "?";
      }
      List<String> names = new ArrayList<>();
      for (UUID uuid : who) {
         names.add(this.ctx.name(uuid));
      }
      return String.join("&7, &f", names);
   }

   private int captureCount(BuildGroup group) {
      return this.drawing ? group.drawings().size() : group.snapshots().size();
   }

   private void hidePlayers() {
      this.setTeamNametags(false);
   }

   private void showPlayers() {
      this.setTeamNametags(true);
   }

   private void setTeamNametags(boolean visible) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayerTeam(TEAM_NAME);
      if (team != null) {
         team.setNameTagVisibility(visible ? Team.Visibility.ALWAYS : Team.Visibility.NEVER);
         team.setSeeFriendlyInvisibles(visible);
      }
   }

   private void toSpectator(ServerPlayer player) {
      player.setGameMode(GameType.SPECTATOR);
      player.setInvisible(false);
   }

   private void enforce() {
      ServerLevel level = this.ctx.plots().level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         Plot plot = this.boundPlot(uuid);
         if (player == null || plot == null) {
            continue;
         }
         boolean openMenu = player.containerMenu != player.inventoryMenu;
         boolean building = (this.phase == Phase.BUILDING || this.phase == Phase.PICKING) && this.isBuilder(uuid);
         boolean watch = !building;
         if (this.drawing && building) {
            DrawKit.allowFlight(player);
         }
         if (watch && this.phase != Phase.SCORING && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            this.toSpectator(player);
         }
         boolean inside = player.serverLevel() == level && (watch
            ? plot.containsWatch(player.getX(), player.getY(), player.getZ())
            : plot.contains(player.getX(), player.getY(), player.getZ()));
         if (!openMenu && !inside) {
            if (this.drawing) {
               if (watch) {
                  plot.teleportCanvasWatch(player, level, 0, 1);
               } else {
                  plot.teleportCanvas(player, level);
               }
            } else if (watch) {
               plot.teleportWatch(player, level, 0, 1);
            } else {
               plot.teleport(player, level);
            }
         }
         if (building) {
            player.setInvisible(true);
         }
      }
   }

   private void actionBars() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      String clock = (seconds / 60) + ":" + String.format("%02d", seconds % 60);
      BuildGroup reviewing = this.reviewingGroup();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         BuildGroup building = this.buildTheme(uuid);
         String msg = switch (this.phase) {
            case BUILDING -> this.isBuilder(uuid)
               ? (this.drawing ? "&e绘画 &f" : "&e建造 &f") + (building == null ? "" : building.currentWord())
                  + " &8| &7主题" + (building == null ? "?" : (building.id() + 1)) + " &8| &7" + clock
               : "&b旁观 &8| &7主题" + (building == null ? "?" : (building.id() + 1)) + " &8| &7" + clock;
            case PICKING -> this.isBuilder(uuid)
               ? (this.customPicked.contains(building == null ? -1 : building.id())
                  ? "&a已选主题 &f" + (building == null ? "" : building.currentWord()) + " &8| &7" + clock
                  : "&e输入主题词 &8| &7" + clock)
               : "&7等待选定主题 &8| &7" + clock;
            case GUESSING -> {
               BuildGroup view = this.guessTheme(uuid);
               String hint = view == null ? "" : " &f" + WordHint.label(view.currentWord());
               yield "&b猜词 主题" + (view == null ? "?" : (view.id() + 1)) + hint
                  + " &8| &7" + clock + (this.lastGuess.containsKey(uuid) ? " &a已提交" : " &e聊天输入");
            }
            case REVIEW -> {
               int shown = this.reviewSnap + 1;
               int total = reviewing == null ? 1 : Math.max(1, this.captureCount(reviewing));
               yield "&6回放 主题" + (reviewing == null ? "?" : (reviewing.id() + 1))
                  + " &f第" + shown + "/" + total + "轮 &8| &7" + clock;
            }
            case SCORING -> "&6评分 主题" + (reviewing == null ? "?" : (reviewing.id() + 1))
               + " &8| &7" + clock + (this.scoredCurrent.contains(uuid) ? " &a已打分" : " &e输入1–5");
            case ENDED -> "";
         };
         if (!msg.isEmpty()) {
            player.displayClientMessage(TextUtil.color(msg), true);
         }
      }
   }

   private void title(ServerPlayer player, String title, String subtitle) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }

   private void ensureTeam(ServerPlayer player, boolean visible) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayerTeam(TEAM_NAME);
      if (team == null) {
         team = board.addPlayerTeam(TEAM_NAME);
         team.setCollisionRule(Team.CollisionRule.NEVER);
      }
      team.setNameTagVisibility(visible ? Team.Visibility.ALWAYS : Team.Visibility.NEVER);
      team.setSeeFriendlyInvisibles(visible);
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void restore(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam current = board.getPlayersTeam(player.getScoreboardName());
      if (current != null) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
      player.setInvisible(false);
      player.closeContainer();
      DrawKit.clear(player.getUUID());
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
