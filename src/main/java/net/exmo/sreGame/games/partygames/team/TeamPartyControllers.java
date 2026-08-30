package net.exmo.sreGame.games.partygames.team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.api.PartyColor;
import net.exmo.sreGame.games.partygames.api.PartyGameAction;
import net.exmo.sreGame.games.partygames.api.PartyGameController;
import net.exmo.sreGame.games.partygames.api.PartyGameDefinition;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Stateful implementations for the 201-214 team games. */
final class TeamPartyControllers {
   private TeamPartyControllers() { }

   static PartyGameController create(PartyGameDefinition definition) {
      if (definition == null) throw new IllegalArgumentException("Missing team party definition");
      return switch (definition.type()) {
         case PRISON_PALS -> new PrisonPals(definition);
         case RPSC -> new Rpsc(definition);
         case TANKS -> new Tanks(definition);
         case CAPTURE_THE_FLAG -> new CaptureFlag(definition);
         case MINE_YOUR_BUSINESS -> new MineBusiness(definition);
         case TEAM_HOCKEY -> new TeamHockey(definition);
         case MAZE_NAVIGATOR -> new MazeNavigator(definition);
         case BOMBS_AWAY -> new BombsAway(definition);
         case LABYRINTH -> new Labyrinth(definition);
         case SNOW_WARS -> new SnowWars(definition);
         case SPACE_JUMPERS -> new SpaceJumpers(definition);
         case BOOM_CARTS -> new BoomCarts(definition);
         case WHAT_THE_CLUCK -> new WhatTheCluck(definition);
         case RECRUITMENT_ROYALE -> new RecruitmentRoyale(definition);
         default -> throw new IllegalArgumentException("Not a 201-214 team game: " + definition.type());
      };
   }

   private abstract static class Base implements PartyGameController {
      protected final PartyGameDefinition definition;
      protected TeamPartyMatchContext c;
      protected int remaining;
      Base(PartyGameDefinition definition) { this.definition = definition; }
      @Override public PartyGameDefinition definition() { return definition; }
      @Override public void prepare(TeamPartyMatchContext context) { c = context; remaining = definition.fixedDurationTicks(); }
      protected ServerPlayer p(UUID id) { return c.player(id); }
      protected Vec3 spawn(int team) { return c.anchor(team == 1 ? "blue_spawn" : "red_spawn", team == 1 ? 10.5 : 85.5, 3, 48.5); }
      protected Vec3 anchor(String name, int team, double x, double y, double z) { return c.anchor(name, x, y, z); }
      protected boolean clock() { if (remaining <= 0) return false; if (--remaining > 0) return false; timeout(); return true; }
      protected void timeout() { compareTeams("时间到"); }
      protected void compareTeams(String reason) {
         int a = c.teamScore(1), b = c.teamScore(2);
         if (a == b) c.draw(reason + "，蓝方和红方平局"); else c.winTeam(a > b ? 1 : 2, reason);
      }
      protected void teleport(ServerPlayer player, Vec3 pos, float yaw) { if (player != null) player.teleportTo(c.level(), pos.x, pos.y, pos.z, yaw, 0); }
      protected void survival(ServerPlayer player) { if (player != null) player.setGameMode(GameType.SURVIVAL); }
      protected void give(ServerPlayer player, ItemStack stack, int slot) { if (player != null) player.getInventory().setItem(slot, stack); }
      protected ItemStack unbreakable(net.minecraft.world.item.Item item, int count) { ItemStack stack = new ItemStack(item, count); stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new Unbreakable(true)); return stack; }
      protected ItemStack colored(net.minecraft.world.item.Item item, PartyColor color) { ItemStack stack = new ItemStack(item); stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(color.rgb(), true)); return stack; }
      protected void addScore(int team, int delta) { c.addTeamScore(team, delta); for (UUID id : c.teamMembers(team)) c.addScore(id, delta); }
      protected boolean sameTeam(ServerPlayer a, Entity b) { return b instanceof ServerPlayer other && c.team(a.getUUID()) == c.team(other.getUUID()); }
      protected ServerPlayer sourcePlayer(DamageSource source) {
         if (source == null) return null;
         Entity entity = source.getEntity(); if (entity instanceof ServerPlayer player) return player;
         Entity direct = source.getDirectEntity(); return direct instanceof ServerPlayer player ? player : null;
      }
      protected ServerPlayer projectilePlayer(DamageSource source) {
         Entity direct = source == null ? null : source.getDirectEntity();
         if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
         return sourcePlayer(source);
      }
      protected boolean enemy(ServerPlayer a, Entity b) { return b instanceof ServerPlayer other && c.team(a.getUUID()) != c.team(other.getUUID()); }
      protected void eliminate(ServerPlayer player, String reason) {
         if (player == null || !c.alive(player.getUUID())) return;
         c.alive(player.getUUID(), false); player.setGameMode(GameType.SPECTATOR); c.actionbar(player, "&c已淘汰 &7" + reason);
         if (!c.teamAlive(c.team(player.getUUID()))) c.winTeam(c.team(player.getUUID()) == 1 ? 2 : 1, reason);
      }
      protected BlockPos targetBlock(String name, int team, int x, int y, int z) { return c.anchorBlock(name, x, y, z); }
      protected boolean near(BlockPos pos, Vec3 target, double radius) { return pos != null && target != null && pos.distToCenterSqr(target.x, target.y, target.z) <= radius * radius; }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { return true; }
      @Override public void close() { }
   }

   private static final class PrisonPals extends Base {
      private final Map<UUID, UUID> owner = new HashMap<>();
      private final Map<UUID, Integer> stages = new HashMap<>();
      PrisonPals(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         for (int team = 1; team <= 2; team++) {
            for (UUID id : c.teamMembers(team)) { ServerPlayer player = p(id); stages.put(id, 0); teleport(player, spawn(team), team == 1 ? -90 : 90); if (player != null) { player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, true, false)); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, true, false)); } }
            for (int i = 0; i < 30; i++) spawnMinion(team, i);
         }
      }
      private void spawnMinion(int team, int index) {
         if (c.level() == null) return; Vec3 base = spawn(team); double direction = team == 1 ? 1 : -1;
         Zombie zombie = new Zombie(c.level()); zombie.moveTo(base.x + direction * (2 + index % 6 * 1.1), base.y, base.z - 4 + (index / 6) * 1.7, team == 1 ? -90 : 90, 0); zombie.setNoAi(true); zombie.setInvulnerable(true); zombie.setSilent(true); zombie.setPersistenceRequired(); color(zombie, c.color(c.teamMembers(team).get(0))); c.level().addFreshEntity(zombie); c.own(zombie); owner.put(zombie.getUUID(), c.teamMembers(team).get(0));
      }
      private void color(Mob mob, PartyColor color) { for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) { ItemStack stack = colored(Items.LEATHER_HELMET, color); if (slot == EquipmentSlot.CHEST) stack = colored(Items.LEATHER_CHESTPLATE, color); if (slot == EquipmentSlot.LEGS) stack = colored(Items.LEATHER_LEGGINGS, color); if (slot == EquipmentSlot.FEET) stack = colored(Items.LEATHER_BOOTS, color); mob.setItemSlot(slot, stack); mob.setDropChance(slot, 0); } }
      @Override public void tick() {
         if (c.level() == null) return; List<Zombie> minions = c.level().getEntitiesOfClass(Zombie.class, new AABB(c.arena().minX(), c.arena().baseY() - 4, c.arena().minZ(), c.arena().maxX() + 1, c.arena().topY() + 4, c.arena().maxZ() + 1), z -> owner.containsKey(z.getUUID()) && !z.isRemoved());
         for (Zombie z : minions) { UUID id = owner.get(z.getUUID()); ServerPlayer leader = p(id); if (leader != null) { Vec3 d = leader.position().subtract(z.position()); if (d.lengthSqr() > 3) z.setDeltaMovement(d.normalize().scale(.13)); else z.setDeltaMovement(Vec3.ZERO); } }
         for (Zombie attacker : minions) { UUID attackerOwner = owner.get(attacker.getUUID()); Vec3 forward = attacker.getLookAngle().multiply(1, 0, 1); if (forward.lengthSqr() < .01) continue; forward = forward.normalize(); Vec3 hit = attacker.position().add(forward.scale(.8)); for (Zombie target : minions) if (!attacker.equals(target) && !c.teamMembers(c.team(attackerOwner)).contains(owner.get(target.getUUID())) && target.position().distanceToSqr(hit) < .7) { UUID newOwner = owner.get(attacker.getUUID()); owner.put(target.getUUID(), newOwner); color(target, c.color(newOwner)); break; } }
         for (int team = 1; team <= 2; team++) { int count = 0; for (UUID id : owner.values()) if (c.team(id) == team) count++; c.teamScore(team, count); if (count == 0) { c.winTeam(team == 1 ? 2 : 1, c.color(c.teamMembers(team == 1 ? 2 : 1).get(0)).display() + "夺取了全部随从"); return; } boolean completed = true; for (UUID id : c.teamMembers(team)) { ServerPlayer player = p(id); int stage = stages.getOrDefault(id, 0); if (player == null) { completed = false; continue; } double direction = team == 1 ? 1 : -1; double step = Math.max(3.0, Math.min(7.0, (c.arena().maxX() - c.arena().minX() - 8) / 5.0)); double checkpoint = spawn(team).x + direction * (stage + 1) * step; checkpoint = direction > 0 ? Math.min(checkpoint, c.arena().maxX() - 2.0) : Math.max(checkpoint, c.arena().minX() + 2.0); if (stage < 5 && ((direction > 0 && player.getX() >= checkpoint) || (direction < 0 && player.getX() <= checkpoint))) stages.put(id, stage + 1); if (stages.getOrDefault(id, 0) < 5) completed = false; } if (completed) { c.teamScore(team, 5); c.winTeam(team, "完成五个逃脱关卡"); return; } }
         clock();
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return owner.containsKey(entity.getUUID()); }
      @Override public void close() { owner.clear(); stages.clear(); }
   }

   private static final class Rpsc extends Base {
      private final Map<Integer, Integer> choices = new HashMap<>(); private int winner; private int attackTicks;
      Rpsc(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (attackTicks <= 0 && (action.type() == PartyGameAction.Type.USE_BLOCK || action.type() == PartyGameAction.Type.DROP_ITEM)) { int choice = action.type() == PartyGameAction.Type.DROP_ITEM ? (action.stack().is(Items.SHEARS) ? 1 : action.stack().is(Items.PAPER) ? 2 : 0) : Math.floorMod(action.block().getX() + action.block().getZ(), 3); choices.put(c.team(player.getUUID()), choice); c.send(player, "&7已选择：" + new String[]{"石头", "剪刀", "布"}[choice]); return true; }
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY) return winner == c.team(player.getUUID()) && enemy(player, action.entity()) ? false : true;
         return true;
      }
      // 0=rock, 1=scissors, 2=paper.  The next value in this cycle is the
      // choice defeated by `a`, which keeps the rule independent of team
      // colour and mirrors the source datapack's three-button order.
      private int wins(int a, int b) { return a == b ? 0 : ((a + 1) % 3 == b ? 1 : 2); }
      @Override public void tick() {
         if (attackTicks > 0) { attackTicks--; if (attackTicks == 0) winner = 0; if (!c.teamAlive(1)) c.winTeam(2, "蓝方逃跑失败"); else if (!c.teamAlive(2)) c.winTeam(1, "红方逃跑失败"); return; }
         if (choices.size() == 2) { int result = wins(choices.get(1), choices.get(2)); if (result == 0) { choices.clear(); c.broadcast("&e平局，重新出拳"); } else { winner = result; attackTicks = 160; c.broadcast((winner == 1 ? "&9蓝方" : "&c红方") + " &f获胜，另一队快逃！"); choices.clear(); } }
         clock();
      }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = sourcePlayer(source); if (attacker == null || attacker.getUUID().equals(player.getUUID())) return true; if (c.team(attacker.getUUID()) == c.team(player.getUUID())) return true; return winner != c.team(attacker.getUUID()); }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "石头剪刀布失败"); return true; }
   }

   private static final class Tanks extends Base {
      private final Map<Integer, Entity> tanks = new HashMap<>(); private final Map<Integer, Integer> health = new HashMap<>(); private final Map<UUID, Integer> cooldown = new HashMap<>();
      Tanks(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), unbreakable(Items.BOW, 1), 0); give(p(id), new ItemStack(Items.ARROW, 64), 1); } for (int team = 1; team <= 2; team++) { WitherSkeleton tank = new WitherSkeleton(EntityType.WITHER_SKELETON, c.level()); Vec3 pos = c.anchor(team == 1 ? "blue_tank" : "red_tank", team == 1 ? 20 : 76, 3, 48); tank.moveTo(pos.x, pos.y, pos.z); tank.setNoAi(true); tank.setInvulnerable(true); tank.setSilent(true); tank.setCustomName(TextUtil.color(team == 1 ? "&9蓝方凋灵坦克" : "&c红方凋灵坦克")); PartyColor color = c.color(c.teamMembers(team).get(0)); tank.setItemSlot(EquipmentSlot.HEAD, colored(Items.LEATHER_HELMET, color)); tank.setItemSlot(EquipmentSlot.CHEST, colored(Items.LEATHER_CHESTPLATE, color)); tank.setItemSlot(EquipmentSlot.MAINHAND, unbreakable(Items.IRON_SWORD, 1)); c.level().addFreshEntity(tank); c.own(tank); tanks.put(team, tank); health.put(team, 8); } }
      private void hit(int team, ServerPlayer attacker) { if (attacker == null || c.team(attacker.getUUID()) == team) return; int value = health.merge(team, -1, Integer::sum); c.broadcast((team == 1 ? "&9蓝方" : "&c红方") + " &f坦克核心 &e" + Math.max(0, value) + "/8"); if (value <= 0) c.winTeam(team == 1 ? 2 : 1, "坦克核心被摧毁"); }
      private void shoot(ServerPlayer player) { int left = cooldown.getOrDefault(player.getUUID(), 0); if (left > 0) return; cooldown.put(player.getUUID(), 20); Vec3 look = player.getLookAngle().normalize(); Arrow arrow = new Arrow(c.level(), player, ItemStack.EMPTY, null); arrow.moveTo(player.getEyePosition().add(look.scale(.6))); arrow.shoot(look.x, look.y, look.z, 1.4F, 0); arrow.setNoGravity(true); c.level().addFreshEntity(arrow); c.own(arrow); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { cooldown.replaceAll((id, value) -> Math.max(0, value - 1)); if ((action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM) && (action.stack().is(Items.BOW) || action.stack().is(Items.ARROW))) { shoot(player); return true; } if (action.type() != PartyGameAction.Type.ATTACK_ENTITY || action.entity() == null) return true; for (int team = 1; team <= 2; team++) if (action.entity().equals(tanks.get(team)) && team != c.team(player.getUUID())) { hit(team, player); return true; } return true; }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { for (int team = 1; team <= 2; team++) if (entity.equals(tanks.get(team))) { hit(team, sourcePlayer(source)); return true; } return false; }
      @Override public void tick() { cooldown.replaceAll((id, value) -> Math.max(0, value - 1)); for (int team = 1; team <= 2; team++) { Entity tank = tanks.get(team); List<UUID> members = c.teamMembers(team); ServerPlayer driver = members.isEmpty() ? null : p(members.get(0)); if (tank != null && driver != null) { Vec3 movement = driver.getDeltaMovement(); tank.setDeltaMovement(movement.x, 0, movement.z); tank.setYRot(driver.getYRot()); } } if (clock()) return; c.teamScore(1, 8 - health.getOrDefault(1, 0)); c.teamScore(2, 8 - health.getOrDefault(2, 0)); }
      @Override public void close() { tanks.clear(); health.clear(); cooldown.clear(); }
   }

   private static final class CaptureFlag extends Base {
      private final Map<Integer, BlockPos> flags = new HashMap<>(); private final Map<UUID, Integer> carriers = new HashMap<>(); private final Map<UUID, Integer> respawn = new HashMap<>();
      CaptureFlag(PartyGameDefinition d) { super(d); }
      @Override public void start() { flags.put(1, targetBlock("blue_flag", 1, 20, 3, 48)); flags.put(2, targetBlock("red_flag", 2, 76, 3, 48)); paintFlags(); for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); } }
      private void paintFlags() { for (int team = 1; team <= 2; team++) c.level().setBlock(flags.get(team), c.color(c.teamMembers(team).get(0)).wool(), 3); }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { for (int team = 1; team <= 2; team++) if (flags.get(team).equals(pos) && team != c.team(player.getUUID()) && !carriers.containsKey(player.getUUID())) { carriers.put(player.getUUID(), team); c.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); give(player, colored(team == 1 ? Items.BLUE_BANNER : Items.RED_BANNER, c.color(player.getUUID())), 8); c.actionbar(player, "&f你拿到了 " + (team == 1 ? "&9蓝旗" : "&c红旗")); return true; } return false; }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = sourcePlayer(source); if (attacker != null && c.team(attacker.getUUID()) != c.team(player.getUUID())) { drop(player); return false; } return true; }
      private void drop(ServerPlayer player) { Integer flag = carriers.remove(player.getUUID()); if (flag != null) { flags.putIfAbsent(flag, targetBlock(flag == 1 ? "blue_flag" : "red_flag", flag, flag == 1 ? 20 : 76, 3, 48)); paintFlags(); player.getInventory().setItem(8, ItemStack.EMPTY); } }
      @Override public boolean death(ServerPlayer player) { drop(player); respawn.put(player.getUUID(), 60); return true; }
      @Override public void tick() { for (UUID id : List.copyOf(respawn.keySet())) { int left = respawn.merge(id, -1, Integer::sum); if (left <= 0) { respawn.remove(id); ServerPlayer player = p(id); if (player != null) { player.setGameMode(GameType.SURVIVAL); player.setHealth(player.getMaxHealth()); Vec3 home = spawn(c.team(id)); teleport(player, home, c.team(id) == 1 ? -90 : 90); } } } for (UUID id : List.copyOf(carriers.keySet())) { ServerPlayer player = p(id); Integer flag = carriers.get(id); if (player == null || !c.alive(id)) { if (player != null) drop(player); continue; } int own = c.team(id); Vec3 base = spawn(own); if (player.position().distanceToSqr(base) < 25 && flag != own) { carriers.remove(id); player.getInventory().setItem(8, ItemStack.EMPTY); addScore(own, 1); paintFlags(); c.sound(SoundEvents.PLAYER_LEVELUP, 1, 1.2F); c.winTeam(own, "成功夺回敌方旗帜"); return; } } clock(); }
      @Override public void close() { carriers.clear(); flags.clear(); respawn.clear(); }
   }

   private static final class MineBusiness extends Base {
      MineBusiness(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), unbreakable(Items.DIAMOND_PICKAXE, 1), 0); give(p(id), unbreakable(Items.IRON_SWORD, 1), 1); } }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { return c.arena().inPlay(pos) && state.getBlock() != Blocks.BEDROCK && state.getBlock() != Blocks.BARRIER; }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = sourcePlayer(source); return attacker == null || c.team(attacker.getUUID()) == c.team(player.getUUID()); }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "在地底战斗中倒下"); return true; }
      @Override public void tick() { for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && player.getY() < c.arena().floorY() - 6) eliminate(player, "掉入矿井"); } clock(); }
   }

   private static final class TeamHockey extends Base {
      private final List<Turtle> pucks = new ArrayList<>();
      TeamHockey(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); for (int i = 0; i < 2; i++) { Turtle puck = new Turtle(EntityType.TURTLE, c.level()); Vec3 pos = c.anchor("puck", 48 + i, 3, 48); puck.moveTo(pos.x, pos.y, pos.z); puck.setNoAi(true); puck.setInvulnerable(true); puck.setSilent(true); c.level().addFreshEntity(puck); c.own(puck); pucks.add(puck); } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && pucks.contains(action.entity())) { Turtle puck = (Turtle) action.entity(); Vec3 look = player.getLookAngle(); puck.setDeltaMovement(look.x * .75, .08, look.z * .75); return true; } return true; }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return pucks.contains(entity); }
      @Override public void tick() { if (clock()) return; for (Turtle puck : List.copyOf(pucks)) { Vec3 d = puck.getDeltaMovement(); puck.setDeltaMovement(d.x * .94, d.y * .92, d.z * .94); for (int team = 1; team <= 2; team++) { Vec3 goal = c.anchor(team == 1 ? "blue_goal" : "red_goal", team == 1 ? 14 : 82, 3, 48); if (puck.position().distanceToSqr(goal) < 16) { int scorer = team == 1 ? 2 : 1; addScore(scorer, 1); c.broadcast((scorer == 1 ? "&9蓝方" : "&c红方") + " &f进球！"); Vec3 center = c.anchor("puck", 48, 3, 48); puck.teleportTo(center.x, center.y, center.z); puck.setDeltaMovement(Vec3.ZERO); if (c.teamScore(scorer) >= 2) c.winTeam(scorer, "先得两球"); } } } }
      @Override public void close() { pucks.clear(); }
   }

   private static final class MazeNavigator extends Base {
      private final Map<Integer, List<BlockPos>> targets = new HashMap<>(); private final Map<UUID, Integer> found = new HashMap<>();
      MazeNavigator(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         for (int team = 1; team <= 2; team++) {
            List<BlockPos> list = new ArrayList<>();
            list.add(targetBlock(team == 1 ? "blue_target" : "red_target", team, team == 1 ? 30 : 66, 3, 48));
            list.add(targetBlock(team == 1 ? "blue_target_2" : "red_target_2", team, team == 1 ? 36 : 60, 3, 44));
            targets.put(team, list);
            for (BlockPos target : list) c.level().setBlock(target, c.color(c.teamMembers(team).get(0)).concrete(), 3);
         }
         for (UUID id : c.seats()) { found.put(id, 0); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), new ItemStack(Items.COMPASS), 0); }
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.USE_BLOCK && action.block() != null) {
            int team = c.team(player.getUUID());
            List<BlockPos> teamTargets = targets.getOrDefault(team, List.of());
            int targetIndex = -1;
            for (int i = 0; i < teamTargets.size(); i++) if (teamTargets.get(i).distSqr(action.block()) <= 2) { targetIndex = i; break; }
            if (targetIndex >= 0 && found.getOrDefault(player.getUUID(), 0) < 2) {
               int value = found.merge(player.getUUID(), 1, Integer::sum);
               // Keep the orb visible for the rest of the team; the claim is
               // tracked per player so one fast teammate cannot consume it.
               give(player, new ItemStack(team == 1 ? Items.BLUE_DYE : Items.RED_DYE), 8);
               c.actionbar(player, "&a找到目标染料 " + value + "/2");
               c.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, .6F, 1.4F);
               return true;
            }
         }
         return true;
      }
      @Override public void tick() {
         for (int team = 1; team <= 2; team++) {
            List<UUID> members = c.teamMembers(team);
            c.teamScore(team, members.stream().mapToInt(id -> found.getOrDefault(id, 0)).sum());
            if (!members.stream().allMatch(id -> found.getOrDefault(id, 0) >= 2)) continue;
            Vec3 meeting = c.anchor(team == 1 ? "blue_meet" : "red_meet", team == 1 ? 38 : 58, 3, 48);
            if (members.stream().allMatch(id -> p(id) != null && p(id).position().distanceToSqr(meeting) < 36)) { c.winTeam(team, "全队找到染料并成功汇合"); return; }
         }
         clock();
      }
      @Override public void close() { targets.clear(); found.clear(); }
   }

   private static final class BombsAway extends Base {
      private final Set<UUID> pending = new HashSet<>(); private final Map<PrimedTnt, Integer> bombs = new HashMap<>(); private final Map<PrimedTnt, Integer> bombOwners = new HashMap<>();
      BombsAway(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), new ItemStack(Items.TNT, 5), 0); } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.DROP_ITEM && action.stack().is(Items.TNT)) { pending.add(player.getUUID()); PrimedTnt tnt = EntityType.TNT.create(c.level()); if (tnt != null) { Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(.8)); tnt.moveTo(pos.x, pos.y, pos.z); tnt.setDeltaMovement(player.getLookAngle().scale(.65).add(0, .25, 0)); tnt.setFuse(200); c.level().addFreshEntity(tnt); c.own(tnt); bombs.put(tnt, 45); bombOwners.put(tnt, c.team(player.getUUID())); } return true; } if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && enemy(player, action.entity()) && action.entity() instanceof ServerPlayer target) { Vec3 push = player.getLookAngle().normalize().scale(1.3); target.push(push.x, .3, push.z); return true; } return true; }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = projectilePlayer(source); if (attacker != null && c.team(attacker.getUUID()) != c.team(player.getUUID())) { Vec3 push = attacker.getLookAngle().normalize().scale(.9); player.push(push.x, .2, push.z); return true; } return true; }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "被炸下浮空岛"); return true; }
      @Override public void tick() { for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && player.getY() < c.arena().floorY() - 3) eliminate(player, "掉落虚空"); } for (UUID id : List.copyOf(pending)) { ServerPlayer player = p(id); if (player == null) { pending.remove(id); continue; } for (ItemEntity item : c.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.getX() - 2, player.getY() - 2, player.getZ() - 2, player.getX() + 2, player.getY() + 2, player.getZ() + 2), value -> value.getItem().is(Items.TNT))) item.discard(); pending.remove(id); } for (PrimedTnt bomb : List.copyOf(bombs.keySet())) { if (bomb.isRemoved()) { bombs.remove(bomb); bombOwners.remove(bomb); continue; } int fuse = bombs.merge(bomb, -1, Integer::sum); if (fuse <= 0) { for (UUID id : c.seats()) { ServerPlayer target = p(id); if (target != null && c.team(id) != nearestOwnerTeam(bomb) && target.position().distanceToSqr(bomb.position()) < 49) { Vec3 push = target.position().subtract(bomb.position()).normalize().scale(1.25); target.push(push.x, .35, push.z); } } bomb.discard(); bombs.remove(bomb); bombOwners.remove(bomb); c.sound(SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.1F); } } if (c.elapsedTicks() % 100 == 0) for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && player.getInventory().countItem(Items.TNT) < 5) give(player, new ItemStack(Items.TNT, 5), 0); } if (!clock()) { if (!c.teamAlive(1)) c.winTeam(2, "蓝方全员淘汰"); else if (!c.teamAlive(2)) c.winTeam(1, "红方全员淘汰"); } }
      private int nearestOwnerTeam(PrimedTnt bomb) { return bombOwners.getOrDefault(bomb, 1); }
      @Override public void close() { pending.clear(); bombs.clear(); bombOwners.clear(); }
   }

   private static final class Labyrinth extends Base {
      private final Map<Integer, List<BlockPos>> goals = new HashMap<>();
      private final Map<UUID, Integer> progress = new HashMap<>();
      private final Map<UUID, Set<BlockPos>> claimed = new HashMap<>();
      private final Map<BlockPos, Integer> restore = new HashMap<>();
      Labyrinth(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         for (int team = 1; team <= 2; team++) {
            List<BlockPos> teamGoals = List.of(
               targetBlock(team == 1 ? "blue_gold_1" : "red_gold_1", team, team == 1 ? 28 : 68, 3, 48),
               targetBlock(team == 1 ? "blue_gold_2" : "red_gold_2", team, team == 1 ? 34 : 74, 3, 48));
            goals.put(team, teamGoals);
            for (BlockPos goal : teamGoals) c.level().setBlock(goal, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
         }
         for (UUID id : c.seats()) { teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), unbreakable(Items.IRON_PICKAXE, 1), 0); progress.put(id, 0); claimed.put(id, new HashSet<>()); }
      }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) {
         if (state.getBlock() != Blocks.GOLD_BLOCK) return false;
         int team = c.team(player.getUUID()); List<BlockPos> teamGoals = goals.getOrDefault(team, List.of());
         BlockPos goal = teamGoals.stream().filter(candidate -> candidate.distSqr(pos) <= 2).findFirst().orElse(null);
         if (goal == null || claimed.getOrDefault(player.getUUID(), Set.of()).contains(goal)) return false;
         claimed.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(goal);
         c.level().setBlock(goal, Blocks.AIR.defaultBlockState(), 3); restore.put(goal, 20);
         int value = progress.merge(player.getUUID(), 1, Integer::sum); addScore(team, 1);
         c.actionbar(player, "&6取得金色目标 " + value + "/2；回到基地完成"); c.sound(SoundEvents.PLAYER_LEVELUP, .55F, 1.5F);
         return true;
      }
      @Override public void tick() {
         for (BlockPos goal : List.copyOf(restore.keySet())) { int left = restore.merge(goal, -1, Integer::sum); if (left <= 0) { restore.remove(goal); c.level().setBlock(goal, Blocks.GOLD_BLOCK.defaultBlockState(), 3); } }
         for (int team = 1; team <= 2; team++) {
            List<UUID> members = c.teamMembers(team); int score = members.stream().mapToInt(id -> Math.min(2, progress.getOrDefault(id, 0))).sum(); c.teamScore(team, score);
            Vec3 base = spawn(team);
            if (members.stream().allMatch(id -> progress.getOrDefault(id, 0) >= 2 && p(id) != null && p(id).position().distanceToSqr(base) <= 36)) { c.winTeam(team, "全队带着两枚金色目标返回基地"); return; }
         }
         clock();
      }
      @Override public void close() { goals.clear(); progress.clear(); claimed.clear(); restore.clear(); }
   }

   private static final class SnowWars extends Base {
      private final Map<UUID, Integer> hits = new HashMap<>();
      SnowWars(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), new ItemStack(Items.SNOWBALL, 16), 0); hits.put(id, 0); } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && enemy(player, action.entity()) && action.entity() instanceof ServerPlayer target) { hit(target, player); return true; } if (action.type() == PartyGameAction.Type.USE_ITEM && action.stack().is(Items.SNOWBALL)) return false; return true; }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = sourcePlayer(source); if (attacker != null && c.team(attacker.getUUID()) != c.team(player.getUUID()) && (source.getDirectEntity() instanceof Snowball || attacker.getMainHandItem().is(Items.SNOWBALL))) { hit(player, attacker); return true; } return true; }
      private void hit(ServerPlayer target, ServerPlayer attacker) { int value = hits.merge(target.getUUID(), 1, Integer::sum); c.actionbar(target, "&c雪球命中 &f" + value + "/3"); if (value >= 3) eliminate(target, "雪球命中三次"); }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "雪球大战淘汰"); return true; }
      @Override public void tick() { if (c.elapsedTicks() % 60 == 0) for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && player.getInventory().countItem(Items.SNOWBALL) < 8) give(player, new ItemStack(Items.SNOWBALL, 16), 0); } if (!clock()) { if (!c.teamAlive(1)) c.winTeam(2, "蓝方全员淘汰"); else if (!c.teamAlive(2)) c.winTeam(1, "红方全员淘汰"); } }
   }

   private static final class SpaceJumpers extends Base {
      private final Map<Integer, Integer> extended = new HashMap<>();
      private final Map<UUID, BlockPos> checkpoints = new HashMap<>();
      private final Set<UUID> jumpers = new HashSet<>();
      SpaceJumpers(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         for (int team = 1; team <= 2; team++) {
            extended.put(team, 0);
            List<UUID> members = c.teamMembers(team);
            for (int i = 0; i < members.size(); i++) {
               UUID id = members.get(i); ServerPlayer player = p(id); teleport(player, spawn(team), team == 1 ? -90 : 90);
               // The source map assigns one jumper per side. With a larger
               // room the remaining members are shooters; a two-player room
               // still remains playable because its sole member is a jumper
               // and may also extend the route with the bow.
               boolean jumper = members.size() == 1 || i == members.size() - 1;
               if (jumper) jumpers.add(id);
               checkpoints.put(id, BlockPos.containing(spawn(team)).immutable());
               if (player != null) {
                  player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, -1, 3, true, false));
                  player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, -1, 0, true, false));
                  give(player, unbreakable(Items.BOW, 1), 0); give(player, new ItemStack(Items.ARROW, 64), 1);
               }
            }
            // Make the first platform visible even if a sparse export omitted
            // the dynamic redstone-driven start block.
            placePath(team, 0);
         }
      }
      private BlockPos path(int team, int step) {
         Vec3 start = spawn(team), finish = c.anchor(team == 1 ? "blue_finish" : "red_finish", team == 1 ? 76 : 20, 10, 48);
         double t = Math.min(1.0, Math.max(0.0, step / 24.0));
         return BlockPos.containing(start.x + (finish.x - start.x) * t, start.y + 2.0 + Math.sin(t * Math.PI) * 3.0, start.z + (finish.z - start.z) * t).immutable();
      }
      private void placePath(int team, int step) {
         BlockPos center = path(team, step); BlockState block = c.color(team == 1 ? c.teamMembers(1).get(0) : c.teamMembers(2).get(0)).concrete();
         for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.level().setBlock(center.offset(dx, 0, dz), block, 3);
      }
      private void extend(int team, ServerPlayer player) {
         int value = extended.merge(team, 1, Integer::sum);
         if (value > 24) { extended.put(team, 24); c.actionbar(player, "&7路线已经延伸到终点"); return; }
         placePath(team, value); c.actionbar(player, "&a平台延伸 &f" + value + "/24"); c.sound(SoundEvents.NOTE_BLOCK_PLING.value(), .5F, 1.25F);
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int team = c.team(player.getUUID());
         if ((action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM) && (action.stack().is(Items.BOW) || action.stack().is(Items.ARROW))) { extend(team, player); return true; }
         return true;
      }
      @Override public void tick() {
         for (UUID id : c.seats()) {
            ServerPlayer player = p(id); if (player == null || !c.alive(id)) continue;
            int team = c.team(id); Vec3 finish = c.anchor(team == 1 ? "blue_finish" : "red_finish", team == 1 ? 76 : 20, 10, 48);
            if (jumpers.contains(id) && player.position().distanceToSqr(finish) < 16) { c.teamScore(team, 1); c.winTeam(team, "抵达太空终点"); return; }
            int best = extended.getOrDefault(team, 0); for (int step = 0; step <= best; step++) if (player.position().distanceToSqr(path(team, step).getCenter()) < 10) checkpoints.put(id, path(team, step));
            if (player.getY() < c.arena().floorY() - 4) { BlockPos checkpoint = checkpoints.getOrDefault(id, BlockPos.containing(spawn(team))); teleport(player, checkpoint.getCenter(), team == 1 ? -90 : 90); c.actionbar(player, "&e掉落，回到最近平台"); }
         }
         clock();
      }
      @Override public void close() { extended.clear(); checkpoints.clear(); jumpers.clear(); }
   }

   private static final class BoomCarts extends Base {
      private final Map<Integer, Integer> lives = new HashMap<>();
      private final Map<Integer, Integer> target = new HashMap<>();
      private final Map<Integer, MinecartTNT> carts = new HashMap<>();
      private final Set<UUID> pending = new HashSet<>();
      private int fuse;
      BoomCarts(PartyGameDefinition d) { super(d); }
      @Override public void start() { lives.put(1, 5); lives.put(2, 5); for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), new ItemStack(Items.TNT_MINECART, 5), 0); } }
      private boolean ownButton(int team, BlockPos pos) { Vec3 button = c.anchor(team == 1 ? "blue_button" : "red_button", 48, 3, 48); return pos != null && pos.distToCenterSqr(button.x, button.y, button.z) <= 25; }
      private void lock(int team, ServerPlayer player) {
         int enemy = team == 1 ? 2 : 1; target.put(team, enemy); c.actionbar(player, "&f已锁定 " + (enemy == 1 ? "&9蓝方" : "&c红方") + "矿车");
         if (target.size() < 2) return;
         fuse = 60; carts.values().forEach(Entity::discard); carts.clear();
         for (int side = 1; side <= 2; side++) {
            Vec3 at = spawn(side); MinecartTNT cart = new MinecartTNT(c.level(), at.x, at.y, at.z); cart.setCustomName(TextUtil.color(side == 1 ? "&9蓝方爆炸矿车" : "&c红方爆炸矿车")); cart.setGlowingTag(true); c.level().addFreshEntity(cart); c.own(cart); carts.put(side, cart);
         }
         c.broadcast("&e双方已锁定目标，矿车将在 3 秒后爆炸！");
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int team = c.team(player.getUUID());
         if (action.type() == PartyGameAction.Type.USE_BLOCK && ownButton(team, action.block())) { lock(team, player); return true; }
         if (action.type() == PartyGameAction.Type.DROP_ITEM && action.stack().is(Items.TNT_MINECART)) { pending.add(player.getUUID()); lock(team, player); return true; }
         return true;
      }
      @Override public void tick() {
         for (UUID id : List.copyOf(pending)) { ServerPlayer player = p(id); if (player != null) for (ItemEntity item : c.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.getX() - 2, player.getY() - 2, player.getZ() - 2, player.getX() + 2, player.getY() + 2, player.getZ() + 2), e -> e.getItem().is(Items.TNT_MINECART))) item.discard(); pending.remove(id); }
         if (fuse > 0) {
            --fuse;
            for (int side = 1; side <= 2; side++) { MinecartTNT cart = carts.get(side); if (cart == null || cart.isRemoved()) continue; Vec3 destination = spawn(side == 1 ? 2 : 1); Vec3 delta = destination.subtract(cart.position()); if (delta.lengthSqr() > .5) cart.setDeltaMovement(delta.normalize().scale(.11)); else cart.setDeltaMovement(Vec3.ZERO); }
            if (fuse == 0) {
               for (int side : new int[]{1, 2}) { int enemy = target.getOrDefault(side, side == 1 ? 2 : 1); lives.put(enemy, lives.getOrDefault(enemy, 5) - 1); }
               carts.values().forEach(Entity::discard); carts.clear(); target.clear(); c.sound(SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.0F);
               c.teamScore(1, 5 - lives.getOrDefault(1, 5)); c.teamScore(2, 5 - lives.getOrDefault(2, 5));
               if (lives.getOrDefault(1, 0) <= 0 && lives.getOrDefault(2, 0) <= 0) c.draw("双方生命同时耗尽");
               else if (lives.getOrDefault(1, 0) <= 0) c.winTeam(2, "蓝方生命耗尽"); else if (lives.getOrDefault(2, 0) <= 0) c.winTeam(1, "红方生命耗尽");
            }
         }
         c.teamScore(1, 5 - lives.getOrDefault(1, 5)); c.teamScore(2, 5 - lives.getOrDefault(2, 5)); clock();
      }
      @Override public void close() { lives.clear(); target.clear(); carts.values().forEach(Entity::discard); carts.clear(); pending.clear(); }
   }

   private static final class WhatTheCluck extends Base {
      private final Map<UUID, Chicken> chickens = new HashMap<>(); private final Map<UUID, Integer> health = new HashMap<>();
      WhatTheCluck(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), new ItemStack(Items.EGG, 10), 0); health.put(id, 10); Chicken chicken = new Chicken(EntityType.CHICKEN, c.level()); Vec3 pos = p(id) == null ? spawn(c.team(id)) : p(id).position().subtract(0, 2, 0); chicken.moveTo(pos.x, pos.y, pos.z); chicken.setNoAi(true); chicken.setInvulnerable(true); chicken.setSilent(true); c.level().addFreshEntity(chicken); c.own(chicken); chickens.put(id, chicken); } }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { ServerPlayer attacker = projectilePlayer(source); if (attacker == null || c.team(attacker.getUUID()) == c.team(player.getUUID())) return true; if (source.getDirectEntity() != null && source.getDirectEntity().getType() == EntityType.EGG) { int value = health.merge(player.getUUID(), -1, Integer::sum); c.actionbar(player, "&c鸡蛋命中 &f" + Math.max(0, value) + "/10"); if (value <= 0) eliminate(player, "被鸡蛋击中十次"); return true; } return true; }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return chickens.containsValue(entity); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.USE_ITEM && action.stack().is(Items.EGG)) return false; return true; }
      @Override public void tick() { for (Map.Entry<UUID, Chicken> entry : chickens.entrySet()) { ServerPlayer player = p(entry.getKey()); Chicken chicken = entry.getValue(); if (player != null && !chicken.isRemoved()) { Vec3 pos = player.position().subtract(0, 2, 0); chicken.teleportTo(pos.x, pos.y, pos.z); } } if (c.elapsedTicks() % 20 == 0) for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && player.getInventory().countItem(Items.EGG) < 10) give(player, new ItemStack(Items.EGG, 10), 0); } if (!clock()) { if (!c.teamAlive(1)) c.winTeam(2, "蓝方全员淘汰"); else if (!c.teamAlive(2)) c.winTeam(1, "红方全员淘汰"); } }
      @Override public void close() { chickens.clear(); health.clear(); }
   }

   private static final class RecruitmentRoyale extends Base {
      private final Map<Integer, Integer> recruits = new HashMap<>();
      private final Map<Integer, Integer> spawned = new HashMap<>();
      private final Map<UUID, Integer> owners = new HashMap<>();
      private final List<Zombie> units = new ArrayList<>();
      private final Set<UUID> capturable = new HashSet<>();
      private boolean spawning, battle;
      RecruitmentRoyale(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         for (UUID id : c.seats()) { survival(p(id)); teleport(p(id), spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); give(p(id), unbreakable(Items.STICK, 1), 0); }
         recruits.put(1, 0); recruits.put(2, 0); spawned.put(1, 0); spawned.put(2, 0);
         Vec3 center = new Vec3(c.arena().centerX() + .5, c.arena().floorY() + 2, c.arena().centerZ() + .5);
         int count = Math.min(24, Math.max(8, c.seats().size() * 3));
         for (int i = 0; i < count; i++) { Zombie mob = new Zombie(c.level()); double angle = i * Math.PI * 2 / count; mob.moveTo(center.x + Math.cos(angle) * 7, center.y, center.z + Math.sin(angle) * 7); mob.setNoAi(true); mob.setInvulnerable(true); mob.setSilent(true); mob.setCustomName(TextUtil.color("&f待招募怪物")); c.level().addFreshEntity(mob); c.own(mob); capturable.add(mob.getUUID()); }
         c.broadcast("&e招募阶段：把怪物击退到己方颜色的坑中！");
      }
      private Vec3 pit(int team) {
         String pit = team == 1 ? "blue_pit" : "red_pit", army = team == 1 ? "blue_army" : "red_army";
         // Older 214 exports predate the explicit pit anchors; the army
         // marker was placed on the same capture hole and is a safe fallback.
         return c.arena().anchors().containsKey(pit) ? c.anchor(pit, team == 1 ? 30 : 66, 3, 48) : c.anchor(army, team == 1 ? 30 : 66, 3, 48);
      }
      private void dress(Zombie unit, int team) {
         PartyColor color = c.color(c.teamMembers(team).get(0));
         unit.setItemSlot(EquipmentSlot.HEAD, colored(Items.LEATHER_HELMET, color)); unit.setItemSlot(EquipmentSlot.CHEST, colored(Items.LEATHER_CHESTPLATE, color));
         unit.setItemSlot(EquipmentSlot.LEGS, colored(Items.LEATHER_LEGGINGS, color)); unit.setItemSlot(EquipmentSlot.FEET, colored(Items.LEATHER_BOOTS, color));
         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) unit.setDropChance(slot, 0);
      }
      private void capture(Zombie mob, int team) { capturable.remove(mob.getUUID()); mob.discard(); int count = Math.min(20, recruits.merge(team, 1, Integer::sum)); c.teamScore(team, count); c.broadcast((team == 1 ? "&9蓝方" : "&c红方") + " &f招募了一名士兵（" + count + "/20）"); }
      private void spawnUnit(int team, int index) {
         Vec3 base = c.anchor(team == 1 ? "blue_army" : "red_army", team == 1 ? 30 : 66, 3, 48); Zombie unit = new Zombie(c.level());
         unit.moveTo(base.x + (index % 5), base.y, base.z + (index / 5)); unit.setNoAi(true); unit.setInvulnerable(true); unit.setSilent(true); unit.setCustomName(TextUtil.color(team == 1 ? "&9蓝方士兵" : "&c红方士兵")); dress(unit, team);
         c.level().addFreshEntity(unit); c.own(unit); units.add(unit); owners.put(unit.getUUID(), team);
      }
      private void enterSpawning() { spawning = true; c.broadcast("&b进入出兵阶段：可使用僵尸刷怪蛋调整出兵顺序"); for (int team = 1; team <= 2; team++) for (UUID id : c.teamMembers(team)) give(p(id), new ItemStack(Items.ZOMBIE_SPAWN_EGG, recruits.getOrDefault(team, 0)), 0); }
      private void enterBattle() { spawning = false; battle = true; for (int team = 1; team <= 2; team++) for (int i = spawned.getOrDefault(team, 0); i < recruits.getOrDefault(team, 0); i++) spawnUnit(team, i); c.broadcast("&e出兵结束，双方军队交战！"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int team = c.team(player.getUUID());
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && !spawning && !battle && action.entity() instanceof Zombie mob && capturable.contains(mob.getUUID())) { Vec3 look = player.getLookAngle().normalize(); mob.setDeltaMovement(look.x * 1.0, .12, look.z * 1.0); return true; }
         if (action.type() == PartyGameAction.Type.USE_ITEM && spawning && action.stack().is(Items.ZOMBIE_SPAWN_EGG)) { int value = spawned.getOrDefault(team, 0); if (value < recruits.getOrDefault(team, 0)) { spawnUnit(team, value); spawned.put(team, value + 1); c.actionbar(player, "&a已放置士兵 " + (value + 1) + "/" + recruits.getOrDefault(team, 0)); } return true; }
         return true;
      }
      @Override public void tick() {
         if (!spawning && !battle && c.elapsedTicks() >= 600) enterSpawning();
         if (spawning && c.elapsedTicks() >= 800) enterBattle();
         if (!spawning && !battle) for (UUID id : List.copyOf(capturable)) { Entity entity = c.level().getEntity(id); if (!(entity instanceof Zombie mob) || mob.isRemoved()) { capturable.remove(id); continue; } for (int team = 1; team <= 2; team++) if (mob.position().distanceToSqr(pit(team)) < 12) { capture(mob, team); break; } }
         if (battle && c.elapsedTicks() % 10 == 0) {
            for (Zombie unit : List.copyOf(units)) {
               if (unit.isRemoved()) { units.remove(unit); owners.remove(unit.getUUID()); continue; }
               int team = owners.getOrDefault(unit.getUUID(), 1); Zombie target = units.stream().filter(other -> !other.isRemoved() && owners.getOrDefault(other.getUUID(), team) != team).min((a, b) -> Double.compare(a.distanceToSqr(unit), b.distanceToSqr(unit))).orElse(null);
               if (target != null) { Vec3 delta = target.position().subtract(unit.position()); if (delta.lengthSqr() > 2.2) unit.setDeltaMovement(delta.normalize().scale(.12)); else { target.discard(); units.remove(target); owners.remove(target.getUUID()); } }
            }
            int one = (int) owners.values().stream().filter(t -> t == 1).count(), two = (int) owners.values().stream().filter(t -> t == 2).count(); c.teamScore(1, one); c.teamScore(2, two);
            if (one == 0 && two == 0) c.draw("双方军队同时溃败"); else if (one == 0) c.winTeam(2, "蓝方军队溃败"); else if (two == 0) c.winTeam(1, "红方军队溃败");
         }
         clock();
      }
      @Override public boolean death(ServerPlayer player) {
         if (player == null) return true;
         if (battle) { eliminate(player, "战场淘汰"); return true; }
         player.setHealth(player.getMaxHealth()); teleport(player, spawn(c.team(player.getUUID())), c.team(player.getUUID()) == 1 ? -90 : 90); c.actionbar(player, "&e你将在出兵阶段重生"); return true;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return owners.containsKey(entity.getUUID()) || capturable.contains(entity.getUUID()); }
      @Override public void close() { for (Zombie mob : units) mob.discard(); for (UUID id : capturable) { Entity entity = c.level().getEntity(id); if (entity != null) entity.discard(); } units.clear(); owners.clear(); capturable.clear(); recruits.clear(); spawned.clear(); }
   }
}
