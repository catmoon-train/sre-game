package net.exmo.sreGame.games.football;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A compact stadium generated entirely from block states, so every match gets a clean pitch. */
public final class FootballArena {
   public enum State { IDLE, IN_USE }

   /** 12v12 needs room for wide passes, recoveries and aerial contests. */
   public static final int FIELD_X = 100;
   public static final int FIELD_Z = 62;
   public static final int PAD = 10;
   public static final int SIZE_X = FIELD_X + PAD * 2;
   public static final int SIZE_Z = FIELD_Z + PAD * 2;
   public static final int STRIDE = SIZE_X + 36;
   public static final int GOAL_HALF_WIDTH = 9;
   public static final int GOAL_HEIGHT = 7;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;

   public FootballArena(int slot, BlockPos origin) { this.slot = slot; this.origin = origin; }
   public int slot() { return this.slot; }
   public BlockPos origin() { return this.origin; }
   public State state() { return this.state; }
   public void setState(State state) { this.state = state; }
   public int baseY() { return this.origin.getY(); }
   public int floorY() { return this.baseY() + 1; }
   public int minX() { return this.origin.getX() + PAD; }
   public int maxX() { return this.minX() + FIELD_X - 1; }
   public int minZ() { return this.origin.getZ() + PAD; }
   public int maxZ() { return this.minZ() + FIELD_Z - 1; }
   public double centerX() { return this.minX() + FIELD_X / 2.0; }
   public double centerZ() { return this.minZ() + FIELD_Z / 2.0; }
   public int fillMaxY() { return this.floorY() + 10; }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX() && x < this.origin.getX() + SIZE_X
         && z >= this.origin.getZ() && z < this.origin.getZ() + SIZE_Z
         && y >= this.baseY() - 2 && y <= this.fillMaxY() + 5;
   }

   public boolean inPitch(double x, double z) {
      return x >= this.minX() && x <= this.maxX() + 1.0 && z >= this.minZ() && z <= this.maxZ() + 1.0;
   }

   public boolean inGoalMouth(double z, double y) {
      return Math.abs(z - this.centerZ()) <= GOAL_HALF_WIDTH && y <= this.floorY() + GOAL_HEIGHT;
   }

   public Vec3 kickoff() { return new Vec3(this.centerX(), this.floorY() + 1.15, this.centerZ()); }

   public Vec3 spawn(FootballTeam team, int index, int size) {
      int column = index / 3;
      int lane = index % 3;
      double z = this.centerZ() + (lane - 1) * 8.5;
      double x = team == FootballTeam.RED ? this.minX() + 13.5 + column * 7.0 : this.maxX() - 12.5 - column * 7.0;
      return new Vec3(x, this.floorY() + 1.0, z);
   }

   public float spawnYaw(FootballTeam team) { return team == FootballTeam.RED ? -90.0F : 90.0F; }

   public void teleport(ServerLevel level, net.minecraft.server.level.ServerPlayer player, Vec3 pos, float yaw) {
      player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0F);
   }

   public BlockState structureWant(int x, int y, int z) {
      int lx = x - this.origin.getX(), lz = z - this.origin.getZ(), ly = y - this.baseY();
      if (lx < 0 || lz < 0 || lx >= SIZE_X || lz >= SIZE_Z || ly < 0 || y > this.fillMaxY()) return Blocks.AIR.defaultBlockState();
      int fx = lx - PAD, fz = lz - PAD;
      boolean pitch = fx >= 0 && fx < FIELD_X && fz >= 0 && fz < FIELD_Z;
      boolean ring = fx >= -2 && fx <= FIELD_X + 1 && fz >= -2 && fz <= FIELD_Z + 1;
      if (ly == 0) return pitch ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
      if (ly == 1 && pitch) {
         if (fx == 0 || fx == FIELD_X - 1 || fz == 0 || fz == FIELD_Z - 1 || fx == FIELD_X / 2 - 1) return Blocks.WHITE_CONCRETE.defaultBlockState();
         int dz = Math.abs(fz - FIELD_Z / 2);
         int dx = Math.abs(fx - FIELD_X / 2);
         if ((dx == 11 && dz <= 11) || (dz == 11 && dx <= 11)) return Blocks.WHITE_CONCRETE.defaultBlockState();
         if ((fx == 16 || fx == FIELD_X - 17) && Math.abs(fz - FIELD_Z / 2) <= 14) return Blocks.WHITE_CONCRETE.defaultBlockState();
         if ((fx == 7 || fx == FIELD_X - 8) && Math.abs(fz - FIELD_Z / 2) <= 22) return Blocks.WHITE_CONCRETE.defaultBlockState();
         return (fx / 8) % 2 == 0 ? Blocks.GREEN_CONCRETE.defaultBlockState() : Blocks.LIME_CONCRETE.defaultBlockState();
      }
      if (!pitch && ring && ly == 1) return Blocks.SMOOTH_STONE.defaultBlockState();
      if (this.isGoal(fx, fz, ly)) return Blocks.IRON_BARS.defaultBlockState();
      if (ring && (fx == -2 || fx == FIELD_X + 1 || fz == -2 || fz == FIELD_Z + 1) && ly >= 2 && ly <= 5) return Blocks.GLASS_PANE.defaultBlockState();
      if (!pitch && ly == 2 && (lz < PAD - 2 || lz > PAD + FIELD_Z + 1)) return Blocks.OAK_SLAB.defaultBlockState();
      if (!pitch && ly >= 3 && ly <= 5 && (lz < PAD - 2 || lz > PAD + FIELD_Z + 1)) return (lx / 3) % 2 == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.BLUE_WOOL.defaultBlockState();
      return Blocks.AIR.defaultBlockState();
   }

   private boolean isGoal(int fx, int fz, int ly) {
      if (Math.abs(fz - FIELD_Z / 2) > GOAL_HALF_WIDTH || ly < 2 || ly > GOAL_HEIGHT + 1) return false;
      boolean left = fx >= -3 && fx <= -1;
      boolean right = fx >= FIELD_X && fx <= FIELD_X + 2;
      if (!left && !right) return false;
      boolean post = Math.abs(fz - FIELD_Z / 2) == GOAL_HALF_WIDTH || ly == GOAL_HEIGHT + 1;
      boolean back = fx == (left ? -3 : FIELD_X + 2);
      return post || back;
   }
}
