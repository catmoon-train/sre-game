package net.exmo.sreGame.games.buildrun;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BuildRunTrack {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int SIZE_X = 168;
   public static final int SIZE_Z = 13;
   public static final int HEIGHT = 24;
   public static final int PIT = 8;
   public static final int LANE_Z = 4;
   public static final int LANE_W = 5;
   public static final int PAD = 2;
   public static final int SPAWN_LX = 4;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;

   public BuildRunTrack(int slot, BlockPos origin) {
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

   public boolean dirty() {
      return this.dirty;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void markClean() {
      this.dirty = false;
   }

   public int floorY() {
      return this.origin.getY() + 1;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX() && x < this.origin.getX() + SIZE_X
         && z >= this.origin.getZ() && z < this.origin.getZ() + SIZE_Z
         && y >= this.origin.getY() - PIT - 1 && y <= this.origin.getY() + HEIGHT + 1;
   }

   public boolean canBuild(BlockPos pos) {
      int lx = pos.getX() - this.origin.getX();
      int ly = pos.getY() - this.origin.getY();
      int lz = pos.getZ() - this.origin.getZ();
      return lx >= 1 && lx < SIZE_X - 1
         && lz >= 1 && lz < SIZE_Z - 1
         && ly >= 1 && ly < HEIGHT - 1
         && !this.isSpawnPad(lx, ly, lz);
   }

   public boolean isSpawnPad(BlockPos pos) {
      return this.isSpawnPad(
         pos.getX() - this.origin.getX(),
         pos.getY() - this.origin.getY(),
         pos.getZ() - this.origin.getZ()
      );
   }

   public boolean isSpawnPad(int lx, int ly, int lz) {
      return ly == 1
         && Math.abs(lx - SPAWN_LX) <= PAD
         && Math.abs(lz - SIZE_Z / 2) <= PAD;
   }

   public boolean onDeathFloor(double y) {
      return y < this.origin.getY() - PIT + 1.2;
   }

   public Vec3 spawn() {
      return new Vec3(this.origin.getX() + 4.5, this.floorY() + 1.0, this.origin.getZ() + SIZE_Z / 2.0 + 0.5);
   }

   public Vec3 watch() {
      return new Vec3(this.origin.getX() + SIZE_X / 2.0, this.origin.getY() + 12.0, this.origin.getZ() + SIZE_Z / 2.0);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, -90.0F, 8.0F);
   }

   public BlockState palette(int lx, int ly, int lz) {
      if (ly < -PIT || ly >= HEIGHT || lx < 0 || lx >= SIZE_X || lz < 0 || lz >= SIZE_Z) {
         return Blocks.AIR.defaultBlockState();
      }
      if (ly < 0) {
         if (lx == 0 || lx == SIZE_X - 1 || lz == 0 || lz == SIZE_Z - 1) {
            return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
         }
         return Blocks.AIR.defaultBlockState();
      }
      if (ly == HEIGHT - 1) {
         return Blocks.BARRIER.defaultBlockState();
      }
      if (lx == 0 || lx == SIZE_X - 1 || lz == 0 || lz == SIZE_Z - 1) {
         return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
      }
      if (ly == 1) {
         return Blocks.SMOOTH_QUARTZ.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }
}
