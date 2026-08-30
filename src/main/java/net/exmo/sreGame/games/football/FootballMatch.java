package net.exmo.sreGame.games.football;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.mixin.LivingJumpAccessor;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class FootballMatch {
   private enum Phase { INTRO, COUNTDOWN, PLAYING, GOAL, ENDED }
   private static final int MATCH_TICKS = 4 * 60 * 20;
   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final FootballArena arena;
   private final Map<UUID, PlayerState> players = new HashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private FootballBall ball;
   private UUID lastTouch;
   private int redScore, blueScore, ticksLeft, pauseTicks, boardTicks;

   public FootballMatch(GameContext ctx, GameRoom room, List<UUID> seats, FootballArena arena) {
      this.ctx = ctx; this.room = room; this.seats = List.copyOf(seats); this.arena = arena;
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&a足球大战"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
      assignTeams();
   }
   public UUID id() { return id; }
   public void start() {
      ServerLevel level = level(); if (level == null) { finish(); return; }
      forEach((player, state) -> { saved.put(player.getUUID(), Saved.capture(player)); board.create(player, "&a足球大战"); boss.addPlayer(player); ensureTeam(player, state.team); player.setGameMode(GameType.ADVENTURE); player.getInventory().clearContent(); player.closeContainer(); heal(player); arena.teleport(level, player, arena.spawn(state.team, state.index, teamSize(state.team)), arena.spawnYaw(state.team)); });
      ctx.broadcast(room, "&8&m----------------");
      ctx.broadcast(room, "&a&l足球大战");
      ctx.broadcast(room, "&7撞向足球即可带球；&f空手左键 &7按视线方向射门或传球。");
      ctx.broadcast(room, "&7按两次跳跃键可二段跳；&f按住 Shift &7消耗体力获得 &b速度 V&7 加速。&f4 分钟 &7后进球更多的队伍获胜。");
      ctx.broadcast(room, "&8&m----------------");
      phase = Phase.INTRO; pauseTicks = 60; ticksLeft = MATCH_TICKS; titleAll("&a足球大战", "&7准备开球");
   }
   public void tick() {
      if (phase == Phase.ENDED) return;
      boardTicks++;
      if (phase == Phase.PLAYING) { ticksLeft--; tickPlayers(); tickBall(); if (ticksLeft <= 0) { finish(); return; } }
      else if (--pauseTicks <= 0) { if (phase == Phase.INTRO || phase == Phase.GOAL) beginCountdown(); else if (phase == Phase.COUNTDOWN) beginPlay(); }
      if (boardTicks % 10 == 0) pushBoard(); updateBoss();
   }
   public boolean handleDamage(ServerPlayer player) { return players.containsKey(player.getUUID()) && phase != Phase.ENDED; }
   public boolean handleDeath(ServerPlayer player) { if (!players.containsKey(player.getUUID()) || phase == Phase.ENDED) return false; heal(player); teleportPlayer(player, players.get(player.getUUID())); return true; }
   public boolean handleAttack(ServerPlayer player, Entity entity) {
      if (phase != Phase.PLAYING || ball == null || !ball.isPart(entity)) return false;
      kick(player); return true;
   }
   public void handleSwing(ServerPlayer player) {
      if (phase != Phase.PLAYING || ball == null || ball.isRemoved()) return;
      if (player.position().distanceToSqr(ball.position()) <= 10.0 && player.getMainHandItem().isEmpty()) kick(player);
   }
   public void onLeave(UUID uuid) { PlayerState state = players.remove(uuid); ServerPlayer player = ctx.player(uuid); if (player != null) restore(player); else board.remove(uuid); if (phase != Phase.ENDED) { ctx.broadcast(room, "&7" + ctx.name(uuid) + " 离开了足球大战。"); if (players.isEmpty()) finish(); } }
   public void endNow() { finish(); }

   private ServerLevel level() { return ctx.football().arenas().level(); }
   private void beginCountdown() { phase = Phase.COUNTDOWN; pauseTicks = 60; removeBall(); resetPositions(); titleAll("&e开球准备", "&7" + ((ticksLeft + 19) / 20) + " 秒后开始"); }
   private void beginPlay() { phase = Phase.PLAYING; spawnBall(); titleAll("&a开球！", "&7进球更多的一方获胜"); playAll(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.3F); }
   private void resetPositions() { forEach((player, state) -> { heal(player); teleportPlayer(player, state); }); }
   private void teleportPlayer(ServerPlayer player, PlayerState state) { ServerLevel level = level(); if (level != null) arena.teleport(level, player, arena.spawn(state.team, state.index, teamSize(state.team)), arena.spawnYaw(state.team)); }
   private void tickPlayers() {
      forEach((player, state) -> {
         if (player.getY() < arena.floorY() - 2 || !arena.inPitch(player.getX(), player.getZ())) { teleportPlayer(player, state); return; }
         tickSprint(player, state);
         boolean jumping = ((LivingJumpAccessor)player).sre$isJumping(); boolean pressed = jumping && !state.wasJumping; state.wasJumping = jumping;
         if (player.onGround()) { state.airJumpReady = true; state.airTicks = 0; }
         else { state.airTicks++; if (state.airJumpReady && state.airTicks > 4 && pressed) { state.airJumpReady = false; Vec3 v = player.getDeltaMovement(); impulse(player, new Vec3(v.x, 0.62, v.z)); level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, .25, .08, .25, .02); play(player, SoundEvents.WOOL_PLACE, .7F, 1.35F); } }
      });
   }
   /** Shift is the football sprint control.  The short hidden effect is refreshed only while stamina lasts. */
   private void tickSprint(ServerPlayer player, PlayerState state) {
      boolean sprinting = player.isShiftKeyDown() && state.stamina > 0.0;
      if (sprinting) {
         state.stamina = Math.max(0.0, state.stamina - 1.0);
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 4, true, false, false));
      } else {
         state.stamina = Math.min(100.0, state.stamina + 0.65);
      }
   }
   private void tickBall() {
      if (ball == null || ball.isRemoved()) { spawnBall(); return; }
      ball.tick(arena);
      Vec3 pos = ball.position();
      if (pos.y < arena.floorY() || pos.y > arena.floorY() + 14 || !arena.contains(pos.x, pos.y, pos.z)) { spawnBall(); return; }
      if (pos.x < arena.minX() - 1.35 && arena.inGoalMouth(pos.z, pos.y)) { score(FootballTeam.BLUE); return; }
      if (pos.x > arena.maxX() + 1.35 && arena.inGoalMouth(pos.z, pos.y)) { score(FootballTeam.RED); return; }
      ServerPlayer carrier = null; double best = 2.25;
      for (UUID uuid : seats) { ServerPlayer p = ctx.player(uuid); if (p == null || !players.containsKey(uuid)) continue; double d = p.position().distanceTo(pos); if (d < best) { best = d; carrier = p; } }
      if (carrier != null) {
         Vec3 motion = carrier.getDeltaMovement(); Vec3 flat = new Vec3(motion.x, 0, motion.z);
         if (flat.lengthSqr() > 0.0025) { ball.dribble(motion); lastTouch = carrier.getUUID(); }
      }
   }
   private void kick(ServerPlayer player) {
      if (ball == null || ball.isRemoved() || !player.getMainHandItem().isEmpty() || player.position().distanceToSqr(ball.position()) > 14.44) return;
      Vec3 dir = player.getLookAngle().normalize();
      double lift = dir.y < -0.15 ? 0.55 : Math.max(0.12, dir.y * 0.85 + 0.28);
      ball.kick(dir, 1.28, lift); lastTouch = player.getUUID();
      Vec3 pos = ball.position(); level().sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 12, .18, .18, .18, .03); play(player, SoundEvents.PLAYER_ATTACK_STRONG, .8F, 1.15F);
   }
   private void score(FootballTeam team) {
      if (team == FootballTeam.RED) redScore++; else blueScore++;
      PlayerState scorer = lastTouch == null ? null : players.get(lastTouch);
      if (scorer != null && scorer.team == team) scorer.goals++;
      String who = scorer != null && scorer.team == team ? " &7· &f" + ctx.name(lastTouch) : "";
      ctx.broadcast(room, "&6⚽ 进球！ " + team.display() + who + " &7  &c" + redScore + " &8: &9" + blueScore);
      titleAll(team.code() + "进球！", "&c" + redScore + " &7: &9" + blueScore);
      Vec3 pos = ball.position(); playAll(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F); level().sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 1, pos.z, 40, 1.2, 1.0, 1.2, .04);
      removeBall(); lastTouch = null; phase = Phase.GOAL; pauseTicks = 60;
   }
   private void spawnBall() { ServerLevel level = level(); if (level == null) return; removeBall(); Vec3 pos = arena.kickoff().add(0.0, 0.38, 0.0); ball = new FootballBall(level, pos); }
   private void removeBall() { if (ball != null) ball.discard(); ball = null; }
   private void finish() {
      if (phase == Phase.ENDED) return; phase = Phase.ENDED; removeBall();
      FootballTeam winner = redScore == blueScore ? null : redScore > blueScore ? FootballTeam.RED : FootballTeam.BLUE;
      ctx.broadcast(room, "&8&m----------------"); ctx.broadcast(room, "&a&l足球大战结束  &c" + redScore + " &8: &9" + blueScore); ctx.broadcast(room, winner == null ? "&7平局。" : "&a获胜：" + winner.display());
      List<PlayerState> ranking = new ArrayList<>(players.values()); ranking.sort(Comparator.comparingInt((PlayerState s) -> s.goals).reversed());
      for (PlayerState state : ranking) if (state.goals > 0) ctx.broadcast(room, state.team.code() + ctx.name(state.uuid) + " &7进球 &e" + state.goals);
      ctx.broadcast(room, "&8&m----------------"); boss.removeAllPlayers(); board.removeAll();
      for (UUID uuid : seats) { ServerPlayer player = ctx.player(uuid); if (player != null) restore(player); }
      ctx.football().arenas().release(arena); ctx.rooms().onMatchEnded(id); ctx.football().remove(this);
   }
   private void assignTeams() { int red = 0, blue = 0; for (int i=0;i<seats.size();i++) { FootballTeam team = i % 2 == 0 ? FootballTeam.RED : FootballTeam.BLUE; int index = team == FootballTeam.RED ? red++ : blue++; players.put(seats.get(i), new PlayerState(seats.get(i), team, index)); } }
   private int teamSize(FootballTeam team) { int n = 0; for (PlayerState state : players.values()) if (state.team == team) n++; return n; }
   private void pushBoard() { int seconds = Math.max(0, ticksLeft / 20); String time = String.format("%d:%02d", seconds / 60, seconds % 60); forEach((p,s) -> board.update(p, List.of("&7时间 &f" + time, "", "&c红队 &f" + redScore, "&9蓝队 &f" + blueScore, "", "&7你是 " + s.team.display(), "&b体力 &f" + Math.round(s.stamina) + "%", "&8Shift 加速（速度 V）"))); }
   private void updateBoss() { boss.setProgress(phase == Phase.PLAYING ? Math.max(0f, Math.min(1f, ticksLeft / (float)MATCH_TICKS)) : 1f); boss.setName(TextUtil.color("&a足球大战  &c" + redScore + " &7: &9" + blueScore)); }
   private void ensureTeam(ServerPlayer player, FootballTeam team) { Scoreboard score = ctx.server().getScoreboard(); String name = team == FootballTeam.RED ? "srftR" : "srftB"; PlayerTeam current = score.getPlayerTeam(name); if (current == null) current = score.addPlayerTeam(name); current.setColor(team.formatting()); current.setCollisionRule(Team.CollisionRule.NEVER); current.setAllowFriendlyFire(false); PlayerTeam existing = score.getPlayersTeam(player.getScoreboardName()); if (existing != null && existing != current) score.removePlayerFromTeam(player.getScoreboardName(), existing); score.addPlayerToTeam(player.getScoreboardName(), current); }
   private void clearTeam(ServerPlayer player) { Scoreboard score = ctx.server().getScoreboard(); PlayerTeam team = score.getPlayersTeam(player.getScoreboardName()); if (team != null && team.getName().startsWith("srft")) score.removePlayerFromTeam(player.getScoreboardName(), team); }
   private void heal(ServerPlayer player) { player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20F); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO); }
   private void restore(ServerPlayer player) { clearTeam(player); player.removeAllEffects(); board.remove(player); boss.removePlayer(player); Saved state = saved.get(player.getUUID()); if (state != null) state.apply(player, ctx); else ctx.rooms().resetLobbyState(player); }
   private void titleAll(String main, String sub) { forEach((p,s) -> title(p,main,sub)); }
   private void title(ServerPlayer player, String main, String sub) { player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8)); player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(main))); player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(sub))); }
   private void playAll(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) { forEach((p,s) -> play(p,sound,volume,pitch)); }
   private void play(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) { player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch); }
   private void impulse(ServerPlayer player, Vec3 velocity) { player.setDeltaMovement(velocity); player.hasImpulse = true; player.hurtMarked = true; player.fallDistance = 0; player.connection.send(new ClientboundSetEntityMotionPacket(player)); }
   private void forEach(PlayerConsumer consumer) { for (UUID uuid : seats) { ServerPlayer player = ctx.player(uuid); PlayerState state = players.get(uuid); if (player != null && state != null) consumer.accept(player,state); } }
   @FunctionalInterface private interface PlayerConsumer { void accept(ServerPlayer player, PlayerState state); }
   private static final class PlayerState { final UUID uuid; final FootballTeam team; final int index; boolean wasJumping, airJumpReady; int airTicks, goals; double stamina = 100.0; PlayerState(UUID uuid, FootballTeam team, int index) { this.uuid=uuid; this.team=team; this.index=index; } }
   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos, float yaw, float pitch, GameType type, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) { List<ItemStack> items=new ArrayList<>(); Inventory inv=player.getInventory(); for(int i=0;i<inv.getContainerSize();i++) items.add(inv.getItem(i).copy()); return new Saved(player.level().dimension(),player.position(),player.getYRot(),player.getXRot(),player.gameMode.getGameModeForPlayer(),items); }
      void apply(ServerPlayer player, GameContext ctx) { ServerLevel level=ctx.server().getLevel(dimension); if(level==null) level=ctx.server().overworld(); player.teleportTo(level,pos.x,pos.y,pos.z,yaw,pitch); player.setGameMode(type); Inventory inv=player.getInventory(); inv.clearContent(); for(int i=0;i<Math.min(inv.getContainerSize(),items.size());i++) inv.setItem(i,items.get(i).copy()); }
   }
}
