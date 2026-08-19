package net.exmo.sreGame.pillarpummel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PummelArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int MAX_SIZE = 80;
   public static final int SPACING = 6;
   public static final int PILLAR_HEIGHT = 6;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;
   private Layout lastLayout = new Layout(7, 2, 0, 0, "SQUARE", 0L);

   public PummelArena(int slot, BlockPos origin) {
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

   public Layout lastLayout() {
      return this.lastLayout;
   }

   public void remember(Layout layout) {
      if (layout != null) {
         this.lastLayout = layout;
      }
   }

   public int basinY() {
      return this.origin.getY();
   }

   public int platformY() {
      return this.origin.getY() + PILLAR_HEIGHT;
   }

   public int wallTop(int maxBuild) {
      return this.platformY() + Math.max(8, maxBuild) + 8;
   }

   public int gridPixel(int pillars) {
      return (Math.max(2, pillars) - 1) * SPACING + 1;
   }

   public int gridOriginX(int pillars) {
      return this.origin.getX() + (MAX_SIZE - this.gridPixel(pillars)) / 2;
   }

   public int gridOriginZ(int pillars) {
      return this.origin.getZ() + (MAX_SIZE - this.gridPixel(pillars)) / 2;
   }

   public int pillarX(int gx, int pillars) {
      return this.gridOriginX(pillars) + gx * SPACING;
   }

   public int pillarZ(int gz, int pillars) {
      return this.gridOriginZ(pillars) + gz * SPACING;
   }

   public BlockPos pillarCap(int gx, int gz, int pillars) {
      return new BlockPos(this.pillarX(gx, pillars), this.platformY(), this.pillarZ(gz, pillars));
   }

   public BlockPos platformCenter(int cx, int cz, int pillars) {
      return new BlockPos(this.pillarX(cx, pillars) + 3, this.platformY(), this.pillarZ(cz, pillars) + 3);
   }

   public Vec3 arenaCenter(int pillars) {
      BlockPos origin = this.origin;
      return new Vec3(origin.getX() + MAX_SIZE / 2.0, this.platformY() + 1.0, origin.getZ() + MAX_SIZE / 2.0);
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX() && x < this.origin.getX() + MAX_SIZE
         && z >= this.origin.getZ() && z < this.origin.getZ() + MAX_SIZE
         && y >= this.basinY() - 2 && y <= this.wallTop(32) + 4;
   }

   public boolean inArenaColumn(int x, int z) {
      return x >= this.origin.getX() && x < this.origin.getX() + MAX_SIZE
         && z >= this.origin.getZ() && z < this.origin.getZ() + MAX_SIZE;
   }

   public boolean isWall(BlockPos pos) {
      int x = pos.getX();
      int z = pos.getZ();
      int y = pos.getY();
      if (!this.inArenaColumn(x, z) || y < this.basinY() || y > this.wallTop(32)) {
         return false;
      }
      int x0 = this.origin.getX();
      int z0 = this.origin.getZ();
      int x1 = x0 + MAX_SIZE - 1;
      int z1 = z0 + MAX_SIZE - 1;
      return x == x0 || x == x1 || z == z0 || z == z1;
   }

   public boolean isBasin(BlockPos pos) {
      return pos.getY() == this.basinY() && this.inArenaColumn(pos.getX(), pos.getZ());
   }

   public boolean isPillar(BlockPos pos, int pillars) {
      if (pos.getY() < this.basinY() + 1 || pos.getY() > this.platformY()) {
         return false;
      }
      int gx = pos.getX() - this.gridOriginX(pillars);
      int gz = pos.getZ() - this.gridOriginZ(pillars);
      if (gx < 0 || gz < 0 || gx % SPACING != 0 || gz % SPACING != 0) {
         return false;
      }
      int px = gx / SPACING;
      int pz = gz / SPACING;
      return px >= 0 && pz >= 0 && px < pillars && pz < pillars;
   }

   public int[] cellAt(int x, int z, int pillars) {
      int cells = pillars - 1;
      int lx = x - this.gridOriginX(pillars);
      int lz = z - this.gridOriginZ(pillars);
      if (lx < 0 || lz < 0) {
         return null;
      }
      int cx = lx / SPACING;
      int cz = lz / SPACING;
      if (cx < 0 || cz < 0 || cx >= cells || cz >= cells) {
         return null;
      }
      int ox = lx % SPACING;
      int oz = lz % SPACING;
      if (ox < 1 || ox > 5 || oz < 1 || oz > 5) {
         return null;
      }
      return new int[] {cx, cz};
   }

   public int[] cellAt(BlockPos pos, int pillars) {
      return this.cellAt(pos.getX(), pos.getZ(), pillars);
   }

   public int[] xBridgeAt(int x, int z, int pillars) {
      int lx = x - this.gridOriginX(pillars);
      int lz = z - this.gridOriginZ(pillars);
      if (lx < 0 || lz < 0 || lz % SPACING != 0) {
         return null;
      }
      int gz = lz / SPACING;
      int gx = lx / SPACING;
      int ox = lx % SPACING;
      if (ox < 1 || ox > 5 || gx < 0 || gx >= pillars - 1 || gz < 0 || gz >= pillars) {
         return null;
      }
      return new int[] {gx, gz};
   }

   public int[] zBridgeAt(int x, int z, int pillars) {
      int lx = x - this.gridOriginX(pillars);
      int lz = z - this.gridOriginZ(pillars);
      if (lx < 0 || lz < 0 || lx % SPACING != 0) {
         return null;
      }
      int gx = lx / SPACING;
      int gz = lz / SPACING;
      int oz = lz % SPACING;
      if (oz < 1 || oz > 5 || gz < 0 || gz >= pillars - 1 || gx < 0 || gx >= pillars) {
         return null;
      }
      return new int[] {gx, gz};
   }

   public int[] spawnCell(int team, int pillars) {
      return this.spawnCell(team, new Layout(pillars, 4, 0, 0, "SQUARE", 0L));
   }

   public int[] preferredSpawn(int team, int pillars) {
      int n = Math.max(1, pillars - 1);
      return switch (team) {
         case 0 -> new int[] {0, 0};
         case 1 -> new int[] {n - 1, n - 1};
         case 2 -> new int[] {n - 1, 0};
         default -> new int[] {0, n - 1};
      };
   }

   public int[] spawnCell(int team, Layout layout) {
      int pillars = layout.grid();
      int[] want = this.preferredSpawn(team, pillars);
      if (this.cellEnabled(want[0], want[1], layout)) {
         return want;
      }
      int best = Integer.MAX_VALUE;
      int[] found = want;
      int n = pillars - 1;
      for (int cx = 0; cx < n; cx++) {
         for (int cz = 0; cz < n; cz++) {
            if (!this.cellEnabled(cx, cz, layout)) {
               continue;
            }
            int dist = (cx - want[0]) * (cx - want[0]) + (cz - want[1]) * (cz - want[1]);
            if (dist < best) {
               best = dist;
               found = new int[] {cx, cz};
            }
         }
      }
      return found;
   }

   public Vec3 spawn(int team, int pillars) {
      int[] cell = this.spawnCell(team, this.lastLayout);
      BlockPos c = this.platformCenter(cell[0], cell[1], pillars);
      return new Vec3(c.getX() + 0.5, this.platformY() + 1.0, c.getZ() + 0.5);
   }

   public Vec3 spawnAt(int cx, int cz, int pillars) {
      BlockPos c = this.platformCenter(cx, cz, pillars);
      return new Vec3(c.getX() + 0.5, this.platformY() + 1.0, c.getZ() + 0.5);
   }

   public BlockPos shopPos(int team, int pillars) {
      int[] cell = this.spawnCell(team, this.lastLayout);
      return this.shopPos(cell[0], cell[1], pillars);
   }

   public BlockPos shopPos(int cx, int cz, int pillars) {
      return this.platformCenter(cx, cz, pillars).offset(-2, 1, -2);
   }

   public BlockPos chestPos(int team, int pillars) {
      int[] cell = this.spawnCell(team, this.lastLayout);
      return this.chestPos(cell[0], cell[1], pillars);
   }

   public BlockPos chestPos(int cx, int cz, int pillars) {
      return this.platformCenter(cx, cz, pillars).offset(2, 1, -2);
   }

   public BlockPos spawnGenerator(int team, int pillars) {
      int[] cell = this.spawnCell(team, this.lastLayout);
      return this.spawnGenerator(cell[0], cell[1], pillars);
   }

   public BlockPos spawnGenerator(int cx, int cz, int pillars) {
      return this.platformCenter(cx, cz, pillars).offset(0, 1, 2);
   }

   public boolean inBase(BlockPos pos, int team, int pillars) {
      int[] cell = this.spawnCell(team, this.lastLayout.grid() == pillars ? this.lastLayout
         : new Layout(pillars, 4, 0, 0, "SQUARE", 0L));
      BlockPos c = this.platformCenter(cell[0], cell[1], pillars);
      return Math.abs(pos.getX() - c.getX()) <= 2
         && Math.abs(pos.getZ() - c.getZ()) <= 2
         && pos.getY() >= this.platformY()
         && pos.getY() <= this.platformY() + 4;
   }

   public boolean inAnyBase(BlockPos pos, int teamCount, int pillars) {
      for (int t = 0; t < teamCount; t++) {
         if (this.inBase(pos, t, pillars)) {
            return true;
         }
      }
      return false;
   }

   public boolean inSafeZone(double x, double z, int team, int pillars, int radius) {
      if (radius <= 0) {
         return false;
      }
      int[] cell = this.spawnCell(team, this.lastLayout);
      BlockPos c = this.platformCenter(cell[0], cell[1], pillars);
      double dx = x - (c.getX() + 0.5);
      double dz = z - (c.getZ() + 0.5);
      return dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5);
   }

   public int safeZoneTeam(double x, double z, int teamCount, int pillars, int radius) {
      for (int t = 0; t < teamCount; t++) {
         if (this.inSafeZone(x, z, t, pillars, radius)) {
            return t;
         }
      }
      return -1;
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
   }

   public BlockState structureWant(int x, int y, int z, Layout layout) {
      int x0 = this.origin.getX();
      int z0 = this.origin.getZ();
      int x1 = x0 + MAX_SIZE - 1;
      int z1 = z0 + MAX_SIZE - 1;
      if (y == this.basinY()) {
         return Blocks.STONE.defaultBlockState();
      }
      if (x == x0 || x == x1 || z == z0 || z == z1) {
         return Blocks.BLACK_CONCRETE.defaultBlockState();
      }
      int pillars = layout.grid();
      BlockPos pos = new BlockPos(x, y, z);
      if (this.isPillar(pos, pillars)) {
         int gx = (pos.getX() - this.gridOriginX(pillars)) / SPACING;
         int gz = (pos.getZ() - this.gridOriginZ(pillars)) / SPACING;
         if (!this.pillarEnabled(gx, gz, layout)) {
            return Blocks.AIR.defaultBlockState();
         }
         if (y == this.platformY()) {
            return Blocks.QUARTZ_BLOCK.defaultBlockState();
         }
         return Blocks.QUARTZ_PILLAR.defaultBlockState();
      }
      if (y == this.platformY()) {
         int[] xb = this.xBridgeAt(x, z, pillars);
         if (xb != null && this.xBridgeEnabled(xb[0], xb[1], layout)) {
            return Blocks.STRUCTURE_VOID.defaultBlockState();
         }
         int[] zb = this.zBridgeAt(x, z, pillars);
         if (zb != null && this.zBridgeEnabled(zb[0], zb[1], layout)) {
            return Blocks.STRUCTURE_VOID.defaultBlockState();
         }
      }
      return Blocks.AIR.defaultBlockState();
   }

   public boolean pillarEnabled(int gx, int gz, Layout layout) {
      int pillars = layout.grid();
      if (gx < 0 || gz < 0 || gx >= pillars || gz >= pillars) {
         return false;
      }
      return switch (layout.arenaShape()) {
         case SQUARE -> true;
         case CIRCLE -> this.pillarDist(gx, gz, pillars) <= this.outerRadius(pillars);
         case RING -> {
            double dist = this.pillarDist(gx, gz, pillars);
            yield dist <= this.outerRadius(pillars) && dist >= this.innerRadius(pillars);
         }
         case PLUS -> this.onPlus(gx, gz, pillars);
         case CROSS -> this.onCross(gx, gz, pillars);
         case SCATTER -> !this.scatterDisabled(layout).contains(gx * 32 + gz);
      };
   }

   public boolean xBridgeEnabled(int gx, int gz, Layout layout) {
      return this.pillarEnabled(gx, gz, layout) && this.pillarEnabled(gx + 1, gz, layout);
   }

   public boolean zBridgeEnabled(int gx, int gz, Layout layout) {
      return this.pillarEnabled(gx, gz, layout) && this.pillarEnabled(gx, gz + 1, layout);
   }

   public boolean cellEnabled(int cx, int cz, Layout layout) {
      return this.pillarEnabled(cx, cz, layout)
         && this.pillarEnabled(cx + 1, cz, layout)
         && this.pillarEnabled(cx, cz + 1, layout)
         && this.pillarEnabled(cx + 1, cz + 1, layout);
   }

   public int enabledCellCount(Layout layout) {
      int n = Math.max(1, layout.grid() - 1);
      int count = 0;
      for (int cx = 0; cx < n; cx++) {
         for (int cz = 0; cz < n; cz++) {
            if (this.cellEnabled(cx, cz, layout)) {
               count++;
            }
         }
      }
      return count;
   }

   private double pillarDist(int gx, int gz, int pillars) {
      double mid = (pillars - 1) / 2.0;
      double dx = (gx - mid) * SPACING;
      double dz = (gz - mid) * SPACING;
      return Math.hypot(dx, dz);
   }

   private int outerRadius(int pillars) {
      return switch (Math.max(4, Math.min(11, pillars))) {
         case 4 -> 12;
         case 5 -> 16;
         case 6 -> 18;
         case 7 -> 22;
         case 8 -> 24;
         case 9 -> 26;
         case 10 -> 28;
         default -> 32;
      };
   }

   private int innerRadius(int pillars) {
      return switch (Math.max(4, Math.min(11, pillars))) {
         case 5, 7 -> 6;
         case 6, 8, 9 -> 8;
         case 10 -> 10;
         case 11 -> 14;
         default -> 4;
      };
   }

   private boolean onPlus(int gx, int gz, int pillars) {
      int a = (pillars - 1) / 2;
      int b = pillars / 2;
      int w = Math.max(0, (pillars - 1) / 4);
      int dx = Math.min(Math.abs(gx - a), Math.abs(gx - b));
      int dz = Math.min(Math.abs(gz - a), Math.abs(gz - b));
      return dx <= w || dz <= w;
   }

   private boolean onCross(int gx, int gz, int pillars) {
      int w = Math.max(0, (pillars - 1) / 5);
      return Math.abs(gx - gz) <= w || Math.abs(gx + gz - (pillars - 1)) <= w;
   }

   private List<Integer> scatterDisabled(Layout layout) {
      int pillars = layout.grid();
      int limit = switch (Math.max(4, Math.min(11, pillars))) {
         case 4 -> 1;
         case 5 -> 2;
         case 6 -> 3;
         case 7 -> 4;
         case 8 -> 6;
         case 9 -> 8;
         case 10 -> 10;
         default -> 12;
      };
      List<Integer> inner = new ArrayList<>();
      for (int gx = 1; gx < pillars - 1; gx++) {
         for (int gz = 1; gz < pillars - 1; gz++) {
            inner.add(gx * 32 + gz);
         }
      }
      Collections.shuffle(inner, new Random(layout.seed()));
      if (inner.size() > limit) {
         return List.copyOf(inner.subList(0, limit));
      }
      return List.copyOf(inner);
   }

   public record Layout(int grid, int teamCount, int initialPlots, int mineCount, String shape, long seed) {
      public PillarPummelSettings.ArenaShape arenaShape() {
         return PillarPummelSettings.ArenaShape.fromName(this.shape);
      }
   }
}
