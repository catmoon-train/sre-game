package net.exmo.sreGame.games.nametagwar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NameTagWarArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int MAX_SIZE = 128;
   public static final int WALL_EXTRA = 8;
   public static final int MIN_LIVE_SIZE = 8;
   public static final BlockState WALL = Blocks.BLACK_CONCRETE.defaultBlockState();
   public static final BlockState BASIN = Blocks.STONE.defaultBlockState();
   public static final BlockState FLOOR = Blocks.WHITE_CONCRETE.defaultBlockState();

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;
   private int lastBorder = MAX_SIZE;

   public NameTagWarArena(int slot, BlockPos origin) {
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

   public void remember(int border) {
      this.lastBorder = Math.max(MIN_LIVE_SIZE, Math.min(MAX_SIZE, border));
   }

   public int floorY() {
      return this.origin.getY();
   }

   public int basinY() {
      return this.origin.getY() - 1;
   }

   public int wallTop() {
      return this.origin.getY() + 6 + WALL_EXTRA;
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

   public double centerX() {
      return this.origin.getX() + MAX_SIZE / 2.0;
   }

   public double centerZ() {
      return this.origin.getZ() + MAX_SIZE / 2.0;
   }

   public boolean contains(double x, double y, double z, int border, int inset) {
      int live = Math.max(MIN_LIVE_SIZE, border - inset * 2);
      int x0 = this.minX(border) + inset;
      int z0 = this.minZ(border) + inset;
      return x >= x0 && x < x0 + live
         && z >= z0 && z < z0 + live
         && y >= this.basinY() - 1.5 && y <= this.wallTop() + 12;
   }

   public boolean isCurrentWall(BlockPos pos, int border, int inset) {
      if (pos.getY() < this.basinY() || pos.getY() > this.wallTop()) {
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

   public Vec3 spawnCenter() {
      return new Vec3(this.centerX(), this.floorY() + 1.0, this.centerZ());
   }

   public List<Vec3> spawnPoints(int players) {
      int n = Math.max(1, players);
      List<Vec3> out = new ArrayList<>(n);
      if (n == 1) {
         out.add(this.spawnCenter());
         return out;
      }
      double radius = Math.max(4.0, (this.lastBorder / 2.0) - 4.0);
      for (int i = 0; i < n; i++) {
         double angle = (Math.PI * 2.0 * i) / n - Math.PI / 2.0;
         double x = this.centerX() + Math.cos(angle) * radius;
         double z = this.centerZ() + Math.sin(angle) * radius;
         out.add(new Vec3(x, this.floorY() + 1.0, z));
      }
      return out;
   }

   public Vec3 watch() {
      return new Vec3(this.centerX(), this.floorY() + 10.0, this.centerZ());
   }

   public Vec3 snapInside(double x, double y, double z, int border, int inset) {
      int live = Math.max(MIN_LIVE_SIZE, border - inset * 2);
      double x0 = this.minX(border) + inset + 1.5;
      double z0 = this.minZ(border) + inset + 1.5;
      double x1 = x0 + live - 3.0;
      double z1 = z0 + live - 3.0;
      double nx = Math.min(x1, Math.max(x0, x));
      double nz = Math.min(z1, Math.max(z0, z));
      double ny = Math.min(this.wallTop() - 1.0, Math.max(this.floorY() + 1.0, y));
      return new Vec3(nx, ny, nz);
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
   }

   public record Layout(int borderSize) {
   }
}
