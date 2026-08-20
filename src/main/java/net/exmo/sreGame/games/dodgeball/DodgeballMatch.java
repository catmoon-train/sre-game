package net.exmo.sreGame.games.dodgeball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class DodgeballMatch {
   public enum Phase {
      INTRO,
      COUNTDOWN,
      FIGHT,
      SETTLE,
      ENDED
   }

   private static final int INTRO_SECONDS = 5;
   private static final int COUNTDOWN_TICKS = 60;
   private static final int SETTLE_SECONDS = 8;
   private static final int CATCH_RANGE = 3;
   private static final int CATCH_COOLDOWN = 10;
   private static final int POWERUP_INTERVAL = 30 * 20;
   private static final double MAX_RANGE = 25.0;
   private static final float THROW_POWER = 1.5F;
   private static final DustParticleOptions CATCH_DUST = new DustParticleOptions(
      new org.joml.Vector3f(0.2F, 1.0F, 0.35F), 1.2F);

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final DodgeballArena arena;
   private final DodgeballSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Map<UUID, Ball> balls = new ConcurrentHashMap<>();
   private final List<ActivePowerup> powerups = new ArrayList<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private int round = 1;
   private int redWins;
   private int blueWins;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int boardTicks;
   private int powerupTicks;
   private boolean begun;
   private boolean frenzyAnnounced;

   public DodgeballMatch(GameContext ctx, GameRoom room, List<UUID> seats, DodgeballArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.dodgeballSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&b躲避球"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.assignTeams();
   }

   public UUID id() {
      return this.id;
   }

   public Phase phase() {
      return this.phase;
   }

   public ServerLevel level() {
      return this.ctx.dodgeball().arenas().level();
   }

   public void start() {
      this.begun = true;
      ServerLevel level = this.level();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) {
            continue;
         }
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&b躲避球");
         this.boss.addPlayer(player);
         this.ensureTeam(player, fighter);
         player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.giveKit(player, fighter);
         this.teleportSpawn(player, fighter, level);
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b&l躲避球");
      this.ctx.broadcast(this.room, "&7红蓝对掷雪球，击中淘汰，空手右键接球可反杀投掷者。");
      this.ctx.broadcast(this.room, "&7每局 &f" + this.settings.roundSeconds() + "s &7· 先赢 &f"
         + this.settings.winsNeeded() + " &7局 · 本局不复活");
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
      if (this.phase == Phase.COUNTDOWN) {
         this.tickCountdown();
      }
      if (this.boardTicks % 10 == 0) {
         this.pushBoard();
      }
      this.updateBoss();
      if (this.ticksLeft > 0) {
         return;
      }
      switch (this.phase) {
         case INTRO -> this.beginCountdown();
         case COUNTDOWN -> this.beginFight();
         case FIGHT -> this.beginSettle(this.roundWinnerByAlive(), "时间到");
         case SETTLE -> {
            if (this.redWins >= this.settings.winsNeeded() || this.blueWins >= this.settings.winsNeeded()
               || this.round >= this.settings.totalRounds()) {
               this.finish();
            } else {
               this.round++;
               this.beginCountdown();
            }
         }
         default -> {
         }
      }
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return InteractionResult.PASS;
      }
      if (this.phase != Phase.FIGHT || !fighter.alive) {
         return InteractionResult.FAIL;
      }
      if (stack == null || stack.isEmpty()) {
         this.tryCatch(player, fighter);
         return InteractionResult.FAIL;
      }
      if (stack.is(Items.SNOWBALL)) {
         this.throwBalls(player, fighter);
         return InteractionResult.FAIL;
      }
      return InteractionResult.FAIL;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      return this.handleUseItem(player, stack);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      return this.fighters.containsKey(player.getUUID()) && this.phase != Phase.ENDED;
   }

   public boolean handleDeath(ServerPlayer player) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.FIGHT && fighter.alive) {
         this.eliminate(player, fighter, null, false, "出局");
      }
      this.heal(player);
      return true;
   }

   public boolean handleSnowballHit(Snowball ball, Entity hit) {
      Ball info = this.balls.get(ball.getUUID());
      if (info == null || this.phase != Phase.FIGHT) {
         return this.arena.contains(ball.getX(), ball.getY(), ball.getZ());
      }
      this.balls.remove(ball.getUUID());
      if (!(hit instanceof ServerPlayer victim)) {
         return true;
      }
      Fighter target = this.fighters.get(victim.getUUID());
      Fighter thrower = this.fighters.get(info.thrower);
      if (target == null || !target.alive || thrower == null || target.team == thrower.team
         || target.uuid.equals(thrower.uuid)) {
         return true;
      }
      if (target.shieldTicks > 0) {
         target.shieldTicks = 0;
         this.play(victim, SoundEvents.SHIELD_BLOCK, 1.0F, 1.2F);
         this.ctx.send(victim, "&b护盾抵挡了一次攻击！");
         return true;
      }
      ServerPlayer attacker = this.ctx.player(thrower.uuid);
      this.eliminate(victim, target, attacker, false, "被雪球击中");
      return true;
   }

   public void onLeave(UUID uuid) {
      this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) {
         this.restore(player);
      } else {
         this.board.remove(uuid);
      }
      if (this.phase != Phase.ENDED) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了躲避球。");
         if (this.phase == Phase.FIGHT) {
            this.checkWipe();
         }
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
      this.forEachOnline((player, fighter) -> this.title(player, "&b躲避球", "&7准备进入场地"));
   }

   private void beginCountdown() {
      this.phase = Phase.COUNTDOWN;
      this.setTimer(COUNTDOWN_TICKS);
      this.frenzyAnnounced = false;
      this.clearBallsAndPowerups();
      ServerLevel level = this.level();
      Map<DodgeballTeam, Integer> index = new HashMap<>();
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null) {
            continue;
         }
         fighter.alive = true;
         fighter.roundKills = 0;
         fighter.streak = 0;
         fighter.shieldTicks = 0;
         fighter.triple = false;
         fighter.homing = false;
         fighter.catchCool = 0;
         fighter.marked = false;
         int i = index.getOrDefault(fighter.team, 0);
         index.put(fighter.team, i + 1);
         fighter.spawnIndex = i;
         player.setGameMode(GameType.ADVENTURE);
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         player.onUpdateAbilities();
         player.removeAllEffects();
         this.heal(player);
         this.giveKit(player, fighter);
         this.teleportSpawn(player, fighter, level);
      }
      this.ctx.broadcast(this.room, "&e第 &f" + this.round + " &e局即将开始  &c" + this.redWins + " &7: &9" + this.blueWins);
   }

   private void tickCountdown() {
      int sec = (this.ticksLeft + 19) / 20;
      if (this.ticksLeft % 20 == 0 && sec > 0 && sec <= 3) {
         String color = sec == 1 ? "&c" : sec == 2 ? "&e" : "&a";
         this.forEachOnline((player, fighter) -> this.title(player, color + sec, "&7回到出生点"));
         this.playAll(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.8F + (3 - sec) * 0.2F);
      }
      ServerLevel level = this.level();
      this.forEachOnline((player, fighter) -> {
         if (!fighter.alive) {
            return;
         }
         player.setDeltaMovement(Vec3.ZERO);
         this.teleportSpawn(player, fighter, level);
      });
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.setTimer(this.settings.roundTicks());
      this.powerupTicks = POWERUP_INTERVAL;
      this.forEachOnline((player, fighter) -> {
         this.title(player, "&a开战！", "&7右键投掷 · 空手接球反杀");
         this.giveKit(player, fighter);
      });
      this.playAll(SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 0.7F);
   }

   private void tickFight() {
      this.tickBalls();
      this.tickPowerups();
      this.forEachOnline((player, fighter) -> {
         if (fighter.catchCool > 0) {
            fighter.catchCool--;
         }
         if (fighter.shieldTicks > 0) {
            fighter.shieldTicks--;
         }
         if (!fighter.alive) {
            return;
         }
         if (this.arena.inLava(player.getX(), player.getY(), player.getZ()) || player.getY() < this.arena.lavaY() - 0.5) {
            this.eliminate(player, fighter, null, false, "掉出场地");
            return;
         }
         this.refillSnowballs(player);
         this.applyFightEffects(player, fighter);
      });
      if (this.settings.frenzy() && this.ticksLeft == 30 * 20 && !this.frenzyAnnounced) {
         this.frenzyAnnounced = true;
         this.forEachOnline((player, fighter) -> this.title(player, "&c绝杀时刻", "&7雪球速度 +20%"));
         this.playAll(SoundEvents.ENDER_DRAGON_AMBIENT, 0.7F, 1.2F);
      }
      if (this.ticksLeft > 0 && this.ticksLeft % (10 * 20) == 0) {
         this.playAll(SoundEvents.BELL_BLOCK, 0.5F, 1.0F);
      }
      this.checkWipe();
   }

   private void beginSettle(DodgeballTeam winner, String reason) {
      if (this.phase != Phase.FIGHT) {
         return;
      }
      this.phase = Phase.SETTLE;
      this.setTimer(SETTLE_SECONDS * 20);
      this.clearBallsAndPowerups();
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.alive) {
            fighter.score += 15;
         }
      }
      if (winner != null) {
         if (winner == DodgeballTeam.RED) {
            this.redWins++;
         } else {
            this.blueWins++;
         }
         for (Fighter fighter : this.fighters.values()) {
            if (fighter.team == winner) {
               fighter.score += 30;
            }
         }
         this.ctx.broadcast(this.room, "&8&m----------------");
         this.ctx.broadcast(this.room, winner.display() + " &a赢下第 &f" + this.round + " &a局 &7" + reason);
         this.ctx.broadcast(this.room, "&c" + this.redWins + " &7: &9" + this.blueWins);
         this.ctx.broadcast(this.room, "&8&m----------------");
         this.forEachOnline((player, fighter) -> this.title(player,
            fighter.team == winner ? "&a本局胜利" : "&c本局落败",
            winner.display() + " &7" + this.redWins + " : " + this.blueWins));
         this.burst(winner);
      } else {
         this.ctx.broadcast(this.room, "&7第 " + this.round + " 局平局。");
         this.forEachOnline((player, fighter) -> this.title(player, "&7平局", "&7双方存活人数相同"));
      }
   }

   private void finish() {
      if (this.phase == Phase.ENDED) {
         return;
      }
      this.phase = Phase.ENDED;
      this.clearBallsAndPowerups();
      DodgeballTeam winner = this.redWins == this.blueWins ? null
         : this.redWins > this.blueWins ? DodgeballTeam.RED : DodgeballTeam.BLUE;
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&b&l躲避球结算");
      this.ctx.broadcast(this.room, "&c红队 " + this.redWins + "  &8-  &9蓝队 " + this.blueWins);
      if (winner != null) {
         this.ctx.broadcast(this.room, "&a获胜：" + winner.display());
      } else {
         this.ctx.broadcast(this.room, "&7平局。");
      }
      List<Fighter> ranked = new ArrayList<>(this.fighters.values());
      ranked.sort(Comparator.comparingInt((Fighter f) -> f.score).reversed());
      int shown = 0;
      for (Fighter fighter : ranked) {
         if (shown++ >= 8) {
            break;
         }
         this.ctx.broadcast(this.room, fighter.team.code() + this.ctx.name(fighter.uuid)
            + " &7击杀 &f" + fighter.kills + " &8| &7得分 &e" + fighter.score);
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            Fighter fighter = this.fighters.get(uuid);
            if (winner != null && fighter != null && fighter.team == winner) {
               this.title(player, "&6胜利", winner.display());
            }
            this.restore(player);
         }
      }
      this.ctx.dodgeball().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.dodgeball().remove(this);
   }

   private void checkWipe() {
      if (this.phase != Phase.FIGHT) {
         return;
      }
      int red = this.aliveCount(DodgeballTeam.RED);
      int blue = this.aliveCount(DodgeballTeam.BLUE);
      if (red <= 0 && blue <= 0) {
         this.beginSettle(null, "双方全灭");
      } else if (red <= 0) {
         this.beginSettle(DodgeballTeam.BLUE, "红队全灭");
      } else if (blue <= 0) {
         this.beginSettle(DodgeballTeam.RED, "蓝队全灭");
      }
   }

   private DodgeballTeam roundWinnerByAlive() {
      int red = this.aliveCount(DodgeballTeam.RED);
      int blue = this.aliveCount(DodgeballTeam.BLUE);
      if (red > blue) {
         return DodgeballTeam.RED;
      }
      if (blue > red) {
         return DodgeballTeam.BLUE;
      }
      int redScore = this.teamRoundScore(DodgeballTeam.RED);
      int blueScore = this.teamRoundScore(DodgeballTeam.BLUE);
      if (redScore > blueScore) {
         return DodgeballTeam.RED;
      }
      if (blueScore > redScore) {
         return DodgeballTeam.BLUE;
      }
      return null;
   }

   private int teamRoundScore(DodgeballTeam team) {
      int sum = 0;
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.team == team) {
            sum += fighter.score;
         }
      }
      return sum;
   }

   private int aliveCount(DodgeballTeam team) {
      int n = 0;
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.team == team && fighter.alive && this.ctx.player(fighter.uuid) != null) {
            n++;
         }
      }
      return n;
   }

   private void throwBalls(ServerPlayer player, Fighter fighter) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int count = fighter.triple ? 3 : 1;
      fighter.triple = false;
      boolean homing = fighter.homing;
      fighter.homing = false;
      float power = this.frenzy() ? THROW_POWER * 1.2F : THROW_POWER;
      float[] yaws = count == 3 ? new float[] {-10.0F, 0.0F, 10.0F} : new float[] {0.0F};
      for (float yawOff : yaws) {
         Snowball ball = new Snowball(level, player);
         ball.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOff, 0.0F, power, 0.4F);
         ball.setGlowingTag(true);
         level.addFreshEntity(ball);
         this.balls.put(ball.getUUID(), new Ball(player.getUUID(), fighter.team, homing, ball.position()));
      }
      this.play(player, SoundEvents.ARROW_SHOOT, 0.8F, 1.35F);
      this.refillSnowballs(player);
   }

   private void tryCatch(ServerPlayer player, Fighter fighter) {
      if (fighter.catchCool > 0) {
         this.ctx.send(player, "&c接球冷却中…");
         this.flash(player, false);
         return;
      }
      fighter.catchCool = CATCH_COOLDOWN;
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Snowball best = null;
      double bestDot = 0.45;
      AABB box = player.getBoundingBox().inflate(CATCH_RANGE);
      for (Snowball ball : level.getEntitiesOfClass(Snowball.class, box)) {
         Ball info = this.balls.get(ball.getUUID());
         if (info == null || info.team == fighter.team) {
            continue;
         }
         Vec3 to = ball.position().subtract(eye);
         if (to.lengthSqr() > CATCH_RANGE * CATCH_RANGE || to.lengthSqr() < 0.04) {
            continue;
         }
         double dot = to.normalize().dot(look);
         if (dot > bestDot) {
            bestDot = dot;
            best = ball;
         }
      }
      if (best == null) {
         this.flash(player, false);
         this.play(player, SoundEvents.VILLAGER_NO, 0.6F, 1.4F);
         return;
      }
      Ball info = this.balls.remove(best.getUUID());
      best.discard();
      this.flash(player, true);
      this.play(player, SoundEvents.SHIELD_BLOCK, 1.0F, 1.4F);
      this.play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.6F);
      fighter.score += 20;
      fighter.kills++;
      fighter.roundKills++;
      fighter.streak++;
      this.announceStreak(player, fighter);
      ServerPlayer thrower = info == null ? null : this.ctx.player(info.thrower);
      Fighter victim = info == null ? null : this.fighters.get(info.thrower);
      if (thrower != null && victim != null && victim.alive) {
         this.eliminate(thrower, victim, player, true, "被接球反杀");
      }
   }

   private void eliminate(ServerPlayer victim, Fighter fighter, ServerPlayer killer, boolean catchKill, String reason) {
      if (!fighter.alive) {
         return;
      }
      fighter.alive = false;
      fighter.streak = 0;
      fighter.marked = false;
      fighter.shieldTicks = 0;
      if (killer != null) {
         Fighter attacker = this.fighters.get(killer.getUUID());
         if (attacker != null && !catchKill) {
            attacker.kills++;
            attacker.roundKills++;
            attacker.streak++;
            int extra = attacker.streak >= 3 ? 15 : attacker.streak == 2 ? 5 : 0;
            attacker.score += 10 + extra;
            this.announceStreak(killer, attacker);
         }
      }
      this.heal(victim);
      victim.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(victim, level, this.arena.watch(), victim.getYRot());
      }
      level = this.level();
      if (level != null) {
         level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(),
            8, 0.4, 0.5, 0.4, 0.02);
         this.play(victim, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.2F);
      }
      this.title(victim, "&c你被淘汰了！", "&7" + reason + " · 观战至本局结束");
      String killerName = killer == null ? "场地" : killer.getGameProfile().getName();
      this.ctx.broadcast(this.room, fighter.team.code() + victim.getGameProfile().getName()
         + " &7被 &f" + killerName + " &7淘汰（" + reason + "）");
      this.checkWipe();
   }

   private void tickBalls() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      Iterator<Map.Entry<UUID, Ball>> it = this.balls.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Ball> entry = it.next();
         Entity entity = level.getEntity(entry.getKey());
         if (!(entity instanceof Snowball ball) || !ball.isAlive()) {
            it.remove();
            continue;
         }
         Ball info = entry.getValue();
         if (ball.position().distanceTo(info.origin) > MAX_RANGE || !this.arena.contains(ball.getX(), ball.getY(), ball.getZ())) {
            ball.discard();
            it.remove();
            continue;
         }
         DustParticleOptions dust = new DustParticleOptions(info.team.dust(), 1.0F);
         level.sendParticles(dust, ball.getX(), ball.getY(), ball.getZ(), 2, 0.02, 0.02, 0.02, 0.0);
         if (!info.homing) {
            continue;
         }
         ServerPlayer nearest = this.nearestEnemy(ball.position(), info.team, info.thrower);
         if (nearest == null) {
            continue;
         }
         Vec3 vel = ball.getDeltaMovement();
         double speed = vel.length();
         if (speed < 0.05) {
            continue;
         }
         Vec3 want = nearest.position().add(0.0, 1.0, 0.0).subtract(ball.position()).normalize();
         Vec3 mixed = vel.normalize().scale(0.82).add(want.scale(0.18)).normalize().scale(speed);
         ball.setDeltaMovement(mixed);
      }
   }

   private ServerPlayer nearestEnemy(Vec3 from, DodgeballTeam team, UUID thrower) {
      ServerPlayer best = null;
      double bestDist = 12.0 * 12.0;
      for (Fighter fighter : this.fighters.values()) {
         if (!fighter.alive || fighter.team == team || fighter.uuid.equals(thrower)) {
            continue;
         }
         ServerPlayer player = this.ctx.player(fighter.uuid);
         if (player == null) {
            continue;
         }
         double dist = player.position().distanceToSqr(from);
         if (dist < bestDist) {
            bestDist = dist;
            best = player;
         }
      }
      return best;
   }

   private void tickPowerups() {
      if (!this.settings.powerups()) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      this.powerupTicks--;
      if (this.powerupTicks <= 0) {
         this.powerupTicks = POWERUP_INTERVAL;
         this.spawnPowerups(level, 2);
      }
      Iterator<ActivePowerup> it = this.powerups.iterator();
      while (it.hasNext()) {
         ActivePowerup drop = it.next();
         Entity entity = level.getEntity(drop.entityId);
         if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
            it.remove();
            continue;
         }
         ServerPlayer picker = this.nearestAlive(item.position(), 1.4);
         if (picker == null) {
            continue;
         }
         Fighter fighter = this.fighters.get(picker.getUUID());
         if (fighter == null || !fighter.alive) {
            continue;
         }
         this.applyPowerup(picker, fighter, drop.type);
         item.discard();
         it.remove();
      }
   }

   private void spawnPowerups(ServerLevel level, int count) {
      List<Vec3> spots = this.arena.powerupSpots();
      Collections.shuffle(spots, ThreadLocalRandom.current());
      DodgeballPowerup[] types = DodgeballPowerup.values();
      int spawned = 0;
      for (Vec3 spot : spots) {
         if (spawned >= count) {
            break;
         }
         if (this.occupied(spot)) {
            continue;
         }
         DodgeballPowerup type = types[ThreadLocalRandom.current().nextInt(types.length)];
         ItemEntity item = new ItemEntity(level, spot.x, spot.y, spot.z, type.stack());
         item.setPickUpDelay(32767);
         item.setUnlimitedLifetime();
         item.setInvulnerable(true);
         item.setGlowingTag(true);
         item.setCustomName(TextUtil.color("&e" + type.label()));
         item.setCustomNameVisible(true);
         item.setDeltaMovement(Vec3.ZERO);
         item.setNoGravity(true);
         level.addFreshEntity(item);
         this.powerups.add(new ActivePowerup(item.getUUID(), type));
         level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.4, spot.z, 10, 0.3, 0.3, 0.3, 0.02);
         spawned++;
      }
   }

   private boolean occupied(Vec3 spot) {
      for (ActivePowerup drop : this.powerups) {
         ServerLevel level = this.level();
         if (level == null) {
            return false;
         }
         Entity entity = level.getEntity(drop.entityId);
         if (entity != null && entity.position().distanceToSqr(spot) < 1.0) {
            return true;
         }
      }
      return false;
   }

   private void applyPowerup(ServerPlayer player, Fighter fighter, DodgeballPowerup type) {
      fighter.score += 2;
      this.play(player, SoundEvents.PLAYER_LEVELUP, 0.5F, 1.4F);
      this.ctx.send(player, "&e拾取道具：&f" + type.label());
      switch (type) {
         case SPEED -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true, true));
         case JUMP -> player.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 1, false, true, true));
         case SHIELD -> {
            fighter.shieldTicks = 10 * 20;
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0, false, true, true));
         }
         case TRIPLE -> fighter.triple = true;
         case HOMING -> fighter.homing = true;
      }
   }

   private ServerPlayer nearestAlive(Vec3 from, double range) {
      ServerPlayer best = null;
      double bestDist = range * range;
      for (Fighter fighter : this.fighters.values()) {
         if (!fighter.alive) {
            continue;
         }
         ServerPlayer player = this.ctx.player(fighter.uuid);
         if (player == null) {
            continue;
         }
         double dist = player.position().distanceToSqr(from);
         if (dist < bestDist) {
            bestDist = dist;
            best = player;
         }
      }
      return best;
   }

   private void applyFightEffects(ServerPlayer player, Fighter fighter) {
      if (this.settings.catchUp()) {
         int mine = this.aliveCount(fighter.team);
         int theirs = this.aliveCount(fighter.team.other());
         if (mine < theirs) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false, true));
         }
      }
      if (fighter.streak >= 5) {
         fighter.marked = true;
         player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, true));
      }
      if (fighter.shieldTicks > 0) {
         player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, true, false, false));
      }
   }

   private void announceStreak(ServerPlayer player, Fighter fighter) {
      if (fighter.streak == 2) {
         this.ctx.broadcast(this.room, "&6" + player.getGameProfile().getName() + " &e双杀！");
         this.title(player, "&6双杀", "&e连击奖励");
      } else if (fighter.streak == 3) {
         this.ctx.broadcast(this.room, "&6" + player.getGameProfile().getName() + " &c三连淘汰！");
         this.forEachOnline((p, f) -> this.title(p, "&6连杀", "&e" + player.getGameProfile().getName()));
      } else if (fighter.streak >= 5 && fighter.streak % 5 == 0) {
         this.ctx.broadcast(this.room, "&c" + player.getGameProfile().getName() + " &6已连杀 &f" + fighter.streak + " &6人，被标记！");
      }
   }

   private boolean frenzy() {
      return this.settings.frenzy() && this.phase == Phase.FIGHT && this.ticksLeft <= 30 * 20;
   }

   private void giveKit(ServerPlayer player, Fighter fighter) {
      player.getInventory().clearContent();
      ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
      chest.set(DataComponents.DYED_COLOR, new DyedItemColor(fighter.team.leather(), true));
      chest.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      player.setItemSlot(EquipmentSlot.CHEST, chest);
      this.refillSnowballs(player);
   }

   private void refillSnowballs(ServerPlayer player) {
      ItemStack snow = player.getInventory().getItem(0);
      if (!snow.is(Items.SNOWBALL) || snow.getCount() < 16) {
         player.getInventory().setItem(0, new ItemStack(Items.SNOWBALL, 16));
      }
   }

   private void teleportSpawn(ServerPlayer player, Fighter fighter, ServerLevel level) {
      if (level == null) {
         return;
      }
      int size = this.aliveCount(fighter.team);
      Vec3 pos = this.arena.spawn(fighter.team, fighter.spawnIndex, Math.max(1, size));
      this.arena.teleport(player, level, pos, this.arena.spawnYaw(fighter.team));
   }

   private void assignTeams() {
      List<UUID> shuffled = new ArrayList<>(this.seats);
      Collections.shuffle(shuffled, ThreadLocalRandom.current());
      int redCount = shuffled.size() / 2;
      int redIndex = 0;
      int blueIndex = 0;
      for (int i = 0; i < shuffled.size(); i++) {
         Fighter fighter = new Fighter(shuffled.get(i));
         if (i < redCount) {
            fighter.team = DodgeballTeam.RED;
            fighter.spawnIndex = redIndex++;
         } else {
            fighter.team = DodgeballTeam.BLUE;
            fighter.spawnIndex = blueIndex++;
         }
         this.fighters.put(fighter.uuid, fighter);
      }
   }

   private void enforce() {
      this.forEachOnline((player, fighter) -> {
         if (!fighter.alive || this.phase == Phase.ENDED) {
            return;
         }
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
         }
      });
   }

   private void pushBoard() {
      int redAlive = this.aliveCount(DodgeballTeam.RED);
      int blueAlive = this.aliveCount(DodgeballTeam.BLUE);
      int redTotal = this.teamSize(DodgeballTeam.RED);
      int blueTotal = this.teamSize(DodgeballTeam.BLUE);
      List<String> lines = new ArrayList<>();
      lines.add("&c红队 &f" + this.redWins + "  &8⚔  &9" + this.blueWins + " &9蓝队");
      lines.add("&c存活 " + redAlive + "/" + redTotal + "   &9" + blueAlive + "/" + blueTotal);
      lines.add("&7第 &f" + this.round + " &7局 &8| &e" + this.clock());
      if (this.frenzy()) {
         lines.add("&c绝杀时刻");
      }
      List<Fighter> ranked = new ArrayList<>(this.fighters.values());
      ranked.sort(Comparator.comparingInt((Fighter f) -> f.score).reversed());
      int n = 0;
      for (Fighter fighter : ranked) {
         if (n++ >= 8) {
            break;
         }
         String state = fighter.alive ? "&a存活" : "&8观战";
         lines.add(fighter.team.code() + this.ctx.name(fighter.uuid)
            + " &7" + fighter.kills + "杀 &e" + fighter.score + " " + state);
      }
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.board.update(player, lines);
         }
      }
   }

   private void updateBoss() {
      String label = switch (this.phase) {
         case INTRO -> "&e准备 &f" + Math.max(0, this.ticksLeft / 20) + "s";
         case COUNTDOWN -> "&a开战倒计时 &f" + Math.max(1, (this.ticksLeft + 19) / 20);
         case FIGHT -> (this.frenzy() ? "&c绝杀 " : "&b躲避球 ") + "&f" + this.clock()
            + "  &c" + this.aliveCount(DodgeballTeam.RED) + " &8: &9" + this.aliveCount(DodgeballTeam.BLUE);
         case SETTLE -> "&6结算 &f" + Math.max(0, this.ticksLeft / 20) + "s";
         case ENDED -> "&7结束";
      };
      this.boss.setName(TextUtil.color(label));
      this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) Math.max(1, this.phaseMaxTicks)));
      this.boss.setColor(this.frenzy() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
   }

   private String clock() {
      int sec = Math.max(0, this.ticksLeft / 20);
      return String.format("%d:%02d", sec / 60, sec % 60);
   }

   private int teamSize(DodgeballTeam team) {
      int n = 0;
      for (Fighter fighter : this.fighters.values()) {
         if (fighter.team == team) {
            n++;
         }
      }
      return n;
   }

   private void clearBallsAndPowerups() {
      ServerLevel level = this.level();
      if (level != null) {
         this.ctx.dodgeball().arenas().clearEntities(level, this.arena);
      }
      this.balls.clear();
      this.powerups.clear();
   }

   private void burst(DodgeballTeam winner) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      Vec3 c = this.arena.center();
      level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 2.0, c.z, 40, 1.5, 1.2, 1.5, 0.05);
      DustParticleOptions dust = new DustParticleOptions(winner.dust(), 1.4F);
      level.sendParticles(dust, c.x, c.y + 1.5, c.z, 50, 2.0, 1.0, 2.0, 0.02);
      this.playAll(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
   }

   private void flash(ServerPlayer player, boolean success) {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      if (success) {
         level.sendParticles(CATCH_DUST, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.4, 0.6, 0.4, 0.02);
         level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
            8, 0.3, 0.4, 0.3, 0.1);
      } else {
         level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
            6, 0.2, 0.3, 0.2, 0.01);
      }
   }

   private void setTimer(int ticks) {
      this.ticksLeft = ticks;
      this.phaseMaxTicks = Math.max(1, ticks);
   }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.fallDistance = 0.0F;
      player.setDeltaMovement(Vec3.ZERO);
      player.invulnerableTime = 10;
   }

   private void title(ServerPlayer player, String title, String subtitle) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }

   private void play(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
   }

   private void playAll(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
      this.forEachOnline((player, fighter) -> this.play(player, sound, volume, pitch));
   }

   private void ensureTeam(ServerPlayer player, Fighter fighter) {
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = fighter.team == DodgeballTeam.RED ? "srdbR" : "srdbB";
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) {
         team = board.addPlayerTeam(name);
      }
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setAllowFriendlyFire(false);
      team.setNameTagVisibility(Team.Visibility.ALWAYS);
      team.setColor(fighter.team.formatting());
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) {
         board.removePlayerFromTeam(player.getScoreboardName(), existing);
      }
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void clearTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam current = board.getPlayersTeam(player.getScoreboardName());
      if (current != null && current.getName().startsWith("srdb")) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
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

   private void forEachOnline(PlayerFighter consumer) {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter != null && player != null) {
            consumer.accept(player, fighter);
         }
      }
   }

   @FunctionalInterface
   private interface PlayerFighter {
      void accept(ServerPlayer player, Fighter fighter);
   }

   static final class Fighter {
      final UUID uuid;
      DodgeballTeam team = DodgeballTeam.RED;
      int spawnIndex;
      boolean alive = true;
      int kills;
      int roundKills;
      int streak;
      int score;
      int catchCool;
      int shieldTicks;
      boolean triple;
      boolean homing;
      boolean marked;

      Fighter(UUID uuid) {
         this.uuid = uuid;
      }
   }

   private record Ball(UUID thrower, DodgeballTeam team, boolean homing, Vec3 origin) {
   }

   private record ActivePowerup(UUID entityId, DodgeballPowerup type) {
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
