package net.exmo.sreGame.games.nametagwar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class NameTagWarMatch {
   public enum Phase {
      INTRO,
      FIGHT,
      ENDED
   }

   private static final int INTRO_SECONDS = 5;
   private static final ChatFormatting[] TEAM_COLORS = {
      ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
      ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE
   };

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final NameTagWarArena arena;
   private final NameTagWarSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final Map<UUID, UUID> tagEntities = new ConcurrentHashMap<>();
   private final Map<UUID, RipSession> ripSessions = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> lastHurtTick = new ConcurrentHashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final Map<UUID, ServerBossEvent> staminaBars = new ConcurrentHashMap<>();
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int phaseMaxTicks;
   private int boardTicks;
   private int totalTicks;
   private int shrinkWaitTicks;
   private int shrinkAccum;
   private int inset;
   private boolean begun;
   private boolean shrinking;

   public NameTagWarMatch(GameContext ctx, GameRoom room, List<UUID> seats, NameTagWarArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.nameTagWarSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6撕名牌"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.assignTeams(this.seats);
   }

   public UUID id() { return this.id; }
   public GameContext ctx() { return this.ctx; }
   public GameRoom room() { return this.room; }
   public Phase phase() { return this.phase; }

   public ServerLevel level() {
      return this.ctx.nameTagWar().arenas().level();
   }

   public NameTagWarArena.Layout layout() {
      return new NameTagWarArena.Layout(this.settings.borderSize());
   }

   public boolean hasTag(UUID player) {
      return this.tagEntities.containsKey(player);
   }

   public boolean isTagEntity(Entity entity) {
      if (entity == null) {
         return false;
      }
      return this.tagEntities.containsValue(entity.getUUID());
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
         this.board.create(player, "&6撕名牌");
         this.boss.addPlayer(player);
         ServerBossEvent stamina = new ServerBossEvent(TextUtil.color("&a体力 &f100%"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
         stamina.setProgress(1.0F);
         stamina.addPlayer(player);
         this.staminaBars.put(uuid, stamina);
         player.setGameMode(GameType.SURVIVAL);
         player.getInventory().clearContent();
         player.closeContainer();
         this.heal(player);
         this.ensureTeam(player, fighter);
         this.giveKit(player);
         if (level != null) {
            this.arena.teleport(player, level, this.arena.spawnCenter());
         }
         this.spawnTag(player);
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l撕名牌");
      this.ctx.broadcast(this.room, "&7每人背部一张名牌，用 &f剪刀 &7对准敌人背部持续撕取。");
      this.ctx.broadcast(this.room, "&7速撕 &b0.25s &7背后即可 · 稳撕 &e0.5s &7侧前即可。");
      this.ctx.broadcast(this.room, "&7场地为 &f3 层竞技场建筑 &7· 四墙中点楼梯连通各层。");
      this.ctx.broadcast(this.room, "&7&l本局禁止互相攻击 &7· 只有撕下名牌才会淘汰。");
      if (this.settings.teams()) {
         this.ctx.broadcast(this.room, "&7组队 &f" + this.settings.teamSize() + "人一组。");
      }
      if (this.settings.border()) {
         this.ctx.broadcast(this.room, "&c边界 " + this.settings.borderSize() + " · "
            + this.settings.shrinkDelaySeconds() + "s 后以 " + this.settings.shrinkSpeedLabel() + " 挤压");
      }
      this.ctx.broadcast(this.room, "&7时限 &f" + this.settings.maxSeconds() + "s &7· 最后存活获胜。");
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.beginIntro();
   }

   public void tick() {
      if (!this.begun || this.phase == Phase.ENDED) {
         return;
      }
      this.totalTicks++;
      this.ticksLeft--;
      this.boardTicks++;
      this.enforce();
      this.tickStamina();
      this.tickRip();
      if (this.totalTicks % 20 == 0) {
         this.cleanupStrayTags();
      }
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
      } else if (this.phase == Phase.FIGHT) {
         this.ctx.broadcast(this.room, "&7时间到，平局。");
         this.finish(null);
      }
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.INTRO || !fighter.alive) {
         return true;
      }
      if (source.getEntity() instanceof ServerPlayer) {
         return true;
      }
      this.lastHurtTick.put(player.getUUID(), this.totalTicks);
      return false;
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighter(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) {
         return false;
      }
      if (this.phase == Phase.INTRO || !fighter.alive) {
         this.heal(player);
         return true;
      }
      this.heal(player);
      this.ctx.send(player, "&7撕名牌中：普通伤害不会淘汰，必须被撕掉名牌才会出局。");
      return true;
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = this.fighters.remove(uuid);
      this.ripSessions.remove(uuid);
      this.lastHurtTick.remove(uuid);
      this.removeTag(uuid);
      ServerBossEvent stamina = this.staminaBars.remove(uuid);
      if (stamina != null) stamina.removeAllPlayers();
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
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了撕名牌。");
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

   public boolean tryStartRip(ServerPlayer attacker, ServerPlayer target) {
      if (this.phase != Phase.FIGHT) {
         return false;
      }
      Fighter aFighter = this.fighter(attacker.getUUID());
      Fighter tFighter = this.fighter(target.getUUID());
      if (aFighter == null || tFighter == null || !aFighter.alive || !tFighter.alive) {
         return false;
      }
      if (attacker.getUUID().equals(target.getUUID())) {
         return false;
      }
      if (this.settings.teams() && aFighter.team != 0 && aFighter.team == tFighter.team && !this.settings.friendlyFire()) {
         this.ctx.send(attacker, "&c不能撕队友的名牌。");
         return false;
      }
      if (!this.hasTag(target.getUUID())) {
         this.ctx.send(attacker, "&c该目标没有名牌。");
         return false;
      }
      NameTagWarSettings.RipMode mode = this.ripperMode(attacker);
      if (mode == null) {
         return false;
      }
      if (!this.isBehind(attacker, target, mode)) {
         this.ctx.send(attacker, "&c必须在目标背后才能撕名牌。");
         return false;
      }
      if (attacker.position().distanceToSqr(target.position()) > this.settings.maxDistanceSq()) {
         this.ctx.send(attacker, "&c距离太远。");
         return false;
      }
      this.ripSessions.compute(attacker.getUUID(), (k, existing) -> {
         if (existing != null && existing.target.equals(target.getUUID()) && existing.mode == mode) {
            return existing;
         }
         return new RipSession(target.getUUID(), mode);
      });
      this.ctx.send(attacker, "&e开始撕取 &f" + this.ctx.name(target.getUUID()) + " &e的名牌…");
      this.ctx.send(target, "&c&l⚠ &e" + this.ctx.name(attacker.getUUID()) + " &c正在撕你的名牌！");
      return true;
   }

   public net.minecraft.world.InteractionResult usePowerup(ServerPlayer player, ItemStack stack) {
      if (this.phase != Phase.FIGHT || stack == null || stack.isEmpty()) return net.minecraft.world.InteractionResult.PASS;
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) return net.minecraft.world.InteractionResult.PASS;
      String kind = data.copyTag().getString("ntw_powerup");
      if (kind == null || kind.isEmpty()) return net.minecraft.world.InteractionResult.PASS;
      switch (kind) {
         case "grapple" -> {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0.0, look.z).normalize();
            player.push(horizontal.x * 1.25, 0.48, horizontal.z * 1.25);
            player.hurtMarked = true;
         }
         case "jump" -> player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 12, 1, true, false, true));
         case "speed" -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 1, true, false, true));
         default -> { return net.minecraft.world.InteractionResult.PASS; }
      }
      stack.shrink(1);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.35F);
      return net.minecraft.world.InteractionResult.FAIL;
   }

   private NameTagWarSettings.RipMode ripperMode(ServerPlayer player) {
      NameTagWarSettings.RipMode mainMode = ripModeOf(player.getMainHandItem());
      if (mainMode != null) {
         return mainMode;
      }
      return ripModeOf(player.getOffhandItem());
   }

   private NameTagWarSettings.RipMode ripModeOf(ItemStack stack) {
      if (stack == null || stack.isEmpty() || !stack.is(Items.SHEARS)) {
         return null;
      }
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return this.settings.defaultRipMode();
      }
      String mode = data.copyTag().getString("ntw_rip_mode");
      return mode == null || mode.isEmpty() ? this.settings.defaultRipMode() : NameTagWarSettings.RipMode.fromName(mode);
   }

   private boolean isBehind(ServerPlayer attacker, ServerPlayer target, NameTagWarSettings.RipMode mode) {
      Vec3 targetLook = target.getLookAngle();
      Vec3 toAttacker = attacker.position().subtract(target.position());
      double len = toAttacker.length();
      if (len < 1e-4) {
         return false;
      }
      toAttacker = toAttacker.scale(1.0 / len);
      double dot = targetLook.dot(toAttacker);
      return dot < mode.angleThreshold();
   }

   private void tickRip() {
      if (this.phase != Phase.FIGHT) {
         this.ripSessions.clear();
         return;
      }
      for (Map.Entry<UUID, RipSession> entry : new ArrayList<>(this.ripSessions.entrySet())) {
         UUID attackerId = entry.getKey();
         RipSession session = entry.getValue();
         ServerPlayer attacker = this.ctx.player(attackerId);
         ServerPlayer target = this.ctx.player(session.target);
         Fighter aFighter = this.fighter(attackerId);
         if (attacker == null || target == null || aFighter == null || !aFighter.alive) {
            this.ripSessions.remove(attackerId);
            continue;
         }
         Fighter tFighter = this.fighter(session.target);
         if (tFighter == null || !tFighter.alive || !this.hasTag(session.target)) {
            this.ripSessions.remove(attackerId);
            this.ctx.send(attacker, "&7目标已无名牌，撕取中断。");
            continue;
         }
         NameTagWarSettings.RipMode mode = this.ripperMode(attacker);
         if (mode == null || mode != session.mode) {
            this.ripSessions.remove(attackerId);
            this.ctx.send(attacker, "&7撕取中断：未持有对应剪刀。");
            continue;
         }
         if (attacker.position().distanceToSqr(target.position()) > this.settings.maxDistanceSq()) {
            this.ripSessions.remove(attackerId);
            this.ctx.send(attacker, "&7撕取中断：距离过远。");
            continue;
         }
         if (!this.isBehind(attacker, target, mode)) {
            this.ripSessions.remove(attackerId);
            this.ctx.send(attacker, "&7撕取中断：已脱离背部。");
            continue;
         }
         if (this.settings.interruptOnMove() && attacker.getDeltaMovement().lengthSqr() > 0.02) {
            this.ripSessions.remove(attackerId);
            this.ctx.send(attacker, "&7撕取中断：你移动了。");
            continue;
         }
         if (this.settings.interruptOnDamage()) {
            Integer hurt = this.lastHurtTick.get(attackerId);
            if (hurt != null && this.totalTicks - hurt < 20) {
               this.ripSessions.remove(attackerId);
               this.ctx.send(attacker, "&7撕取中断：你被攻击了。");
               continue;
            }
         }
         session.ticks++;
         this.showRipProgress(attacker, target, session);
         if (session.ticks >= mode.ticks()) {
            this.ripSessions.remove(attackerId);
            this.ripTag(attacker, target);
         }
      }
   }

   private void showRipProgress(ServerPlayer attacker, ServerPlayer target, RipSession session) {
      int total = session.mode.ticks();
      int done = Math.min(total, session.ticks);
      int bars = 20;
      int filled = (int) Math.round(bars * (done / (double) total));
      StringBuilder bar = new StringBuilder();
      for (int i = 0; i < bars; i++) {
         bar.append(i < filled ? "&c|" : "&7-");
      }
      String label = session.mode == NameTagWarSettings.RipMode.FAST ? "&b速撕" : "&e稳撕";
      attacker.displayClientMessage(TextUtil.color(label + " &f" + this.ctx.name(session.target) + " &c[" + bar + "&c]"), true);
      target.displayClientMessage(TextUtil.color("&c&l⚠ &e" + this.ctx.name(attacker.getUUID()) + " &c正在撕你 &c[" + bar + "&c]"), true);
   }

   private void ripTag(ServerPlayer attacker, ServerPlayer target) {
      Fighter tFighter = this.fighter(target.getUUID());
      if (tFighter == null || !tFighter.alive) {
         return;
      }
      this.removeTag(target.getUUID());
      this.ctx.broadcast(this.room, "&c&l" + this.ctx.name(attacker.getUUID()) + " &e撕下了 &f" + this.ctx.name(target.getUUID()) + " &e的名牌！");
      this.eliminate(target, tFighter, "&c名牌被撕");
      ServerPlayer attackerPlayer = this.ctx.player(attacker.getUUID());
      if (attackerPlayer != null) {
         this.heal(attackerPlayer);
      }
   }

   private void beginIntro() {
      this.phase = Phase.INTRO;
      this.setTimer(INTRO_SECONDS * 20);
      this.forEachOnline((player, fighter) -> this.title(player, "&6撕名牌", "&e无敌准备中"));
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.shrinkWaitTicks = this.settings.border() ? this.settings.shrinkDelaySeconds() * 20 : Integer.MAX_VALUE;
      this.setTimer(this.settings.maxSeconds() * 20);
      this.forEachOnline((player, fighter) -> {
         player.removeAllEffects();
         this.title(player, "&c开战", "&7撕下别人的名牌");
      });
      for (int i = 0; i < 3; i++) this.spawnPowerup();
      this.ctx.broadcast(this.room, "&c战斗开始！");
   }

   private void tickFight() {
      if (this.totalTicks % (20 * 15) == 0) this.spawnPowerup();
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

   private void tickStamina() {
      for (Fighter fighter : this.fighters.values()) {
         ServerPlayer player = this.ctx.player(fighter.uuid);
         ServerBossEvent bar = this.staminaBars.get(fighter.uuid);
         if (player == null || bar == null) continue;
         boolean boosting = this.phase == Phase.FIGHT && fighter.alive && player.isShiftKeyDown()
            && fighter.stamina > 0;
         if (boosting) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
            if (horizontal.lengthSqr() > 1.0E-5) {
               horizontal = horizontal.normalize();
               // Shift should feel like an immediate dash, not a slowly building nudge.
               player.setDeltaMovement(player.getDeltaMovement().add(horizontal.x * 0.09, 0.0, horizontal.z * 0.09));
               player.hurtMarked = true;
            }
            // Minecraft uses a zero-based amplifier: 3 is the visible Speed IV effect.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 3, true, false, true));
            fighter.stamina = Math.max(0, fighter.stamina - 2);
         } else {
            fighter.stamina = Math.min(100, fighter.stamina + 1);
         }
         bar.setName(TextUtil.color((fighter.stamina > 25 ? "&a体力 " : "&c体力 ") + "&f" + fighter.stamina + "%"));
         bar.setProgress(fighter.stamina / 100.0F);
      }
   }

   private void spawnPowerup() {
      ServerLevel level = this.level();
      if (level == null) return;
      int border = this.settings.borderSize();
      double x = this.arena.minX(border) + 4 + java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.max(1, border - 8));
      double z = this.arena.minZ(border) + 4 + java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.max(1, border - 8));
      int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(3);
      ItemStack item = switch (roll) {
         case 0 -> powerup(Items.FISHING_ROD, "grapple", "&b勾爪");
         case 1 -> powerup(Items.RABBIT_FOOT, "jump", "&a跳跃提升");
         default -> powerup(Items.SUGAR, "speed", "&e速度提升");
      };
      ItemEntity entity = new ItemEntity(level, x, this.arena.floorY() + 1.0, z, item);
      entity.setPickUpDelay(10);
      level.addFreshEntity(entity);
   }

   private ItemStack powerup(net.minecraft.world.item.Item item, String kind, String name) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString("ntw_powerup", kind)));
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      return stack;
   }

   private void shrinkOnce() {
      int live = this.settings.borderSize() - this.inset * 2;
      if (live <= NameTagWarArena.MIN_LIVE_SIZE) {
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
      int maxY = this.arena.wallTop();
      net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (int y = minY; y <= maxY; y++) {
         for (int x = x0; x <= x1; x++) {
            level.setBlock(pos.set(x, y, z0), NameTagWarArena.WALL, 2);
            level.setBlock(pos.set(x, y, z1), NameTagWarArena.WALL, 2);
         }
         for (int z = z0; z <= z1; z++) {
            level.setBlock(pos.set(x0, y, z), NameTagWarArena.WALL, 2);
            level.setBlock(pos.set(x1, y, z), NameTagWarArena.WALL, 2);
         }
      }
      for (ServerPlayer player : this.alivePlayers()) {
         net.minecraft.core.BlockPos feet = player.blockPosition();
         if (this.arena.isCurrentWall(feet, this.settings.borderSize(), this.inset)
            || this.arena.isCurrentWall(feet.above(), this.settings.borderSize(), this.inset)) {
            Vec3 snap = this.arena.snapInside(player.getX(), player.getY(), player.getZ(),
               this.settings.borderSize(), this.inset);
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
         if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
         }
         if (this.phase == Phase.INTRO) {
            player.invulnerableTime = 20;
         }
         boolean inside = player.serverLevel() == level && this.arena.contains(
            player.getX(), player.getY(), player.getZ(), this.settings.borderSize(), this.inset);
         if (!inside) {
            if (this.phase == Phase.FIGHT) {
               this.eliminate(player, fighter, "&c越界出局");
               continue;
            }
            this.arena.teleport(player, level, this.arena.spawnCenter());
         }
         if (this.phase == Phase.FIGHT && player.getY() < this.arena.basinY() - 1) {
            this.eliminate(player, fighter, "&c掉入虚空");
         }
         this.refreshTagIfMissing(player);
      }
   }

   private void eliminate(ServerPlayer player, Fighter fighter, String title) {
      if (!fighter.alive) {
         this.heal(player);
         return;
      }
      fighter.alive = false;
      this.ripSessions.remove(player.getUUID());
      this.removeTag(player.getUUID());
      this.heal(player);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SPECTATOR);
      ServerLevel level = this.level();
      if (level != null) {
         this.arena.teleport(player, level, this.arena.watch());
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
      this.ctx.broadcast(this.room, "&6&l撕名牌结算");
      if (winner != null) {
         String name = this.ctx.name(winner.uuid);
         if (this.settings.teams() && winner.team > 0) {
            this.ctx.broadcast(this.room, "&a获胜队伍 &e#" + winner.team + " &7（" + name + " 等）");
         } else {
            this.ctx.broadcast(this.room, "&a胜者： &f" + name);
         }
         ServerPlayer player = this.ctx.player(winner.uuid);
         if (player != null) {
            this.title(player, "&6胜利", "&e撕名牌");
         }
      } else {
         this.ctx.broadcast(this.room, "&7没有幸存者。");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.boss.removeAllPlayers();
      for (ServerBossEvent stamina : this.staminaBars.values()) stamina.removeAllPlayers();
      this.staminaBars.clear();
      this.board.removeAll();
      for (UUID uuid : this.seats) {
         this.removeTag(uuid);
      }
      this.cleanupStrayTags();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         if (player != null) {
            this.restore(player);
         }
      }
      this.ctx.nameTagWar().arenas().release(this.arena);
      this.ctx.rooms().onMatchEnded(this.id);
      this.ctx.nameTagWar().remove(this);
   }

   private void spawnTag(ServerPlayer player) {
      if (this.tagEntities.containsKey(player.getUUID())) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
      if (display == null) {
         return;
      }
      ChatFormatting color = this.teamColor(this.fighter(player.getUUID()).team);
      display.setText(TextUtil.color("&l" + color + player.getGameProfile().getName()));
      display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
      display.setBackgroundColor(0x80000000);
      display.setNoGravity(true);
      display.setInvulnerable(true);
      display.setSilent(true);
      display.setCustomNameVisible(false);
      display.addTag("ntw_tag");
      level.addFreshEntity(display);
      this.tagEntities.put(player.getUUID(), display.getUUID());
      NameTagWarManager.registerTagEntity(display.getUUID());
      this.positionTag(player, display);
   }

   private void positionTag(ServerPlayer player, Display.TextDisplay display) {
      if (display == null || display.isRemoved()) {
         return;
      }
      Vec3 look = player.getLookAngle();
      double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
      double dx = hLen > 1e-6 ? look.x / hLen : 0.0;
      double dz = hLen > 1e-6 ? look.z / hLen : 0.0;
      double yOff = this.settings.heightOffset();
      if (player.isShiftKeyDown()) {
         yOff -= this.settings.sneakHeightReduce();
      }
      double hOff = this.settings.horizontalOffset();
      Vec3 base = player.position();
      display.setPos(base.x - dx * hOff, base.y + yOff, base.z - dz * hOff);
      display.setYRot(player.getYRot() + 180.0F);
   }

   private void refreshTagIfMissing(ServerPlayer player) {
      UUID owner = player.getUUID();
      UUID tagId = this.tagEntities.get(owner);
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      if (tagId == null) {
         this.spawnTag(player);
         return;
      }
      Entity existing = level.getEntity(tagId);
      if (existing == null || existing.isRemoved()) {
         this.tagEntities.remove(owner);
         NameTagWarManager.unregisterTagEntity(tagId);
         this.spawnTag(player);
         return;
      }
      if (existing instanceof Display.TextDisplay display && this.phase != Phase.ENDED) {
         this.positionTag(player, display);
      }
   }

   private void removeTag(UUID owner) {
      UUID tagId = this.tagEntities.remove(owner);
      if (tagId == null) {
         return;
      }
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      Entity entity = level.getEntity(tagId);
      if (entity != null) {
         entity.discard();
      }
      NameTagWarManager.unregisterTagEntity(tagId);
   }

   private void cleanupStrayTags() {
      ServerLevel level = this.level();
      if (level == null) {
         return;
      }
      int border = this.settings.borderSize();
      int inset = this.inset;
      AABB box = new AABB(
         this.arena.minX(border) - 4,
         this.arena.basinY() - 4,
         this.arena.minZ(border) - 4,
         this.arena.maxX(border) + 4,
         this.arena.wallTop() + 16,
         this.arena.maxZ(border) + 4);
      java.util.Set<UUID> tracked = new java.util.HashSet<>(this.tagEntities.values());
      for (Entity entity : level.getEntities((Entity) null, box, e -> e.getTags().contains("ntw_tag"))) {
         if (tracked.contains(entity.getUUID())) {
            continue;
         }
         entity.discard();
         NameTagWarManager.unregisterTagEntity(entity.getUUID());
      }
   }

   private void giveKit(ServerPlayer player) {
      NameTagWarSettings.RipMode defaultMode = this.settings.defaultRipMode();
      if (this.settings.giveBothRippers()) {
         player.getInventory().add(ripShears(NameTagWarSettings.RipMode.FAST));
         player.getInventory().add(ripShears(NameTagWarSettings.RipMode.STEADY));
      } else {
         player.getInventory().add(ripShears(defaultMode));
      }
      player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
   }

   private ItemStack ripShears(NameTagWarSettings.RipMode mode) {
      ItemStack stack = new ItemStack(Items.SHEARS);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString("ntw_rip_mode", mode.name())));
      stack.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
      String name = mode == NameTagWarSettings.RipMode.FAST ? "&b速撕剪刀 &7(0.25s)" : "&e稳撕剪刀 &7(0.5s)";
      stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name));
      return stack;
   }

   private ChatFormatting teamColor(int team) {
      int idx = team - 1;
      if (idx < 0 || idx >= TEAM_COLORS.length) {
         return ChatFormatting.WHITE;
      }
      return TEAM_COLORS[idx];
   }

   private void ensureTeam(ServerPlayer player, Fighter fighter) {
      if (!this.settings.teams() || fighter.team <= 0) {
         return;
      }
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = "srntw" + fighter.team;
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
      if (current != null && current.getName().startsWith("srntw")) {
         board.removePlayerFromTeam(player.getScoreboardName(), current);
      }
   }

   private void assignTeams(List<UUID> members) {
      List<UUID> shuffled = new ArrayList<>(members);
      java.util.Collections.shuffle(shuffled, java.util.concurrent.ThreadLocalRandom.current());
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
      ServerBossEvent stamina = this.staminaBars.remove(player.getUUID());
      if (stamina != null) stamina.removeAllPlayers();
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
      if (this.phase == Phase.FIGHT) {
         this.boss.setName(TextUtil.color("&c时限 &f" + Math.max(0, this.ticksLeft / 20) + "s &8| &a存活 "
            + this.aliveFighters().size()));
         this.boss.setProgress(Math.max(0.0F, this.ticksLeft / (float) Math.max(1, this.phaseMaxTicks)));
      }
   }

   private void pushBoard() {
      List<String> lines = new ArrayList<>();
      lines.add("&7存活 &f" + this.aliveFighters().size() + "&7/" + this.seats.size());
      lines.add("&7时限 &e" + Math.max(0, this.ticksLeft / 20) + "s");
      if (this.settings.border()) {
         lines.add("&7边界 &c" + Math.max(NameTagWarArena.MIN_LIVE_SIZE, this.settings.borderSize() - this.inset * 2));
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
      boolean alive = true;
      int stamina = 100;

      Fighter(UUID uuid) {
         this.uuid = uuid;
      }
   }

   private static final class RipSession {
      final UUID target;
      final NameTagWarSettings.RipMode mode;
      int ticks;

      RipSession(UUID target, NameTagWarSettings.RipMode mode) {
         this.target = target;
         this.mode = mode;
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
