package net.exmo.sreGame.games.fillinthewall;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FillWallArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int TRACK_LENGTH = 20;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private Layout builtLayout = null;

   public FillWallArena(int slot, BlockPos origin) {
      this.slot = slot;
      this.origin = origin;
   }

   public int slot() {
      return this.slot;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public State state() {
      return this.state;
   }

   public void setState(State state) {
      this.state = state;
   }

   public Layout builtLayout() {
      return this.builtLayout;
   }

   public int fieldX() {
      return this.origin.getX();
   }

   public int fieldY() {
      return this.origin.getY();
   }

   public int fieldZ() {
      return this.origin.getZ();
   }

   /** Build the arena structure for the given layout, then run {@code whenReady}. */
   public void prepare(ServerLevel level, Layout layout, Runnable whenReady) {
      if (level == null || layout == null) {
         if (whenReady != null) {
            whenReady.run();
         }
         return;
      }
      this.build(level, layout);
      this.builtLayout = layout;
      if (whenReady != null) {
         whenReady.run();
      }
   }

   private void build(ServerLevel level, Layout layout) {
      int fx = this.fieldX();
      int fy = this.fieldY();
      int fz = this.fieldZ();
      int len = layout.length();
      int h = layout.height();
      int stand = layout.standingDistance();
      int track = layout.trackLength();
      int minX = fx - track - 2;
      int maxX = fx + stand + 2;
      int minZ = fz - 3;
      int maxZ = fz + len + 3;
      int minY = fy - 3;
      int maxY = fy + h + 4;
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
               pos.set(x, y, z);
               level.setBlock(pos, air, 2);
            }
         }
      }
      BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(pos.set(x, fy - 1, z), floor, 2);
         }
      }
      // Back wall so players don't fall off behind their platform.
      BlockState back = Blocks.IRON_BLOCK.defaultBlockState();
      int backX = fx + stand + 2;
      for (int z = minZ; z <= maxZ; z++) {
         for (int y = fy; y <= fy + h + 2; y++) {
            level.setBlock(pos.set(backX, y, z), back, 2);
         }
      }
      // Field frame (border) on the field plane.
      BlockState frame = Blocks.IRON_BLOCK.defaultBlockState();
      for (int z = fz - 1; z <= fz + len; z++) {
         level.setBlock(pos.set(fx, fy + h, z), frame, 2);
         level.setBlock(pos.set(fx, fy - 1, z), frame, 2);
      }
      for (int y = fy - 1; y <= fy + h; y++) {
         level.setBlock(pos.set(fx, y, fz - 1), frame, 2);
         level.setBlock(pos.set(fx, y, fz + len), frame, 2);
      }
   }

   /** Tear down everything the arena built. */
   public void release(ServerLevel level) {
      if (level == null) {
         return;
      }
      Layout layout = this.builtLayout;
      if (layout == null) {
         return;
      }
      int fx = this.fieldX();
      int fy = this.fieldY();
      int fz = this.fieldZ();
      int len = layout.length();
      int h = layout.height();
      int stand = layout.standingDistance();
      int track = layout.trackLength();
      int minX = fx - track - 2;
      int maxX = fx + stand + 2;
      int minZ = fz - 3;
      int maxZ = fz + len + 3;
      int minY = fy - 3;
      int maxY = fy + h + 4;
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
               level.setBlock(pos.set(x, y, z), air, 2);
            }
        }
      }
      this.builtLayout = null;
   }

   public boolean contains(double x, double y, double z, Layout layout) {
      if (layout == null) {
         return false;
      }
      int fx = this.fieldX();
      int fy = this.fieldY();
      int fz = this.fieldZ();
      int len = layout.length();
      int h = layout.height();
      int stand = layout.standingDistance();
      int track = layout.trackLength();
      return x >= fx - track - 2 && x <= fx + stand + 3
         && z >= fz - 3 && z <= fz + len + 3
         && y >= fy - 3 && y <= fy + h + 5;
   }

   public Vec3 spawnVec(Layout layout) {
      int fx = this.fieldX();
      int fy = this.fieldY();
      int fz = this.fieldZ();
      int len = layout.length();
      int stand = layout.standingDistance();
      return new Vec3(fx + stand + 0.5, fy + 0.0, fz + len / 2.0 + 0.5);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, 180.0F, 0.0F);
   }

   public record Layout(int length, int height, int standingDistance, int trackLength) {
   }
}
