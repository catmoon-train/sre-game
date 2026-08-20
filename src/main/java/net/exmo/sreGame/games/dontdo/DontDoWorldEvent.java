package net.exmo.sreGame.games.dontdo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum DontDoWorldEvent {
   NONE("无事件", "暂时风平浪静", 70),
   ANVIL("天降正义", "头顶会掉落铁砧", 12),
   LOSE_WEIGHT("重力失常", "获得跳跃与缓降", 12),
   LIGHTNING("闪电时刻", "可能被雷击中", 10),
   FREEZE("冰冻风扇", "周围生物被冻结", 10),
   HEAL_RAIN("治愈之雨", "持续恢复生命", 18),
   SPEED("速度狂热", "获得速度但防御下降", 16),
   ORE_WAKE("矿脉觉醒", "附近矿石自动掉落", 14),
   MOB_SWARM("虫群危机", "周围刷出僵尸", 12),
   TREMOR("大地震颤", "地面裂开并露出矿石", 12),
   VORTEX("物品漩涡", "附近掉落物被吸过来", 16);

   public final String title;
   public final String describe;
   public final float weight;

   DontDoWorldEvent(String title, String describe, float weight) {
      this.title = title;
      this.describe = describe;
      this.weight = weight;
   }

   public static DontDoWorldEvent pick(DontDoWorldEvent except) {
      DontDoWorldEvent[] all = values();
      double total = 0.0;
      for (DontDoWorldEvent event : all) {
         if (event != except) {
            total += event.weight;
         }
      }
      double roll = ThreadLocalRandom.current().nextDouble() * total;
      double cursor = 0.0;
      for (DontDoWorldEvent event : all) {
         if (event == except) {
            continue;
         }
         cursor += event.weight;
         if (roll <= cursor) {
            return event;
         }
      }
      return NONE;
   }

   public void tick(DontDoMatch match, ServerLevel level, ServerPlayer player, int tick) {
      switch (this) {
         case ANVIL -> {
            if (tick % 80 == 0) {
               BlockPos pos = player.blockPosition().above(10);
               FallingBlockEntity.fall(level, pos, Blocks.ANVIL.defaultBlockState());
            }
         }
         case LOSE_WEIGHT -> {
            if (tick % 40 == 0) {
               player.addEffect(new MobEffectInstance(MobEffects.JUMP, 80, 2, true, false));
               player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 1, true, false));
            }
         }
         case LIGHTNING -> {
            if (tick % 100 == 0 && ThreadLocalRandom.current().nextFloat() < 0.3F) {
               LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
               if (bolt != null) {
                  bolt.moveTo(player.getX(), player.getY(), player.getZ());
                  bolt.setVisualOnly(false);
                  level.addFreshEntity(bolt);
               }
            }
         }
         case FREEZE -> {
            AABB box = player.getBoundingBox().inflate(16.0);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof ServerPlayer))) {
               living.setDeltaMovement(Vec3.ZERO);
               living.hurtMarked = true;
            }
         }
         case HEAL_RAIN -> {
            if (tick % 40 == 0) {
               player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false));
               player.causeFoodExhaustion(0.4F);
            }
         }
         case SPEED -> {
            if (tick % 40 == 0) {
               player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
               player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, true, false));
            }
         }
         case ORE_WAKE -> {
            if (tick % 30 != 0) {
               return;
            }
            BlockPos origin = player.blockPosition();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -2; dx <= 2; dx++) {
               for (int dy = -2; dy <= 2; dy++) {
                  for (int dz = -2; dz <= 2; dz++) {
                     BlockState state = level.getBlockState(cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz));
                     if (isOre(state)) {
                        List<ItemStack> drops = Block.getDrops(state, level, cursor, null, player, player.getMainHandItem());
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                        for (ItemStack drop : drops) {
                           Block.popResource(level, cursor, drop);
                        }
                        return;
                     }
                  }
               }
            }
         }
         case MOB_SWARM -> {
            if (tick % 80 == 0 && ThreadLocalRandom.current().nextFloat() < 0.45F) {
               Zombie zombie = EntityType.ZOMBIE.create(level);
               if (zombie != null) {
                  double ox = player.getX() + ThreadLocalRandom.current().nextDouble(-8, 8);
                  double oz = player.getZ() + ThreadLocalRandom.current().nextDouble(-8, 8);
                  zombie.moveTo(ox, player.getY(), oz, 0.0F, 0.0F);
                  level.addFreshEntity(zombie);
               }
            }
         }
         case TREMOR -> {
            if (tick % 40 != 0) {
               return;
            }
            int x = player.blockPosition().getX() + ThreadLocalRandom.current().nextInt(-4, 5);
            int z = player.blockPosition().getZ() + ThreadLocalRandom.current().nextInt(-4, 5);
            int y = player.blockPosition().getY() - 1;
            BlockPos pos = new BlockPos(x, y, z);
            if (!match.island().inPlayable(pos)) {
               return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
               BlockState next = ThreadLocalRandom.current().nextBoolean()
                  ? Blocks.COAL_ORE.defaultBlockState()
                  : Blocks.AIR.defaultBlockState();
               if (ThreadLocalRandom.current().nextFloat() < 0.15F) {
                  next = Blocks.IRON_ORE.defaultBlockState();
               }
               level.setBlock(pos, next, 3);
            }
         }
         case VORTEX -> {
            AABB box = player.getBoundingBox().inflate(10.0);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
               Vec3 pull = player.position().subtract(item.position()).normalize().scale(0.25);
               item.setDeltaMovement(item.getDeltaMovement().add(pull));
            }
         }
         default -> {
         }
      }
   }

   private static boolean isOre(BlockState state) {
      return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
         || state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
         || state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
         || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
         || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
         || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
         || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE);
   }
}
