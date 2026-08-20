package net.exmo.sreGame.games.dig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DigArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int SIZE = 32;
   public static final int GAP = 4;
   public static final int PAD = 1;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;
   private int lastLayers = 3;

   public DigArena(int slot, BlockPos origin) {
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

   public int lastLayers() {
      return this.lastLayers;
   }

   public void remember(int layers) {
      this.lastLayers = Math.max(2, Math.min(6, layers));
   }

   public int lavaY() {
      return this.origin.getY();
   }

   public int layerY(int layerIndex) {
      return this.origin.getY() + layerIndex * (1 + GAP);
   }

   public int topY(int layers) {
      return this.layerY(Math.max(1, layers) - 1);
   }

   public int wallTop(int layers) {
      return this.topY(layers) + 4;
   }

   public int minX() {
      return this.origin.getX();
   }

   public int minZ() {
      return this.origin.getZ();
   }

   public int maxX() {
      return this.origin.getX() + SIZE - 1;
   }

   public int maxZ() {
      return this.origin.getZ() + SIZE - 1;
   }

   public double centerX() {
      return this.origin.getX() + SIZE / 2.0;
   }

   public double centerZ() {
      return this.origin.getZ() + SIZE / 2.0;
   }

   public boolean contains(double x, double y, double z, int layers) {
      return x >= this.minX() && x < this.minX() + SIZE
         && z >= this.minZ() && z < this.minZ() + SIZE
         && y >= this.lavaY() - 0.5 && y <= this.wallTop(layers) + 1;
   }

   public boolean isInner(BlockPos pos) {
      return pos.getX() >= this.minX() && pos.getX() <= this.maxX()
         && pos.getZ() >= this.minZ() && pos.getZ() <= this.maxZ();
   }

   public boolean isWall(BlockPos pos, int layers) {
      if (pos.getY() < this.lavaY() || pos.getY() > this.wallTop(layers)) {
         return false;
      }
      int x = pos.getX();
      int z = pos.getZ();
      int x0 = this.minX() - PAD;
      int z0 = this.minZ() - PAD;
      int x1 = this.maxX() + PAD;
      int z1 = this.maxZ() + PAD;
      if (x < x0 || x > x1 || z < z0 || z > z1) {
         return false;
      }
      return x == x0 || x == x1 || z == z0 || z == z1;
   }

   public boolean isSnowLayer(int y, int layers) {
      for (int i = 1; i < layers; i++) {
         if (y == this.layerY(i)) {
            return true;
         }
      }
      return false;
   }

   public boolean isBreakableSnow(BlockPos pos, int layers, BlockState state) {
      return state.is(Blocks.SNOW_BLOCK) && this.isInner(pos) && this.isSnowLayer(pos.getY(), layers);
   }

   public Vec3 spawn(int index, int total, int layers) {
      int n = Math.max(1, total);
      double angle = (Math.PI * 2.0 * Math.floorMod(index, n)) / n;
      double radius = SIZE / 2.0 - 4.0;
      double x = this.centerX() + Math.cos(angle) * radius + 0.5;
      double z = this.centerZ() + Math.sin(angle) * radius + 0.5;
      return new Vec3(x, this.topY(layers) + 1.0, z);
   }

   public Vec3 watch(int layers) {
      return new Vec3(this.centerX() + 0.5, this.topY(layers) + 6.0, this.centerZ() + 0.5);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
   }

   public static BlockState snow() {
      return Blocks.SNOW_BLOCK.defaultBlockState();
   }

   public static BlockState lava() {
      return Blocks.LAVA.defaultBlockState();
   }

   public static BlockState wall() {
      return Blocks.BARRIER.defaultBlockState();
   }
}
