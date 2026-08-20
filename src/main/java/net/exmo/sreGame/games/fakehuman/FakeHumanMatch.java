package net.exmo.sreGame.games.fakehuman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.FakeHumanIdGui;
import net.exmo.sreGame.gui.FakeHumanInspectGui;
import net.exmo.sreGame.gui.FakeHumanPickGui;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.voice.SreVoicePlugin;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class FakeHumanMatch {
   public enum Phase {
      PREP,
      DAY,
      NIGHT,
      REVEAL,
      ENDED
   }

   public static final String TEAM_NAME = "srefh";
   private static final int NIGHT_SECONDS = 60;
   private static final int REVEAL_SECONDS = 8;
   private static final int UNTIE_TICKS = 200;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Safehouse house;
   private final Map<UUID, FakeHumanPlayer> players = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Set<String> usedAliases = ConcurrentHashMap.newKeySet();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final Set<UUID> currentKnockers = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Untie> untying = new ConcurrentHashMap<>();
   private Phase phase = Phase.PREP;
   private int day;
   private int ticksLeft;
   private int boardTicks;
   private boolean begun;
   private int trust = 5;
   private int stones;
   private int ammo;
   private int ropes;
   private int inspects;
   private int capacity;
   private int baseCapacity;
   private int majorityNights;
   private int miskills;
   private int keeperCorrect;
   private int keeperWrong;
   private boolean letImpostorIn;
   private boolean impostorExposed;
   private DayEvent event;
   private UUID pendingIdAsk;
   private UUID nightVictim;
   private String pendingReveal;
   private Outcome outcome = Outcome.NONE;

   private enum Outcome {
      NONE,
      HUMAN,
      IMPOSTOR,
      PYRRHIC
   }

   public FakeHumanMatch(GameContext ctx, GameRoom room, List<UUID> seats, Safehouse house) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.house = house;
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&c谁是伪人"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      for (UUID uuid : seats) {
         this.players.put(uuid, new FakeHumanPlayer(uuid));
      }
   }

   public UUID id() {
      return this.id;
   }

   public Phase phase() {
      return this.phase;
   }

   public FakeHumanPlayer player(UUID uuid) {
      return this.players.get(uuid);
   }

   public boolean contains(UUID uuid) {
      return this.players.containsKey(uuid);
   }

   public boolean tempDisabled() {
      return this.event == DayEvent.RAIN;
   }

   public boolean inspectDisabled() {
      return this.event == DayEvent.BLACKOUT;
   }

   public boolean voiceMuted(UUID uuid) {
      FakeHumanPlayer state = this.player(uuid);
      return state == null || !state.alive() || state.bound() || state.zone() == Zone.SPECTATE;
   }

   public boolean sameVoiceZone(UUID a, UUID b) {
      return this.voiceZone(a) == this.voiceZone(b);
   }

   public void start() {
      this.begun = true;
      this.assign();
      ServerLevel level = this.ctx.fakeHuman().houses().level();
      int watchIndex = 0;
      int watchers = Math.max(1, this.seats.size() - 1);
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         FakeHumanPlayer state = this.player(uuid);
         if (player == null || state == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&c谁是伪人");
         this.boss.addPlayer(player);
         this.ensureTeam(player);
         player.getInventory().clearContent();
         player.closeContainer();
         if (state.keeper()) {
            state.setZone(Zone.INSIDE);
            state.setBedIndex(0);
            player.setGameMode(GameType.ADVENTURE);
            this.house.teleport(player, level, this.house.living(0), 180.0F);
         } else {
            state.setZone(Zone.SPECTATE);
            player.setGameMode(GameType.SPECTATOR);
            this.house.teleport(player, level, this.house.doorWatch(watchIndex++, watchers), 0.0F);
         }
         this.sendBriefing(player, state);
      }
      this.giveKits();
      this.syncVoice();
      this.beginDay(1);
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      this.tickUntie();
      this.actionBars();
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
         this.syncVoice();
      }
      if (this.boardTicks % 20 == 0) {
         this.giveKits();
      }
      this.updateBoss();
      if (this.ticksLeft > 0) {
         return;
      }
      if (this.phase == Phase.DAY) {
         this.beginNight();
      } else if (this.phase == Phase.NIGHT) {
         this.settleNight();
      } else if (this.phase == Phase.REVEAL) {
         if (this.tryEnd()) {
            return;
         }
         if (this.day >= this.room.fakeHumanSettings().days()) {
            this.finale();
         } else {
            this.beginDay(this.day + 1);
         }
      }
   }

   public boolean handleChat(ServerPlayer player, String message) {
      FakeHumanPlayer state = this.player(player.getUUID());
      if (state == null) {
         return false;
      }
      if (!state.alive() || state.bound() || state.zone() == Zone.SPECTATE) {
         this.ctx.send(player, state.bound() ? "&c你被捆绑，无法说话。" : "&7旁观中不能说话。");
         return true;
      }
      return false;
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      String action = GuiItems.actionTag(stack);
      if (action == null || !action.startsWith("fh_")) {
         return false;
      }
      if (this.phase != Phase.DAY) {
         this.ctx.send(player, "&7现在不能做这件事。");
         return true;
      }
      FakeHumanPlayer state = this.player(player.getUUID());
      if (state == null || !state.alive() || state.bound()) {
         return true;
      }
      switch (action) {
         case FakeHumanKits.KNOCK -> this.knock(player);
         case FakeHumanKits.ID -> FakeHumanIdGui.openOwn(this.ctx, player, state);
         case FakeHumanKits.ID_SHOW -> this.showId(player);
         case FakeHumanKits.ID_REFUSE -> this.refuseId(player);
         case FakeHumanKits.ACCUSE -> FakeHumanPickGui.open(this.ctx, player, this, FakeHumanPickGui.Kind.ACCUSE);
         case FakeHumanKits.VOUCH -> FakeHumanPickGui.open(this.ctx, player, this, FakeHumanPickGui.Kind.VOUCH);
         case FakeHumanKits.ADMIT -> this.hotbarOrPick(player, FakeHumanPickGui.Kind.ADMIT, this.knockersAlive());
         case FakeHumanKits.REFUSE -> this.refuseHotbar(player);
         case FakeHumanKits.STONE -> this.hotbarOrPick(player, FakeHumanPickGui.Kind.STONE, this.doorOrInsideGuests());
         case FakeHumanKits.GUN -> this.hotbarOrPick(player, FakeHumanPickGui.Kind.GUN, this.doorOrInsideGuests());
         case FakeHumanKits.ROPE -> this.hotbarOrPick(player, FakeHumanPickGui.Kind.ROPE, this.insideGuests());
         case FakeHumanKits.INSPECT -> FakeHumanPickGui.open(this.ctx, player, this, FakeHumanPickGui.Kind.INSPECT);
         case FakeHumanKits.ID_ASK -> FakeHumanPickGui.open(this.ctx, player, this, FakeHumanPickGui.Kind.ID_ASK);
         case FakeHumanKits.NIGHT -> this.tryNight(player);
         default -> {
         }
      }
      return true;
   }

   public boolean handleUseEntity(ServerPlayer player, ServerPlayer target, ItemStack stack) {
      if (target == null) {
         return false;
      }
      FakeHumanPlayer self = this.player(player.getUUID());
      FakeHumanPlayer other = this.player(target.getUUID());
      if (self == null || other == null) {
         return false;
      }
      if (this.tryUntie(player, target)) {
         return true;
      }
      String action = GuiItems.actionTag(stack);
      if (action == null || !action.startsWith("fh_") || this.phase != Phase.DAY || !self.alive() || self.bound()) {
         return action != null && action.startsWith("fh_");
      }
      switch (action) {
         case FakeHumanKits.ADMIT -> this.admit(player, target.getUUID());
         case FakeHumanKits.REFUSE -> this.refuseTarget(player, target.getUUID());
         case FakeHumanKits.STONE -> this.stone(player, target.getUUID());
         case FakeHumanKits.GUN -> this.shoot(player, target.getUUID());
         case FakeHumanKits.ROPE -> this.bind(player, target.getUUID());
         case FakeHumanKits.INSPECT -> FakeHumanInspectGui.open(this.ctx, player, target.getUUID());
         case FakeHumanKits.ID_ASK -> this.askId(player, target.getUUID());
         case FakeHumanKits.ACCUSE -> this.accuse(player, target.getUUID());
         case FakeHumanKits.VOUCH -> this.vouch(player, target.getUUID());
         default -> {
         }
      }
      return true;
   }

   public void handlePick(ServerPlayer player, FakeHumanPickGui.Kind kind, UUID target) {
      if (this.phase != Phase.DAY) {
         return;
      }
      switch (kind) {
         case ADMIT -> this.admit(player, target);
         case STONE -> this.stone(player, target);
         case GUN -> this.shoot(player, target);
         case ROPE -> this.bind(player, target);
         case INSPECT -> FakeHumanInspectGui.open(this.ctx, player, target);
         case ID_ASK -> this.askId(player, target);
         case ACCUSE -> this.accuse(player, target);
         case VOUCH -> this.vouch(player, target);
         case REFUSE -> this.refuseTarget(player, target);
      }
   }

   public List<UUID> pickTargets(UUID actor, FakeHumanPickGui.Kind kind) {
      return switch (kind) {
         case ADMIT -> this.knockersAlive();
         case STONE, GUN -> this.doorOrInsideGuests();
         case ROPE -> this.insideGuests();
         case REFUSE -> this.currentKnockers.isEmpty() ? this.insideGuests() : new ArrayList<>(this.currentKnockers);
         case INSPECT, ID_ASK -> this.inspectable();
         case ACCUSE, VOUCH -> this.aliveExcept(actor);
      };
   }

   public void inspect(ServerPlayer keeper, UUID target, InspectType type) {
      if (!this.isKeeper(keeper.getUUID()) || this.phase != Phase.DAY) {
         return;
      }
      if (this.inspectDisabled()) {
         this.ctx.send(keeper, "&c停电中无法查验。");
         return;
      }
      if (type == InspectType.TEMP && this.tempDisabled()) {
         this.ctx.send(keeper, "&c雨夜导致体温查验失效。");
         return;
      }
      if (this.inspects <= 0) {
         this.ctx.send(keeper, "&c没有查验药剂了。");
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive()) {
         return;
      }
      this.inspects--;
      boolean truth = switch (type) {
         case TEMP -> state.impostor() && state.card() != null && state.card().tempAbnormal();
         case EYES, TEETH -> state.impostor();
         case BACKGROUND -> state.impostor() && state.card() != null && state.card().contradictory();
      };
      if (ThreadLocalRandom.current().nextDouble() < type.missRate()) {
         truth = !truth;
      }
      String result = switch (type) {
         case TEMP -> truth ? "&c体温异常" : "&a体温正常";
         case EYES -> truth ? "&c虹膜发红" : "&a眼睛看起来正常";
         case TEETH -> truth ? "&c牙齿过于整齐/有金牙" : "&a牙齿普通";
         case BACKGROUND -> truth ? "&c证件存在矛盾" : "&a背景暂无破绽";
      };
      this.ctx.send(keeper, "&b查验 &f" + this.guestName(state) + " &8· &7" + type.display() + " &8→ " + result);
      this.giveKits();
   }

   public void onLeave(UUID uuid) {
      FakeHumanPlayer state = this.player(uuid);
      if (state == null) {
         return;
      }
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
         this.boss.removePlayer(player);
      }
      this.board.remove(uuid);
      SreVoicePlugin.leave(uuid);
      if (this.phase == Phase.ENDED) {
         return;
      }
      if (state.keeper()) {
         this.ctx.broadcast(this.room, "&c屋主离开，伪人获胜。");
         this.finish(Outcome.IMPOSTOR);
         return;
      }
      if (state.alive()) {
         this.eliminate(state, FakeHumanPlayer.Death.LEAVE, false);
         this.ctx.broadcast(this.room, "&7" + this.guestName(state) + " 离开并亮出身份：" + state.role().labeled());
      }
      this.currentKnockers.remove(uuid);
      this.tryEnd();
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish(this.outcome == Outcome.NONE ? Outcome.HUMAN : this.outcome);
      }
   }

   private void assign() {
      UUID keeperId = this.room.host();
      FakeHumanPlayer keeper = this.player(keeperId);
      if (keeper != null) {
         keeper.setRole(Role.KEEPER);
         keeper.tags().addAll(PersonaTag.pick());
         keeper.setCard(IdCard.human(this.usedAliases));
      }
      List<UUID> visitors = new ArrayList<>();
      for (UUID uuid : this.seats) {
         if (!uuid.equals(keeperId)) {
            visitors.add(uuid);
         }
      }
      Collections.shuffle(visitors);
      int impostors = this.seats.size() <= 5 ? 1 : 2;
      List<IdCard> humanCards = new ArrayList<>();
      for (int i = impostors; i < visitors.size(); i++) {
         FakeHumanPlayer state = this.player(visitors.get(i));
         state.setRole(Role.HUMAN);
         state.tags().addAll(PersonaTag.pick());
         state.setCard(IdCard.human(this.usedAliases));
         state.setSupply(Supply.random());
         humanCards.add(state.card());
      }
      if (humanCards.isEmpty() && keeper != null) {
         humanCards.add(keeper.card());
      }
      for (int i = 0; i < impostors && i < visitors.size(); i++) {
         FakeHumanPlayer state = this.player(visitors.get(i));
         state.setRole(Role.IMPOSTOR);
         state.tags().addAll(PersonaTag.pick());
         state.setCard(IdCard.impostor(humanCards, this.usedAliases));
         state.setSupply(Supply.random());
      }
      this.baseCapacity = Math.max(2, this.seats.size() - 1);
      this.capacity = this.baseCapacity;
   }

   private void sendBriefing(ServerPlayer player, FakeHumanPlayer state) {
      this.ctx.send(player, "&8&m----------------");
      this.ctx.send(player, "&c谁是伪人 &7· 你的身份：" + state.role().labeled());
      if (state.keeper()) {
         this.ctx.send(player, "&6你是安全屋主人。白天决定去留，误杀会扣信任。独居必死。");
      } else if (state.impostor()) {
         this.ctx.send(player, "&c你是伪人。白天装人，夜里人数占优时可杀人。");
      } else {
         this.ctx.send(player, "&a你是真人访客。活下去，或帮屋主找出伪人。");
      }
      this.ctx.send(player, "&7化名：&f" + state.alias());
      if (!state.keeper() && state.supply() != null) {
         this.ctx.send(player, "&7随身补给：&f" + state.supply().display() + " &8（请进后入库）");
      }
      StringBuilder tags = new StringBuilder("&e人设：");
      for (PersonaTag tag : state.tags()) {
         tags.append(" &f").append(tag.display());
         if (state.impostor()) {
            tags.append("&8(").append(tag.tell()).append(")");
         }
      }
      this.ctx.send(player, tags.toString());
      this.ctx.send(player, "&8&m----------------");
   }

   private void beginDay(int day) {
      this.day = day;
      this.phase = Phase.DAY;
      this.reincarnateDead();
      this.event = DayEvent.values()[ThreadLocalRandom.current().nextInt(DayEvent.values().length)];
      this.capacity = this.baseCapacity;
      if (this.event == DayEvent.SHORTAGE) {
         this.capacity = Math.max(1, this.baseCapacity - 1);
         this.ensureCapacity();
      }
      this.pendingIdAsk = null;
      this.currentKnockers.clear();
      this.untying.clear();
      for (FakeHumanPlayer state : this.players.values()) {
         state.bumpDayAlive();
      }
      this.ticksLeft = this.room.fakeHumanSettings().daySeconds() * 20;
      this.ctx.broadcast(this.room, "&6第 &f" + this.day + "&6/&f" + this.room.fakeHumanSettings().days()
         + " &6天白天开始。事件：&e" + this.event.display() + " &7— " + this.event.desc());
      if (this.event == DayEvent.FEMA && this.miskills > 0) {
         this.ctx.broadcast(this.room, "&cFEMA 记录：本局误杀/误驱逐 &f" + this.miskills + " &c次。");
      }
      this.drawTodaysArrivals();
      if (this.event == DayEvent.CONFESS) {
         this.confess();
      }
      this.teleportDay();
      this.giveKits();
      this.syncVoice();
   }

   private void reincarnateDead() {
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() || state.keeper() || state.leftPermanently()) {
            continue;
         }
         this.reincarnateVisitor(state);
      }
   }

   private void reincarnateVisitor(FakeHumanPlayer state) {
      Role role = ThreadLocalRandom.current().nextDouble() < 0.35 ? Role.IMPOSTOR : Role.HUMAN;
      List<IdCard> humans = IdCard.livingHumans(this.players.values());
      IdCard card = role == Role.IMPOSTOR
         ? IdCard.impostor(humans, this.usedAliases)
         : IdCard.human(this.usedAliases);
      List<PersonaTag> tags = PersonaTag.pick();
      Supply supply = Supply.random();
      String old = state.alias();
      state.reincarnate(role, card, supply, tags);
      this.ctx.broadcast(this.room, "&8" + old + " 的旧身份作废，有人改头换面，待在未到访池。");
      ServerPlayer player = this.ctx.player(state.uuid());
      if (player != null) {
         this.sendBriefing(player, state);
         player.setGameMode(GameType.SPECTATOR);
      }
   }

   private void confess() {
      List<FakeHumanPlayer> candidates = new ArrayList<>();
      for (UUID uuid : this.currentKnockers) {
         FakeHumanPlayer state = this.player(uuid);
         if (state != null && state.alive()) {
            candidates.add(state);
         }
      }
      if (candidates.isEmpty()) {
         return;
      }
      FakeHumanPlayer pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
      this.ctx.broadcast(this.room, "&e自首者：&f" + this.guestName(pick) + " &e声称自己是伪人。");
   }

   private void drawTodaysArrivals() {
      this.currentKnockers.clear();
      List<FakeHumanPlayer> pool = new ArrayList<>();
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.unarrived()) {
            pool.add(state);
         }
      }
      Collections.shuffle(pool);
      int take = this.event == DayEvent.GROUP_KNOCK ? 2 : 1;
      for (int i = 0; i < take && i < pool.size(); i++) {
         FakeHumanPlayer state = pool.get(i);
         state.setZone(Zone.DOOR);
         this.currentKnockers.add(state.uuid());
      }
      if (this.currentKnockers.isEmpty()) {
         this.ctx.broadcast(this.room, "&7今日没有访客上门。屋主可用时钟提前入夜。");
         return;
      }
      ServerLevel level = this.ctx.fakeHuman().houses().level();
      int i = 0;
      StringBuilder names = new StringBuilder();
      for (UUID uuid : this.currentKnockers) {
         FakeHumanPlayer state = this.player(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null && level != null) {
            Vec3 pos = i++ == 0 ? this.house.doorOutside() : this.house.doorOutsideSecondary();
            player.setGameMode(GameType.ADVENTURE);
            this.house.teleport(player, level, pos, 0.0F);
         }
         if (names.length() > 0) {
            names.append("&7, &f");
         }
         names.append(state == null ? "?" : this.guestName(state));
      }
      FakeHumanPlayer keeper = this.keeperState();
      ServerPlayer host = keeper == null ? null : this.ctx.player(keeper.uuid());
      if (host != null && level != null) {
         this.house.teleport(host, level, this.house.vestibule(), 180.0F);
      }
      this.ctx.broadcast(this.room, "&e敲门：&f" + names + " &7— 隔门对话后决定去留。今日不再来下一批。");
   }

   private void beginNight() {
      this.phase = Phase.NIGHT;
      this.ticksLeft = NIGHT_SECONDS * 20;
      this.nightVictim = null;
      for (UUID uuid : List.copyOf(this.currentKnockers)) {
         FakeHumanPlayer state = this.player(uuid);
         if (state != null && state.alive()) {
            this.toUnarrived(state);
         }
      }
      this.currentKnockers.clear();
      this.assignBeds();
      this.teleportNight();
      this.ctx.broadcast(this.room, "&8夜晚降临。关灯，回到床上…");
      this.giveKits();
      this.syncVoice();
   }

   private void settleNight() {
      int humans = 0;
      int impostors = 0;
      List<FakeHumanPlayer> insideHumans = new ArrayList<>();
      boolean killerReady = false;
      for (FakeHumanPlayer state : this.players.values()) {
         if (!state.alive() || state.zone() != Zone.INSIDE) {
            continue;
         }
         if (state.impostor()) {
            impostors++;
            if (!state.bound()) {
               killerReady = true;
            }
         } else {
            humans++;
            insideHumans.add(state);
         }
      }
      if (humans == 1 && impostors == 0 && this.keeperAlive() && this.insideCount() <= 1) {
         this.pendingReveal = "&c独居之夜。屋主在空屋里死去。";
         FakeHumanPlayer keeper = this.keeperState();
         if (keeper != null) {
            keeper.kill(FakeHumanPlayer.Death.NIGHT);
         }
         this.outcome = Outcome.IMPOSTOR;
         this.beginReveal();
         return;
      }
      if (impostors >= humans && killerReady && !insideHumans.isEmpty()) {
         insideHumans.sort(Comparator.comparing((FakeHumanPlayer p) -> p.bound() ? 0 : 1));
         FakeHumanPlayer victim = insideHumans.get(0);
         this.nightVictim = victim.uuid();
         this.eliminate(victim, FakeHumanPlayer.Death.NIGHT, false);
         this.pendingReveal = "&c夜里传来一声闷响。&f" + this.guestName(victim)
            + " &c死了，身份：" + victim.role().labeled();
      } else {
         this.pendingReveal = "&a平安夜。屋内暂时安全。";
      }
      if (impostors >= humans && impostors > 0) {
         this.majorityNights++;
      } else {
         this.majorityNights = 0;
      }
      this.beginReveal();
   }

   private void beginReveal() {
      this.phase = Phase.REVEAL;
      this.ticksLeft = REVEAL_SECONDS * 20;
      if (this.pendingReveal != null) {
         this.ctx.broadcast(this.room, this.pendingReveal);
      }
      this.teleportDay();
      this.giveKits();
   }

   private boolean tryEnd() {
      if (!this.keeperAlive() && this.day < this.room.fakeHumanSettings().days()) {
         this.finish(Outcome.IMPOSTOR);
         return true;
      }
      int livingImpostors = this.count(p -> p.alive() && p.impostor());
      int insideHumans = this.count(p -> p.alive() && p.zone() == Zone.INSIDE && p.role().humanSide());
      if (livingImpostors == 0 && insideHumans >= 2) {
         this.finish(Outcome.HUMAN);
         return true;
      }
      if (this.majorityNights >= 2) {
         this.finish(Outcome.IMPOSTOR);
         return true;
      }
      if (this.trust <= 0) {
         FakeHumanPlayer keeper = this.keeperState();
         if (keeper != null && keeper.alive()) {
            keeper.kill(FakeHumanPlayer.Death.OVERTHROW);
            this.ctx.broadcast(this.room, "&c信任归零，屋主被推翻。");
         }
         this.finish(Outcome.IMPOSTOR);
         return true;
      }
      return false;
   }

   private void finale() {
      if (!this.keeperAlive()) {
         this.finish(Outcome.IMPOSTOR);
         return;
      }
      int impostors = this.count(p -> p.alive() && p.zone() == Zone.INSIDE && p.impostor());
      int humans = this.count(p -> p.alive() && p.zone() == Zone.INSIDE && p.role().humanSide());
      if (humans == 1 && impostors == 1) {
         this.finish(Outcome.PYRRHIC);
      } else if (this.keeperAlive() && humans >= 1) {
         this.finish(Outcome.HUMAN);
      } else {
         this.finish(Outcome.IMPOSTOR);
      }
   }

   private void admit(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID())) {
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || !this.currentKnockers.contains(target) && state.zone() != Zone.DOOR) {
         this.ctx.send(actor, "&c只能请进正在敲门的人。");
         return;
      }
      if (this.guestInside() >= this.capacity) {
         this.ctx.send(actor, "&c床位已满（容量 &f" + this.capacity + "&c）。先把人打发回门外。");
         return;
      }
      state.setZone(Zone.INSIDE);
      state.setAdmittedByKeeper(true);
      if (state.impostor()) {
         this.letImpostorIn = true;
      }
      this.currentKnockers.remove(target);
      this.stashSupply(state);
      this.ctx.broadcast(this.room, "&a" + this.guestName(state) + " &a被请进安全屋。");
      this.teleportDay();
      this.giveKits();
      this.tryEnd();
   }

   private void stashSupply(FakeHumanPlayer state) {
      if (state.supply() == null) {
         return;
      }
      switch (state.supply()) {
         case STONE -> this.stones++;
         case AMMO -> this.ammo++;
         case ROPE -> this.ropes++;
         case INSPECT -> this.inspects++;
      }
      this.ctx.broadcast(this.room, "&7柜中多了 &f" + state.supply().display() + "&7。");
      state.setSupply(null);
   }

   private void refuseHotbar(ServerPlayer actor) {
      if (!this.isKeeper(actor.getUUID())) {
         return;
      }
      if (this.currentKnockers.size() == 1) {
         this.refuseTarget(actor, this.currentKnockers.iterator().next());
         return;
      }
      FakeHumanPickGui.open(this.ctx, actor, this, FakeHumanPickGui.Kind.REFUSE);
   }

   private void refuseTarget(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID())) {
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || state.keeper()) {
         return;
      }
      if (this.currentKnockers.contains(target) || state.zone() == Zone.DOOR) {
         this.toUnarrived(state);
         this.currentKnockers.remove(target);
         this.ctx.broadcast(this.room, "&7" + this.guestName(state) + " 被拒之门外，回到未到访池。");
         this.teleportDay();
         this.giveKits();
         return;
      }
      if (state.zone() == Zone.INSIDE) {
         this.toUnarrived(state);
         this.ctx.broadcast(this.room, "&e" + this.guestName(state) + " 被打发回门外。");
         this.teleportDay();
         this.giveKits();
      }
   }

   private void toUnarrived(FakeHumanPlayer state) {
      state.setBound(false);
      state.setZone(Zone.SPECTATE);
   }

   private void stone(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID()) || this.stones <= 0) {
         this.ctx.send(actor, "&c没有驱逐之石了。");
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || state.keeper() || state.zone() == Zone.SPECTATE) {
         return;
      }
      this.stones--;
      boolean impostor = state.impostor();
      this.judge(impostor, 1, 2);
      this.eliminate(state, FakeHumanPlayer.Death.STONE, true);
      this.currentKnockers.remove(target);
      this.ctx.broadcast(this.room, "&6驱逐之石命中 &f" + this.guestName(state)
         + " &6— 身份：" + state.role().labeled());
      this.teleportDay();
      this.giveKits();
      this.tryEnd();
   }

   private void shoot(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID()) || this.ammo <= 0) {
         this.ctx.send(actor, "&c没有弹药了。");
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || state.keeper() || state.zone() == Zone.SPECTATE) {
         return;
      }
      this.ammo--;
      boolean impostor = state.impostor();
      this.judge(impostor, 1, 3);
      this.eliminate(state, FakeHumanPlayer.Death.GUN, true);
      this.currentKnockers.remove(target);
      this.ctx.broadcast(this.room, "&c枪声响起。&f" + this.guestName(state)
         + " &c显形，身份：" + state.role().labeled());
      this.teleportDay();
      this.giveKits();
      this.tryEnd();
   }

   private void bind(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID()) || this.ropes <= 0) {
         this.ctx.send(actor, "&c没有绳子了。");
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || state.keeper() || state.zone() != Zone.INSIDE) {
         this.ctx.send(actor, "&c只能捆绑屋内访客。");
         return;
      }
      if (state.bound()) {
         return;
      }
      this.ropes--;
      state.setBound(true);
      this.ctx.broadcast(this.room, "&e" + this.guestName(state) + " 被捆绑。");
      this.giveKits();
      this.syncVoice();
   }

   private void askId(ServerPlayer actor, UUID target) {
      if (!this.isKeeper(actor.getUUID())) {
         return;
      }
      FakeHumanPlayer state = this.player(target);
      if (state == null || !state.alive() || state.zone() == Zone.SPECTATE) {
         return;
      }
      this.pendingIdAsk = target;
      ServerPlayer other = this.ctx.player(target);
      this.ctx.send(actor, "&7你要求 &f" + this.guestName(state) + " &7出示证件。");
      if (other != null) {
         this.ctx.send(other, "&e屋主要求你出示证件。点出示或拒绝。");
      }
   }

   private void showId(ServerPlayer player) {
      FakeHumanPlayer state = this.player(player.getUUID());
      if (state == null || !state.alive()) {
         return;
      }
      if (!player.getUUID().equals(this.pendingIdAsk)) {
         this.ctx.send(player, "&7屋主还没有要求你出示。");
         return;
      }
      FakeHumanPlayer keeper = this.keeperState();
      ServerPlayer host = keeper == null ? null : this.ctx.player(keeper.uuid());
      if (host != null) {
         FakeHumanIdGui.openShown(this.ctx, host, state);
         this.ctx.send(host, "&a" + this.guestName(state) + " 出示了证件。");
      }
      this.pendingIdAsk = null;
   }

   private void refuseId(ServerPlayer player) {
      FakeHumanPlayer state = this.player(player.getUUID());
      if (state == null) {
         return;
      }
      state.setRefusedId(true);
      this.ctx.broadcast(this.room, "&c" + this.guestName(state) + " 拒绝出示证件。");
      FakeHumanPlayer keeper = this.keeperState();
      ServerPlayer host = keeper == null ? null : this.ctx.player(keeper.uuid());
      if (host != null) {
         this.ctx.send(host, "&c对方拒绝了，记下嫌疑。");
      }
      if (player.getUUID().equals(this.pendingIdAsk)) {
         this.pendingIdAsk = null;
      }
   }

   private void accuse(ServerPlayer player, UUID target) {
      FakeHumanPlayer state = this.player(target);
      if (state == null || target.equals(player.getUUID())) {
         return;
      }
      state.suspectedBy().add(player.getUUID());
      FakeHumanPlayer self = this.player(player.getUUID());
      this.ctx.broadcast(this.room, "&c" + this.guestName(self) + " 指认了 " + this.guestName(state) + "。");
   }

   private void vouch(ServerPlayer player, UUID target) {
      FakeHumanPlayer state = this.player(target);
      if (state == null || target.equals(player.getUUID())) {
         return;
      }
      state.vouchedBy().add(player.getUUID());
      FakeHumanPlayer self = this.player(player.getUUID());
      this.ctx.broadcast(this.room, "&a" + this.guestName(self) + " 为 " + this.guestName(state) + " 担保。");
   }

   private void knock(ServerPlayer player) {
      if (!this.currentKnockers.contains(player.getUUID())) {
         this.ctx.send(player, "&7还没轮到你。");
         return;
      }
      FakeHumanPlayer state = this.player(player.getUUID());
      this.ctx.broadcast(this.room, "&e" + this.guestName(state) + " 在敲门。");
   }

   private void tryNight(ServerPlayer actor) {
      if (!this.isKeeper(actor.getUUID())) {
         return;
      }
      if (!this.currentKnockers.isEmpty()) {
         this.ctx.send(actor, "&c先处理完门口的人。");
         return;
      }
      this.beginNight();
   }

   private void judge(boolean correct, int plus, int minus) {
      if (correct) {
         this.trust = Math.min(10, this.trust + plus);
         this.keeperCorrect++;
      } else {
         this.trust = Math.max(0, this.trust - minus);
         this.keeperWrong++;
         this.miskills++;
      }
   }

   private void eliminate(FakeHumanPlayer state, FakeHumanPlayer.Death death, boolean byKeeper) {
      if (state.impostor() && !this.impostorExposed) {
         state.setFirstExposedImpostor(true);
         this.impostorExposed = true;
      }
      state.kill(death);
      ServerPlayer player = this.ctx.player(state.uuid());
      if (player != null) {
         player.setGameMode(GameType.SPECTATOR);
         this.house.teleport(player, this.ctx.fakeHuman().houses().level(), this.house.spectator(), 0.0F);
      }
   }

   private void ensureCapacity() {
      while (this.guestInside() > this.capacity) {
         FakeHumanPlayer extra = null;
         for (FakeHumanPlayer state : this.players.values()) {
            if (state.alive() && !state.keeper() && state.zone() == Zone.INSIDE) {
               extra = state;
               break;
            }
         }
         if (extra == null) {
            break;
         }
         this.toUnarrived(extra);
         this.ctx.broadcast(this.room, "&e容量不足，" + this.guestName(extra) + " 被请回未到访池。");
      }
   }

   private void assignBeds() {
      int next = 1;
      FakeHumanPlayer keeper = this.keeperState();
      if (keeper != null) {
         keeper.setBedIndex(0);
      }
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() && state.zone() == Zone.INSIDE && !state.keeper()) {
            state.setBedIndex(next++);
         }
      }
   }

   private void teleportDay() {
      ServerLevel level = this.ctx.fakeHuman().houses().level();
      if (level == null) {
         return;
      }
      int watchIndex = 0;
      int watchers = Math.max(1, this.count(p -> p.alive() && p.zone() == Zone.SPECTATE));
      int liveIndex = 0;
      int doorIndex = 0;
      for (UUID uuid : this.seats) {
         FakeHumanPlayer state = this.player(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (state == null || player == null) {
            continue;
         }
         if (!state.alive() || state.zone() == Zone.DEAD) {
            player.setGameMode(GameType.SPECTATOR);
            this.house.teleport(player, level, this.house.spectator(), 0.0F);
            continue;
         }
         switch (state.zone()) {
            case SPECTATE -> {
               player.setGameMode(GameType.SPECTATOR);
               this.house.teleport(player, level, this.house.doorWatch(watchIndex++, watchers), 0.0F);
            }
            case DOOR -> {
               player.setGameMode(GameType.ADVENTURE);
               Vec3 pos = doorIndex++ == 0 ? this.house.doorOutside() : this.house.doorOutsideSecondary();
               this.house.teleport(player, level, pos, 0.0F);
            }
            case INSIDE -> {
               player.setGameMode(GameType.ADVENTURE);
               if (state.keeper()) {
                  this.house.teleport(player, level,
                     this.currentKnockers.isEmpty() ? this.house.living(0) : this.house.vestibule(),
                     this.currentKnockers.isEmpty() ? 180.0F : 180.0F);
               } else {
                  this.house.teleport(player, level, this.house.living(liveIndex++), 0.0F);
               }
            }
            case DEAD -> this.house.teleport(player, level, this.house.spectator(), 0.0F);
         }
      }
   }

   private void teleportNight() {
      ServerLevel level = this.ctx.fakeHuman().houses().level();
      if (level == null) {
         return;
      }
      int watchIndex = 0;
      int watchers = Math.max(1, this.count(p -> p.alive() && p.zone() == Zone.SPECTATE));
      for (UUID uuid : this.seats) {
         FakeHumanPlayer state = this.player(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (state == null || player == null) {
            continue;
         }
         if (!state.alive() || state.zone() == Zone.DEAD) {
            player.setGameMode(GameType.SPECTATOR);
            this.house.teleport(player, level, this.house.spectator(), 0.0F);
            continue;
         }
         if (state.zone() == Zone.INSIDE || state.keeper()) {
            if (state.keeper()) {
               state.setZone(Zone.INSIDE);
            }
            player.setGameMode(GameType.ADVENTURE);
            this.house.teleport(player, level, this.house.bed(Math.max(0, state.bedIndex())), 90.0F);
         } else {
            player.setGameMode(GameType.SPECTATOR);
            this.house.teleport(player, level, this.house.doorWatch(watchIndex++, watchers), 0.0F);
         }
      }
   }

   private void enforce() {
      ServerLevel level = this.ctx.fakeHuman().houses().level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.seats) {
         FakeHumanPlayer state = this.player(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (state == null || player == null) {
            continue;
         }
         player.getFoodData().setFoodLevel(20);
         player.setInvisible(false);
         this.applyEffects(player, state);
         if (!state.alive() || state.zone() == Zone.DEAD) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            if (!this.house.contains(player.getX(), player.getY(), player.getZ())) {
               this.snap(player, state, level);
            }
            continue;
         }
         if (state.zone() == Zone.SPECTATE) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            if (!this.house.inWaiting(player.getX(), player.getY(), player.getZ())
               && !this.house.inDoor(player.getX(), player.getY(), player.getZ())) {
               this.snap(player, state, level);
            }
            continue;
         }
         if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
         }
         boolean ok = this.house.contains(player.getX(), player.getY(), player.getZ())
            && this.inAllowed(player, state);
         if (!ok || state.bound()) {
            this.snap(player, state, level);
         }
      }
   }

   private boolean inAllowed(ServerPlayer player, FakeHumanPlayer state) {
      double x = player.getX();
      double y = player.getY();
      double z = player.getZ();
      return switch (state.zone()) {
         case SPECTATE, DEAD -> true;
         case DOOR -> this.house.inDoor(x, y, z);
         case INSIDE -> this.house.inHouse(x, y, z);
      };
   }

   private void snap(ServerPlayer player, FakeHumanPlayer state, ServerLevel level) {
      switch (state.zone()) {
         case SPECTATE -> this.house.teleport(player, level, this.house.doorWatch(0, 1), 0.0F);
         case DOOR -> this.house.teleport(player, level, this.house.doorOutside(), 0.0F);
         case INSIDE -> this.house.teleport(player, level,
            state.keeper() ? this.house.vestibule() : this.house.living(0), 180.0F);
         case DEAD -> this.house.teleport(player, level, this.house.spectator(), 0.0F);
      }
   }

   private void applyEffects(ServerPlayer player, FakeHumanPlayer state) {
      if (this.phase == Phase.NIGHT && state.alive() && state.zone() == Zone.INSIDE) {
         player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false, false));
      }
      if (!state.alive() || state.zone() == Zone.SPECTATE) {
         return;
      }
      if (state.bound()) {
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9, true, false, false));
      }
      if (state.tags().contains(PersonaTag.DYING)) {
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false, false));
      }
      if (state.tags().contains(PersonaTag.DRUNK)) {
         player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, false, false));
      }
   }

   private boolean tryUntie(ServerPlayer helper, ServerPlayer target) {
      FakeHumanPlayer other = this.player(target.getUUID());
      FakeHumanPlayer self = this.player(helper.getUUID());
      if (other == null || self == null || !other.bound() || !self.alive() || self.bound() || !other.alive()) {
         this.untying.remove(helper.getUUID());
         return false;
      }
      if (helper.distanceTo(target) > 3.5F) {
         this.untying.remove(helper.getUUID());
         return false;
      }
      Untie progress = this.untying.computeIfAbsent(helper.getUUID(), id -> new Untie(target.getUUID()));
      if (!progress.target.equals(target.getUUID())) {
         progress = new Untie(target.getUUID());
         this.untying.put(helper.getUUID(), progress);
      }
      progress.ticks++;
      helper.displayClientMessage(TextUtil.color("&e解开绳子 &f" + (progress.ticks * 100 / UNTIE_TICKS) + "%"), true);
      if (progress.ticks >= UNTIE_TICKS) {
         other.setBound(false);
         this.untying.remove(helper.getUUID());
         this.ctx.broadcast(this.room, "&a" + this.guestName(self) + " 解开了 "
            + this.guestName(other) + " 的绳子。");
         this.syncVoice();
      }
      return true;
   }

   private void tickUntie() {
      this.untying.entrySet().removeIf(e -> {
         ServerPlayer helper = this.ctx.player(e.getKey());
         ServerPlayer target = this.ctx.player(e.getValue().target);
         return helper == null || target == null || helper.distanceTo(target) > 3.5F;
      });
   }

   private void giveKits() {
      boolean canNight = this.phase == Phase.DAY && this.currentKnockers.isEmpty();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         FakeHumanPlayer state = this.player(uuid);
         if (player == null || state == null) {
            continue;
         }
         Inventory inv = player.getInventory();
         inv.clearContent();
         List<ItemStack> items;
         if (!state.alive()) {
            items = FakeHumanKits.deadWatch();
         } else if (state.zone() == Zone.SPECTATE) {
            items = FakeHumanKits.spectator();
         } else if (state.keeper()) {
            items = FakeHumanKits.keeperDoor(canNight);
            FakeHumanKits.addSupplies(items, this.stones, this.ammo, this.ropes, this.inspects);
         } else if (state.zone() == Zone.DOOR) {
            items = FakeHumanKits.arriver(state);
         } else {
            items = FakeHumanKits.insideGuest(state);
         }
         for (int i = 0; i < items.size() && i < inv.getContainerSize(); i++) {
            inv.setItem(i, items.get(i));
         }
      }
   }

   private void syncVoice() {
      for (UUID uuid : this.seats) {
         FakeHumanPlayer state = this.player(uuid);
         if (state == null) {
            continue;
         }
         SreVoicePlugin.applyGroup(uuid, this.voiceZone(uuid), this.id);
      }
   }

   private Zone voiceZone(UUID uuid) {
      FakeHumanPlayer state = this.player(uuid);
      if (state == null || !state.alive()) {
         return Zone.DEAD;
      }
      if (state.zone() == Zone.DOOR || state.zone() == Zone.SPECTATE) {
         return Zone.DOOR;
      }
      if (state.keeper() && !this.currentKnockers.isEmpty() && this.phase == Phase.DAY) {
         return Zone.DOOR;
      }
      return Zone.INSIDE;
   }

   private void updateBoss() {
      int total = switch (this.phase) {
         case DAY -> Math.max(1, this.room.fakeHumanSettings().daySeconds() * 20);
         case NIGHT -> NIGHT_SECONDS * 20;
         default -> REVEAL_SECONDS * 20;
      };
      this.boss.setProgress(Math.max(0.0F, Math.min(1.0F, this.ticksLeft / (float) total)));
      String title = switch (this.phase) {
         case DAY -> "&6Day " + this.day + " 白天";
         case NIGHT -> "&8Day " + this.day + " 夜晚";
         case REVEAL -> "&e揭晓";
         default -> "&c谁是伪人";
      };
      this.boss.setName(TextUtil.color(title));
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null && !this.boss.getPlayers().contains(player)) {
            this.boss.addPlayer(player);
         }
      }
   }

   private void actionBars() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      String clock = String.format("%d:%02d", seconds / 60, seconds % 60);
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         FakeHumanPlayer state = this.player(uuid);
         if (player == null || state == null) {
            continue;
         }
         String extra;
         if (state.keeper()) {
            extra = "&8| &6信任 " + this.trust + " &8| &7石" + this.stones + " 弹" + this.ammo
               + " 绳" + this.ropes + " 药" + this.inspects;
         } else if (!state.alive()) {
            extra = "&8| 等待再访";
         } else if (state.zone() == Zone.SPECTATE) {
            extra = "&8| 旁观门口 禁麦";
         } else {
            extra = "&8| " + state.role().labeled() + " &7" + state.alias();
         }
         player.displayClientMessage(TextUtil.color("&7" + clock + " " + extra), true);
      }
   }

   private void pushBoard() {
      int seconds = Math.max(0, this.ticksLeft / 20);
      String clock = String.format("%d:%02d", seconds / 60, seconds % 60);
      int unarrived = this.count(FakeHumanPlayer::unarrived);
      String guests = this.todayGuests();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         FakeHumanPlayer state = this.player(uuid);
         if (player == null || state == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&7&m---------------");
         lines.add("&7Day &f" + this.day + "&7/&f" + this.room.fakeHumanSettings().days());
         lines.add("&7阶段 &f" + switch (this.phase) {
            case DAY -> "白天";
            case NIGHT -> "夜晚";
            case REVEAL -> "揭晓";
            default -> this.phase.name();
         });
         lines.add("&7剩余 &f" + clock);
         if (this.event != null) {
            lines.add("&e" + this.event.display());
         }
         lines.add("&6信任 &f" + this.trust);
         lines.add("&7床位 &f" + this.guestInside() + "&7/&f" + this.capacity);
         lines.add("&7未到访 &f" + unarrived);
         lines.add("&7柜 &f石" + this.stones + " 弹" + this.ammo + " 绳" + this.ropes + " 药" + this.inspects);
         if (!guests.isEmpty() && this.phase == Phase.DAY) {
            lines.add("&e今日 &f" + guests);
         }
         if (!state.keeper() && state.alive()) {
            lines.add("&7化名 &f" + state.alias());
            lines.add(state.role().labeled());
         }
         lines.add("&7&m---------------");
         this.board.update(player, lines);
      }
   }

   private String todayGuests() {
      StringBuilder names = new StringBuilder();
      for (UUID uuid : this.currentKnockers) {
         FakeHumanPlayer state = this.player(uuid);
         if (state == null) {
            continue;
         }
         if (names.length() > 0) {
            names.append("&7, ");
         }
         names.append(state.alias());
      }
      return names.toString();
   }

   private String guestName(FakeHumanPlayer state) {
      if (state == null) {
         return "访客";
      }
      if (state.keeper()) {
         return "屋主";
      }
      return "访客 " + state.alias();
   }

   private void finish(Outcome outcome) {
      this.phase = Phase.ENDED;
      this.outcome = outcome;
      String title = switch (outcome) {
         case PYRRHIC -> "&6惨胜 — 真人方勉强活过终局";
         case HUMAN -> "&a真人 / 屋主胜利";
         default -> "&c伪人胜利";
      };
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&c&l谁是伪人结算");
      this.ctx.broadcast(this.room, title);
      for (UUID uuid : this.seats) {
         FakeHumanPlayer state = this.player(uuid);
         this.ctx.broadcast(this.room, "&8• &f" + this.ctx.name(uuid)
            + (state == null ? "" : " &7(" + state.alias() + ") " + state.role().labeled())
            + (state != null && !state.alive() ? " &8(出局)" : " &a存活"));
      }
      this.awardTitles();
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         SreVoicePlugin.leave(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.fakeHuman().houses().release(this.house);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.fakeHuman().remove(this);
   }

   private void awardTitles() {
      FakeHumanPlayer keeper = this.keeperState();
      if (keeper != null && this.keeperCorrect + this.keeperWrong > 0 && this.keeperCorrect >= this.keeperWrong) {
         this.ctx.broadcast(this.room, "&6最强大脑 &7— &f" + this.ctx.name(keeper.uuid())
            + " &7正确 " + this.keeperCorrect + " / 错误 " + this.keeperWrong);
      }
      UUID actor = null;
      int best = -1;
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.maxImpostorDays() > best) {
            best = state.maxImpostorDays();
            actor = state.uuid();
         }
      }
      if (actor != null && best > 0) {
         this.ctx.broadcast(this.room, "&5影帝 &7— &f" + this.ctx.name(actor) + " &7伪人存活 " + best + " 天");
      }
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.wronged()) {
            this.ctx.broadcast(this.room, "&c冤大头 &7— &f" + this.ctx.name(state.uuid()));
         }
         if (state.firstExposedImpostor()) {
            this.ctx.broadcast(this.room, "&4自爆卡车 &7— &f" + this.ctx.name(state.uuid()));
         }
         if (state.alive() || state.death() != FakeHumanPlayer.Death.NONE) {
            if (state.suspectedBy().isEmpty() && state.vouchedBy().isEmpty() && !state.keeper()) {
               this.ctx.broadcast(this.room, "&7小透明 &7— &f" + this.ctx.name(state.uuid()));
            }
         }
      }
      if (keeper != null && !this.letImpostorIn) {
         this.ctx.broadcast(this.room, "&b门神 &7— &f" + this.ctx.name(keeper.uuid()));
      }
   }

   private void hotbarOrPick(ServerPlayer player, FakeHumanPickGui.Kind kind, List<UUID> options) {
      if (kind == FakeHumanPickGui.Kind.ADMIT && this.currentKnockers.size() == 1) {
         this.admit(player, this.currentKnockers.iterator().next());
         return;
      }
      FakeHumanPickGui.open(this.ctx, player, this, kind);
   }

   private List<UUID> knockersAlive() {
      return new ArrayList<>(this.currentKnockers);
   }

   private List<UUID> doorOrInsideGuests() {
      List<UUID> out = new ArrayList<>();
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() && !state.keeper() && (state.zone() == Zone.DOOR || state.zone() == Zone.INSIDE)) {
            out.add(state.uuid());
         }
      }
      return out;
   }

   private List<UUID> insideGuests() {
      List<UUID> out = new ArrayList<>();
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() && !state.keeper() && state.zone() == Zone.INSIDE) {
            out.add(state.uuid());
         }
      }
      return out;
   }

   private List<UUID> inspectable() {
      List<UUID> out = new ArrayList<>();
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() && !state.keeper() && (state.zone() == Zone.DOOR || state.zone() == Zone.INSIDE)) {
            out.add(state.uuid());
         }
      }
      return out;
   }

   private List<UUID> aliveExcept(UUID actor) {
      List<UUID> out = new ArrayList<>();
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.alive() && !state.uuid().equals(actor) && state.zone() != Zone.SPECTATE) {
            out.add(state.uuid());
         }
      }
      return out;
   }

   private int insideCount() {
      return this.count(p -> p.alive() && p.zone() == Zone.INSIDE);
   }

   private int guestInside() {
      return this.count(p -> p.alive() && !p.keeper() && p.zone() == Zone.INSIDE);
   }

   private int count(java.util.function.Predicate<FakeHumanPlayer> test) {
      int n = 0;
      for (FakeHumanPlayer state : this.players.values()) {
         if (test.test(state)) {
            n++;
         }
      }
      return n;
   }

   private boolean keeperAlive() {
      FakeHumanPlayer keeper = this.keeperState();
      return keeper != null && keeper.alive();
   }

   private FakeHumanPlayer keeperState() {
      for (FakeHumanPlayer state : this.players.values()) {
         if (state.keeper()) {
            return state;
         }
      }
      return null;
   }

   private boolean isKeeper(UUID uuid) {
      FakeHumanPlayer state = this.player(uuid);
      return state != null && state.keeper();
   }

   private void ensureTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayerTeam(TEAM_NAME);
      if (team == null) {
         team = board.addPlayerTeam(TEAM_NAME);
         team.setCollisionRule(Team.CollisionRule.NEVER);
         team.setNameTagVisibility(Team.Visibility.NEVER);
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
      player.removeAllEffects();
      this.board.remove(player);
      this.boss.removePlayer(player);
      SreVoicePlugin.leave(player.getUUID());
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   private static final class Untie {
      final UUID target;
      int ticks;

      Untie(UUID target) {
         this.target = target;
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
