package net.exmo.sreGame.games.skyworld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SkyArena {
   public enum State {
      IDLE,
      IN_USE
   }

   public static final int STRIDE = 384;
   public static final int MAX_ISLANDS = 32;
   public static final int CENTER_RADIUS = 14;
   public static final int MID_RADIUS = 6;
   public static final int PLAYER_RADIUS = 8;
   public static final int TEAM_RADIUS = 11;
   public static final int ISLAND_Y_OFFSET = 40;
   private static final int EDGE_GAP = 10;

   /** SWR StandardCage offsets, plus corner pillars so players cannot squeeze out. */
   private static final int[][] CAGE = {
      {0, 0, 0},
      {0, 1, 1}, {0, 1, -1}, {1, 1, 0}, {-1, 1, 0},
      {-1, 1, 1}, {1, 1, 1}, {-1, 1, -1}, {1, 1, -1},
      {-1, 1, 0}, {0, 2, 1}, {0, 2, -1}, {1, 2, 0}, {-1, 2, 0}, {0, 3, 1},
      {-1, 2, 1}, {1, 2, 1}, {-1, 2, -1}, {1, 2, -1},
      {0, 3, -1}, {1, 3, 0}, {-1, 3, 0},
      {-1, 3, 1}, {1, 3, 1}, {-1, 3, -1}, {1, 3, -1},
      {0, 4, 0}
   };

   private final int slot;
   private final BlockPos origin;
   private State state = State.IDLE;
   private final Map<BlockPos, BlockState> voxelMap = new LinkedHashMap<>();
   private final List<Voxel> voxels = new ArrayList<>();
   private final List<BlockPos> islandChests = new ArrayList<>();
   private final List<BlockPos> midChests = new ArrayList<>();
   private final List<BlockPos> centerChests = new ArrayList<>();
   private final List<SpawnPad> spawns = new ArrayList<>();
   private int islandCount;

   public SkyArena(int slot, BlockPos origin) {
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

   public int centerX() {
      return this.origin.getX() + STRIDE / 2;
   }

   public int centerZ() {
      return this.origin.getZ() + STRIDE / 2;
   }

   public int islandY() {
      return this.origin.getY() + ISLAND_Y_OFFSET;
   }

   public int voidY() {
      return this.origin.getY() + 8;
   }

   public int fillMinY() {
      return this.origin.getY();
   }

   public int fillMaxY() {
      return this.islandY() + 24;
   }

   public List<Voxel> voxels() {
      return this.voxels;
   }

   public List<BlockPos> islandChests() {
      return this.islandChests;
   }

   public List<BlockPos> midChests() {
      return this.midChests;
   }

   public List<BlockPos> centerChests() {
      return this.centerChests;
   }

   public int islandCount() {
      return this.islandCount;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.origin.getX() && x < this.origin.getX() + STRIDE
         && z >= this.origin.getZ() && z < this.origin.getZ() + STRIDE
         && y >= this.origin.getY() - 8 && y <= this.fillMaxY() + 16;
   }

   public boolean inPlay(BlockPos pos) {
      return this.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
         && pos.getY() >= this.voidY();
   }

   public Vec3 watch() {
      return new Vec3(this.centerX() + 0.5, this.islandY() + 18, this.centerZ() + 0.5);
   }

   public Vec3 centerTop() {
      return new Vec3(this.centerX() + 0.5, this.islandY() + 1.0, this.centerZ() + 0.5);
   }

   public SpawnPad spawn(int index) {
      if (this.spawns.isEmpty()) {
         return new SpawnPad(this.centerTop(), 0.0F, 0);
      }
      return this.spawns.get(Math.floorMod(index, this.spawns.size()));
   }

   public void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
      player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0F);
   }

   public List<BlockPos> cageBlocks(int spawnIndex) {
      return this.cageBlocks(this.spawn(spawnIndex).pos());
   }

   public List<BlockPos> cageBlocks(Vec3 spawn) {
      BlockPos base = BlockPos.containing(spawn.x, spawn.y - 1.0, spawn.z);
      List<BlockPos> out = new ArrayList<>();
      for (int[] off : CAGE) {
         out.add(base.offset(off[0], off[1], off[2]));
      }
      return out;
   }

   public void generate(int islandCount, boolean teams) {
      this.islandCount = Math.max(1, Math.min(MAX_ISLANDS, islandCount));
      this.voxelMap.clear();
      this.voxels.clear();
      this.islandChests.clear();
      this.midChests.clear();
      this.centerChests.clear();
      this.spawns.clear();
      int y = this.islandY();
      int cx = this.centerX();
      int cz = this.centerZ();
      int starterR = teams ? TEAM_RADIUS : PLAYER_RADIUS;
      int outer = this.outerRing(starterR);
      int mids = this.midCount();
      int inner = this.innerRing(outer, starterR);
      this.buildCenter(cx, y, cz);
      for (int i = 0; i < mids; i++) {
         double angle = (Math.PI * 2.0 * (i + 0.5)) / mids - Math.PI / 2.0;
         int mx = cx + (int) Math.round(Math.cos(angle) * inner);
         int mz = cz + (int) Math.round(Math.sin(angle) * inner);
         this.buildMid(mx, y, mz, cx, cz, i * 23 + 7);
      }
      for (int i = 0; i < this.islandCount; i++) {
         double angle = (Math.PI * 2.0 * i) / this.islandCount - Math.PI / 2.0;
         int ix = cx + (int) Math.round(Math.cos(angle) * outer);
         int iz = cz + (int) Math.round(Math.sin(angle) * outer);
         this.buildStarter(ix, y, iz, starterR, cx, cz, teams, i * 17 + 31);
      }
      for (Map.Entry<BlockPos, BlockState> entry : this.voxelMap.entrySet()) {
         this.voxels.add(new Voxel(entry.getKey(), entry.getValue()));
      }
   }

   public void ensureChests(ServerLevel level) {
      if (level == null) {
         return;
      }
      for (BlockPos pos : this.islandChests) {
         this.ensureChestBlock(level, pos);
      }
      for (BlockPos pos : this.midChests) {
         this.ensureChestBlock(level, pos);
      }
      for (BlockPos pos : this.centerChests) {
         this.ensureChestBlock(level, pos);
      }
   }

   private void ensureChestBlock(ServerLevel level, BlockPos pos) {
      BlockState want = this.voxelMap.get(pos);
      if (want == null || !want.is(Blocks.CHEST)) {
         want = Blocks.CHEST.defaultBlockState();
      }
      if (!level.getBlockState(pos).is(Blocks.CHEST)) {
         BlockPos below = pos.below();
         if (level.getBlockState(below).isAir()) {
            level.setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
         level.setBlock(pos, want, 3);
      }
   }

   private int midCount() {
      int n = this.islandCount;
      if (n <= 3) {
         return 2;
      }
      if (n <= 6) {
         return 3;
      }
      if (n <= 8) {
         return 4;
      }
      if (n <= 12) {
         return 6;
      }
      if (n <= 16) {
         return 8;
      }
      if (n <= 24) {
         return 10;
      }
      return 12;
   }

   private int outerRing(int starterR) {
      double packed = this.islandCount * (starterR * 2 + EDGE_GAP) / (Math.PI * 2.0);
      int min = CENTER_RADIUS + MID_RADIUS + starterR + EDGE_GAP * 2 + 4;
      return Math.max(min, (int) Math.ceil(packed));
   }

   private int innerRing(int outer, int starterR) {
      int innerMin = CENTER_RADIUS + MID_RADIUS + EDGE_GAP;
      int innerMax = outer - starterR - MID_RADIUS - EDGE_GAP;
      if (innerMax <= innerMin) {
         return innerMin;
      }
      return (innerMin + innerMax) / 2;
   }

   private void buildCenter(int cx, int y, int cz) {
      this.sculpt(cx, y, cz, CENTER_RADIUS, 90, true);
      this.tree(cx, y + 1, cz, 5);
      Direction[] faces = {
         Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST,
         Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH
      };
      int[] ox = {0, 0, 8, -8, 5, -5, 5, -5};
      int[] oz = {8, -8, 0, 0, 5, 5, -5, -5};
      for (int i = 0; i < ox.length; i++) {
         this.addChest(cx + ox[i], y + 1, cz + oz[i], faces[i], Band.CENTER);
      }
      this.put(cx + 4, y + 1, cz + 2, Blocks.CRAFTING_TABLE.defaultBlockState());
      this.put(cx - 4, y + 1, cz - 2, Blocks.FURNACE.defaultBlockState());
      this.pile(cx + 6, y + 1, cz - 4, Blocks.OAK_LOG, 3);
      this.pile(cx - 6, y + 1, cz + 4, Blocks.COBBLESTONE, 4);
      this.basin(cx + 3, y, cz - 6, Blocks.WATER);
      this.basin(cx - 3, y, cz + 6, Blocks.LAVA);
      for (int i = 0; i < 12; i++) {
         int dx = ((i * 5) % 11) - 5;
         int dz = ((i * 7) % 11) - 5;
         if (dx * dx + dz * dz < 4) {
            continue;
         }
         this.put(cx + dx, y - 2, cz + dz, Blocks.IRON_ORE.defaultBlockState());
      }
   }

   private void buildMid(int mx, int y, int mz, int cx, int cz, int seed) {
      this.sculpt(mx, y, mz, MID_RADIUS, seed, false);
      Direction toCenter = facingCenter(mx, mz, cx, cz);
      Direction right = toCenter.getClockWise();
      this.addChest(mx + right.getStepX() * 3, y + 1, mz + right.getStepZ() * 3, right.getOpposite(), Band.MID);
      this.addChest(mx - right.getStepX() * 3, y + 1, mz - right.getStepZ() * 3, right, Band.MID);
      this.put(mx, y + 1, mz, Blocks.CRAFTING_TABLE.defaultBlockState());
      this.pile(mx + toCenter.getStepX() * 2, y + 1, mz + toCenter.getStepZ() * 2, Blocks.IRON_ORE, 2);
      this.pile(mx - toCenter.getStepX() * 3, y + 1, mz - toCenter.getStepZ() * 3, Blocks.OAK_LOG, 3);
      this.put(mx + right.getStepX() - toCenter.getStepX() * 2, y + 1,
         mz + right.getStepZ() - toCenter.getStepZ() * 2, Blocks.LANTERN.defaultBlockState());
      for (int i = 0; i < 5; i++) {
         int dx = Math.floorMod(hash(seed, i, 2), 5) - 2;
         int dz = Math.floorMod(hash(seed, 3, i), 5) - 2;
         if (dx == 0 && dz == 0) {
            continue;
         }
         this.put(mx + dx, y - 2, mz + dz, Blocks.IRON_ORE.defaultBlockState());
      }
   }

   private void buildStarter(int ix, int y, int iz, int radius, int cx, int cz, boolean teams, int seed) {
      this.sculpt(ix, y, iz, radius, seed, false);
      Direction toCenter = facingCenter(ix, iz, cx, cz);
      Direction right = toCenter.getClockWise();
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            this.put(ix + dx, y, iz + dz, Blocks.SMOOTH_STONE.defaultBlockState());
         }
      }
      int chestDist = teams ? 5 : 4;
      this.addChest(
         ix + right.getStepX() * chestDist, y + 1, iz + right.getStepZ() * chestDist,
         right.getOpposite(), Band.ISLAND
      );
      this.addChest(
         ix - right.getStepX() * chestDist, y + 1, iz - right.getStepZ() * chestDist,
         right, Band.ISLAND
      );
      if (teams) {
         this.addChest(
            ix + toCenter.getStepX() * 3, y + 1, iz + toCenter.getStepZ() * 3,
            toCenter.getOpposite(), Band.ISLAND
         );
      }
      int bx = ix - toCenter.getStepX() * 5;
      int bz = iz - toCenter.getStepZ() * 5;
      this.tree(bx, y + 1, bz, 4);
      this.put(ix + right.getStepX() * 2 - toCenter.getStepX() * 3, y + 1, iz + right.getStepZ() * 2 - toCenter.getStepZ() * 3,
         Blocks.CRAFTING_TABLE.defaultBlockState());
      this.pile(ix - right.getStepX() * 3 - toCenter.getStepX() * 2, y + 1,
         iz - right.getStepZ() * 3 - toCenter.getStepZ() * 2, Blocks.OAK_PLANKS, 3);
      this.pile(ix + toCenter.getStepX() * 2 + right.getStepX(), y + 1,
         iz + toCenter.getStepZ() * 2 + right.getStepZ(), Blocks.COBBLESTONE, 3);
      this.basin(ix - toCenter.getStepX() * 4 + right.getStepX() * 3, y,
         iz - toCenter.getStepZ() * 4 + right.getStepZ() * 3, Blocks.WATER);
      float yaw = yawToward(ix + 0.5, iz + 0.5, cx + 0.5, cz + 0.5);
      this.spawns.add(new SpawnPad(new Vec3(ix + 0.5, y + 1.0, iz + 0.5), yaw, this.spawns.size()));
   }

   private void sculpt(int cx, int y, int cz, int radius, int seed, boolean center) {
      int extra = 2;
      for (int dx = -radius - extra; dx <= radius + extra; dx++) {
         for (int dz = -radius - extra; dz <= radius + extra; dz++) {
            int jitter = Math.floorMod(hash(seed, dx, dz), 3) - 1;
            int r = radius + jitter;
            int dist2 = dx * dx + dz * dz;
            if (dist2 > r * r) {
               continue;
            }
            double t = Math.sqrt(dist2) / Math.max(1.0, r);
            int depth = t > 0.88 ? 2 : t > 0.62 ? 3 : 4;
            if (center) {
               depth++;
            }
            this.put(cx + dx, y, cz + dz, Blocks.GRASS_BLOCK.defaultBlockState());
            this.put(cx + dx, y - 1, cz + dz, Blocks.DIRT.defaultBlockState());
            for (int d = 2; d <= depth; d++) {
               this.put(cx + dx, y - d, cz + dz, this.stoneMix(seed, dx, dz, d, center));
            }
            if (t > 0.55 && t < 0.9 && Math.floorMod(hash(seed + 3, dx, dz), 7) == 0) {
               this.put(cx + dx, y + 1, cz + dz, Blocks.SHORT_GRASS.defaultBlockState());
            }
            if (center && t < 0.35 && Math.floorMod(hash(seed + 9, dx, dz), 11) == 0) {
               this.put(cx + dx, y - 3, cz + dz, Blocks.IRON_ORE.defaultBlockState());
            }
         }
      }
   }

   private BlockState stoneMix(int seed, int dx, int dz, int depth, boolean center) {
      int n = Math.floorMod(hash(seed + depth * 19, dx, dz), 8);
      if (center && n == 0) {
         return Blocks.IRON_ORE.defaultBlockState();
      }
      if (n <= 1) {
         return Blocks.COBBLESTONE.defaultBlockState();
      }
      if (n == 2) {
         return Blocks.ANDESITE.defaultBlockState();
      }
      if (n == 3) {
         return Blocks.GRAVEL.defaultBlockState();
      }
      return Blocks.STONE.defaultBlockState();
   }

   private void tree(int x, int y, int z, int height) {
      for (int i = 0; i < height; i++) {
         this.put(x, y + i, z, Blocks.OAK_LOG.defaultBlockState());
      }
      int leafBottom = Math.max(1, height - 3);
      BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().trySetValue(LeavesBlock.PERSISTENT, true);
      for (int dy = leafBottom; dy <= height + 1; dy++) {
         int spread = dy >= height ? 1 : 2;
         for (int dx = -spread; dx <= spread; dx++) {
            for (int dz = -spread; dz <= spread; dz++) {
               if (dx == 0 && dz == 0 && dy < height) {
                  continue;
               }
               if (Math.abs(dx) == spread && Math.abs(dz) == spread && dy != height) {
                  continue;
               }
               this.put(x + dx, y + dy, z + dz, leaves);
            }
         }
      }
   }

   private void pile(int x, int y, int z, net.minecraft.world.level.block.Block block, int count) {
      this.put(x, y, z, block.defaultBlockState());
      if (count >= 2) {
         this.put(x + 1, y, z, block.defaultBlockState());
      }
      if (count >= 3) {
         this.put(x, y + 1, z, block.defaultBlockState());
      }
      if (count >= 4) {
         this.put(x + 1, y + 1, z, block.defaultBlockState());
      }
   }

   private void basin(int x, int y, int z, net.minecraft.world.level.block.Block fluid) {
      this.put(x, y, z, Blocks.STONE.defaultBlockState());
      this.put(x + 1, y, z, Blocks.STONE.defaultBlockState());
      this.put(x - 1, y, z, Blocks.STONE.defaultBlockState());
      this.put(x, y, z + 1, Blocks.STONE.defaultBlockState());
      this.put(x, y, z - 1, Blocks.STONE.defaultBlockState());
      this.put(x, y + 1, z, fluid.defaultBlockState());
   }

   private void addChest(int x, int y, int z, Direction facing, Band band) {
      this.put(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
      BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
      BlockPos pos = new BlockPos(x, y, z);
      this.voxelMap.put(pos, state);
      switch (band) {
         case CENTER -> this.centerChests.add(pos);
         case MID -> this.midChests.add(pos);
         case ISLAND -> this.islandChests.add(pos);
      }
   }

   private void put(int x, int y, int z, BlockState state) {
      BlockPos pos = new BlockPos(x, y, z);
      BlockState existing = this.voxelMap.get(pos);
      if (existing != null && existing.is(Blocks.CHEST)) {
         return;
      }
      this.voxelMap.put(pos, state);
   }

   private static Direction facingCenter(int x, int z, int cx, int cz) {
      int dx = cx - x;
      int dz = cz - z;
      if (Math.abs(dx) > Math.abs(dz)) {
         return dx > 0 ? Direction.EAST : Direction.WEST;
      }
      return dz > 0 ? Direction.SOUTH : Direction.NORTH;
   }

   private static float yawToward(double x, double z, double tx, double tz) {
      double dx = tx - x;
      double dz = tz - z;
      return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
   }

   private static int hash(int seed, int x, int z) {
      int h = seed * 374761393 + x * 668265263 + z * 1274126177;
      h = (h ^ (h >> 13)) * 1274126177;
      return h ^ (h >> 16);
   }

   public record Voxel(BlockPos pos, BlockState state) {
   }

   public record SpawnPad(Vec3 pos, float yaw, int islandIndex) {
   }

   public enum Band {
      ISLAND,
      MID,
      CENTER
   }
}
