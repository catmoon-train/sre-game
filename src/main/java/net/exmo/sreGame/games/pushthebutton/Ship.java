package net.exmo.sreGame.games.pushthebutton;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class Ship {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int SIZE_X = 40;
   public static final int SIZE_Z = 32;
   public static final int HEIGHT = 8;
   public static final int CANVAS_COUNT = 4;
   public static final int CANVAS_W = 7;
   public static final int CANVAS_H = 5;
   public static final int BIO_STATIONS = 2;

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private boolean dirty;

   public Ship(int slot, BlockPos origin) {
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

   public int minX() {
      return this.origin.getX();
   }

   public int minY() {
      return this.origin.getY();
   }

   public int minZ() {
      return this.origin.getZ();
   }

   public int maxX() {
      return this.origin.getX() + SIZE_X - 1;
   }

   public int maxY() {
      return this.origin.getY() + HEIGHT - 1;
   }

   public int maxZ() {
      return this.origin.getZ() + SIZE_Z - 1;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.minX() && x < this.minX() + SIZE_X
         && z >= this.minZ() && z < this.minZ() + SIZE_Z
         && y >= this.minY() && y <= this.maxY() + 1.5;
   }

   public BlockPos buttonBase() {
      return new BlockPos(this.minX() + 20, this.minY() + 1, this.minZ() + 16);
   }

   public boolean isButton(BlockPos pos) {
      if (pos == null) {
         return false;
      }
      BlockPos base = this.buttonBase();
      return pos.equals(base) || pos.equals(base.above());
   }

   public Vec3 loungeSpawn() {
      return new Vec3(this.minX() + 20.5, this.minY() + 1.0, this.minZ() + 16.5);
   }

   public Vec3 captainStand() {
      return new Vec3(this.minX() + 34.5, this.minY() + 1.0, this.minZ() + 16.5);
   }

   public Vec3 airlockCell(int index) {
      int i = Math.max(0, Math.min(2, index));
      return new Vec3(this.minX() + 4.5, this.minY() + 1.0, this.minZ() + 10.5 + i * 5);
   }

   public Vec3 canvasStand(int index) {
      int i = Math.max(0, Math.min(CANVAS_COUNT - 1, index));
      int minLx = canvasMinLx(i);
      return new Vec3(this.minX() + minLx + CANVAS_W / 2.0, this.minY() + 1.0, this.minZ() + SIZE_Z - 4.5);
   }

   public Vec3 bioStand(int index) {
      int i = Math.max(0, Math.min(BIO_STATIONS - 1, index));
      int minLx = bioCopyMinLx(i);
      return new Vec3(this.minX() + minLx + 1.5, this.minY() + 1.0, this.minZ() + 4.5);
   }

   public static int canvasMinLx(int index) {
      return 10 + index * CANVAS_W;
   }

   public int canvasWallZ() {
      return this.minZ() + SIZE_Z - 2;
   }

   public boolean isCanvas(BlockPos pos, int index) {
      if (pos == null || pos.getZ() != this.canvasWallZ()) {
         return false;
      }
      int minLx = canvasMinLx(index);
      int x = pos.getX() - this.minX();
      int y = pos.getY() - this.minY();
      return x >= minLx && x < minLx + CANVAS_W && y >= 2 && y < 2 + CANVAS_H;
   }

   public boolean isAnyCanvas(BlockPos pos) {
      for (int i = 0; i < CANVAS_COUNT; i++) {
         if (this.isCanvas(pos, i)) {
            return true;
         }
      }
      return false;
   }

   public int canvasIndex(BlockPos pos) {
      for (int i = 0; i < CANVAS_COUNT; i++) {
         if (this.isCanvas(pos, i)) {
            return i;
         }
      }
      return -1;
   }

   public static int bioTemplateMinLx(int index) {
      return index == 0 ? 11 : 20;
   }

   public static int bioCopyMinLx(int index) {
      return index == 0 ? 15 : 24;
   }

   public int bioWallZ() {
      return this.minZ() + 2;
   }

   public boolean isBioCopy(BlockPos pos, int index) {
      return this.inBioPanel(pos, bioCopyMinLx(index));
   }

   public boolean isBioTemplate(BlockPos pos, int index) {
      return this.inBioPanel(pos, bioTemplateMinLx(index));
   }

   public boolean isAnyBioCopy(BlockPos pos) {
      return this.isBioCopy(pos, 0) || this.isBioCopy(pos, 1);
   }

   public int bioCopyIndex(BlockPos pos) {
      if (this.isBioCopy(pos, 0)) {
         return 0;
      }
      if (this.isBioCopy(pos, 1)) {
         return 1;
      }
      return -1;
   }

   private boolean inBioPanel(BlockPos pos, int minLx) {
      if (pos == null || pos.getZ() != this.bioWallZ()) {
         return false;
      }
      int x = pos.getX() - this.minX();
      int y = pos.getY() - this.minY();
      return x >= minLx && x < minLx + 3 && y >= 2 && y < 5;
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
      if (player == null || level == null || pos == null) {
         return;
      }
      player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0f);
      player.fallDistance = 0f;
   }

   public void resetCanvas(ServerLevel level, int index) {
      if (level == null) {
         return;
      }
      int minLx = canvasMinLx(index);
      int z = this.canvasWallZ();
      for (int x = 0; x < CANVAS_W; x++) {
         for (int y = 0; y < CANVAS_H; y++) {
            level.setBlock(new BlockPos(this.minX() + minLx + x, this.minY() + 2 + y, z),
               Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
         }
      }
   }

   public void paintBioTemplate(ServerLevel level, int index, net.minecraft.world.level.block.state.BlockState[][] pattern) {
      if (level == null || pattern == null) {
         return;
      }
      int minLx = bioTemplateMinLx(index);
      int z = this.bioWallZ();
      for (int x = 0; x < 3; x++) {
         for (int y = 0; y < 3; y++) {
            level.setBlock(new BlockPos(this.minX() + minLx + x, this.minY() + 2 + y, z), pattern[x][y], 3);
            level.setBlock(new BlockPos(this.minX() + bioCopyMinLx(index) + x, this.minY() + 2 + y, z),
               Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
         }
      }
   }

   public boolean bioMatches(ServerLevel level, int index, net.minecraft.world.level.block.state.BlockState[][] pattern) {
      if (level == null || pattern == null) {
         return false;
      }
      int minLx = bioCopyMinLx(index);
      int z = this.bioWallZ();
      for (int x = 0; x < 3; x++) {
         for (int y = 0; y < 3; y++) {
            if (!level.getBlockState(new BlockPos(this.minX() + minLx + x, this.minY() + 2 + y, z)).is(pattern[x][y].getBlock())) {
               return false;
            }
         }
      }
      return true;
   }
}
