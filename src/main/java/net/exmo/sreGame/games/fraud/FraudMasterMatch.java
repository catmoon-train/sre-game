package net.exmo.sreGame.games.fraud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildwar.Plot;
import net.exmo.sreGame.games.draw.Canvas;
import net.exmo.sreGame.games.draw.DrawKit;
import net.exmo.sreGame.games.fraud.gui.ActionGui;
import net.exmo.sreGame.games.fraud.gui.IncomingCallGui;
import net.exmo.sreGame.games.fraud.gui.PhoneGui;
import net.exmo.sreGame.games.fraud.round.RoundHandler;
import net.exmo.sreGame.games.fraud.round.RoundType;
import net.exmo.sreGame.games.fraud.voice.FraudVoicePlugin;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class FraudMasterMatch {
   public enum Phase {
      PREPARE,
      CALL,
      ACTION,
      SETTLE,
      ENDED
   }

   public static final String TEAM_PREFIX = "srefm";
   public static final int PREPARE_SECONDS = 10;
   public static final int CALL_SECONDS = 120;
   public static final int SETTLE_SECONDS = 15;
   public static final int STANDARD_ROUNDS = 8;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Set<UUID> remaining = ConcurrentHashMap.newKeySet();
   private final Map<UUID, ColorCode> colors = new HashMap<>();
   private final Map<UUID, Plot> plots = new HashMap<>();
   private final Map<UUID, Canvas> canvases = new HashMap<>();
   private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> lastDelta = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> roundWins = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> betrayals = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> voteHits = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> sumError = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> prisonerCoops = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> prisonerPlays = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final List<RoundType> schedule;
   private final PhoneService phones;
   private final SidebarBoard board;
   private final List<Plot> claimed;
   private final int doubleRoundIndex;
   private Phase phase = Phase.PREPARE;
   private int roundIndex;
   private int ticksLeft;
   private int boardTicks;
   private boolean begun;
   private RoundHandler handler;

   public FraudMasterMatch(GameContext ctx, GameRoom room, List<UUID> seats, List<Plot> claimed) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.remaining.addAll(seats);
      this.claimed = List.copyOf(claimed);
      this.phones = new PhoneService(this);
      this.board = new SidebarBoard(ctx.server());
      this.schedule = new ArrayList<>(RoundType.shuffledStandard(STANDARD_ROUNDS));
      this.schedule.add(RoundType.FINALE);
      this.doubleRoundIndex = room.fraudSettings().doubleRound()
         ? ThreadLocalRandom.current().nextInt(STANDARD_ROUNDS)
         : -1;
      for (int i = 0; i < seats.size(); i++) {
         UUID uuid = seats.get(i);
         this.colors.put(uuid, ColorCode.ofIndex(i));
         this.scores.put(uuid, 0);
         Plot plot = claimed.get(i);
         this.plots.put(uuid, plot);
         this.canvases.put(uuid, BoothHut.canvas(plot));
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

   public FraudMasterSettings settings() {
      return this.room.fraudSettings();
   }

   public Phase phase() {
      return this.phase;
   }

   public PhoneService phones() {
      return this.phones;
   }

   public RoundHandler handler() {
      return this.handler;
   }

   public List<UUID> seats() {
      return this.seats;
   }

   public List<UUID> alive() {
      List<UUID> list = new ArrayList<>();
      for (UUID uuid : this.seats) {
         if (this.remaining.contains(uuid)) {
            list.add(uuid);
         }
      }
      return list;
   }

   public boolean alive(UUID uuid) {
      return uuid != null && this.remaining.contains(uuid);
   }

   public ColorCode color(UUID uuid) {
      return this.colors.get(uuid);
   }

   public UUID byColor(ColorCode color) {
      for (Map.Entry<UUID, ColorCode> entry : this.colors.entrySet()) {
         if (entry.getValue() == color && this.remaining.contains(entry.getKey())) {
            return entry.getKey();
         }
      }
      return null;
   }

   public Plot plot(UUID uuid) {
      return this.plots.get(uuid);
   }

   public Canvas canvas(UUID uuid) {
      return this.canvases.get(uuid);
   }

   public int score(UUID uuid) {
      return this.scores.getOrDefault(uuid, 0);
   }

   public Map<UUID, Integer> scores() {
      return this.scores;
   }

   public boolean doubleThisRound() {
      return this.roundIndex == this.doubleRoundIndex && this.currentType() != RoundType.FINALE;
   }

   public RoundType currentType() {
      return this.handler == null ? null : this.handler.type();
   }

   public int roundNumber() {
      return this.roundIndex + 1;
   }

   public int totalRounds() {
      return this.schedule.size();
   }

   public int ticksLeft() {
      return this.ticksLeft;
   }

   public void skipPhase() {
      this.ticksLeft = 1;
   }

   public String clock() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
   }

   public String label(UUID uuid) {
      ColorCode color = this.colors.get(uuid);
      String name = this.ctx.name(uuid);
      return color == null ? "&f" + name : color.tagged() + " &f" + name;
   }

   public String coloredName(UUID uuid) {
      ColorCode color = this.colors.get(uuid);
      return color == null ? this.ctx.name(uuid) : color.chat() + this.ctx.name(uuid);
   }

   public ServerPlayer player(UUID uuid) {
      return this.ctx.player(uuid);
   }

   public void send(ServerPlayer player, String message) {
      this.ctx.send(player, message);
   }

   public void send(UUID uuid, String message) {
      this.ctx.send(this.player(uuid), message);
   }

   public void broadcast(String message) {
      this.ctx.broadcast(this.room, message);
   }

   public void addScore(UUID uuid, int delta) {
      if (uuid == null || delta == 0 || !this.remaining.contains(uuid)) {
         return;
      }
      int applied = this.doubleThisRound() ? delta * 2 : delta;
      this.scores.merge(uuid, applied, Integer::sum);
      this.lastDelta.merge(uuid, applied, Integer::sum);
   }

   public void addBetrayal(UUID uuid) {
      this.betrayals.merge(uuid, 1, Integer::sum);
   }

   public void addPrisonerPlay(UUID uuid, boolean cooperated) {
      this.prisonerPlays.merge(uuid, 1, Integer::sum);
      if (cooperated) {
         this.prisonerCoops.merge(uuid, 1, Integer::sum);
      }
   }

   public void addVoteHit(UUID uuid) {
      this.voteHits.merge(uuid, 1, Integer::sum);
   }

   public void addSumError(UUID uuid, int error) {
      this.sumError.merge(uuid, error, Integer::sum);
   }

   public int betrayals(UUID uuid) {
      return this.betrayals.getOrDefault(uuid, 0);
   }

   public boolean canPaint(UUID uuid) {
      return this.handler != null && this.handler.canPaint(this, uuid);
   }

   public Canvas paintCanvas(UUID uuid) {
      return this.handler == null ? null : this.handler.canvas(this, uuid);
   }

   public void start() {
      this.begun = true;
      if (FraudVoicePlugin.missing()) {
         this.broadcast("&e未检测到 Simple Voice Chat。电话界面仍可用，但没有语音。");
      }
      ServerLevel level = this.ctx.plots().level();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.player(uuid);
         if (player != null) {
            this.saved.put(uuid, Saved.capture(player));
            this.board.create(player, "&6诈骗大师");
            this.ensureTeam(player);
            Plot plot = this.plots.get(uuid);
            if (level != null && plot != null) {
               BoothHut.teleport(player, level, plot);
            }
         }
      }
      this.broadcast("&6《诈骗大师》开局。你的代号已写在计分板上。不要相信任何人。");
      this.beginRound();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      this.phones.tick();
      if (this.handler != null) {
         if (this.phase == Phase.CALL) {
            this.handler.onCallTick(this);
         } else if (this.phase == Phase.ACTION) {
            this.handler.onActionTick(this);
         }
      }
      this.actionBars();
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      if (this.ticksLeft <= 0) {
         this.advance();
      }
   }

   public void onLeave(UUID uuid) {
      if (!this.remaining.remove(uuid)) {
         return;
      }
      this.phones.hangup(uuid);
      if (this.handler != null) {
         this.handler.onLeave(this, uuid);
      }
      ServerPlayer player = this.player(uuid);
      if (player != null) {
         this.restore(player);
      }
      this.board.remove(uuid);
      DrawKit.clear(uuid);
      if (this.remaining.size() < 2) {
         this.broadcast("&c人数不足，提前结算。");
         this.finish();
      }
   }

   public void endNow() {
      this.finish();
   }

   public boolean handleChat(ServerPlayer player, String message) {
      if (this.phones.inCall(player.getUUID())) {
         this.relayCallChat(player, message);
         return true;
      }
      if (this.phase == Phase.ACTION && this.handler != null) {
         this.handler.handleChat(this, player, message);
      } else {
         this.send(player, "&c未接通电话无法聊天。接通后仅通话对象可见。");
      }
      return true;
   }

   private void relayCallChat(ServerPlayer player, String message) {
      String text = message == null ? "" : message.trim().replace("&", "").replace("§", "");
      if (text.isEmpty()) {
         return;
      }
      String line = "&8[&b☎&8] " + this.label(player.getUUID()) + " &7▸ &f" + text;
      for (UUID uuid : this.phones.members(player.getUUID())) {
         this.send(uuid, line);
      }
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      String action = GuiItems.actionTag(stack);
      if (action == null) {
         return false;
      }
      return switch (action) {
         case "phone" -> {
            if (this.phase == Phase.CALL) {
               if (this.phones.isIncoming(player.getUUID())) {
                  IncomingCallGui.open(this, player);
               } else {
                  PhoneGui.open(this, player);
               }
            } else {
               this.send(player, "&c现在不能打电话。");
            }
            yield true;
         }
         case "hangup" -> {
            this.phones.hangup(player.getUUID());
            yield true;
         }
         case "answer" -> {
            this.phones.answer(player);
            yield true;
         }
         case "reject" -> {
            this.phones.reject(player);
            yield true;
         }
         case "action" -> {
            if (this.phase == Phase.ACTION) {
               ActionGui.open(this, player);
            }
            yield true;
         }
         case "rules" -> {
            this.sendRules(player);
            yield true;
         }
         default -> false;
      };
   }

   public boolean handleGuiAction(ServerPlayer player, String action, String extra) {
      if ("dial".equals(action)) {
         if (extra == null) {
            return true;
         }
         try {
            this.phones.dial(player, UUID.fromString(extra));
         } catch (IllegalArgumentException ignored) {
         }
         return true;
      }
      if ("hangup".equals(action)) {
         this.phones.hangup(player.getUUID());
         return true;
      }
      if ("answer".equals(action)) {
         this.phones.answer(player);
         return true;
      }
      if ("reject".equals(action)) {
         this.phones.reject(player);
         return true;
      }
      if (this.phase == Phase.ACTION && this.handler != null) {
         boolean handled = this.handler.handleAction(this, player, action, extra);
         if (handled) {
            ActionGui.open(this, player);
         }
         return handled;
      }
      return false;
   }

   public void giveKit(ServerPlayer player) {
      if (player == null) {
         return;
      }
      UUID uuid = player.getUUID();
      if (this.canPaint(uuid)) {
         DrawKit.give(player);
         player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, phoneItem());
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         player.onUpdateAbilities();
         return;
      }
      DrawKit.clear(uuid);
      player.getInventory().clearContent();
      player.getInventory().setItem(0, phoneItem());
      player.getInventory().setItem(1, hangupItem());
      if (this.phones.isIncoming(uuid)) {
         player.getInventory().setItem(2, answerItem());
         player.getInventory().setItem(3, rejectItem());
      } else if (this.phase == Phase.ACTION) {
         player.getInventory().setItem(2, actionItem());
      }
      player.getInventory().setItem(8, rulesItem());
   }

   public void refreshPhoneUi(UUID uuid) {
      ServerPlayer player = this.player(uuid);
      if (player == null) {
         return;
      }
      this.giveKit(player);
      if (this.phase != Phase.CALL) {
         return;
      }
      if (this.phones.isIncoming(uuid)) {
         IncomingCallGui.open(this, player);
         return;
      }
      if (player.containerMenu != player.inventoryMenu) {
         PhoneGui.open(this, player);
      }
   }

   public void refreshKits() {
      for (UUID uuid : this.remaining) {
         this.giveKit(this.player(uuid));
      }
   }

   public void openActionGuis() {
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.player(uuid);
         if (player != null) {
            ActionGui.open(this, player);
         }
      }
   }

   public List<UUID> ranked() {
      List<UUID> list = this.alive();
      list.sort(Comparator
         .comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed()
         .thenComparing(Comparator.comparingInt((UUID id) -> this.roundWins.getOrDefault(id, 0)).reversed())
         .thenComparing(Comparator.comparingInt((UUID id) -> this.betrayals.getOrDefault(id, 0)).reversed()));
      return list;
   }

   public UUID leader() {
      List<UUID> ranked = this.ranked();
      return ranked.isEmpty() ? null : ranked.get(0);
   }

   public void sendRules(ServerPlayer player) {
      if (this.handler == null) {
         return;
      }
      this.send(player, "&6—— " + this.handler.type().display() + " ——");
      this.send(player, "&7" + this.handler.rules());
      for (String line : this.handler.privateInfo(this, player.getUUID())) {
         this.send(player, "&e" + line);
      }
   }

   private void beginRound() {
      this.lastDelta.clear();
      this.phones.resetRoundDials();
      this.phones.setOpen(false);
      RoundType type = this.schedule.get(this.roundIndex);
      this.handler = type.create();
      this.phase = Phase.PREPARE;
      this.ticksLeft = PREPARE_SECONDS * 20;
      this.broadcast("&6第 " + this.roundNumber() + "/" + this.totalRounds() + " 回合： &f"
         + type.display() + " &8· &7" + type.theme());
      if (this.doubleThisRound()) {
         this.broadcast("&c&l本回合得分 ×2！");
      }
      this.handler.onPrepare(this);
      this.refreshKits();
      for (UUID uuid : this.remaining) {
         this.sendRules(this.player(uuid));
      }
   }

   private void advance() {
      switch (this.phase) {
         case PREPARE -> this.beginCall();
         case CALL -> this.beginAction();
         case ACTION -> this.beginSettle();
         case SETTLE -> this.nextRoundOrFinish();
         case ENDED -> {
         }
      }
   }

   private void beginCall() {
      this.phase = Phase.CALL;
      this.ticksLeft = CALL_SECONDS * 20;
      this.phones.setOpen(true);
      if (this.handler != null) {
         this.handler.onCallStart(this);
      }
      this.refreshKits();
      this.broadcast("&a电话已开放。拨打联系人，对方可选择接听或拒绝。接通后聊天仅对方可见。");
   }

   private void beginAction() {
      if (this.settings().callTax()) {
         for (UUID uuid : this.remaining) {
            int tax = (int) Math.round(this.phones.roundDials(uuid) * 0.5);
            if (tax > 0) {
               this.addScore(uuid, -tax);
               this.send(uuid, "&c通话税 -" + tax);
            }
         }
      }
      this.phones.setOpen(false);
      this.phase = Phase.ACTION;
      int seconds = this.handler == null ? 30 : this.handler.actionSeconds(this);
      this.ticksLeft = seconds * 20;
      if (this.handler != null) {
         this.handler.onActionStart(this);
      }
      this.refreshKits();
      this.openActionGuis();
      this.broadcast("&e操作阶段：在时限内提交选择。");
   }

   private void beginSettle() {
      this.phones.setOpen(false);
      this.phase = Phase.SETTLE;
      this.ticksLeft = SETTLE_SECONDS * 20;
      if (this.handler != null) {
         this.handler.onActionTimeout(this);
         this.handler.onSettle(this);
      }
      this.markRoundWinners();
      this.refreshKits();
      this.broadcast("&6结算");
      this.broadcastScoreboard();
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.player(uuid);
         if (player != null) {
            player.closeContainer();
         }
      }
   }

   private void markRoundWinners() {
      int best = Integer.MIN_VALUE;
      for (UUID uuid : this.remaining) {
         int delta = this.lastDelta.getOrDefault(uuid, 0);
         if (delta > best) {
            best = delta;
         }
      }
      if (best <= 0) {
         return;
      }
      for (UUID uuid : this.remaining) {
         if (this.lastDelta.getOrDefault(uuid, 0) == best) {
            this.roundWins.merge(uuid, 1, Integer::sum);
         }
      }
   }

   private void nextRoundOrFinish() {
      this.roundIndex++;
      if (this.roundIndex >= this.schedule.size()) {
         this.finish();
         return;
      }
      this.beginRound();
   }

   private void finish() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.phones.setOpen(false);
      this.phones.hangupAll();
      List<UUID> ranked = this.rankedWithTiebreak();
      this.broadcast("&6&l《诈骗大师》终局");
      int place = 1;
      for (UUID uuid : ranked) {
         this.broadcast("&8" + place + ". " + this.label(uuid) + " &e" + this.score(uuid) + " 分");
         place++;
      }
      if (!ranked.isEmpty()) {
         this.broadcast("&6胜者：「诈骗大师」 " + this.label(ranked.get(0)));
      }
      this.awardTitles(ranked);
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.plots().release(this.claimed);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.fraudMaster().remove(this);
   }

   private List<UUID> rankedWithTiebreak() {
      List<UUID> list = new ArrayList<>(this.seats);
      list.sort(Comparator
         .comparingInt((UUID id) -> this.scores.getOrDefault(id, 0)).reversed()
         .thenComparing(Comparator.comparingInt((UUID id) -> this.roundWins.getOrDefault(id, 0)).reversed())
         .thenComparing(Comparator.comparingInt((UUID id) -> this.betrayals.getOrDefault(id, 0)).reversed())
         .thenComparingInt(id -> ThreadLocalRandom.current().nextInt()));
      return list;
   }

   private void awardTitles(List<UUID> ranked) {
      this.award("诈骗大师", ranked.isEmpty() ? null : ranked.get(0));
      UUID honest = this.bestHonest();
      UUID backstab = this.extreme(this.betrayals, true);
      UUID hated = this.extreme(this.voteHits, true);
      UUID ghost = this.bestGhost();
      UUID chatter = this.bestTalker();
      UUID reverse = this.extreme(this.sumError, true);
      this.award("老实人", honest);
      this.award("背刺之王", backstab);
      this.award("万人嫌", hated);
      this.award("小透明", ghost);
      this.award("电话粥", chatter);
      this.award("反向预言家", reverse);
   }

   private void award(String title, UUID uuid) {
      if (uuid == null) {
         return;
      }
      this.broadcast("&e称号 &6" + title + " &7→ " + this.label(uuid));
   }

   private UUID bestHonest() {
      UUID best = null;
      for (UUID uuid : this.seats) {
         int plays = this.prisonerPlays.getOrDefault(uuid, 0);
         if (plays <= 0) {
            continue;
         }
         if (this.prisonerCoops.getOrDefault(uuid, 0) == plays && this.betrayals.getOrDefault(uuid, 0) == 0) {
            best = uuid;
         }
      }
      return best;
   }

   private UUID bestGhost() {
      for (UUID uuid : this.seats) {
         if (this.phones.neverDialed(uuid)) {
            return uuid;
         }
      }
      return null;
   }

   private UUID bestTalker() {
      UUID best = null;
      long max = 0;
      for (UUID uuid : this.seats) {
         long ticks = this.phones.talkTicks(uuid);
         if (ticks > max) {
            max = ticks;
            best = uuid;
         }
      }
      return max <= 0 ? null : best;
   }

   private UUID extreme(Map<UUID, Integer> map, boolean highest) {
      UUID best = null;
      int value = highest ? Integer.MIN_VALUE : Integer.MAX_VALUE;
      for (UUID uuid : this.seats) {
         int current = map.getOrDefault(uuid, 0);
         if (highest ? current > value : current < value) {
            value = current;
            best = uuid;
         }
      }
      if (highest && value <= 0) {
         return null;
      }
      return best;
   }

   private void broadcastScoreboard() {
      List<UUID> ranked = this.ranked();
      for (UUID uuid : ranked) {
         int delta = this.lastDelta.getOrDefault(uuid, 0);
         String deltaText = delta > 0 ? "&a+" + delta : (delta < 0 ? "&c" + delta : "&70");
         this.broadcast(this.label(uuid) + " &8→ &e" + this.score(uuid) + " &7(" + deltaText + "&7)");
      }
   }

   private void pushBoard() {
      List<UUID> ranked = this.ranked();
      String phaseName = switch (this.phase) {
         case PREPARE -> "&e准备";
         case CALL -> "&a通话";
         case ACTION -> "&6操作";
         case SETTLE -> "&d结算";
         case ENDED -> "&7结束";
      };
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.player(uuid);
         if (player == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7&m---------------");
         lines.add("&7回合 &f" + this.roundNumber() + "&7/&f" + this.totalRounds());
         lines.add("&7游戏 &f" + (this.handler == null ? "-" : this.handler.type().display()));
         lines.add(phaseName + " &f" + this.clock());
         if (this.doubleThisRound()) {
            lines.add("&c得分 ×2");
         }
         ColorCode self = this.colors.get(uuid);
         lines.add("&7代号 " + (self == null ? "&f?" : self.tagged()));
         if (this.handler != null) {
            lines.addAll(this.handler.boardExtra(this, uuid));
         }
         if (this.phase == Phase.CALL && this.phones.isIncoming(uuid)) {
            UUID from = this.phones.incomingCaller(uuid);
            ColorCode fromColor = from == null ? null : this.colors.get(from);
            lines.add("&a来电 &f" + (fromColor == null ? "?" : fromColor.display()));
         } else if (this.phase == Phase.CALL && this.phones.isOutgoing(uuid)) {
            UUID to = this.phones.outgoingCallee(uuid);
            ColorCode toColor = to == null ? null : this.colors.get(to);
            lines.add("&e呼叫 &f" + (toColor == null ? "?" : toColor.display()));
         } else if (this.phase == Phase.CALL && this.phones.inCall(uuid)) {
            List<String> mates = new ArrayList<>();
            for (UUID mate : this.phones.members(uuid)) {
               ColorCode color = this.colors.get(mate);
               mates.add(color == null ? "?" : color.display());
            }
            lines.add("&a通话 &f" + String.join(" ", mates));
         }
         lines.add("&7&m---------------");
         int shown = 0;
         for (UUID row : ranked) {
            if (shown >= 8) {
               break;
            }
            lines.add((row.equals(uuid) ? "&a" : "&f") + this.shortName(row)
               + " &e" + this.score(row));
            shown++;
         }
         this.board.update(player, lines);
      }
   }

   private String shortName(UUID uuid) {
      ColorCode color = this.colors.get(uuid);
      String name = this.ctx.name(uuid);
      if (name.length() > 8) {
         name = name.substring(0, 8);
      }
      if (color == null) {
         return "&f" + name;
      }
      return color.chat() + "[" + color.display() + "]" + name;
   }

   private void actionBars() {
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.player(uuid);
         if (player == null) {
            continue;
         }
         String extra = this.handler == null ? null : this.handler.actionBar(this, uuid);
         String phase = switch (this.phase) {
            case PREPARE -> "&e准备中，电话未开放";
            case CALL -> {
               UUID incomingFrom = this.phones.incomingCaller(uuid);
               UUID outgoingTo = this.phones.outgoingCallee(uuid);
               if (incomingFrom != null) {
                  yield "&a来电：" + this.label(incomingFrom) + " &7右键接听 / 拒绝";
               }
               if (outgoingTo != null) {
                  yield "&e正在呼叫 " + this.label(outgoingTo);
               }
               yield this.phones.inCall(uuid) ? "&a通话中 &8· &7聊天仅对方可见" : "&7静音 · 打开电话拨号";
            }
            case ACTION -> "&6提交操作";
            case SETTLE -> "&d结算";
            case ENDED -> "&7结束";
         };
         String msg = phase + " &8| &f" + this.clock() + (extra == null || extra.isBlank() ? "" : " &8| " + extra);
         player.displayClientMessage(TextUtil.color(msg), true);
      }
   }

   private void enforce() {
      ServerLevel level = this.ctx.plots().level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.remaining) {
         ServerPlayer player = this.player(uuid);
         Plot plot = this.plots.get(uuid);
         if (player == null || plot == null) {
            continue;
         }
         player.setInvisible(false);
         if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
         }
         player.getAbilities().invulnerable = true;
         player.getAbilities().mayfly = false;
         player.onUpdateAbilities();
         boolean inside = player.serverLevel() == level && BoothHut.contains(plot, player.getX(), player.getY(), player.getZ());
         if (!inside) {
            if (this.canPaint(uuid) || this.facingCanvas()) {
               BoothHut.teleportCanvas(player, level, plot);
            } else {
               BoothHut.teleport(player, level, plot);
            }
         }
      }
   }

   private boolean facingCanvas() {
      return this.handler != null && this.handler.type() == RoundType.DRAW_LIE
         && (this.phase == Phase.CALL || this.phase == Phase.ACTION || this.phase == Phase.PREPARE);
   }

   private void ensureTeam(ServerPlayer player) {
      ColorCode color = this.colors.get(player.getUUID());
      if (color == null) {
         return;
      }
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = TEAM_PREFIX + color.name().toLowerCase();
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
         team.setCollisionRule(Team.CollisionRule.NEVER);
         team.setNameTagVisibility(Team.Visibility.ALWAYS);
         team.setColor(color.formatting());
         team.setPlayerPrefix(TextUtil.color(color.tagged() + " "));
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
      player.getAbilities().invulnerable = false;
      player.getAbilities().mayfly = false;
      player.onUpdateAbilities();
      DrawKit.clear(player.getUUID());
      this.board.remove(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   private static ItemStack phoneItem() {
      return GuiItems.action("recovery_compass", "&b电话",
         List.of("&7右键打开联系人", "&e对方可选择接听或拒绝", "&7接通后聊天仅对方可见"), "phone");
   }

   private static ItemStack hangupItem() {
      return GuiItems.action("red_concrete", "&c挂断",
         List.of("&7结束通话、取消呼叫或拒绝来电"), "hangup");
   }

   private static ItemStack answerItem() {
      return GuiItems.action("lime_concrete", "&a接听",
         List.of("&7接通后才能说话"), "answer");
   }

   private static ItemStack rejectItem() {
      return GuiItems.action("barrier", "&c拒绝",
         List.of("&7对方会收到忙音"), "reject");
   }

   private static ItemStack actionItem() {
      return GuiItems.action("clock", "&6提交操作",
         List.of("&7重新打开本回合选择界面"), "action");
   }

   private static ItemStack rulesItem() {
      return GuiItems.action("book", "&e本回合规则",
         List.of("&7右键查看规则与私密信息"), "rules");
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos,
                        float yaw, float pitch, GameType gameType, List<ItemStack> items) {
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
         player.getAbilities().invulnerable = false;
         player.onUpdateAbilities();
         Inventory inv = player.getInventory();
         inv.clearContent();
         for (int i = 0; i < Math.min(inv.getContainerSize(), this.items.size()); i++) {
            inv.setItem(i, this.items.get(i).copy());
         }
      }
   }
}
