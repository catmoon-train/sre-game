package net.exmo.sreGame.games.hypixelsays;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Reusable 16-seat arena with a resettable personal work area for each contestant. */
public final class HypixelSaysArena {
   public static final int SIZE = 88;
   public static final int STRIDE = 104;
   private final int slot;
   private final BlockPos origin;
   private boolean inUse;
   private final List<Entity> spawned = new ArrayList<>();

   HypixelSaysArena(int slot, BlockPos origin) { this.slot = slot; this.origin = origin; }
   public int slot() { return slot; }
   public BlockPos origin() { return origin; }
   public boolean inUse() { return inUse; }
   public void setInUse(boolean value) { inUse = value; }
   public int floorY() { return origin.getY(); }
   public int minX() { return origin.getX(); }
   public int minZ() { return origin.getZ(); }
   public int maxX() { return minX() + SIZE - 1; }
   public int maxZ() { return minZ() + SIZE - 1; }
   public boolean contains(double x, double y, double z) { return x >= minX() && x <= maxX() + 1 && z >= minZ() && z <= maxZ() + 1 && y >= floorY() - 4 && y <= floorY() + 18; }
   public Vec3 spawn(int index, int players) {
      double radius = 27.0;
      double angle = Math.PI * 2.0 * index / Math.max(1, players);
      return new Vec3(minX() + SIZE / 2.0 + Math.cos(angle) * radius, floorY() + 1.0, minZ() + SIZE / 2.0 + Math.sin(angle) * radius);
   }
   public BlockPos workPos(int index, int players) {
      Vec3 v = spawn(index, players);
      return BlockPos.containing(v.x, floorY(), v.z);
   }
   public void build(ServerLevel level) {
      clear(level);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX(); x <= maxX(); x++) for (int z = minZ(); z <= maxZ(); z++) {
         boolean edge = x == minX() || x == maxX() || z == minZ() || z == maxZ();
         level.setBlock(pos.set(x, floorY(), z), edge ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), 2);
         if (edge) for (int y = floorY() + 1; y <= floorY() + 4; y++) level.setBlock(pos.set(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
      }
      int cx = minX() + SIZE / 2, cz = minZ() + SIZE / 2;
      for (int x = cx - 5; x <= cx + 5; x++) for (int z = cz - 5; z <= cz + 5; z++) level.setBlock(pos.set(x, floorY(), z), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
   }
   public void resetRound(ServerLevel level) {
      discardSpawned();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX() + 1; x < maxX(); x++) for (int z = minZ() + 1; z < maxZ(); z++) for (int y = floorY() + 1; y <= floorY() + 5; y++) level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
   }
   public void track(Entity entity) { if (entity != null) spawned.add(entity); }
   public void discardSpawned() { for (Entity entity : spawned) if (entity != null && !entity.isRemoved()) entity.discard(); spawned.clear(); }
   public void clear(ServerLevel level) {
      discardSpawned();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = minX(); x <= maxX(); x++) for (int z = minZ(); z <= maxZ(); z++) for (int y = floorY(); y <= floorY() + 8; y++) level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
   }
   public void clearEntities(ServerLevel level) {
      AABB box = new AABB(minX(), floorY() - 4, minZ(), maxX() + 1, floorY() + 18, maxZ() + 1);
      for (Entity entity : level.getEntities((Entity)null, box, e -> !(e instanceof Player))) entity.discard();
   }
}
