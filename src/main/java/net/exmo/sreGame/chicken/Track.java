package net.exmo.sreGame.chicken;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Track {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int HEIGHT = 18;
   public static final int PIT = 12;
   public static final int SPAWN_LEN = TrackLayout.SPAWN_LEN;
   public static final float FACE_PLUS_X = -90.0F;

   private static final int[] BASE_CHECKPOINTS = {40, 84, 128};
   private static final int[][] BASE_GAPS = {
      {18, 19}, {28, 30}, {35, 36}, {44, 45}, {54, 56}, {62, 64}, {69, 70},
      {80, 82}, {88, 90}, {98, 99}, {106, 108}, {122, 124}, {132, 134}, {140, 141}, {156, 157}
   };
   private static final int[] BASE_SLIME = {32, 33, 48, 49, 76, 77, 103, 114, 115, 136, 137};
   private static final int[] BASE_HONEY = {38, 39, 66, 67, 94, 95, 143, 144, 151, 152};

   private final int slot;
   private final BlockPos origin;
   private TrackLayout layout = TrackLayout.defaults();
   private State state = State.IDLE;
   private boolean dirty;

   public Track(int slot, BlockPos origin) {
      this.slot = slot;
      this.origin = origin;
   }

   public int slot() {
      return this.slot;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public TrackLayout layout() {
      return this.layout;
   }

   public void setLayout(TrackLayout layout) {
      this.layout = layout == null ? TrackLayout.defaults() : layout;
   }

   public int sizeX() {
      return this.layout.sizeX();
   }

   public int sizeZ() {
      return this.layout.sizeZ();
   }

   public int laneWidth() {
      return this.layout.laneWidth();
   }

   public int laneZ() {
      return this.layout.laneZ();
   }

   public int finishMin() {
      return this.layout.finishMin();
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
      return x >= ox && x < ox + this.sizeX()
         && z >= oz && z < oz + this.sizeZ()
         && y >= oy - PIT - 1.5 && y <= oy + HEIGHT + 1.5;
   }

   public boolean inFinish(double x, double y, double z) {
      int lx = (int) Math.floor(x) - this.origin.getX();
      int ly = (int) Math.floor(y) - this.origin.getY();
      int lz = (int) Math.floor(z) - this.origin.getZ();
      return lx >= this.finishMin() && lx < this.sizeX() && ly >= 1 && ly < HEIGHT - 1 && this.onLane(lz);
   }

   public boolean onDeathFloor(double x, double y, double z) {
      return y < this.origin.getY() - PIT + 1.15;
   }

   public boolean canPlace(BlockPos pos) {
      int lx = pos.getX() - this.origin.getX();
      int ly = pos.getY() - this.origin.getY();
      int lz = pos.getZ() - this.origin.getZ();
      return lx >= SPAWN_LEN + 4 && lx <= this.finishMin() - 5 && ly >= 1 && ly <= HEIGHT - 2 && this.innerZ(lz);
   }

   public boolean isTemplate(BlockPos pos) {
      int lx = pos.getX() - this.origin.getX();
      int ly = pos.getY() - this.origin.getY();
      int lz = pos.getZ() - this.origin.getZ();
      BlockState want = this.palette(lx, ly, lz);
      return !want.isAir();
   }

   public Vec3 spawn(int index, int total) {
      int n = Math.max(1, total);
      int width = this.laneWidth();
      int lane = Math.floorMod(index, width);
      int alongCount = (n + width - 1) / width;
      int alongIndex = index / width;
      double along = alongCount <= 1 ? 4.5 : 2.6 + alongIndex * (4.4 / Math.max(1, alongCount - 1));
      return this.abs(along, 2.0, this.laneZ() + lane + 0.5);
   }

   public Vec3 checkpointSpawn(int index) {
      int[] checkpoints = this.checkpoints();
      int safe = Math.max(0, Math.min(checkpoints.length, index));
      int lx = safe == 0 ? 4 : checkpoints[safe - 1];
      return this.abs(lx + 0.5, 2.0, this.laneZ() + this.laneWidth() / 2.0);
   }

   public int checkpointAt(double x, double z) {
      int lx = (int) Math.floor(x) - this.origin.getX();
      int lz = (int) Math.floor(z) - this.origin.getZ();
      if (!this.onLane(lz)) {
         return 0;
      }
      int[] checkpoints = this.checkpoints();
      for (int i = checkpoints.length - 1; i >= 0; i--) {
         if (Math.abs(lx - checkpoints[i]) <= 2) {
            return i + 1;
         }
      }
      return 0;
   }

   public Vec3 watch() {
      return this.abs(this.sizeX() / 2.0, 12.0, this.sizeZ() / 2.0);
   }

   public List<BlockPos> eggSpots() {
      int left = Math.max(1, this.laneZ() - 2);
      int right = Math.min(this.sizeZ() - 2, this.laneZ() + this.laneWidth() + 1);
      return List.of(
         this.block(this.layout.mapX(50), 2, left),
         this.block(this.layout.mapX(88), 2, right),
         this.block(this.layout.mapX(125), 2, left)
      );
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
      player.teleportTo(level, pos.x, pos.y, pos.z, FACE_PLUS_X, 8.0F);
   }

   public void setSpawnGate(ServerLevel level, boolean closed) {
      for (int lz = this.laneZ(); lz < this.laneZ() + this.laneWidth(); lz++) {
         for (int ly = 2; ly <= 3; ly++) {
            BlockPos pos = this.origin.offset(SPAWN_LEN - 1, ly, lz);
            BlockState want = closed ? this.palette(SPAWN_LEN - 1, ly, lz) : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, want, 2);
         }
      }
   }

   public BlockState palette(int lx, int ly, int lz) {
      int sizeX = this.sizeX();
      int sizeZ = this.sizeZ();
      if (ly < -PIT || ly >= HEIGHT || lx < 0 || lx >= sizeX || lz < 0 || lz >= sizeZ) {
         return Blocks.AIR.defaultBlockState();
      }
      if (ly < 0) {
         if (ly == -PIT) {
            return Blocks.BLACK_CONCRETE.defaultBlockState();
         }
         if (lx == 0 || lx == sizeX - 1 || lz == 0 || lz == sizeZ - 1) {
            return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
         }
         return Blocks.AIR.defaultBlockState();
      }
      if (ly == HEIGHT - 1) {
         return Blocks.BARRIER.defaultBlockState();
      }
      if (lx == 0) {
         return Blocks.YELLOW_CONCRETE.defaultBlockState();
      }
      if (lx == sizeX - 1) {
         return Blocks.GOLD_BLOCK.defaultBlockState();
      }
      if (lz == 0 || lz == sizeZ - 1) {
         if (ly == HEIGHT - 2 && lx % 8 == 4) {
            return Blocks.GLOWSTONE.defaultBlockState();
         }
         return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
      }
      if (ly == HEIGHT - 2 && lx % 8 == 4) {
         return Blocks.LANTERN.defaultBlockState();
      }
      if (lx < SPAWN_LEN && ly == 1 && this.onLane(lz)) {
         return Blocks.SMOOTH_STONE.defaultBlockState();
      }
      if (lx == SPAWN_LEN - 1 && ly >= 2 && ly <= 3 && this.onLane(lz)) {
         return Blocks.IRON_BARS.defaultBlockState();
      }
      if (lx >= this.finishMin() && ly == 1 && this.onLane(lz)) {
         return Blocks.GOLD_BLOCK.defaultBlockState();
      }
      if (lx == this.finishMin() && ly >= 2 && ly <= 3 && this.onLane(lz)) {
         return Blocks.IRON_BARS.defaultBlockState();
      }
      if (this.isEggPedestal(lx, lz) && ly == 1) {
         return Blocks.SMOOTH_STONE.defaultBlockState();
      }
      int railA = this.layout.mapX(73);
      int railB = this.layout.mapX(79);
      if (lx >= Math.min(railA, railB) && lx <= Math.max(railA, railB)
         && (lz == this.laneZ() - 1 || lz == this.laneZ() + this.laneWidth()) && ly >= 1 && ly <= 7) {
         return Blocks.IRON_BARS.defaultBlockState();
      }
      if ((lx == this.layout.mapX(119) || lx == this.layout.mapX(154)) && this.onLane(lz) && ly >= 1 && ly <= 5) {
         return Blocks.STONE.defaultBlockState();
      }
      int py = this.courseY(lx);
      if (py > 0 && ly == py && this.onLane(lz)) {
         if (this.isCheckpointX(lx)) {
            return Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
         }
         if (this.isSlimeX(lx)) {
            return Blocks.SLIME_BLOCK.defaultBlockState();
         }
         if (this.isHoneyX(lx)) {
            return Blocks.HONEY_BLOCK.defaultBlockState();
         }
         return Blocks.SMOOTH_STONE.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private boolean innerZ(int lz) {
      return lz >= 1 && lz <= this.sizeZ() - 2;
   }

   private boolean onLane(int lz) {
      return lz >= this.laneZ() && lz < this.laneZ() + this.laneWidth();
   }

   private boolean isEggPedestal(int lx, int lz) {
      int left = Math.max(1, this.laneZ() - 2);
      int right = Math.min(this.sizeZ() - 2, this.laneZ() + this.laneWidth() + 1);
      return lx == this.layout.mapX(50) && lz == left
         || lx == this.layout.mapX(88) && lz == right
         || lx == this.layout.mapX(125) && lz == left;
   }

   private boolean isSlimeX(int lx) {
      for (int base : BASE_SLIME) {
         if (lx == this.layout.mapX(base)) {
            return true;
         }
      }
      return false;
   }

   private boolean isHoneyX(int lx) {
      for (int base : BASE_HONEY) {
         if (lx == this.layout.mapX(base)) {
            return true;
         }
      }
      return false;
   }

   private int[] checkpoints() {
      int[] out = new int[BASE_CHECKPOINTS.length];
      for (int i = 0; i < BASE_CHECKPOINTS.length; i++) {
         out[i] = this.layout.mapX(BASE_CHECKPOINTS[i]);
      }
      return out;
   }

   private int courseY(int lx) {
      if (lx < SPAWN_LEN || lx >= this.finishMin()) {
         return 0;
      }
      if (this.isGap(lx)) {
         return 0;
      }
      if (this.inMapped(lx, 22, 26)) {
         return 2;
      }
      if (this.inMapped(lx, 48, 52)) {
         return 3;
      }
      if (this.inMapped(lx, 92, 96)) {
         return 2;
      }
      if (this.inMapped(lx, 112, 116)) {
         return 4;
      }
      if (this.inMapped(lx, 148, 152)) {
         return 2;
      }
      return 1;
   }

   private boolean isGap(int lx) {
      for (int[] range : BASE_GAPS) {
         if (this.inMapped(lx, range[0], range[1])) {
            return true;
         }
      }
      return false;
   }

   private boolean isCheckpointX(int lx) {
      for (int checkpoint : this.checkpoints()) {
         if (lx == checkpoint) {
            return true;
         }
      }
      return false;
   }

   private boolean inMapped(int lx, int from, int to) {
      int a = this.layout.mapX(from);
      int b = this.layout.mapX(to);
      return lx >= Math.min(a, b) && lx <= Math.max(a, b);
   }

   private Vec3 abs(double x, double y, double z) {
      return new Vec3(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
   }

   private BlockPos block(int lx, int ly, int lz) {
      return this.origin.offset(lx, ly, lz);
   }
}
