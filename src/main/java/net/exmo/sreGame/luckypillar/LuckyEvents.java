package net.exmo.sreGame.luckypillar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.GameContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class LuckyEvents {
   private LuckyEvents() {
   }

   public static void roll(LuckyPillarMatch match, ServerPlayer player, BlockPos pos) {
      if (player == null || match == null) {
         return;
      }
      Kind kind = Kind.values()[ThreadLocalRandom.current().nextInt(Kind.values().length)];
      match.ctx().broadcast(match.room(), "&6" + player.getGameProfile().getName() + " &7打开了幸运方块：&e" + kind.label);
      kind.apply(match, player, pos);
   }

   public enum Kind {
      DIAMOND_GEAR("钻石套", (match, player, pos) -> {
         give(player, new ItemStack(Items.DIAMOND_HELMET));
         give(player, new ItemStack(Items.DIAMOND_CHESTPLATE));
         give(player, new ItemStack(Items.DIAMOND_LEGGINGS));
         give(player, new ItemStack(Items.DIAMOND_BOOTS));
         give(player, new ItemStack(Items.DIAMOND_SWORD));
      }),
      GOLDEN_APPLE("金苹果", (match, player, pos) -> give(player, new ItemStack(Items.GOLDEN_APPLE, 3))),
      PEARLS("末影珍珠", (match, player, pos) -> give(player, new ItemStack(Items.ENDER_PEARL, 8))),
      TOTEM("图腾", (match, player, pos) -> give(player, new ItemStack(Items.TOTEM_OF_UNDYING))),
      BLOCKS("整组方块", (match, player, pos) -> give(player, new ItemStack(Items.COBBLESTONE, 64))),
      TNT("点燃TNT", (match, player, pos) -> spawnTnt(match, pos, 3)),
      LIGHTNING("闪电", (match, player, pos) -> strike(match, player.position())),
      ANVIL("铁砧", (match, player, pos) -> {
         ServerLevel level = match.level();
         if (level != null) {
            level.setBlock(BlockPos.containing(player.getX(), player.getY() + 8, player.getZ()), Blocks.ANVIL.defaultBlockState(), 3);
         }
      }),
      LAVA("岩浆桶", (match, player, pos) -> give(player, new ItemStack(Items.LAVA_BUCKET))),
      HARM("瞬间伤害", (match, player, pos) ->
         player.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0))),
      SCRAMBLE("打乱背包", (match, player, pos) -> scramble(player)),
      WARP("随机传送", (match, player, pos) -> match.warpRandom(player)),
      OBSIDIAN("黑曜石圈", (match, player, pos) -> ring(match, player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()));

      final String label;
      final Handler handler;

      Kind(String label, Handler handler) {
         this.label = label;
         this.handler = handler;
      }

      void apply(LuckyPillarMatch match, ServerPlayer player, BlockPos pos) {
         this.handler.run(match, player, pos);
      }
   }

   static void give(ServerPlayer player, ItemStack stack) {
      if (!player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
   }

   static void spawnTnt(LuckyPillarMatch match, BlockPos pos, int count) {
      ServerLevel level = match.level();
      if (level == null) {
         return;
      }
      for (int i = 0; i < count; i++) {
         PrimedTnt tnt = EntityType.TNT.create(level);
         if (tnt != null) {
            tnt.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
            tnt.setFuse(40 + i * 10);
            level.addFreshEntity(tnt);
         }
      }
   }

   static void strike(LuckyPillarMatch match, Vec3 pos) {
      ServerLevel level = match.level();
      if (level != null) {
         var bolt = EntityType.LIGHTNING_BOLT.create(level);
         if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            level.addFreshEntity(bolt);
         }
      }
   }

   static void scramble(ServerPlayer player) {
      List<ItemStack> items = new ArrayList<>();
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         items.add(player.getInventory().getItem(i).copy());
      }
      Collections.shuffle(items);
      for (int i = 0; i < items.size(); i++) {
         player.getInventory().setItem(i, items.get(i));
      }
   }

   static void ring(LuckyPillarMatch match, BlockPos center, net.minecraft.world.level.block.state.BlockState state) {
      ServerLevel level = match.level();
      if (level == null) {
         return;
      }
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) {
               continue;
            }
            BlockPos at = center.offset(dx, 0, dz);
            if (match.canReplace(at)) {
               level.setBlock(at, state, 3);
            }
         }
      }
   }

   @FunctionalInterface
   interface Handler {
      void run(LuckyPillarMatch match, ServerPlayer player, BlockPos pos);
   }
}
