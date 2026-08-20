package net.exmo.sreGame.games.parkour;

import net.minecraft.core.BlockPos;

public final class ParkourDirector {
   private static final int SAFE = 5;
   private final int minX;
   private final int maxX;
   private final int minY;
   private final int maxY;
   private final int minZ;
   private final int maxZ;

   public ParkourDirector(BlockPos center, int border, int minY, int maxY) {
      int half = border / 2;
      this.minX = center.getX() - half;
      this.maxX = center.getX() + half;
      this.minZ = center.getZ() - half;
      this.maxZ = center.getZ() + half;
      this.minY = minY;
      this.maxY = maxY;
   }

   public int[] heading(BlockPos latest, int hx, int hz) {
      int x = latest.getX();
      int z = latest.getZ();
      if (x <= this.minX + SAFE) {
         return new int[] {1, 0};
      }
      if (x >= this.maxX - SAFE) {
         return new int[] {-1, 0};
      }
      if (z <= this.minZ + SAFE) {
         return new int[] {0, 1};
      }
      if (z >= this.maxZ - SAFE) {
         return new int[] {0, -1};
      }
      return new int[] {hx, hz};
   }

   public int height(BlockPos latest, int height) {
      if (latest.getY() <= this.minY + SAFE) {
         return 1;
      }
      if (latest.getY() >= this.maxY - SAFE) {
         return -1;
      }
      return height;
   }

   public boolean inZone(BlockPos pos) {
      return pos.getX() >= this.minX && pos.getX() <= this.maxX
         && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ
         && pos.getY() >= this.minY && pos.getY() <= this.maxY;
   }
}
