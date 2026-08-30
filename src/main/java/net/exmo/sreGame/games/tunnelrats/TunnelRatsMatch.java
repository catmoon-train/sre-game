package net.exmo.sreGame.games.tunnelrats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Runtime implementation of the original data-pack's mine, bed and elimination loop. */
public final class TunnelRatsMatch {
   public enum Phase { INTRO, FIGHT, SETTLE, ENDED }

   private static final int SETTLE_TICKS = 6 * 20;
   private static final int TAG_TICKS = 15 * 20;
   private static final int RED_LEATHER = 0xD64A42;
   private static final int BLUE_LEATHER = 0x3F68D8;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final TunnelRatsArena arena;
   private final TunnelRatsSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final int[] previousActive = new int[3];
   private final int[] lastStandTicks = new int[3];
   private final boolean[] bedAlive = new boolean[3];
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int ticks;
   private boolean started;

   public TunnelRatsMatch(GameContext ctx, GameRoom room, List<UUID> seats, TunnelRatsArena arena) {
      this.ctx = ctx;
      this.room = room;
      this.seats = List.copyOf(seats);
      this.arena = arena;
      this.settings = room.tunnelRatsSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6地道战"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.assignTeams();
   }

   public UUID id() { return this.id; }
   public ServerLevel level() { return this.ctx.tunnelRats().arenas().level(); }

   public void start() {
      ServerLevel level = this.level();
      if (level == null) { this.endImmediately(); return; }
      this.started = true;
      this.bedAlive[1] = this.arena.bedIntact(level, 1);
      this.bedAlive[2] = this.arena.bedIntact(level, 2);
      this.previousActive[1] = this.activeCount(1);
      this.previousActive[2] = this.activeCount(2);
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) continue;
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&6地道战");
         this.boss.addPlayer(player);
         this.ensureTeam(player, fighter);
         player.closeContainer();
         player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent();
         player.removeAllEffects();
         this.heal(player);
         this.giveStarterKit(player, fighter);
         this.teleportSpawn(player, fighter);
         this.title(player, "&6地道战", "&e保护己方床，挖穿矿层");
      }
      this.phase = Phase.INTRO;
      this.ticksLeft = this.settings.countdownTicks();
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l地道战：Tunnel Rats");
      this.ctx.broadcast(this.room, "&7挖掘随机矿层，攻入敌方基地并摧毁床位。");
      this.ctx.broadcast(this.room, "&7己方床存在时，阵亡 &f" + this.settings.respawnSeconds() + " 秒 &7后复活；床毁后死亡即淘汰。");
      this.ctx.broadcast(this.room, "&c红队 &f" + this.teamSize(1) + " 人 &8| &9蓝队 &f" + this.teamSize(2) + " 人");
      this.ctx.broadcast(this.room, "&8&m----------------");
   }

   public void tick() {
      if (!this.started || this.phase == Phase.ENDED) return;
      this.ticks++;
      this.enforcePlayers();
      if (this.phase == Phase.INTRO) {
         this.tickIntro();
      } else if (this.phase == Phase.FIGHT) {
         this.tickFight();
      } else if (this.phase == Phase.SETTLE && --this.ticksLeft <= 0) {
         this.restoreAndClose();
         return;
      }
      if (this.ticks % 10 == 0) this.updateHud();
      if (this.ticks % 20 == 0) this.applyEffects();
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Fighter fighter = this.fighters.get(player.getUUID());
      return fighter != null && !fighter.eliminated && fighter.respawnTicks == 0
         && this.phase == Phase.FIGHT && this.arena.isMineable(pos);
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      return fighter == null || this.phase != Phase.FIGHT || fighter.eliminated || fighter.respawnTicks > 0
         ? InteractionResult.FAIL : InteractionResult.PASS;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase != Phase.FIGHT || fighter.eliminated || fighter.respawnTicks > 0) return InteractionResult.FAIL;
      BlockPos clicked = hit.getBlockPos();
      if (player.level().getBlockState(clicked).getBlock() instanceof BedBlock) return InteractionResult.FAIL;
      if (!player.isShiftKeyDown() && BuildSafety.isWorkstation(player.level().getBlockState(clicked).getBlock())) {
         return this.arena.isMineable(clicked) ? InteractionResult.PASS : InteractionResult.FAIL;
      }
      BlockPos place = clicked.relative(hit.getDirection());
      if (stack.getItem() instanceof BlockItem && !this.arena.isBuildable(place)) return InteractionResult.FAIL;
      return InteractionResult.PASS;
   }

   public boolean handleDamage(ServerPlayer victim, DamageSource source) {
      Fighter target = this.fighters.get(victim.getUUID());
      if (target == null) return false;
      if (this.phase != Phase.FIGHT || target.eliminated || target.respawnTicks > 0) return true;
      Entity sourceEntity = source.getEntity();
      if (sourceEntity instanceof ServerPlayer attacker) {
         Fighter attackerFighter = this.fighters.get(attacker.getUUID());
         if (attackerFighter == null || attackerFighter.eliminated || attackerFighter.respawnTicks > 0) return true;
         if (!this.settings.friendlyFire() && attackerFighter.team == target.team) return true;
         if (!attacker.getUUID().equals(victim.getUUID())) {
            target.lastHit = attacker.getUUID();
            target.lastHitTick = this.ticks;
         }
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) return false;
      this.heal(player);
      if (this.phase != Phase.FIGHT || fighter.eliminated || fighter.respawnTicks > 0) {
         this.teleportSpawn(player, fighter);
         return true;
      }
      if (this.arena.bedIntact(this.level(), fighter.team)) {
         fighter.respawnTicks = this.settings.respawnTicks();
         player.setGameMode(GameType.SPECTATOR);
         this.teleportWatch(player, fighter);
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(fighter.uuid) + " &c阵亡 &7，将在 &f"
            + this.settings.respawnSeconds() + " 秒 &7后于床位复活。" + this.killerSuffix(fighter));
      } else {
         this.eliminate(player, fighter, "&c床已被摧毁，无法复活。");
      }
      return true;
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) this.restore(player); else this.board.remove(uuid);
      if (fighter != null && this.phase != Phase.ENDED) {
         this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了地道战。");
      }
      if (!this.started && this.fighters.isEmpty()) {
         this.endImmediately();
         return;
      }
      this.checkWin();
   }

   public void endNow() {
      if (this.phase == Phase.ENDED) return;
      if (!this.started) {
         this.endImmediately();
         return;
      }
      this.finish(0);
   }

   private void tickIntro() {
      if (this.ticksLeft > 0 && this.ticksLeft % 20 == 0 && this.ticksLeft <= 100) {
         int seconds = Math.max(1, this.ticksLeft / 20);
         this.forEachOnline((player, fighter) -> this.title(player, "&e" + seconds, "&7准备开战"));
      }
      if (--this.ticksLeft <= 0) this.beginFight();
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.ctx.broadcast(this.room, "&c&l开战！ &7现在可以挖掘、合成、摧毁敌方床位并战斗。 ");
      this.forEachOnline((player, fighter) -> {
         player.setGameMode(GameType.SURVIVAL);
         player.invulnerableTime = 60;
         this.title(player, "&c开战", "&f摧毁敌方床位并淘汰所有敌人");
      });
   }

   private void tickFight() {
      this.checkBeds();
      this.tickRespawns();
      this.tickLastStand();
      this.checkWin();
   }

   private void checkBeds() {
      ServerLevel level = this.level();
      for (int team = 1; team <= 2; team++) {
         boolean intact = this.arena.bedIntact(level, team);
         if (this.bedAlive[team] && !intact) {
            this.bedAlive[team] = false;
            String teamName = this.teamName(team);
            this.ctx.broadcast(this.room, this.teamColor(team) + "&l" + teamName + "的床被摧毁！ &7该队成员死亡后将被淘汰。");
            for (UUID uuid : this.seats) {
               Fighter fighter = this.fighters.get(uuid);
               ServerPlayer player = this.ctx.player(uuid);
               if (fighter != null && player != null && fighter.team == team) this.title(player, "&c床已被摧毁", "&7死亡后将无法复活");
            }
         }
      }
   }

   private void tickRespawns() {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter == null || player == null || fighter.eliminated || fighter.respawnTicks <= 0) continue;
         fighter.respawnTicks--;
         int seconds = Math.max(1, (fighter.respawnTicks + 19) / 20);
         player.displayClientMessage(TextUtil.color("&e" + seconds + " 秒后复活"), true);
         if (fighter.respawnTicks > 0) continue;
         if (!this.arena.bedIntact(this.level(), fighter.team)) {
            this.eliminate(player, fighter, "&c床已被摧毁，无法复活。");
            continue;
         }
         player.setGameMode(GameType.SURVIVAL);
         this.heal(player);
         this.giveTeamArmor(player, fighter);
         this.teleportSpawn(player, fighter);
         this.title(player, "&a复活", "&7继续守护床位");
      }
   }

   private void tickLastStand() {
      if (this.settings.lastStandSeconds() <= 0) return;
      for (int team = 1; team <= 2; team++) {
         int active = this.activeCount(team);
         if (this.previousActive[team] > 1 && active == 1 && this.lastStandTicks[team] == 0) {
            this.lastStandTicks[team] = this.settings.lastStandSeconds() * 20;
            this.ctx.broadcast(this.room, this.teamColor(team) + this.teamName(team) + "&e只剩最后一人！将在 &f"
               + this.settings.lastStandSeconds() / 60 + " 分钟 &e后被淘汰。 ");
         }
         this.previousActive[team] = active;
         if (this.lastStandTicks[team] <= 0) continue;
         this.lastStandTicks[team]--;
         if (this.lastStandTicks[team] > 0) continue;
         Fighter last = this.lastActive(team);
         ServerPlayer player = last == null ? null : this.ctx.player(last.uuid);
         if (last != null && player != null) this.eliminate(player, last, "&c孤军时限已结束。");
      }
   }

   private void eliminate(ServerPlayer player, Fighter fighter, String reason) {
      if (fighter.eliminated) return;
      fighter.eliminated = true;
      fighter.respawnTicks = 0;
      player.closeContainer();
      player.getInventory().clearContent();
      player.removeAllEffects();
      player.setGameMode(GameType.SPECTATOR);
      this.teleportWatch(player, fighter);
      this.title(player, "&c淘汰", reason);
      this.ctx.broadcast(this.room, "&c" + this.ctx.name(fighter.uuid) + " &7被淘汰。" + this.killerSuffix(fighter));
      this.checkWin();
   }

   private void enforcePlayers() {
      ServerLevel level = this.level();
      if (level == null) return;
      this.forEachOnline((player, fighter) -> {
         if (fighter.eliminated) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);
            return;
         }
         if (fighter.respawnTicks > 0) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);
            if (player.level() != level || !this.arena.contains(player.getX(), player.getY(), player.getZ())) this.teleportWatch(player, fighter);
            return;
         }
         if (this.phase == Phase.INTRO) {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) player.setGameMode(GameType.ADVENTURE);
            Vec3 spawn = this.arena.spawn(fighter.team, fighter.spawnIndex);
            if (player.level() != level || player.position().distanceToSqr(spawn) > 36) this.teleportSpawn(player, fighter);
         } else if (this.phase == Phase.FIGHT && (player.level() != level || !this.arena.contains(player.getX(), player.getY(), player.getZ()))) {
            this.teleportSpawn(player, fighter);
            this.ctx.send(player, "&e已回到己方基地。");
         }
      });
   }

   private void checkWin() {
      if (!this.started || this.phase == Phase.SETTLE || this.phase == Phase.ENDED) return;
      int red = this.activeCount(1);
      int blue = this.activeCount(2);
      if (red == 0 || blue == 0) this.finish(red == blue ? 0 : red > 0 ? 1 : 2);
   }

   private void finish(int winner) {
      if (this.phase == Phase.SETTLE || this.phase == Phase.ENDED) return;
      this.phase = Phase.SETTLE;
      this.ticksLeft = SETTLE_TICKS;
      this.ctx.broadcast(this.room, "&8&m----------------");
      if (winner == 0) this.ctx.broadcast(this.room, "&6地道战结束 &7— 平局。 ");
      else this.ctx.broadcast(this.room, this.teamColor(winner) + "&l" + this.teamName(winner) + "获胜！");
      this.forEachOnline((player, fighter) -> this.title(player,
         winner != 0 && fighter.team == winner ? "&a胜利" : "&c对局结束", "&7即将返回大厅"));
   }

   private void restoreAndClose() {
      if (this.phase == Phase.ENDED) return;
      this.phase = Phase.ENDED;
      this.forEachOnline((player, fighter) -> this.restore(player));
      this.ctx.tunnelRats().arenas().release(this.arena);
      this.ctx.tunnelRats().remove(this);
      this.ctx.rooms().onMatchEnded(this.id);
   }

   private void endImmediately() {
      this.phase = Phase.ENDED;
      this.ctx.tunnelRats().arenas().release(this.arena);
      this.ctx.tunnelRats().remove(this);
      this.ctx.rooms().onMatchEnded(this.id);
   }

   private void assignTeams() {
      int redIndex = 0;
      int blueIndex = 0;
      for (UUID uuid : this.seats) {
         int team = this.room.duelSettings().teamOf(uuid);
         if (team != 1 && team != 2) team = redIndex <= blueIndex ? 1 : 2;
         Fighter fighter = new Fighter(uuid, team, team == 1 ? redIndex++ : blueIndex++);
         this.fighters.put(uuid, fighter);
      }
   }

   private void giveStarterKit(ServerPlayer player, Fighter fighter) {
      Inventory inventory = player.getInventory();
      inventory.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
      inventory.setItem(1, new ItemStack(Items.WOODEN_AXE));
      inventory.setItem(2, new ItemStack(Items.OAK_LOG, 12));
      inventory.setItem(3, new ItemStack(Items.BREAD, 16));
      inventory.setItem(4, new ItemStack(Items.TORCH, 24));
      this.giveTeamArmor(player, fighter);
   }

   private void giveTeamArmor(ServerPlayer player, Fighter fighter) {
      if (!this.settings.teamArmor()) return;
      int color = fighter.team == 1 ? RED_LEATHER : BLUE_LEATHER;
      this.equipLeather(player, EquipmentSlot.HEAD, Items.LEATHER_HELMET, color);
      this.equipLeather(player, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, color);
      this.equipLeather(player, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, color);
      this.equipLeather(player, EquipmentSlot.FEET, Items.LEATHER_BOOTS, color);
   }

   private void equipLeather(ServerPlayer player, EquipmentSlot slot, net.minecraft.world.item.Item item, int color) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
      stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      player.setItemSlot(slot, stack);
   }

   private void applyEffects() {
      if (this.phase != Phase.FIGHT) return;
      this.forEachOnline((player, fighter) -> {
         if (fighter.eliminated || fighter.respawnTicks > 0) return;
         if (this.settings.nightVision()) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60, 0, true, false));
         if (this.settings.speed()) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
         if (this.settings.haste()) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, 0, true, false));
      });
   }

   private void teleportSpawn(ServerPlayer player, Fighter fighter) {
      ServerLevel level = this.level();
      if (level == null) return;
      Vec3 pos = this.arena.spawn(fighter.team, fighter.spawnIndex);
      player.teleportTo(level, pos.x, pos.y, pos.z, this.arena.spawnYaw(fighter.team), 0.0F);
      player.fallDistance = 0.0F;
      player.invulnerableTime = 60;
   }

   private void teleportWatch(ServerPlayer player, Fighter fighter) {
      ServerLevel level = this.level();
      if (level == null) return;
      Vec3 pos = this.arena.watch(fighter.team);
      player.teleportTo(level, pos.x, pos.y, pos.z, this.arena.spawnYaw(fighter.team), 25.0F);
   }

   private void ensureTeam(ServerPlayer player, Fighter fighter) {
      Scoreboard board = this.ctx.server().getScoreboard();
      String name = fighter.team == 1 ? "srtrR" : "srtrB";
      PlayerTeam team = board.getPlayerTeam(name);
      if (team == null) team = board.addPlayerTeam(name);
      team.setCollisionRule(Team.CollisionRule.NEVER);
      team.setAllowFriendlyFire(this.settings.friendlyFire());
      team.setNameTagVisibility(Team.Visibility.ALWAYS);
      team.setColor(fighter.team == 1 ? ChatFormatting.RED : ChatFormatting.BLUE);
      PlayerTeam existing = board.getPlayersTeam(player.getScoreboardName());
      if (existing != null && existing != team) board.removePlayerFromTeam(player.getScoreboardName(), existing);
      board.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private void clearTeam(ServerPlayer player) {
      Scoreboard board = this.ctx.server().getScoreboard();
      PlayerTeam team = board.getPlayersTeam(player.getScoreboardName());
      if (team != null && team.getName().startsWith("srtr")) board.removePlayerFromTeam(player.getScoreboardName(), team);
   }

   private void updateHud() {
      String phaseText = switch (this.phase) {
         case INTRO -> "&e准备 " + Math.max(0, (this.ticksLeft + 19) / 20) + "s";
         case FIGHT -> "&c战斗中";
         case SETTLE -> "&6结算";
         case ENDED -> "&7结束";
      };
      int lastStand = Math.max(this.lastStandTicks[1], this.lastStandTicks[2]);
      String last = lastStand <= 0 ? "" : " &8| &e孤军 " + (lastStand + 19) / 20 + "s";
      this.boss.setName(TextUtil.color("&6地道战 &8| " + phaseText + " &8| &c" + this.activeCount(1) + " &8: &9" + this.activeCount(2) + last));
      this.boss.setProgress(this.phase == Phase.INTRO
         ? Math.max(0.0F, this.ticksLeft / (float) Math.max(1, this.settings.countdownTicks())) : 1.0F);
      this.forEachOnline((player, fighter) -> this.board.update(player, List.of(
         "&7阶段 " + phaseText,
         "&c红队 &f" + this.activeCount(1) + " &8| &9蓝队 &f" + this.activeCount(2),
         "&c红床 " + (this.arena.bedIntact(this.level(), 1) ? "&a完整" : "&c已毁"),
         "&9蓝床 " + (this.arena.bedIntact(this.level(), 2) ? "&a完整" : "&c已毁"),
         fighter.respawnTicks > 0 ? "&e复活 " + Math.max(1, (fighter.respawnTicks + 19) / 20) + "s" : fighter.eliminated ? "&c已淘汰" : "&a存活"
      )));
   }

   private int activeCount(int team) {
      int count = 0;
      for (Fighter fighter : this.fighters.values()) if (fighter.team == team && !fighter.eliminated) count++;
      return count;
   }

   private Fighter lastActive(int team) {
      for (Fighter fighter : this.fighters.values()) if (fighter.team == team && !fighter.eliminated) return fighter;
      return null;
   }

   private int teamSize(int team) {
      int count = 0;
      for (Fighter fighter : this.fighters.values()) if (fighter.team == team) count++;
      return count;
   }

   private String killerSuffix(Fighter victim) {
      return victim.lastHit != null && this.ticks - victim.lastHitTick <= TAG_TICKS
         ? " &8(归因：" + this.ctx.name(victim.lastHit) + ")" : "";
   }

   private String teamName(int team) { return team == 1 ? "红队" : "蓝队"; }
   private String teamColor(int team) { return team == 1 ? "&c" : "&9"; }

   private void heal(ServerPlayer player) {
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.fallDistance = 0.0F;
      player.setDeltaMovement(Vec3.ZERO);
      player.invulnerableTime = 40;
   }

   private void restore(ServerPlayer player) {
      this.clearTeam(player);
      player.closeContainer();
      player.removeAllEffects();
      this.board.remove(player);
      this.boss.removePlayer(player);
      Saved state = this.saved.get(player.getUUID());
      if (state != null) state.apply(player, this.ctx);
      else this.ctx.rooms().resetLobbyState(player);
   }

   private void title(ServerPlayer player, String title, String subtitle) {
      player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
      player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title)));
      player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle)));
   }

   private void forEachOnline(PlayerAction action) {
      for (UUID uuid : this.seats) {
         Fighter fighter = this.fighters.get(uuid);
         ServerPlayer player = this.ctx.player(uuid);
         if (fighter != null && player != null) action.accept(player, fighter);
      }
   }

   @FunctionalInterface private interface PlayerAction { void accept(ServerPlayer player, Fighter fighter); }

   private static final class Fighter {
      final UUID uuid;
      final int team;
      final int spawnIndex;
      boolean eliminated;
      int respawnTicks;
      UUID lastHit;
      int lastHitTick = Integer.MIN_VALUE / 2;

      Fighter(UUID uuid, int team, int spawnIndex) {
         this.uuid = uuid;
         this.team = team;
         this.spawnIndex = spawnIndex;
      }
   }

   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos,
                        float yaw, float pitch, GameType type, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>();
         Inventory inventory = player.getInventory();
         for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy());
         return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
            player.gameMode.getGameModeForPlayer(), items);
      }

      void apply(ServerPlayer player, GameContext ctx) {
         ServerLevel level = ctx.server().getLevel(this.dimension);
         if (level == null) level = ctx.server().overworld();
         player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
         player.setGameMode(this.type);
         Inventory inventory = player.getInventory();
         inventory.clearContent();
         for (int i = 0; i < Math.min(inventory.getContainerSize(), this.items.size()); i++) {
            inventory.setItem(i, this.items.get(i).copy());
         }
      }
   }
}
