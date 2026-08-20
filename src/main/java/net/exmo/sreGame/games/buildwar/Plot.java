package net.exmo.sreGame.games.buildwar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class Plot {
   public enum State {
      IDLE,
      IN_USE
   }

   private final int slot;
   private final BlockPos origin;
   private final int size;
   private final int height;
   private State state = State.IDLE;
   private boolean dirty;

   public Plot(int slot, BlockPos origin, int size, int height) {
      this.slot = slot;
      this.origin = origin;
      this.size = size;
      this.height = height;
   }

   public int slot() {
      return this.slot;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public int size() {
      return this.size;
   }

   public int height() {
      return this.height;
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

   public Vec3 spawn() {
      return new Vec3(
         this.origin.getX() + this.size / 2.0 + 0.5,
         this.origin.getY() + 1.0,
         this.origin.getZ() + this.size / 2.0 + 0.5
      );
   }

   public boolean contains(BlockPos pos) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      return x >= this.origin.getX()
         && x < this.origin.getX() + this.size
         && z >= this.origin.getZ()
         && z < this.origin.getZ() + this.size
         && y >= this.origin.getY()
         && y <= this.origin.getY() + this.height;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX()
         && x < this.origin.getX() + this.size
         && z >= this.origin.getZ()
         && z < this.origin.getZ() + this.size
         && y >= this.origin.getY()
         && y <= this.origin.getY() + this.height + 0.5;
   }

   public boolean containsWatch(double x, double y, double z) {
      return x >= this.origin.getX()
         && x < this.origin.getX() + this.size
         && z >= this.origin.getZ()
         && z < this.origin.getZ() + this.size
         && y >= this.origin.getY() - 2.0
         && y <= this.origin.getY() + this.height + 16.0;
   }

   public void teleport(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
      Vec3 spawn = this.spawn();
      player.teleportTo(level, spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0F);
   }

   public void teleportWatch(net.minecraft.server.level.ServerPlayer player, ServerLevel level, int index, int total) {
      double cx = this.origin.getX() + this.size / 2.0;
      double cz = this.origin.getZ() + this.size / 2.0;
      double cy = this.origin.getY() + Math.min(this.height - 1.0, 5.5);
      int n = Math.max(1, total);
      double radius = Math.max(2.5, this.size / 2.0 - 3.0);
      double angle = (Math.PI * 2.0 * Math.floorMod(index, n)) / n;
      double x = cx + Math.cos(angle) * radius + 0.5;
      double z = cz + Math.sin(angle) * radius + 0.5;
      double dx = cx + 0.5 - x;
      double dz = cz + 0.5 - z;
      float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
      player.teleportTo(level, x, cy, z, yaw, 28.0F);
   }

   public int canvasWallZ() {
      return this.origin.getZ() + this.size - 1;
   }

   public void teleportCanvas(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
      double cx = this.origin.getX() + this.size / 2.0 + 0.5;
      double z = this.origin.getZ() + this.size - 8.5;
      if (z < this.origin.getZ() + 1.5) {
         z = this.origin.getZ() + this.size / 2.0 + 0.5;
      }
      player.teleportTo(level, cx, this.origin.getY() + 1.0, z, 0.0F, 8.0F);
   }

   public void teleportCanvasWatch(net.minecraft.server.level.ServerPlayer player, ServerLevel level, int index, int total) {
      int n = Math.max(1, total);
      double z = this.origin.getZ() + Math.max(3.0, this.size - 7.5);
      double span = Math.max(2.0, this.size - 6.0);
      double t = n <= 1 ? 0.5 : Math.floorMod(index, n) / (double) (n - 1);
      double x = this.origin.getX() + 3.0 + t * span;
      player.teleportTo(level, x, this.origin.getY() + 2.5, z, 0.0F, 10.0F);
   }
}
