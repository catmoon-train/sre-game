package net.exmo.sreGame.games.blockedcombat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A reusable, self-contained mining arena. Generation is deterministic per round seed. */
public final class BlockedCombatArena {
   public static final int STRIDE = 112;
   public static final int HEIGHT = 32;
   private static final List<Block> BLOCKS = List.of(
      Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE, Blocks.DIRT, Blocks.GRAVEL, Blocks.SAND,
      Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.GLASS, Blocks.COBWEB, Blocks.SOUL_SAND, Blocks.NETHERRACK,
      Blocks.REDSTONE_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
      Blocks.LAPIS_ORE, Blocks.EMERALD_ORE, Blocks.OBSIDIAN, Blocks.CRAFTING_TABLE, Blocks.FURNACE,
      Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.BOOKSHELF,
      Blocks.ANVIL, Blocks.CAULDRON, Blocks.NOTE_BLOCK, Blocks.SNOW_BLOCK
   );

   public enum State { IDLE, IN_USE }

   private final int index;
   private final BlockPos origin;
   private State state = State.IDLE;
   private int size = 60;
   private int tntScarcity = 40;
   private long seed;

   public BlockedCombatArena(int index, BlockPos origin) {
      this.index = index;
      this.origin = origin;
   }

   public int index() { return this.index; }
   public BlockPos origin() { return this.origin; }
   public State state() { return this.state; }
   public void setState(State state) { this.state = state; }
   public int size() { return this.size; }
   public int floorY() { return this.origin.getY(); }
   public int topY() { return this.floorY() + HEIGHT; }
   public int minX() { return this.origin.getX(); }
   public int maxX() { return this.minX() + this.size - 1; }
   public int minZ() { return this.origin.getZ(); }
   public int maxZ() { return this.minZ() + this.size - 1; }

   public void configure(int size, int tntScarcity, long seed) {
      this.size = size;
      this.tntScarcity = tntScarcity;
      this.seed = seed;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.minX() - 0.5 && x <= this.maxX() + 1.5
         && z >= this.minZ() - 0.5 && z <= this.maxZ() + 1.5
         && y >= this.floorY() && y <= this.topY() + 18;
   }

   public boolean isMineable(BlockPos pos) {
      return pos.getX() > this.minX() && pos.getX() < this.maxX()
         && pos.getZ() > this.minZ() && pos.getZ() < this.maxZ()
         && pos.getY() > this.floorY() && pos.getY() <= this.topY();
   }

   public boolean isBuildable(BlockPos pos) {
      return pos.getX() > this.minX() && pos.getX() < this.maxX()
         && pos.getZ() > this.minZ() && pos.getZ() < this.maxZ()
         && pos.getY() > this.floorY() && pos.getY() <= this.topY();
   }

   public Vec3 spawn(int team, int memberIndex, int spread) {
      int inset = 7;
      int x = switch (Math.floorMod(team, 4)) { case 1, 2 -> this.maxX() - inset; default -> this.minX() + inset; };
      int z = switch (Math.floorMod(team, 4)) { case 2, 3 -> this.maxZ() - inset; default -> this.minZ() + inset; };
      int normalizedSpread = spread <= 1 ? 1 : 3;
      int dx = normalizedSpread == 1 ? 0 : Math.floorMod(memberIndex, normalizedSpread) - 1;
      int dz = normalizedSpread == 1 ? 0 : Math.floorMod(memberIndex / normalizedSpread, normalizedSpread) - 1;
      return new Vec3(x + dx + 0.5, this.floorY() + 2.0, z + dz + 0.5);
   }

   public BlockState blockAt(int x, int y, int z) {
      if (y == this.floorY() || x == this.minX() || x == this.maxX() || z == this.minZ() || z == this.maxZ()) {
         return Blocks.BEDROCK.defaultBlockState();
      }
      // Players begin in a protected chamber inside the mine, with a one-block opening
      // toward the material field.  This replaces the old exposed roof platforms.
      if (inSpawnRoom(x, y, z)) return spawnRoomBlock(x, y, z);
      if (y == this.topY() + 1) return Blocks.BARRIER.defaultBlockState();
      if (y <= this.floorY() || y > this.topY()) return Blocks.AIR.defaultBlockState();
      int value = Math.floorMod(hash(this.seed, x, y, z), 1000);
      if (value < 13) return value < (13 * (100 - this.tntScarcity)) / 100
         ? Blocks.TNT.defaultBlockState() : Blocks.GLASS.defaultBlockState();
      return BLOCKS.get(Math.floorMod(hash(this.seed + 17, x, y, z), BLOCKS.size())).defaultBlockState();
   }

   private boolean inSpawnRoom(int x, int y, int z) {
      if (y < this.floorY() + 1 || y > this.floorY() + 4) return false;
      for (int team = 0; team < 4; team++) {
         Vec3 spawn = spawn(team, 0, 1);
         int sx = (int) Math.floor(spawn.x), sz = (int) Math.floor(spawn.z);
         if (Math.abs(x - sx) <= 4 && Math.abs(z - sz) <= 4) return true;
      }
      return false;
   }

   private BlockState spawnRoomBlock(int x, int y, int z) {
      for (int team = 0; team < 4; team++) {
         Vec3 spawn = spawn(team, 0, 1);
         int sx = (int) Math.floor(spawn.x), sz = (int) Math.floor(spawn.z);
         if (Math.abs(x - sx) > 4 || Math.abs(z - sz) > 4) continue;
         if (y == this.floorY() + 1 || y == this.floorY() + 4) return Blocks.BEDROCK.defaultBlockState();
         boolean exit = x == sx + (team == 0 || team == 3 ? 4 : -4) && z == sz;
         if (exit) return Blocks.AIR.defaultBlockState();
         return Math.abs(x - sx) == 4 || Math.abs(z - sz) == 4
            ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
      }
      return Blocks.AIR.defaultBlockState();
   }

   private static int hash(long seed, int x, int y, int z) {
      long n = seed + x * 341873128712L + y * 132897987541L + z * 42317861L;
      n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL;
      n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L;
      return (int) (n ^ (n >>> 33));
   }
}
