package net.exmo.sreGame.luckypillar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class RandomEvents {
   public static final int INTERVAL_TICKS = 30 * 20;

   private RandomEvents() {
   }

   public static void roll(LuckyPillarMatch match) {
      Kind kind = Kind.values()[ThreadLocalRandom.current().nextInt(Kind.values().length)];
      match.ctx().broadcast(match.room(), "&d随机事件： &e" + kind.label);
      kind.apply(match);
   }

   public enum Kind {
      LIGHTNING("天雷", match -> {
         for (ServerPlayer player : match.alivePlayers()) {
            LuckyEvents.strike(match, player.position());
         }
      }),
      TNT_RAIN("TNT雨", match -> {
         for (ServerPlayer player : match.alivePlayers()) {
            LuckyEvents.spawnTnt(match, player.blockPosition().above(4), 1);
         }
      }),
      SWAP("全员换位", match -> swap(match)),
      LEVITATE("漂浮", match -> {
         for (ServerPlayer player : match.alivePlayers()) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 80, 1));
         }
      }),
      BLIND("失明", match -> {
         for (ServerPlayer player : match.alivePlayers()) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
         }
      }),
      EXTRA("额外物品", match -> match.giveRefresh(false)),
      ZOMBIES("僵尸来袭", match -> spawnZombies(match)),
      LAUNCH("弹射柱顶", match -> {
         for (ServerPlayer player : match.alivePlayers()) {
            match.warpOwnPillar(player);
         }
      });

      final String label;
      final Handler handler;

      Kind(String label, Handler handler) {
         this.label = label;
         this.handler = handler;
      }

      void apply(LuckyPillarMatch match) {
         this.handler.run(match);
      }
   }

   private static void swap(LuckyPillarMatch match) {
      List<ServerPlayer> alive = new ArrayList<>(match.alivePlayers());
      if (alive.size() < 2) {
         return;
      }
      List<Vec3> pos = new ArrayList<>();
      List<Float> yaw = new ArrayList<>();
      List<Float> pitch = new ArrayList<>();
      for (ServerPlayer player : alive) {
         pos.add(player.position());
         yaw.add(player.getYRot());
         pitch.add(player.getXRot());
      }
      Collections.rotate(pos, 1);
      Collections.rotate(yaw, 1);
      Collections.rotate(pitch, 1);
      ServerLevel level = match.level();
      if (level == null) {
         return;
      }
      for (int i = 0; i < alive.size(); i++) {
         Vec3 at = pos.get(i);
         alive.get(i).teleportTo(level, at.x, at.y, at.z, yaw.get(i), pitch.get(i));
      }
   }

   private static void spawnZombies(LuckyPillarMatch match) {
      ServerLevel level = match.level();
      if (level == null) {
         return;
      }
      for (ServerPlayer player : match.alivePlayers()) {
         Zombie zombie = EntityType.ZOMBIE.create(level);
         if (zombie == null) {
            continue;
         }
         BlockPos at = player.blockPosition().offset(ThreadLocalRandom.current().nextInt(3) - 1, 0,
            ThreadLocalRandom.current().nextInt(3) - 1);
         zombie.moveTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, 0, 0);
         level.addFreshEntity(zombie);
      }
   }

   @FunctionalInterface
   interface Handler {
      void run(LuckyPillarMatch match);
   }
}
