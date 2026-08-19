package net.exmo.sreGame.luckypillar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PillarArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int MAX_SIZE = 192;
   public static final int WALL_EXTRA = 16;
   public static final int MIN_LIVE_SIZE = 8;
   public static final BlockState WALL = Blocks.BLACK_CONCRETE.defaultBlockState();
   public static final BlockState BASIN = Blocks.STONE.defaultBlockState();

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;
   private int lastBorder = MAX_SIZE;
   private int lastHeight = 80;

   public PillarArena(int slot, BlockPos origin) {
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

   public int lastBorder() {
      return this.lastBorder;
   }

   public int lastHeight() {
      return this.lastHeight;
   }

   public void remember(int border, int height) {
      this.lastBorder = Math.max(MIN_LIVE_SIZE, Math.min(MAX_SIZE, border));
      this.lastHeight = Math.max(16, Math.min(80, height));
   }

   public int minX(int border) {
      return this.origin.getX() + (MAX_SIZE - border) / 2;
   }

   public int minZ(int border) {
      return this.origin.getZ() + (MAX_SIZE - border) / 2;
   }

   public int maxX(int border) {
      return this.minX(border) + border - 1;
   }

   public int maxZ(int border) {
      return this.minZ(border) + border - 1;
   }

   public int floorY() {
      return this.origin.getY();
   }

   public int basinY() {
      return this.origin.getY() - 1;
   }

   public int wallTop(int pillarHeight) {
      return this.origin.getY() + pillarHeight + WALL_EXTRA;
   }

   public double centerX() {
      return this.origin.getX() + MAX_SIZE / 2.0;
   }

   public double centerZ() {
      return this.origin.getZ() + MAX_SIZE / 2.0;
   }

   public boolean contains(double x, double y, double z, int border, int pillarHeight, int inset) {
      int live = Math.max(MIN_LIVE_SIZE, border - inset * 2);
      int x0 = this.minX(border) + inset;
      int z0 = this.minZ(border) + inset;
      return x >= x0 && x < x0 + live
         && z >= z0 && z < z0 + live
         && y >= this.basinY() - 1.5 && y <= this.wallTop(pillarHeight) + 2;
   }

   public boolean isFloor(BlockPos pos) {
      return pos.getY() == this.floorY()
         && pos.getX() >= this.origin.getX() && pos.getX() < this.origin.getX() + MAX_SIZE
         && pos.getZ() >= this.origin.getZ() && pos.getZ() < this.origin.getZ() + MAX_SIZE;
   }

   public boolean isBasin(BlockPos pos) {
      return pos.getY() == this.basinY()
         && pos.getX() >= this.origin.getX() && pos.getX() < this.origin.getX() + MAX_SIZE
         && pos.getZ() >= this.origin.getZ() && pos.getZ() < this.origin.getZ() + MAX_SIZE;
   }

   public boolean isCurrentWall(BlockPos pos, int border, int pillarHeight, int inset) {
      if (pos.getY() < this.basinY() || pos.getY() > this.wallTop(pillarHeight)) {
         return false;
      }
      int x0 = this.minX(border) + inset;
      int z0 = this.minZ(border) + inset;
      int x1 = this.maxX(border) - inset;
      int z1 = this.maxZ(border) - inset;
      if (pos.getX() < x0 || pos.getX() > x1 || pos.getZ() < z0 || pos.getZ() > z1) {
         return pos.getX() >= this.origin.getX() && pos.getX() < this.origin.getX() + MAX_SIZE
            && pos.getZ() >= this.origin.getZ() && pos.getZ() < this.origin.getZ() + MAX_SIZE;
      }
      return pos.getX() == x0 || pos.getX() == x1 || pos.getZ() == z0 || pos.getZ() == z1;
   }

   public boolean canBuild(BlockPos pos, int border, int pillarHeight, int inset) {
      int live = Math.max(MIN_LIVE_SIZE, border - inset * 2);
      int x0 = this.minX(border) + inset + 1;
      int z0 = this.minZ(border) + inset + 1;
      return pos.getX() >= x0 && pos.getX() < x0 + live - 2
         && pos.getZ() >= z0 && pos.getZ() < z0 + live - 2
         && pos.getY() > this.floorY()
         && pos.getY() < this.wallTop(pillarHeight);
   }

   public List<BlockPos> pillarBases(int players, int border) {
      int n = Math.max(1, players);
      double radius = Math.max(4.0, border / 4.0);
      double cx = this.centerX();
      double cz = this.centerZ();
      List<BlockPos> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         double angle = (Math.PI * 2.0 * i) / n - Math.PI / 2.0;
         int x = (int) Math.floor(cx + Math.cos(angle) * radius);
         int z = (int) Math.floor(cz + Math.sin(angle) * radius);
         out.add(new BlockPos(x, this.floorY(), z));
      }
      return out;
   }

   public Vec3 spawnOn(BlockPos base, int pillarHeight) {
      return new Vec3(base.getX() + 0.5, this.floorY() + pillarHeight + 1.0, base.getZ() + 0.5);
   }

   public Vec3 watch(int pillarHeight) {
      return new Vec3(this.centerX(), this.floorY() + pillarHeight + 8.0, this.centerZ());
   }

   public Vec3 snapInside(double x, double y, double z, int border, int pillarHeight, int inset) {
      int live = Math.max(MIN_LIVE_SIZE, border - inset * 2);
      double x0 = this.minX(border) + inset + 1.5;
      double z0 = this.minZ(border) + inset + 1.5;
      double x1 = x0 + live - 3.0;
      double z1 = z0 + live - 3.0;
      double nx = Math.min(x1, Math.max(x0, x));
      double nz = Math.min(z1, Math.max(z0, z));
      double ny = Math.min(this.wallTop(pillarHeight) - 1.0, Math.max(this.floorY() + 1.0, y));
      return new Vec3(nx, ny, nz);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
   }

   public record Layout(int borderSize, int pillarHeight, BlockState floor, BlockState pillar, boolean fishing, int players) {
   }
}
