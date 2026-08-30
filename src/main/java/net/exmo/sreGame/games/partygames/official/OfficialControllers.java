package net.exmo.sreGame.games.partygames.official;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.exmo.sreGame.games.partygames.PartyGameType;
import net.exmo.sreGame.games.partygames.api.PartyColor;
import net.exmo.sreGame.games.partygames.api.PartyGameAction;
import net.exmo.sreGame.games.partygames.api.PartyGameController;
import net.exmo.sreGame.games.partygames.api.PartyGameDefinition;
import net.exmo.sreGame.games.partygames.api.PartyMatchContext;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Controller implementations for the room-safe 101-114 catalogue. */
final class OfficialControllers {
   private OfficialControllers() { }

   static PartyGameController create(PartyGameDefinition definition) {
      if (definition == null) throw new IllegalArgumentException("Missing official party definition");
      return switch (definition.type()) {
         case MINIONS -> new Minions(definition);
         case RING_IN_THE_RING -> new Ring(definition);
         case GLADIATOR_FIGHT -> new Gladiator(definition);
         case TURTLE_HOCKEY -> new TurtleHockey(definition);
         case GO_FISH -> new Fishing(definition);
         case DONT_PUSH_MY_BUTTONS -> new Buttons(definition);
         case BRIDGE_CROSSING -> new Bridge(definition);
         case PIG_PUSHERS -> new PigPushers(definition);
         case BALANCE_BEAM -> new BalanceBeam(definition);
         case BUTTON_SEARCH -> new ButtonSearch(definition);
         case BETRIS -> new Betris(definition);
         case DEUCE -> new Deuce(definition);
         case DECRYPTION -> new Decryption(definition);
         case CANNONEERS -> new Cannoneers(definition);
         default -> throw new IllegalArgumentException("Not an official duel: " + definition.type());
      };
   }

   private abstract static class Base implements PartyGameController {
      protected final PartyGameDefinition definition;
      protected PartyMatchContext c;
      protected int remaining;

      Base(PartyGameDefinition definition) { this.definition = definition; }
      @Override public PartyGameDefinition definition() { return definition; }
      @Override public void prepare(PartyMatchContext context) { this.c = context; this.remaining = definition.fixedDurationTicks(); }
      @Override public void start() { }
      protected boolean clock() {
         if (definition.fixedDurationTicks() <= 0) return false;
         if (--remaining > 0) return false;
         timeout(); return true;
      }
      protected void timeout() { finishByScore("时间到"); }
      protected void finishByScore(String reason) {
         UUID a = c.seats().get(0), b = c.seats().get(1);
         if (c.score(a) == c.score(b)) c.draw(reason + "，双方平局");
         else c.win(c.score(a) > c.score(b) ? a : b, reason);
      }
      protected ServerPlayer p(int seat) { return c.player(c.seats().get(seat)); }
      protected UUID id(int seat) { return c.seats().get(seat); }
      protected int seat(UUID id) { return c.seat(id); }
      protected Vec3 spawn(int seat) { return c.anchor(seat == 0 ? "blue_spawn" : "red_spawn", seat == 0 ? 31.5 : 64.5, 2, 48.5); }
      protected void teleport(int seat, Vec3 position, float yaw) {
         ServerPlayer player = p(seat); if (player != null) player.teleportTo(c.level(), position.x, position.y, position.z, yaw, 0);
      }
      protected void announceScore() { c.broadcast("&9蓝方 &f" + c.score(id(0)) + " &8- &f" + c.score(id(1)) + " &c红方"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { return true; }
      @Override public void close() { }
   }

   private static final class Minions extends Base {
      private final Map<UUID, UUID> owner = new LinkedHashMap<>();
      private final Map<UUID, Integer> conversionCooldown = new HashMap<>();

      Minions(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         for (int seat = 0; seat < 2; seat++) {
            teleport(seat, spawn(seat), seat == 0 ? 90 : -90);
            ServerPlayer player = p(seat);
            if (player != null) {
               player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, true, false));
               player.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, true, false));
               player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 2, true, false));
            }
            for (int i = 0; i < 30; i++) spawnMinion(seat, i);
            c.score(id(seat), 30);
         }
      }
      private void spawnMinion(int seat, int index) {
         ServerLevel level = c.level(); if (level == null) return;
         Vec3 base = spawn(seat); double forward = seat == 0 ? -1 : 1;
         Zombie zombie = new Zombie(level);
         zombie.moveTo(base.x + forward * (2 + index % 6 * 1.15), base.y,
            base.z - 4.4 + (index / 6) * 2.2, seat == 0 ? 90 : -90, 0);
         zombie.setNoAi(true); zombie.setSilent(true); zombie.setInvulnerable(true); zombie.setPersistenceRequired();
         dye(zombie, c.color(id(seat)));
         level.addFreshEntity(zombie); c.own(zombie); owner.put(zombie.getUUID(), id(seat));
      }
      private void dye(Mob mob, PartyColor color) {
         equip(mob, EquipmentSlot.HEAD, Items.LEATHER_HELMET, color.rgb());
         equip(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, color.rgb());
         equip(mob, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, color.rgb());
         equip(mob, EquipmentSlot.FEET, Items.LEATHER_BOOTS, color.rgb());
      }
      private void equip(Mob mob, EquipmentSlot slot, Item item, int rgb) {
         ItemStack stack = new ItemStack(item); stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
         stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); mob.setItemSlot(slot, stack); mob.setDropChance(slot, 0);
      }
      @Override public void tick() {
         List<Zombie> minions = liveMinions();
         for (Zombie zombie : minions) {
            conversionCooldown.computeIfPresent(zombie.getUUID(), (key, value) -> value <= 1 ? null : value - 1);
            UUID ownerId = owner.get(zombie.getUUID()); ServerPlayer player = c.player(ownerId);
            if (player == null) continue;
            Vec3 toward = player.position().subtract(zombie.position());
            if (toward.lengthSqr() > 3.0) {
               Vec3 motion = toward.normalize().scale(0.16); zombie.setDeltaMovement(motion.x, zombie.getDeltaMovement().y, motion.z);
               zombie.setYRot((float) (Math.toDegrees(Math.atan2(-motion.x, motion.z))));
            } else zombie.setDeltaMovement(zombie.getDeltaMovement().scale(0.4));
         }
         Set<UUID> converted = new HashSet<>();
         for (Zombie attacker : minions) {
            if (conversionCooldown.containsKey(attacker.getUUID())) continue;
            UUID attackerOwner = owner.get(attacker.getUUID()); Vec3 forward = attacker.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 point = attacker.position().add(forward.scale(0.65));
            for (Zombie target : minions) {
               if (target == attacker || converted.contains(target.getUUID()) || attackerOwner.equals(owner.get(target.getUUID()))) continue;
               if (target.position().distanceToSqr(point) > 0.55) continue;
               owner.put(target.getUUID(), attackerOwner); dye(target, c.color(attackerOwner)); conversionCooldown.put(target.getUUID(), 12);
               converted.add(target.getUUID()); break;
            }
         }
         for (UUID player : c.seats()) c.score(player, (int) minions.stream().filter(m -> player.equals(owner.get(m.getUUID()))).count());
         if (c.elapsedTicks() % 5 == 0) for (Zombie minion : minions) c.level().sendParticles(dust(c.color(owner.get(minion.getUUID()))), minion.getX(), minion.getY() + 1.2, minion.getZ(), 1, .05, .1, .05, 0);
         if (c.score(id(0)) == 0) c.win(id(1), "红方夺取了全部随从");
         else if (c.score(id(1)) == 0) c.win(id(0), "蓝方夺取了全部随从");
      }
      private List<Zombie> liveMinions() {
         List<Zombie> out = new ArrayList<>();
         if (c.level() == null) return out;
         for (Zombie zombie : c.level().getEntitiesOfClass(Zombie.class, arenaBox(c), z -> owner.containsKey(z.getUUID()) && !z.isRemoved())) out.add(zombie);
         return out;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return owner.containsKey(entity.getUUID()); }
      @Override public void close() { owner.clear(); conversionCooldown.clear(); }
   }

   private static final class Ring extends Base {
      private final Map<UUID, Integer> cooldown = new HashMap<>();
      Ring(PartyGameDefinition definition) { super(definition); }
      @Override public void start() { teleport(0, spawn(0), 0); teleport(1, spawn(1), 180); }
      @Override public void tick() { cooldown.replaceAll((id, value) -> value - 1); cooldown.values().removeIf(v -> v <= 0); clock(); }
      private int threshold() { return OfficialRuleMath.bellThreshold(remaining); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() != PartyGameAction.Type.USE_BLOCK || action.block() == null || !(c.level().getBlockState(action.block()).getBlock() instanceof BellBlock)) return true;
         Vec3 own = spawn(seat(player.getUUID())), otherBell = spawn(1 - seat(player.getUUID()));
         if (action.block().distToCenterSqr(own.x, own.y, own.z) > action.block().distToCenterSqr(otherBell.x, otherBell.y, otherBell.z)) return true;
         if (cooldown.containsKey(player.getUUID())) return true;
         cooldown.put(player.getUUID(), 4); UUID other = c.opponent(player.getUUID()); c.addScore(player.getUUID(), 1); c.addScore(other, -1);
         announceScore(); if (c.score(player.getUUID()) - c.score(other) >= threshold()) c.win(player.getUUID(), "率先达到 " + threshold() + " 分差");
         return true;
      }
   }

   private static final class Gladiator extends Base {
      Gladiator(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         for (int seat = 0; seat < 2; seat++) {
            teleport(seat, spawn(seat), seat == 0 ? 90 : -90);
            ServerPlayer player = p(seat); if (player == null) continue; player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(0, unbreakable(Items.IRON_SWORD, 1)); player.getInventory().setItem(1, unbreakable(Items.BOW, 1));
            player.getInventory().setItem(2, new ItemStack(Items.ARROW, 4));
            player.setItemSlot(EquipmentSlot.FEET, unbreakable(Items.IRON_BOOTS, 1));
            ItemStack chest = unbreakable(Items.IRON_CHESTPLATE, 1);
            Holder<Enchantment> protection = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROJECTILE_PROTECTION);
            chest.enchant(protection, 1); player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.LEGS, unbreakable(Items.CHAINMAIL_LEGGINGS, 1));
            player.setItemSlot(EquipmentSlot.HEAD, unbreakable(Items.CHAINMAIL_HELMET, 1));
         }
      }
      @Override public void tick() { for (UUID id : c.seats()) { ServerPlayer player = c.player(id); if (player != null) { player.getFoodData().setFoodLevel(17); player.getFoodData().setSaturation(0); } } }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         return action.type() != PartyGameAction.Type.ATTACK_ENTITY && action.type() != PartyGameAction.Type.USE_ITEM;
      }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { return false; }
      @Override public boolean death(ServerPlayer player) { c.win(c.opponent(player.getUUID()), c.color(player.getUUID()).display() + "被击败"); return true; }
   }

   private static final class TurtleHockey extends Base {
      private Turtle puck;
      TurtleHockey(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         teleport(0, spawn(0), -90); teleport(1, spawn(1), 90);
         colorGoal("blue_goal", PartyColor.BLUE); colorGoal("red_goal", PartyColor.RED);
         puck = new Turtle(EntityType.TURTLE, c.level()); puck.moveTo(c.anchor("puck", 48.5, 2, 48.5)); puck.setNoAi(true); puck.setInvulnerable(true); puck.setPersistenceRequired();
         c.level().addFreshEntity(puck); c.own(puck);
      }
      private void colorGoal(String anchor, PartyColor color) {
         BlockPos center = c.anchorBlock(anchor, 48, 2, 48);
         for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -3; z <= 3; z++) {
            BlockPos pos = center.offset(x, y, z); Block block = c.level().getBlockState(pos).getBlock();
            if (block == Blocks.DIAMOND_BLOCK || block == Blocks.BLUE_CONCRETE || block == Blocks.RED_CONCRETE) c.level().setBlock(pos, color.concrete(), 3);
         }
      }
      @Override public void tick() {
         if (clock() || puck == null || puck.isRemoved()) return;
         puck.setDeltaMovement(puck.getDeltaMovement().multiply(0.985, 1, 0.985));
         Block goal = c.level().getBlockState(puck.blockPosition().above(2)).getBlock();
          if (goal == Blocks.BLUE_CONCRETE) c.win(id(1), "红方将海龟打入蓝方球门");
          else if (goal == Blocks.RED_CONCRETE) c.win(id(0), "蓝方将海龟打入红方球门");
      }
      @Override protected void timeout() { c.draw("时间到，无人进球"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() == puck) { push(player, puck, 1.25, 0.18); return true; }
         return true;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return entity == puck; }
   }

   private static final class Fishing extends Base {
      Fishing(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         for (int seat = 0; seat < 2; seat++) { teleport(seat, spawn(seat), 90); ServerPlayer player = p(seat); if (player != null) player.getInventory().setItem(0, unbreakable(Items.FISHING_ROD, 1)); }
      }
      @Override public void tick() {
         if (clock()) return;
         for (UUID id : c.seats()) { ServerPlayer player = c.player(id); if (player != null && fishCount(player) > 0) { c.win(id, c.color(id).display() + "率先钓到鱼"); return; } }
      }
      @Override protected void timeout() { c.draw("时间到，无人钓到鱼"); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) { return action.type() != PartyGameAction.Type.USE_ITEM; }
      private int fishCount(ServerPlayer player) {
         int count = 0; for (int i = 0; i < player.getInventory().getContainerSize(); i++) { ItemStack stack = player.getInventory().getItem(i); if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.PUFFERFISH) || stack.is(Items.TROPICAL_FISH)) count += stack.getCount(); } return count;
      }
   }

   private static final class Buttons extends Base {
      private final int[] cells = new int[9];
      Buttons(PartyGameDefinition definition) { super(definition); }
      @Override public void start() { teleport(0, spawn(0), 180); teleport(1, spawn(1), 0); render(); }
      @Override public void tick() {
         int elapsed = c.elapsedTicks(); int threshold = OfficialRuleMath.buttonThreshold(elapsed);
         for (int seat = 0; seat < 2; seat++) if (owned(seat + 1) >= threshold) { c.win(id(seat), c.color(id(seat)).display() + "占领了 " + threshold + " 格"); return; }
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() != PartyGameAction.Type.USE_BLOCK || action.block() == null || !(c.level().getBlockState(action.block()).getBlock() instanceof ButtonBlock)) return true;
         BlockPos wall = c.anchorBlock("button_wall", 47, 3, 48);
         int col = action.block().getX() - wall.getX(), row = 2 - (action.block().getY() - wall.getY()); if (col < 0 || col > 2 || row < 0 || row > 2) return true;
         cells[row * 3 + col] = seat(player.getUUID()) + 1; render(); return true;
      }
      private int owned(int team) { int n = 0; for (int cell : cells) if (cell == team) n++; return n; }
      private void render() { BlockPos wall = c.anchorBlock("button_wall", 47, 3, 48); for (int i = 0; i < 9; i++) c.level().setBlock(wall.offset(i % 3, 2 - i / 3, 0), cells[i] == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : PartyColor.ofTeam(cells[i]).concrete(), 3); }
   }

   private static final class Bridge extends Base {
      private final Map<UUID, Integer> respawn = new HashMap<>();
      private final Set<BlockPos> pendingPlacements = new HashSet<>();
      private final Set<BlockPos> placedBlocks = new HashSet<>();
      private int roundPause;
      Bridge(PartyGameDefinition definition) { super(definition); }
      @Override public void start() { resetRound(); }
      private void resetRound() {
         for (BlockPos pos : placedBlocks) c.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         placedBlocks.clear(); pendingPlacements.clear(); respawn.clear();
         for (int seat = 0; seat < 2; seat++) {
            ServerPlayer player = p(seat); if (player == null) continue; player.setHealth(player.getMaxHealth()); player.setGameMode(GameType.ADVENTURE); teleport(seat, spawn(seat), seat == 0 ? 180 : 0); giveBridgeKit(player);
         }
         roundPause = 60;
      }
      @Override public void tick() {
         for (BlockPos pos : List.copyOf(pendingPlacements)) if (c.level().getBlockState(pos).is(Blocks.BLUE_CONCRETE) || c.level().getBlockState(pos).is(Blocks.RED_CONCRETE)) { placedBlocks.add(pos); pendingPlacements.remove(pos); }
         if (roundPause > 0) { for (int seat = 0; seat < 2; seat++) teleport(seat, spawn(seat), seat == 0 ? 180 : 0); if (--roundPause == 0) for (int seat = 0; seat < 2; seat++) { ServerPlayer player = p(seat); if (player != null) player.setGameMode(GameType.SURVIVAL); } return; }
         for (Map.Entry<UUID, Integer> entry : List.copyOf(respawn.entrySet())) {
            int value = entry.getValue() - 1; if (value > 0) respawn.put(entry.getKey(), value); else { respawn.remove(entry.getKey()); int seat = seat(entry.getKey()); ServerPlayer player = c.player(entry.getKey()); if (player != null) { player.setGameMode(GameType.SURVIVAL); teleport(seat, spawn(seat), seat == 0 ? 180 : 0); giveBridgeKit(player); } }
         }
         for (int seat = 0; seat < 2; seat++) {
            ServerPlayer player = p(seat); if (player == null || respawn.containsKey(player.getUUID())) continue;
            Vec3 goal = c.anchor(seat == 0 ? "blue_goal" : "red_goal", 48.5, 0, seat == 0 ? 24 : 72);
            boolean scored = player.position().distanceToSqr(goal) <= 16;
            if (scored) point(player.getUUID());
         }
      }
      private void point(UUID scorer) { int score = c.addScore(scorer, 1); announceScore(); if (score >= 2) c.win(scorer, "率先赢得两轮"); else resetRound(); }
      @Override public boolean death(ServerPlayer player) { player.setHealth(player.getMaxHealth()); player.setGameMode(GameType.SPECTATOR); respawn.put(player.getUUID(), 60); return true; }
      @Override public boolean damage(ServerPlayer player, DamageSource source) { return roundPause > 0; }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (roundPause > 0) return true;
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY) return false;
         if (action.type() == PartyGameAction.Type.USE_BLOCK && bridgeBlock(action.stack()) && action.block() != null && action.face() != null) pendingPlacements.add(action.block().relative(action.face()).immutable());
         return action.type() != PartyGameAction.Type.USE_ITEM
            && !(action.type() == PartyGameAction.Type.USE_BLOCK && bridgeBlock(action.stack()));
      }
      @Override public boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { return placedBlocks.remove(pos); }
      private boolean bridgeBlock(ItemStack stack) { return stack.is(Items.BLUE_CONCRETE) || stack.is(Items.RED_CONCRETE); }
      private void giveBridgeKit(ServerPlayer player) {
         player.getInventory().clearContent(); player.getInventory().setItem(0, unbreakable(Items.STONE_SWORD, 1)); player.getInventory().setItem(1, unbreakable(Items.BOW, 1));
         player.getInventory().setItem(2, new ItemStack(Items.ARROW, 10)); player.getInventory().setItem(3, new ItemStack(c.color(player.getUUID()) == PartyColor.BLUE ? Items.BLUE_CONCRETE : Items.RED_CONCRETE, 64)); player.getInventory().setItem(4, unbreakable(Items.IRON_PICKAXE, 1));
      }
   }

   private static final class PigPushers extends Base {
      private final Pig[] pigs = new Pig[2];
      PigPushers(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         generateRoute();
         for (int seat = 0; seat < 2; seat++) {
            teleport(seat, spawn(seat), -90);
            Pig pig = new Pig(EntityType.PIG, c.level()); pig.moveTo(c.anchor(seat == 0 ? "blue_pig" : "red_pig", 32.5, 2, seat == 0 ? 43.5 : 53.5)); pig.setNoAi(true); pig.setInvulnerable(true); pig.setPersistenceRequired();
            pig.setGlowingTag(true); pig.setCustomName(TextUtil.color(seat == 0 ? "&9蓝方的猪" : "&c红方的猪")); pig.setCustomNameVisible(true);
            c.level().addFreshEntity(pig); c.own(pig); pigs[seat] = pig;
         }
      }
      /** Replays the data pack's 1/2/3-wide random template-strip algorithm and mirrors it to lane two. */
      private void generateRoute() {
         BlockPos route = c.anchorBlock("route_origin", 15, 7, 9), copy = c.anchorBlock("route_copy", 15, 7, 15);
         for (int dx = 0; dx < 32; dx++) for (int dz = 0; dz < 5; dz++) {
            c.level().setBlock(route.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
            c.level().setBlock(copy.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
         }
         int offset = 0;
         while (offset <= 27) {
            int width = 1 + c.random().nextInt(3);
            BlockPos source;
            if (width == 1) source = c.anchorBlock("template_1", 12, 4, 12).offset(c.random().nextInt(26), 0, 0);
            else if (width == 2) source = c.anchorBlock("template_2", 14, 4, 18).offset(c.random().nextInt(17) * 2, 0, 0);
            else source = c.anchorBlock("template_3", 12, 4, 6).offset(c.random().nextInt(12) * 3, 0, 0);
            copyBlocks(source, route.offset(offset, 0, 0), width, 5);
            offset += width + 1;
         }
         copyBlocks(route, copy, 32, 5);
      }
      private void copyBlocks(BlockPos source, BlockPos destination, int width, int depth) {
         BlockState[][] states = new BlockState[width][depth];
         for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) states[x][z] = c.level().getBlockState(source.offset(x, 0, z));
         for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) c.level().setBlock(destination.offset(x, 0, z), states[x][z], 3);
      }
      @Override public void tick() {
         if (clock()) return;
         if (c.elapsedTicks() % 5 == 0) for (int seat = 0; seat < 2; seat++) if (pigs[seat] != null && !pigs[seat].isRemoved()) c.level().sendParticles(dust(PartyColor.ofTeam(seat + 1)), pigs[seat].getX(), pigs[seat].getY() + .8, pigs[seat].getZ(), 3, .15, .15, .15, 0);
         boolean escaped0 = escaped(pigs[0]), escaped1 = escaped(pigs[1]);
         if (escaped0 && escaped1) { c.draw("两只猪都逃出了赛道"); return; }
         for (int seat = 0; seat < 2; seat++) if (pigs[seat] != null && c.level().getBlockState(pigs[seat].blockPosition().below()).is(Blocks.COARSE_DIRT)) { c.win(id(seat), c.color(id(seat)).display() + "率先把猪推进谷仓"); return; }
      }
      private boolean escaped(Pig pig) { return pig == null || pig.isRemoved() || pig.getY() < c.arena().baseY() - 2 || !c.arena().contains(pig.getX(), pig.getY(), pig.getZ()); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY) for (Pig pig : pigs) if (action.entity() == pig) { if (pigs[seat(player.getUUID())] == pig) push(player, pig, 0.78, 0.08); return true; }
         return true;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return entity == pigs[0] || entity == pigs[1]; }
   }

   private static final class BalanceBeam extends Base {
      private final Map<UUID, Vec3> checkpoints = new HashMap<>();
      private final Map<UUID, Integer> progress = new HashMap<>();
      private int gravity;
      BalanceBeam(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         gravity = c.random().nextBoolean() ? 1 : -1;
         for (int seat = 0; seat < 2; seat++) {
            Vec3 start = spawn(seat); checkpoints.put(id(seat), start); progress.put(id(seat), 0); teleport(seat, start, 0);
         }
      }
      @Override public void tick() {
         if (clock()) return;
         if (c.elapsedTicks() > 0 && c.elapsedTicks() % 200 == 0) { gravity = c.random().nextInt(31) - 15; if (gravity == 0) gravity = 1; c.broadcast("&d引力改变：&f" + (gravity > 0 ? "向右 " : "向左 ") + Math.abs(gravity)); }
         for (UUID id : c.seats()) {
            ServerPlayer player = c.player(id); if (player == null) continue;
            player.setDeltaMovement(player.getDeltaMovement().add(gravity * 0.0035, 0, 0));
            if (player.getY() < c.arena().baseY() - 1) { Vec3 checkpoint = checkpoints.get(id); player.teleportTo(c.level(), checkpoint.x, checkpoint.y, checkpoint.z, 0, 0); player.setDeltaMovement(Vec3.ZERO); continue; }
            Vec3 start = spawn(seat(id)), finish = c.anchor("finish", 48.5, 3, 76.5), route = finish.subtract(start);
            double lengthSquared = Math.max(1, route.lengthSqr());
            int step = Math.max(0, Math.min(100, (int) Math.floor(player.position().subtract(start).dot(route) / lengthSquared * 100)));
            Block beneath = c.level().getBlockState(player.blockPosition().below()).getBlock();
            if (step > progress.getOrDefault(id, 0) && (beneath == Blocks.WHITE_STAINED_GLASS || beneath == Blocks.WHITE_CONCRETE)) { progress.put(id, step); checkpoints.put(id, player.position()); c.score(id, step); c.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.6F); }
            if (beneath == Blocks.GOLD_BLOCK || player.position().distanceToSqr(finish) <= 4) { c.win(id, c.color(id).display() + "率先通过平衡木"); return; }
         }
      }
      @Override protected void timeout() { finishByScore("时间到，按检查点判定"); }
   }

   private static final class ButtonSearch extends Base {
      private enum Phase { HIDE, SEARCH }
      private record Hidden(BlockPos pos, BlockPos container, UUID entity) { }
      private final Map<UUID, Hidden> hidden = new HashMap<>();
      private final Set<UUID> confirmed = new HashSet<>();
      private final Set<BlockPos> initialButtons = new HashSet<>();
      private final Map<BlockPos, UUID> placedOwners = new HashMap<>();
      private Phase phase = Phase.HIDE;
      private int phaseTicks = 600;

      ButtonSearch(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         phase = Phase.HIDE; phaseTicks = 600;
         initialButtons.clear(); placedOwners.clear();
         for (BlockPos pos : BlockPos.betweenClosed(c.arena().minX(), c.arena().baseY(), c.arena().minZ(), c.arena().maxX(), c.arena().topY(), c.arena().maxZ())) {
            if (c.level().getBlockState(pos).getBlock() instanceof ButtonBlock) initialButtons.add(pos.immutable());
         }
         for (int seat = 0; seat < 2; seat++) { teleport(seat, roomSpawn(seat), 180); ServerPlayer player = p(seat); if (player != null) { player.getInventory().setItem(0, ownedButton(player.getUUID())); player.getInventory().setItem(8, named(Items.LIME_DYE, "&a确认藏好")); } }
         c.broadcast("&e藏匿阶段：30 秒。按钮可放置、丢下或放入容器；手持绿色染料右键可提前确认。");
      }
      private Vec3 roomSpawn(int room) { return spawn(room); }
      @Override public void tick() {
         if (phase == Phase.HIDE && c.elapsedTicks() % 5 == 0) scanHidden();
         if (phase == Phase.SEARCH) checkPickedUpOrApproached();
         if (--phaseTicks > 0 && !(phase == Phase.HIDE && confirmed.size() == 2)) return;
         if (phase == Phase.HIDE) beginSearch(); else c.draw("搜索时间到，无人找到按钮");
      }
      private void scanHidden() {
         ServerLevel level = c.level(); if (level == null || phase != Phase.HIDE) return;
         for (UUID id : c.seats()) {
            hidden.remove(id);
            int seat = seat(id); AABB room = roomBox(c, seat, roomSpawn(seat));
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, room, e -> isOwnedButton(e.getItem(), id))) { hidden.put(id, new Hidden(item.blockPosition(), null, item.getUUID())); break; }
            if (hidden.containsKey(id)) continue;
            for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, room, e -> isOwnedButton(e.getItem(), id))) { hidden.put(id, new Hidden(null, null, frame.getUUID())); break; }
            if (hidden.containsKey(id)) continue;
            for (BlockPos pos : BlockPos.betweenClosed((int) room.minX, (int) room.minY, (int) room.minZ, (int) room.maxX, (int) room.maxY, (int) room.maxZ)) {
               BlockEntity be = level.getBlockEntity(pos); if (!(be instanceof Container container)) continue;
               for (int slot = 0; slot < container.getContainerSize(); slot++) if (isOwnedButton(container.getItem(slot), id)) { hidden.put(id, new Hidden(null, pos.immutable(), null)); break; }
               if (hidden.containsKey(id)) break;
            }
            if (hidden.containsKey(id)) continue;
            for (Map.Entry<BlockPos, UUID> placement : placedOwners.entrySet()) if (placement.getValue().equals(id)
               && room.contains(Vec3.atCenterOf(placement.getKey())) && !initialButtons.contains(placement.getKey())
               && level.getBlockState(placement.getKey()).getBlock() instanceof ButtonBlock) {
               hidden.put(id, new Hidden(placement.getKey(), null, null)); break;
            }
         }
      }
      private void beginSearch() {
         scanHidden();
         for (UUID id : c.seats()) if (!hidden.containsKey(id)) { ServerPlayer player = c.player(id); if (player != null) {
            ItemEntity item = new ItemEntity(c.level(), player.getX(), player.getY(), player.getZ(), ownedButton(id)); item.setPickUpDelay(32767); item.setUnlimitedLifetime(); c.level().addFreshEntity(item); c.own(item);
            hidden.put(id, new Hidden(item.blockPosition(), null, item.getUUID()));
         } }
         phase = Phase.SEARCH; phaseTicks = 3600;
         for (int seat = 0; seat < 2; seat++) { ServerPlayer player = p(seat); if (player != null) { player.getInventory().clearContent(); teleport(seat, roomSpawn(1 - seat), 180); } }
         c.broadcast("&a搜索阶段：已交换房间，找到并激活对方按钮！");
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (phase == Phase.HIDE) {
            if (action.type() == PartyGameAction.Type.USE_ITEM && action.stack().is(Items.LIME_DYE)) { confirmed.add(player.getUUID()); c.broadcast("&e" + c.color(player.getUUID()).display() + "已确认藏好按钮。"); }
            if (action.type() == PartyGameAction.Type.USE_BLOCK && action.block() != null && action.face() != null && isOwnedButton(action.stack(), player.getUUID())) {
               placedOwners.put(action.block().relative(action.face()).immutable(), player.getUUID());
            }
            return action.type() != PartyGameAction.Type.USE_BLOCK && action.type() != PartyGameAction.Type.USE_ENTITY;
         }
         Hidden opponent = hidden.get(c.opponent(player.getUUID()));
         if (opponent == null) return false;
         if (action.type() == PartyGameAction.Type.USE_ENTITY && action.entity() != null && action.entity().getUUID().equals(opponent.entity())) { found(player.getUUID()); return true; }
         if (action.type() != PartyGameAction.Type.USE_BLOCK || action.block() == null) return false;
         if (action.block().equals(opponent.pos()) || action.block().equals(opponent.container())) found(player.getUUID());
         return action.block().equals(opponent.pos());
      }
      private void checkPickedUpOrApproached() {
         for (UUID searcher : c.seats()) {
            UUID owner = c.opponent(searcher); ServerPlayer player = c.player(searcher); Hidden target = hidden.get(owner); if (player == null || target == null) continue;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (isOwnedButton(player.getInventory().getItem(slot), owner)) { found(searcher); return; }
            if (target.entity() != null) { Entity entity = c.level().getEntity(target.entity()); if (entity instanceof ItemEntity && player.distanceToSqr(entity) <= 2.25) { found(searcher); return; } }
         }
      }
      private void found(UUID player) { c.win(player, c.color(player).display() + "找到并激活了按钮"); }
      private ItemStack ownedButton(UUID owner) { ItemStack stack = named(Items.STONE_BUTTON, "&f藏匿按钮"); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("mp2_button_owner", owner.toString()))); return stack; }
      private boolean isOwnedButton(ItemStack stack, UUID owner) { if (!stack.is(Items.STONE_BUTTON)) return false; CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data != null && owner.toString().equals(data.copyTag().getString("mp2_button_owner")); }
   }

   private static final class Betris extends Base {
      private final Board[] boards = {new Board(), new Board()};
      private final Map<UUID, Vec3> stands = new HashMap<>();
      private int fallTicks;
      private int permanentTicks;
      Betris(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         for (int seat = 0; seat < 2; seat++) { boards[seat].next(c.random()); Vec3 stand = spawn(seat); stands.put(id(seat), stand); teleport(seat, stand, 0); render(seat); }
      }
      @Override public void tick() {
         int topped = 0;
         for (int seat = 0; seat < 2; seat++) {
            ServerPlayer player = p(seat); if (player == null) continue; Vec3 stand = stands.get(id(seat));
            Vec3 displacement = player.position().subtract(stand); if (displacement.lengthSqr() > 0.05) { Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize(); if (displacement.dot(forward) > 0.08) hold(seat); player.teleportTo(c.level(), stand.x, stand.y, stand.z, player.getYRot(), 0); }
            if (player.isShiftKeyDown() && c.elapsedTicks() % 2 == 0) topped |= step(seat);
         }
         if (++fallTicks >= 12) { fallTicks = 0; topped |= step(0); topped |= step(1); }
         if (c.elapsedTicks() >= 1800 && ++permanentTicks >= 200) { permanentTicks = 0; if (boards[0].garbage(c.random(), true)) topped |= 1; if (boards[1].garbage(c.random(), true)) topped |= 2; render(0); render(1); }
         finishTops(topped);
      }
      private int step(int seat) { Board board = boards[seat]; if (board.move(0, 1)) { render(seat); return 0; } return lock(seat); }
      private int lock(int seat) {
         Board board = boards[seat]; board.lock(); int sent = board.clearRows();
         int topped = 0;
         for (int i = 0; i < sent; i++) if (boards[1 - seat].garbage(c.random(), false)) topped |= 1 << (1 - seat);
         if (!board.next(c.random())) topped |= 1 << seat;
         render(seat); render(1 - seat); return topped;
      }
      private void finishTops(int topped) { if (topped == 3) c.draw("双方方块同时触顶"); else if ((topped & 1) != 0) c.win(id(1), "蓝方方块触顶"); else if ((topped & 2) != 0) c.win(id(0), "红方方块触顶"); }
      private void hold(int seat) { if (boards[seat].hold(c.random())) render(seat); }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int seat = seat(player.getUUID()); Board board = boards[seat];
         switch (action.type()) {
            case JUMP -> { while (board.move(0, 1)) { } finishTops(lock(seat)); }
            case HOTBAR_DELTA -> { int direction = Integer.signum(action.amount()); int count = Math.min(4, Math.abs(action.amount())); for (int i = 0; i < count; i++) board.move(direction, 0); render(seat); }
            case LEFT_CLICK -> { board.rotate(-1); render(seat); }
            case RIGHT_CLICK, USE_ITEM -> { board.rotate(1); render(seat); }
            default -> { }
         }
         return true;
      }
      private void render(int seat) {
         BlockPos boardOrigin = c.anchorBlock(seat == 0 ? "blue_board" : "red_board", seat == 0 ? 20 : 66, 23, 40); Board board = boards[seat]; int[][] view = board.view();
         for (int y = 0; y < Board.H; y++) for (int x = 0; x < Board.W; x++) {
            BlockState state = switch (view[y][x]) { case 1 -> PartyColor.ofTeam(seat + 1).concrete(); case 2 -> Blocks.GRAY_CONCRETE.defaultBlockState(); case 3 -> PartyColor.ofTeam(seat + 1).glass(); case 4 -> Blocks.OBSIDIAN.defaultBlockState(); default -> Blocks.BLACK_CONCRETE.defaultBlockState(); };
            c.level().setBlock(boardOrigin.offset(x, -y, 0), state, 2);
         }
      }
   }

   private static final class Deuce extends Base {
      private Slime ball;
      private UUID lastHit;
      private int serveCount;
      private int serverSeat;
      private int resetTicks;
      private int groundTicks;
      Deuce(PartyGameDefinition definition) { super(definition); }
      @Override public void start() { serverSeat = c.random().nextBoolean() ? 0 : 1; beginPoint(); }
      private void beginPoint() {
         if (ball != null && !ball.isRemoved()) ball.discard(); ball = null; lastHit = null; resetTicks = 40; groundTicks = 0;
         for (int seat = 0; seat < 2; seat++) { teleport(seat, spawn(seat), seat == 0 ? 90 : -90); ServerPlayer player = p(seat); if (player != null) player.getInventory().clearContent(); }
         ServerPlayer server = p(serverSeat); if (server != null) server.getInventory().setItem(0, named(Items.SLIME_BALL, "&a丢出发球"));
         c.broadcast("&e" + c.color(id(serverSeat)).display() + "发球");
      }
      @Override public void tick() {
         if (resetTicks > 0) { resetTicks--; return; }
         if (ball == null || ball.isRemoved()) return;
         if (lastHit != null) c.level().sendParticles(dust(c.color(lastHit)), ball.getX(), ball.getY() + .35, ball.getZ(), 2, .08, .08, .08, 0);
         for (ItemEntity item : c.level().getEntitiesOfClass(ItemEntity.class, arenaBox(c), e -> e.getItem().is(Items.SLIME_BALL))) item.discard();
         Vec3 courtMin = c.anchor("court_min", 4, 3, 9), courtMax = c.anchor("court_max", 36, 12, 15);
         boolean out = ball.getX() < courtMin.x || ball.getX() > courtMax.x || ball.getZ() < courtMin.z || ball.getZ() > courtMax.z || ball.getY() < courtMin.y - 2;
         if (out) { point(lastHit == null ? 1 - serverSeat : 1 - seat(lastHit)); return; }
         groundTicks = ball.onGround() ? groundTicks + 1 : 0;
         if (groundTicks >= 10) {
            int landing = ball.position().distanceToSqr(spawn(0)) <= ball.position().distanceToSqr(spawn(1)) ? 0 : 1;
            int winner;
            if (lastHit == null) winner = 1 - serverSeat;
            else { int hitter = seat(lastHit); boolean crossed = hitter != landing; winner = crossed ? hitter : 1 - hitter; }
            point(winner);
         }
      }
      private void point(int seat) {
         c.addScore(id(seat), 1); announceScore(); serveCount++; if (serveCount % 2 == 0) serverSeat = 1 - serverSeat;
         if (Math.abs(c.score(id(0)) - c.score(id(1))) >= 2) c.win(id(seat), "领先两分"); else beginPoint();
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         if (action.type() == PartyGameAction.Type.DROP_ITEM && seat(player.getUUID()) == serverSeat && action.stack().is(Items.SLIME_BALL) && ball == null) {
            ball = new Slime(EntityType.SLIME, c.level()); ball.setSize(1, true); ball.setNoAi(true); ball.setInvulnerable(true); ball.moveTo(player.getX(), player.getY() + 1.3, player.getZ()); c.level().addFreshEntity(ball); c.own(ball); resetTicks = 0; groundTicks = 0; return true;
         }
         if (action.type() == PartyGameAction.Type.ATTACK_ENTITY && action.entity() == ball) { lastHit = player.getUUID(); groundTicks = 0; Vec3 look = player.getLookAngle().normalize(); ball.setDeltaMovement(look.x * 0.95, Math.max(0.32, look.y * 0.95), look.z * 0.95); return true; }
         return true;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) { return entity == ball; }
   }

   private static final class Decryption extends Base {
      private final Map<Character, Integer> mapping = new LinkedHashMap<>();
      private String answer;
      private final Map<UUID, Integer> retry = new HashMap<>();
      private final Set<UUID> solved = new HashSet<>();
      Decryption(PartyGameDefinition definition) { super(definition); }
      @Override public void start() {
         DecryptionPuzzle puzzle = DecryptionPuzzle.generate(c.random()); mapping.clear(); mapping.putAll(puzzle.mapping()); answer = puzzle.answer();
         c.broadcast("&6密码：&f" + puzzle.code()); c.broadcast("&7映射：&f" + mapping.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a,b) -> a + "  " + b).orElse(""));
         for (int seat = 0; seat < 2; seat++) { BlockPos door = c.anchorBlock(seat == 0 ? "blue_door" : "red_door", seat == 0 ? 50 : 46, 2, 48); c.level().setBlock(door, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3); c.level().setBlock(door.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3); teleport(seat, spawn(seat), 0); giveTemplate(p(seat)); }
      }
      @Override public void tick() {
         for (Map.Entry<UUID, Integer> entry : List.copyOf(retry.entrySet())) { int left = entry.getValue() - 1; if (left <= 0) { retry.remove(entry.getKey()); giveTemplate(c.player(entry.getKey())); } else retry.put(entry.getKey(), left); }
         scanHoppers();
         for (UUID id : solved) { ServerPlayer player = c.player(id); if (player == null) continue; int seat = seat(id); Vec3 exit = c.anchor(seat == 0 ? "blue_exit" : "red_exit", seat == 0 ? 50 : 46, 2, 52); if (player.position().distanceToSqr(exit) <= 4) { c.win(id, c.color(id).display() + "率先破译并离开房间"); return; } }
      }
      private void scanHoppers() {
         for (int seat = 0; seat < 2; seat++) {
            BlockPos pos = c.anchorBlock(seat == 0 ? "blue_hopper" : "red_hopper", seat == 0 ? 43 : 53, 2, 48); BlockEntity be = c.level().getBlockEntity(pos); if (!(be instanceof Container container)) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) { ItemStack stack = container.getItem(slot); if (!stack.is(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)) continue; String submitted = stack.getHoverName().getString().trim(); container.setItem(slot, ItemStack.EMPTY); UUID id = id(seat);
               if (answer.equals(submitted)) { solved.add(id); c.level().setBlock(c.anchorBlock(seat == 0 ? "blue_door" : "red_door", seat == 0 ? 50 : 46, 2, 48), Blocks.AIR.defaultBlockState(), 3); c.broadcast("&a" + c.color(id).display() + "密码正确，门已打开！"); }
               else { retry.put(id, 10); c.send(c.player(id), "&c密码错误，模板即将退回。"); }
            }
         }
      }
      private void giveTemplate(ServerPlayer player) { if (player == null || solved.contains(player.getUUID())) return; ItemStack stack = named(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, ""); player.getInventory().add(stack); player.experienceLevel = 1; player.experienceProgress = 0; }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         return action.type() != PartyGameAction.Type.USE_BLOCK;
      }
   }

   private static final class Cannoneers extends Base {
      private final int[] power = {10, 10};
      private final boolean[] aimed = new boolean[2];
      private final Vec3[] aim = new Vec3[2];
      private final boolean[] fired = new boolean[2];
      private final boolean[] hit = new boolean[2];
      private final Slime[] targets = new Slime[2];
      private final Map<UUID, Integer> arrows = new HashMap<>();
      private int wind;
      private int volleyTicks;
      private boolean inFlight;
      Cannoneers(PartyGameDefinition definition) { super(definition); }
      @Override public void start() { newRound(); }
      private void newRound() {
         for (UUID arrowId : List.copyOf(arrows.keySet())) { Entity oldArrow = c.level().getEntity(arrowId); if (oldArrow != null) oldArrow.discard(); }
         wind = c.random().nextInt(21) - 10; Arrays.fill(aimed, false); Arrays.fill(aim, null); Arrays.fill(fired, false); Arrays.fill(hit, false); arrows.clear(); inFlight = false; volleyTicks = 0;
         for (int seat = 0; seat < 2; seat++) {
            if (targets[seat] != null && !targets[seat].isRemoved()) targets[seat].discard();
            int height = 1 + c.random().nextInt(6); Vec3 platformBase = c.anchor(seat == 0 ? "blue_platform" : "red_platform", seat == 0 ? 27.5 : 68.5, 2, 48.5); BlockPos base = BlockPos.containing(platformBase);
            for (int y = 0; y <= 10; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) c.level().setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            BlockPos platform = base.above(height - 1); for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) c.level().setBlock(platform.offset(x, 0, z), PartyColor.ofTeam(seat + 1).concrete(), 3);
            for (int y = base.getY(); y < platform.getY(); y++) c.level().setBlock(new BlockPos(base.getX(), y, base.getZ()), Blocks.CHAIN.defaultBlockState(), 3);
            Slime target = new Slime(EntityType.SLIME, c.level()); target.setSize(2, true); target.setNoAi(true); target.setInvulnerable(false); target.setGlowingTag(true); target.setCustomName(TextUtil.color(seat == 0 ? "&9蓝方炮台" : "&c红方炮台")); target.setCustomNameVisible(true); target.moveTo(Vec3.atCenterOf(platform.above())); c.level().addFreshEntity(target); c.own(target); targets[seat] = target;
            teleport(seat, spawn(seat), 90); ServerPlayer player = p(seat); if (player != null) player.getInventory().setItem(0, named(Items.FIRE_CHARGE, "&6丢弃以锁定开火"));
         }
         int wallHeight = c.random().nextInt(5); BlockPos wall = c.anchorBlock("wall_origin", 3, 14, 23);
         // The source arena hangs four blocks of iron bars below the randomized cap.
         // Clear that whole vertical envelope so a lower wall from the previous volley
         // cannot survive into the next one.
         for (int x = 0; x <= 22; x++) for (int y = -4; y <= 8; y++) c.level().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
         BlockPos top = wall.above(wallHeight); for (int x = 0; x <= 22; x++) { for (int y = -4; y < 0; y++) c.level().setBlock(top.offset(x, y, 0), Blocks.IRON_BARS.defaultBlockState(), 3); c.level().setBlock(top.offset(x, 0, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3); }
         c.broadcast("&b本轮风力：&f" + wind + " &7（正数向南，负数向北）");
      }
      @Override public void tick() {
         for (ItemEntity item : c.level().getEntitiesOfClass(ItemEntity.class, arenaBox(c), e -> e.getItem().is(Items.FIRE_CHARGE))) item.discard();
         for (int seat = 0; seat < 2; seat++) { ServerPlayer player = p(seat); if (player != null && !fired[seat] && !hasItem(player, Items.FIRE_CHARGE)) player.getInventory().setItem(0, named(Items.FIRE_CHARGE, "&6丢弃以锁定开火")); }
         for (int seat = 0; seat < 2; seat++) { ServerPlayer player = p(seat); if (player != null) c.actionbar(player, (aimed[seat] ? "&a准心已确认" : "&e移动鼠标瞄准") + " &8| &f威力 " + power[seat] + "/20 &8| &b风 " + wind); }
         if (!inFlight && fired[0] && fired[1]) fireVolley();
         if (!inFlight) return;
         volleyTicks++;
         for (Arrow arrow : c.level().getEntitiesOfClass(Arrow.class, arenaBox(c), a -> arrows.containsKey(a.getUUID()))) { arrow.setDeltaMovement(arrow.getDeltaMovement().add(0, 0, OfficialRuleMath.cannonWindAcceleration(wind))); c.level().sendParticles(dust(PartyColor.ofTeam(arrows.get(arrow.getUUID()) + 1)), arrow.getX(), arrow.getY(), arrow.getZ(), 2, .03, .03, .03, 0); }
         boolean any = !c.level().getEntitiesOfClass(Arrow.class, arenaBox(c), a -> arrows.containsKey(a.getUUID()) && a.getDeltaMovement().lengthSqr() > 0.0001).isEmpty();
         if (any && volleyTicks < 200) return;
         if (hit[0] && hit[1] || !hit[0] && !hit[1]) { c.broadcast(hit[0] ? "&e双方同时命中，本轮无结果。" : "&7双方均未命中，重新生成环境。"); newRound(); }
         else c.win(hit[0] ? id(0) : id(1), "炮弹命中对方加农炮");
      }
      private void fireVolley() {
         inFlight = true; volleyTicks = 0;
         for (int seat = 0; seat < 2; seat++) { ServerPlayer player = p(seat); if (player == null || targets[seat] == null) continue; Vec3 look = aim[seat] == null ? player.getLookAngle().normalize() : aim[seat]; Arrow arrow = new Arrow(c.level(), player, ItemStack.EMPTY, null); Vec3 start = targets[seat].position().add(look.scale(1.7)); arrow.moveTo(start); float velocity = 0.80F + power[seat] * 0.06F; arrow.shoot(look.x, look.y, look.z, velocity, 0); arrow.setNoGravity(false); arrow.setGlowingTag(true); c.level().addFreshEntity(arrow); c.own(arrow); arrows.put(arrow.getUUID(), seat); }
      }
      @Override public boolean action(ServerPlayer player, PartyGameAction action) {
         int seat = seat(player.getUUID());
         if (action.type() == PartyGameAction.Type.HOTBAR_DELTA && !fired[seat]) power[seat] = Math.max(1, Math.min(20, power[seat] + action.amount()));
         else if (action.type() == PartyGameAction.Type.LEFT_CLICK && !fired[seat]) { aimed[seat] = !aimed[seat]; aim[seat] = aimed[seat] ? player.getLookAngle().normalize() : null; }
         else if (action.type() == PartyGameAction.Type.DROP_ITEM && aimed[seat]) { fired[seat] = true; c.send(player, "&a开火已锁定，等待对手。"); }
         return true;
      }
      @Override public boolean mobDamage(Entity entity, DamageSource source) {
         for (int targetSeat = 0; targetSeat < 2; targetSeat++) if (entity == targets[targetSeat] && source.getDirectEntity() instanceof Arrow arrow) { Integer shooter = arrows.get(arrow.getUUID()); if (shooter != null && shooter != targetSeat) hit[shooter] = true; return true; }
         return entity == targets[0] || entity == targets[1];
      }
   }

   static final class Board {
      static final int W = 10, H = 20;
      private static final int[][][][] SHAPES = {
         {{{0,1},{1,1},{2,1},{3,1}},{{2,0},{2,1},{2,2},{2,3}}},
         {{{0,0},{0,1},{1,1},{2,1}},{{1,0},{2,0},{1,1},{1,2}},{{0,1},{1,1},{2,1},{2,2}},{{1,0},{1,1},{0,2},{1,2}}},
         {{{2,0},{0,1},{1,1},{2,1}},{{1,0},{1,1},{1,2},{2,2}},{{0,1},{1,1},{2,1},{0,2}},{{0,0},{1,0},{1,1},{1,2}}},
         {{{1,0},{2,0},{1,1},{2,1}}},
         {{{1,0},{2,0},{0,1},{1,1}},{{1,0},{1,1},{2,1},{2,2}}},
         {{{1,0},{0,1},{1,1},{2,1}},{{1,0},{1,1},{2,1},{1,2}},{{0,1},{1,1},{2,1},{1,2}},{{1,0},{0,1},{1,1},{1,2}}},
         {{{0,0},{1,0},{1,1},{2,1}},{{2,0},{1,1},{2,1},{1,2}}}
      };
      private final int[][] cells = new int[H][W];
      private final Deque<Integer> bag = new ArrayDeque<>();
      private int type, rotation, x = 3, y;
      private int held = -1;
      private boolean canHold = true;
      int currentType() { return type; }
      int heldType() { return held; }
      int cell(int row, int column) { return cells[row][column]; }
      void setCell(int row, int column, int value) { cells[row][column] = value; }
      boolean next(java.util.Random random) { type = take(random); rotation = 0; x = 3; y = 0; canHold = true; return valid(x, y, rotation); }
      private int take(java.util.Random random) { if (bag.isEmpty()) { List<Integer> values = new ArrayList<>(); for (int i = 0; i < 7; i++) values.add(i); Collections.shuffle(values, random); bag.addAll(values); } return bag.removeFirst(); }
      boolean move(int dx, int dy) { if (!valid(x + dx, y + dy, rotation)) return false; x += dx; y += dy; return true; }
      void rotate(int direction) { int count = SHAPES[type].length, next = Math.floorMod(rotation + direction, count); for (int kick : new int[]{0,-1,1,-2,2}) if (valid(x + kick, y, next)) { x += kick; rotation = next; return; } }
      boolean hold(java.util.Random random) { if (!canHold) return false; int previous = held; held = type; canHold = false; if (previous < 0) type = take(random); else type = previous; rotation = 0; x = 3; y = 0; return valid(x,y,rotation); }
      void lock() { for (int[] p : SHAPES[type][rotation]) { int px=x+p[0], py=y+p[1]; if (py>=0&&py<H&&px>=0&&px<W) cells[py][px]=1; } }
      int clearRows() { int send=0, write=H-1; for (int read=H-1; read>=0; read--) { boolean full=true, garbage=false, permanent=false; for (int v : cells[read]) { if (v==0) full=false; if (v==2) garbage=true; if (v==4) permanent=true; } if (full && !permanent) { if (!garbage) send++; continue; } cells[write--]=Arrays.copyOf(cells[read],W); } while(write>=0)cells[write--]=new int[W]; return send; }
      boolean garbage(java.util.Random random, boolean permanent) { for(int y=0;y<H-1;y++)cells[y]=Arrays.copyOf(cells[y+1],W); int hole=random.nextInt(W); cells[H-1]=new int[W]; for(int x=0;x<W;x++)if(x!=hole)cells[H-1][x]=permanent?4:2; return !valid(this.x,this.y,rotation); }
      int[][] view(){int[][] out=new int[H][W];for(int i=0;i<H;i++)out[i]=Arrays.copyOf(cells[i],W);int ghost=y;while(valid(x,ghost+1,rotation))ghost++;for(int[]p:SHAPES[type][rotation]){int px=x+p[0],py=ghost+p[1];if(py>=0&&py<H&&out[py][px]==0)out[py][px]=3;}for(int[]p:SHAPES[type][rotation]){int px=x+p[0],py=y+p[1];if(py>=0&&py<H)out[py][px]=1;}return out;}
      private boolean valid(int nx,int ny,int nr){for(int[]p:SHAPES[type][nr]){int px=nx+p[0],py=ny+p[1];if(px<0||px>=W||py>=H)return false;if(py>=0&&cells[py][px]!=0)return false;}return true;}
   }

   private static ItemStack unbreakable(Item item, int count) { ItemStack stack = new ItemStack(item, count); stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); return stack; }
   private static boolean hasItem(ServerPlayer player, Item item) { for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(item)) return true; return false; }
   private static DustParticleOptions dust(PartyColor color) { int rgb = color.rgb(); return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.15F); }
   private static ItemStack named(Item item, String name) { ItemStack stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, TextUtil.color(name)); return stack; }
   private static net.minecraft.nbt.CompoundTag tag(String key, String value) { net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag(); tag.putString(key, value); return tag; }
   private static void push(ServerPlayer player, Entity entity, double horizontal, double vertical) { Vec3 look = player.getLookAngle().multiply(1, 0, 1); if (look.lengthSqr() < 0.001) return; look = look.normalize(); entity.setDeltaMovement(look.x * horizontal, vertical, look.z * horizontal); entity.hurtMarked = true; }
   private static AABB arenaBox(PartyMatchContext c) { return new AABB(c.arena().minX(), c.arena().baseY() - 4, c.arena().minZ(), c.arena().maxX() + 1, c.arena().topY() + 4, c.arena().maxZ() + 1); }
   private static AABB roomBox(PartyMatchContext c, int seat, Vec3 center) {
      return new AABB(Math.max(c.arena().minX(), center.x - 34), Math.max(c.arena().baseY(), center.y - 3), Math.max(c.arena().minZ(), center.z - 16),
         Math.min(c.arena().maxX() + 1, center.x + 34), Math.min(c.arena().topY() + 1, center.y + 16), Math.min(c.arena().maxZ() + 1, center.z + 16));
   }
}
