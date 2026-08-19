package net.exmo.sreGame.fakehuman;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Safehouse {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int SIZE_X = 32;
   public static final int SIZE_Z = 48;
   public static final int HEIGHT = 8;
   public static final int MAX_BEDS = 8;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;

   public Safehouse(int slot, BlockPos origin) {
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

   public boolean contains(double x, double y, double z) {
      int ox = this.origin.getX();
      int oy = this.origin.getY();
      int oz = this.origin.getZ();
      return x >= ox && x < ox + SIZE_X
         && z >= oz && z < oz + SIZE_Z
         && y >= oy && y <= oy + HEIGHT + 1.5;
   }

   public boolean inWaiting(double x, double y, double z) {
      return this.inBox(x, y, z, 1, 1, 1, 31, 6, 12);
   }

   public boolean inDoor(double x, double y, double z) {
      return this.inBox(x, y, z, 12, 1, 9, 20, 6, 17);
   }

   public boolean inHouse(double x, double y, double z) {
      return this.inBox(x, y, z, 1, 1, 13, 31, 6, 47);
   }

   private boolean inBox(double x, double y, double z, int x1, int y1, int z1, int x2, int y2, int z2) {
      int ox = this.origin.getX();
      int oy = this.origin.getY();
      int oz = this.origin.getZ();
      return x >= ox + x1 && x < ox + x2
         && y >= oy + y1 && y < oy + y2
         && z >= oz + z1 && z < oz + z2;
   }

   public Vec3 waitingSpawn(int index, int total) {
      int n = Math.max(1, total);
      double t = n <= 1 ? 0.5 : Math.floorMod(index, n) / (double) (n - 1);
      return this.abs(4.5 + t * 22.0, 1.0, 5.5);
   }

   public Vec3 doorOutside() {
      return this.abs(16.5, 1.0, 10.5);
   }

   public Vec3 doorOutsideSecondary() {
      return this.abs(14.5, 1.0, 10.5);
   }

   public Vec3 vestibule() {
      return this.abs(16.5, 1.0, 14.5);
   }

   public Vec3 living(int index) {
      int n = Math.max(1, index);
      double t = Math.floorMod(n, 6) / 6.0;
      return this.abs(6.5 + t * 18.0, 1.0, 21.5);
   }

   public Vec3 bed(int index) {
      int i = Math.floorMod(index, MAX_BEDS);
      boolean left = i < 4;
      int row = left ? i : i - 4;
      double x = left ? 6.5 : 25.5;
      double z = 31.5 + row * 2.5;
      return this.abs(x, 1.0, z);
   }

   public Vec3 spectator() {
      return this.abs(16.5, 4.5, 22.5);
   }

   public Vec3 doorWatch(int index, int total) {
      int n = Math.max(1, total);
      double t = n <= 1 ? 0.5 : Math.floorMod(index, n) / (double) (n - 1);
      return this.abs(8.5 + t * 16.0, 3.4, 6.5);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
      player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 8.0F);
   }

   private Vec3 abs(double x, double y, double z) {
      return new Vec3(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
   }
}
