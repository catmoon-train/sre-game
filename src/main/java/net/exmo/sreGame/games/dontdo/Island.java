package net.exmo.sreGame.games.dontdo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Island {
   public enum State {
      IDLE,
      IN_USE
   }

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private long seed;
   private int[] heightmap;

   public Island(int slot, BlockPos origin) {
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

   public long seed() {
      return this.seed;
   }

   public void resetSeed(long seed) {
      this.seed = seed;
      this.heightmap = IslandGenerator.heightmap(seed);
   }

   public int heightAt(int px, int pz) {
      if (this.heightmap == null || px < 0 || pz < 0 || px >= IslandGenerator.PLAY || pz >= IslandGenerator.PLAY) {
         return 0;
      }
      return this.heightmap[pz * IslandGenerator.PLAY + px];
   }

   public int playMinX() {
      return this.origin.getX() + IslandGenerator.BARRIER + IslandGenerator.WALL;
   }

   public int playMinZ() {
      return this.origin.getZ() + IslandGenerator.BARRIER + IslandGenerator.WALL;
   }

   public int playMaxX() {
      return this.playMinX() + IslandGenerator.PLAY - 1;
   }

   public int playMaxZ() {
      return this.playMinZ() + IslandGenerator.PLAY - 1;
   }

   public boolean inPlayable(BlockPos pos) {
      return pos.getX() >= this.playMinX() && pos.getX() <= this.playMaxX()
         && pos.getZ() >= this.playMinZ() && pos.getZ() <= this.playMaxZ()
         && pos.getY() > IslandGenerator.MIN_Y && pos.getY() < IslandGenerator.MAX_Y;
   }

   public AABB playBox() {
      return new AABB(
         this.playMinX(), IslandGenerator.MIN_Y, this.playMinZ(),
         this.playMaxX() + 1.0, IslandGenerator.MAX_Y, this.playMaxZ() + 1.0
      );
   }

   public boolean inBounds(BlockPos pos) {
      int x = pos.getX();
      int z = pos.getZ();
      return x >= this.origin.getX() && x < this.origin.getX() + IslandGenerator.TOTAL
         && z >= this.origin.getZ() && z < this.origin.getZ() + IslandGenerator.TOTAL
         && pos.getY() >= IslandGenerator.MIN_Y && pos.getY() <= IslandGenerator.MAX_Y;
   }

   public boolean isBorder(BlockPos pos) {
      return this.inBounds(pos) && !this.inPlayable(pos);
   }

   public Vec3 spawn(int index, int total) {
      int n = Math.max(1, total);
      double angle = (Math.PI * 2.0 * index) / n;
      double radius = 8.0;
      int cx = (this.playMinX() + this.playMaxX()) / 2;
      int cz = (this.playMinZ() + this.playMaxZ()) / 2;
      int x = (int) Math.round(cx + Math.cos(angle) * radius);
      int z = (int) Math.round(cz + Math.sin(angle) * radius);
      int px = x - this.playMinX();
      int pz = z - this.playMinZ();
      int y = this.heightAt(px, pz) + 1;
      return new Vec3(x + 0.5, y, z + 0.5);
   }

   public Vec3 watch() {
      int cx = (this.playMinX() + this.playMaxX()) / 2;
      int cz = (this.playMinZ() + this.playMaxZ()) / 2;
      return new Vec3(cx + 0.5, 40.0, cz + 0.5);
   }

   public Vec3 respawn() {
      int cx = (this.playMinX() + this.playMaxX()) / 2 + ThreadLocal.x();
      int cz = (this.playMinZ() + this.playMaxZ()) / 2 + ThreadLocal.z();
      int px = Math.max(0, Math.min(IslandGenerator.PLAY - 1, cx - this.playMinX()));
      int pz = Math.max(0, Math.min(IslandGenerator.PLAY - 1, cz - this.playMinZ()));
      return new Vec3(this.playMinX() + px + 0.5, this.heightAt(px, pz) + 1, this.playMinZ() + pz + 0.5);
   }

   private static final class ThreadLocal {
      static int x() {
         return java.util.concurrent.ThreadLocalRandom.current().nextInt(-12, 13);
      }

      static int z() {
         return java.util.concurrent.ThreadLocalRandom.current().nextInt(-12, 13);
      }
   }
}
