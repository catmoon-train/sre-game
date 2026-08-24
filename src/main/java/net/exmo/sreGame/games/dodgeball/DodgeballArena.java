package net.exmo.sreGame.games.dodgeball;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DodgeballArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int FLOOR_X = 40;
   public static final int FLOOR_Z = 30;
   public static final int PAD = 4;
   public static final int SIZE_X = FLOOR_X + PAD * 2;
   public static final int SIZE_Z = FLOOR_Z + PAD * 2;
   public static final int STRIDE = SIZE_X + 32;
   public static final int WALL_HEIGHT = 4;
   private static final int[][] RED_COVERS = {{6, 7}, {6, 20}, {13, 7}, {13, 20}};
   private static final int[][] BLUE_COVERS = {{27, 7}, {27, 20}, {34, 7}, {34, 20}};
   private static final int[][] POWERUP_SPOTS = {
      {10, 11}, {10, 19}, {17, 15}, {23, 15}, {30, 11}, {30, 19}
   };

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;

   public DodgeballArena(int slot, BlockPos origin) {
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

   public int lavaY() {
      return this.origin.getY();
   }

   public int floorY() {
      return this.origin.getY() + 1;
   }

   public int wallTop() {
      return this.floorY() + WALL_HEIGHT;
   }

   public int lampY() {
      return this.wallTop() + 1;
   }

   public int specY() {
      return this.floorY() + 2;
   }

   public int fillMaxY() {
      return this.lampY() + 1;
   }

   public int floorMinX() {
      return this.origin.getX() + PAD;
   }

   public int floorMinZ() {
      return this.origin.getZ() + PAD;
   }

   public int floorMaxX() {
      return this.floorMinX() + FLOOR_X - 1;
   }

   public int floorMaxZ() {
      return this.floorMinZ() + FLOOR_Z - 1;
   }

   public int midX() {
      return this.floorMinX() + 20;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX() && x < this.origin.getX() + SIZE_X
         && z >= this.origin.getZ() && z < this.origin.getZ() + SIZE_Z
         && y >= this.lavaY() - 2 && y <= this.fillMaxY() + 8;
   }

   public boolean inPlay(double x, double z) {
      return x >= this.floorMinX() && x < this.floorMaxX() + 1
         && z >= this.floorMinZ() && z < this.floorMaxZ() + 1;
   }

   public boolean onOwnSide(DodgeballTeam team, double x) {
      return team == DodgeballTeam.RED ? x < this.midX() : x > this.midX() + 1.0;
   }

   public double clampX(DodgeballTeam team, double x) {
      if (team == DodgeballTeam.RED) {
         return Math.min(x, this.midX() - 0.31);
      }
      return Math.max(x, this.midX() + 1.31);
   }

   public Vec3 ballPad(DodgeballTeam team) {
      double x = team == DodgeballTeam.RED ? this.midX() - 4.5 : this.midX() + 5.5;
      return new Vec3(x, this.floorY() + 1.1, this.floorMinZ() + 15.5);
   }

   public boolean inLava(double x, double y, double z) {
      if (y > this.floorY() + 0.2) {
         return false;
      }
      return this.contains(x, y, z) && !this.inPlay(x, z);
   }

   public Vec3 spawn(DodgeballTeam team, int index, int teamSize) {
      int spread = Math.max(0, teamSize - 1);
      double z = this.floorMinZ() + 15.0 + (index - spread / 2.0) * 2.0;
      double x = team == DodgeballTeam.RED ? this.floorMinX() + 4.5 : this.floorMaxX() - 3.5;
      return new Vec3(x, this.floorY() + 1.0, z);
   }

   public float spawnYaw(DodgeballTeam team) {
      return team == DodgeballTeam.RED ? -90.0F : 90.0F;
   }

   public Vec3 watch() {
      return new Vec3(this.midX() + 0.5, this.floorY() + 8.0, this.floorMinZ() + FLOOR_Z / 2.0);
   }

   public Vec3 center() {
      return new Vec3(this.midX() + 0.5, this.floorY() + 1.5, this.floorMinZ() + FLOOR_Z / 2.0);
   }

   public List<Vec3> powerupSpots() {
      List<Vec3> out = new ArrayList<>();
      for (int[] spot : POWERUP_SPOTS) {
         out.add(new Vec3(
            this.floorMinX() + spot[0] + 0.5,
            this.floorY() + 1.2,
            this.floorMinZ() + spot[1] + 0.5
         ));
      }
      return out;
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
      player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0F);
   }

   public BlockState structureWant(int x, int y, int z) {
      int lx = x - this.origin.getX();
      int ly = y - this.origin.getY();
      int lz = z - this.origin.getZ();
      if (lx < 0 || lz < 0 || lx >= SIZE_X || lz >= SIZE_Z || ly < 0 || y > this.fillMaxY()) {
         return Blocks.AIR.defaultBlockState();
      }
      int fx = lx - PAD;
      int fz = lz - PAD;
      boolean inFloor = fx >= 0 && fx < FLOOR_X && fz >= 0 && fz < FLOOR_Z;
      boolean inMoat = !inFloor && fx >= -1 && fx <= FLOOR_X && fz >= -1 && fz <= FLOOR_Z;
      boolean inSpec = !inFloor && !inMoat;

      if (ly == 0) {
         if (inMoat) {
            return Blocks.LAVA.defaultBlockState();
         }
         if (inFloor) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
         }
         return Blocks.STONE.defaultBlockState();
      }
      if (inFloor && this.isCover(fx, fz) && ly >= 2 && ly <= 3) {
         return fx < 20
            ? Blocks.RED_CONCRETE.defaultBlockState()
            : Blocks.BLUE_CONCRETE.defaultBlockState();
      }
      if (inFloor && this.isPerimeter(fx, fz) && ly >= 2 && ly <= WALL_HEIGHT + 1) {
         if (y == this.lampY()) {
            return Blocks.REDSTONE_LAMP.defaultBlockState();
         }
         return Blocks.GLASS.defaultBlockState();
      }
      if (inFloor && ly == 1) {
         if (fx == 15 && fz == 15) {
            return Blocks.RED_CONCRETE.defaultBlockState();
         }
         if (fx == 25 && fz == 15) {
            return Blocks.BLUE_CONCRETE.defaultBlockState();
         }
         if (fx == 20) {
            return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
         }
         return fx < 20
            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
            : Blocks.SMOOTH_QUARTZ.defaultBlockState();
      }
      if (inSpec && y == this.specY()) {
         return Blocks.SMOOTH_STONE.defaultBlockState();
      }
      if (inSpec && this.isSpecRail(lx, lz) && y > this.specY() && y <= this.specY() + 2) {
         return Blocks.GLASS.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private boolean isPerimeter(int fx, int fz) {
      return fx == 0 || fx == FLOOR_X - 1 || fz == 0 || fz == FLOOR_Z - 1;
   }

   private boolean isCover(int fx, int fz) {
      return this.matchesCover(RED_COVERS, fx, fz) || this.matchesCover(BLUE_COVERS, fx, fz);
   }

   private boolean matchesCover(int[][] covers, int fx, int fz) {
      for (int[] cover : covers) {
         if (fx == cover[0] && fz == cover[1]) {
            return true;
         }
      }
      return false;
   }

   private boolean isSpecRail(int lx, int lz) {
      int innerMin = PAD - 1;
      int innerMaxX = PAD + FLOOR_X;
      int innerMaxZ = PAD + FLOOR_Z;
      boolean innerRing = lx == innerMin || lx == innerMaxX || lz == innerMin || lz == innerMaxZ;
      boolean inRingBox = lx >= innerMin && lx <= innerMaxX && lz >= innerMin && lz <= innerMaxZ;
      return innerRing && inRingBox;
   }
}
