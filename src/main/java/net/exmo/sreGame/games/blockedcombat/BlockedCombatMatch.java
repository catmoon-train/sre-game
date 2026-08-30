package net.exmo.sreGame.games.blockedcombat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

public final class BlockedCombatMatch {
   public enum Phase { INTRO, FIGHT, SETTLE, ENDED }
   private static final int SETTLE_TICKS = 6 * 20;
   private static final int TAG_TICKS = 15 * 20;

   private final UUID id = UUID.randomUUID();
   private final GameContext ctx;
   private final GameRoom room;
   private final List<UUID> seats;
   private final BlockedCombatArena arena;
   private final BlockedCombatSettings settings;
   private final Map<UUID, Fighter> fighters = new ConcurrentHashMap<>();
   private final Map<UUID, Saved> saved = new HashMap<>();
   private final SidebarBoard board;
   private final ServerBossEvent boss;
   private final String teamPrefix;
   private Phase phase = Phase.INTRO;
   private int ticksLeft;
   private int ticks;
   private boolean started;

   public BlockedCombatMatch(GameContext ctx, GameRoom room, List<UUID> seats, BlockedCombatArena arena) {
      this.ctx = ctx; this.room = room; this.seats = List.copyOf(seats); this.arena = arena; this.settings = room.blockedCombatSettings();
      this.board = new SidebarBoard(ctx.server());
      this.boss = new ServerBossEvent(TextUtil.color("&6疯狂惊天矿工团"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
      this.boss.setVisible(true);
      this.teamPrefix = "srbc" + this.id.toString().replace("-", "").substring(0, 8);
      assignTeams();
   }

   public UUID id() { return this.id; }
   public ServerLevel level() { return this.ctx.blockedCombat().arenas().level(); }

   public void start() {
      this.started = true;
      ServerLevel level = level();
      for (UUID uuid : this.seats) {
         ServerPlayer player = this.ctx.player(uuid);
         Fighter fighter = this.fighters.get(uuid);
         if (player == null || fighter == null) continue;
         this.saved.put(uuid, Saved.capture(player));
         this.board.create(player, "&6矿工团");
         this.boss.addPlayer(player);
         player.closeContainer(); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); heal(player);
         giveStarterKit(player);
         this.applyNametag(player, fighter);
         this.dyeArmor(player, fighter.color());
         teleportSpawn(player, fighter);
         title(player, "&6疯狂惊天矿工团", "&e挖掘、发展并击败敌队");
      }
      this.ctx.broadcast(this.room, "&8&m----------------");
      this.ctx.broadcast(this.room, "&6&l疯狂惊天矿工团");
      this.ctx.broadcast(this.room, "&7随机方块矿坑已生成：&f" + this.settings.arenaSize() + "×" + this.settings.arenaSize());
      this.ctx.broadcast(this.room, "&7每队 &f" + this.settings.teamSize() + " &7人，死亡上限 &f" + this.settings.deathLimit() + " &7次。");
      this.ctx.broadcast(this.room, teamLineup());
      this.ctx.broadcast(this.room, "&7最后仍有成员存活的队伍获胜；越界会回到出生点。&8&m----------------");
      this.phase = Phase.INTRO; this.ticksLeft = this.settings.prepareTicks();
      this.checkWin();
   }

   public void tick() {
      if (!this.started || this.phase == Phase.ENDED) return;
      this.ticks++; this.ticksLeft--;
      if (this.phase == Phase.INTRO && this.ticksLeft <= 0) beginFight();
      else if (this.phase == Phase.SETTLE && this.ticksLeft <= 0) restoreAndClose();
      enforcePlayers();
      if (this.ticks % 10 == 0) updateHud();
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      Fighter fighter = this.fighters.get(player.getUUID());
      return fighter != null && fighter.alive && this.phase == Phase.FIGHT && this.arena.isMineable(pos);
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      return fighter == null || !fighter.alive || this.phase != Phase.FIGHT ? InteractionResult.FAIL : InteractionResult.PASS;
   }

   public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || !fighter.alive || this.phase != Phase.FIGHT) return InteractionResult.FAIL;
      if (stack.getItem() instanceof BlockItem && !this.arena.isBuildable(hit.getBlockPos().relative(hit.getDirection()))) return InteractionResult.FAIL;
      return InteractionResult.PASS;
   }

   public boolean handleDamage(ServerPlayer victim, DamageSource source) {
      Fighter target = this.fighters.get(victim.getUUID());
      if (target == null) return false;
      if (this.phase != Phase.FIGHT || !target.alive) return true;
      Entity sourceEntity = source.getEntity();
      if (sourceEntity instanceof ServerPlayer attacker) {
         Fighter killer = this.fighters.get(attacker.getUUID());
         if (killer != null) {
            if (!this.settings.friendlyFire() && killer.team == target.team) return true;
            if (!attacker.getUUID().equals(victim.getUUID())) { target.lastHit = attacker.getUUID(); target.lastHitTick = this.ticks; }
         }
      }
      return false;
   }

   public boolean handleDeath(ServerPlayer player, DamageSource source) {
      Fighter fighter = this.fighters.get(player.getUUID());
      if (fighter == null || this.phase == Phase.ENDED) return false;
      heal(player);
      if (this.phase != Phase.FIGHT || !fighter.alive) { teleportSpawn(player, fighter); return true; }
      fighter.deaths++;
      player.drop(new ItemStack(Items.GOLDEN_APPLE), true, false);
      if (fighter.deaths >= this.settings.deathLimit()) {
         fighter.alive = false; player.getInventory().clearContent(); player.setGameMode(GameType.SPECTATOR);
         Vec3 watch = new Vec3((this.arena.minX() + this.arena.maxX()) / 2.0 + 0.5, this.arena.topY() + 12.0,
            (this.arena.minZ() + this.arena.maxZ()) / 2.0 + 0.5);
         player.teleportTo(level(), watch.x, watch.y, watch.z, 0.0F, 45.0F);
         title(player, "&c出局", "&7死亡次数已达上限");
         this.ctx.broadcast(this.room, fighter.color().code() + player.getGameProfile().getName() + " &7已被淘汰。" + killerSuffix(fighter));
         checkWin();
      } else {
         teleportSpawn(player, fighter);
         title(player, "&c阵亡", "&7死亡 " + fighter.deaths + "/" + this.settings.deathLimit());
         this.ctx.broadcast(this.room, fighter.color().code() + player.getGameProfile().getName() + " &7阵亡（" + fighter.deaths + "/" + this.settings.deathLimit() + "）。" + killerSuffix(fighter));
      }
      return true;
   }

   public void onLeave(UUID uuid) {
      Fighter fighter = this.fighters.remove(uuid);
      ServerPlayer player = this.ctx.player(uuid);
      if (player != null) restore(player); else this.board.remove(uuid);
      if (fighter != null && this.phase != Phase.ENDED) this.ctx.broadcast(this.room, "&7" + this.ctx.name(uuid) + " 离开了矿工团对局。");
      if (!this.started && this.fighters.isEmpty()) { this.endNow(); return; }
      checkWin();
   }

   public void endNow() {
      if (this.phase == Phase.ENDED) return;
      if (!this.started) {
         this.phase = Phase.ENDED;
         this.ctx.blockedCombat().arenas().release(this.arena);
         this.ctx.blockedCombat().remove(this);
         this.ctx.rooms().onMatchEnded(this.id);
         return;
      }
      finish(null);
   }

   private void beginFight() {
      this.phase = Phase.FIGHT;
      this.ctx.broadcast(this.room, "&c&l开战！ &7现在可以挖掘、合成和战斗。 ");
      forEachOnline((player, fighter) -> title(player, "&c开战", "&f最后存活的队伍获胜"));
   }

   private void enforcePlayers() {
      ServerLevel level = level(); if (level == null) return;
      forEachOnline((player, fighter) -> {
         if (!fighter.alive) { if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR); return; }
         if (this.phase == Phase.INTRO) {
            Vec3 spawn = this.arena.spawn(fighter.team, fighter.slot, this.settings.spawnSpread());
            if (player.level() != level || player.position().distanceToSqr(spawn) > 25) teleportSpawn(player, fighter);
            player.fallDistance = 0.0F;
         } else if (this.phase == Phase.FIGHT && (player.level() != level || !this.arena.contains(player.getX(), player.getY(), player.getZ()))) {
            teleportSpawn(player, fighter);
            this.ctx.send(player, "&e已回到队伍出生点。");
         }
      });
   }

   private void checkWin() {
      if (this.phase == Phase.ENDED || this.phase == Phase.SETTLE || !this.started) return;
      Set<Integer> teams = new HashSet<>();
      for (Fighter fighter : this.fighters.values()) if (fighter.alive && this.ctx.player(fighter.uuid) != null) teams.add(fighter.team);
      if (teams.size() <= 1) finish(teams.isEmpty() ? null : teams.iterator().next());
   }

   private void finish(Integer winningTeam) {
      if (this.phase == Phase.SETTLE || this.phase == Phase.ENDED) return;
      this.phase = Phase.SETTLE; this.ticksLeft = SETTLE_TICKS;
      this.ctx.broadcast(this.room, "&8&m----------------");
      if (winningTeam == null) this.ctx.broadcast(this.room, "&6疯狂惊天矿工团结束 &7— 无队伍存活，平局。");
      else this.ctx.broadcast(this.room, "&6疯狂惊天矿工团结束 &a— " + BlockedCombatColor.of(winningTeam).display() + " &a获胜！");
      forEachOnline((player, fighter) -> title(player, winningTeam != null && fighter.team == winningTeam ? "&a胜利" : "&c对局结束", "&7即将返回大厅"));
   }

   private void restoreAndClose() {
      if (this.phase == Phase.ENDED) return;
      this.phase = Phase.ENDED;
      forEachOnline((player, fighter) -> restore(player));
      this.ctx.blockedCombat().arenas().release(this.arena);
      this.ctx.blockedCombat().remove(this);
      this.ctx.rooms().onMatchEnded(this.id);
   }

   private void assignTeams() {
      int teamSize = this.settings.teamSize();
      for (int i = 0; i < this.seats.size(); i++) this.fighters.put(this.seats.get(i), new Fighter(this.seats.get(i), i / teamSize, i % teamSize));
   }
   private String teamLineup() {
      int teams = 1;
      for (Fighter fighter : this.fighters.values()) teams = Math.max(teams, fighter.team + 1);
      StringBuilder sb = new StringBuilder("&7队伍：");
      for (int i = 0; i < teams; i++) {
         if (i > 0) sb.append(" &8| ");
         sb.append(BlockedCombatColor.of(i).display());
      }
      return sb.toString();
   }
   private String aliveTeamsLine() {
      Map<Integer, Integer> counts = new TreeMap<>();
      for (Fighter fighter : this.fighters.values()) if (fighter.alive) counts.merge(fighter.team, 1, Integer::sum);
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
         if (sb.length() > 0) sb.append(" &8| ");
         sb.append(BlockedCombatColor.of(entry.getKey()).display()).append("&7 ").append(entry.getValue());
      }
      return sb.length() == 0 ? "&7无" : sb.toString();
   }
   private void applyNametag(ServerPlayer player, Fighter fighter) {
      ServerScoreboard scoreboard = this.ctx.server().getScoreboard();
      String name = this.teamPrefix + fighter.team;
      PlayerTeam team = scoreboard.getPlayerTeam(name);
      if (team == null) {
         team = scoreboard.addPlayerTeam(name);
         team.setColor(fighter.color().formatting());
         team.setCollisionRule(Team.CollisionRule.PUSH_OWN_TEAM);
      }
      PlayerTeam existing = scoreboard.getPlayersTeam(player.getScoreboardName());
      if (existing != null) scoreboard.removePlayerFromTeam(player.getScoreboardName(), existing);
      scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
   }
   private void clearNametag(ServerPlayer player) {
      ServerScoreboard scoreboard = this.ctx.server().getScoreboard();
      PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
      if (current != null && current.getName().startsWith("srbc")) scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
   }
   private void dyeArmor(ServerPlayer player, BlockedCombatColor color) {
      ItemStack[] pieces = {
         new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
         new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS)
      };
      DyedItemColor dye = new DyedItemColor(color.rgb(), true);
      for (ItemStack piece : pieces) {
         piece.set(DataComponents.DYED_COLOR, dye);
         piece.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
      }
      player.setItemSlot(EquipmentSlot.HEAD, pieces[0]);
      player.setItemSlot(EquipmentSlot.CHEST, pieces[1]);
      player.setItemSlot(EquipmentSlot.LEGS, pieces[2]);
      player.setItemSlot(EquipmentSlot.FEET, pieces[3]);
   }
   private void teleportSpawn(ServerPlayer player, Fighter fighter) {
      ServerLevel level = level(); if (level == null) return;
      Vec3 spawn = this.arena.spawn(fighter.team, fighter.slot, this.settings.spawnSpread());
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, 0.0F, 0.0F); player.fallDistance = 0.0F; player.invulnerableTime = 60;
   }
   private void giveStarterKit(ServerPlayer player) {
      Inventory inventory = player.getInventory();
      inventory.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
      inventory.setItem(1, new ItemStack(Items.OAK_LOG, this.settings.richStarterKit() ? 12 : 4));
      inventory.setItem(2, new ItemStack(Items.BREAD, this.settings.richStarterKit() ? 16 : 8));
      if (this.settings.richStarterKit()) inventory.setItem(3, new ItemStack(Items.STONE_AXE));
   }
   private void updateHud() {
      int alive = 0; Set<Integer> teams = new HashSet<>();
      for (Fighter fighter : this.fighters.values()) if (fighter.alive) { alive++; teams.add(fighter.team); }
      String timer = this.phase == Phase.INTRO ? "&e准备 " + Math.max(0, (this.ticksLeft + 19) / 20) + "s" : this.phase == Phase.SETTLE ? "&6结算" : "&c战斗中";
      this.boss.setName(TextUtil.color("&6矿工团 &8| " + timer + " &8| &b存活 " + alive + " &8| " + aliveTeamsLine()));
      this.boss.setProgress(this.phase == Phase.INTRO ? Math.max(0F, this.ticksLeft / (float) this.settings.prepareTicks()) : Math.max(0.05F, teams.size() / 4F));
      forEachOnline((player, fighter) -> this.board.update(player, List.of(
         "&7阶段 " + timer, "&7队伍 " + fighter.color().display(), "&7死亡 &f" + fighter.deaths + "&7/" + this.settings.deathLimit(), "&7存活队伍 &f" + teams.size()
      )));
   }
   private String killerSuffix(Fighter victim) {
      return victim.lastHit != null && this.ticks - victim.lastHitTick <= TAG_TICKS ? " &8(归因：" + this.ctx.name(victim.lastHit) + ")" : "";
   }
   private void heal(ServerPlayer player) { player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20.0F); player.removeAllEffects(); player.fallDistance = 0.0F; player.invulnerableTime = 40; }
   private void restore(ServerPlayer player) {
      this.board.remove(player); this.boss.removePlayer(player); this.clearNametag(player); player.closeContainer(); player.removeAllEffects();
      Saved state = this.saved.get(player.getUUID()); if (state != null) state.apply(player, this.ctx); else this.ctx.rooms().resetLobbyState(player);
   }
   private void title(ServerPlayer player, String title, String subtitle) { player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10)); player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.color(title))); player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.color(subtitle))); }
   private void forEachOnline(PlayerAction action) { for (UUID uuid : this.seats) { Fighter fighter = this.fighters.get(uuid); ServerPlayer player = this.ctx.player(uuid); if (fighter != null && player != null) action.accept(player, fighter); } }
   @FunctionalInterface private interface PlayerAction { void accept(ServerPlayer player, Fighter fighter); }
   private static final class Fighter { final UUID uuid; final int team; final int slot; int deaths; boolean alive = true; UUID lastHit; int lastHitTick = Integer.MIN_VALUE / 2; Fighter(UUID uuid, int team, int slot) { this.uuid = uuid; this.team = team; this.slot = slot; } BlockedCombatColor color() { return BlockedCombatColor.of(this.team); } }
   private record Saved(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 pos, float yaw, float pitch, GameType type, List<ItemStack> items) {
      static Saved capture(ServerPlayer player) { List<ItemStack> items = new ArrayList<>(); Inventory inventory = player.getInventory(); for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy()); return new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(), items); }
      void apply(ServerPlayer player, GameContext ctx) { ServerLevel level = ctx.server().getLevel(this.dimension); if (level == null) level = ctx.server().overworld(); player.teleportTo(level, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch); player.setGameMode(this.type); Inventory inventory = player.getInventory(); inventory.clearContent(); for (int i = 0; i < Math.min(inventory.getContainerSize(), this.items.size()); i++) inventory.setItem(i, this.items.get(i).copy()); }
   }
}
