package net.exmo.sreGame.games.partygames.team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.partygames.MapTemplate;
import net.exmo.sreGame.games.partygames.PartyArena;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.PartySession;
import net.exmo.sreGame.games.partygames.api.PartyColor;
import net.exmo.sreGame.games.partygames.api.PartyGameAction;
import net.exmo.sreGame.games.partygames.api.PartyGameController;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.exmo.sreGame.util.SidebarBoard;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

/** Lifecycle shell for one room-isolated 201-214 team match. */
public final class TeamPartyMatch implements PartySession {
   private enum Phase { PREPARE, RUNNING, ENDED }
   private final UUID id = UUID.randomUUID();
   private final GameContext game;
   private final GameRoom room;
   private final PartyGameType type;
   private final MapTemplate template;
   private final PartyArena arena;
   private final List<UUID> seats;
   private final Map<UUID, Snapshot> snapshots = new HashMap<>();
   private final SidebarBoard board;
   private final PartyGameController controller;
   private final TeamPartyMatchContext context;
   private final List<PlayerTeam> temporaryTeams = new ArrayList<>();
   private Phase phase = Phase.PREPARE;
   private int prepareTicks = 100;
   private int hudTicks;

   public TeamPartyMatch(GameContext game, GameRoom room, PartyGameType type, MapTemplate template, PartyArena arena) {
      this.game = game; this.room = room; this.type = type; this.template = template; this.arena = arena; this.seats = List.copyOf(room.members());
      this.board = new SidebarBoard(game.server()); this.controller = TeamPartyGames.create(type);
      this.context = new TeamPartyMatchContext(game, room, arena, id, seats, template.seed(), this::finish);
      for (UUID seat : seats) { ServerPlayer player = game.player(seat); if (player != null) snapshots.put(seat, Snapshot.capture(player)); }
   }

   @Override public UUID id() { return id; }
   @Override public PartyGameType type() { return type; }
   @Override public PartyArena arena() { return arena; }

   @Override public void start() {
      try { startChecked(); }
      catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} failed to initialize in match {}", type.id(), id, error); finish(null, "游戏初始化异常，已安全恢复玩家"); }
   }

   private void startChecked() {
      if (seats.size() < 2 || context.level() == null || seats.stream().anyMatch(uuid -> game.player(uuid) == null)) { finish(null, "玩家不足，比赛取消"); return; }
      controller.prepare(context);
      for (UUID uuid : seats) {
         ServerPlayer player = game.player(uuid); if (player == null) continue;
         snapshots.putIfAbsent(uuid, Snapshot.capture(player)); player.closeContainer(); player.setGameMode(GameType.ADVENTURE);
         player.getInventory().clearContent(); player.removeAllEffects(); player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
         int team = context.team(uuid); Vec3 spawn = context.anchor(team == 1 ? "blue_spawn" : "red_spawn", team == 1 ? 10.5 : 85.5, 3, 48.5);
         player.teleportTo(context.level(), spawn.x, spawn.y, spawn.z, team == 1 ? -90 : 90, 0);
         applyColor(player, PartyColor.ofTeam(team), team); applyColorArmor(player, PartyColor.ofTeam(team));
         board.create(player, "&6" + type.displayName());
      }
      context.broadcast("&8&m--------------------------------");
      context.broadcast("&6&l" + type.displayName() + " &8| &f团队规则重制");
      for (String rule : controller.definition().rules()) context.broadcast("&7• " + rule);
      context.broadcast("&8&m--------------------------------"); showCountdown(5); pushHud();
   }

   @Override public void tick() {
      if (phase == Phase.ENDED) return;
      if (phase == Phase.PREPARE) {
         if (prepareTicks > 0 && prepareTicks % 20 == 0) showCountdown(prepareTicks / 20);
         if (--prepareTicks <= 0) {
            phase = Phase.RUNNING;
            try { controller.start(); context.broadcast("&a&l开始！"); context.sound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1, 1.3F); }
            catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} could not start in match {}", type.id(), id, error); finish(null, "游戏启动异常，已安全恢复玩家"); }
         }
         return;
      }
      context.advanceTick();
      try { controller.tick(); }
      catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} tick failed in match {}", type.id(), id, error); finish(null, "游戏运行异常，已安全恢复玩家"); return; }
      if (++hudTicks >= 10) { hudTicks = 0; pushHud(); }
   }

   private void showCountdown(int seconds) { context.forEachPlayer(player -> context.title(player, "&e" + seconds, "&7" + type.displayName())); }
   private void pushHud() {
      for (UUID uuid : seats) {
         ServerPlayer player = game.player(uuid); if (player == null) continue;
         int own = context.team(uuid), other = own == 1 ? 2 : 1;
         board.update(player, List.of("&7场景 &f" + template.id(), "&7队伍 " + colorCode(PartyColor.ofTeam(own)) + PartyColor.ofTeam(own).display(),
            "&7本队 &f" + context.teamScore(own), "&7对方 &f" + context.teamScore(other), "&7存活 &f" + context.livingCount(own) + "&7/&f" + context.teamMembers(own).size(),
            "&7阶段 &e" + (phase == Phase.PREPARE ? "准备" : "进行中")));
      }
   }
   private String colorCode(PartyColor color) { return color == PartyColor.BLUE ? "&9" : "&c"; }
   private void applyColor(ServerPlayer player, PartyColor color, int team) {
      var scoreboard = game.server().getScoreboard(); String name = "sre_mp2_" + id.toString().substring(0, 6) + "_" + team;
      PlayerTeam scoreboardTeam = scoreboard.getPlayerTeam(name);
      if (scoreboardTeam == null) { scoreboardTeam = scoreboard.addPlayerTeam(name); temporaryTeams.add(scoreboardTeam); }
      scoreboardTeam.setColor(color.formatting()); scoreboardTeam.setCollisionRule(Team.CollisionRule.NEVER);
      scoreboardTeam.setPlayerPrefix(TextUtil.color(color == PartyColor.BLUE ? "&9[蓝方] " : "&c[红方] "));
      scoreboard.addPlayerToTeam(player.getScoreboardName(), scoreboardTeam);
   }
   private void applyColorArmor(ServerPlayer player, PartyColor color) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack armor = new ItemStack(slot == EquipmentSlot.HEAD ? Items.LEATHER_HELMET : slot == EquipmentSlot.CHEST ? Items.LEATHER_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.LEATHER_LEGGINGS : Items.LEATHER_BOOTS);
         armor.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(color.rgb(), true)); player.setItemSlot(slot, armor);
      }
   }

   @Override public boolean handleDamage(ServerPlayer player, DamageSource source) { return contains(player) && (phase != Phase.RUNNING || safe(() -> controller.damage(player, source))); }
   @Override public boolean handleDeath(ServerPlayer player) { return contains(player) && (phase != Phase.RUNNING || safe(() -> controller.death(player))); }
   @Override public boolean handleAttack(ServerPlayer player, Entity target) { return contains(player) && safe(() -> controller.action(player, PartyGameAction.entity(PartyGameAction.Type.ATTACK_ENTITY, target))); }
   @Override public boolean handleMobDamage(Entity entity, DamageSource source) { return phase == Phase.RUNNING && safe(() -> controller.mobDamage(entity, source)); }
   @Override public boolean handleMobDeath(Entity entity, DamageSource source) { return phase == Phase.RUNNING && safe(() -> controller.mobDeath(entity, source)); }
   @Override public InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack) { return contains(player) && safe(() -> controller.action(player, PartyGameAction.block(PartyGameAction.Type.USE_BLOCK, hit.getBlockPos(), hit.getDirection(), stack))) ? InteractionResult.FAIL : InteractionResult.PASS; }
   @Override public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) { return contains(player) && safe(() -> controller.action(player, PartyGameAction.item(PartyGameAction.Type.USE_ITEM, stack))) ? InteractionResult.FAIL : InteractionResult.PASS; }
   @Override public InteractionResult handleUseEntity(ServerPlayer player, Entity entity) { return contains(player) && safe(() -> controller.action(player, PartyGameAction.entity(PartyGameAction.Type.USE_ENTITY, entity))) ? InteractionResult.FAIL : InteractionResult.PASS; }
   @Override public boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state) { return contains(player) && phase == Phase.RUNNING && safe(() -> controller.breakBlock(player, pos, state)); }
   @Override public void handleLeftClick(ServerPlayer player) { if (contains(player) && phase == Phase.RUNNING) safe(() -> controller.action(player, PartyGameAction.simple(PartyGameAction.Type.LEFT_CLICK))); }
   @Override public boolean handleJump(ServerPlayer player) { if (!contains(player) || phase != Phase.RUNNING) return false; safe(() -> controller.action(player, PartyGameAction.simple(PartyGameAction.Type.JUMP))); return safe(() -> controller.cancelJump(player)); }
   @Override public void handleSneak(ServerPlayer player, boolean sneaking) { if (contains(player) && phase == Phase.RUNNING) safe(() -> controller.action(player, PartyGameAction.sneak(sneaking))); }
   @Override public void handleHotbar(ServerPlayer player, int previousSlot, int newSlot) { if (!contains(player) || phase != Phase.RUNNING) return; int delta = newSlot - previousSlot; if (delta > 4) delta -= 9; if (delta < -4) delta += 9; int amount = delta; safe(() -> controller.action(player, PartyGameAction.hotbar(amount))); }
   @Override public void handleDrop(ServerPlayer player, ItemStack stack) { if (contains(player) && phase == Phase.RUNNING) safe(() -> controller.action(player, PartyGameAction.item(PartyGameAction.Type.DROP_ITEM, stack))); }

   private boolean safe(BooleanSupplier call) {
      try { return call.getAsBoolean(); }
      catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} input failed in match {}", type.id(), id, error); finish(null, "游戏输入处理异常，已安全恢复玩家"); return true; }
   }
   private boolean contains(ServerPlayer player) { return player != null && seats.contains(player.getUUID()) && phase != Phase.ENDED; }

   @Override public void onLeave(UUID uuid) {
      if (phase == Phase.ENDED || !seats.contains(uuid)) return;
      try { controller.leave(uuid); } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} leave handler failed for {} in match {}", type.id(), uuid, id, error); }
      if (phase == Phase.RUNNING) context.winTeam(context.team(uuid) == 1 ? 2 : 1, context.color(uuid).display() + "离线弃权"); else finish(null, "准备阶段玩家离线");
   }
   @Override public void endNow() { finish(null, "比赛被管理员终止"); }

   private void finish(UUID winner, String reason) {
      if (phase == Phase.ENDED) return; phase = Phase.ENDED;
      if (winner == null) context.broadcast("&e" + type.displayName() + "结束：&f" + reason);
      else context.broadcast("&6" + type.displayName() + "结束：" + colorCode(context.color(winner)) + context.color(winner).display() + " &f获胜 &8- &7" + reason);
      context.sound(winner == null ? net.minecraft.sounds.SoundEvents.VILLAGER_NO : net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.9F, winner == null ? 0.9F : 1.2F);
      try { controller.close(); } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} cleanup failed in match {}", type.id(), id, error); }
      try { context.close(); } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} context cleanup failed in match {}", type.id(), id, error); }
      try { board.removeAll(); } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} HUD cleanup failed in match {}", type.id(), id, error); }
      for (UUID uuid : seats) {
         try {
            ServerPlayer player = game.player(uuid);
            if (player != null) restore(player);
            else { Snapshot snapshot = snapshots.remove(uuid); if (snapshot != null) game.partyGames().deferRestore(uuid, joined -> snapshot.apply(joined, game)); }
         } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} could not restore player {} in match {}", type.id(), uuid, id, error); }
      }
      try { var scoreboard = game.server().getScoreboard(); for (PlayerTeam team : temporaryTeams) if (scoreboard.getPlayerTeam(team.getName()) != null) scoreboard.removePlayerTeam(team); }
      catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} team cleanup failed in match {}", type.id(), id, error); }
      temporaryTeams.clear();
      try { game.partyGames().arenas().release(arena); } catch (RuntimeException error) { SreGame.LOGGER.error("Team party game {} arena release failed in match {}", type.id(), id, error); }
      room.setState(RoomState.WAITING); game.rooms().onMatchEnded(id); game.partyGames().remove(this);
   }
   private void restore(ServerPlayer player) { board.remove(player); Snapshot snapshot = snapshots.remove(player.getUUID()); if (snapshot != null) snapshot.apply(player, game); }

   private record Snapshot(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch, GameType gameType, List<ItemStack> items, int selectedSlot,
                           float health, int food, float saturation, int level, int totalXp, float xpProgress, List<MobEffectInstance> effects, String team) {
      static Snapshot capture(ServerPlayer player) {
         List<ItemStack> items = new ArrayList<>(); Inventory inventory = player.getInventory(); for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy());
         List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
         return new Snapshot(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(), items, inventory.selected,
            player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(), player.experienceLevel, player.totalExperience, player.experienceProgress,
            effects, player.getTeam() == null ? null : player.getTeam().getName());
      }
      void apply(ServerPlayer player, GameContext game) {
         ServerLevel level = game.server().getLevel(dimension); if (level == null) level = game.server().overworld(); player.teleportTo(level, position.x, position.y, position.z, yaw, pitch); player.setGameMode(gameType);
         Inventory inventory = player.getInventory(); inventory.clearContent(); for (int i = 0; i < Math.min(items.size(), inventory.getContainerSize()); i++) inventory.setItem(i, items.get(i).copy()); inventory.selected = Math.max(0, Math.min(8, selectedSlot));
         player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(new MobEffectInstance(effect)); player.setHealth(Math.min(health, player.getMaxHealth())); player.getFoodData().setFoodLevel(food); player.getFoodData().setSaturation(saturation);
         player.experienceLevel = this.level; player.totalExperience = totalXp; player.experienceProgress = xpProgress;
         var scoreboard = game.server().getScoreboard(); scoreboard.removePlayerFromTeam(player.getScoreboardName()); if (team != null) { PlayerTeam old = scoreboard.getPlayerTeam(team); if (old != null) scoreboard.addPlayerToTeam(player.getScoreboardName(), old); }
      }
   }
}
