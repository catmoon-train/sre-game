package net.exmo.sreGame.dontdo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class IslandGenerator {
   public static final int PLAY = 256;
   public static final int WALL = 3;
   public static final int BARRIER = 1;
   public static final int TOTAL = PLAY + WALL * 2 + BARRIER * 2;
   public static final int MIN_Y = -64;
   public static final int MAX_Y = 80;
   public static final int SURFACE = 0;

   private IslandGenerator() {
   }

   public static int[] heightmap(long seed) {
      int[] map = new int[PLAY * PLAY];
      Noise noise = new Noise(seed);
      for (int z = 0; z < PLAY; z++) {
         for (int x = 0; x < PLAY; x++) {
            double n = noise.fbm(x / 42.0, z / 42.0, 4) * 6.5
               + noise.fbm(x / 12.0, z / 12.0, 2) * 1.8;
            map[z * PLAY + x] = SURFACE + (int) Math.round(n);
         }
      }
      return map;
   }

   public static BlockState blockAt(Island island, int wx, int wy, int wz) {
      int lx = wx - island.origin().getX();
      int lz = wz - island.origin().getZ();
      if (lx < 0 || lz < 0 || lx >= TOTAL || lz >= TOTAL || wy < MIN_Y || wy > MAX_Y) {
         return Blocks.AIR.defaultBlockState();
      }
      if (lx < BARRIER || lz < BARRIER || lx >= TOTAL - BARRIER || lz >= TOTAL - BARRIER) {
         return Blocks.BARRIER.defaultBlockState();
      }
      if (lx < BARRIER + WALL || lz < BARRIER + WALL
         || lx >= TOTAL - BARRIER - WALL || lz >= TOTAL - BARRIER - WALL) {
         return Blocks.BEDROCK.defaultBlockState();
      }
      int px = lx - BARRIER - WALL;
      int pz = lz - BARRIER - WALL;
      int height = island.heightAt(px, pz);
      if (wy == MIN_Y) {
         return Blocks.BEDROCK.defaultBlockState();
      }
      if (wy > height) {
         if (height <= -2 && wy <= -1) {
            return Blocks.WATER.defaultBlockState();
         }
         return Blocks.AIR.defaultBlockState();
      }
      Noise noise = new Noise(island.seed());
      if (wy < height - 3 && wy > MIN_Y + 2 && cave(noise, wx, wy, wz)) {
         return Blocks.AIR.defaultBlockState();
      }
      BlockState ore = oreAt(noise, wx, wy, wz, height);
      if (ore != null) {
         return ore;
      }
      if (wy == height) {
         return height <= -2 ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
      }
      if (wy >= height - 3) {
         return height <= -2 ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
      }
      return wy < -8 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
   }

   public static int placeTree(ServerLevel level, Island island, int px, int pz) {
      int height = island.heightAt(px, pz);
      if (height <= -1) {
         return 0;
      }
      int wx = island.playMinX() + px;
      int wz = island.playMinZ() + pz;
      BlockPos ground = new BlockPos(wx, height, wz);
      if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
         return 0;
      }
      int trunk = 4 + (int) ((island.seed() >> (px + pz & 7)) & 1);
      int placed = 0;
      for (int i = 1; i <= trunk; i++) {
         level.setBlock(new BlockPos(wx, height + i, wz), Blocks.OAK_LOG.defaultBlockState(), 2);
         placed++;
      }
      int top = height + trunk;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
               if (dy >= 1 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
                  continue;
               }
               if (dx == 0 && dz == 0 && dy <= 0) {
                  continue;
               }
               BlockPos leaf = new BlockPos(wx + dx, top + dy, wz + dz);
               if (level.getBlockState(leaf).isAir()) {
                  level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 2);
                  placed++;
               }
            }
         }
      }
      return placed;
   }

   private static boolean cave(Noise noise, int x, int y, int z) {
      double n = noise.noise3(x / 14.0, y / 9.0, z / 14.0);
      double n2 = noise.noise3((x + 40) / 22.0, y / 16.0, (z - 17) / 22.0);
      return n > 0.52 && n2 > 0.12;
   }

   private static BlockState oreAt(Noise noise, int x, int y, int z, int height) {
      double v = (noise.hash3(x, y, z) + 1.0) * 0.5;
      boolean deep = y < -8;
      if (y <= -20 && v < 0.0012 && noise.noise3(x / 5.0, y / 3.0, z / 5.0) > 0.85) {
         return deep ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DIAMOND_ORE.defaultBlockState();
      }
      if (y <= -12 && v < 0.028) {
         return deep ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState();
      }
      if (y <= -8 && v < 0.036) {
         return deep ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState();
      }
      if (y <= 4 && v < 0.048) {
         return deep ? Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState() : Blocks.LAPIS_ORE.defaultBlockState();
      }
      if (y <= 8 && v < 0.07) {
         return deep ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
      }
      if (y <= 10 && v < 0.09) {
         return deep ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
      }
      if (y <= height - 2 && v < 0.13) {
         return deep ? Blocks.DEEPSLATE_COAL_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
      }
      return null;
   }

   static final class Noise {
      private final int seed;

      Noise(long seed) {
         this.seed = (int) seed;
      }

      double fbm(double x, double z, int octaves) {
         double sum = 0.0;
         double amp = 1.0;
         double freq = 1.0;
         double norm = 0.0;
         for (int i = 0; i < octaves; i++) {
            sum += amp * noise2(x * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.0;
         }
         return sum / Math.max(0.0001, norm);
      }

      double noise2(double x, double z) {
         int x0 = (int) Math.floor(x);
         int z0 = (int) Math.floor(z);
         double fx = x - x0;
         double fz = z - z0;
         double u = fade(fx);
         double v = fade(fz);
         double a = lerp(hash2(x0, z0), hash2(x0 + 1, z0), u);
         double b = lerp(hash2(x0, z0 + 1), hash2(x0 + 1, z0 + 1), u);
         return lerp(a, b, v);
      }

      double noise3(double x, double y, double z) {
         int x0 = (int) Math.floor(x);
         int y0 = (int) Math.floor(y);
         int z0 = (int) Math.floor(z);
         double fx = fade(x - x0);
         double fy = fade(y - y0);
         double fz = fade(z - z0);
         double n000 = hash3(x0, y0, z0);
         double n100 = hash3(x0 + 1, y0, z0);
         double n010 = hash3(x0, y0 + 1, z0);
         double n110 = hash3(x0 + 1, y0 + 1, z0);
         double n001 = hash3(x0, y0, z0 + 1);
         double n101 = hash3(x0 + 1, y0, z0 + 1);
         double n011 = hash3(x0, y0 + 1, z0 + 1);
         double n111 = hash3(x0 + 1, y0 + 1, z0 + 1);
         double nx00 = lerp(n000, n100, fx);
         double nx10 = lerp(n010, n110, fx);
         double nx01 = lerp(n001, n101, fx);
         double nx11 = lerp(n011, n111, fx);
         double nxy0 = lerp(nx00, nx10, fy);
         double nxy1 = lerp(nx01, nx11, fy);
         return lerp(nxy0, nxy1, fz);
      }

      double hash2(int x, int z) {
         long n = x * 374761393L + z * 668265263L + this.seed * 1274126177L;
         n = (n ^ (n >> 13)) * 1274126177L;
         return ((n & 0xffffffffL) / (double) 0xffffffffL) * 2.0 - 1.0;
      }

      double hash3(int x, int y, int z) {
         long n = x * 374761393L + y * 668265263L + z * 1274126177L + this.seed * 2246822519L;
         n = (n ^ (n >> 13)) * 1274126177L;
         return ((n & 0xffffffffL) / (double) 0xffffffffL) * 2.0 - 1.0;
      }

      private static double fade(double t) {
         return t * t * (3.0 - 2.0 * t);
      }

      private static double lerp(double a, double b, double t) {
         return a + (b - a) * t;
      }
   }
}
