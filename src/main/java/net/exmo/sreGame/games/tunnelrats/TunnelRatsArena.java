package net.exmo.sreGame.games.tunnelrats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * A disposable mirrored excavation arena.  It intentionally follows the source
 * map's rule: players start in protected-looking rooms, then mine a solid random
 * material field to reach the opposing team's bed.
 */
public final class TunnelRatsArena {
   public static final int STRIDE = 144;
   public static final int WIDTH = 36;
   public static final int HEIGHT = 22;

   public enum State { IDLE, IN_USE }

   private final int index;
   private final BlockPos origin;
   private State state = State.IDLE;
   private int length = 72;
   private long seed;

   public TunnelRatsArena(int index, BlockPos origin) {
      this.index = index;
      this.origin = origin;
   }

   public int index() { return this.index; }
   public BlockPos origin() { return this.origin; }
   public State state() { return this.state; }
   public void setState(State state) { this.state = state; }
   public int length() { return this.length; }
   public int minX() { return this.origin.getX(); }
   public int maxX() { return this.minX() + this.length + 1; }
   public int minZ() { return this.origin.getZ(); }
   public int maxZ() { return this.minZ() + WIDTH + 1; }
   public int floorY() { return this.origin.getY(); }
   public int topY() { return this.floorY() + HEIGHT + 1; }
   public int centerZ() { return this.minZ() + 1 + WIDTH / 2; }

   public void configure(int length, long seed) {
      this.length = Math.max(40, Math.min(100, length));
      this.seed = seed;
   }

   public boolean contains(double x, double y, double z) {
      return x >= this.minX() - 1 && x <= this.maxX() + 2
         && y >= this.floorY() - 8 && y <= this.topY() + 12
         && z >= this.minZ() - 1 && z <= this.maxZ() + 2;
   }

   public boolean isMineable(BlockPos pos) {
      return pos.getX() > this.minX() && pos.getX() < this.maxX()
         && pos.getZ() > this.minZ() && pos.getZ() < this.maxZ()
         && pos.getY() > this.floorY() && pos.getY() < this.topY();
   }

   public boolean isBuildable(BlockPos pos) {
      return pos.getX() > this.minX() && pos.getX() < this.maxX()
         && pos.getZ() > this.minZ() && pos.getZ() < this.maxZ()
         && pos.getY() > this.floorY() && pos.getY() < this.topY();
   }

   public Vec3 spawn(int team, int memberIndex) {
      int zOffset = Math.floorMod(memberIndex, 12) - 5;
      int z = Math.max(this.minZ() + 3, Math.min(this.maxZ() - 3, this.centerZ() + zOffset));
      int x = team == 1 ? this.minX() + 6 : this.maxX() - 6;
      return new Vec3(x + 0.5, this.floorY() + 2.1, z + 0.5);
   }

   public float spawnYaw(int team) { return team == 1 ? -90.0F : 90.0F; }

   public Vec3 watch(int team) {
      double x = team == 1 ? this.minX() + 8.5 : this.maxX() - 8.5;
      return new Vec3(x, this.topY() + 3.5, this.centerZ() + 0.5);
   }

   public BlockPos bedFoot(int team) {
      return new BlockPos(team == 1 ? this.minX() + 4 : this.maxX() - 4, this.floorY() + 2, this.centerZ());
   }

   public BlockPos bedHead(int team) { return this.bedFoot(team).relative(team == 1 ? Direction.EAST : Direction.WEST); }

   public boolean bedIntact(ServerLevel level, int team) {
      if (level == null) return false;
      return level.getBlockState(this.bedFoot(team)).getBlock() instanceof BedBlock
         && level.getBlockState(this.bedHead(team)).getBlock() instanceof BedBlock;
   }

   public BlockState blockAt(int x, int y, int z) {
      if (x == this.minX() || x == this.maxX() || z == this.minZ() || z == this.maxZ()
         || y == this.floorY() || y == this.topY()) {
         return Blocks.BEDROCK.defaultBlockState();
      }
      if (this.isBaseFloor(x, y, z)) return Blocks.SMOOTH_STONE.defaultBlockState();
      if (this.isBedFoot(x, y, z, 1)) return bedState(Blocks.RED_BED.defaultBlockState(), Direction.EAST, BedPart.FOOT);
      if (this.isBedHead(x, y, z, 1)) return bedState(Blocks.RED_BED.defaultBlockState(), Direction.EAST, BedPart.HEAD);
      if (this.isBedFoot(x, y, z, 2)) return bedState(Blocks.BLUE_BED.defaultBlockState(), Direction.WEST, BedPart.FOOT);
      if (this.isBedHead(x, y, z, 2)) return bedState(Blocks.BLUE_BED.defaultBlockState(), Direction.WEST, BedPart.HEAD);
      if (this.isBaseFixture(x, y, z)) return this.baseFixture(x, y, z);
      if (this.isBaseAir(x, y, z)) return Blocks.AIR.defaultBlockState();
      return material(x, y, z);
   }

   private boolean isBaseFloor(int x, int y, int z) {
      return y == this.floorY() + 1 && this.inBase(x, z);
   }

   private boolean isBaseAir(int x, int y, int z) {
      if (y < this.floorY() + 2 || y > this.floorY() + 7 || !this.inBase(x, z)) return false;
      return !this.isBaseFixture(x, y, z) && !this.isBedFoot(x, y, z, 1) && !this.isBedHead(x, y, z, 1)
         && !this.isBedFoot(x, y, z, 2) && !this.isBedHead(x, y, z, 2);
   }

   private boolean inBase(int x, int z) {
      boolean red = x >= this.minX() + 1 && x <= this.minX() + 8;
      boolean blue = x >= this.maxX() - 8 && x <= this.maxX() - 1;
      return (red || blue) && z >= this.centerZ() - 6 && z <= this.centerZ() + 6;
   }

   private boolean isBedFoot(int x, int y, int z, int team) { return new BlockPos(x, y, z).equals(this.bedFoot(team)); }
   private boolean isBedHead(int x, int y, int z, int team) { return new BlockPos(x, y, z).equals(this.bedHead(team)); }

   private boolean isBaseFixture(int x, int y, int z) {
      if (y != this.floorY() + 2 && y != this.floorY() + 6) return false;
      if (y == this.floorY() + 2 && (x == this.minX() + 4 || x == this.maxX() - 4)
         && z == this.centerZ()) return true;
      return (x == this.minX() + 2 || x == this.maxX() - 2 || x == this.minX() + 7 || x == this.maxX() - 7)
         && (z == this.centerZ() - 4 || z == this.centerZ() + 4);
   }

   private BlockState baseFixture(int x, int y, int z) {
      if (y == this.floorY() + 6) return Blocks.LANTERN.defaultBlockState();
      if ((x == this.minX() + 4 || x == this.maxX() - 4) && z == this.centerZ()) return Blocks.CHEST.defaultBlockState();
      return (x == this.minX() + 7 || x == this.maxX() - 7)
         ? Blocks.ENCHANTING_TABLE.defaultBlockState() : Blocks.FURNACE.defaultBlockState();
   }

   /** Restock the two base chests after terrain generation so each round starts fair. */
   public void populateSupplies(ServerLevel level) {
      fillSupply(level, new BlockPos(this.minX() + 4, this.floorY() + 2, this.centerZ()));
      fillSupply(level, new BlockPos(this.maxX() - 4, this.floorY() + 2, this.centerZ()));
   }

   private static void fillSupply(ServerLevel level, BlockPos pos) {
      if (!(level.getBlockEntity(pos) instanceof Container chest)) return;
      chest.clearContent();
      chest.setItem(0, new ItemStack(Items.OAK_LOG, 16));
      chest.setItem(1, new ItemStack(Items.COBBLESTONE, 24));
      chest.setItem(2, new ItemStack(Items.BREAD, 12));
      chest.setItem(3, new ItemStack(Items.TORCH, 32));
      chest.setItem(4, new ItemStack(Items.IRON_INGOT, 4));
      chest.setChanged();
   }

   private BlockState material(int x, int y, int z) {
      int value = Math.floorMod(hash(this.seed, x, y, z), 1000);
      if (value < 410) return Blocks.STONE.defaultBlockState();
      if (value < 610) return Blocks.DEEPSLATE.defaultBlockState();
      if (value < 700) return Blocks.COBBLESTONE.defaultBlockState();
      if (value < 760) return Blocks.ANDESITE.defaultBlockState();
      if (value < 805) return Blocks.TUFF.defaultBlockState();
      if (value < 845) return Blocks.GRAVEL.defaultBlockState();
      if (value < 875) return Blocks.DIRT.defaultBlockState();
      if (value < 915) return Blocks.COAL_ORE.defaultBlockState();
      if (value < 950) return Blocks.IRON_ORE.defaultBlockState();
      if (value < 972) return Blocks.REDSTONE_ORE.defaultBlockState();
      if (value < 985) return Blocks.GOLD_ORE.defaultBlockState();
      if (value < 994) return Blocks.LAPIS_ORE.defaultBlockState();
      if (value < 998) return Blocks.DIAMOND_ORE.defaultBlockState();
      return Blocks.EMERALD_ORE.defaultBlockState();
   }

   private static BlockState bedState(BlockState state, Direction facing, BedPart part) {
      return state.setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, part);
   }

   private static int hash(long seed, int x, int y, int z) {
      long value = seed + x * 341873128712L + y * 132897987541L + z * 42317861L;
      value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
      value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
      return (int) (value ^ (value >>> 33));
   }
}
