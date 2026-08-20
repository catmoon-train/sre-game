package net.exmo.sreGame.games.luckypillar;

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
import net.exmo.sreGame.games.buildwar.BuildSafety;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class LuckyPillarMatch {
   public enum Phase {
      INTRO,
      FIGHT,
      ENDED
   }

   private static final ChatFormatting[] TEAM_COLORS = {
      ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
      ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE
   };

   private static final int INTRO_SECONDS = 5;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final PillarArena arena;
   private final LuckyPillarSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Set<BlockPos> pillars = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> luckyBlocks = ConcurrentHashMap.newKeySet();
   private final List<BlockPos> pillarBases;
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int boardTicks;
   private int refreshTicks;
   private int eventTicks;
   private int shrinkWaitTicks;
   private int shrinkAccum;
   private int inset;
   private boolean begun;
   private boolean shrinking;

   public LuckyPillarMatch(GameContext ctx, GameRoom room, List<UUID> seats, PillarArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.luckyPillarSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6幸运之柱"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      List<UUID> ordered = this.assignTeams(this.seats);
      this.pillarBases = arena.pillarBases(this.pillarCount(), this.settings.borderSize());
      int ffaIndex = 0;
      for (UUID uuid : ordered) {
         Fighter fighter = this.fighters.get(uuid);
         if (fighter == null) {
            continue;
         }
         if (this.settings.teams() && fighter.team > 0) {
            fighter.pillarIndex = Math.min(fighter.team - 1, this.pillarBases.size() - 1);
         } else {
            fighter.pillarIndex = Math.min(ffaIndex, this.pillarBases.size() - 1);
            ffaIndex++;
         }
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

   public Phase phase() {
      return this.phase;
   }

   public ServerLevel level() {
      return this.ctx.luckyPillar().arenas().level();
   }

   public PillarArena.Layout layout() {
      return new PillarArena.Layout(
         this.settings.borderSize(),
         this.settings.pillarHeight(),
         this.settings.fishingMode() ? Blocks.WATER.defaultBlockState() : this.settings.floor().state(),
         this.settings.pillar().state(),
         this.settings.fishingMode(),
         this.pillarBases.size()
      );
   }

   public void rememberPillars() {
      int height = this.settings.pillarHeight();
      for (BlockPos base : this.pillarBases) {
         for (int y = 1; y <= height; y++) {
            this.pillars.add(base.offset(0, y, 0));
         }
      }
   }

   public void start() {
      this.begun = true;
      this.rememberPillars();
      ServerLevel level = this.level();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&6幸运之柱");
         this.boss.addPlayer(player);
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.ensureTeam(player, fighter);
         this.applyIntroProtection(player);
         if (this.settings.fishingMode()) {
            player.getInventory().add(this.fishingRod(player));
         }
         BlockPos base = this.pillarBases.get(fighter.pillarIndex);
         if (level != null) {
            this.arena.teleport(player, level, this.arena.spawnOn(base, this.settings.pillarHeight()));
         }
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l幸运之柱");
      this.ctx.broadcast(this.room, "&7每隔 &f" + this.settings.refreshSeconds() + "s &7获得 &f"
         + this.settings.refreshCount() + " &7个" + (this.settings.luckyBlockMode() ? "幸运方块" : "随机物品") + "。");
      if (this.settings.teams()) {
         this.ctx.broadcast(this.room, "&7组队 &f" + this.settings.teamSize() + "人一组 &7· 友伤关闭");
      }
      if (this.settings.fishingMode()) {
         this.ctx.broadcast(this.room, "&b钓鱼模式：踩水中毒，用饵钓" + this.settings.lureLevel() + " 钓竿钓随机物品。");
      }
      if (this.settings.border()) {
         this.ctx.broadcast(this.room, "&c边界 " + this.settings.borderSize() + " · "
            + this.settings.shrinkDelaySeconds() + "s 后以 " + this.settings.shrinkSpeedLabel() + " 挤压");
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
      if (this.phase == Phase.FIGHT) {
         this.tickFight();
      }
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      this.updateBoss();
      if (this.ticksLeft > 0) {
         return;
      }
      if (this.phase == Phase.INTRO) {
         this.beginFight();
      }
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || !fighter.alive) {
         return InteractionResult.FAIL;
      }
      if (!player.isShiftKeyDown()
         && BuildSafety.isWorkstation(player.level().getBlockState(hit.getBlockPos()).getBlock())) {
         return InteractionResult.PASS;
      }
      BlockPos place = hit.getBlockPos().relative(hit.getDirection());
      if (!this.arena.canBuild(place, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)
         || this.pillars.contains(place)
         || this.arena.isFloor(place)
         || this.arena.isBasin(place)
         || this.arena.isCurrentWall(place, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)) {
         return InteractionResult.FAIL;
      }
      if (this.ctx.luckyPillar().items().isLuckyBlock(stack)) {
         this.luckyBlocks.add(place.immutable());
      }
      return InteractionResult.PASS;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || !fighter.alive) {
         return false;
      }
      if (this.pillars.contains(pos)
         || this.arena.isFloor(pos)
         || this.arena.isBasin(pos)
         || this.arena.isCurrentWall(pos, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)
         || !this.arena.canBuild(pos, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)
            && !this.luckyBlocks.contains(pos)) {
         return false;
      }
      if (this.luckyBlocks.remove(pos)) {
         ServerLevel level = this.level();
         if (level != null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         }
         LuckyEvents.roll(this, player, pos);
         return false;
      }
      return true;
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.INTRO || !fighter.alive) {
         return true;
      }
      if (source.getEntity() instanceof ServerPlayer attacker) {
         Fighter other = this.fighter(attacker.getUUID());
         if (other != null && this.settings.teams() && other.team != 0 && other.team == fighter.team) {
            return true;
         }
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.INTRO || !fighter.alive) {
         this.heal(player);
         this.applyIntroProtection(player);
         return true;
      }
      if (this.tryTotem(player, source)) {
         return true;
      }
      this.heal(player);
      if (this.phase == Phase.FIGHT) {
         this.eliminate(player, fighter, "&c阵亡");
      }
      return true;
   }

   public boolean handleFishingCatch(ServerPlayer player) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || !fighter.alive || this.phase != Phase.FIGHT || !this.settings.fishingMode()) {
         return false;
      }
      this.giveTo(player, this.settings.refreshCount(), false);
      this.ctx.send(player, "&b钓到了 " + this.settings.refreshCount() + " 件随机物品！");
      return true;
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase == Phase.ENDED) {
         return;
      }
      if (fighter != null) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了幸运之柱。");
      }
      this.checkWin();
   }

   public void endNow() {
      if (this.phase != Phase.ENDED) {
         this.finish(null);
      }
   }

   public List<ServerPlayer> alivePlayers() {
      List<ServerPlayer> out = new ArrayList<>();
      for (Fighter fighter : this.fighters.values()) {
         if (!fighter.alive) {
            continue;
         }
         ServerPlayer player = this.ctx.player(fighter.uuid);
         if (player != null) {
            out.add(player);
         }
      }
      return out;
   }

   public void giveRefresh(boolean lucky) {
      for (ServerPlayer player : this.alivePlayers()) {
         this.giveTo(player, this.settings.refreshCount(), lucky);
      }
   }

   public void warpRandom(ServerPlayer player) {
      if (this.pillarBases.isEmpty()) {
         return;
      }
      BlockPos base = this.pillarBases.get(ThreadLocalRandom.current().nextInt(this.pillarBases.size()));
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.spawnOn(base, this.settings.pillarHeight()));
      }
   }

   public void warpOwnPillar(ServerPlayer player) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.pillarBases.isEmpty()) {
         return;
      }
      BlockPos base = this.pillarBases.get(Math.min(fighter.pillarIndex, this.pillarBases.size() - 1));
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.spawnOn(base, this.settings.pillarHeight()));
      }
   }

   public boolean canReplace(BlockPos pos) {
      return this.arena.canBuild(pos, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)
         && !this.pillars.contains(pos);
   }

   private void beginIntro() {
      this.phase = Phase.INTRO;
      this.setTimer(INTRO_SECONDS * 20);
      this.forEachOnline((player, fighter) -> {
         this.applyIntroProtection(player);
         this.title(player, "&6幸运之柱", "&e无敌准备中");
      });
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.refreshTicks = this.settings.refreshSeconds() * 20;
      this.eventTicks = RandomEvents.INTERVAL_TICKS;
      this.shrinkWaitTicks = this.settings.border() ? this.settings.shrinkDelaySeconds() * 20 : Integer.MAX_VALUE;
      this.setTimer(Integer.MAX_VALUE);
      this.forEachOnline((player, fighter) -> {
         player.removeEffect(MobEffects.SLOW_FALLING);
         this.title(player, "&c开战", "&7活到最后");
      });
      this.ctx.broadcast(this.room, "&c战斗开始！");
   }

   private void tickFight() {
      this.refreshTicks--;
      if (this.refreshTicks <= 0) {
         this.refreshTicks = this.settings.refreshSeconds() * 20;
         this.giveRefresh(this.settings.luckyBlockMode());
         this.ctx.broadcast(this.room, this.settings.luckyBlockMode() ? "&6幸运方块已发放。" : "&e随机物品已发放。");
      }
      if (this.settings.randomEvents()) {
         this.eventTicks--;
         if (this.eventTicks <= 0) {
            this.eventTicks = RandomEvents.INTERVAL_TICKS;
            RandomEvents.roll(this);
         }
      }
      if (this.settings.border()) {
         if (!this.shrinking) {
            this.shrinkWaitTicks--;
            if (this.shrinkWaitTicks <= 0) {
               this.shrinking = true;
               this.ctx.broadcast(this.room, "&c边界开始挤压！");
            }
         } else {
            this.shrinkAccum++;
            if (this.shrinkAccum >= this.settings.ticksPerShrinkBlock()) {
               this.shrinkAccum = 0;
               this.shrinkOnce();
            }
         }
      }
   }

   private void shrinkOnce() {
      int live = this.settings.borderSize() - this.inset * 2;
      if (live <= PillarArena.MIN_LIVE_SIZE) {
         return;
      }
      this.inset++;
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int x0 = this.arena.minX(this.settings.borderSize()) + this.inset;
      int z0 = this.arena.minZ(this.settings.borderSize()) + this.inset;
      int x1 = this.arena.maxX(this.settings.borderSize()) - this.inset;
      int z1 = this.arena.maxZ(this.settings.borderSize()) - this.inset;
      int minY = this.arena.basinY();
      int maxY = this.arena.wallTop(this.settings.pillarHeight());
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int y = minY; y <= maxY; y++) {
         for (int x = x0; x <= x1; x++) {
            level.setBlock(pos.set(x, y, z0), PillarArena.WALL, 2);
            level.setBlock(pos.set(x, y, z1), PillarArena.WALL, 2);
         }
         for (int z = z0; z <= z1; z++) {
            level.setBlock(pos.set(x0, y, z), PillarArena.WALL, 2);
            level.setBlock(pos.set(x1, y, z), PillarArena.WALL, 2);
         }
      }
      for (ServerPlayer player : this.alivePlayers()) {
         BlockPos feet = player.blockPosition();
         if (this.arena.isCurrentWall(feet, this.settings.borderSize(), this.settings.pillarHeight(), this.inset)
            || this.arena.isCurrentWall(feet.above(), this.settings.borderSize(), this.settings.pillarHeight(), this.inset)) {
            Vec3 snap = this.arena.snapInside(player.getX(), player.getY(), player.getZ(),
               this.settings.borderSize(), this.settings.pillarHeight(), this.inset);
            this.arena.teleport(player, level, snap);
         }
      }
   }

   private void enforce() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null) {
            continue;
         }
         if (!fighter.alive) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            continue;
         }
         if (this.phase == Phase.FIGHT && player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
         }
         if (this.phase == Phase.INTRO && player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
         }
         if (this.phase == Phase.INTRO) {
            this.applyIntroProtection(player);
            double minStand = this.arena.floorY() + this.settings.pillarHeight() - 0.2;
            if (player.getY() < minStand) {
               this.arena.teleport(player, level, this.arena.spawnOn(
                  this.pillarBases.get(Math.min(fighter.pillarIndex, this.pillarBases.size() - 1)),
                  this.settings.pillarHeight()));
            }
         }
         boolean inside = player.serverLevel() == level && this.arena.contains(
            player.getX(), player.getY(), player.getZ(),
            this.settings.borderSize(), this.settings.pillarHeight(), this.inset);
         if (!inside) {
            if (this.phase == Phase.FIGHT) {
               this.eliminate(player, fighter, "&c越界出局");
               continue;
            }
            this.arena.teleport(player, level, this.arena.spawnOn(
               this.pillarBases.get(Math.min(fighter.pillarIndex, this.pillarBases.size() - 1)),
               this.settings.pillarHeight()));
         }
         if (this.settings.fishingMode() && this.phase == Phase.FIGHT && fighter.alive && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0, true, false, false));
         }
         if (this.phase == Phase.FIGHT && player.getY() < this.arena.basinY() - 1) {
            this.eliminate(player, fighter, "&c掉入虚空");
         }
      }
   }

   private void eliminate(ServerPlayer player, Fighter fighter, String title) {
      if (!fighter.alive) {
         this.heal(player);
         return;
      }
      fighter.alive = false;
      this.dropAll(player);
      this.heal(player);
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.watch(this.settings.pillarHeight()));
      }
      this.title(player, title, "&7旁观至对局结束");
      this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " 出局了。");
      this.checkWin();
   }

   private void checkWin() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      List<Fighter> alive = this.aliveFighters();
      if (this.settings.teams()) {
         int teamsLeft = (int) alive.stream().mapToInt(f -> f.team).distinct().count();
         if (teamsLeft <= 1) {
            this.finish(alive.isEmpty() ? null : alive.get(0));
         }
         return;
      }
      if (alive.size() <= 1) {
         this.finish(alive.isEmpty() ? null : alive.get(0));
      }
   }

   private void finish(Fighter winner) {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l幸运之柱结算");
      if (winner != null) {
         String name = this.ctx.name(winner.uuid);
         if (this.settings.teams() && winner.team > 0) {
            this.ctx.broadcast(this.room, "&a获胜队伍 &e#" + winner.team + " &7（" + name + " 等）");
         } else {
            this.ctx.broadcast(this.room, "&a胜者： &f" + name);
         }
         ServerPlayer player = this.ctx.player(winner.uuid);
         if (player != null) {
            this.title(player, "&6胜利", "&e幸运之柱");
         }
      } else {
         this.ctx.broadcast(this.room, "&7没有幸存者。");
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
      this.ctx.luckyPillar().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.luckyPillar().remove(this);
   }

   private void giveTo(ServerPlayer player, int count, boolean lucky) {
      LuckyItemPool pool = this.ctx.luckyPillar().items();
      for (int i = 0; i < count; i++) {
         ItemStack stack = lucky
            ? pool.luckyBlock()
            : pool.roll(player.registryAccess(), ThreadLocalRandom.current());
         LuckyEvents.give(player, stack);
      }
   }

   private ItemStack fishingRod(ServerPlayer player) {
      ItemStack rod = new ItemStack(Items.FISHING_ROD);
      rod.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      Holder<Enchantment> lure = player.registryAccess()
         .registryOrThrow(Registries.ENCHANTMENT)
         .getHolderOrThrow(Enchantments.LURE);
      rod.enchant(lure, this.settings.lureLevel());
      rod.set(DataComponents.CUSTOM_NAME, TextUtil.color("&b幸运钓竿"));
      return rod;
   }

   private int pillarCount() {
      if (!this.settings.teams()) {
         return Math.max(1, this.seats.size());
      }
      int maxTeam = 0;
      for (Fighter fighter : this.fighters.values()) {
         maxTeam = Math.max(maxTeam, fighter.team);
      }
      return Math.max(1, maxTeam);
   }

   private void applyIntroProtection(ServerPlayer player) {
      player.fallDistance = 0.0F;
      player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 255, false, false, false));
   }

   private boolean tryTotem(ServerPlayer player, DamageSource source) {
      if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return false;
      }
      ItemStack used = null;
      for (InteractionHand hand : InteractionHand.values()) {
         ItemStack stack = player.getItemInHand(hand);
         if (stack.is(Items.TOTEM_OF_UNDYING)) {
            used = stack.copy();
            stack.shrink(1);
            break;
         }
      }
      if (used == null) {
         return false;
      }
      player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
      CriteriaTriggers.USED_TOTEM.trigger(player, used);
      player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
      player.setHealth(1.0F);
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
      player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
      player.level().broadcastEntityEvent(player, (byte) 35);
      return true;
   }

   private void ensureTeam(ServerPlayer player, Fighter fighter) {
      if (!this.settings.teams() || fighter.team <= 0) {
         return;
      }
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = "srlp" + fighter.team;
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
      }
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setAllowFriendlyFire(false);
      team.setNameTagVisibility(Team.Visibility.ALWAYS);
      int color = fighter.team - 1;
      if (color >= 0 && color < TEAM_COLORS.length) {
         team.setColor(TEAM_COLORS[color]);
      }
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void clearTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam current = board.getPlayersTeam(player.getScoreboardName());
      if (current != null && current.getName().startsWith("srlp")) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
   }

   private List<UUID> assignTeams(List<UUID> members) {
      List<UUID> shuffled = new ArrayList<>(members);
      CollectionsShuffle(shuffled);
      int team = 1;
      int inTeam = 0;
      for (UUID uuid : shuffled) {
         Fighter fighter = new Fighter(uuid);
         if (this.settings.teams()) {
            fighter.team = team;
            inTeam++;
            if (inTeam >= this.settings.teamSize()) {
               team++;
               inTeam = 0;
            }
         }
         this.fighters.put(uuid, fighter);
      }
      if (this.settings.teams()) {
         shuffled.sort(Comparator.comparingInt(uuid -> this.fighters.get(uuid).team));
      }
      return shuffled;
   }

   private static void CollectionsShuffle(List<UUID> list) {
      java.util.Collections.shuffle(list, ThreadLocalRandom.current());
   }

   private List<Fighter> aliveFighters() {
      List<Fighter> out = new ArrayList<>();
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         if (fighter != null && fighter.alive && this.ctx.player(uuid) != null) {
            out.add(fighter);
         }
      }
      return out;
   }

   private void dropAll(ServerPlayer player) {
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty()) {
            player.drop(stack.copy(), true, false);
            inv.setItem(i, ItemStack.EMPTY);
         }
      }
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.fallDistance = 0.0F;
      player.removeAllEffects();
      player.invulnerableTime = 20;
   }

   private void restore(ServerPlayer player) {
      this.clearTeam(player);
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

   private void setTimer(int ticks) {
      this.ticksLeft = ticks;
      this.phaseMaxTicks = Math.max(1, ticks);
   }

   private void updateBoss() {
      if (this.phase == Phase.INTRO) {
         this.boss.setName(TextUtil.color("&e准备 &f" + Math.max(0, this.ticksLeft / 20) + "s"));
         this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) this.phaseMaxTicks));
         return;
      }
      if (this.settings.border() && !this.shrinking) {
         this.boss.setName(TextUtil.color("&c边界 &f" + Math.max(0, this.shrinkWaitTicks / 20) + "s"));
         int max = this.settings.shrinkDelaySeconds() * 20;
         this.boss.setProgress(Math.max(0.0F, this.shrinkWaitTicks / (float) Math.max(1, max)));
         return;
      }
      this.boss.setName(TextUtil.color("&6刷新 &f" + Math.max(0, this.refreshTicks / 20) + "s &8| &a存活 "
         + this.aliveFighters().size()));
      this.boss.setProgress(Math.max(0.0F, this.refreshTicks / (float) Math.max(1, this.settings.refreshSeconds() * 20)));
   }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7存活 &f" + this.aliveFighters().size() + "&7/" + this.seats.size());
      lines.add("&7刷新 &e" + Math.max(0, this.refreshTicks / 20) + "s");
      if (this.settings.border()) {
         lines.add("&7边界 &c" + Math.max(PillarArena.MIN_LIVE_SIZE, this.settings.borderSize() - this.inset * 2));
      }
      if (this.settings.fishingMode()) {
         lines.add("&b钓鱼模式");
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.board.update(player, lines);
         }
      }
   }

   private void title(ServerPlayer player, String title, String sub) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub)));
   }

   private void forEachOnline(PlayerFighter action) {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighter(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter != null && player != null) {
            action.accept(player, fighter);
         }
      }
   }

   private Fighter fighter(UUID uuid) {
      return this.fighters.get(uuid);
   }

   @FunctionalInterface
   private interface PlayerFighter {
      void accept(ServerPlayer player, Fighter fighter);
   }

   static final class Fighter {
      final UUID uuid;
      int team;
      int pillarIndex;
      boolean alive = true;

      Fighter(UUID uuid) {
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
