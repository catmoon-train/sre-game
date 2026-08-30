package net.exmo.sreGame.games.nametagwar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class NameTagWarArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int MAX_SIZE = 128;
   public static final int MIN_LIVE_SIZE = 8;

   /** 多层竞技场：楼层数与层高（层内净高 = FLOOR_SPACING - 1）。 */
   public static final int FLOORS = 2;
   public static final int FLOOR_SPACING = 5;
   /** 楼梯：每面墙中点一条，2 宽 × 5 长，向内上升一层。 */
   public static final int STAIR_LEN = FLOOR_SPACING;
   /** 结构柱网格（避开中心出生点与楼梯）。 */
   public static final int PILLAR_SPACING = 16;
   public static final int PILLAR_OFFSET = 8;
   /** Perimeter columns break up the long outer-wall lanes, so players cannot safely hug one wall. */
   public static final int PERIMETER_PILLAR_SPACING = 8;
   public static final int PERIMETER_PILLAR_OFFSET = 4;

   /** 收缩阶段的黑墙（危险圈）。 */
   public static final BlockState WALL = Blocks.BLACK_CONCRETE.defaultBlockState();
   /** 建筑外墙（竞技场壳体）。 */
   public static final BlockState EXTERIOR = Blocks.STONE_BRICKS.defaultBlockState();
   public static final BlockState BASIN = Blocks.STONE.defaultBlockState();
   public static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
   public static final BlockState STAIR = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
   public static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();

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

   public int floorLevel(int i) {
      return this.origin.getY() + i * FLOOR_SPACING;
   }

   public int basinY() {
      return this.origin.getY() - 1;
   }

   public int wallTop() {
      return this.origin.getY() + FLOORS * FLOOR_SPACING - 1;
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
      return new Vec3(this.centerX(), this.wallTop() + 6.0, this.centerZ());
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

   /**
    * 多层竞技场建筑的方块生成：给定绝对坐标与当前边界大小，返回应放置的方块。
    */
   public BlockState structureWant(int x, int y, int z, int size) {
      int x0 = this.minX(size);
      int z0 = this.minZ(size);
      int x1 = this.maxX(size);
      int z1 = this.maxZ(size);
      if (x < x0 || x > x1 || z < z0 || z > z1 || y < this.basinY() || y > this.wallTop()) {
         return Blocks.AIR.defaultBlockState();
      }
      int lx = x - x0;
      int lz = z - z0;
      int relY = y - this.origin.getY();
      int n = size;

      if (relY == -1) {
         return BASIN;
      }
      boolean edge = lx == 0 || lx == n - 1 || lz == 0 || lz == n - 1;
      if (edge) {
         return EXTERIOR;
      }
      BlockState centerLadder = this.centerLadder(lx, relY, lz, n);
      if (centerLadder != null) {
         return centerLadder;
      }
      BlockState stair = this.stairState(lx, relY, lz, n);
      if (stair != null) {
         return stair;
      }
      if (this.isPillar(lx, lz, n)) {
         return PILLAR;
      }
      if (this.isCornerTopFill(lx, relY, lz, n)) {
         return FLOOR;
      }
      if (relY >= 0 && relY % FLOOR_SPACING == 0 && relY / FLOOR_SPACING < FLOORS) {
         return this.isStairHole(lx, lz, relY, n) ? Blocks.AIR.defaultBlockState() : FLOOR;
      }
      return Blocks.AIR.defaultBlockState();
   }

   /** 结构柱：内部网格，通高，作为每层掩体。 */
   private boolean isPillar(int lx, int lz, int n) {
      int inset = 3;
      if (this.isPerimeterPillar(lx, lz, n)) {
         return true;
      }
      if (lx < inset || lz < inset || lx >= n - inset || lz >= n - inset) {
         return false;
      }
      return lx % PILLAR_SPACING == PILLAR_OFFSET && lz % PILLAR_SPACING == PILLAR_OFFSET;
   }

   /**
    * One block in from every exterior wall, add regularly-spaced full-height columns.
    * Stairs are checked first by {@link #structureWant}, so their entrances remain clear.
    */
   private boolean isPerimeterPillar(int lx, int lz, int n) {
      boolean onNorthOrSouth = (lz == 2 || lz == n - 3)
         && lx >= 3 && lx <= n - 4
         && Math.floorMod(lx - PERIMETER_PILLAR_OFFSET, PERIMETER_PILLAR_SPACING) == 0;
      boolean onWestOrEast = (lx == 2 || lx == n - 3)
         && lz >= 3 && lz <= n - 4
         && Math.floorMod(lz - PERIMETER_PILLAR_OFFSET, PERIMETER_PILLAR_SPACING) == 0;
      return onNorthOrSouth || onWestOrEast;
   }

   /** Close the top-cell pockets in every corner so no player can hide in the ceiling grid. */
   private boolean isCornerTopFill(int lx, int relY, int lz, int n) {
      if (relY != FLOORS * FLOOR_SPACING - 1) return false;
      boolean north = lz <= 3, south = lz >= n - 4;
      boolean west = lx <= 3, east = lx >= n - 4;
      return (north || south) && (west || east);
   }

   /**
    * 楼梯：北/西墙中点（0→1 层，向内上升），南/东墙中点（1→2 层，向内上升）。
    * 每条楼梯只上升一层，终点处的地板开 2×2 洞口供玩家通过。
    */
   private BlockState stairState(int lx, int relY, int lz, int n) {
      int c = n / 2;
      if (lx == c - 1 || lx == c) {
         BlockState s = this.zWallStair(lz, relY, n);
         if (s != null) {
            return s;
         }
      }
      if (lz == c - 1 || lz == c) {
         BlockState s = this.xWallStair(lx, relY, n);
         if (s != null) {
            return s;
         }
      }
      return null;
   }

   /** A central vertical ladder provides a direct route through all three floors. */
   private BlockState centerLadder(int lx, int relY, int lz, int n) {
      int c = n / 2;
      if (lx == c && lz == c + 1 && relY >= 1 && relY <= wallTop() - origin.getY()) return PILLAR;
      if (lx == c && lz == c && relY >= 1 && relY <= wallTop() - origin.getY()) {
         return Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
      }
      return null;
   }

   /** 沿 Z 向跑的楼梯：北墙（0→1，向南升）。 */
   private BlockState zWallStair(int lz, int relY, int n) {
      if (relY >= 1 && relY <= STAIR_LEN && lz == relY) {
         return STAIR.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
      }
      return null;
   }

   /** 沿 X 向跑的楼梯：西墙（0→1，向东升）。 */
   private BlockState xWallStair(int lx, int relY, int n) {
      if (relY >= 1 && relY <= STAIR_LEN && lx == relY) {
         return STAIR.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
      }
      return null;
   }

   /** 楼梯终点地板上的 2×2 洞口：1 层开在北/西，2 层开在南/东。 */
   private boolean isStairHole(int lx, int lz, int relY, int n) {
      int c = n / 2;
      boolean crossX = lx == c - 1 || lx == c;
      boolean crossZ = lz == c - 1 || lz == c;
      if (relY == FLOOR_SPACING) {
         return (crossX && (lz == STAIR_LEN || lz == STAIR_LEN + 1))
            || (crossZ && (lx == STAIR_LEN || lx == STAIR_LEN + 1));
      }
      return false;
   }

   public record Layout(int borderSize) {
   }
}
