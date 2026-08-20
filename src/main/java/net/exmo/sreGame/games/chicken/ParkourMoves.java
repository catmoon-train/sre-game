package net.exmo.sreGame.games.chicken;

import net.exmo.sreGame.mixin.LivingJumpAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ParkourMoves {
   private static final double[] BODY_Y = {0.08, 0.42, 0.88, 1.42};

   private ParkourMoves() {
   }

   public static void tick(ServerPlayer player, ChickenHorseMatch.Racer racer, ServerLevel level) {
      if (player.getAbilities().flying || player.isSpectator()) {
         return;
      }
      if (racer.freezeTicks > 0) {
         impulse(player, Vec3.ZERO);
         return;
      }
      boolean onGround = player.onGround();
      boolean jumping = ((LivingJumpAccessor) player).sre$isJumping();
      boolean shift = player.isShiftKeyDown();
      WallSense wall = senseWall(player, level);
      boolean onWall = wall.away() != null && !onGround;
      boolean jumpPress = jumping && !racer.wasJumping;
      racer.wasJumping = jumping;

      if (onGround) {
         racer.airJumps = racer.jumpLock > 0 ? 0 : 1;
         racer.airTicks = 0;
         racer.wallJumpReady = true;
         return;
      }
      racer.airTicks++;
      if (onWall) {
         racer.wallJumpReady = true;
      }

      if (tryMantle(player, level, wall, jumpPress || shift || jumping)) {
         racer.wallJumpReady = true;
         racer.airJumps = racer.jumpLock > 0 ? 0 : 1;
         return;
      }

      if (onWall && jumpPress && racer.wallJumpReady) {
         racer.wallJumpReady = false;
         racer.airJumps = racer.jumpLock > 0 ? 0 : 1;
         Vec3 look = player.getLookAngle();
         Vec3 away = wall.away();
         if (wall.nearLedge()) {
            impulse(player, new Vec3(away.x * 0.22 + look.x * 0.06, 0.92, away.z * 0.22 + look.z * 0.06));
         } else {
            impulse(player, new Vec3(away.x * 0.78 + look.x * 0.16, 0.68, away.z * 0.78 + look.z * 0.16));
         }
         burst(level, player, 4);
         return;
      }

      boolean canDouble = !onWall && racer.airJumps > 0 && racer.airTicks > 4 && racer.jumpLock <= 0;
      boolean heldApex = jumping && racer.airTicks > 6 && player.getDeltaMovement().y < 0.18;
      if (canDouble && (jumpPress || heldApex)) {
         racer.airJumps--;
         Vec3 mot = player.getDeltaMovement();
         impulse(player, new Vec3(mot.x, 0.62, mot.z));
         burst(level, player, 8);
         level.playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
      } else if (onWall && shift) {
         Vec3 away = wall.away();
         double up = wall.nearLedge() ? 0.42 : 0.26;
         impulse(player, new Vec3(-away.x * 0.10, up, -away.z * 0.10));
      }
   }

   public static void impulse(ServerPlayer player, Vec3 motion) {
      player.setDeltaMovement(motion);
      player.hasImpulse = true;
      player.hurtMarked = true;
      player.fallDistance = 0.0F;
      player.connection.send(new ClientboundSetEntityMotionPacket(player));
   }

   public static void burst(ServerLevel level, ServerPlayer player, int count) {
      level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), count, 0.25, 0.08, 0.25, 0.02);
   }

   private static boolean tryMantle(ServerPlayer player, ServerLevel level, WallSense wall, boolean climbing) {
      if (!climbing || wall.ledgeY() < 0.0 || wall.away() == null) {
         return false;
      }
      double feet = player.getY();
      if (feet < wall.ledgeY() - 1.05 || feet > wall.ledgeY() + 0.28) {
         return false;
      }
      Vec3 away = wall.away();
      double tx = player.getX() - away.x * 0.48;
      double ty = wall.ledgeY() + 0.02;
      double tz = player.getZ() - away.z * 0.48;
      BlockPos stand = BlockPos.containing(tx, ty, tz);
      BlockPos head = stand.above();
      if (!empty(level, stand) || !empty(level, head)) {
         impulse(player, new Vec3(-away.x * 0.12, 0.46, -away.z * 0.12));
         return true;
      }
      player.teleportTo(level, tx, ty, tz, player.getYRot(), player.getXRot());
      impulse(player, new Vec3(-away.x * 0.18, 0.18, -away.z * 0.18));
      burst(level, player, 6);
      level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.45F, 1.35F);
      return true;
   }

   private static WallSense senseWall(ServerPlayer player, ServerLevel level) {
      double reach = player.getBbWidth() * 0.5 + 0.22;
      double nx = 0.0;
      double nz = 0.0;
      int hits = 0;
      double bestLedge = -1.0;
      boolean nearLedge = false;
      for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
         boolean low = false;
         boolean high = false;
         for (double bodyY : BODY_Y) {
            BlockPos pos = BlockPos.containing(
               player.getX() + dir.getStepX() * reach,
               player.getY() + bodyY,
               player.getZ() + dir.getStepZ() * reach
            );
            if (!climbable(level, pos)) {
               continue;
            }
            hits++;
            nx -= dir.getStepX();
            nz -= dir.getStepZ();
            if (bodyY < 1.0) {
               low = true;
            } else {
               high = true;
            }
         }
         if (!low) {
            continue;
         }
         double surface = columnTop(
            level,
            BlockPos.containing(
               player.getX() + dir.getStepX() * (reach + 0.12),
               player.getY() + 0.2,
               player.getZ() + dir.getStepZ() * (reach + 0.12)
            ),
            (int) Math.floor(player.getY() - 0.25),
            (int) Math.floor(player.getY() + player.getBbHeight() + 1.6)
         );
         if (surface < 0.0) {
            continue;
         }
         if (player.getY() >= surface - 1.15 && player.getY() <= surface + 0.25) {
            nearLedge = true;
            if (surface > bestLedge) {
               bestLedge = surface;
            }
         } else if (!high && player.getY() + player.getBbHeight() >= surface - 0.35) {
            nearLedge = true;
            if (surface > bestLedge) {
               bestLedge = surface;
            }
         }
      }
      if (hits == 0) {
         return WallSense.NONE;
      }
      Vec3 vec = new Vec3(nx, 0.0, nz);
      if (vec.lengthSqr() < 1.0E-6) {
         return WallSense.NONE;
      }
      return new WallSense(vec.normalize(), bestLedge, nearLedge);
   }

   private static double columnTop(ServerLevel level, BlockPos start, int minY, int maxY) {
      int top = -1;
      for (int y = minY; y <= maxY; y++) {
         BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
         if (!climbable(level, pos)) {
            if (top >= 0) {
               break;
            }
            continue;
         }
         top = y;
      }
      if (top < 0) {
         return -1.0;
      }
      BlockPos above = new BlockPos(start.getX(), top + 1, start.getZ());
      BlockPos above2 = above.above();
      if (!empty(level, above) || !empty(level, above2)) {
         return -1.0;
      }
      return top + 1.0;
   }

   private static boolean empty(ServerLevel level, BlockPos pos) {
      return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   private static boolean climbable(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getCollisionShape(level, pos).isEmpty()) {
         return false;
      }
      Block block = state.getBlock();
      if (block == Blocks.GLOWSTONE || block == Blocks.BARRIER) {
         return false;
      }
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
      return !path.equals("glass") && !path.endsWith("stained_glass");
   }

   private record WallSense(Vec3 away, double ledgeY, boolean nearLedge) {
      static final WallSense NONE = new WallSense(null, -1.0, false);
   }
}
