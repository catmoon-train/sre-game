package net.exmo.sreGame.games.partygames.team;

import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Room-local implementations for the asymmetric 301-314 catalogue.
 *
 * <p>The source datapack uses global scoreboards and role tags.  This class
 * deliberately keeps those values in a controller instance instead, so two
 * rooms can play the same game without sharing timers, entities or targets.
 */
final class AdvancedPartyControllers {
   private AdvancedPartyControllers() { }

   static PartyGameController create(PartyGameDefinition definition) {
      if (definition == null) throw new IllegalArgumentException("Missing 301-314 definition");
      return switch (definition.type()) {
         case HIDE_AND_SEEK -> new HideAndSeek(definition);
         case GAME_THEORY -> new GameTheory(definition);
         case BOSS_BRAWL -> new BossBrawl(definition);
         case GOLD_RUSH -> new GoldRush(definition);
         case BLOCK_BUSTER -> new BlockBuster(definition);
         case PAC_CUBE -> new PacCube(definition);
         case GHOST_HUNT -> new GhostHunt(definition);
         case TREETOP_HOP -> new TreetopHop(definition);
         case SLIME_TIME -> new SlimeTime(definition);
         case IN_THE_ZONE -> new InTheZone(definition);
         case GHAST_BLAST -> new GhastBlast(definition);
         case EGGCELLENCE -> new Eggcellence(definition);
         case RAVAGER_RODEO -> new RavagerRodeo(definition);
         case MOUSE_TRAP -> new MouseTrap(definition);
         default -> throw new IllegalArgumentException("Not a 301-314 game: " + definition.type());
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
      protected Vec3 center() { return c.anchor("center", (c.arena().maxX() - c.arena().minX()) / 2.0, 3, (c.arena().maxZ() - c.arena().minZ()) / 2.0); }
      protected boolean clock() { if (remaining <= 0) return false; if (--remaining > 0) return false; timeout(); return true; }
      protected void timeout() {
         int one = c.teamScore(1), two = c.teamScore(2);
         if (one == two) c.draw("时间到，蓝方和红方平局"); else c.winTeam(one > two ? 1 : 2, "时间到");
      }
      protected void teleport(ServerPlayer player, Vec3 pos, float yaw) { if (player != null && c.level() != null) player.teleportTo(c.level(), pos.x, pos.y, pos.z, yaw, 0); }
      protected void survival(ServerPlayer player) { if (player != null) player.setGameMode(GameType.SURVIVAL); }
      protected void give(ServerPlayer player, ItemStack stack, int slot) { if (player != null) player.getInventory().setItem(slot, stack); }
      /** Manual use actions return FAIL from the interaction hook, so consume the stack here. */
      protected boolean consume(ServerPlayer player, net.minecraft.world.item.Item item) {
         if (player == null || item == null) return false;
         for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) { stack.shrink(1); return true; }
         }
         return false;
      }
      protected ItemStack unbreakable(net.minecraft.world.item.Item item, int count) { ItemStack stack = new ItemStack(item, count); stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new Unbreakable(true)); return stack; }
      protected ServerPlayer attacker(DamageSource source) {
         if (source == null) return null;
         if (source.getEntity() instanceof ServerPlayer player) return player;
         if (source.getDirectEntity() instanceof ServerPlayer player) return player;
         if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
         return null;
      }
      protected boolean enemy(ServerPlayer player, Entity entity) { return entity instanceof ServerPlayer other && c.team(player.getUUID()) != c.team(other.getUUID()); }
      protected void eliminate(ServerPlayer player, String reason) {
         if (player == null || !c.alive(player.getUUID())) return;
         c.alive(player.getUUID(), false); player.setGameMode(GameType.SPECTATOR); c.actionbar(player, "&c已淘汰 &7" + reason);
      }
      protected int aliveRole(Map<UUID, Integer> roles, int role) { return (int) c.seats().stream().filter(id -> roles.getOrDefault(id, 0) == role && c.alive(id)).count(); }
      protected UUID firstAlive(Map<UUID, Integer> roles, int role) { return c.seats().stream().filter(id -> roles.getOrDefault(id, 0) == role && c.alive(id)).findFirst().orElse(null); }
      protected void winRole(Map<UUID, Integer> roles, int role, String reason) { c.win(firstAlive(roles, role), reason); }
      protected void push(Entity entity, ServerPlayer player, double strength) {
         if (entity == null || player == null) return;
         Vec3 look = player.getLookAngle().normalize(); entity.setDeltaMovement(look.x * strength, Math.max(.12, look.y * strength + .2), look.z * strength); entity.hurtMarked = true;
      }
      protected void teamName(int team, Entity entity, String label) {
         PartyColor color = c.color(c.teamMembers(team).isEmpty() ? c.seats().get(0) : c.teamMembers(team).get(0));
         entity.setCustomName(TextUtil.color((color == PartyColor.BLUE ? "&9蓝方" : "&c红方") + label)); entity.setCustomNameVisible(true);
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { return true; }
      @Override public void close() { }
   }

   private static final class HideAndSeek extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private boolean released;
      HideAndSeek(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); released = false; for (UUID id : c.seats()) roles.put(id, 2); roles.put(c.seats().get(c.random().nextInt(c.seats().size())), 1); }
      @Override public void start() {
         UUID seeker = firstAlive(roles, 1); teleport(p(seeker), center(), 180);
         for (UUID id : c.seats()) {
            ServerPlayer player = p(id); if (player == null) continue;
            if (roles.get(id) == 1) { give(player, unbreakable(Items.IRON_SWORD, 1), 0); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 900, 0, true, false)); }
            else { player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, true, false)); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, true, false)); }
         }
         c.broadcast("&e躲藏阶段开始：&f45 秒后释放抓捕者");
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (roles.getOrDefault(player.getUUID(), 0) == 1 && action.type() == PartyGameAction.Type.DROP_ITEM && action.stack().is(Items.IRON_SWORD)) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, -1, player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED) ? 0 : 1, true, false));
            c.actionbar(player, "&e抓捕者速度已切换"); return true;
         }
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof ServerPlayer victim) return !(released && roles.getOrDefault(player.getUUID(), 0) == 1 && roles.getOrDefault(victim.getUUID(), 0) == 2);
         return true;
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) {
         ServerPlayer sourcePlayer = attacker(source); if (sourcePlayer == null) return true;
         return !(released && roles.getOrDefault(sourcePlayer.getUUID(), 0) == 1 && roles.getOrDefault(victim.getUUID(), 0) == 2);
      }
      @Override public boolean death(ServerPlayer player) {
         int role = roles.getOrDefault(player.getUUID(), 0); if (role == 2 && released) { eliminate(player, "抓捕者发现了你"); if (aliveRole(roles, 2) == 0) winRole(roles, 1, "所有躲藏者都已被找到"); }
         else if (role == 1) { eliminate(player, "抓捕者被击败"); winRole(roles, 2, "抓捕者被击败"); }
         return true;
      }
      @Override public void tick() {
         if (!released && c.elapsedTicks() >= 900) {
            released = true; remaining = 90 * 20;
            ServerPlayer seeker = p(firstAlive(roles, 1));
            if (seeker != null) seeker.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
            c.broadcast("&c抓捕者上场了！");
         }
         if (released && aliveRole(roles, 2) == 0) { winRole(roles, 1, "所有躲藏者都已被找到"); return; }
         if (!released) return;
         if (clock()) return;
         winRole(roles, 2, "躲藏者坚持到了时间结束");
      }
      @Override public void close() { roles.clear(); }
   }

   private static final class GameTheory extends Base {
      private final Map<UUID, Integer> choices = new HashMap<>();
      private int round;
      private int roundTicks;
      GameTheory(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); choices.clear(); round = 1; roundTicks = 200; }
      @Override public void start() {
         round = 1; roundTicks = 200;
         giveChoices();
         c.broadcast("&e第 1/6 轮：&f选择 A、B 或 C，把选择物品丢出提交");
      }
      private void giveChoices() {
         for (UUID id : c.seats()) {
            ServerPlayer player = p(id);
            give(player, new ItemStack(Items.PAPER), 0); // A
            give(player, new ItemStack(Items.SHEARS), 1); // B
            give(player, new ItemStack(Items.FEATHER), 2); // C
         }
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() != PartyGameAction.Type.USE_BLOCK && action.type() != PartyGameAction.Type.DROP_ITEM) return true;
         int choice = action.type() == PartyGameAction.Type.DROP_ITEM ? choiceFor(action.stack()) : Math.floorMod((action.block() == null ? 0 : action.block().getX()) + (action.block() == null ? 0 : action.block().getZ()), 3) + 1;
         if (choice == 0) return true;
         choices.put(player.getUUID(), choice); c.actionbar(player, "&e本轮选择：&f" + (char)('A' + choice - 1)); return true;
      }
      private int choiceFor(ItemStack stack) { if (stack.is(Items.PAPER)) return 1; if (stack.is(Items.SHEARS)) return 2; if (stack.is(Items.FEATHER)) return 3; return 0; }
      private void resolve() {
         int a = teamChoice(1), b = teamChoice(2);
         int[][] payoff = {{3, 5, 0}, {0, 3, 5}, {5, 0, 3}};
         c.addTeamScore(1, payoff[a - 1][b - 1]); c.addTeamScore(2, payoff[b - 1][a - 1]);
         choices.clear(); round++; roundTicks = 200;
         if (round > 6) { int one = c.teamScore(1), two = c.teamScore(2); if (one == two) c.draw("六轮结束，双方同分"); else c.winTeam(one > two ? 1 : 2, "六轮结束"); }
         else { giveChoices(); c.broadcast("&e第 " + round + "/6 轮：&f重新选择"); }
      }
      private int teamChoice(int team) {
         int[] count = new int[4];
         for (UUID id : c.teamMembers(team)) count[choices.getOrDefault(id, 1)]++;
         int best = 1; for (int i = 2; i <= 3; i++) if (count[i] > count[best]) best = i;
         return best;
      }
      @Override public void tick() { if (round > 6) return; if (choices.keySet().containsAll(c.seats()) || --roundTicks <= 0) resolve(); }
      @Override public void close() { choices.clear(); }
   }

   private static final class BossBrawl extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private final Map<UUID, Integer> arrowRecharge = new HashMap<>();
      private final List<Snowball> projectiles = new ArrayList<>();
      private UUID boss;
      BossBrawl(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); arrowRecharge.clear(); projectiles.clear(); boss = c.seats().get(c.random().nextInt(c.seats().size())); for (UUID id : c.seats()) roles.put(id, id.equals(boss) ? 1 : 2); }
      @Override public void start() {
         for (UUID id : c.seats()) { ServerPlayer player = p(id); survival(player); if (roles.get(id) == 1) { give(player, unbreakable(Items.DIAMOND_AXE, 1), 0); give(player, new ItemStack(Items.FIRE_CHARGE, 8), 1); give(player, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 4), 2); } else { give(player, unbreakable(Items.IRON_SWORD, 1), 0); give(player, new ItemStack(Items.BOW), 1); give(player, new ItemStack(Items.ARROW, 2), 2); arrowRecharge.put(id, 0); } }
         c.broadcast("&c首领乱斗开始！&f首领：" + c.color(boss).display() + "方");
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (roles.getOrDefault(player.getUUID(), 0) == 1 && (action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM)) {
             ItemStack stack = action.stack(); if (stack.is(Items.FIRE_CHARGE) || stack.is(Items.ZOMBIE_SPAWN_EGG)) { if (action.type() == PartyGameAction.Type.USE_ITEM && !consume(player, stack.getItem())) return true; Snowball projectile = new Snowball(c.level(), player); projectile.setDeltaMovement(player.getLookAngle().normalize().scale(1.25)); projectile.setGlowingTag(true); c.level().addFreshEntity(projectile); c.own(projectile); projectiles.add(projectile); return true; }
         }
         return action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof ServerPlayer victim ? roles.getOrDefault(player.getUUID(), 0) == roles.getOrDefault(victim.getUUID(), 0) : false;
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || roles.getOrDefault(sourcePlayer.getUUID(), 0) == roles.getOrDefault(victim.getUUID(), 0); }
      @Override public boolean death(ServerPlayer player) { int role = roles.getOrDefault(player.getUUID(), 0); eliminate(player, "首领乱斗淘汰"); if (aliveRole(roles, role) == 0) winRole(roles, role == 1 ? 2 : 1, "敌方角色组已被消灭"); return true; }
      @Override public void tick() {
         for (UUID id : c.seats()) if (roles.getOrDefault(id, 0) == 2 && c.alive(id)) {
            ServerPlayer player = p(id); if (player == null) continue;
            int ticks = arrowRecharge.getOrDefault(id, 0) + 1;
            if (ticks >= 160 && player.getInventory().countItem(Items.ARROW) < 2) { player.getInventory().add(new ItemStack(Items.ARROW)); ticks = 0; c.actionbar(player, "&e挑战者箭矢已补充"); }
            arrowRecharge.put(id, ticks);
         }
         for (Snowball projectile : List.copyOf(projectiles)) {
            if (projectile.isRemoved() || !c.inside(projectile.position())) { projectile.discard(); projectiles.remove(projectile); continue; }
            for (UUID id : c.seats()) { ServerPlayer target = p(id); if (target != null && roles.getOrDefault(id, 0) == 2 && target.position().distanceToSqr(projectile.position()) < 2.25) { projectile.discard(); projectiles.remove(projectile); target.setHealth(Math.max(0, target.getHealth() - 4)); c.actionbar(target, "&c被首领投射物击中"); if (target.getHealth() <= 0) { eliminate(target, "首领投射物击中"); if (aliveRole(roles, 2) == 0) winRole(roles, 1, "挑战者全员倒下"); } break; } }
         }
         if (aliveRole(roles, 1) == 0) winRole(roles, 2, "首领被击败"); else if (aliveRole(roles, 2) == 0) winRole(roles, 1, "挑战者全员倒下"); else clock();
      }
      @Override public void close() { projectiles.forEach(Entity::discard); projectiles.clear(); arrowRecharge.clear(); roles.clear(); boss = null; }
   }

   private static final class GoldRush extends Base {
      private final List<ItemEntity> drops = new ArrayList<>();
      private final Map<UUID, Boolean> sneaking = new HashMap<>();
      private final Map<UUID, Integer> bootTicks = new HashMap<>();
      GoldRush(PartyGameDefinition d) { super(d); }
      @Override public void start() { sneaking.clear(); bootTicks.clear(); for (UUID id : c.seats()) { sneaking.put(id, false); give(p(id), unbreakable(Items.DIAMOND_PICKAXE, 1), 0); } }
      private int value(ItemStack stack) { if (stack.is(Items.GOLD_NUGGET)) return 1; if (stack.is(Items.GOLD_INGOT)) return 3; if (stack.is(Items.GOLD_BLOCK)) return 6; if (stack.is(Items.GILDED_BLACKSTONE)) return 2; return 0; }
      private void drop() {
         if (c.level() == null) return; Vec3 point = center().add(c.random().nextInt(15) - 7, 4, c.random().nextInt(15) - 7); ItemStack stack = switch (c.random().nextInt(7)) { case 0 -> new ItemStack(Items.GOLD_NUGGET, 1); case 1 -> new ItemStack(Items.GOLD_INGOT, 1); case 2 -> new ItemStack(Items.GOLD_BLOCK, 1); case 3 -> new ItemStack(Items.GILDED_BLACKSTONE, 1); case 4 -> new ItemStack(Items.DIAMOND_BOOTS, 1); case 5 -> new ItemStack(Items.NETHERITE_BOOTS, 1); default -> new ItemStack(Items.SLIME_BALL, 1); }; ItemEntity entity = new ItemEntity(c.level(), point.x, point.y, point.z, stack); entity.setPickUpDelay(32767); entity.setUnlimitedLifetime(); c.level().addFreshEntity(entity); c.own(entity); drops.add(entity);
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.SNEAK) sneaking.put(player.getUUID(), action.active()); return true; }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { if (player == null || (!player.getMainHandItem().is(Items.DIAMOND_PICKAXE) && !player.getMainHandItem().is(Items.IRON_PICKAXE))) return true; int value = state.is(Blocks.GOLD_BLOCK) ? 6 : state.is(Blocks.GILDED_BLACKSTONE) || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) ? 2 : 0; if (value == 0) return true; c.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2); c.addScore(player.getUUID(), value); c.addTeamScore(c.team(player.getUUID()), value); c.actionbar(player, "&6+" + value + " 分"); return true; }
      @Override public void tick() {
         if (c.elapsedTicks() % 20 == 0 && drops.size() < 30) drop();
         for (ItemEntity item : List.copyOf(drops)) { if (item.isRemoved()) { drops.remove(item); continue; } ServerPlayer collector = c.seats().stream().map(this::p).filter(player -> player != null && sneaking.getOrDefault(player.getUUID(), false) && player.position().distanceToSqr(item.position()) < 2.25).findFirst().orElse(null); if (collector != null) { ItemStack stack = item.getItem(); int value = value(stack); if (value > 0) { c.addScore(collector.getUUID(), value); c.addTeamScore(c.team(collector.getUUID()), value); c.actionbar(collector, "&6拾取 +" + value + " 分"); } else if (stack.is(Items.DIAMOND_BOOTS)) { collector.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, unbreakable(Items.DIAMOND_BOOTS, 1)); bootTicks.put(collector.getUUID(), 100); collector.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 3)); c.actionbar(collector, "&b速度靴：速度提升 5 秒"); } else if (stack.is(Items.NETHERITE_BOOTS)) { collector.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, unbreakable(Items.NETHERITE_BOOTS, 1)); bootTicks.put(collector.getUUID(), 100); collector.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 100, 3)); c.actionbar(collector, "&a跳跃靴：跳跃提升 5 秒"); } else if (stack.is(Items.SLIME_BALL)) { collector.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 3)); collector.setDeltaMovement(0, Math.min(collector.getDeltaMovement().y, 0), 0); c.actionbar(collector, "&d踩中黏液，移动受阻"); } item.discard(); drops.remove(item); } }
         for (UUID id : List.copyOf(bootTicks.keySet())) { int left = bootTicks.merge(id, -1, Integer::sum); if (left <= 0) { ServerPlayer player = p(id); if (player != null && (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS) || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS))) player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY); bootTicks.remove(id); } }
         if (clock()) return; c.teamScore(1, teamTotal(1)); c.teamScore(2, teamTotal(2));
      }
      private int teamTotal(int team) { return c.teamMembers(team).stream().mapToInt(c::score).sum(); }
      @Override public void close() { drops.forEach(Entity::discard); drops.clear(); sneaking.clear(); bootTicks.clear(); }
   }

   private static final class BlockBuster extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>(); // 1=hider, 2=hunter
      private final Map<UUID, Display.BlockDisplay> disguises = new HashMap<>();
      private final Map<UUID, Integer> trackerTicks = new HashMap<>();
      private final Map<UUID, Vec3> lastPositions = new HashMap<>();
      private final Map<UUID, Integer> stillTicks = new HashMap<>();
      private final Set<UUID> solidified = new HashSet<>();
      private final Set<Block> disguiseBlocks = Set.of(Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.COBWEB,
         Blocks.OAK_LEAVES, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.OAK_WOOD);
      BlockBuster(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) {
         super.prepare(context); roles.clear(); disguises.clear(); trackerTicks.clear(); lastPositions.clear(); stillTicks.clear(); solidified.clear();
         for (int team = 1; team <= 2; team++) {
            List<UUID> members = c.teamMembers(team);
            if (members.size() > 1) roles.put(members.get(c.random().nextInt(members.size())), 2);
         }
         if (roles.isEmpty()) roles.put(c.seats().get(c.random().nextInt(c.seats().size())), 2);
         for (UUID id : c.seats()) roles.putIfAbsent(id, 1);
         if (aliveRole(roles, 1) == 0) {
            UUID hunter = firstAlive(roles, 2); if (hunter != null) roles.put(hunter, 1);
         }
      }
      @Override public void start() {
         for (UUID id : c.seats()) {
            ServerPlayer player = p(id); if (player == null) continue;
            if (roles.getOrDefault(id, 0) == 2) {
               give(player, unbreakable(Items.NETHERITE_SWORD, 1), 0);
               trackerTicks.put(id, 600);
            } else {
               player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, true, false));
               // The disguise must hide the player model; team identity remains
               // available through the room scoreboard and spectator HUD.
               for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) player.setItemSlot(slot, ItemStack.EMPTY);
               for (int slot = 0; slot < 8; slot++) give(player, new ItemStack(blockItem(slot)), slot);
               lastPositions.put(id, player.position()); stillTicks.put(id, 0);
               setDisguise(id, Blocks.SHORT_GRASS.defaultBlockState());
            }
         }
         c.broadcast("&e方块躲猫猫开始：&f躲藏者丢出快捷栏方块伪装，搜寻者攻击伪装体寻找目标");
      }
      private net.minecraft.world.item.Item blockItem(int slot) {
         return switch (slot) {
            case 0 -> Items.SHORT_GRASS; case 1 -> Items.DANDELION; case 2 -> Items.COBWEB; case 3 -> Items.OAK_LEAVES;
            case 4 -> Items.COARSE_DIRT; case 5 -> Items.COBBLESTONE; case 6 -> Items.GRAVEL; default -> Items.OAK_LOG;
         };
      }
      private void setDisguise(UUID owner, BlockState state) {
         ServerPlayer player = p(owner); if (player == null || c.level() == null) return;
         Display.BlockDisplay display = disguises.get(owner);
         if (display == null || display.isRemoved()) {
            display = EntityType.BLOCK_DISPLAY.create(c.level()); if (display == null) return;
            c.level().addFreshEntity(display); c.own(display); disguises.put(owner, display);
         }
         display.setBlockState(state); display.setPos(player.getX(), player.getY(), player.getZ());
         display.setYRot(player.getYRot()); display.setXRot(0);
      }
      private void removeDisguise(UUID owner) { Display.BlockDisplay display = disguises.remove(owner); if (display != null) display.discard(); }
      private UUID ownerOf(Display.BlockDisplay display) { return disguises.entrySet().stream().filter(entry -> entry.getValue() == display).map(Map.Entry::getKey).findFirst().orElse(null); }
      private BlockState stateFor(ItemStack stack) {
         Block block = Block.byItem(stack.getItem()); return disguiseBlocks.contains(block) ? block.defaultBlockState() : null;
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         UUID id = player.getUUID(); int role = roles.getOrDefault(id, 0);
         if (role == 1 && action.type() == PartyGameAction.Type.DROP_ITEM) {
            BlockState state = stateFor(action.stack());
            if (state != null) { setDisguise(id, state); removeDroppedItem(player, action.stack()); c.actionbar(player, "&a已伪装为 " + state.getBlock().getName().getString() + "，保持不动 2 秒后固化"); }
            return true;
         }
         if (role == 2 && action.type() == PartyGameAction.Type.ATTACK_ENTITY) {
            if (action.entity() instanceof Display.BlockDisplay display) {
               UUID owner = ownerOf(display); if (owner != null) { removeDisguise(owner); eliminate(p(owner), "被搜寻者找到"); checkRoles(); return true; }
            }
            if (action.entity() instanceof ServerPlayer victim && roles.getOrDefault(victim.getUUID(), 0) == 1) return false;
         }
         if (role == 2 && action.type() == PartyGameAction.Type.DROP_ITEM && action.stack().is(Items.NETHERITE_SWORD)) {
            int ready = trackerTicks.getOrDefault(id, 0);
            if (ready >= 600) {
               UUID nearest = c.seats().stream().filter(target -> roles.getOrDefault(target, 0) == 1 && c.alive(target))
                  .min(Comparator.comparingDouble(target -> p(target) == null ? Double.MAX_VALUE : p(target).distanceToSqr(player))).orElse(null);
               trackerTicks.put(id, 0);
               if (nearest != null) { ServerPlayer target = p(nearest); if (target != null) { target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, true, false)); c.actionbar(player, "&e追踪到最近目标，目标发光 3 秒"); c.sound(SoundEvents.NOTE_BLOCK_PLING.value(), .8F, 1.6F); } }
            } else c.actionbar(player, "&7追踪冷却：" + Math.max(0, (600 - ready) / 20) + " 秒");
            return true;
         }
         return true;
      }
      private void removeDroppedItem(ServerPlayer player, ItemStack stack) {
         if (c.level() == null || stack == null || stack.isEmpty()) return;
         AABB box = new AABB(player.position().subtract(1.5, 1.5, 1.5), player.position().add(1.5, 1.5, 1.5));
         c.level().getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getItem().is(stack.getItem())).stream().findFirst().ifPresent(Entity::discard);
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) {
         ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || roles.getOrDefault(sourcePlayer.getUUID(), 0) != 2 || roles.getOrDefault(victim.getUUID(), 0) != 1;
      }
      @Override public boolean death(ServerPlayer player) {
         int role = roles.getOrDefault(player.getUUID(), 0); eliminate(player, "方块躲猫猫淘汰"); removeDisguise(player.getUUID());
         if (role == 1) checkRoles(); else if (role == 2 && aliveRole(roles, 2) == 0) winRole(roles, 1, "所有搜寻者被淘汰"); return true;
      }
      private void checkRoles() { if (aliveRole(roles, 1) == 0) winRole(roles, 2, "所有躲藏者都被找到"); }
      @Override public void tick() {
         for (UUID id : c.seats()) {
            ServerPlayer player = p(id); if (player == null || !c.alive(id)) continue;
            if (roles.getOrDefault(id, 0) == 1) {
               Vec3 previous = lastPositions.put(id, player.position());
               int still = previous != null && previous.distanceToSqr(player.position()) < .0004 ? stillTicks.getOrDefault(id, 0) + 1 : 0;
               stillTicks.put(id, still);
               if (still >= 40) solidified.add(id); else solidified.remove(id);
               Display.BlockDisplay display = disguises.get(id); if (display == null || display.isRemoved()) setDisguise(id, Blocks.SHORT_GRASS.defaultBlockState()); else if (!solidified.contains(id)) display.setPos(player.getX(), player.getY(), player.getZ());
            }
            else trackerTicks.put(id, Math.min(600, trackerTicks.getOrDefault(id, 0) + 1));
         }
         checkRoles(); if (clock()) winRole(roles, 1, "躲藏者坚持到时间结束");
      }
      @Override public void close() { disguises.values().forEach(Entity::discard); disguises.clear(); trackerTicks.clear(); lastPositions.clear(); stillTicks.clear(); solidified.clear(); roles.clear(); }
   }

   private static final class PacCube extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private final Map<UUID, Integer> lives = new HashMap<>();
      private final Map<UUID, Integer> powerTicks = new HashMap<>();
      private final List<ItemEntity> pellets = new ArrayList<>();
      private UUID ghostId;
      private int ghostRespawnTicks;
      PacCube(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); lives.clear(); powerTicks.clear(); ghostRespawnTicks = 0; ghostId = c.seats().get(c.random().nextInt(c.seats().size())); for (UUID id : c.seats()) { roles.put(id, id.equals(ghostId) ? 2 : 1); lives.put(id, 2); } }
      @Override public void start() { for (UUID id : c.seats()) { ServerPlayer player = p(id); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, -1, roles.get(id) == 1 ? 1 : 0, true, false)); if (roles.get(id) == 2) give(player, unbreakable(Items.IRON_SWORD, 1), 0); } for (int i = 0; i < 32; i++) spawnPellet(); }
      private void spawnPellet() { Vec3 point = center().add(c.random().nextInt(25) - 12, 1, c.random().nextInt(25) - 12); ItemEntity item = new ItemEntity(c.level(), point.x, point.y, point.z, new ItemStack(Items.SLIME_BALL)); item.setPickUpDelay(32767); item.setUnlimitedLifetime(); c.level().addFreshEntity(item); c.own(item); pellets.add(item); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof ServerPlayer target) {
            // PvP is controller-owned: a ghost hit consumes a life through
            // collision below, while a powered pacman can knock the ghost back.
            if (roles.getOrDefault(player.getUUID(), 0) == 1 && roles.getOrDefault(target.getUUID(), 0) == 2 && powerTicks.getOrDefault(player.getUUID(), 0) > 0) push(target, player, .8);
            return true;
         }
         return true;
      }
      private void eatGhost(ServerPlayer ghost, ServerPlayer pacman) {
         if (ghost == null || ghostRespawnTicks > 0) return;
         ghostRespawnTicks = 200; c.alive(ghost.getUUID(), false); ghost.setGameMode(GameType.SPECTATOR);
         c.actionbar(pacman, "&e能量豆击中幽灵，幽灵 10 秒后重生"); c.actionbar(ghost, "&c你被能量豆击中，10 秒后重生");
      }
      private void respawnGhost() {
         ServerPlayer ghost = p(ghostId); if (ghost == null) return;
         c.alive(ghostId, true); survival(ghost); teleport(ghost, spawn(c.team(ghostId)), c.team(ghostId) == 1 ? -90 : 90); give(ghost, unbreakable(Items.IRON_SWORD, 1), 0); ghostRespawnTicks = 0;
      }
      private void hitPacman(ServerPlayer player) { int left = lives.merge(player.getUUID(), -1, Integer::sum); if (left <= 0) { eliminate(player, "被幽灵吃掉"); if (aliveRole(roles, 1) == 0) winRole(roles, 2, "幽灵吃掉所有吃豆人"); } else { teleport(player, spawn(c.team(player.getUUID())), c.team(player.getUUID()) == 1 ? -90 : 90); c.actionbar(player, "&c被吃掉了，还剩 " + left + " 条命"); } }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || roles.getOrDefault(sourcePlayer.getUUID(), 0) != 2 || roles.getOrDefault(victim.getUUID(), 0) != 1; }
      @Override public void tick() {
         powerTicks.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
         if (ghostRespawnTicks > 0 && --ghostRespawnTicks == 0) respawnGhost();
         for (ItemEntity pellet : List.copyOf(pellets)) { if (pellet.isRemoved()) { pellets.remove(pellet); continue; } ServerPlayer runner = c.seats().stream().map(this::p).filter(player -> player != null && roles.getOrDefault(player.getUUID(), 0) == 1 && player.position().distanceToSqr(pellet.position()) < 2.25).findFirst().orElse(null); if (runner != null) { c.addScore(runner.getUUID(), 1); c.addTeamScore(c.team(runner.getUUID()), 1); powerTicks.put(runner.getUUID(), 200); c.actionbar(runner, "&e能量豆：幽灵暂时可被击退"); pellet.discard(); pellets.remove(pellet); } }
         ServerPlayer ghost = p(firstAlive(roles, 2)); if (ghost != null) for (UUID id : c.seats()) { ServerPlayer runner = p(id); if (runner != null && roles.getOrDefault(id, 0) == 1 && runner.position().distanceToSqr(ghost.position()) < 2.25) { if (powerTicks.getOrDefault(id, 0) > 0) eatGhost(ghost, runner); else hitPacman(runner); } }
         if (pellets.isEmpty() && aliveRole(roles, 1) > 0) { winRole(roles, 1, "吃掉所有能量球"); return; } if (clock()) return; if (aliveRole(roles, 1) == 0) winRole(roles, 2, "所有吃豆人都已淘汰");
      }
      @Override public void close() { pellets.forEach(Entity::discard); pellets.clear(); roles.clear(); lives.clear(); powerTicks.clear(); ghostId = null; ghostRespawnTicks = 0; }
   }

   private static final class GhostHunt extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private final List<Villager> villagers = new ArrayList<>();
      GhostHunt(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); UUID hunter = c.seats().get(c.random().nextInt(c.seats().size())); for (UUID id : c.seats()) roles.put(id, id.equals(hunter) ? 2 : 1); }
      @Override public void start() { for (UUID id : c.seats()) { ServerPlayer player = p(id); if (roles.get(id) == 1) { player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, true, false)); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, true, false)); } else give(player, unbreakable(Items.IRON_SWORD, 1), 0); } for (int i = 0; i < 6; i++) { Villager villager = new Villager(EntityType.VILLAGER, c.level()); Vec3 point = center().add((i % 3) * 7 - 7, 1, (i / 3) * 7 - 3); villager.moveTo(point.x, point.y, point.z); villager.setNoAi(true); villager.setInvulnerable(true); villager.setCustomName(TextUtil.color("&f村民")); villager.setCustomNameVisible(true); c.level().addFreshEntity(villager); c.own(villager); villagers.add(villager); } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof Villager villager && roles.getOrDefault(player.getUUID(), 0) == 1 && villagers.contains(villager)) { Vec3 at = villager.position(); villager.discard(); villagers.remove(villager); Zombie zombie = EntityType.ZOMBIE.create(c.level()); if (zombie != null) { zombie.moveTo(at.x, at.y, at.z); zombie.setNoAi(true); zombie.setInvulnerable(true); zombie.setCustomName(TextUtil.color("&7已转化")); zombie.setCustomNameVisible(true); c.level().addFreshEntity(zombie); c.own(zombie); } c.addTeamScore(c.team(player.getUUID()), 1); c.sound(SoundEvents.ZOMBIE_VILLAGER_CURE, .4F, .7F); return true; } return action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof ServerPlayer target && roles.getOrDefault(player.getUUID(), 0) == roles.getOrDefault(target.getUUID(), 0); }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || roles.getOrDefault(sourcePlayer.getUUID(), 0) != 2 || roles.getOrDefault(victim.getUUID(), 0) != 1; }
      @Override public boolean death(ServerPlayer player) { int role = roles.getOrDefault(player.getUUID(), 0); eliminate(player, "幽灵猎手淘汰"); if (aliveRole(roles, role) == 0) winRole(roles, role == 1 ? 2 : 1, "敌方角色组已清空"); return true; }
      @Override public void tick() { if (villagers.isEmpty()) { winRole(roles, 1, "村民全部被消灭"); return; } if (aliveRole(roles, 1) == 0) { winRole(roles, 2, "所有幽灵被猎杀"); return; } if (clock()) { winRole(roles, 2, "太阳升起，村民获救"); } }
      @Override public void close() { villagers.forEach(Entity::discard); villagers.clear(); roles.clear(); }
   }

   private static final class TreetopHop extends Base {
      TreetopHop(PartyGameDefinition d) { super(d); }
      @Override public void start() { for (UUID id : c.seats()) { ServerPlayer player = p(id); survival(player); give(player, new ItemStack(c.team(id) == 1 ? Items.CROSSBOW : Items.BOW), 0); give(player, new ItemStack(Items.ARROW, 64), 1); } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && enemy(player, action.entity())) return false;
         // Let vanilla bow/crossbow charging and firing run; this controller
         // only owns team damage and the fall/respawn rule.
         if (action.type() == PartyGameAction.Type.USE_ITEM && (action.stack().is(Items.BOW) || action.stack().is(Items.CROSSBOW))) return false;
         return true;
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || c.team(sourcePlayer.getUUID()) == c.team(victim.getUUID()); }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "从树冠坠落"); if (!c.teamAlive(c.team(player.getUUID()))) c.winTeam(c.team(player.getUUID()) == 1 ? 2 : 1, "敌方团队全部淘汰"); return true; }
      @Override public void tick() { for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player == null || !c.alive(id)) continue; if (player.getY() < c.arena().floorY() - 2) teleport(player, spawn(c.team(id)), c.team(id) == 1 ? -90 : 90); if (player.getInventory().countItem(Items.ARROW) < 8) give(player, new ItemStack(Items.ARROW, 32), 1); } if (c.teamAlive(1) && c.teamAlive(2)) { if (clock()) { int one = c.livingCount(1), two = c.livingCount(2); if (one == two) c.draw("时间到，双方仍有 " + one + " 人存活"); else c.winTeam(one > two ? 1 : 2, "时间到，存活人数更多"); } } }
   }

   private static final class SlimeTime extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private Slime slime;
      SlimeTime(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); UUID controller = c.seats().get(c.random().nextInt(c.seats().size())); for (UUID id : c.seats()) roles.put(id, id.equals(controller) ? 1 : 2); }
      @Override public void start() {
         for (UUID id : c.seats()) if (roles.get(id) == 2) {
            ServerPlayer runner = p(id); if (runner == null) continue;
            runner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, -1, 1, true, false));
            give(runner, new ItemStack(Items.FEATHER, 2), 1); give(runner, new ItemStack(Items.GLASS), 2); give(runner, new ItemStack(Items.ENDER_EYE), 3);
         }
         slime = new Slime(EntityType.SLIME, c.level()); slime.setSize(3, true); slime.setNoAi(true); slime.setInvulnerable(true); slime.setGlowingTag(true); slime.moveTo(center()); c.level().addFreshEntity(slime); c.own(slime); teamName(c.team(firstAlive(roles, 1)), slime, "史莱姆");
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int role = roles.getOrDefault(player.getUUID(), 0);
         if (role == 2 && action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() == slime) { push(slime, player, 1.1); return true; }
         if (role == 2 && action.type() == PartyGameAction.Type.USE_ITEM) {
            if (action.stack().is(Items.FEATHER)) { if (consume(player, Items.FEATHER)) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 3)); return true; }
            if (action.stack().is(Items.GLASS)) { if (consume(player, Items.GLASS)) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 100, 0)); return true; }
            if (action.stack().is(Items.ENDER_EYE)) {
               if (!consume(player, Items.ENDER_EYE)) return true;
               ServerPlayer teammate = c.teamMembers(c.team(player.getUUID())).stream().map(this::p).filter(other -> other != null && !other.equals(player) && c.alive(other.getUUID())).findFirst().orElse(null);
               if (teammate != null) teleport(player, teammate.position().add(0, 0.2, 0), teammate.getYRot());
               return true;
            }
         }
         if (role == 1 && (action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM) && slime != null) { slime.setDeltaMovement(player.getLookAngle().normalize().scale(.25)); return true; }
         return true;
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { return true; }
      @Override public void tick() { if (slime == null || slime.isRemoved()) return; ServerPlayer controller = p(firstAlive(roles, 1)); if (controller != null && slime.position().distanceToSqr(controller.position()) > 36) slime.setDeltaMovement(controller.position().subtract(slime.position()).normalize().scale(.18)); for (UUID id : c.seats()) { ServerPlayer runner = p(id); if (runner != null && roles.getOrDefault(id, 0) == 2 && runner.position().distanceToSqr(slime.position()) < 9) eliminate(runner, "被史莱姆吞噬"); } if (aliveRole(roles, 2) == 0) winRole(roles, 1, "史莱姆吞噬所有逃生者"); else if (clock()) winRole(roles, 2, "逃生者坚持到时间结束"); }
      @Override public void close() { if (slime != null) slime.discard(); roles.clear(); slime = null; }
   }

   private static final class InTheZone extends Base {
      private int progress;
      InTheZone(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); progress = 0; }
      @Override public void start() { for (UUID id : c.seats()) give(p(id), unbreakable(Items.STICK, 1), 0); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof ServerPlayer target && enemy(player, target)) { push(target, player, 0.7); return true; } return true; }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || c.team(sourcePlayer.getUUID()) == c.team(victim.getUUID()); }
      @Override public void tick() { Vec3 zone = c.anchor("zone", (c.arena().maxX() - c.arena().minX()) / 2.0, 1, (c.arena().maxZ() - c.arena().minZ()) / 2.0); int one = 0, two = 0; for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player == null || !c.alive(id) || player.position().distanceToSqr(zone) > 36) continue; if (c.team(id) == 1) one++; else two++; } progress = Math.max(-160, Math.min(160, progress + Integer.compare(one, two))); c.teamScore(1, Math.max(0, progress)); c.teamScore(2, Math.max(0, -progress)); if (progress >= 160) c.winTeam(1, "蓝方占领据点"); else if (progress <= -160) c.winTeam(2, "红方占领据点"); else clock(); }
   }

   private static final class GhastBlast extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private final List<Snowball> projectiles = new ArrayList<>();
      private Ghast ghast;
      private int ghastHealth = 12;
      private boolean pilotDescending;
      GhastBlast(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); projectiles.clear(); ghastHealth = 12; pilotDescending = false; UUID pilot = c.seats().get(c.random().nextInt(c.seats().size())); for (UUID id : c.seats()) roles.put(id, id.equals(pilot) ? 1 : 2); }
      @Override public void start() { ServerPlayer pilot = p(firstAlive(roles, 1)); if (pilot != null) { give(pilot, new ItemStack(Items.FIRE_CHARGE, 16), 0); give(pilot, unbreakable(Items.IRON_SWORD, 1), 1); } for (UUID id : c.seats()) if (roles.get(id) == 2) { give(p(id), unbreakable(Items.IRON_SWORD, 1), 0); give(p(id), new ItemStack(Items.BOW), 1); give(p(id), new ItemStack(Items.ARROW, 16), 2); } ghast = new Ghast(EntityType.GHAST, c.level()); ghast.setNoAi(true); ghast.setInvulnerable(true); ghast.setGlowingTag(true); ghast.moveTo(center().x, center().y + 6, center().z); c.level().addFreshEntity(ghast); c.own(ghast); teamName(c.team(firstAlive(roles, 1)), ghast, "恶魂"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int role = roles.getOrDefault(player.getUUID(), 0);
         if (role == 1 && action.type() == PartyGameAction.Type.SNEAK) { pilotDescending = action.active(); return true; }
         if (role == 1 && (action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM) && action.stack().is(Items.FIRE_CHARGE)) { if (action.type() == PartyGameAction.Type.USE_ITEM && !consume(player, Items.FIRE_CHARGE)) return true; Snowball ball = new Snowball(c.level(), player); ball.setDeltaMovement(player.getLookAngle().normalize().scale(1.1)); ball.setGlowingTag(true); c.level().addFreshEntity(ball); c.own(ball); projectiles.add(ball); return true; }
         if (role == 2 && action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() == ghast) { ghastHealth--; c.actionbar(player, "&e恶魂生命：&f" + Math.max(0, ghastHealth) + "/12"); if (ghastHealth <= 0) winRole(roles, 2, "幸存者击败恶魂"); return true; }
         if (role == 2 && action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() instanceof Snowball ball && projectiles.contains(ball)) { ball.setDeltaMovement(player.getLookAngle().normalize().scale(1.3)); return true; }
         return role == 1 && action.type() == PartyGameAction.Type.ATTACK_ENTITY;
      }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { ServerPlayer sourcePlayer = attacker(source); return sourcePlayer == null || roles.getOrDefault(sourcePlayer.getUUID(), 0) == roles.getOrDefault(victim.getUUID(), 0); }
      @Override public boolean death(ServerPlayer player) { int role = roles.getOrDefault(player.getUUID(), 0); eliminate(player, "恶魂爆破淘汰"); if (aliveRole(roles, role) == 0) winRole(roles, role == 1 ? 2 : 1, "敌方角色组已清空"); return true; }
      @Override public void tick() { ServerPlayer pilot = p(firstAlive(roles, 1)); if (pilot != null && ghast != null) { Vec3 horizontal = pilot.position().subtract(ghast.position()); double vy = pilotDescending ? -.12 : .12; if (horizontal.lengthSqr() > 25) ghast.setDeltaMovement(horizontal.normalize().scale(.18).add(0, vy, 0)); else ghast.setDeltaMovement(0, vy, 0); } for (Snowball ball : List.copyOf(projectiles)) { if (ball.isRemoved() || !c.inside(ball.position())) { ball.discard(); projectiles.remove(ball); continue; } for (UUID id : c.seats()) { ServerPlayer survivor = p(id); if (survivor != null && roles.getOrDefault(id, 0) == 2 && survivor.position().distanceToSqr(ball.position()) < 2.25) { ball.discard(); projectiles.remove(ball); survivor.setHealth(Math.max(0, survivor.getHealth() - 5)); c.actionbar(survivor, "&c恶魂火球命中"); if (survivor.getHealth() <= 0) { eliminate(survivor, "被恶魂火球击倒"); if (aliveRole(roles, 2) == 0) winRole(roles, 1, "所有幸存者已倒下"); } break; } } } for (UUID id : c.seats()) { ServerPlayer survivor = p(id); if (survivor != null && roles.getOrDefault(id, 0) == 2 && ghast != null && survivor.position().distanceToSqr(ghast.position()) < 9) { winRole(roles, 1, "恶魂接近并击杀幸存者"); return; } } if (aliveRole(roles, 2) == 0) winRole(roles, 1, "所有幸存者已倒下"); else if (clock()) winRole(roles, 2, "幸存者存活到时间结束"); }
      @Override public void close() { projectiles.forEach(Entity::discard); projectiles.clear(); if (ghast != null) ghast.discard(); roles.clear(); ghast = null; pilotDescending = false; }
   }

   private static final class Eggcellence extends Base {
      private final Map<Integer, Set<BlockPos>> cells = new HashMap<>();
      private final Map<Integer, Map<BlockPos, Boolean>> target = new HashMap<>();
      private final Map<Integer, Map<BlockPos, Boolean>> state = new HashMap<>();
      private final Map<Integer, List<ItemFrame>> frames = new HashMap<>();
      private final Map<Integer, List<ItemStack>> frameTarget = new HashMap<>();
      private final Map<Integer, Integer> correct = new HashMap<>();
      private static final List<net.minecraft.world.item.Item> EGG_TYPES = List.of(Items.CHICKEN_SPAWN_EGG, Items.COW_SPAWN_EGG, Items.PIG_SPAWN_EGG, Items.SHEEP_SPAWN_EGG, Items.ZOMBIE_SPAWN_EGG, Items.SKELETON_SPAWN_EGG, Items.CREEPER_SPAWN_EGG, Items.SPIDER_SPAWN_EGG);
      Eggcellence(PartyGameDefinition d) { super(d); }
      @Override public void start() {
         cells.clear(); target.clear(); state.clear(); frames.clear(); frameTarget.clear(); correct.clear();
         List<ItemFrame> blueFrames = findFrames(1), redFrames = findFrames(2);
         if (blueFrames.size() >= 2 && blueFrames.size() == redFrames.size()) {
            startItemFrames(blueFrames, redFrames); c.broadcast("&e两面刷怪蛋墙已生成：&f把对应位置排列成完全一致"); return;
         }
         // Both boards use one deterministic target pattern.  The initial
         // state is deliberately scrambled per team, so a match requires
         // solving the wall rather than simply clicking twelve times.
         boolean[] pattern = new boolean[12];
         for (int i = 0; i < pattern.length; i++) pattern[i] = c.random().nextBoolean();
         for (int team = 1; team <= 2; team++) {
            Set<BlockPos> teamCells = new HashSet<>();
            Map<BlockPos, Boolean> teamTarget = new HashMap<>();
            Map<BlockPos, Boolean> teamState = new HashMap<>();
            BlockPos origin = c.anchorBlock(team == 1 ? "blue_board" : "red_board", team == 1 ? 12 : 84, 5, 48);
            int matches = 0;
            for (int i = 0; i < pattern.length; i++) {
               BlockPos pos = origin.offset(i % 4, 0, i / 4).immutable();
               boolean initial = c.random().nextBoolean();
               // Avoid an unwinnable-looking board while still guaranteeing
               // that the two boards do not start in the solved state.
               if (i == pattern.length - 1 && initial == pattern[i] && matches == pattern.length - 1) initial = !initial;
               teamCells.add(pos); teamTarget.put(pos, pattern[i]); teamState.put(pos, initial);
               if (initial == pattern[i]) matches++;
               c.level().setBlock(pos, initial ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.BROWN_WOOL.defaultBlockState(), 2);
            }
            cells.put(team, teamCells); target.put(team, teamTarget); state.put(team, teamState); correct.put(team, matches); c.teamScore(team, matches);
         }
         c.broadcast("&e两面鸡蛋墙已生成：&f把自己一侧排列成相同图案");
      }
      private List<ItemFrame> findFrames(int team) {
         Vec3 anchor = c.anchor(team == 1 ? "blue_board" : "red_board", team == 1 ? 12 : 84, 5, 48);
         AABB box = new AABB(anchor.x - 12, anchor.y - 10, anchor.z - 12, anchor.x + 12, anchor.y + 10, anchor.z + 12);
         List<ItemFrame> result = c.level().getEntitiesOfClass(ItemFrame.class, box, frame -> !frame.isRemoved());
         result.sort(Comparator.comparingInt((ItemFrame frame) -> frame.blockPosition().getY()).thenComparingInt(frame -> frame.blockPosition().getZ()).thenComparingInt(frame -> frame.blockPosition().getX()));
         return result.size() > 64 ? new ArrayList<>(result.subList(0, 64)) : result;
      }
      private void startItemFrames(List<ItemFrame> blue, List<ItemFrame> red) {
         frames.put(1, blue); frames.put(2, red);
         List<ItemStack> pattern = new ArrayList<>();
         for (int i = 0; i < blue.size(); i++) pattern.add(new ItemStack(EGG_TYPES.get(c.random().nextInt(EGG_TYPES.size()))));
         for (int team = 1; team <= 2; team++) {
            List<ItemFrame> board = frames.get(team); List<ItemStack> goals = new ArrayList<>(); int matches = 0;
            for (int i = 0; i < board.size(); i++) {
               ItemStack goal = pattern.get(i).copy(); ItemStack initial = new ItemStack(EGG_TYPES.get(c.random().nextInt(EGG_TYPES.size())));
               if (initial.is(goal.getItem()) && i == board.size() - 1) initial = new ItemStack(EGG_TYPES.get((EGG_TYPES.indexOf(goal.getItem()) + 1) % EGG_TYPES.size()));
               board.get(i).setItem(initial); goals.add(goal); if (initial.is(goal.getItem())) matches++;
            }
            frameTarget.put(team, goals); correct.put(team, matches); c.teamScore(team, matches);
         }
      }
      private int frameScore(int team) {
         List<ItemFrame> board = frames.getOrDefault(team, List.of()); List<ItemStack> goals = frameTarget.getOrDefault(team, List.of()); int score = 0;
         for (int i = 0; i < Math.min(board.size(), goals.size()); i++) if (board.get(i).getItem().is(goals.get(i).getItem())) score++;
         return score;
      }
      private ItemStack nextEgg(ItemStack current) {
         int index = -1; for (int i = 0; i < EGG_TYPES.size(); i++) if (current.is(EGG_TYPES.get(i))) { index = i; break; }
         return new ItemStack(EGG_TYPES.get((index + 1 + EGG_TYPES.size()) % EGG_TYPES.size()));
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int team = c.team(player.getUUID());
         if (action.type() == PartyGameAction.Type.USE_ENTITY && action.entity() instanceof ItemFrame frame && frames.getOrDefault(team, List.of()).contains(frame)) {
            frame.setItem(nextEgg(frame.getItem())); int score = frameScore(team); correct.put(team, score); c.teamScore(team, score); c.actionbar(player, "&e正确格数：&f" + score + "/" + frames.get(team).size());
            if (score == frames.get(team).size()) c.winTeam(team, "完成一模一样的刷怪蛋墙"); return true;
         }
         BlockPos pos = action.block();
         if (action.type() == PartyGameAction.Type.USE_ENTITY && action.entity() != null) pos = action.entity().blockPosition();
         if (pos == null || !cells.getOrDefault(team, Set.of()).contains(pos)) return true;
         Map<BlockPos, Boolean> teamState = state.get(team);
         boolean next = !teamState.getOrDefault(pos, false); teamState.put(pos, next);
         c.level().setBlock(pos, next ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.BROWN_WOOL.defaultBlockState(), 2);
         int score = (int) teamState.entrySet().stream().filter(entry -> entry.getValue().equals(target.get(team).get(entry.getKey()))).count();
         correct.put(team, score); c.teamScore(team, score); c.actionbar(player, "&e正确格数：&f" + score + "/12");
         if (score == 12) c.winTeam(team, "完成一模一样的鸡蛋墙");
         return true;
      }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { return true; }
      @Override public void tick() { if (!frames.isEmpty()) { for (int team = 1; team <= 2; team++) { int score = frameScore(team); correct.put(team, score); c.teamScore(team, score); } } if (clock()) { if (correct.getOrDefault(1, 0) == correct.getOrDefault(2, 0)) c.draw("时间到，双方排列进度相同"); else c.winTeam(correct.getOrDefault(1, 0) > correct.getOrDefault(2, 0) ? 1 : 2, "正确排列格数更多"); } }
      @Override public void close() { cells.clear(); target.clear(); state.clear(); frames.clear(); frameTarget.clear(); correct.clear(); }
   }

   private static final class RavagerRodeo extends Base {
      private Ravager ravager;
      private int dashCooldown;
      RavagerRodeo(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); dashCooldown = 0; }
      @Override public void start() { for (UUID id : c.seats()) { survival(p(id)); give(p(id), unbreakable(Items.STICK, 1), 0); give(p(id), new ItemStack(Items.SUGAR, 3), 1); } ravager = new Ravager(EntityType.RAVAGER, c.level()); ravager.setNoAi(true); ravager.setInvulnerable(true); ravager.setGlowingTag(true); ravager.moveTo(center()); c.level().addFreshEntity(ravager); c.own(ravager); teamName(1, ravager, "劫掠兽"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { if (action.type() == PartyGameAction.Type.USE_ENTITY && action.entity() == ravager) { if (!ravager.getPassengers().contains(player)) player.startRiding(ravager, true); teamName(c.team(player.getUUID()), ravager, "劫掠兽"); c.actionbar(player, "&e你已骑上劫掠兽"); return true; } if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() == ravager) { push(ravager, player, 0.7); return true; } if ((action.type() == PartyGameAction.Type.JUMP || action.type() == PartyGameAction.Type.USE_ITEM && action.stack().is(Items.SUGAR)) && ravager != null && ravager.getPassengers().contains(player) && dashCooldown <= 0) { if (action.type() == PartyGameAction.Type.USE_ITEM && !consume(player, Items.SUGAR)) return true; dashCooldown = 200; push(ravager, player, 1.3); c.actionbar(player, "&e冲刺！冷却 10 秒"); return true; } return true; }
      @Override public boolean damage(ServerPlayer victim, DamageSource source) { return true; }
      @Override public boolean death(ServerPlayer player) { eliminate(player, "被劫掠兽撞出场地"); if (!c.teamAlive(c.team(player.getUUID()))) c.winTeam(c.team(player.getUUID()) == 1 ? 2 : 1, "敌方团队全部出界"); return true; }
      @Override public void tick() { if (ravager == null || ravager.isRemoved()) return; dashCooldown = Math.max(0, dashCooldown - 1); Entity rider = ravager.getFirstPassenger(); int riderTeam = rider instanceof ServerPlayer riderPlayer ? c.team(riderPlayer.getUUID()) : 0; if (rider instanceof ServerPlayer player) { Vec3 look = player.getLookAngle().multiply(1, 0, 1); if (look.lengthSqr() > .01) { look = look.normalize(); ravager.setDeltaMovement(look.scale(.2)); ravager.setYRot(player.getYRot()); } } if (c.elapsedTicks() % 30 == 0) for (UUID id : c.seats()) { ServerPlayer player = p(id); if (player != null && riderTeam != 0 && player.position().distanceToSqr(ravager.position()) < 20 && c.team(id) != riderTeam) { Vec3 away = player.position().subtract(ravager.position()).normalize(); player.setDeltaMovement(away.x * .8, .55, away.z * .8); player.hurtMarked = true; } if (player != null && player.getY() < c.arena().floorY() - 2) eliminate(player, "冲出竞技场"); } if (!c.teamAlive(1)) c.winTeam(2, "蓝方全员出界"); else if (!c.teamAlive(2)) c.winTeam(1, "红方全员出界"); else if (clock()) { int one = c.livingCount(1), two = c.livingCount(2); if (one == two) c.draw("时间到，双方存活人数相同"); else c.winTeam(one > two ? 1 : 2, "时间到，存活人数更多"); } }
      @Override public void close() { if (ravager != null) ravager.discard(); ravager = null; }
   }

   private static final class MouseTrap extends Base {
      private final Map<UUID, Integer> roles = new HashMap<>();
      private final Map<UUID, Integer> axeUses = new HashMap<>();
      private final Set<BlockPos> traps = new HashSet<>();
      MouseTrap(PartyGameDefinition d) { super(d); }
      @Override public void prepare(TeamPartyMatchContext context) { super.prepare(context); roles.clear(); axeUses.clear(); traps.clear(); for (UUID id : c.seats()) roles.put(id, c.team(id) == 1 ? 1 : 2); }
      @Override public void start() { for (UUID id : c.seats()) { ServerPlayer player = p(id); if (roles.get(id) == 1) give(player, new ItemStack(Items.EGG, 64), 0); else { give(player, unbreakable(Items.STONE_AXE, 1), 0); player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, -1, 2, true, false)); } } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int role = roles.getOrDefault(player.getUUID(), 0); if (role == 2 && action.type() == PartyGameAction.Type.JUMP) return true;
         if (role == 1 && (action.type() == PartyGameAction.Type.USE_ITEM || action.type() == PartyGameAction.Type.DROP_ITEM) && action.stack().is(Items.EGG)) { if (action.type() == PartyGameAction.Type.USE_ITEM && !consume(player, Items.EGG)) return true; BlockPos pos = BlockPos.containing(player.position().add(player.getLookAngle().normalize().scale(2))); if (c.arena().inPlay(pos)) { c.level().setBlock(pos, Blocks.CRIMSON_HYPHAE.defaultBlockState(), 2); traps.add(pos.immutable()); } return true; }
         return true;
      }
      @Override public boolean cancelJump(ServerPlayer player) {
         if (roles.getOrDefault(player.getUUID(), 0) != 2) return false;
         c.actionbar(player, "&c老鼠不能跳跃"); return true;
      }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { if (roles.getOrDefault(player.getUUID(), 0) == 2 && traps.contains(pos) && player.getMainHandItem().is(Items.STONE_AXE)) { int uses = axeUses.merge(player.getUUID(), 1, Integer::sum); if (uses <= 3) { c.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2); traps.remove(pos); c.actionbar(player, "&a破坏陷阱 " + uses + "/3"); } return true; } return true; }
      private boolean trapped(ServerPlayer player) { BlockPos pos = player.blockPosition(); return traps.contains(pos) && traps.contains(pos.east()) && traps.contains(pos.west()) && traps.contains(pos.north()) && traps.contains(pos.south()); }
      @Override public void tick() { for (UUID id : c.seats()) { ServerPlayer mouse = p(id); if (mouse != null && roles.getOrDefault(id, 0) == 2 && trapped(mouse)) eliminate(mouse, "被困在 1×1 陷阱"); } if (aliveRole(roles, 2) == 0) winRole(roles, 1, "所有老鼠都被困住"); else if (clock()) winRole(roles, 2, "老鼠坚持到时间结束"); }
      @Override public void close() { roles.clear(); axeUses.clear(); traps.clear(); }
   }
}
