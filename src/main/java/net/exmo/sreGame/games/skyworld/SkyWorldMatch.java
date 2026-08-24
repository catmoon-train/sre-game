package net.exmo.sreGame.games.skyworld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildwar.BuildSafety;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class SkyWorldMatch {
   public enum Phase {
      INTRO,
      FIGHT,
      SETTLE,
      ENDED
   }

   private static final ChatFormatting[] TEAM_COLORS = {
      ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
      ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE
   };
   private static final int INTRO_TICKS = 15 * 20;
   private static final int SETTLE_TICKS = 8 * 20;
   private static final int TAG_TICKS = 10 * 20;
   private static final int MIN_BORDER = 16;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final SkyArena arena;
   private final SkyWorldSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Map<BlockPos, BlockState> cages = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int boardTicks;
   private int fightTicks;
   private int graceTicks;
   private int refillTicks;
   private int shrinkWaitTicks;
   private int shrinkAccum;
   private int borderRadius;
   private boolean shrinking;
   private boolean begun;
   private boolean chestsFilled;
   private Fighter settledWinner;

   public SkyWorldMatch(GameContext ctx, GameRoom room, List<UUID> seats, SkyArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.skyWorldSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&b空岛战争"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.assignTeams();
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
      return this.ctx.skyWorld().arenas().level();
   }

   public int islandCount() {
      if (!this.settings.teams()) {
         return Math.max(1, this.seats.size());
      }
      int maxTeam = 0;
      for (Fighter fighter : this.fighters.values()) {
         maxTeam = Math.max(maxTeam, fighter.team);
      }
      return Math.max(1, maxTeam);
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.level();
      this.placeCages(level);
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&b空岛战争");
         this.boss.addPlayer(player);
         player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.ensureTeam(player, fighter);
         this.giveKitSelectors(player, fighter);
         if (level != null) {
            SkyArena.SpawnPad pad = this.arena.spawn(fighter.spawnIndex);
            this.arena.teleport(player, level, this.spawnPos(fighter), pad.yaw());
         }
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b&l空岛战争");
      this.ctx.broadcast(this.room, "&7宝箱 &f" + this.settings.chestTier().label()
         + " &8| &7保护 &f" + this.settings.pvpGraceSeconds() + "s");
      if (this.settings.teams()) {
         this.ctx.broadcast(this.room, "&7组队 &f" + this.settings.teamSize() + "人一组 &7· 友伤 "
            + this.settings.onOff(this.settings.friendlyFire()));
      }
      if (this.settings.refill()) {
         this.ctx.broadcast(this.room, "&7补箱 &f" + this.settings.refillSeconds() + "s");
      }
      this.ctx.broadcast(this.room, "&e笼子内右键选择职业，倒计时后开战。");
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.beginIntro();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.ticksLeft--;
      this.boardTicks++;
      if (this.phase == Phase.INTRO && this.ticksLeft > 0 && this.ticksLeft % 20 == 0 && this.ticksLeft <= 100) {
         int sec = this.ticksLeft / 20;
         this.forEachOnline((player, fighter) -> this.title(player, "&e" + sec, "&7即将开战"));
      }
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
      } else if (this.phase == Phase.SETTLE) {
         this.finish();
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null) {
         return InteractionResult.PASS;
      }
      if (this.phase == Phase.INTRO && fighter.alive) {
         SkyKit kit = SkyKit.fromSelector(stack);
         if (kit != null) {
            fighter.kit = kit;
            this.giveKitSelectors(player, fighter);
            this.ctx.send(player, "&a已选择职业： &f" + kit.label());
            return InteractionResult.FAIL;
         }
         return InteractionResult.FAIL;
      }
      if (this.phase != Phase.FIGHT || !fighter.alive) {
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || !fighter.alive) {
         return InteractionResult.FAIL;
      }
      BlockPos clicked = hit.getBlockPos();
      if (!player.isShiftKeyDown()
         && BuildSafety.isWorkstation(player.level().getBlockState(clicked).getBlock())) {
         return this.arena.inPlay(clicked) ? InteractionResult.PASS : InteractionResult.FAIL;
      }
      BlockPos place = clicked.relative(hit.getDirection());
      if (!this.arena.inPlay(place) || this.cages.containsKey(place)) {
         return InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || !fighter.alive) {
         return false;
      }
      return this.arena.inPlay(pos) && !this.cages.containsKey(pos);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase != Phase.FIGHT || !fighter.alive) {
         return true;
      }
      if (source.getEntity() instanceof ServerPlayer attacker) {
         Fighter other = this.fighter(attacker.getUUID());
         if (other == null || !other.alive) {
            return true;
         }
         if (this.graceTicks > 0) {
            return true;
         }
         if (this.settings.teams() && !this.settings.friendlyFire() && other.team != 0 && other.team == fighter.team) {
            return true;
         }
         fighter.tag(attacker.getUUID(), this.fightTicks);
         if (SkyLoot.isInstakill(attacker.getMainHandItem())) {
            player.setHealth(Math.min(player.getHealth(), 1.0F));
         }
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase != Phase.FIGHT || !fighter.alive) {
         this.heal(player);
         this.returnToPad(player, fighter);
         return true;
      }
      this.heal(player);
      this.eliminate(player, fighter, this.killMessage(fighter), "&c阵亡");
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
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了空岛战争。");
      }
      this.checkWin();
   }

   public void endNow() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      if (this.phase != Phase.SETTLE) {
         this.settledWinner = null;
      }
      this.finish();
   }

   private void beginIntro() {
      this.phase = Phase.INTRO;
      this.setTimer(INTRO_TICKS);
      this.forEachOnline((player, fighter) -> this.title(player, "&b空岛战争", "&e选择职业 · 准备开战"));
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.fightTicks = 0;
      this.graceTicks = this.settings.pvpGraceSeconds() * 20;
      this.refillTicks = this.settings.refill() ? this.settings.refillSeconds() * 20 : Integer.MAX_VALUE;
      this.borderRadius = this.settings.borderSize();
      this.shrinkWaitTicks = this.settings.border() ? this.settings.shrinkDelaySeconds() * 20 : Integer.MAX_VALUE;
      this.setTimer(Integer.MAX_VALUE);
      this.removeCages();
      this.fillChests();
      this.forEachOnline((player, fighter) -> {
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         fighter.kit.give(player);
         player.removeAllEffects();
         this.heal(player);
         this.returnToPad(player, fighter);
         this.title(player, "&c开战", this.graceTicks > 0 ? "&7保护 " + (this.graceTicks / 20) + "s" : "&7活到最后");
      });
      this.ctx.broadcast(this.room, "&c笼子已打开，战斗开始！");
   }

   private void tickFight() {
      this.fightTicks++;
      if (this.graceTicks > 0) {
         this.graceTicks--;
         if (this.graceTicks == 0) {
            this.ctx.broadcast(this.room, "&c保护时间结束，可以 PVP！");
         }
      }
      if (this.settings.refill() && !this.chestsFilled) {
         this.refillTicks--;
         if (this.refillTicks <= 0) {
            this.chestsFilled = true;
            this.fillChests();
            this.ctx.broadcast(this.room, "&6宝箱已补充！");
            this.forEachOnline((player, fighter) -> {
               if (fighter.alive) {
                  this.title(player, "&6补箱", "&e中岛与玩家岛已刷新");
               }
            });
         }
      }
      if (this.settings.border()) {
         if (!this.shrinking) {
            this.shrinkWaitTicks--;
            if (this.shrinkWaitTicks <= 0) {
               this.shrinking = true;
               this.ctx.broadcast(this.room, "&c边界开始收缩！");
            }
         } else {
            this.shrinkAccum++;
            if (this.shrinkAccum >= this.settings.ticksPerShrinkBlock() && this.borderRadius > MIN_BORDER) {
               this.shrinkAccum = 0;
               this.borderRadius--;
            }
         }
      }
   }

   private void beginSettle(Fighter winner) {
      if (this.phase == Phase.SETTLE || this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.SETTLE;
      this.settledWinner = winner;
      this.setTimer(SETTLE_TICKS);
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b&l空岛战争结算");
      if (winner != null) {
         String name = this.ctx.name(winner.uuid);
         if (this.settings.teams() && winner.team > 0) {
            this.ctx.broadcast(this.room, "&a获胜队伍 &e#" + winner.team + " &7（" + name + " 等）");
         } else {
            this.ctx.broadcast(this.room, "&a胜者： &f" + name);
         }
         ServerPlayer player = this.ctx.player(winner.uuid);
         if (player != null) {
            this.title(player, "&6胜利", "&e空岛战争");
         }
      } else {
         this.ctx.broadcast(this.room, "&7没有幸存者。");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
   }

   private void finish() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.removeCages();
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.skyWorld().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.skyWorld().remove(this);
   }

   private void enforce() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      this.forEachOnline((player, fighter) -> {
         if (this.phase == Phase.ENDED) {
            return;
         }
         if (!fighter.alive) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
               player.setGameMode(GameType.SPECTATOR);
            }
            return;
         }
         if (this.phase == Phase.INTRO) {
            Vec3 pad = this.spawnPos(fighter);
            if (player.position().distanceToSqr(pad) > 4.0) {
               this.arena.teleport(player, level, pad, this.arena.spawn(fighter.spawnIndex).yaw());
            }
            player.fallDistance = 0.0F;
            return;
         }
         if (this.phase != Phase.FIGHT) {
            return;
         }
         if (player.getY() < this.arena.voidY()) {
            this.eliminate(player, fighter, "&c" + player.getGameProfile().getName() + " 掉入虚空。", "&c掉入虚空");
            return;
         }
         if (this.settings.border()) {
            double dx = player.getX() - (this.arena.centerX() + 0.5);
            double dz = player.getZ() - (this.arena.centerZ() + 0.5);
            if (Math.sqrt(dx * dx + dz * dz) > this.borderRadius + 0.5) {
               this.eliminate(player, fighter, "&c" + player.getGameProfile().getName() + " 越界出局。", "&c越界出局");
            }
         }
      });
   }

   private void eliminate(ServerPlayer player, Fighter fighter, String broadcast, String title) {
      if (!fighter.alive || this.phase != Phase.FIGHT) {
         this.heal(player);
         return;
      }
      fighter.alive = false;
      this.dropAll(player);
      this.heal(player);
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.watch(), 0.0F);
      }
      this.title(player, title, "&7旁观至对局结束");
      this.ctx.broadcast(this.room, broadcast);
      this.checkWin();
   }

   private String killMessage(Fighter victim) {
      String name = this.ctx.name(victim.uuid);
      if (victim.lastHit != null && this.fightTicks - victim.lastHitTick <= TAG_TICKS) {
         return "&c" + name + " &7被 &f" + this.ctx.name(victim.lastHit) + " &c击杀。";
      }
      return "&c" + name + " 出局了。";
   }

   private void checkWin() {
      if (this.phase == Phase.ENDED || this.phase == Phase.SETTLE) {
         return;
      }
      List<Fighter> alive = this.aliveFighters();
      if (this.settings.teams()) {
         int teamsLeft = (int) alive.stream().mapToInt(f -> f.team).distinct().count();
         if (teamsLeft <= 1) {
            this.beginSettle(alive.isEmpty() ? null : alive.get(0));
         }
         return;
      }
      if (alive.size() <= 1) {
         this.beginSettle(alive.isEmpty() ? null : alive.get(0));
      }
   }

   private void fillChests() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      this.arena.ensureChests(level);
      for (BlockPos pos : this.arena.islandChests()) {
         this.fillOne(level, pos, SkyArena.Band.ISLAND);
      }
      for (BlockPos pos : this.arena.midChests()) {
         this.fillOne(level, pos, SkyArena.Band.MID);
      }
      for (BlockPos pos : this.arena.centerChests()) {
         this.fillOne(level, pos, SkyArena.Band.CENTER);
      }
   }

   private void fillOne(ServerLevel level, BlockPos pos, SkyArena.Band band) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be instanceof RandomizableContainerBlockEntity chest) {
         SkyLoot.fill(chest, band, this.settings.chestTier(), level.registryAccess());
         chest.setChanged();
      }
   }

   private void placeCages(ServerLevel level) {
      if (level == null) {
         return;
      }
      this.cages.clear();
      for (Fighter fighter : this.fighters.values()) {
         for (BlockPos pos : this.arena.cageBlocks(this.spawnPos(fighter))) {
            if (this.cages.containsKey(pos)) {
               continue;
            }
            this.cages.put(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
         }
      }
   }

   private void removeCages() {
      ServerLevel level = this.level();
      if (level == null) {
         this.cages.clear();
         return;
      }
      for (Map.Entry<BlockPos, BlockState> entry : this.cages.entrySet()) {
         level.setBlock(entry.getKey(), entry.getValue(), 3);
      }
      this.cages.clear();
   }

   private void giveKitSelectors(ServerPlayer player, Fighter fighter) {
      Inventory inv = player.getInventory();
      inv.clearContent();
      int slot = 0;
      for (SkyKit kit : SkyKit.values()) {
         inv.setItem(slot++, kit.selector(kit == fighter.kit));
      }
   }

   private Vec3 spawnPos(Fighter fighter) {
      SkyArena.SpawnPad pad = this.arena.spawn(fighter.spawnIndex);
      if (fighter.spawnOffset == 0.0) {
         return pad.pos();
      }
      double rad = Math.toRadians(pad.yaw());
      return pad.pos().add(Math.cos(rad) * fighter.spawnOffset * 1.4, 0.0, Math.sin(rad) * fighter.spawnOffset * 1.4);
   }

   private void returnToPad(ServerPlayer player, Fighter fighter) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      SkyArena.SpawnPad pad = this.arena.spawn(fighter.spawnIndex);
      this.arena.teleport(player, level, this.spawnPos(fighter), pad.yaw());
      player.fallDistance = 0.0F;
   }

   private void assignTeams() {
      List<UUID> shuffled = new ArrayList<>(this.seats);
      java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
      int team = 1;
      int inTeam = 0;
      int ffaIndex = 0;
      for (UUID uuid : shuffled) {
         Fighter fighter = new Fighter(uuid);
         if (this.settings.teams()) {
            fighter.team = team;
            fighter.spawnIndex = team - 1;
            inTeam++;
            if (inTeam >= this.settings.teamSize()) {
               team++;
               inTeam = 0;
            }
         } else {
            fighter.spawnIndex = ffaIndex++;
         }
         this.fighters.put(uuid, fighter);
      }
      if (this.settings.teams()) {
         int perIsland = this.settings.teamSize();
         Map<Integer, Integer> offsets = new HashMap<>();
         List<UUID> ordered = new ArrayList<>(shuffled);
         ordered.sort(Comparator.comparingInt(uuid -> this.fighters.get(uuid).team));
         for (UUID uuid : ordered) {
            Fighter fighter = this.fighters.get(uuid);
            int used = offsets.getOrDefault(fighter.team, 0);
            offsets.put(fighter.team, used + 1);
            fighter.spawnOffset = used - (perIsland - 1) / 2.0;
         }
      }
   }

   private void ensureTeam(ServerPlayer player, Fighter fighter) {
      if (!this.settings.teams() || fighter.team <= 0) {
         return;
      }
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = "srsw" + fighter.team;
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
      }
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setAllowFriendlyFire(this.settings.friendlyFire());
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
      if (current != null && current.getName().startsWith("srsw")) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
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
         this.boss.setName(TextUtil.color("&e开战 &f" + Math.max(0, this.ticksLeft / 20) + "s"));
         this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) this.phaseMaxTicks));
         return;
      }
      if (this.phase == Phase.SETTLE) {
         this.boss.setName(TextUtil.color("&6结算 &f" + Math.max(0, this.ticksLeft / 20) + "s"));
         this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) this.phaseMaxTicks));
         return;
      }
      if (this.graceTicks > 0) {
         this.boss.setName(TextUtil.color("&a保护 &f" + (this.graceTicks / 20) + "s &8| &b存活 "
            + this.aliveFighters().size()));
         int max = Math.max(1, this.settings.pvpGraceSeconds() * 20);
         this.boss.setProgress(this.graceTicks / (float) max);
         return;
      }
      if (this.settings.refill() && !this.chestsFilled) {
         this.boss.setName(TextUtil.color("&6补箱 &f" + Math.max(0, this.refillTicks / 20) + "s &8| &b存活 "
            + this.aliveFighters().size()));
         int max = Math.max(1, this.settings.refillSeconds() * 20);
         this.boss.setProgress(this.refillTicks / (float) max);
         return;
      }
      if (this.settings.border() && !this.shrinking) {
         this.boss.setName(TextUtil.color("&c边界 &f" + Math.max(0, this.shrinkWaitTicks / 20) + "s"));
         int max = Math.max(1, this.settings.shrinkDelaySeconds() * 20);
         this.boss.setProgress(this.shrinkWaitTicks / (float) max);
         return;
      }
      this.boss.setName(TextUtil.color("&b存活 &f" + this.aliveFighters().size() + "&7/" + this.seats.size()
         + (this.settings.border() ? " &8| &c圈 " + this.borderRadius : "")));
      this.boss.setProgress(this.aliveFighters().size() / (float) Math.max(1, this.seats.size()));
   }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7存活 &f" + this.aliveFighters().size() + "&7/" + this.seats.size());
      if (this.phase == Phase.INTRO) {
         lines.add("&e开战 &f" + Math.max(0, this.ticksLeft / 20) + "s");
         lines.add("&7右键选择职业");
      } else if (this.phase == Phase.SETTLE) {
         lines.add("&6结算中");
      } else {
         if (this.graceTicks > 0) {
            lines.add("&a保护 &f" + (this.graceTicks / 20) + "s");
         }
         if (this.settings.refill() && !this.chestsFilled) {
            lines.add("&6补箱 &e" + Math.max(0, this.refillTicks / 20) + "s");
         }
         if (this.settings.border()) {
            lines.add("&7边界 &c" + this.borderRadius);
         }
         lines.add("&7宝箱 &f" + this.settings.chestTier().label());
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
      int spawnIndex;
      double spawnOffset;
      SkyKit kit = SkyKit.NONE;
      boolean alive = true;
      UUID lastHit;
      int lastHitTick = Integer.MIN_VALUE / 2;

      Fighter(UUID uuid) {
         this.uuid = uuid;
      }

      void tag(UUID attacker, int tick) {
         this.lastHit = attacker;
         this.lastHitTick = tick;
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
