package net.exmo.sreGame.games.chicken;

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
import net.exmo.sreGame.gui.ChickenHorsePickGui;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class ChickenHorseMatch {
   public enum Phase {
      INTRO,
      PLACE,
      COUNTDOWN,
      RACE,
      SCORE,
      ENDED
   }

   public static final String TEAM_NAME = "srech";
   private static final int INTRO_SECONDS = 5;
   private static final int COUNTDOWN_TICKS = 60;
   private static final int SCORE_SECONDS = 8;
   private static final int[] PLACE_POINTS = {5, 3, 2};
   private static final DustParticleOptions KILL_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.12F), 1.15F);
   private static final double MAGNET_RANGE = 8.0;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final Track track;
   private final ChickenHorseSettings settings;
   private final Map<UUID, Racer> racers = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Set<BlockPos> playerPlaced = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Gadget> gadgets = new ConcurrentHashMap<>();
   private final Map<BlockPos, Integer> fakeStand = new ConcurrentHashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final List<UUID> finishOrder = new ArrayList<>();
   private Phase phase = Phase.INTRO;
   private int round = 1;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int boardTicks;
   private boolean begun;
   private boolean laserLit;
   private BlockPos eggPos;
   private UUID eggHolder;

   public ChickenHorseMatch(GameContext ctx, GameRoom room, List<UUID> seats, Track track) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.track = track;
      this.settings = room.chickenHorseSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6超级鸡马"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      for (UUID uuid : this.seats) {
         this.racers.put(uuid, new Racer(uuid));
      }
   }

   public UUID id() {
      return this.id;
   }

   public Phase phase() {
      return this.phase;
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      int i = 0;
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&6超级鸡马");
         this.boss.addPlayer(player);
         this.ensureTeam(player);
         player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.track.teleport(player, level, this.track.spawn(i++, this.seats.size()));
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l超级鸡马");
      this.ctx.broadcast(this.room, "&7每人每轮随机 1 或 2 个机关额度，从 6 个里自选，再发 2–4 块铺路。");
      this.ctx.broadcast(this.room, "&7赛道不清空，越往后越毒。先到终点得分，金蛋过线再 +2。");
      this.ctx.broadcast(this.room, "&7玩家互不碰撞。热键栏末格右键可隐藏/显示其他人。");
      this.ctx.broadcast(this.room, "&b冲关：贴墙跳蹬墙 · 潜行攀爬到顶可翻上去");
      this.ctx.broadcast(this.room, "&c受伤或掉下虚空出局旁观，摔落伤害除外。");
      if (SreSceneBlocks.loaded()) {
         this.ctx.broadcast(this.room, "&d已加载 SRE，可摆假方块、断桥、喷火、滚石等场景机关。");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.beginIntro();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      this.tickGadgets();
      this.actionBars();
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      if (this.boardTicks % 20 == 0 && this.phase == Phase.PLACE) {
         this.giveKits();
      }
      this.updateBoss();
      if (this.phase == Phase.COUNTDOWN) {
         this.tickCountdownTitles();
      }
      if (this.phase == Phase.RACE && this.allFinished()) {
         this.beginScore();
         return;
      }
      if (this.ticksLeft > 0) {
         return;
      }
      switch (this.phase) {
         case INTRO -> this.beginPlace();
         case PLACE -> this.beginCountdown();
         case COUNTDOWN -> this.beginRace();
         case RACE -> this.beginScore();
         case SCORE -> {
            if (this.round >= this.settings.rounds()) {
               this.finish();
            } else {
               this.round++;
               this.beginPlace();
            }
         }
         default -> {
         }
      }
   }

   public boolean handleUseItem(ServerPlayer player, ItemStack stack) {
      if (this.racer(player.getUUID()) == null) {
         return false;
      }
      if (ChickenHorseVisibility.isHideItem(stack)) {
         this.toggleHideOthers(player);
         return true;
      }
      if (this.phase == Phase.PLACE && !this.trapsChosen(player.getUUID())) {
         this.openPick(player);
         return true;
      }
      if (this.phase == Phase.PLACE && Gadget.fromStack(stack) != null) {
         this.ctx.send(player, "&7对着方块右键放置。");
         return true;
      }
      return true;
   }

   public boolean tryPlace(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null) {
         return false;
      }
      if (ChickenHorseVisibility.isHideItem(stack)) {
         this.toggleHideOthers(player);
         return true;
      }
      if (this.phase != Phase.PLACE) {
         return false;
      }
      Gadget gadget = Gadget.fromStack(stack);
      if (gadget == null) {
         if (!racer.trapChosen) {
            this.openPick(player);
            return true;
         }
         this.ctx.send(player, "&c请使用热键栏里的机关。");
         return false;
      }
      if (!racer.trapChosen && !gadget.isPath()) {
         this.openPick(player);
         return true;
      }
      if (!gadget.available() || gadget.blockState().isAir() && !gadget.isBomb()) {
         this.ctx.send(player, "&c当前未加载 SRE，无法放置该机关。");
         return false;
      }
      if (gadget.isPath()) {
         if (!racer.pathKit.contains(gadget)) {
            this.ctx.send(player, "&c没有这块铺路材料了。");
            return false;
         }
      } else if (racer.trapKit.isEmpty() || !racer.trapKit.contains(gadget)) {
         this.ctx.send(player, "&c本轮机关已用尽。");
         return false;
      }
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level == null) {
         return false;
      }
      BlockPos place = hit.getBlockPos().relative(hit.getDirection()).immutable();
      if (!this.track.canPlace(place)) {
         this.ctx.send(player, "&c不能放在出生区、终点区或赛道外。");
         return false;
      }
      if (this.track.isTemplate(place) && !this.playerPlaced.contains(place)) {
         this.ctx.send(player, "&c不能覆盖底图。");
         return false;
      }
      if (!level.getBlockState(place).canBeReplaced() && !this.playerPlaced.contains(place)) {
         this.ctx.send(player, "&c这里已经有方块了。");
         return false;
      }
      if (gadget.isBomb()) {
         this.detonate(level, place);
         racer.trapKit.remove(gadget);
      } else {
         level.setBlock(place, gadget.liveState(this.laserLit), 3);
         this.playerPlaced.add(place);
         this.gadgets.put(place, gadget);
         if (gadget.isPath()) {
            racer.pathKit.remove(gadget);
         } else {
            racer.trapKit.remove(gadget);
         }
      }
      racer.quota = racer.trapKit.size();
      if (racer.trapKit.isEmpty() && racer.pathKit.isEmpty()) {
         player.getInventory().clearContent();
         this.giveHideItem(player, racer);
         this.ctx.send(player, "&e东西用完了，等冲关吧。");
      } else {
         this.giveKit(player, racer);
      }
      return true;
   }

   public void openPick(ServerPlayer player) {
      if (this.phase == Phase.PLACE && !this.trapsChosen(player.getUUID())) {
         ChickenHorsePickGui.open(this.ctx, player);
      }
   }

   public boolean trapsChosen(UUID uuid) {
      Racer racer = this.racer(uuid);
      return racer != null && racer.trapChosen;
   }

   public List<Gadget> trapOffer(UUID uuid) {
      Racer racer = this.racer(uuid);
      return racer == null ? List.of() : racer.trapOffer;
   }

   public List<Gadget> trapPicks(UUID uuid) {
      Racer racer = this.racer(uuid);
      return racer == null ? List.of() : racer.trapPicks;
   }

   public int trapQuota(UUID uuid) {
      Racer racer = this.racer(uuid);
      return racer == null ? 1 : Math.max(1, racer.quota);
   }

   public void toggleTrapPick(ServerPlayer player, Gadget gadget) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null || racer.trapChosen || gadget == null || !racer.trapOffer.contains(gadget)) {
         return;
      }
      if (racer.trapPicks.contains(gadget)) {
         racer.trapPicks.remove(gadget);
         return;
      }
      if (racer.trapPicks.size() >= racer.quota) {
         this.ctx.send(player, "&c本轮额度只有 &f" + racer.quota + " &c个。");
         return;
      }
      racer.trapPicks.add(gadget);
   }

   public boolean confirmTrapPicks(ServerPlayer player) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null || racer.trapChosen) {
         return false;
      }
      int n = racer.trapPicks.size();
      if (n < 1 || n > racer.quota) {
         this.ctx.send(player, "&c请点选 &f1–" + racer.quota + " &c个机关。");
         return false;
      }
      racer.trapKit = new ArrayList<>(racer.trapPicks);
      racer.trapChosen = true;
      racer.quota = racer.trapKit.size();
      this.giveKit(player, racer);
      this.title(player, "&a已选 " + n + " 个", "&7对着方块右键放置");
      return true;
   }

   private void autoConfirmPicks(ServerPlayer player, Racer racer) {
      if (racer.trapChosen) {
         player.closeContainer();
         return;
      }
      if (racer.trapPicks.isEmpty()) {
         int take = Math.min(racer.quota, racer.trapOffer.size());
         racer.trapPicks.addAll(racer.trapOffer.subList(0, take));
      }
      this.confirmTrapPicks(player);
      player.closeContainer();
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      return false;
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase != Phase.RACE || racer.finishedRound || racer.outThisRound) {
         return true;
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FALL)) {
         player.fallDistance = 0.0F;
         return true;
      }
      boolean voidHit = source.is(DamageTypes.FELL_OUT_OF_WORLD);
      if (racer.iFrames > 0 && !voidHit) {
         return true;
      }
      this.eliminate(player, racer, voidHit ? "&c掉进虚空" : "&c受伤出局");
      return true;
   }

   public boolean handleDeath(ServerPlayer player) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null || this.phase == Phase.ENDED) {
         return false;
      }
      this.heal(player);
      player.invulnerableTime = 30;
      if (this.phase == Phase.RACE && !racer.finishedRound) {
         this.eliminate(player, racer, "&c出局");
         return true;
      }
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null && this.phase != Phase.SCORE && this.phase != Phase.RACE) {
         this.track.teleport(player, level, this.track.spawn(this.seats.indexOf(player.getUUID()), this.seats.size()));
      }
      return true;
   }

   private void eliminate(ServerPlayer player, Racer racer, String title) {
      if (racer.outThisRound || racer.finishedRound) {
         this.heal(player);
         return;
      }
      racer.outThisRound = true;
      racer.deaths++;
      this.heal(player);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null) {
         this.track.teleport(player, level, this.track.watch());
      }
      this.title(player, title, "&7旁观，等待下一轮");
      this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " 出局了。");
   }

   public void onLeave(UUID uuid) {
      Racer racer = this.racers.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase == Phase.ENDED) {
         return;
      }
      if (racer != null) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了鸡马。");
      }
      if (this.aliveCount() < 2) {
         this.finish();
      }
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish();
      }
   }

   private void beginIntro() {
      this.phase = Phase.INTRO;
      this.setTimer(INTRO_SECONDS * 20);
      this.forEachOnline((player, racer) -> this.title(player, "&6超级鸡马", "&e先随机摆机关，再冲关"));
   }

   private void beginPlace() {
      this.phase = Phase.PLACE;
      this.setTimer(this.settings.placeSeconds() * 20);
      this.finishOrder.clear();
      this.eggHolder = null;
      this.fakeStand.clear();
      this.placeEgg();
      int i = 0;
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null) {
         this.track.setSpawnGate(level, true);
      }
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            i++;
            continue;
         }
         racer.trapOffer = Gadget.rollRoundTraps();
         racer.trapPicks.clear();
         racer.trapKit.clear();
         racer.trapChosen = false;
         racer.quota = 1 + ThreadLocalRandom.current().nextInt(2);
         racer.pathKit = Gadget.rollPaths();
         racer.finishedRound = false;
         racer.outThisRound = false;
         racer.hasEgg = false;
         racer.checkpoint = 0;
         racer.iFrames = 0;
         racer.freezeTicks = 0;
         racer.jumpLock = 0;
         racer.airJumps = 1;
         racer.airTicks = 0;
         racer.wasJumping = false;
         racer.wallJumpReady = true;
         player.setGameMode(GameType.CREATIVE);
         player.getAbilities().mayfly = true;
         player.getAbilities().flying = true;
         player.onUpdateAbilities();
         player.closeContainer();
         this.heal(player);
         if (level != null) {
            this.track.teleport(player, level, this.track.spawn(i, this.seats.size()));
         }
         this.giveKit(player, racer);
         this.openPick(player);
         this.title(player, "&e改造", "&f额度 &e" + racer.quota + " &8· &7从 6 个里选 · 铺路 &f" + racer.pathKit.size());
         i++;
      }
      this.ctx.broadcast(this.room, "&e第 &f" + this.round + "&e 轮改造：随机 1 或 2 个额度，从 6 个机关里选择。");
   }

   private void beginCountdown() {
      this.phase = Phase.COUNTDOWN;
      this.setTimer(COUNTDOWN_TICKS);
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null) {
         this.track.setSpawnGate(level, false);
      }
      int i = 0;
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            i++;
            continue;
         }
         this.autoConfirmPicks(player, racer);
         racer.outThisRound = false;
         player.getInventory().clearContent();
         player.setGameMode(GameType.ADVENTURE);
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         player.onUpdateAbilities();
         player.removeAllEffects();
         this.heal(player);
         if (level != null) {
            this.track.teleport(player, level, this.track.spawn(i, this.seats.size()));
         }
         i++;
      }
   }

   private void beginRace() {
      this.phase = Phase.RACE;
      this.setTimer(this.settings.raceSeconds() * 20);
      this.finishOrder.clear();
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null) {
         this.track.setSpawnGate(level, false);
      }
      int i = 0;
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            i++;
            continue;
         }
         racer.finishedRound = false;
         racer.outThisRound = false;
         racer.checkpoint = 0;
         racer.iFrames = 20;
         racer.launchCool = 0;
         racer.freezeTicks = 0;
         racer.jumpLock = 0;
         racer.airJumps = 1;
         racer.airTicks = 0;
         racer.wasJumping = false;
         racer.wallJumpReady = true;
         player.getInventory().clearContent();
         player.setGameMode(GameType.ADVENTURE);
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         player.onUpdateAbilities();
         player.removeAllEffects();
         this.heal(player);
         if (level != null) {
            this.track.teleport(player, level, this.track.spawn(i, this.seats.size()));
         }
         this.title(player, "&a冲关！", this.settings.goldEgg() ? "&e侧路金蛋过线 +2" : "&7先到终点得分");
         i++;
      }
   }

   private void beginScore() {
      this.phase = Phase.SCORE;
      this.setTimer(SCORE_SECONDS * 20);
      this.applyRoundScores();
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            continue;
         }
         player.getInventory().clearContent();
         player.setGameMode(GameType.SPECTATOR);
         if (level != null) {
            this.track.teleport(player, level, this.track.watch());
         }
      }
   }

   private void applyRoundScores() {
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6第 &f" + this.round + "&6 轮结算");
      int place = 0;
      for (UUID uuid : this.finishOrder) {
         Racer racer = this.racer(uuid);
         if (racer == null) {
            continue;
         }
         int pts = place < PLACE_POINTS.length ? PLACE_POINTS[place] : 1;
         String egg = "";
         if (racer.hasEgg && this.settings.goldEgg()) {
            pts += 2;
            egg = " &6+金蛋";
         }
         racer.score += pts;
         racer.finishes++;
         this.ctx.broadcast(this.room, "&e#" + (place + 1) + " &f" + this.ctx.name(uuid) + " &a+" + pts + egg);
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.title(player, "&e第 " + (place + 1) + " 名", "&a+" + pts + " 分");
         }
         place++;
      }
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         if (racer == null || racer.finishedRound) {
            continue;
         }
         this.ctx.broadcast(this.room, (racer.outThisRound ? "&c出局 " : "&7DNF ") + "&f" + this.ctx.name(uuid) + " &80");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
   }

   private void finish() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      List<Racer> ranked = new ArrayList<>();
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         if (racer != null) {
            ranked.add(racer);
         }
      }
      ranked.sort(Comparator
         .comparingInt((Racer r) -> r.score).reversed()
         .thenComparing(Comparator.comparingInt((Racer r) -> r.finishes).reversed())
         .thenComparingInt(r -> r.deaths));
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l超级鸡马结算");
      int rank = 1;
      for (Racer racer : ranked) {
         String prefix = rank == 1 ? "&6① " : rank == 2 ? "&f② " : rank == 3 ? "&e③ " : "&7" + rank + ". ";
         this.ctx.broadcast(this.room, prefix + "&f" + this.ctx.name(racer.uuid)
            + " &e" + racer.score + "分 &8| &7完赛 " + racer.finishes + " &8| &c死 " + racer.deaths);
         ServerPlayer player = this.ctx.player(racer.uuid);
         if (player != null && rank == 1) {
            this.title(player, "&6鸡马之王", "&e" + racer.score + " 分");
         }
         rank++;
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.chickenHorse().tracks().release(this.track);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.chickenHorse().remove(this);
   }

   private void enforce() {
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level == null) {
         return;
      }
      int i = 0;
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         int index = i++;
         if (racer == null || player == null) {
            continue;
         }
         if (racer.hideSwitchCool > 0) {
            racer.hideSwitchCool--;
         }
         this.giveHideItem(player, racer);
         if (racer.iFrames > 0) {
            racer.iFrames--;
         }
         if (racer.launchCool > 0) {
            racer.launchCool--;
         }
         if (racer.jumpLock > 0) {
            racer.jumpLock--;
         }
         if (this.phase == Phase.RACE && racer.freezeTicks > 0) {
            racer.freezeTicks--;
            ParkourMoves.impulse(player, Vec3.ZERO);
            if (racer.freezeTicks <= 0) {
               this.eliminate(player, racer, "&c夹住了");
            }
            continue;
         }
         player.getFoodData().setFoodLevel(20);
         player.getFoodData().setSaturation(8.0F);
         if (this.phase == Phase.SCORE || racer.finishedRound && this.phase == Phase.RACE || racer.outThisRound && this.phase == Phase.RACE) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            continue;
         }
         if (this.phase == Phase.PLACE) {
            if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
               player.setGameMode(GameType.CREATIVE);
            }
            if (!player.getAbilities().mayfly) {
               player.getAbilities().mayfly = true;
               player.onUpdateAbilities();
            }
         } else {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
               player.setGameMode(GameType.ADVENTURE);
            }
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
               player.getAbilities().mayfly = false;
               player.getAbilities().flying = false;
               player.onUpdateAbilities();
            }
         }
         if (this.phase == Phase.COUNTDOWN || this.phase == Phase.INTRO) {
            Vec3 spawn = this.track.spawn(index, this.seats.size());
            player.setDeltaMovement(Vec3.ZERO);
            if (player.position().distanceToSqr(spawn) > 2.25) {
               this.track.teleport(player, level, spawn);
            }
            continue;
         }
         Vec3 pos = player.position();
         if (!this.track.contains(pos.x, pos.y, pos.z) || this.track.onDeathFloor(pos.x, pos.y, pos.z)) {
            if (this.phase == Phase.RACE) {
               this.eliminate(player, racer, "&c掉下去了");
            } else {
               this.track.teleport(player, level, this.track.spawn(index, this.seats.size()));
            }
            continue;
         }
         if (this.phase == Phase.RACE) {
            int reached = this.track.checkpointAt(pos.x, pos.z);
            if (reached > racer.checkpoint) {
               racer.checkpoint = reached;
               player.displayClientMessage(TextUtil.color("&b检查点 &f" + reached), true);
            }
            if (this.track.inFinish(pos.x, pos.y, pos.z) && !racer.finishedRound) {
               this.markFinished(player, racer);
               continue;
            }
            ParkourMoves.tick(player, racer, level);
         }
      }
   }

   private void tickGadgets() {
      if (this.phase != Phase.RACE) {
         return;
      }
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level == null) {
         return;
      }
      if (this.boardTicks % 25 == 0) {
         this.laserLit = !this.laserLit;
         this.refreshLasers(level);
      }
      if (this.boardTicks % 3 == 0) {
         this.sparkGadgets(level);
      }
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null || racer.finishedRound || racer.outThisRound) {
            continue;
         }
         if (this.eggPos != null && this.eggHolder == null && player.position().distanceToSqr(Vec3.atCenterOf(this.eggPos)) < 1.6) {
            this.eggHolder = uuid;
            racer.hasEgg = true;
            level.setBlock(this.eggPos, Blocks.AIR.defaultBlockState(), 3);
            this.ctx.broadcast(this.room, "&6" + player.getGameProfile().getName() + " 抢走了金蛋！过线才算分。");
         }
         this.tickLaserBeam(level, player, racer);
         this.tickMagnets(player);
         this.tickRollers(level, player, racer);
         BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ()).immutable();
         BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.05, player.getZ()).immutable();
         Gadget gadget = this.gadgets.get(below);
         BlockPos at = below;
         if (gadget == null) {
            gadget = this.gadgets.get(feet);
            at = feet;
         }
         if (gadget != null) {
            this.applyGadget(level, player, racer, gadget, at);
         }
      }
   }

   private void applyGadget(ServerLevel level, ServerPlayer player, Racer racer, Gadget gadget, BlockPos at) {
      Vec3 mot = player.getDeltaMovement();
      switch (gadget) {
         case FAKE -> {
            int stood = this.fakeStand.merge(at, 1, Integer::sum);
            if (stood >= 4) {
               level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gadget.blockState()),
                  at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 18, 0.3, 0.2, 0.3, 0.08);
               level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
               level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
               this.gadgets.remove(at);
               this.playerPlaced.remove(at);
               this.fakeStand.remove(at);
            }
         }
         case LAUNCH -> this.burstPad(level, player, racer, new Vec3(1.05, 0.88, 0.0), 12);
         case CANNON -> this.burstPad(level, player, racer, new Vec3(1.55, 1.05, 0.0), 16);
         case BOOST -> this.burstPad(level, player, racer, new Vec3(1.35, 0.28, mot.z), 10);
         case SUPERBOUNCE -> this.burstPad(level, player, racer, new Vec3(mot.x, 1.28, mot.z), 12);
         case LIFTER -> this.burstPad(level, player, racer, new Vec3(mot.x, 1.12, mot.z), 12);
         case WARP -> {
            if (racer.launchCool > 0) {
               return;
            }
            racer.launchCool = 20;
            player.teleportTo(player.getX() + 6.0, player.getY(), player.getZ());
            ParkourMoves.impulse(player, new Vec3(0.55, 0.22, 0.0));
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.6, player.getZ(), 24, 0.3, 0.4, 0.3, 0.08);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
         }
         case WIND -> ParkourMoves.impulse(player, new Vec3(mot.x - 0.42, Math.max(mot.y, 0.08), mot.z));
         case CURRENT -> ParkourMoves.impulse(player, new Vec3(mot.x - 0.40, mot.y, mot.z));
         case CONVEYOR -> ParkourMoves.impulse(player, new Vec3(mot.x + 0.34, mot.y, mot.z));
         case GRAVITY -> ParkourMoves.impulse(player, new Vec3(mot.x * 0.7, 0.62, mot.z * 0.7));
         case SINKHOLE -> ParkourMoves.impulse(player, new Vec3(mot.x, -0.95, mot.z));
         case REVERSE -> {
            if (racer.launchCool <= 0) {
               racer.launchCool = 10;
               ParkourMoves.impulse(player, new Vec3(-Math.abs(mot.x) - 0.55, mot.y, mot.z));
            }
         }
         case SIDEWIND -> {
            double mid = this.track.origin().getZ() + this.track.sizeZ() / 2.0;
            double pull = player.getZ() < mid ? -0.42 : 0.42;
            ParkourMoves.impulse(player, new Vec3(mot.x, mot.y, mot.z + pull));
         }
         case SPIKE, SAW, WITHER -> {
            if (racer.iFrames <= 0) {
               player.hurt(player.damageSources().magic(), 8.0F);
            }
         }
         case BEARTRAP -> {
            if (racer.freezeTicks <= 0 && racer.iFrames <= 0) {
               racer.freezeTicks = 16;
               player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9, true, false, false));
               level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
         }
         case BLIND -> player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false));
         case LEVITATE -> player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 1, true, false, false));
         case FLASH -> player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 50, 0, true, false, false));
         case FROST -> {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, true, false, false));
            player.setTicksFrozen(Math.min(180, player.getTicksFrozen() + 12));
         }
         case JUMPLOCK -> racer.jumpLock = Math.max(racer.jumpLock, 35);
         default -> {
         }
      }
   }

   private void burstPad(ServerLevel level, ServerPlayer player, Racer racer, Vec3 motion, int cool) {
      if (racer.launchCool > 0) {
         return;
      }
      racer.launchCool = cool;
      ParkourMoves.impulse(player, motion);
      level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(), 14, 0.2, 0.2, 0.2, 0.08);
      level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.65F, 1.15F);
   }

   private void tickLaserBeam(ServerLevel level, ServerPlayer player, Racer racer) {
      if (!this.laserLit || racer.iFrames > 0) {
         return;
      }
      for (var entry : this.gadgets.entrySet()) {
         if (entry.getValue() != Gadget.LASER) {
            continue;
         }
         BlockPos pos = entry.getKey();
         AABB beam = new AABB(pos.getX() + 0.15, pos.getY() + 0.35, pos.getZ() + 0.2,
            pos.getX() + 6.8, pos.getY() + 1.75, pos.getZ() + 0.8);
         if (beam.intersects(player.getBoundingBox())) {
            player.hurt(player.damageSources().magic(), 8.0F);
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 6, 0.15, 0.2, 0.15, 0.01);
            return;
         }
      }
   }

   private void tickMagnets(ServerPlayer player) {
      Vec3 pos = player.position();
      double rangeSq = MAGNET_RANGE * MAGNET_RANGE;
      boolean near = false;
      for (var entry : this.gadgets.entrySet()) {
         if (entry.getValue() != Gadget.MAGNET) {
            continue;
         }
         BlockPos at = entry.getKey();
         double dx = at.getX() + 0.5 - pos.x;
         double dy = at.getY() + 0.5 - pos.y;
         double dz = at.getZ() + 0.5 - pos.z;
         if (dx * dx + dy * dy + dz * dz <= rangeSq) {
            near = true;
            break;
         }
      }
      if (!near) {
         return;
      }
      double mid = this.track.origin().getZ() + this.track.sizeZ() / 2.0;
      double pull = player.getZ() < mid ? -0.28 : 0.28;
      Vec3 mot = player.getDeltaMovement();
      ParkourMoves.impulse(player, new Vec3(mot.x, mot.y, mot.z + pull));
   }

   private void tickRollers(ServerLevel level, ServerPlayer player, Racer racer) {
      if (racer.iFrames > 0) {
         return;
      }
      AABB box = player.getBoundingBox().inflate(0.45);
      if (!level.getEntities(player, box, ChickenHorseMatch::isRoller).isEmpty()) {
         player.hurt(player.damageSources().magic(), 8.0F);
         level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.2, 0.25, 0.2, 0.05);
      }
   }

   private static boolean isRoller(Entity entity) {
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      return id != null && "noellesroles".equals(id.getNamespace())
         && ("rolling_stone".equals(id.getPath()) || "rolling_log".equals(id.getPath()));
   }

   private void refreshLasers(ServerLevel level) {
      for (var entry : this.gadgets.entrySet()) {
         if (entry.getValue() == Gadget.LASER) {
            level.setBlock(entry.getKey(), Gadget.LASER.liveState(this.laserLit), 2);
         }
      }
   }

   private void sparkGadgets(ServerLevel level) {
      for (var entry : this.gadgets.entrySet()) {
         BlockPos pos = entry.getKey();
         Gadget gadget = entry.getValue();
         double x = pos.getX() + 0.5;
         double y = pos.getY() + 1.05;
         double z = pos.getZ() + 0.5;
         if (gadget.lethal()) {
            level.sendParticles(KILL_DUST, x, y, z, 5, 0.32, 0.14, 0.32, 0.0);
            if (gadget == Gadget.LASER && this.laserLit) {
               for (int i = 1; i <= 6; i++) {
                  level.sendParticles(KILL_DUST, x + i, pos.getY() + 0.85, z, 2, 0.14, 0.08, 0.14, 0.0);
               }
            }
            continue;
         }
         if (gadget == Gadget.WIND || gadget == Gadget.CURRENT) {
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.35, 0.25, 0.35, 0.06);
            level.sendParticles(ParticleTypes.GUST, x - 0.4, y + 0.2, z, 1, 0.1, 0.05, 0.1, 0.0);
         } else if (gadget == Gadget.LAUNCH || gadget == Gadget.CANNON || gadget == Gadget.BOOST) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 2, 0.2, 0.1, 0.2, 0.0);
         } else if (gadget == Gadget.CONVEYOR) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.25, 0.05, 0.25, 0.0);
         } else if (gadget == Gadget.MAGNET) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 6, 0.55, 0.35, 0.55, 0.0);
         } else if (gadget == Gadget.WARP) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 2, 0.2, 0.15, 0.2, 0.0);
         }
      }
   }

   private void detonate(ServerLevel level, BlockPos center) {
      level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.1F, 1.0F);
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
      level.sendParticles(ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 24, 0.8, 0.6, 0.8, 0.05);
      for (BlockPos pos : List.copyOf(this.playerPlaced)) {
         if (chebyshev(pos, center) > 2) {
            continue;
         }
         if (this.track.isTemplate(pos) && !this.playerPlaced.contains(pos)) {
            continue;
         }
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         this.playerPlaced.remove(pos);
         this.gadgets.remove(pos);
         this.fakeStand.remove(pos);
      }
   }

   private static int chebyshev(BlockPos a, BlockPos b) {
      return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
   }

   private void markFinished(ServerPlayer player, Racer racer) {
      racer.finishedRound = true;
      this.finishOrder.add(player.getUUID());
      player.getInventory().clearContent();
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level != null) {
         this.track.teleport(player, level, this.track.watch());
      }
      int place = this.finishOrder.size();
      this.ctx.broadcast(this.room, "&a" + player.getGameProfile().getName() + " 完赛！&7第 " + place + " 名");
      this.title(player, "&a完赛", "&e第 " + place + " 名");
   }

   private void placeEgg() {
      ServerLevel level = this.ctx.chickenHorse().tracks().level();
      if (level == null || !this.settings.goldEgg()) {
         this.eggPos = null;
         return;
      }
      List<BlockPos> spots = new ArrayList<>(this.track.eggSpots());
      BlockPos pick = spots.get(ThreadLocalRandom.current().nextInt(spots.size()));
      if (!level.getBlockState(pick).isAir() && !this.playerPlaced.contains(pick)) {
         for (BlockPos spot : spots) {
            if (level.getBlockState(spot).isAir() || this.playerPlaced.contains(spot)) {
               pick = spot;
               break;
            }
         }
      }
      this.eggPos = pick.immutable();
      this.playerPlaced.remove(this.eggPos);
      this.gadgets.remove(this.eggPos);
      level.setBlock(this.eggPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
   }

   private void giveKits() {
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null || this.phase != Phase.PLACE) {
            continue;
         }
         if (player.containerMenu != player.inventoryMenu) {
            continue;
         }
         if (!racer.trapChosen) {
            this.openPick(player);
            continue;
         }
         if (!racer.trapKit.isEmpty() || !racer.pathKit.isEmpty()) {
            this.giveKit(player, racer);
         }
      }
   }

   private void giveKit(ServerPlayer player, Racer racer) {
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         inv.setItem(i, ItemStack.EMPTY);
      }
      int slot = 0;
      for (Gadget gadget : racer.trapKit) {
         if (slot >= 8) {
            break;
         }
         inv.setItem(slot++, gadget.stack());
      }
      java.util.LinkedHashMap<Gadget, Integer> paths = new java.util.LinkedHashMap<>();
      for (Gadget gadget : racer.pathKit) {
         paths.merge(gadget, 1, Integer::sum);
      }
      for (var entry : paths.entrySet()) {
         if (slot >= 8) {
            break;
         }
         inv.setItem(slot++, entry.getKey().stack(entry.getValue()));
      }
      this.giveHideItem(player, racer);
      player.containerMenu.broadcastChanges();
   }

   private void giveHideItem(ServerPlayer player, Racer racer) {
      ItemStack want = ChickenHorseVisibility.item(racer.hideOthers);
      ItemStack have = player.getInventory().getItem(8);
      if (ChickenHorseVisibility.isHideItem(have)
         && racer.hideOthers == "1".equals(GuiItems.extraTag(have, "hide"))) {
         return;
      }
      player.getInventory().setItem(8, want);
   }

   private void toggleHideOthers(ServerPlayer player) {
      Racer racer = this.racer(player.getUUID());
      if (racer == null || racer.hideSwitchCool > 0) {
         return;
      }
      racer.hideSwitchCool = 8;
      racer.hideOthers = !racer.hideOthers;
      this.giveHideItem(player, racer);
      ChickenHorseVisibility.sync(player, racer.hideOthers, this.seatedPlayers());
      this.ctx.send(player, racer.hideOthers ? "&c已隐藏其他玩家" : "&a已显示其他玩家");
      player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS,
         0.45F, racer.hideOthers ? 0.7F : 1.25F);
   }

   public boolean hidesOthers(UUID uuid) {
      Racer racer = this.racer(uuid);
      return racer != null && racer.hideOthers;
   }

   private List<ServerPlayer> seatedPlayers() {
      List<ServerPlayer> list = new ArrayList<>();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            list.add(player);
         }
      }
      return list;
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.setRemainingFireTicks(0);
      player.fallDistance = 0.0F;
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(8.0F);
      player.removeAllEffects();
   }

   private boolean allFinished() {
      int live = 0;
      int done = 0;
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         if (racer == null || this.ctx.player(uuid) == null) {
            continue;
         }
         live++;
         if (racer.finishedRound || racer.outThisRound) {
            done++;
         }
      }
      return live > 0 && done >= live;
   }

   private int aliveCount() {
      int n = 0;
      for (UUID uuid : this.seats) {
         if (this.racer(uuid) != null) {
            n++;
         }
      }
      return n;
   }

   private void setTimer(int ticks) {
      this.phaseMaxTicks = Math.max(1, ticks);
      this.ticksLeft = this.phaseMaxTicks;
   }

   private void tickCountdownTitles() {
      int sec = (this.ticksLeft + 19) / 20;
      if (this.ticksLeft == 40 || this.ticksLeft == 20 || this.ticksLeft == 1) {
         String title = this.ticksLeft == 1 ? "&aGO" : "&e" + sec;
         this.forEachOnline((player, racer) -> this.title(player, title, "&7准备冲关"));
      }
   }

   private void actionBars() {
      int sec = Math.max(0, (this.ticksLeft + 19) / 20);
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            continue;
         }
         String msg = switch (this.phase) {
            case INTRO -> "&6超级鸡马 &8| &7即将开始 " + sec + "s";
            case PLACE -> racer.trapChosen
               ? "&e改造 &f" + sec + "s &8| &7机关 &e" + racer.trapKit.size()
                  + " &8| &7铺路 &f" + racer.pathKit.size()
                  + " &8| &7第 &f" + this.round + "/" + this.settings.rounds() + " &7轮"
               : "&e改造 &f" + sec + "s &8| &c从 6 个里选 &f" + racer.trapPicks.size() + "/" + racer.quota
                  + " &8| &7铺路 &f" + racer.pathKit.size();
            case COUNTDOWN -> "&e倒计时 &f" + sec;
            case RACE -> racer.finishedRound
               ? "&a完赛旁观 &8| &7剩余 &f" + this.remainingRacers()
               : racer.outThisRound
                  ? "&c出局旁观 &8| &7等待下一轮"
                  : "&a冲关 &f" + sec + "s &8| &7检查点 &b" + racer.checkpoint
                     + " &8| &7完赛 &f" + this.finishOrder.size() + "/" + this.aliveCount()
                     + (racer.hasEgg ? " &6金蛋" : "");
            case SCORE -> "&6结算 &f" + sec + "s &8| &7总分 &e" + racer.score;
            case ENDED -> "";
         };
         if (!msg.isEmpty()) {
            player.displayClientMessage(TextUtil.color(msg), true);
         }
      }
   }

   private void pushBoard() {
      int sec = Math.max(0, (this.ticksLeft + 19) / 20);
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer == null || player == null) {
            continue;
         }
         List<String> lines = new ArrayList<>();
         lines.add("&6超级鸡马");
         lines.add("&7第 &f" + this.round + "/" + this.settings.rounds() + " &7轮");
         lines.add("&7&m---------------");
         lines.add(switch (this.phase) {
            case INTRO -> "&e准备中";
            case PLACE -> "&e改造 &f" + sec + "s";
            case COUNTDOWN -> "&e倒计时";
            case RACE -> "&a冲关 &f" + sec + "s";
            case SCORE -> "&6结算";
            case ENDED -> "&7结束";
         });
         if (this.phase == Phase.PLACE) {
            lines.add("&7机关 &e" + racer.trapKit.size());
            lines.add("&7铺路 &f" + racer.pathKit.size());
         }
         if (this.phase == Phase.RACE) {
            lines.add("&7检查点 &b" + racer.checkpoint);
            lines.add("&7完赛 &f" + this.finishOrder.size());
            if (racer.outThisRound) {
               lines.add("&c已出局");
            } else {
               lines.add("&7潜行攀墙可翻顶");
            }
         }
         lines.add("&7总分 &e" + racer.score);
         lines.add("&c死亡 &f" + racer.deaths);
         lines.add("&7&m---------------");
         this.board.update(player, lines);
      }
   }

   private void updateBoss() {
      float progress = this.ticksLeft / (float) this.phaseMaxTicks;
      this.boss.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
      String label = switch (this.phase) {
         case INTRO -> "&6超级鸡马";
         case PLACE -> "&e改造 第" + this.round + "轮";
         case COUNTDOWN -> "&e倒计时";
         case RACE -> "&a冲关 第" + this.round + "轮";
         case SCORE -> "&6结算";
         case ENDED -> "&7结束";
      };
      this.boss.setName(TextUtil.color(label));
   }

   private int remainingRacers() {
      int n = 0;
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         if (racer != null && this.ctx.player(uuid) != null && !racer.finishedRound && !racer.outThisRound) {
            n++;
         }
      }
      return n;
   }

   private void title(ServerPlayer player, String title, String subtitle) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }

   private void forEachOnline(PlayerRacer consumer) {
      for (UUID uuid : this.seats) {
         Racer racer = this.racer(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (racer != null && player != null) {
            consumer.accept(player, racer);
         }
      }
   }

   private Racer racer(UUID uuid) {
      return this.racers.get(uuid);
   }

   private void ensureTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayerTeam(TEAM_NAME);
      if (team == null) {
         team = board.addPlayerTeam(TEAM_NAME);
      }
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setNameTagVisibility(Team.Visibility.ALWAYS);
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void restore(ServerPlayer player) {
      Racer racer = this.racer(player.getUUID());
      if (racer != null) {
         racer.hideOthers = false;
      }
      ChickenHorseVisibility.sync(player, false, this.seatedPlayers());
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
      Saved state = this.saved.get(player.getUUID());
      if (state != null) {
         state.apply(player, this.ctx);
      } else {
         player.setGameMode(GameType.ADVENTURE);
         this.ctx.rooms().resetLobbyState(player);
      }
   }

   @FunctionalInterface
   private interface PlayerRacer {
      void accept(ServerPlayer player, Racer racer);
   }

   static final class Racer {
      final UUID uuid;
      int score;
      int finishes;
      int deaths;
      int checkpoint;
      int quota;
      boolean finishedRound;
      boolean outThisRound;
      boolean hasEgg;
      int iFrames;
      int launchCool;
      int freezeTicks;
      int jumpLock;
      int airJumps = 1;
      int airTicks;
      boolean wasJumping;
      boolean wallJumpReady = true;
      boolean hideOthers;
      int hideSwitchCool;
      boolean trapChosen;
      List<Gadget> trapOffer = new ArrayList<>();
      List<Gadget> trapPicks = new ArrayList<>();
      List<Gadget> trapKit = new ArrayList<>();
      List<Gadget> pathKit = new ArrayList<>();

      Racer(UUID uuid) {
         this.uuid = uuid;
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
